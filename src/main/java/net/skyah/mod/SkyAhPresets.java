package net.skyah.mod;

import java.util.List;

/** Built-in filter profiles every player has. They set the filters only;
 *  item lists, colors and the rest stay as they are. */
final class SkyAhPresets {
    record Preset(String name, String desc, String tip, long minProfit, double minPct,
                  boolean purse, boolean bin, boolean auctions) {
    }

    static final List<Preset> ALL = List.of(
            new Preset("Starter", "small budget",
                    "For players with few coins: only deals your purse can pay, 500k+ profit and 8%+, BIN only. "
                            + "About 65 an hour with 20M in the purse, fewer with less.",
                    500_000L, 8, true, true, false),
            new Preset("Balanced", "the recommended mix",
                    "1M+ profit and 5%+, every price, BIN and auctions. About 55 deals an hour. This is the default.",
                    1_000_000L, 5, false, true, true),
            new Preset("Big flips", "10M+ profit",
                    "Only big ones: 10M+ profit and 5%+. About 4 an hour, but each one counts.",
                    10_000_000L, 5, false, true, true));

    private SkyAhPresets() {
    }

    static boolean matches(Preset p, SkyAhConfig c) {
        return c.minProfit == p.minProfit() && c.minPct == p.minPct() && c.maxPrice == 0
                && c.maxPriceFromPurse == p.purse() && c.maxSellTimeS == 0
                && c.showBin == p.bin() && c.showAuctions == p.auctions();
    }

    static void apply(Preset p, SkyAhConfig c) {
        c.minProfit = p.minProfit();
        c.minPct = p.minPct();
        c.maxPrice = 0;
        c.maxPriceFromPurse = p.purse();
        c.maxSellTimeS = 0;
        c.showBin = p.bin();
        c.showAuctions = p.auctions();
        c.activeProfile = p.name();
    }

    static boolean isPresetName(String name) {
        return ALL.stream().anyMatch(p -> p.name().equalsIgnoreCase(name == null ? "" : name.trim()));
    }

    /** Name of the built-in or own profile the current filters equal, or null. */
    static String activeName(SkyAhConfig c) {
        for (Preset p : ALL) {
            if (matches(p, c)) {
                return p.name();
            }
        }
        if (c.profiles != null) {
            for (SkyAhProfile p : c.profiles) {
                if (p != null && p.name != null && p.matches(c)) {
                    return p.name;
                }
            }
        }
        return null;
    }
}
