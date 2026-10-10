package net.skyah.mod;

import net.minecraft.client.Minecraft;
import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/** One WebSocket connection to the SkyAH flip feed, with auto-reconnect.
 *  A generation counter guarantees that only the newest socket can connect,
 *  receive or reconnect, so a stale one can never hold the key server-side. */
public class FlipsSocket {
    private volatile WebSocketClient client;
    private volatile boolean stopped = true;
    private final AtomicLong generation = new AtomicLong();
    private int attempts;
    public volatile int lastCloseCode = 0;
    public volatile String lastCloseReason = "";
    public volatile long lastMessageAt = 0L;
    /** Smoothed WebSocket round trip (ms), -1 until the first pong. */
    public volatile long rttMs = -1L;
    private volatile long pingSentAt = 0L;

    public String lastCloseLabel() {
        int code = lastCloseCode;
        if (code == 0) {
            return "";
        }
        if (code == 4401) {
            return " (auth failed, check key)";
        }
        if (code == 4402) {
            return " (key inactive - activate on skyah.net/api)";
        }
        if (code == 4409) {
            return " (key in use by another client)";
        }
        if (code == 1013) {
            return " (server full)";
        }
        return " (closed " + code + ")";
    }

    public synchronized void start() {
        stopped = false;
        connect();
    }

    public synchronized void stop() {
        stopped = true;
        generation.incrementAndGet();
        WebSocketClient c = client;
        client = null;
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    public synchronized void restart() {
        stop();
        attempts = 0;
        start();
    }

    public boolean isOpen() {
        WebSocketClient c = client;
        return c != null && c.isOpen();
    }

    /** Best-effort client -> server message (settings, purchases, open timings). */
    public boolean send(String json) {
        WebSocketClient c = client;
        if (c == null || !c.isOpen()) {
            return false;
        }
        try {
            c.send(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void connect() {
        long gen = generation.incrementAndGet();
        WebSocketClient previous = client;
        if (previous != null) {
            try {
                previous.close();
            } catch (Exception ignored) {
            }
        }
        try {
            String url = SkyAhMod.CONFIG.effectiveUrl();
            String key = SkyAhMod.CONFIG.apiKey == null ? "" : SkyAhMod.CONFIG.apiKey.trim();
            if (!key.isEmpty()) {
                SkyAhLog.add("using key (" + key.length() + " chars)");
            }
            URI uri = URI.create(url);
            String shown = url.replaceAll("key=[^&]+", "key=***");
            java.util.Map<String, String> headers = new java.util.HashMap<>();
            if (!key.isEmpty()) {
                headers.put("Authorization", "Bearer " + key);
            }
            WebSocketClient c = new WebSocketClient(uri, headers) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    if (gen != generation.get()) {
                        try {
                            close();
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    attempts = 0;
                    SkyAhLog.add("connected [" + SkyAhMod.BUILD + "]: " + shown);
                    SkyAhMod.sendSettings();
                    startPinger(gen, this);
                }

                @Override
                public void onWebsocketPong(WebSocket conn, Framedata f) {
                    super.onWebsocketPong(conn, f);
                    long sent = pingSentAt;
                    if (sent > 0L) {
                        pingSentAt = 0L;
                        long rtt = System.currentTimeMillis() - sent;
                        long prev = rttMs;
                        rttMs = prev < 0 ? rtt : (prev * 3 + rtt) / 4;
                    }
                }

                @Override
                public void onMessage(String message) {
                    if (gen != generation.get()) {
                        return;
                    }
                    long receivedAt = System.currentTimeMillis();
                    lastMessageAt = receivedAt;
                    Flip flip = Flip.parse(message);
                    if (flip == null) {
                        return;
                    }
                    flip.receivedAt = receivedAt;
                    boolean replayFrame = flip.replay != null && flip.replay;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc == null) {
                        return;
                    }
                    mc.execute(() -> {
                        // Counted even when hidden: the settings screens show
                        // how many incoming deals the filters keep.
                        FlipChat.received(flip);
                        if (replayFrame && !SkyAhMod.CONFIG.showReplay) {
                            return;
                        }
                        FlipChat.show(flip);
                        if (!replayFrame) {
                            SkyAhLatency.log(flip, receivedAt, System.currentTimeMillis(), rttMs);
                        }
                    });
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (gen != generation.get()) {
                        return;
                    }
                    lastCloseCode = code;
                    lastCloseReason = reason == null ? "" : reason;
                    SkyAhLog.add("closed: " + code
                            + (reason == null || reason.isBlank() ? "" : " " + reason));
                    if (code == 4401) {
                        SkyAhLog.add("auth failed: check your API key in /skyah menu");
                    } else if (code == 4409) {
                        SkyAhLog.add("key in use: closing stale sockets should free it");
                    }
                    if (!stopped) {
                        scheduleReconnect(gen);
                    }
                }

                @Override
                public void onError(Exception ex) {
                    if (gen != generation.get()) {
                        return;
                    }
                    SkyAhLog.add("error: " + ex.getClass().getSimpleName()
                            + (ex.getMessage() == null ? "" : " " + ex.getMessage()));
                }
            };
            c.setConnectionLostTimeout(60);
            client = c;
            c.connect();
        } catch (Exception e) {
            SkyAhLog.add("connect failed: " + e.getMessage());
            scheduleReconnect(gen);
        }
    }

    /** RTT probe for the latency log: one WebSocket ping every 20s while
     *  this generation's socket is open. */
    private void startPinger(long gen, WebSocketClient c) {
        Thread t = new Thread(() -> {
            while (!stopped && gen == generation.get() && c.isOpen()) {
                try {
                    pingSentAt = System.currentTimeMillis();
                    c.sendPing();
                } catch (Exception ignored) {
                }
                try {
                    Thread.sleep(20_000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "skyah-ping");
        t.setDaemon(true);
        t.start();
    }

    private void scheduleReconnect(long gen) {
        if (stopped) {
            return;
        }
        int delaySec = lastCloseCode == 4409
                ? 15
                : Math.min(30, 1 << Math.min(attempts, 5));
        attempts++;
        SkyAhLog.add("reconnecting in " + delaySec + "s (attempt " + attempts + ")");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delaySec * 1000L + ThreadLocalRandom.current().nextInt(1000));
            } catch (InterruptedException e) {
                return;
            }
            if (!stopped && gen == generation.get()) {
                connect();
            }
        }, "skyah-reconnect");
        t.setDaemon(true);
        t.start();
    }
}
