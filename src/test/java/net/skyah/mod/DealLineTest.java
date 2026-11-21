package net.skyah.mod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The deal line in both styles with every switch, and which style a config starts with. */
class DealLineTest {
    private static final DealLine.Show DEFAULTS = new DealLine.Show(true, true, true, true, true, false, false);
    private static final DealLine.Show ALL = new DealLine.Show(true, true, true, true, true, true, true);
    private static final DealLine.Show NONE = new DealLine.Show(false, false, false, false, false, false, false);

    private static String line(Flip f, DealLine.Show s, boolean classic) {
        long profit = f.realProfit;
        return DealLine.text(DealLine.parts(f, s, profit, FlipChat.pctOf(f, profit), classic), "{item}");
    }

    private static Flip bin() {
        return FlipChat.samples().get(0);
    }

    private static Flip auction() {
        return FlipChat.samples().get(1);
    }

    @Test
    void classicPutsTheItemFirst() {
        assertEquals("[SkyAH] {item} 18.5M -> 22.4M (+3.23M 17%) LBin: 23.1M Vol: 31", line(bin(), DEFAULTS, true));
        String p = FlipChat.signed(24_525_000L) + " " + FlipChat.pctText(FlipChat.pctOf(auction(), 24_525_000L));
        assertEquals("[SkyAH] {item} AUCTION 540M -> 610M (" + p + ") LBin: 585M Vol: 0.6", line(auction(), DEFAULTS, true));
        assertEquals("[SkyAH] {item} 18.5M -> 22.4M (+3.23M 17%) LBin: 23.1M Vol: 31 ~44m Fee: 672k",
                line(bin(), ALL, true));
        assertEquals("[SkyAH] {item} (+3.23M)", line(bin(), NONE, true));
        assertEquals("[SkyAH] {item} sell 22.4M (+3.23M 17%)",
                line(bin(), new DealLine.Show(false, true, true, false, false, false, false), true));
    }

    @Test
    void skyahLineIsUnchanged() {
        assertEquals("[SkyAH] +3.23M 17% {item} 18.5M → 22.4M floor 23.1M 31/d", line(bin(), DEFAULTS, false));
        String p = FlipChat.signed(24_525_000L) + " " + FlipChat.pctText(FlipChat.pctOf(auction(), 24_525_000L));
        assertEquals("[SkyAH] " + p + " AUCTION {item} 540M → 610M floor 585M 0.6/d", line(auction(), DEFAULTS, false));
        assertEquals("[SkyAH] +3.23M {item}", line(bin(), NONE, false));
        assertEquals("[SkyAH] +3.23M 17% {item} 18.5M → 22.4M floor 23.1M 31/d ~44m fee 672k", line(bin(), ALL, false));
    }

    @Test
    void colorsFollowTheSwitchesAndThePrice() {
        List<DealLine.Part> c = DealLine.parts(auction(), DEFAULTS, 24_525_000L, 4.5, true);
        assertTrue(c.stream().anyMatch(p -> p.text().equals("585M") && p.color().equals("red")));     // floor below target
        assertTrue(c.stream().anyMatch(p -> p.text().equals("0.6") && p.color().equals("yellow")));  // under 1 a day
        assertTrue(c.stream().anyMatch(p -> p.bold() && p.color().equals(DealLine.TIER)));
        assertEquals(1, c.stream().filter(DealLine.Part::title).count());
        List<DealLine.Part> s = DealLine.parts(auction(), DEFAULTS, 24_525_000L, 4.5, false);
        assertEquals(1, s.stream().filter(DealLine.Part::title).count());
        assertTrue(s.stream().anyMatch(p -> p.text().equals(" floor 585M") && p.color().equals("red")));
    }

    @Test
    void preApiLabelInBothStyles() {
        Flip f = bin();
        f.preApi = true;
        assertTrue(line(f, DEFAULTS, true).startsWith("[SkyAH] {item} PRE-API 18.5M"));
        assertTrue(line(f, DEFAULTS, false).startsWith("[SkyAH] +3.23M 17% PRE-API {item}"));
    }

    @Test
    void whichStyleAConfigUses() {
        assertTrue(DealLine.classic("classic"));
        assertTrue(DealLine.classic(" Classic "));
        assertFalse(DealLine.classic(null));
        assertFalse(DealLine.classic("skyah"));
        assertFalse(DealLine.classic("something else"));
        // A config file that was there keeps today's line (field absent); a fresh install starts classic.
        assertNull(DealLine.initialStyle(true));
        assertEquals(DealLine.CLASSIC, DealLine.initialStyle(false));
    }
}
