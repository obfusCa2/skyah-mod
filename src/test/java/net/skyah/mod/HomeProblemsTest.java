package net.skyah.mod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The problem lines of the menu's first page: order, the fix of each, at most three. */
class HomeProblemsTest {
    private static HomeProblems.State state(HomeProblems.Conn conn, boolean keyDue, int kept, String hotkey,
                                            String takenBy, String latest) {
        return new HomeProblems.State(true, conn, keyDue, 60, true, kept, 200, hotkey, takenBy, "0.3.2", latest);
    }

    private static List<HomeProblems.Kind> kinds(List<HomeProblems.Problem> p) {
        return p.stream().map(HomeProblems.Problem::kind).toList();
    }

    @Test
    void allSetHasNoLines() {
        assertTrue(HomeProblems.all(state(HomeProblems.Conn.OPEN, false, 30, "R", null, "0.3.2")).isEmpty());
    }

    @Test
    void worstFirstAndAtMostThree() {
        HomeProblems.State s = state(HomeProblems.Conn.FREE, false, 0, null, null, "0.3.3");
        assertEquals(List.of(HomeProblems.Kind.FREE, HomeProblems.Kind.FILTERS_EMPTY, HomeProblems.Kind.NO_HOTKEY,
                HomeProblems.Kind.UPDATE), kinds(HomeProblems.all(s)));
        List<HomeProblems.Problem> shown = HomeProblems.shown(s);
        assertEquals(HomeProblems.MAX, shown.size());
        assertEquals(HomeProblems.Fix.WEBSITE, shown.get(0).fix());
        assertEquals(HomeProblems.Fix.FILTERS, shown.get(1).fix());
        assertEquals(HomeProblems.Fix.HOTKEY_DEFAULT, shown.get(2).fix());
        assertEquals("No deal hotkey yet: click to use B", shown.get(2).text());
    }

    @Test
    void eachConnectionHasItsLine() {
        assertEquals(List.of(HomeProblems.Kind.KEY_INACTIVE),
                kinds(HomeProblems.all(state(HomeProblems.Conn.INACTIVE, false, 30, "R", null, null))));
        assertEquals(List.of(HomeProblems.Kind.KEY_DUE),
                kinds(HomeProblems.all(state(HomeProblems.Conn.OPEN, true, 30, "R", null, null))));
        assertEquals(HomeProblems.Fix.SETTINGS,
                HomeProblems.all(state(HomeProblems.Conn.REJECTED, false, 30, "R", null, null)).get(0).fix());
        assertEquals(HomeProblems.Fix.NONE,
                HomeProblems.all(state(HomeProblems.Conn.ELSEWHERE, false, 30, "R", null, null)).get(0).fix());
        assertTrue(HomeProblems.all(state(HomeProblems.Conn.DOWN, false, 30, "R", null, null)).isEmpty());
        assertEquals(List.of(HomeProblems.Kind.FREE_CLOSED),
                kinds(HomeProblems.all(state(HomeProblems.Conn.FREE_CLOSED, false, 30, "R", null, null))));
    }

    @Test
    void takenHotkeyNamesBoth() {
        HomeProblems.Problem p = HomeProblems.all(state(HomeProblems.Conn.OPEN, false, 30, "Q", "Drop Selected Item",
                null)).get(0);
        assertEquals(HomeProblems.Kind.HOTKEY_TAKEN, p.kind());
        assertEquals("Deal hotkey Q is also Drop Selected Item: pick another", p.text());
        assertEquals(HomeProblems.Fix.HOTKEY_PICK, p.fix());
    }

    @Test
    void noLineNamesTheBazaar() {
        for (HomeProblems.Conn c : HomeProblems.Conn.values()) {
            for (HomeProblems.Problem p : HomeProblems.all(state(c, true, 0, null, null, "0.4.0"))) {
                assertTrue(!p.text().toLowerCase().contains("bazaar"), c + ": " + p.text());
            }
        }
    }

    @Test
    void dealsOffKeepsOnlyConnectionLines() {
        HomeProblems.State s = new HomeProblems.State(true, HomeProblems.Conn.INACTIVE, false, 60, false, 0, 200, null,
                null, "0.3.2", null);
        assertEquals(List.of(HomeProblems.Kind.KEY_INACTIVE), kinds(HomeProblems.all(s)));
        HomeProblems.State off = new HomeProblems.State(false, HomeProblems.Conn.INACTIVE, false, 60, false, 0, 200,
                null, null, "0.3.2", null);
        assertTrue(HomeProblems.all(off).isEmpty());
    }
}
