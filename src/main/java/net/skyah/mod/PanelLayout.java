package net.skyah.mod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Where each panel of the mod sits on screen, how large it is drawn and whether it shows, free of game
 *  classes so it can be tested. All positions and sizes here are in panel units: GUI pixels divided by
 *  the panel's drawn scale (see PanelScale), so the panel's own pixels stay whole.
 *
 *  A moved panel keeps its place as a fraction of the free space on screen (0 = left or top edge, 1 =
 *  right or bottom edge, 0.5 = centered), so it stays in the same spot when the window size or the GUI
 *  scale changes, and can never end up off screen. A panel that was never moved uses its default place. */
final class PanelLayout {
    /** The deal panel next to an auction (SkyAhPanel). */
    static final String DEAL = "deal";
    /** Every panel, in the order the edit mode lists them. */
    static final List<String> IDS = List.of(DEAL);
    /** Space kept between a panel and the screen edge. */
    static final int EDGE = 2;

    /** One panel's settings, stored in the config under panels.(id). x and y: null = the default place,
     *  else the fraction of the free space described above. */
    public static final class Place {
        public boolean shown = true;
        public double scale = PanelScale.DEFAULT;
        public Double x = null;
        public Double y = null;

        public Place() {
        }

        Place(boolean shown, double scale, Double x, Double y) {
            this.shown = shown;
            this.scale = scale;
            this.x = x;
            this.y = y;
        }

        /** True once the panel was moved away from its default place. */
        boolean moved() {
            return x != null || y != null;
        }

        Place copy() {
            return new Place(shown, scale, x, y);
        }
    }

    private PanelLayout() {
    }

    /** The display name in the edit mode. */
    static String name(String id) {
        return DEAL.equals(id) ? "Deal panel" : id;
    }

    /** A copy within the allowed values: scale clamped, broken or out of range places fixed. */
    static Place sanitize(Place p) {
        if (p == null) {
            return new Place();
        }
        return new Place(p.shown, PanelScale.clamp(p.scale), fraction(p.x), fraction(p.y));
    }

    private static Double fraction(Double v) {
        if (v == null || !Double.isFinite(v)) {
            return null;
        }
        return clamp01(v);
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    /** The panels as loaded from a config file, cleaned up. legacyScale: the single panel text scale that
     *  test builds before the per-panel settings stored ("panelScale"); it becomes the deal panel's scale
     *  unless that panel has its own settings already. */
    static Map<String, Place> migrate(Map<String, Place> panels, Double legacyScale) {
        Map<String, Place> out = new LinkedHashMap<>();
        if (panels != null) {
            for (Map.Entry<String, Place> e : panels.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    out.put(e.getKey(), sanitize(e.getValue()));
                }
            }
        }
        if (legacyScale != null && !out.containsKey(DEAL)) {
            Place p = new Place();
            p.scale = PanelScale.clamp(legacyScale);
            out.put(DEAL, p);
        }
        return out;
    }

    /** The settings of one panel (the defaults when it has none). Never null; not stored by this call. */
    static Place get(Map<String, Place> panels, String id) {
        Place p = panels == null ? null : panels.get(id);
        return p == null ? new Place() : p;
    }

    /** The settings of one panel, added to the map first when it has none, for changes. */
    static Place edit(Map<String, Place> panels, String id) {
        return panels.computeIfAbsent(id, k -> new Place());
    }

    /** The panel's start on one axis (x or y): within the screen and EDGE from its edges when there is
     *  room; a panel too large for that is centered, its left or top edge kept on screen. */
    static int clamp(int pos, int size, int screen) {
        int hi = screen - size - EDGE;
        if (hi < EDGE) {
            return Math.max(0, (screen - size) / 2);
        }
        return Math.max(EDGE, Math.min(hi, pos));
    }

    /** The start on one axis for a fraction of the free space. */
    static int pos(double frac, int size, int screen) {
        int free = Math.max(0, screen - size - 2 * EDGE);
        return clamp(EDGE + (int) Math.round(clamp01(frac) * free), size, screen);
    }

    /** The fraction of the free space for a start on one axis (the inverse of pos). */
    static double frac(int pos, int size, int screen) {
        int free = screen - size - 2 * EDGE;
        if (free <= 0) {
            return 0.5;
        }
        return clamp01((pos - EDGE) / (double) free);
    }

    /** Top left corner {x, y} of a panel of w x h on a screen of screenW x screenH; def: its default
     *  place, used on each axis the panel was not moved on. */
    static int[] place(Place p, int screenW, int screenH, int w, int h, int defX, int defY) {
        int x = p != null && p.x != null ? pos(p.x, w, screenW) : clamp(defX, w, screenW);
        int y = p != null && p.y != null ? pos(p.y, h, screenH) : clamp(defY, h, screenH);
        return new int[] {x, y};
    }

    /** Stores a dragged panel's new corner as fractions (the inverse of place). */
    static void moveTo(Place p, int x, int y, int screenW, int screenH, int w, int h) {
        p.x = frac(x, w, screenW);
        p.y = frac(y, h, screenH);
    }

    /** The deal panel's default place {x, y} in panel units: right of the container, vertically aligned
     *  with its top, or left of it when the right side has no room. screenW and screenH: GUI pixels;
     *  rows: the container's rows (1 to 6); scale: the drawn panel scale; w: the panel width. */
    static int[] dealDefault(int screenW, int screenH, int rows, float scale, int w) {
        int r = Math.max(1, Math.min(6, rows));
        int cw = 176;
        int ch = 114 + r * 18;
        int cx = (screenW - cw) / 2;
        int cy = (screenH - ch) / 2;
        int left = (int) Math.floor(cx / scale);
        int right = (int) Math.ceil((cx + cw) / scale);
        int x = right + 6;
        if (x + w > (int) Math.floor(screenW / scale) - 4) {
            x = left - w - 6;
        }
        if (x < 4) {
            x = 4;
        }
        int y = Math.max(4, (int) Math.ceil(cy / scale));
        return new int[] {x, y};
    }

    /** For the log: "deal shown 0.80 x 0.95 y 0.10", "deal hidden 0.80 default place". */
    static String describe(Map<String, Place> panels) {
        StringBuilder sb = new StringBuilder();
        for (String id : IDS) {
            Place p = get(panels, id);
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(id).append(p.shown ? " shown " : " hidden ")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", p.scale))
                    .append(p.moved() ? String.format(java.util.Locale.ROOT, " x %.2f y %.2f",
                            p.x == null ? -1.0 : p.x, p.y == null ? -1.0 : p.y) : " default place");
        }
        return sb.toString();
    }

    /** Screen size in panel units for a size in GUI pixels. */
    static int units(int gui, float scale) {
        return (int) Math.floor(gui / scale);
    }
}
