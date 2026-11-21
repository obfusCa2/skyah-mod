package net.skyah.mod;

import java.util.ArrayList;
import java.util.List;

/** Where the rows of the SkyAH menu pages go, free of game classes so it can be tested: every page must
 *  fit without scrolling down to the smallest GUI (320 x 240, and 427 x 240 at GUI scale 3 on 1280 x 720).
 *  All values are GUI pixels from the top; bottom() is the lowest pixel a page draws. */
final class MenuLayout {
    /** The first row under the title. */
    static final int TOP = 24;
    /** Space kept free under the last row. */
    static final int MARGIN = 2;
    /** One line of text, and one line of a wrapped paragraph. */
    static final int LINE = 10;

    private MenuLayout() {
    }

    /** Button height: 18 on short screens, 20 from 300 on (as the settings screens always did). */
    static int buttonH(int height) {
        return height < 300 ? 18 : 20;
    }

    /** One button row plus the space to the next. */
    static int gap(int height) {
        return buttonH(height) + (height < 300 ? 3 : 4);
    }

    /** The column width for a screen this wide. */
    static int width(int screenWidth) {
        return Math.min(400, screenWidth - 32);
    }

    /** True when a page whose lowest pixel is bottom fits on a screen this high. */
    static boolean fits(int bottom, int height) {
        return bottom <= height - MARGIN;
    }

    /* ── first page ── */

    /** status: the status line; problems: the y of each problem line (a button or a text line);
     *  allSet: the "all set" line (-1 with problems); flips, last: the button rows; flipsLine: the gray
     *  line under the flips row. */
    record Home(int status, int allSet, List<Integer> problems, int flips, int flipsLine, int last, int bottom) {
    }

    /** problemButtons: per problem line, true when it is a button (it has a fix). */
    static Home home(int height, List<Boolean> problemButtons) {
        int h = buttonH(height);
        int y = TOP;
        int status = y;
        y += 14;
        int allSet = -1;
        List<Integer> problems = new ArrayList<>();
        if (problemButtons.isEmpty()) {
            allSet = y;
            y += 14;
        }
        for (boolean button : problemButtons) {
            problems.add(y);
            y += button ? gap(height) : 14;
        }
        y += 6;
        int flips = y;
        y += h + 3;
        int flipsLine = y;
        y += 15;
        return new Home(status, allSet, problems, flips, flipsLine, y, y + h);
    }

    /* ── Auction flips ── */

    /** rows: the four rows of two buttons; hint and risk: the two text lines; last: the Done row. */
    record Flips(int coverage, int[] rows, int hint, int risk, int last, int bottom) {
    }

    static Flips flips(int height) {
        int h = buttonH(height);
        int g = gap(height);
        int y = TOP + 16;
        int[] rows = {y, y + g, y + 2 * g, y + 3 * g};
        y = rows[3] + h + 4;
        int hint = y;
        y += 12;
        int risk = y;
        y += 16;
        return new Flips(TOP, rows, hint, risk, y, y + h);
    }

    /** rows: three rows of two buttons, the help paragraph under the second, last: the third row. */
    record FlipsMore(int[] rows, int help, int last, int bottom) {
    }

    static FlipsMore flipsMore(int height, int helpLines) {
        int h = buttonH(height);
        int g = gap(height);
        int first = TOP;
        int second = TOP + g;
        int help = second + h + 6;
        int last = help + helpLines * LINE + 6;
        return new FlipsMore(new int[] {first, second}, help, last, last + h);
    }

    /* ── Settings ── */

    /** status and msg lines; activate, paste, website: the buttons (-1 when not shown); hint: the
     *  paragraph; last: the row of Panels and Done; keys: the key paragraph under it. */
    record Settings(int status, int msg, int activate, int paste, int website, int hint, int last, int keys,
                    int bottom) {
    }

    static Settings settings(int height, boolean activate, int hintLines, int keysLines) {
        int h = buttonH(height);
        int g = gap(height);
        int y = TOP;
        int status = y;
        y += 13;
        int msg = y;
        y += 14;
        int act = -1;
        if (activate) {
            act = y;
            y += g;
        }
        int paste = y;
        y += g;
        int website = -1;
        if (!activate) {
            website = y;
            y += g;
        }
        y += 2;
        int hint = y;
        y += hintLines * LINE + 8;
        int last = y;
        y += g + 2;
        int keys = y;
        return new Settings(status, msg, act, paste, website, hint, last, keys,
                Math.max(last + h, keys + keysLines * LINE - 2));
    }
}
