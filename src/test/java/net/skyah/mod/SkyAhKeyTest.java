package net.skyah.mod;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyAhKeyTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final long EVENING = ZonedDateTime.of(2026, 10, 12, 21, 45, 0, 0, BERLIN).toInstant().toEpochMilli();
    private static final long MIN = 60_000L;

    @Test
    void untilTextUsesThePlayersClock() {
        assertEquals("21:45", SkyAhKey.untilText(EVENING, BERLIN, Locale.GERMANY));
        assertEquals("15:45", SkyAhKey.untilText(EVENING, ZoneId.of("America/New_York"), Locale.GERMANY));
        assertEquals("9:45 PM", SkyAhKey.untilText(EVENING, BERLIN, Locale.US).replace(' ', ' '));
    }

    @Test
    void windowTextNamesAnyLength() {
        assertEquals("2 hours", SkyAhKey.windowText(120 * MIN));
        assertEquals("1 hour", SkyAhKey.windowText(60 * MIN));
        assertEquals("90 minutes", SkyAhKey.windowText(90 * MIN));
        assertEquals("1 minute", SkyAhKey.windowText(30_000L));
    }

    @Test
    void warningScalesWithShortWindows() {
        assertEquals(10 * MIN, SkyAhKey.warnMs(0L));
        assertEquals(10 * MIN, SkyAhKey.warnMs(120 * MIN));
        assertEquals(10 * MIN, SkyAhKey.warnMs(24 * 60 * MIN));
        assertEquals(5 * MIN, SkyAhKey.warnMs(20 * MIN));
    }

    @Test
    void renewalIsAnyLaterEnd() {
        long now = EVENING;
        assertTrue(SkyAhKey.isRenewal(now - 5 * MIN, now + 120 * MIN));   // after it ran out
        assertTrue(SkyAhKey.isRenewal(now + 50 * MIN, now + 120 * MIN));  // renewed early
        assertTrue(SkyAhKey.isRenewal(now + 2 * MIN, now + 30 * MIN));    // short window
        assertFalse(SkyAhKey.isRenewal(0L, now + 120 * MIN));             // first state after connect
        assertFalse(SkyAhKey.isRenewal(now + 60 * MIN, now + 60 * MIN));  // same window again
        assertFalse(SkyAhKey.isRenewal(now + 60 * MIN, Long.MAX_VALUE));  // made permanent
        assertFalse(SkyAhKey.isRenewal(now + 60 * MIN, 0L));              // key gone
    }

    @Test
    void tipTakesTheServerWindow() {
        assertTrue(SkyAhKey.activateTip().contains("unlocks deals for a limited time."));
        SkyAhKey.update("{\"type\":\"key\",\"active_until_ms\":" + EVENING + ",\"window_s\":10800}");
        assertTrue(SkyAhKey.activateTip().contains("unlocks deals for 3 hours."));
        SkyAhKey.update("{\"type\":\"key\",\"active_until_ms\":" + EVENING + "}");
        assertTrue(SkyAhKey.activateTip().contains("unlocks deals for 3 hours."));
        SkyAhKey.reset();
    }
}
