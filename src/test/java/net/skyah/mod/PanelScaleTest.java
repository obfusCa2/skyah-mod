package net.skyah.mod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A panel's size: clamped setting, drawn on whole pixels, resized one pixel step at a time. */
class PanelScaleTest {
    @Test
    void settingIsClamped() {
        assertEquals(0.8, PanelScale.clamp(0.8), 1e-9);
        assertEquals(0.6, PanelScale.clamp(0.1), 1e-9);
        assertEquals(1.5, PanelScale.clamp(3.0), 1e-9);
        assertEquals(PanelScale.DEFAULT, PanelScale.clamp(Double.NaN), 1e-9);
        assertEquals(PanelScale.DEFAULT, PanelScale.clamp(Double.POSITIVE_INFINITY), 1e-9);
    }

    @Test
    void drawnOnWholePixels() {
        assertEquals(0.75f, PanelScale.effective(0.8, 4), 1e-6);   // 3 screen pixels per panel pixel
        assertEquals(2f / 3f, PanelScale.effective(0.8, 3), 1e-6);
        assertEquals(1.0f, PanelScale.effective(0.8, 2), 1e-6);    // 0.5 would be below the minimum
        assertEquals(1.0f, PanelScale.effective(0.8, 1), 1e-6);
        assertEquals(0.8f, PanelScale.effective(0.8, 5), 1e-6);
        assertEquals(5f / 6f, PanelScale.effective(0.8, 6), 1e-6);
        assertEquals(1.0f, PanelScale.effective(1.0, 4), 1e-6);
        assertEquals(0.75f, PanelScale.effective(0.6, 4), 1e-6);   // 2/4 is below 0.6: the next step up
        assertEquals(0.8f, PanelScale.effective(0.8, 0), 1e-6);    // GUI scale unknown: the setting
        assertEquals(0.6f, PanelScale.effective(0.6, 5), 1e-6);
        assertEquals(1.5f, PanelScale.effective(1.5, 2), 1e-6);    // 3 screen pixels per panel pixel
        assertEquals(4f / 3f, PanelScale.effective(1.5, 3), 1e-6); // 4.5 rounds to 5 > 1.5 * 3: capped
        assertEquals(1.0f, PanelScale.effective(1.5, 1), 1e-6);    // GUI scale 1 has only 1.0
    }

    @Test
    void nudgeStepsByWholePixelsWithoutWrap() {
        // GUI scale 4: 0.75, 1.0, 1.25, 1.5.
        assertEquals(1.0, PanelScale.nudge(0.8, 1, 4), 1e-9);
        assertEquals(1.25, PanelScale.nudge(1.0, 1, 4), 1e-9);
        assertEquals(1.5, PanelScale.nudge(1.5, 1, 4), 1e-9);
        assertEquals(0.75, PanelScale.nudge(0.75, -1, 4), 1e-9);
        // GUI scale 3: 2/3, 1, 4/3.
        assertEquals(1.0, PanelScale.nudge(0.8, 1, 3), 1e-9);
        assertEquals(2.0 / 3.0, PanelScale.nudge(1.0, -1, 3), 1e-9);
        // GUI scale 2: 1.0 and 1.5 only.
        assertEquals(1.5, PanelScale.nudge(0.8, 1, 2), 1e-9);
        assertEquals(1.0, PanelScale.nudge(1.5, -1, 2), 1e-9);
        // Unknown GUI scale: tenths.
        assertEquals(0.9, PanelScale.nudge(0.8, 1, 0), 1e-9);
        assertEquals(0.6, PanelScale.nudge(0.6, -1, 0), 1e-9);
        // Every nudge result is drawn as it is (no hidden rounding).
        for (int gui = 1; gui <= 6; gui++) {
            for (int dir : new int[] {-1, 1}) {
                double s = PanelScale.nudge(0.8, dir, gui);
                assertEquals(s, PanelScale.effective(s, gui), 1e-6, "gui " + gui);
            }
        }
    }

    @Test
    void canNudgeSaysWhenTheEndIsReached() {
        assertFalse(PanelScale.canNudge(0.8, -1, 2));
        assertTrue(PanelScale.canNudge(0.8, 1, 2));
        assertFalse(PanelScale.canNudge(1.0, 1, 1));
        assertFalse(PanelScale.canNudge(1.0, -1, 1));
        assertFalse(PanelScale.canNudge(0.8, -1, 3));   // 2/3 is the smallest at GUI scale 3
        assertTrue(PanelScale.canNudge(1.0, -1, 3));
        assertEquals("67%", PanelScale.percent(2f / 3f));
        assertEquals("100%", PanelScale.percent(1f));
    }
}
