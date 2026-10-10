package net.skyah.mod;

/** Session counters shown in the settings screen. */
public final class SkyAhStats {
    private static volatile long shown = 0;

    private SkyAhStats() {
    }

    public static void shown() {
        shown++;
    }

    public static long shownCount() {
        return shown;
    }
}
