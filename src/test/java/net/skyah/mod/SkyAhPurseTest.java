package net.skyah.mod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyAhPurseTest {
    @Test
    void parsesPlainLines() {
        assertEquals(1_234_567L, SkyAhPurse.parse("Purse: 1,234,567"));
        assertEquals(1_234_567L, SkyAhPurse.parse("Purse: 1,234,567.8 (+50)"));
        assertEquals(98_765L, SkyAhPurse.parse("Piggy: 98,765"));
        assertEquals(-1L, SkyAhPurse.parse("Bits: 1,234"));
        assertEquals(-1L, SkyAhPurse.parse(null));
    }

    @Test
    void ignoresTheEntryNameBetweenPrefixAndSuffix() {
        // prefix "Purse: §6250,834" + entry name (emoji) + suffix ",112"
        assertEquals(250_834_112L, SkyAhPurse.parse("Purse: §6250,834🎂§6,112"));
        assertEquals(250_834_112L, SkyAhPurse.parse("Purse: 250,834⚽,112 (+5)"));
        assertEquals(251_000_000L, SkyAhPurse.parse("Pur👽se: 251,000,000"));
    }

    @Test
    void ignoresPlainTextBetweenTheDigitGroups() {
        // 234M was read as 234k: a space or the letter of a color code stayed between the groups.
        assertEquals(234_567_890L, SkyAhPurse.parse("Purse: 234,567 ,890"));
        assertEquals(234_567_890L, SkyAhPurse.parse("Purse: §6234,567§z,890 (+1,250)"));
        assertEquals(234_567_890L, SkyAhPurse.parse("Purse: 234,567🎂 ,890.4"));
        assertEquals(-1L, SkyAhPurse.parse("Purse: "));
        assertEquals(-1L, SkyAhPurse.parse("Purse: 1.2.3"));
    }

    @Test
    void escapesNonAsciiForTheLog() {
        assertEquals("Purse: 1\\u00a76,2", SkyAhPurse.escaped("Purse: 1§6,2"));
        assertEquals("", SkyAhPurse.escaped(null));
    }
}
