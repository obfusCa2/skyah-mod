package net.skyah.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhSensorTest {
    private static final long T0 = 1_763_000_000_000L;
    private final List<String> log = new ArrayList<>();

    private AhSensor sensor() {
        return new AhSensor(log::add);
    }

    /** Lore of a fresh BIN listing as the game shows it (with formatting codes). */
    private static List<String> binLore(String seller, String price) {
        return List.of(
                "§7Gear Score: §d580",
                "§8§m-----------------",
                "§7Seller: " + seller,
                "§7Buy it now: §6" + price,
                "",
                "§7Ends in: §e5h 59m 50s",
                "§cCan buy in: 14s",
                "",
                "§eClick to inspect!");
    }

    private static AhSensor.Item item(String seller, long price, String uuid) {
        return new AhSensor.Item(seller, price, true, null, 21_590L, "HYPERION", uuid, 1);
    }

    private static JsonObject json(String frame) {
        return JsonParser.parseString(frame).getAsJsonObject();
    }

    @Test
    void auctionConfirmTitles() {
        for (String t : List.of("Confirm Purchase", "Confirm Bid", "\u00a7rConfirm Purchase", " Confirm Bid ")) {
            assertTrue(AhSensor.auctionConfirmTitle(t), t);
        }
        for (String t : List.of("Confirm Buy Order", "Confirm Sell Offer", "Confirm Instant Buy",
                "Confirm", "Confirm Purchase Order", "confirm purchase", "", "BIN Auction View")) {
            assertFalse(AhSensor.auctionConfirmTitle(t), t);
        }
        assertFalse(AhSensor.auctionConfirmTitle(null));
    }

    @Test
    void titleWhitelist() {
        for (String t : List.of("Auctions Browser", "Auction View", "BIN Auction View", "Manage Auctions",
                "Auction House", "Your Bids", "Auctions: \"hyperion\"", "Auctions: \"a very long sear",
                "Technoblade's Auctions", "Someone’s Auctions")) {
            assertTrue(AhSensor.titleAllowed(t), t);
        }
        for (String t : List.of("", "Chest", "Large Chest", "Ender Chest (1/9)", "Backpack (Slot #1)",
                "Confirm Purchase", "Create BIN Auction", "Auction Stats", "'s Auctions", "auctions browser",
                "My Auctions Browser", "Your Bazaar Orders", "Auctions", "Personal Vault")) {
            assertFalse(AhSensor.titleAllowed(t), t);
        }
        assertFalse(AhSensor.titleAllowed(null));
    }

    @Test
    void mainNetworkOnly() {
        assertTrue(AhSensor.hypixelMain("mc.hypixel.net"));
        assertTrue(AhSensor.hypixelMain("hypixel.net"));
        assertTrue(AhSensor.hypixelMain("MC.Hypixel.NET:25565"));
        assertTrue(AhSensor.hypixelMain("mc.hypixel.net."));
        assertTrue(AhSensor.hypixelMain("mc.hypixel.io"));
        assertFalse(AhSensor.hypixelMain("alpha.hypixel.net"));
        assertFalse(AhSensor.hypixelMain("alpha.hypixel.net:25565"));
        assertFalse(AhSensor.hypixelMain("hypixel.net.example.com"));
        assertFalse(AhSensor.hypixelMain("nothypixel.net"));
        assertFalse(AhSensor.hypixelMain("localhost"));
        assertFalse(AhSensor.hypixelMain(null));
    }

    @Test
    void sellerWithoutRank() {
        assertEquals("Parakkz", AhSensor.seller("§b[MVP§e+§b] Parakkz"));
        assertEquals("Some_Name1", AhSensor.seller("[MVP++] Some_Name1"));
        assertEquals("Name", AhSensor.seller("[VIP] Name"));
        assertEquals("Name", AhSensor.seller("Name"));
        assertEquals("Name", AhSensor.seller("[YOUTUBE] Name"));
        assertEquals("Name", AhSensor.seller("[312] [MVP+] Name"));
        assertEquals("Name", AhSensor.seller("[MVP+] Name ✦"));
        assertNull(AhSensor.seller("Refreshing..."));
        assertNull(AhSensor.seller("§7Refreshing..."));
        assertNull(AhSensor.seller(""));
        assertNull(AhSensor.seller("[MVP+]"));
        assertNull(AhSensor.seller("Name.With.Dots"));
        assertNull(AhSensor.seller("WayTooLongPlayerName"));
    }

    @Test
    void coinFormats() {
        assertEquals(1_250_000L, AhSensor.coins("1,250,000 coins"));
        assertEquals(190_000_000L, AhSensor.coins("§6190,000,000 coins"));
        assertEquals(500L, AhSensor.coins("500 coins"));
        assertEquals(1L, AhSensor.coins("1 coin"));
        assertEquals(42_000L, AhSensor.coins("42000"));
        assertEquals(1_500_000L, AhSensor.coins("1.5M coins"));
        assertEquals(2_000_000_000L, AhSensor.coins("2b"));
        assertEquals(750_000L, AhSensor.coins("750k"));
        assertNull(AhSensor.coins("free"));
        assertNull(AhSensor.coins(""));
        assertNull(AhSensor.coins("0 coins"));
        assertNull(AhSensor.coins("1,250, coins"));
        assertNull(AhSensor.coins("12.5 coins"));
    }

    @Test
    void durationFormats() {
        assertEquals(14L, AhSensor.seconds("14s"));
        assertEquals(14L, AhSensor.seconds("§e14s"));
        assertEquals(21_590L, AhSensor.seconds("5h 59m 50s"));
        assertEquals(21_592L, AhSensor.seconds("05h 59m 52s"));
        assertEquals(13 * 86_400L + 23 * 3_600L, AhSensor.seconds("13d 23h"));
        assertEquals(23 * 3_600L, AhSensor.seconds("23h"));
        assertEquals(65L, AhSensor.seconds("1m 5s"));
        assertEquals(7_200L, AhSensor.seconds("2 hours"));
        assertEquals(86_400L, AhSensor.seconds("1 day"));
        assertEquals(14L, AhSensor.seconds("0:14"));
        assertEquals(3_723L, AhSensor.seconds("1:02:03"));
        assertEquals(30L, AhSensor.seconds("30s!"));
        assertNull(AhSensor.seconds("Ended!"));
        assertNull(AhSensor.seconds("Soon!"));
        assertNull(AhSensor.seconds("Expired"));
        assertNull(AhSensor.seconds(""));
        assertNull(AhSensor.seconds("5h and then some"));
    }

    @Test
    void readsAListing() {
        AhSensor.Item it = AhSensor.read(binLore("§b[MVP§e+§b] Parakkz", "190,000,000 coins"),
                "HYPERION", "28FDB3CB-1029-48CF-9591-3E7D8D67BA9A", 1);
        assertNotNull(it);
        assertEquals("Parakkz", it.seller());
        assertEquals(190_000_000L, it.price());
        assertTrue(it.bin());
        assertEquals(14L, it.buyInS());
        assertEquals(21_590L, it.endsInS());
        assertEquals("HYPERION", it.id());
        assertEquals("28fdb3cb-1029-48cf-9591-3e7d8d67ba9a", it.uuid());
        assertEquals(1, it.count());
    }

    @Test
    void bidAuctionsUseTheTopBid() {
        AhSensor.Item start = AhSensor.read(List.of("§7Seller: §aName", "§7Starting bid: §61,000 coins",
                "§7Ends in: §e13d 23h"), "ENCHANTED_BOOK", null, 1);
        assertNotNull(start);
        assertFalse(start.bin());
        assertEquals(1_000L, start.price());
        assertNull(start.buyInS());
        AhSensor.Item top = AhSensor.read(List.of("§7Seller: Name", "§7Bids: §a3 bids", "§7Starting bid: §61,000 coins",
                "§7Top bid: §64,500 coins", "§7Bidder: §aOther", "§7Ends in: §e2h"), "ENCHANTED_BOOK", null, 1);
        assertNotNull(top);
        assertEquals(4_500L, top.price());
        assertFalse(top.bin());
        assertEquals(7_200L, top.endsInS());
    }

    @Test
    void onlySlotsWithSellerAndPriceQualify() {
        // A chest item, a renamed item, a menu button, a player's own items: no seller or no price line.
        assertNull(AhSensor.read(List.of("§7A sword", "§9Sharpness VII", "§6§lLEGENDARY SWORD"), "HYPERION", null, 1));
        assertNull(AhSensor.read(List.of("§7Seller: Name"), "HYPERION", null, 1));
        assertNull(AhSensor.read(List.of("§7Buy it now: §61,000 coins"), "HYPERION", null, 1));
        assertNull(AhSensor.read(List.of("§7Price: 1,000 coins", "§7Owner: Name"), "HYPERION", null, 1));
        assertNull(AhSensor.read(List.of(), "HYPERION", null, 1));
        assertNull(AhSensor.read(null, "HYPERION", null, 1));
        // Seller name not loaded yet, or no readable price: skipped.
        assertNull(AhSensor.read(List.of("§7Seller: §7Refreshing...", "§7Buy it now: §61,000 coins"), "X", null, 1));
        assertNull(AhSensor.read(List.of("§7Seller: Name", "§7Buy it now: §6???"), "X", null, 1));
        // Lines must start with the label: a lore line that mentions it is not one.
        assertNull(AhSensor.read(List.of("§7Ask the Seller: Name", "§7Buy it now: §61,000 coins"), "X", null, 1));
    }

    @Test
    void badIdsAreDroppedNotTheListing() {
        AhSensor.Item it = AhSensor.read(List.of("Seller: Name", "Buy it now: 5,000 coins"), "bad id!", "not-a-uuid", 64);
        assertNotNull(it);
        assertNull(it.id());
        assertNull(it.uuid());
        assertEquals(64, it.count());
        AhSensor.Item withIds = it.withIds("ENCHANTED_DIAMOND", "0123456789abcdef0123456789abcdef");
        assertEquals("ENCHANTED_DIAMOND", withIds.id());
        assertEquals("0123456789abcdef0123456789abcdef", withIds.uuid());
    }

    @Test
    void ownListingLineOnlyFromTheGame() {
        assertTrue(AhSensor.selfListing("§eBIN Auction started for §5Hyperion§e!"));
        assertTrue(AhSensor.selfListing("Auction started for Enchanted Book!"));
        assertTrue(AhSensor.selfListing("Something else\nBIN Auction started for Hyperion!"));
        assertFalse(AhSensor.selfListing("[MVP+] Someone: BIN Auction started for Hyperion!"));
        assertFalse(AhSensor.selfListing("Someone: Auction started for Hyperion!"));
        assertFalse(AhSensor.selfListing("Party > [VIP] Someone: BIN Auction started for x"));
        assertFalse(AhSensor.selfListing("From [MVP+] Someone: Auction started for x"));
        assertFalse(AhSensor.selfListing("You purchased Hyperion for 1,000 coins!"));
        assertFalse(AhSensor.selfListing(null));
    }

    @Test
    void sendsNothingUntilSwitchedOn() {
        AhSensor s = sensor();
        Object screen = new Object();
        List<AhSensor.Item> items = List.of(item("Name", 1_000_000L, null));
        assertTrue(s.menu(screen, "Auctions Browser", items, T0).isEmpty());
        assertTrue(s.menu(screen, "Auctions Browser", items, T0 + 1_000).isEmpty());
        assertNull(s.self("0123456789abcdef0123456789abcdef", T0));
        s.serverOn();
        s.off("disconnected");
        assertTrue(s.menu(screen, "Auctions Browser", items, T0 + 2_000).isEmpty());
        assertTrue(s.menu(screen, "Auctions Browser", items, T0 + 3_000).isEmpty());
    }

    @Test
    void waitsForAQuietMenuThenSendsOnce() {
        AhSensor s = sensor();
        s.serverOn();
        Object screen = new Object();
        List<AhSensor.Item> one = List.of(item("Name", 1_000_000L, null));
        List<AhSensor.Item> two = List.of(item("Name", 1_000_000L, null), item("Other", 2_000_000L, null));
        assertTrue(s.menu(screen, "Auctions Browser", one, T0).isEmpty());
        assertTrue(s.menu(screen, "Auctions Browser", two, T0 + 100).isEmpty()); // still filling
        assertTrue(s.menu(screen, "Auctions Browser", two, T0 + 300).isEmpty()); // 200 ms quiet
        List<String> out = s.menu(screen, "Auctions Browser", two, T0 + 400);
        assertEquals(1, out.size());
        JsonObject o = json(out.get(0));
        assertEquals("ah", o.get("type").getAsString());
        assertEquals(1, o.get("v").getAsInt());
        assertEquals("Auctions Browser", o.get("title").getAsString());
        JsonArray items = o.getAsJsonArray("items");
        assertEquals(2, items.size());
        JsonObject first = items.get(0).getAsJsonObject();
        assertEquals("Name", first.get("seller").getAsString());
        assertEquals(1_000_000L, first.get("price").getAsLong());
        assertTrue(first.get("bin").getAsBoolean());
        assertTrue(first.get("buy_in_s").isJsonNull());
        assertEquals(21_590L, first.get("ends_in_s").getAsLong());
        assertEquals("HYPERION", first.get("id").getAsString());
        assertTrue(first.get("uuid").isJsonNull());
        assertEquals(1, first.get("count").getAsInt());
        // Same menu, nothing new: nothing more.
        assertTrue(s.menu(screen, "Auctions Browser", two, T0 + 2_000).isEmpty());
        assertEquals(2, s.sentItems());
    }

    @Test
    void skipsListingsSentInTheLastTenMinutes() {
        AhSensor s = sensor();
        s.serverOn();
        List<AhSensor.Item> page = List.of(item("Name", 1_000_000L, "u1".repeat(16)), item("Other", 5L, null));
        Object a = new Object();
        s.menu(a, "Auctions Browser", page, T0);
        assertEquals(1, s.menu(a, "Auctions Browser", page, T0 + 300).size());
        // Reopened a minute later, one listing repriced: only that one goes out.
        Object b = new Object();
        List<AhSensor.Item> again = List.of(item("Name", 1_000_000L, "u1".repeat(16)), item("Other", 6L, null));
        s.menu(b, "Auctions Browser", again, T0 + 60_000);
        List<String> out = s.menu(b, "Auctions Browser", again, T0 + 60_300);
        assertEquals(1, out.size());
        JsonArray items = json(out.get(0)).getAsJsonArray("items");
        assertEquals(1, items.size());
        assertEquals(6L, items.get(0).getAsJsonObject().get("price").getAsLong());
        // After ten minutes the first one counts as new again.
        Object c = new Object();
        s.menu(c, "Auctions Browser", page, T0 + 11 * 60_000);
        assertEquals(2, json(s.menu(c, "Auctions Browser", page, T0 + 11 * 60_000 + 300).get(0))
                .getAsJsonArray("items").size());
    }

    @Test
    void aMenuClosedEarlyStillCounts() {
        AhSensor s = sensor();
        s.serverOn();
        Object a = new Object();
        List<AhSensor.Item> page = List.of(item("Name", 1_000_000L, null));
        assertTrue(s.menu(a, "Auction View", page, T0).isEmpty());
        // The view closed 100 ms later (no auction menu open now): its listing is sent.
        List<String> out = s.menu(null, null, null, T0 + 100);
        assertEquals(1, out.size());
        assertEquals("Auction View", json(out.get(0)).get("title").getAsString());
        assertTrue(s.menu(null, null, null, T0 + 500).isEmpty());
    }

    @Test
    void splitsAtSixtyItemsAndEightKilobytes() {
        AhSensor s = sensor();
        s.serverOn();
        List<AhSensor.Item> many = new ArrayList<>();
        for (int i = 0; i < 130; i++) {
            many.add(new AhSensor.Item("Seller" + i, 1_000_000L + i, true, 14L, 21_590L,
                    "SOME_LONG_SKYBLOCK_ITEM_ID_" + i, String.format("%032x", i), 1));
        }
        Object screen = new Object();
        s.menu(screen, "Auctions Browser", many, T0);
        List<String> out = s.menu(screen, "Auctions Browser", many, T0 + 300);
        assertTrue(out.size() >= 3);
        int total = 0;
        for (String frame : out) {
            assertTrue(frame.getBytes(StandardCharsets.UTF_8).length <= AhSensor.MAX_BYTES, "frame too large");
            int n = json(frame).getAsJsonArray("items").size();
            assertTrue(n >= 1 && n <= AhSensor.MAX_ITEMS);
            total += n;
        }
        assertEquals(130, total);
    }

    @Test
    void capsMessagesPerMinute() {
        AhSensor s = sensor();
        s.serverOn();
        int frames = 0;
        for (int i = 0; i < 40; i++) {
            Object screen = new Object();
            List<AhSensor.Item> page = List.of(item("Name", 1_000L + i, null));
            s.menu(screen, "Auctions Browser", page, T0 + i * 1_000L);
            frames += s.menu(screen, "Auctions Browser", page, T0 + i * 1_000L + 300).size();
        }
        assertEquals(AhSensor.MAX_MESSAGES_PER_MIN, frames);
        // Next minute: sends again.
        Object screen = new Object();
        List<AhSensor.Item> page = List.of(item("Name", 99L, null));
        s.menu(screen, "Auctions Browser", page, T0 + 61_000);
        assertEquals(1, s.menu(screen, "Auctions Browser", page, T0 + 61_300).size());
    }

    @Test
    void ownListingMessage() {
        AhSensor s = sensor();
        s.serverOn();
        String msg = s.self("01234567-89ab-cdef-0123-456789ABCDEF", T0);
        assertNotNull(msg);
        JsonObject o = json(msg);
        assertEquals("ah_self", o.get("type").getAsString());
        assertEquals(1, o.get("v").getAsInt());
        assertEquals("0123456789abcdef0123456789abcdef", o.get("uuid").getAsString());
        assertNull(s.self("01234567-89ab-cdef-0123-456789abcdef", T0 + 1_000)); // several in a row: one
        assertNotNull(s.self("01234567-89ab-cdef-0123-456789abcdef", T0 + 6_000));
        assertNull(s.self("not a uuid", T0 + 20_000));
    }

    @Test
    void buyLockFromLore() {
        assertEquals(14L, AhSensor.buyIn(binLore("Name", "1 coins")));
        assertEquals(3L, AhSensor.buyIn(List.of("§7Click to buy", "§cCan buy in: §e3s")));
        assertNull(AhSensor.buyIn(List.of("§7Seller: Name", "§7Buy it now: 1 coins")));
    }
}
