package net.skyah.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Panel places: kept as fractions of the free space, always on screen, the deal panel's default next to
 *  the auction menu as before, and configs from earlier builds loading into the per-panel settings. */
class PanelLayoutTest {
    @Test
    void fractionsAnchorToTheEdges() {
        assertEquals(PanelLayout.EDGE, PanelLayout.pos(0.0, 150, 640));
        assertEquals(640 - 150 - PanelLayout.EDGE, PanelLayout.pos(1.0, 150, 640));
        assertEquals(PanelLayout.EDGE + 243, PanelLayout.pos(0.5, 150, 640));
        // Same fraction, other window size: still at the right edge, still centered.
        assertEquals(427 - 150 - PanelLayout.EDGE, PanelLayout.pos(1.0, 150, 427));
        int free = 854 - 150 - 2 * PanelLayout.EDGE;
        assertEquals(PanelLayout.EDGE + free / 2, PanelLayout.pos(0.5, 150, 854));
    }

    @Test
    void neverOffScreen() {
        assertEquals(PanelLayout.EDGE, PanelLayout.clamp(-50, 150, 640));
        assertEquals(640 - 150 - PanelLayout.EDGE, PanelLayout.clamp(1000, 150, 640));
        assertEquals(100, PanelLayout.clamp(100, 150, 640));
        // Larger than the screen: the left or top edge stays visible.
        assertEquals(0, PanelLayout.clamp(10, 300, 200));
        assertEquals(1, PanelLayout.clamp(10, 150, 152));
        // Fractions out of range land at the edges.
        assertEquals(PanelLayout.EDGE, PanelLayout.pos(-3.0, 150, 640));
        assertEquals(640 - 150 - PanelLayout.EDGE, PanelLayout.pos(7.0, 150, 640));
        for (int screen : new int[] {200, 320, 427, 640, 960}) {
            for (double f : new double[] {0.0, 0.3, 1.0}) {
                int x = PanelLayout.pos(f, 150, screen);
                assertTrue(x >= 0 && x + 150 <= Math.max(screen, 150), screen + " " + f + " -> " + x);
            }
        }
    }

    @Test
    void dragRoundTrips() {
        PanelLayout.Place p = new PanelLayout.Place();
        for (int x : new int[] {2, 17, 200, 488}) {
            PanelLayout.moveTo(p, x, 40, 640, 360, 150, 120);
            assertArrayEquals(new int[] {x, 40}, PanelLayout.place(p, 640, 360, 150, 120, 0, 0));
        }
        // Dragged past the edge: stored at the edge.
        PanelLayout.moveTo(p, 900, -20, 640, 360, 150, 120);
        assertEquals(1.0, p.x, 1e-9);
        assertEquals(0.0, p.y, 1e-9);
        assertTrue(p.moved());
        // No room to move: centered.
        assertEquals(0.5, PanelLayout.frac(0, 300, 200), 1e-9);
    }

    @Test
    void defaultPlaceIsTheOldOne() {
        // Wide screen: right of the 6 row menu, at its top.
        assertArrayEquals(new int[] {521, 129}, PanelLayout.dealDefault(854, 480, 6, 1f, 150));
        // 427 wide at full size: no room on either side, at the left edge as before.
        assertArrayEquals(new int[] {4, 9}, PanelLayout.dealDefault(427, 240, 10, 1f, 150));
        // GUI scale 3 on 1920 x 1080 at 2/3: right of the menu without covering it.
        float sc = 2f / 3f;
        int[] at = PanelLayout.dealDefault(640, 360, 6, sc, 150);
        int menuRight = (640 + 176) / 2;
        assertTrue(at[0] * sc >= menuRight, "x " + at[0]);
        assertTrue((at[0] + 150) * sc <= 640, "x " + at[0]);
        // Never moved: the default place, kept on screen.
        PanelLayout.Place p = new PanelLayout.Place();
        assertArrayEquals(new int[] {521, 129}, PanelLayout.place(p, 854, 480, 150, 110, 521, 129));
        assertArrayEquals(new int[] {640 - 150 - PanelLayout.EDGE, 360 - 110 - PanelLayout.EDGE},
                PanelLayout.place(p, 640, 360, 150, 110, 600, 300));
    }

    @Test
    void sanitizeFixesBrokenValues() {
        PanelLayout.Place p = PanelLayout.sanitize(new PanelLayout.Place(false, 9.0, Double.NaN, 2.0));
        assertFalse(p.shown);
        assertEquals(PanelScale.MAX, p.scale, 1e-9);
        assertNull(p.x);
        assertEquals(1.0, p.y, 1e-9);
        PanelLayout.Place d = PanelLayout.sanitize(null);
        assertTrue(d.shown);
        assertEquals(PanelScale.DEFAULT, d.scale, 1e-9);
        assertFalse(d.moved());
    }

    @Test
    void migrationKeepsTheOldPanelScale() {
        Map<String, PanelLayout.Place> m = PanelLayout.migrate(null, 0.7);
        assertEquals(0.7, PanelLayout.get(m, PanelLayout.DEAL).scale, 1e-9);
        assertFalse(PanelLayout.get(m, PanelLayout.DEAL).moved());
        // Out of range: clamped.
        assertEquals(PanelScale.MIN, PanelLayout.get(PanelLayout.migrate(null, 0.1), PanelLayout.DEAL).scale, 1e-9);
        // Own settings win over the old value.
        Map<String, PanelLayout.Place> own = new LinkedHashMap<>();
        own.put(PanelLayout.DEAL, new PanelLayout.Place(true, 1.25, 0.9, 0.1));
        assertEquals(1.25, PanelLayout.get(PanelLayout.migrate(own, 0.7), PanelLayout.DEAL).scale, 1e-9);
        // Nothing stored: defaults, nothing added.
        assertTrue(PanelLayout.migrate(null, null).isEmpty());
        Map<String, PanelLayout.Place> broken = new HashMap<>();
        broken.put(PanelLayout.DEAL, null);
        broken.put(null, new PanelLayout.Place());
        assertTrue(PanelLayout.migrate(broken, null).isEmpty());
    }

    /** The config fields involved, as the config file is read and written. */
    static final class Cfg {
        Map<String, PanelLayout.Place> panels = new LinkedHashMap<>();
        Double panelScale = null;
        boolean enabled = true;
    }

    @Test
    void configFilesLoadAndSave() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        // A released 0.3.2 config: no panel settings at all.
        Cfg old = gson.fromJson("{\"enabled\": true, \"minProfit\": 1000000, \"bzKeysMode\": \"on\"}", Cfg.class);
        Map<String, PanelLayout.Place> m = PanelLayout.migrate(old.panels, old.panelScale);
        assertTrue(m.isEmpty());
        assertTrue(PanelLayout.get(m, PanelLayout.DEAL).shown);
        // A test build config with the single panel scale and the bazaar keys.
        Cfg test = gson.fromJson("{\"panelScale\": 0.9, \"bzCoachHidden\": true, \"bzIgnored\": [\"X\"]}", Cfg.class);
        m = PanelLayout.migrate(test.panels, test.panelScale);
        assertEquals(0.9, PanelLayout.get(m, PanelLayout.DEAL).scale, 1e-9);
        // Written back: the panels map, no panelScale, no place for a panel never moved.
        test.panels = m;
        test.panelScale = null;
        String json = gson.toJson(test);
        assertFalse(json.contains("panelScale"), json);
        assertFalse(json.contains("\"x\""), json);
        Cfg again = gson.fromJson(json, Cfg.class);
        assertEquals(0.9, PanelLayout.get(again.panels, PanelLayout.DEAL).scale, 1e-9);
        // A moved, hidden panel survives the round trip.
        PanelLayout.Place p = PanelLayout.edit(again.panels, PanelLayout.DEAL);
        p.shown = false;
        PanelLayout.moveTo(p, 10, 20, 640, 360, 150, 110);
        Cfg back = gson.fromJson(gson.toJson(again), Cfg.class);
        PanelLayout.Place q = PanelLayout.get(back.panels, PanelLayout.DEAL);
        assertFalse(q.shown);
        assertArrayEquals(new int[] {10, 20}, PanelLayout.place(q, 640, 360, 150, 110, 0, 0));
        assertTrue(PanelLayout.describe(back.panels).startsWith("deal hidden 0.90 x "), PanelLayout.describe(back.panels));
    }
}
