package net.skyah.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bazaar tracking state and queue, free of game classes so it can be tested.
 *
 *  OFF: records nothing (the default). ON: the server sent the switch on the
 *  current connection; events are recorded and sent in batches. HOLD: the
 *  connection dropped while ON; events wait in the queue until the next
 *  connection sends the switch again. A new connection without the switch, a
 *  stopped socket or a long time offline turns it OFF and empties the queue.
 *
 *  Only reads what the game shows. Nothing is clicked, sent to the game or
 *  changed. */
final class BazaarRecorder {
    enum State { OFF, ON, HOLD }

    static final int QUEUE_CAP = 500;
    static final int BATCH_EVENTS = 50;
    static final int BATCH_CHARS = 30_000;
    static final int EVENT_CHARS = 28_000;
    static final long FLUSH_MS = 3_000L;
    static final long ON_WAIT_MS = 20_000L;
    static final long HOLD_MS = 60 * 60_000L;
    static final int LINE_MAX = 500;
    static final int NAME_MAX = 120;
    static final int LORE_LINES = 40;
    static final int LORE_LINE_MAX = 200;
    static final int CUT_LORE_LINES = 12;

    private static final Pattern FORMAT = Pattern.compile("§.?");
    private static final Pattern LINES = Pattern.compile("\\R");
    private static final Pattern PROFILE = Pattern.compile("^Profile ID: ([0-9a-fA-F-]{32,36})$");

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    private final String session;
    private final Consumer<String> log;
    private final Deque<String> queue = new ArrayDeque<>();
    private State state = State.OFF;
    private long holdSince;
    private long waitUntil;
    private long lastFlush;
    private int seq;
    private int dropped;
    private long sentTotal;
    private String profile;

    BazaarRecorder(String session, Consumer<String> log) {
        this.session = session;
        this.log = log;
    }

    /** Text without formatting codes. */
    static String plain(String text) {
        return text == null ? "" : FORMAT.matcher(text).replaceAll("");
    }

    static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** The bazaar lines of one chat message: lines that start with "[Bazaar] "
     *  (a player's message starts with a rank or name, never with that) and the
     *  "Profile ID: " line. */
    static List<String> chatLines(String text) {
        List<String> out = new ArrayList<>();
        for (String line : LINES.split(plain(text))) {
            line = line.stripTrailing();
            if (line.startsWith("[Bazaar] ") || PROFILE.matcher(line).matches()) {
                out.add(cut(line, LINE_MAX));
            }
        }
        return out;
    }

    synchronized State state() {
        return state;
    }

    synchronized boolean recording() {
        return state != State.OFF;
    }

    synchronized int queued() {
        return queue.size();
    }

    /** The server switched tracking on for this connection. */
    synchronized void serverOn() {
        State was = state;
        state = State.ON;
        waitUntil = 0L;
        if (was != State.ON) {
            log.accept("bazaar tracking on" + (queue.isEmpty() ? "" : " (" + queue.size() + " queued)"));
        }
    }

    synchronized void connected(long now) {
        if (state == State.HOLD) {
            waitUntil = now + ON_WAIT_MS;
        }
    }

    synchronized void disconnected(long now) {
        if (state == State.ON) {
            state = State.HOLD;
            holdSince = now;
        }
        waitUntil = 0L;
    }

    synchronized void off(String why) {
        if (state != State.OFF || !queue.isEmpty()) {
            log.accept("bazaar tracking off (" + why + ")"
                    + (queue.isEmpty() ? "" : ", " + queue.size() + " unsent events dropped"));
        }
        state = State.OFF;
        queue.clear();
        profile = null;
        waitUntil = 0L;
    }

    synchronized void timers(long now) {
        if (state != State.HOLD) {
            return;
        }
        if (waitUntil > 0L && now >= waitUntil) {
            off("not switched on for this connection");
        } else if (now - holdSince >= HOLD_MS) {
            off("offline too long");
        }
    }

    private Map<String, Object> event(String kind, long t, long purse) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("seq", ++seq);
        ev.put("t", t);
        ev.put("k", kind);
        ev.put("purse", purse >= 0 ? purse : -1L);
        if (profile != null) {
            ev.put("profile", profile);
        }
        return ev;
    }

    /** One chat message; returns the lines recorded. */
    synchronized List<String> chat(String text, long now, long purse) {
        if (state == State.OFF || text == null) {
            return List.of();
        }
        List<String> lines = chatLines(text);
        for (String line : lines) {
            Matcher m = PROFILE.matcher(line);
            if (m.matches()) {
                profile = m.group(1);
            }
            Map<String, Object> ev = event("chat", now, purse);
            ev.put("line", line);
            push(gson.toJson(ev));
        }
        return lines;
    }

    /** One settled menu: slots as maps with i, id, name, count and lore. */
    synchronized boolean menu(String title, List<Map<String, Object>> slots, long t, long purse) {
        if (state == State.OFF) {
            return false;
        }
        Map<String, Object> ev = event("menu", t, purse);
        ev.put("title", title);
        ev.put("slots", slots);
        String json = gson.toJson(ev);
        if (json.length() > EVENT_CHARS) {
            List<Map<String, Object>> shorter = new ArrayList<>();
            for (Map<String, Object> slot : slots) {
                Map<String, Object> copy = new LinkedHashMap<>(slot);
                Object lore = copy.get("lore");
                if (lore instanceof List<?> l && l.size() > CUT_LORE_LINES) {
                    copy.put("lore", new ArrayList<>(l.subList(0, CUT_LORE_LINES)));
                }
                shorter.add(copy);
            }
            ev.put("slots", shorter);
            ev.put("cut", true);
            json = gson.toJson(ev);
            if (json.length() > EVENT_CHARS) {
                log.accept("bazaar menu too large, skipped: " + title);
                return false;
            }
        }
        push(json);
        return true;
    }

    private void push(String json) {
        queue.addLast(json);
        while (queue.size() > QUEUE_CAP) {
            queue.removeFirst();
            if (dropped++ % 50 == 0) {
                log.accept("bazaar queue full, oldest events dropped (" + dropped + " so far)");
            }
        }
    }

    /** At most one batch every FLUSH_MS while ON. Returns the events sent. */
    synchronized int flush(long now, Predicate<String> send) {
        if (state != State.ON || queue.isEmpty() || now - lastFlush < FLUSH_MS) {
            return 0;
        }
        lastFlush = now;
        StringBuilder sb = new StringBuilder(256)
                .append("{\"type\":\"bz\",\"v\":1,\"session\":\"").append(session).append("\",\"events\":[");
        int n = 0;
        for (String ev : queue) {
            if (n > 0 && (n >= BATCH_EVENTS || sb.length() + ev.length() + 3 > BATCH_CHARS)) {
                break;
            }
            if (n > 0) {
                sb.append(',');
            }
            sb.append(ev);
            n++;
        }
        sb.append("]}");
        if (!send.test(sb.toString())) {
            return 0;
        }
        for (int i = 0; i < n; i++) {
            queue.removeFirst();
        }
        sentTotal += n;
        return n;
    }

    synchronized long sentTotal() {
        return sentTotal;
    }
}
