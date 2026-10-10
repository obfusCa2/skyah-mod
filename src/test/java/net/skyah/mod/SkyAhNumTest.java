package net.skyah.mod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyAhNumTest {
    @Test
    void parsesCoins() {
        assertEquals(5_000_000L, SkyAhNum.parseCoins("5m", -1));
        assertEquals(500_000L, SkyAhNum.parseCoins("500k", -1));
        assertEquals(1_500_000_000L, SkyAhNum.parseCoins("1.5b", -1));
        assertEquals(2_000_000L, SkyAhNum.parseCoins("2,000,000", -1));
        assertEquals(3_500_000L, SkyAhNum.parseCoins("3500000", -1));
        assertEquals(0L, SkyAhNum.parseCoins("", -1));
        assertEquals(42L, SkyAhNum.parseCoins("abc", 42L));
        assertEquals(42L, SkyAhNum.parseCoins("-5m", 42L));
    }

    @Test
    void parsesPercent() {
        assertEquals(15.0, SkyAhNum.parsePct("15", -1));
        assertEquals(12.5, SkyAhNum.parsePct("12.5%", -1));
        assertEquals(0.0, SkyAhNum.parsePct("", -1));
        assertEquals(7.0, SkyAhNum.parsePct("x", 7.0));
    }

    @Test
    void editBoxValues() {
        assertEquals("", SkyAhNum.editCoins(0L));
        assertEquals("1.5m", SkyAhNum.editCoins(1_500_000L));
        assertEquals("250k", SkyAhNum.editCoins(250_000L));
        assertEquals("1b", SkyAhNum.editCoins(1_000_000_000L));
        assertEquals("1.25b", SkyAhNum.editCoins(1_250_000_000L));
        assertEquals("1234567", SkyAhNum.editCoins(1_234_567L));
        assertEquals(2_750_000L, SkyAhNum.parseCoins(SkyAhNum.editCoins(2_750_000L), -1));
    }

    @Test
    void stepsWrapAround() {
        long[] steps = {0, 1_000_000L, 5_000_000L};
        assertEquals(5_000_000L, SkyAhNum.nextStep(3_000_000L, steps));
        assertEquals(0L, SkyAhNum.nextStep(5_000_000L, steps));
        assertEquals(1_000_000L, SkyAhNum.prevStep(3_000_000L, steps));
        assertEquals(5_000_000L, SkyAhNum.prevStep(0L, steps));
        assertEquals(-1L, SkyAhNum.prevStep(0L, new long[]{-1L, 0L, 5L}));
    }

    @Test
    void chatNumbers() {
        assertEquals("3.23M", FlipChat.fmt(3_228_000L));
        assertEquals("22.4M", FlipChat.fmt(22_400_000L));
        assertEquals("540M", FlipChat.fmt(540_000_000L));
        assertEquals("1M", FlipChat.fmt(999_600L));
        assertEquals("950k", FlipChat.fmt(950_000L));
        assertEquals("1.25B", FlipChat.fmt(1_250_000_000L));
        assertEquals("+3.23M", FlipChat.signed(3_228_000L));
        assertEquals("-1.2M", FlipChat.signed(-1_200_000L));
        assertEquals("158", FlipChat.rate(158.0));
        assertEquals("6.3", FlipChat.rate(6.3));
        assertEquals("2", FlipChat.rate(2.0));
        assertEquals("0.6", FlipChat.rate(0.6));
        assertEquals("45s", FlipChat.duration(45));
        assertEquals("44m", FlipChat.duration(2_640));
        assertEquals("11.5h", FlipChat.duration(41_400));
        assertEquals("2h", FlipChat.duration(7_200));
        assertEquals("3d", FlipChat.duration(3 * 86_400 + 600));
    }

    @Test
    void readsPurseLine() {
        assertEquals(1_234_567L, SkyAhPurse.parse("Purse: 1,234,567"));
        assertEquals(1_234_567L, SkyAhPurse.parse("§6Purse: §61,234,567.5 §e(+50)"));
        assertEquals(3_000L, SkyAhPurse.parse("Piggy: 3,000"));
        assertEquals(-1L, SkyAhPurse.parse("Bank: 5,000"));
    }
}
