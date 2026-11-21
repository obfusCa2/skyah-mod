package net.skyah.mod;

/** Size of a panel on screen, free of game classes so it can be tested. Each panel has a setting from
 *  MIN to MAX (see PanelLayout.Place); the panel draws at the nearest step that keeps every one of its
 *  pixels on whole screen pixels at the current GUI scale, so its text stays sharp. */
final class PanelScale {
    static final double MIN = 0.6;
    static final double MAX = 1.5;
    static final double DEFAULT = 0.8;

    private PanelScale() {
    }

    /** The setting within MIN..MAX (DEFAULT for a broken value). */
    static double clamp(double v) {
        if (!Double.isFinite(v)) {
            return DEFAULT;
        }
        return Math.max(MIN, Math.min(MAX, v));
    }

    /** Smallest and largest k for a drawn scale of k / guiScale. */
    private static int lo(int guiScale) {
        return (int) Math.ceil(MIN * guiScale - 1e-9);
    }

    private static int hi(int guiScale) {
        return Math.max(lo(guiScale), (int) Math.floor(MAX * guiScale + 1e-9));
    }

    /** The scale the panel draws at: k / guiScale nearest to the setting (k whole), within MIN..MAX;
     *  the setting itself when the GUI scale is unknown. */
    static float effective(double setting, int guiScale) {
        double s = clamp(setting);
        if (guiScale <= 0) {
            return (float) s;
        }
        int k = (int) Math.round(s * guiScale);
        k = Math.max(lo(guiScale), Math.min(hi(guiScale), k));
        return (float) k / guiScale;
    }

    /** One step larger (dir > 0) or smaller (dir < 0) from the drawn scale: the next whole pixel step at
     *  this GUI scale, a tenth when it is unknown. Stays within MIN..MAX (no wrap). */
    static double nudge(double setting, int dir, int guiScale) {
        int d = Integer.signum(dir);
        if (guiScale <= 0) {
            return clamp(Math.round((clamp(setting) + 0.1 * d) * 10) / 10.0);
        }
        int k = Math.round(effective(setting, guiScale) * guiScale) + d;
        k = Math.max(lo(guiScale), Math.min(hi(guiScale), k));
        return (double) k / guiScale;
    }

    /** True when nudge in this direction would change the drawn scale. */
    static boolean canNudge(double setting, int dir, int guiScale) {
        return effective(nudge(setting, dir, guiScale), guiScale) != effective(setting, guiScale);
    }

    /** The drawn scale as a percentage, "80%". */
    static String percent(float drawn) {
        return Math.round(drawn * 100) + "%";
    }
}
