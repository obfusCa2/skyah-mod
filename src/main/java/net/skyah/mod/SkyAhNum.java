package net.skyah.mod;

/** Compact number parsing for settings: "5m", "500k", "1b", "10%". */
public final class SkyAhNum {
    private SkyAhNum() {
    }

    /** Empty input means 0; invalid input keeps the fallback. */
    public static long parseCoins(String raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim().toLowerCase().replace(",", "").replace("_", "").replace(" ", "");
        if (s.isEmpty()) {
            return 0L;
        }
        long mult = 1L;
        if (s.endsWith("k")) {
            mult = 1_000L;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            mult = 1_000_000L;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("b")) {
            mult = 1_000_000_000L;
            s = s.substring(0, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s);
            if (v < 0) {
                return fallback;
            }
            return (long) (v * mult);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Accepts "10" or "10%". Empty means 0; invalid keeps the fallback. */
    public static double parsePct(String raw, double fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim().replace("%", "").replace(",", ".");
        if (s.isEmpty()) {
            return 0.0;
        }
        try {
            double v = Double.parseDouble(s);
            return v < 0 ? fallback : v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Value for an edit box: 0 -> "", 1500000 -> "1.5m", 250000 -> "250k",
     *  amounts that do not shorten exactly stay plain digits. */
    public static String editCoins(long v) {
        if (v <= 0) {
            return "";
        }
        if (v >= 1_000_000_000L && v % 10_000_000L == 0) {
            return strip(v / 1e9) + "b";
        }
        if (v >= 1_000_000L && v % 1_000L == 0) {
            return strip(v / 1e6) + "m";
        }
        if (v >= 1_000L && v % 1_000L == 0) {
            return (v / 1_000L) + "k";
        }
        return Long.toString(v);
    }

    private static String strip(double v) {
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /** Next preset above the current value; wraps to the first one. */
    public static long nextStep(long current, long[] steps) {
        for (long s : steps) {
            if (s > current) {
                return s;
            }
        }
        return steps[0];
    }
}
