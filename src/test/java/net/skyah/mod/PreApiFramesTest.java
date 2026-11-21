package net.skyah.mod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Free feed url, free and pre-API deal frames, and how long a locked deal is waited for. */
class PreApiFramesTest {
    private static final long T0 = 1_763_000_000_000L;

    @Test
    void freeFeedWithoutKey() {
        String keyed = "wss://skyah.net/item-api/ws/v1/flips";
        assertEquals("wss://skyah.net/item-api/ws/v1/free", FreeMode.feedUrl(keyed, false, false));
        assertEquals(keyed, FreeMode.feedUrl(keyed, true, false));
        assertEquals("ws://localhost:8092/ws/v1/free?x=1", FreeMode.feedUrl("ws://localhost:8092/ws/v1/flips?x=1", false, false));
        // A custom url is used as it is.
        assertEquals("wss://example.org/ws/test/flips", FreeMode.feedUrl("wss://example.org/ws/test/flips", false, false));
        assertNull(FreeMode.feedUrl(null, false, false));
    }

    @Test
    void dealsOffUsesTheFreeFeedWithoutDeals() {
        String keyed = "wss://skyah.net/item-api/ws/v1/flips";
        assertEquals("wss://skyah.net/item-api/ws/v1/free?deals=off", FreeMode.feedUrl(keyed, false, true));
        assertEquals("ws://localhost:8092/ws/v1/free?x=1&deals=off",
                FreeMode.feedUrl("ws://localhost:8092/ws/v1/flips?x=1", false, true));
    }

    @Test
    void keyedFeedOnlyWithAKeyDealsOnAndNotRefused() {
        assertTrue(FreeMode.keyedFeed(true, true, false));
        assertFalse(FreeMode.keyedFeed(false, true, false)); // no key: free feed
        assertFalse(FreeMode.keyedFeed(true, false, false)); // deals off: free feed without deals
        assertFalse(FreeMode.keyedFeed(true, true, true));   // inactive or not accepted: free feed
    }

    @Test
    void delayText() {
        assertEquals("60s", FreeMode.delayText(60));
        assertEquals("45s", FreeMode.delayText(45));
        assertEquals("2m", FreeMode.delayText(120));
        assertEquals("90s", FreeMode.delayText(90));
        assertEquals("2m 30s", FreeMode.delayText(150));
    }

    @Test
    void controlFramesByType() {
        assertEquals("free", FlipsSocket.frameType("{\"type\":\"free\",\"delay_s\":60,\"key_url\":\"https://skyah.net/api#api-keys\"}"));
        assertEquals("ah_on", FlipsSocket.frameType("{\"type\": \"ah_on\"}"));
        assertEquals("key", FlipsSocket.frameType("{\"type\":\"key\",\"permanent\":true}"));
        assertNull(FlipsSocket.frameType("{\"auction_id\":\"abc\",\"name\":\"Say \\\"type\\\" here\",\"price\":5}"));
        assertNull(FlipsSocket.frameType("not json \"type\""));
        assertNull(FlipsSocket.frameType(null));
        // Frames for other builds of the mod are dropped before the deal parser.
        assertTrue(FlipsSocket.ignored(FlipsSocket.frameType("{\"type\":\"bz_coach\",\"v\":1,\"orders\":[]}")));
        assertTrue(FlipsSocket.ignored(FlipsSocket.frameType("{\"type\":\"dbg_on\",\"v\":1}")));
        assertFalse(FlipsSocket.ignored("bz_on"));
        assertFalse(FlipsSocket.ignored(null));
        // Control frames are never deals.
        assertNull(Flip.parse("{\"type\":\"free\",\"delay_s\":60,\"key_url\":\"https://skyah.net/api#api-keys\"}"));
        assertNull(Flip.parse("{\"type\":\"ah_on\"}"));
    }

    @Test
    void preApiFields() {
        Flip f = Flip.parse("{\"auction_id\":\"a1\",\"name\":\"Hyperion\",\"price\":900000000,\"bin\":true,"
                + "\"pre_api\":true,\"buyable_at_ms\":" + (T0 + 17_000) + ",\"timing\":{\"sent_ms\":" + T0 + "}}");
        assertNotNull(f);
        assertTrue(f.preApi);
        assertEquals(T0 + 17_000, f.buyableAtMs);
        // This computer's clock runs 2 s ahead of the server: the buy time moves with it.
        f.receivedAt = T0 + 2_000;
        assertEquals(T0 + 19_000, f.buyableAtLocal());
        assertTrue(f.locked(T0 + 18_000));
        assertFalse(f.locked(T0 + 19_000));
        assertFalse(f.free);
    }

    @Test
    void freeFields() {
        Flip f = Flip.parse("{\"auction_id\":\"a1\",\"name\":\"Hyperion\",\"price\":5,\"free\":true,\"delayed_s\":60,\"replay\":true}");
        assertNotNull(f);
        assertTrue(f.free);
        assertEquals(60L, f.delayedS);
        assertTrue(f.replay);
        assertFalse(f.preApi);
        assertEquals(0L, f.buyableAtLocal());
        assertFalse(f.locked(T0));
    }

    @Test
    void oddOrUnknownFieldsNeverDropADeal() {
        Flip f = Flip.parse("{\"auction_id\":\"a1\",\"name\":\"Hyperion\",\"price\":5,\"pre_api\":\"yes\","
                + "\"buyable_at_ms\":\"soon\",\"free\":1,\"delayed_s\":{\"x\":1},\"something_new\":[1,2,{\"a\":null}],"
                + "\"another\":{\"nested\":true}}");
        assertNotNull(f);
        assertEquals("a1", f.auctionId);
        assertFalse(f.preApi);
        assertEquals(0L, f.buyableAtMs);
        assertFalse(f.free);
        assertEquals(0L, f.delayedS);
        // A buy time that is no epoch in ms is ignored.
        Flip g = Flip.parse("{\"auction_id\":\"a2\",\"pre_api\":true,\"buyable_at_ms\":1763000000}");
        assertNotNull(g);
        assertEquals(0L, g.buyableAtMs);
        assertNull(Flip.parse("[1,2]"));
        assertNull(Flip.parse("{\"price\":5}"));
    }

    private static Flip preApi(long buyableAt) {
        Flip f = new Flip();
        f.auctionId = "a1";
        f.name = "Hyperion";
        f.bin = true;
        f.preApi = true;
        f.buyableAtMs = buyableAt;
        return f;
    }

    @Test
    void holdWaitsForTheBuyTime() {
        Flip plain = new Flip();
        assertEquals(T0 + 3_000, SkyAhHold.viewDeadline(plain, T0));
        assertEquals(T0 + 3_000, SkyAhHold.viewDeadline(null, T0));
        // Buyable in 15 s: no view until then is no reason to move on.
        assertEquals(T0 + 16_500, SkyAhHold.viewDeadline(preApi(T0 + 15_000), T0));
        // Already buyable: the normal 3 s.
        assertEquals(T0 + 3_000, SkyAhHold.viewDeadline(preApi(T0 - 5_000), T0));
        // A buy time far off (clock trouble) is waited for 30 s at most.
        assertEquals(T0 + 31_500, SkyAhHold.viewDeadline(preApi(T0 + 600_000), T0));
        // Only pre-API deals wait.
        Flip notPre = preApi(T0 + 15_000);
        notPre.preApi = false;
        assertEquals(T0 + 3_000, SkyAhHold.viewDeadline(notPre, T0));
    }

    @Test
    void viewWithoutButtonIsUnreadableOnlyAfterTheBuyTime() {
        assertEquals(T0 + 1_500, SkyAhOpenTracker.unreadableAfter(new Flip(), T0, T0 - 500));
        assertEquals(T0 + 16_500, SkyAhOpenTracker.unreadableAfter(preApi(T0 + 15_000), T0, T0 - 500));
        assertEquals(T0 + 1_500, SkyAhOpenTracker.unreadableAfter(preApi(T0 - 1_000), T0, T0 - 500));
        assertEquals(T0 + 31_000, SkyAhOpenTracker.unreadableAfter(preApi(T0 + 600_000), T0, T0 - 500));
    }

    @Test
    void lockCountdownText() {
        assertEquals("14s", SkyAhPanel.lockText(13_200L));
        assertEquals("1s", SkyAhPanel.lockText(1_000L));
        assertEquals("soon", SkyAhPanel.lockText(400L));
        assertEquals("soon", SkyAhPanel.lockText(0L));
        assertEquals("soon", SkyAhPanel.lockText(-1L));
    }

    @Test
    void lockLeftFromTheDealWhileNotOpen() {
        Flip f = preApi(T0 + 15_000);
        assertEquals(10_000L, SkyAhOpenTracker.lockLeftMs(f, T0 + 5_000));
        assertEquals(-1L, SkyAhOpenTracker.lockLeftMs(f, T0 + 15_000));
        assertEquals(-1L, SkyAhOpenTracker.lockLeftMs(new Flip(), T0));
        assertEquals(-1L, SkyAhOpenTracker.lockLeftMs(null, T0));
    }
}
