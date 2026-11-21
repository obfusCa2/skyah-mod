package net.skyah.mod;

import java.util.ArrayList;
import java.util.List;

/** The parts of a deal line in chat, in one of two styles, free of game classes so it can be tested.
 *  "skyah": the profit first, then the item and the numbers. "classic": the item first, then
 *  cost -> target, the profit in brackets, then lowest BIN and volume, the order most flip lines use.
 *  Every switch of the chat line works in both. */
final class DealLine {
    static final String SKYAH = "skyah";
    static final String CLASSIC = "classic";
    /** The color of a part that takes the profit color (the first matching profit color rule). */
    static final String TIER = "tier";

    /** Which parts the line shows (the chat line switches). */
    record Show(boolean price, boolean target, boolean pct, boolean floor, boolean volume, boolean sellTime,
                boolean fee) {
    }

    /** One part of the line. color: a chat color name or TIER; title: the item name goes here (the caller
     *  draws it with its own colors, text is then empty). */
    record Part(String text, String color, boolean bold, boolean title) {
        static Part of(String text, String color) {
            return new Part(text, color, false, false);
        }
    }

    private DealLine() {
    }

    /** True for the classic style; anything else (absent, "skyah", unknown) is the SkyAH line. */
    static boolean classic(String style) {
        return style != null && CLASSIC.equalsIgnoreCase(style.trim());
    }

    /** The style a config starts with: none (the SkyAH line) when a config file was there already, the
     *  classic line on a fresh install. */
    static String initialStyle(boolean configFileExisted) {
        return configFileExisted ? null : CLASSIC;
    }

    static List<Part> parts(Flip f, Show s, long profit, double pct, boolean classic) {
        List<Part> out = new ArrayList<>();
        out.add(Part.of("[", "dark_gray"));
        out.add(Part.of("SkyAH", "gold"));
        out.add(Part.of("] ", "dark_gray"));
        if (classic) {
            out.add(new Part("", null, false, true));
            labels(out, f);
            if (s.price()) {
                out.add(Part.of(" " + FlipChat.fmt(f.price), "white"));
            }
            if (s.target() && f.targetSellPrice != null) {
                out.add(Part.of((s.price() ? " -> " : " sell ") + FlipChat.fmt(f.targetSellPrice), "gray"));
            }
            out.add(Part.of(" (", TIER));
            out.add(new Part(FlipChat.signed(profit), TIER, true, false));
            if (s.pct()) {
                out.add(Part.of(" " + FlipChat.pctText(pct), TIER));
            }
            out.add(Part.of(")", TIER));
            if (s.floor() && f.floor != null) {
                out.add(Part.of(" LBin: ", "gray"));
                out.add(Part.of(FlipChat.fmt(f.floor), "below".equals(f.floorVsTarget) ? "red" : "aqua"));
            }
            if (s.volume() && f.salesPerDay != null) {
                out.add(Part.of(" Vol: ", "gray"));
                out.add(Part.of(FlipChat.rate(f.salesPerDay), f.salesPerDay < 1.0 ? "yellow" : "aqua"));
            }
            if (s.sellTime() && f.sellTimeS != null) {
                out.add(Part.of(" ~" + FlipChat.duration(f.sellTimeS), "dark_gray"));
            }
            if (s.fee() && f.fee != null) {
                out.add(Part.of(" Fee: ", "gray"));
                out.add(Part.of(FlipChat.fmt(f.fee), "dark_gray"));
            }
            return out;
        }
        out.add(new Part(FlipChat.signed(profit), TIER, true, false));
        if (s.pct()) {
            out.add(Part.of(" " + FlipChat.pctText(pct), TIER));
        }
        labels(out, f);
        out.add(Part.of(" ", null));
        out.add(new Part("", null, false, true));
        if (s.price()) {
            out.add(Part.of(" " + FlipChat.fmt(f.price), "white"));
        }
        if (s.target() && f.targetSellPrice != null) {
            out.add(Part.of((s.price() ? " → " : " sell ") + FlipChat.fmt(f.targetSellPrice), "gray"));
        }
        if (s.floor() && f.floor != null) {
            out.add(Part.of(" floor " + FlipChat.fmt(f.floor), "below".equals(f.floorVsTarget) ? "red" : "dark_gray"));
        }
        if (s.volume() && f.salesPerDay != null) {
            // Yellow under 1 sale a day.
            out.add(Part.of(" " + FlipChat.rate(f.salesPerDay) + "/d", f.salesPerDay < 1.0 ? "yellow" : "dark_gray"));
        }
        if (s.sellTime() && f.sellTimeS != null) {
            out.add(Part.of(" ~" + FlipChat.duration(f.sellTimeS), "dark_gray"));
        }
        if (s.fee() && f.fee != null) {
            out.add(Part.of(" fee " + FlipChat.fmt(f.fee), "dark_gray"));
        }
        return out;
    }

    /** AUCTION (you have to bid) and PRE-API (brand new listing) after the profit or the item. */
    private static void labels(List<Part> out, Flip f) {
        if (f.bin == null || !f.bin) {
            out.add(Part.of(" AUCTION", "yellow"));
        }
        if (f.preApi) {
            out.add(Part.of(" PRE-API", "light_purple"));
        }
    }

    /** The line as plain text, the item name in place of the title part (logs, tests). */
    static String text(List<Part> parts, String itemName) {
        StringBuilder sb = new StringBuilder();
        for (Part p : parts) {
            sb.append(p.title() ? itemName : p.text());
        }
        return sb.toString();
    }
}
