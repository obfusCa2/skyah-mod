package net.skyah.mod;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyAhHoldTest {
    private static final long NOW = 1_800_000_000_000L;

    private static Flip deal(String id, long profit, long price, long ageMs) {
        Flip f = new Flip();
        f.auctionId = id;
        f.name = id;
        f.bin = true;
        f.expectedProfit = profit;
        f.price = price;
        f.receivedAt = NOW - ageMs;
        f.shownAt = f.receivedAt;
        return f;
    }

    private static Flip best(Flip... flips) {
        return SkyAhHold.best(List.of(flips), NOW, f -> f.expectedProfit);
    }

    @Test
    void moreProfitIsWorthMoreEvenThoughItSellsFaster() {
        long prev = 0;
        double prevScore = -1;
        for (long p = 100_000L; p <= 2_000_000_000L; p += Math.max(100_000L, prev / 10)) {
            double s = SkyAhHold.score(p, 0L);
            assertTrue(s > prevScore, "score must grow with profit at " + p);
            prevScore = s;
            prev = p;
        }
    }

    @Test
    void chanceFallsWithProfitAndAgeAndStaysInRange() {
        assertEquals(0.73, SkyAhHold.chance(1_000_000L, 0L), 1e-9);
        assertTrue(SkyAhHold.chance(15_000_000L, 0L) < SkyAhHold.chance(3_000_000L, 0L));
        assertEquals(0.16, SkyAhHold.chance(500_000_000L, 0L), 1e-9);
        assertTrue(SkyAhHold.chance(5_000_000L, 60_000L) < SkyAhHold.chance(5_000_000L, 1_000L));
        assertEquals(0.73 * 0.65, SkyAhHold.chance(1_000_000L, 3_600_000L), 1e-9);
    }

    @Test
    void picksTheBestUnopenedBinDeal() {
        Flip small = deal("a", 2_000_000L, 10_000_000L, 1_000L);
        Flip big = deal("b", 20_000_000L, 50_000_000L, 30_000L);
        assertSame(big, best(small, big));
        big.opened = true;
        assertSame(small, best(small, big));
        small.bin = false;
        assertNull(best(small, big));
    }

    @Test
    void skipsTestDeals() {
        Flip test = deal("test", 50_000_000L, 1L, 0L);
        Flip cheap = deal("d", 3_000_000L, 20_000_000L, 0L);
        assertSame(cheap, best(test, cheap));
        assertNull(best(test));
    }

    @Test
    void sameProfitFresherWins() {
        Flip old = deal("old", 5_000_000L, 20_000_000L, 90_000L);
        Flip fresh = deal("new", 5_000_000L, 20_000_000L, 2_000L);
        assertSame(fresh, best(old, fresh));
    }

    @Test
    void jitterStaysWithin25PercentAndVaries() {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            long v = SkyAhHold.jitter(100L);
            assertTrue(v >= 75L && v <= 125L, "jitter out of range: " + v);
            seen.add(v);
        }
        assertTrue(seen.size() > 20);
    }

    @Test
    void nearestStepForHandEditedValues() {
        assertEquals(300L, SkyAhHold.nearest(310L, SkyAhHold.AFTER_STEPS));
        assertEquals(150L, SkyAhHold.nearest(0L, SkyAhHold.AFTER_STEPS));
        assertEquals(1000L, SkyAhHold.nearest(99_999L, SkyAhHold.AFTER_STEPS));
        assertEquals(100L, SkyAhHold.nearest(-5L, SkyAhHold.DELAY_STEPS));
    }
}
