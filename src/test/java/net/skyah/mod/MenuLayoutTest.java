package net.skyah.mod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every menu page fits without scrolling down to the smallest GUI: 320 x 240, and 427 x 240 (GUI scale 3
 *  on 1280 x 720). Worst cases: the most lines and rows each page can have. */
class MenuLayoutTest {
    private static final int[] HEIGHTS = {240, 270, 360};

    private static void fits(String page, int bottom, int height) {
        assertTrue(MenuLayout.fits(bottom, height), page + " ends at " + bottom + " on a screen " + height + " high");
    }

    @Test
    void sizesAsTheScreensAlwaysUsed() {
        assertEquals(18, MenuLayout.buttonH(240));
        assertEquals(21, MenuLayout.gap(240));
        assertEquals(20, MenuLayout.buttonH(300));
        assertEquals(24, MenuLayout.gap(300));
        assertEquals(395, MenuLayout.width(427));
        assertEquals(288, MenuLayout.width(320));
        assertEquals(400, MenuLayout.width(854));
    }

    @Test
    void homeFitsWithThreeProblemButtons() {
        for (int h : HEIGHTS) {
            fits("home", MenuLayout.home(h, List.of(true, true, true)).bottom(), h);
            fits("home", MenuLayout.home(h, List.of(false, true, false)).bottom(), h);
            fits("home", MenuLayout.home(h, List.of()).bottom(), h);
        }
        MenuLayout.Home none = MenuLayout.home(240, List.of());
        assertTrue(none.allSet() > 0);
        assertTrue(none.problems().isEmpty());
        MenuLayout.Home one = MenuLayout.home(240, List.of(true));
        assertEquals(-1, one.allSet());
        assertEquals(one.flipsLine() + 15, one.last());
    }

    @Test
    void flipsPagesFit() {
        for (int h : HEIGHTS) {
            fits("flips", MenuLayout.flips(h).bottom(), h);
            // The help paragraph: two lines at 427, three at 320; four to be safe.
            fits("flips more", MenuLayout.flipsMore(h, 4).bottom(), h);
        }
    }

    @Test
    void settingsFits() {
        for (int h : HEIGHTS) {
            fits("settings", MenuLayout.settings(h, true, 3, 3).bottom(), h);
            fits("settings", MenuLayout.settings(h, false, 3, 3).bottom(), h);
        }
        MenuLayout.Settings s = MenuLayout.settings(240, false, 1, 1);
        assertEquals(-1, s.activate());
        assertTrue(s.website() > s.paste());
    }
}
