package net.skyah.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
    /** When the current socket opened; 0 while not open. */
    private volatile long openedAt = 0L;
    public volatile int lastCloseCode = 0;
    public volatile String lastCloseReason = "";
    /** 4401 or 4402 once the keyed feed refused the key (not accepted, inactive): the mod then
     *  uses the free feed and sends the key along, and the server sends it back (4410) once the
     *  key is active. 0 = use the keyed feed. Saving a key or Reconnect clears it. */
    public volatile int keyedRefused = 0;
    /** Close code of the free feed for a key that is active again: back to the keyed feed. */
    static final int KEY_ACTIVE_CODE = 4410;
    /** The current socket goes to the keyed feed. */
    private volatile boolean keyed = false;
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
        if (code == FreeMode.CLOSED_CODE) {
            return " (free mode closed, get a key on skyah.net/api)";
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
        SkyAhBazaarRecorder.stopped();
        SkyAhListings.ended("connection stopped");
        FreeMode.reset();
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
        keyedRefused = 0;
        start();
    }

    /** Deals in chat switched on or off: another feed (keyed, or free with deals off), the same
     *  data. The recorder keeps its queue over the switch. */
    public synchronized void switchFeed() {
        keyedRefused = 0;
        attempts = 0;
        if (stopped) {
            start();
            return;
        }
        SkyAhBazaarRecorder.disconnected();
        SkyAhListings.ended("feed switch");
        FreeMode.reset();
        connect();
    }

    /** True between start() and stop(): the socket is open or reconnecting. */
    public boolean running() {
        return !stopped;
    }

    public boolean isOpen() {
        WebSocketClient c = client;
        return c != null && c.isOpen();
    }

    /** The url this connection uses: the configured one for the keyed feed, else the free feed. */
    static String connectUrl() {
        FlipsSocket s = SkyAhMod.SOCKET;
        return feedUrl(s != null && s.keyedRefused != 0);
    }

    private static String feedUrl(boolean refused) {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        return FreeMode.feedUrl(cfg.effectiveUrl(), FreeMode.keyedFeed(cfg.hasKey(), cfg.enabled, refused), !cfg.enabled);
    }

    /** The "type" of a control frame from the server, null for deal frames
     *  (they have none) and anything unreadable. */
    static String frameType(String json) {
        if (json == null || !json.contains("\"type\"")) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement t = root.getAsJsonObject().get("type");
            return t != null && t.isJsonPrimitive() ? t.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Control frames this build has no use for (sent to other builds of the mod): dropped quietly,
     *  before the deal parser sees them. */
    static boolean ignored(String type) {
        return "bz_coach".equals(type) || "dbg_on".equals(type);
    }

    /** Best-effort client -> server message (settings, purchases, open timings, key state requests). */
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
            SkyAhConfig cfg = SkyAhMod.CONFIG;
            keyed = FreeMode.keyedFeed(cfg.hasKey(), cfg.enabled, keyedRefused != 0);
            String url = feedUrl(keyedRefused != 0);
            // The key goes along to the free feed too: the server then knows the account and
            // sends the mod back once the key is active.
            String key = cfg.apiKey == null ? "" : cfg.apiKey.trim();
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
                    openedAt = System.currentTimeMillis();
                    FreeMode.reset();
                    SkyAhListings.ended("new connection");
                    SkyAhLog.add("connected [" + SkyAhMod.BUILD + "]: " + shown);
                    SkyAhBazaarRecorder.connected();
                    SkyAhMod.sendSettings(true);
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
                    // Control frames by their type; unknown types fall through
                    // to the deal parser, which drops frames without a deal.
                    String type = frameType(message);
                    if ("key".equals(type)) {
                        SkyAhKey.update(message);
                        return;
                    }
                    if ("version".equals(type)) {
                        SkyAhMod.versionMessage(message);
                        return;
                    }
                    if ("bz_on".equals(type)) {
                        SkyAhBazaarRecorder.serverOn();
                        return;
                    }
                    if ("free".equals(type)) {
                        FreeMode.hello(message);
                        return;
                    }
                    if ("ah_on".equals(type)) {
                        SkyAhListings.serverOn();
                        return;
                    }
                    if (ignored(type)) {
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
                        // Deals in chat switched off while this frame was on its way: nothing shows.
                        if (!SkyAhMod.CONFIG.enabled || replayFrame && !SkyAhMod.CONFIG.showReplay) {
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
                    SkyAhBazaarRecorder.disconnected();
                    SkyAhListings.ended("disconnected");
                    FreeMode.reset();
                    // The server accepts and then closes rejected keys, so
                    // only a connection that lasted resets the backoff.
                    if (openedAt > 0L && System.currentTimeMillis() - openedAt > 30_000L) {
                        attempts = 0;
                    }
                    openedAt = 0L;
                    SkyAhLog.add("closed: " + code
                            + (reason == null || reason.isBlank() ? "" : " " + reason));
                    boolean fast = false;
                    if (keyed && (code == 4401 || code == 4402)) {
                        keyedRefused = code;
                        fast = true;
                        SkyAhLog.add(code == 4402 ? "key inactive: free feed until it is activated"
                                : "key not accepted: free feed");
                    } else if (code == KEY_ACTIVE_CODE) {
                        keyedRefused = 0;
                        fast = true;
                        SkyAhLog.add("key active: back to live deals");
                    }
                    if (code == 4401) {
                        SkyAhLog.add("auth failed: check your API key in /skyah menu");
                    } else if (code == 4409) {
                        SkyAhLog.add("key in use: closing stale sockets should free it");
                    } else if (code == FreeMode.CLOSED_CODE) {
                        SkyAhLog.add("free mode closed on the server: a key on skyah.net/api gives live deals");
                    }
                    if (!stopped) {
                        scheduleReconnect(gen, fast);
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
            scheduleReconnect(gen, false);
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

    private void scheduleReconnect(long gen, boolean fast) {
        if (stopped) {
            return;
        }
        int delaySec;
        if (fast && attempts < 4) {
            // Switching between the keyed and the free feed; a loop falls back to the backoff below.
            delaySec = 1;
        } else if (lastCloseCode == 4409) {
            delaySec = 15;
        } else if (lastCloseCode == FreeMode.CLOSED_CODE) {
            // Free feed switched off: check back now and then. Saving a key connects at once.
            delaySec = Math.min(300, 30 << Math.min(attempts, 4));
        } else if (lastCloseCode == 4401 || lastCloseCode == 4402) {
            // No, wrong or inactive key: fast retries cannot fix that. Saving
            // a key or Reconnect in the menu still connects right away.
            delaySec = Math.min(lastCloseCode == 4401 ? 60 : 30, 5 << Math.min(attempts, 4));
        } else {
            delaySec = Math.min(30, 1 << Math.min(attempts, 5));
        }
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
