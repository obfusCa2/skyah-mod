package net.skyah.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BazaarRecorderTest {
    private static final long T0 = 1_763_000_000_000L;
    private final List<String> log = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();

    private BazaarRecorder recorder() {
        return new BazaarRecorder("abcd1234efgh5678", log::add);
    }

    private boolean send(String frame) {
        sent.add(frame);
        return true;
    }

    private static JsonArray events(String frame) {
        JsonObject o = JsonParser.parseString(frame).getAsJsonObject();
        assertEquals("bz", o.get("type").getAsString());
        assertEquals(1, o.get("v").getAsInt());
        assertEquals("abcd1234efgh5678", o.get("session").getAsString());
        return o.getAsJsonArray("events");
    }

    @Test
    void keepsOnlyBazaarAndProfileLines() {
        assertEquals(List.of("[Bazaar] Buy Order Setup! 64x Enchanted Coal for 9,619 coins."),
                BazaarRecorder.chatLines("§6[Bazaar] §7Buy Order Setup! §a64§7x Enchanted Coal for 9,619 coins."));
        assertEquals(List.of(), BazaarRecorder.chatLines("[MVP+] Someone: [Bazaar] Your Buy Order for 1x Enchanted Coal was filled!"));
        assertEquals(List.of(), BazaarRecorder.chatLines("Party > Someone: [Bazaar] Claimed 1 coins"));
        assertEquals(List.of(), BazaarRecorder.chatLines("You bought Hyperion for 1,000 coins!"));
        assertEquals(List.of("Profile ID: 0123456789abcdef0123456789abcdef", "[Bazaar] Claiming orders..."),
                BazaarRecorder.chatLines("Profile ID: 0123456789abcdef0123456789abcdef\n[Bazaar] Claiming orders...\nother"));
        assertEquals(List.of(), BazaarRecorder.chatLines("Profile ID: not an id"));
        assertEquals(BazaarRecorder.LINE_MAX, BazaarRecorder.chatLines("[Bazaar] " + "x".repeat(900)).get(0).length());
    }

    @Test
    void recordsNothingUntilTheServerSwitchesItOn() {
        BazaarRecorder r = recorder();
        assertEquals(List.of(), r.chat("[Bazaar] Claiming order...", T0, 5));
        assertFalse(r.menu("Order options", List.of(), T0, 5));
        assertEquals(0, r.queued());
        assertEquals(0, r.flush(T0 + 10_000, this::send));
        assertTrue(sent.isEmpty());
    }

    @Test
    void sendsBatchesAtMostEveryFewSeconds() {
        BazaarRecorder r = recorder();
        r.serverOn();
        r.chat("Profile ID: 0123456789abcdef0123456789abcdef", T0, -1);
        r.chat("[Bazaar] Buy Order Setup! 64x Enchanted Coal for 9,619 coins.", T0 + 5, 1_000_000);
        assertEquals(2, r.flush(T0 + 10_000, this::send));
        JsonArray ev = events(sent.get(0));
        assertEquals(2, ev.size());
        JsonObject second = ev.get(1).getAsJsonObject();
        assertEquals(2, second.get("seq").getAsInt());
        assertEquals("chat", second.get("k").getAsString());
        assertEquals(T0 + 5, second.get("t").getAsLong());
        assertEquals(1_000_000L, second.get("purse").getAsLong());
        assertEquals("0123456789abcdef0123456789abcdef", second.get("profile").getAsString());
        assertEquals(-1L, ev.get(0).getAsJsonObject().get("purse").getAsLong());

        r.chat("[Bazaar] Claiming order...", T0 + 11_000, 5);
        assertEquals(0, r.flush(T0 + 11_000, this::send));   // within FLUSH_MS
        assertEquals(1, r.flush(T0 + 13_100, this::send));
    }

    @Test
    void splitsLargeQueuesIntoBoundedFrames() {
        BazaarRecorder r = recorder();
        r.serverOn();
        for (int i = 0; i < 120; i++) {
            r.chat("[Bazaar] Claimed 1x Enchanted Coal worth 150 coins bought for 150.0 each!", T0 + i, 5);
        }
        long now = T0 + 10_000;
        while (r.queued() > 0) {
            assertTrue(r.flush(now, this::send) > 0);
            now += BazaarRecorder.FLUSH_MS;
        }
        assertEquals(3, sent.size());
        for (String frame : sent) {
            assertTrue(frame.length() <= BazaarRecorder.BATCH_CHARS);
            assertTrue(events(frame).size() <= BazaarRecorder.BATCH_EVENTS);
        }
    }

    @Test
    void keepsEventsWhenSendingFails() {
        BazaarRecorder r = recorder();
        r.serverOn();
        r.chat("[Bazaar] Claiming order...", T0, 5);
        assertEquals(0, r.flush(T0 + 10_000, frame -> false));
        assertEquals(1, r.queued());
        assertEquals(1, r.flush(T0 + 20_000, this::send));
        assertEquals(0, r.queued());
    }

    @Test
    void queueDropsOldestBeyondTheCap() {
        BazaarRecorder r = recorder();
        r.serverOn();
        r.disconnected(T0);
        for (int i = 0; i < BazaarRecorder.QUEUE_CAP + 20; i++) {
            r.chat("[Bazaar] Claiming order...", T0 + i, 5);
        }
        assertEquals(BazaarRecorder.QUEUE_CAP, r.queued());
        r.serverOn();
        r.flush(T0 + 10_000, this::send);
        assertEquals(21, events(sent.get(0)).get(0).getAsJsonObject().get("seq").getAsInt());
    }

    @Test
    void holdsWhileOfflineAndStopsWithoutANewSwitch() {
        BazaarRecorder r = recorder();
        r.serverOn();
        r.disconnected(T0);
        assertEquals(BazaarRecorder.State.HOLD, r.state());
        r.chat("[Bazaar] Claiming order...", T0 + 1, 5);
        assertEquals(1, r.queued());
        assertEquals(0, r.flush(T0 + 10_000, this::send));   // not sent while offline

        r.connected(T0 + 20_000);
        r.timers(T0 + 20_000 + BazaarRecorder.ON_WAIT_MS - 1);
        assertEquals(BazaarRecorder.State.HOLD, r.state());
        r.serverOn();
        assertEquals(1, r.flush(T0 + 60_000, this::send));

        r.disconnected(T0 + 70_000);
        r.chat("[Bazaar] Claiming order...", T0 + 70_001, 5);
        r.connected(T0 + 80_000);
        r.timers(T0 + 80_000 + BazaarRecorder.ON_WAIT_MS);
        assertEquals(BazaarRecorder.State.OFF, r.state());
        assertEquals(0, r.queued());
        assertEquals(List.of(), r.chat("[Bazaar] Claiming order...", T0 + 200_000, 5));
    }

    @Test
    void holdEndsAfterLongOfflineAndOnStop() {
        BazaarRecorder r = recorder();
        r.serverOn();
        r.disconnected(T0);
        r.timers(T0 + BazaarRecorder.HOLD_MS);
        assertEquals(BazaarRecorder.State.OFF, r.state());
        r.serverOn();
        r.chat("[Bazaar] Claiming order...", T0, 5);
        r.off("stopped");
        assertEquals(BazaarRecorder.State.OFF, r.state());
        assertEquals(0, r.queued());
    }

    private static Map<String, Object> slot(int i, int loreLines, int lineLength) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("i", i);
        s.put("id", "ENCHANTED_COAL");
        s.put("name", "BUY Enchanted Coal");
        s.put("count", 1);
        List<String> lore = new ArrayList<>();
        for (int j = 0; j < loreLines; j++) {
            lore.add("- " + j + "x " + "y".repeat(lineLength));
        }
        s.put("lore", lore);
        return s;
    }

    @Test
    void menuSnapshotsAndTheirSizeLimit() {
        BazaarRecorder r = recorder();
        r.serverOn();
        assertTrue(r.menu("Your Bazaar Orders", List.of(slot(10, 3, 20)), T0, 7));
        List<Map<String, Object>> big = new ArrayList<>();
        List<Map<String, Object>> huge = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            big.add(slot(i, 30, 45));
            huge.add(slot(i, 40, 190));
        }
        assertTrue(r.menu("Your Bazaar Orders", big, T0 + 1, 7));
        assertFalse(r.menu("Your Bazaar Orders", huge, T0 + 2, 7));
        assertEquals(2, r.queued());
        r.flush(T0 + 10_000, this::send);
        JsonArray ev = events(sent.get(0));
        JsonObject first = ev.get(0).getAsJsonObject();
        assertEquals("menu", first.get("k").getAsString());
        assertEquals("Your Bazaar Orders", first.get("title").getAsString());
        assertEquals("ENCHANTED_COAL", first.getAsJsonArray("slots").get(0).getAsJsonObject().get("id").getAsString());
        assertFalse(first.has("cut"));
        if (ev.size() == 1) {   // the cut snapshot went into the next frame
            r.flush(T0 + 20_000, this::send);
            ev = events(sent.get(1));
        }
        JsonObject cut = ev.get(ev.size() - 1).getAsJsonObject();
        assertTrue(cut.get("cut").getAsBoolean());
        assertEquals(BazaarRecorder.CUT_LORE_LINES,
                cut.getAsJsonArray("slots").get(0).getAsJsonObject().getAsJsonArray("lore").size());
        for (String frame : sent) {
            assertTrue(frame.length() <= BazaarRecorder.BATCH_CHARS);
        }
    }
}
