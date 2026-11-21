package net.skyah.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Free mode: without a key the mod connects to the free feed, which sends
 *  every deal a little later than the keyed feed. The server says how much
 *  later in its first frame ({"type":"free","delay_s":60}). A key is
 *  activated on the website only; the menu links there. */
final class FreeMode {
    static final String KEYED_PATH = "/ws/v1/flips";
    static final String FREE_PATH = "/ws/v1/free";
    static final int DEFAULT_DELAY_S = 60;
    /** Close code of a free feed that is switched off on the server. */
    static final int CLOSED_CODE = 4404;

    private static volatile boolean active = false;
    private static volatile int delayS = DEFAULT_DELAY_S;

    private FreeMode() {
    }

    /** The url to connect to: the configured one for the keyed feed, else its free twin
     *  (with deals in chat off: "deals=off", the server then sends no deals). */
    static String feedUrl(String url, boolean keyed, boolean dealsOff) {
        if (keyed || url == null) {
            return url;
        }
        String u = url.trim();
        int q = u.indexOf('?');
        String path = q >= 0 ? u.substring(0, q) : u;
        String query = q >= 0 ? u.substring(q) : "";
        if (path.endsWith(KEYED_PATH)) {
            if (dealsOff) {
                query = query.isEmpty() ? "?deals=off" : query + "&deals=off";
            }
            return path.substring(0, path.length() - KEYED_PATH.length()) + FREE_PATH + query;
        }
        return u; // a custom url is used as it is
    }

    /** The keyed feed needs a key, deals in chat on and a key the server has not refused. */
    static boolean keyedFeed(boolean hasKey, boolean dealsOn, boolean refused) {
        return hasKey && dealsOn && !refused;
    }

    /** {"type":"free"} from the server (socket thread). */
    static void hello(String json) {
        int delay = DEFAULT_DELAY_S;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            JsonElement d = o.get("delay_s");
            if (d != null && d.isJsonPrimitive() && d.getAsJsonPrimitive().isNumber()) {
                delay = (int) Math.max(0, Math.min(3_600, d.getAsLong()));
            }
        } catch (Exception e) {
            SkyAhLog.add("free message: delay unreadable, using " + DEFAULT_DELAY_S + "s");
        }
        delayS = delay;
        if (!active) {
            SkyAhLog.add("free mode: deals arrive " + delay + "s late");
        }
        active = true;
    }

    /** The connection ended or a new one starts: free only after the next hello. */
    static void reset() {
        active = false;
    }

    /** The current connection is the free feed (its hello came). */
    static boolean active() {
        return active;
    }

    static int delayS() {
        return delayS;
    }

    /** "60s", "90s", "2m", "2m 30s". */
    static String delayText(int seconds) {
        if (seconds < 120) {
            return seconds + "s";
        }
        int m = seconds / 60;
        int s = seconds % 60;
        return s == 0 ? m + "m" : m + "m " + s + "s";
    }
}
