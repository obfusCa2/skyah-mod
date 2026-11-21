package net.skyah.mod;

import java.util.ArrayList;
import java.util.List;

/** The lines under the status on the SkyAH menu's first page: what is wrong right now, worst first, each
 *  with what fixes it. Free of game classes so it can be tested. At most MAX lines are shown. */
final class HomeProblems {
    static final int MAX = 3;
    /** The key the "no deal hotkey yet" line binds with one click: the usual key for the next flip. */
    static final String DEFAULT_HOTKEY = "B";

    /** The connection as the menu sees it. */
    enum Conn { OPEN, FREE, FREE_CLOSED, INACTIVE, REJECTED, ELSEWHERE, DOWN }

    /** What a click on the line does. NONE: the line is text only. */
    enum Fix { NONE, ACTIVATE, WEBSITE, SETTINGS, FILTERS, HOTKEY_DEFAULT, HOTKEY_PICK, UPDATE }

    enum Kind { KEY_INACTIVE, KEY_DUE, FREE, FREE_CLOSED, KEY_REJECTED, KEY_ELSEWHERE, FILTERS_EMPTY, NO_HOTKEY,
        HOTKEY_TAKEN, UPDATE }

    /** color: a chat color name. */
    record Problem(Kind kind, String text, String color, Fix fix) {
    }

    /** wanted: a connection is wanted (deals in chat on); deals: deals in chat on; keyDue: the key runs
     *  out soon; kept and total: what the filters keep of the last deals; hotkey: the deal hotkey's name
     *  or null when unbound; hotkeyTakenBy: what else uses that key or null; latest: the newest released
     *  version or null. */
    record State(boolean wanted, Conn conn, boolean keyDue, int freeDelayS, boolean deals, int kept, int total,
                 String hotkey, String hotkeyTakenBy, String version, String latest) {
    }

    private HomeProblems() {
    }

    /** Every problem in order, worst first (callers show the first MAX). */
    static List<Problem> all(State s) {
        List<Problem> out = new ArrayList<>();
        if (s.wanted()) {
            switch (s.conn()) {
                case INACTIVE -> out.add(new Problem(Kind.KEY_INACTIVE, "Activate your key on skyah.net/api (one bid)",
                        "yellow", Fix.ACTIVATE));
                case FREE -> out.add(new Problem(Kind.FREE, "Get a free key on skyah.net/api for live deals",
                        "yellow", Fix.WEBSITE));
                case FREE_CLOSED -> out.add(new Problem(Kind.FREE_CLOSED, "Free mode is closed: get a key on skyah.net/api",
                        "yellow", Fix.WEBSITE));
                case REJECTED -> out.add(new Problem(Kind.KEY_REJECTED,
                        "Key not accepted: paste it again under Settings", "yellow", Fix.SETTINGS));
                case ELSEWHERE -> out.add(new Problem(Kind.KEY_ELSEWHERE,
                        "Your key is in use in another game: close it there", "red", Fix.NONE));
                case OPEN -> {
                    if (s.keyDue()) {
                        out.add(new Problem(Kind.KEY_DUE, "Renew your key on skyah.net/api before it runs out",
                                "yellow", Fix.ACTIVATE));
                    }
                }
                default -> {
                }
            }
        }
        if (s.deals()) {
            if (s.total() >= 10 && s.kept() == 0) {
                out.add(new Problem(Kind.FILTERS_EMPTY, "Your filters keep no deals: open Filters", "yellow", Fix.FILTERS));
            }
            if (s.hotkey() == null) {
                out.add(new Problem(Kind.NO_HOTKEY, "No deal hotkey yet: click to use " + DEFAULT_HOTKEY, "yellow",
                        Fix.HOTKEY_DEFAULT));
            } else if (s.hotkeyTakenBy() != null) {
                out.add(new Problem(Kind.HOTKEY_TAKEN, "Deal hotkey " + s.hotkey() + " is also " + s.hotkeyTakenBy()
                        + ": pick another", "yellow", Fix.HOTKEY_PICK));
            }
        }
        if (s.latest() != null && s.version() != null && SkyAhNum.isNewer(s.latest(), s.version())) {
            out.add(new Problem(Kind.UPDATE, "Update v" + s.latest() + " is out: download it on skyah.net/api", "green",
                    Fix.UPDATE));
        }
        return out;
    }

    /** The lines the page shows. */
    static List<Problem> shown(State s) {
        List<Problem> all = all(s);
        return all.size() <= MAX ? all : all.subList(0, MAX);
    }
}
