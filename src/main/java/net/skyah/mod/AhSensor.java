package net.skyah.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Auction house listings the player sees, free of game classes so it can be
 *  tested. Off until the server switches it on for the current connection
 *  ({"type":"ah_on"}). Only menus with an auction house title count, and in
 *  them only items whose lore carries a seller line and a price line; the
 *  game adds those lines in auction menus only, so chests, backpacks and the
 *  player's own inventory never pass. Sends seller, price and item id, never
 *  lore, names or anything else. Nothing is clicked or sent to the game. */
final class AhSensor {
    /** One listing as sent to the server. */
    record Item(String seller, long price, boolean bin, Long buyInS, Long endsInS, String id, String uuid, int count) {
        /** Same seller, item and price: sent once per SENT_TTL_MS. */
        String key() {
            return seller + "|" + id + "|" + uuid + "|" + price;
        }

        /** The same listing with the SkyBlock id and item uuid from the item data. */
        Item withIds(String newId, String newUuid) {
            return new Item(seller, price, bin, buyInS, endsInS, cleanId(newId), cleanUuid(newUuid), count);
        }
    }

    static final int MAX_ITEMS = 60;
    static final int MAX_BYTES = 8 * 1024;
    /** A menu counts once its listings stayed the same this long (slots arrive after the screen opens). */
    static final long QUIET_MS = 300L;
    static final long SENT_TTL_MS = 10 * 60_000L;
    static final int SENT_CAP = 5_000;
    /** Own cap below the server's 30 per minute. */
    static final int MAX_MESSAGES_PER_MIN = 25;
    /** At most one own-listing message this often (several listings in a row). */
    static final long SELF_GAP_MS = 5_000L;
    static final int TITLE_MAX = 80;

    static final Set<String> TITLES = Set.of(
            "Auctions Browser", "Auction View", "BIN Auction View", "Manage Auctions", "Auction House", "Your Bids");

    private static final Pattern FORMAT = Pattern.compile("§.?");
    private static final Pattern LINES = Pattern.compile("\\R");
    private static final Pattern NAME = Pattern.compile("^([A-Za-z0-9_]{1,16})(?:\\s.*)?$");
    private static final Pattern BRACKETS = Pattern.compile("^(?:\\[[^\\]]{0,24}\\]\\s*)+");
    private static final Pattern COINS = Pattern.compile(
            "^([0-9][0-9,]{0,24})(?:\\.([0-9]{1,6}))?\\s*([kmb])?(?:\\s*coins?)?(?:\\s.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,3}):(\\d{1,2})(?::(\\d{1,2}))?$");
    private static final Pattern PARTS = Pattern.compile(
            "^(?:\\d{1,6}\\s*(?:d|days?|h|hours?|hrs?|m|mins?|minutes?|s|secs?|seconds?)[\\s,]*)+$");
    private static final Pattern PART = Pattern.compile(
            "(\\d{1,6})\\s*(d|days?|h|hours?|hrs?|m|mins?|minutes?|s|secs?|seconds?)");
    private static final Pattern ITEM_ID = Pattern.compile("^[A-Za-z0-9_:;.\\-]{1,80}$");
    private static final Pattern ITEM_UUID = Pattern.compile("^[0-9a-fA-F-]{32,36}$");
    private static final Pattern SELF = Pattern.compile("^(?:BIN )?Auction started for .{1,120}$");

    private final Gson gson = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private final Consumer<String> log;
    private boolean on;
    private Object screen;
    private String title;
    private List<Item> items = List.of();
    private String contentKey;
    private long changedAt;
    private boolean handled;
    private final LinkedHashMap<String, Long> sent = new LinkedHashMap<>();
    private long windowStart;
    private int windowCount;
    private boolean capLogged;
    private long lastSelfAt;
    private long sentItems;

    AhSensor(Consumer<String> log) {
        this.log = log;
    }

    /* ── parsing (static, pure) ── */

    /** Text without formatting codes. */
    static String plain(String text) {
        return text == null ? "" : FORMAT.matcher(text).replaceAll("");
    }

    /** Auction confirm screens (buy it now, bid). They do not show the item,
     *  so the side panel matches them by title; other confirm screens, such as
     *  the bazaar's "Confirm Buy Order", must not match. */
    static final Set<String> CONFIRM_TITLES = Set.of("Confirm Purchase", "Confirm Bid");

    static boolean auctionConfirmTitle(String title) {
        if (title == null) {
            return false;
        }
        return CONFIRM_TITLES.contains(FORMAT.matcher(title).replaceAll("").trim());
    }

    /** Auction house menus only: browser, search results, auction views, own
     *  auctions and bids, and a player's auctions ("Name's Auctions"). */
    static boolean titleAllowed(String title) {
        if (title == null || title.isEmpty()) {
            return false;
        }
        if (TITLES.contains(title) || title.startsWith("Auctions: \"")) {
            return true;
        }
        for (String end : new String[]{"'s Auctions", "’s Auctions"}) {
            if (title.endsWith(end) && title.length() > end.length()) {
                return true;
            }
        }
        return false;
    }

    /** Main network only: the test network has its own auction house. */
    static boolean hypixelMain(String address) {
        if (address == null) {
            return false;
        }
        String host = address.trim().toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) {
            host = host.substring(0, colon);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.equals("alpha.hypixel.net") || host.endsWith(".alpha.hypixel.net")) {
            return false;
        }
        return host.equals("hypixel.net") || host.endsWith(".hypixel.net")
                || host.equals("hypixel.io") || host.endsWith(".hypixel.io");
    }

    /** "[MVP+] Name" -> "Name"; null for "Refreshing..." or anything that is no player name. */
    static String seller(String text) {
        String s = plain(text).trim();
        s = BRACKETS.matcher(s).replaceFirst("");
        Matcher m = NAME.matcher(s);
        return m.matches() ? m.group(1) : null;
    }

    /** "1,250,000 coins" -> 1250000; "1.5M" -> 1500000; null when unreadable. */
    static Long coins(String text) {
        Matcher m = COINS.matcher(plain(text).trim());
        if (!m.matches()) {
            return null;
        }
        String whole = m.group(1);
        if (whole.endsWith(",") || whole.contains(",,")) {
            return null;
        }
        try {
            BigDecimal v = new BigDecimal(whole.replace(",", "") + (m.group(2) == null ? "" : "." + m.group(2)));
            String unit = m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.ROOT);
            v = switch (unit) {
                case "k" -> v.movePointRight(3);
                case "m" -> v.movePointRight(6);
                case "b" -> v.movePointRight(9);
                default -> v;
            };
            if (unit.isEmpty() && m.group(2) != null) {
                return null;
            }
            long out = v.longValue();
            return out > 0 && out <= 1_000_000_000_000L ? out : null;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /** "5h 59m 50s", "13d 23h", "14s", "0:14" -> seconds; null for "Ended!" and the like. */
    static Long seconds(String text) {
        String s = plain(text).trim().toLowerCase(Locale.ROOT);
        while (s.endsWith("!") || s.endsWith(".")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        if (s.isEmpty()) {
            return null;
        }
        Matcher c = CLOCK.matcher(s);
        if (c.matches()) {
            long a = Long.parseLong(c.group(1));
            long b = Long.parseLong(c.group(2));
            return c.group(3) == null ? a * 60 + b : a * 3600 + b * 60 + Long.parseLong(c.group(3));
        }
        if (!PARTS.matcher(s).matches()) {
            return null;
        }
        long total = 0;
        Matcher p = PART.matcher(s);
        while (p.find()) {
            long n = Long.parseLong(p.group(1));
            char unit = p.group(2).charAt(0);
            total += switch (unit) {
                case 'd' -> n * 86_400L;
                case 'h' -> n * 3_600L;
                case 'm' -> n * 60L;
                default -> n;
            };
        }
        return total;
    }

    /** Value after "Label: " when the line starts with that label (any case), else null. */
    static String after(String line, String label) {
        if (line.length() < label.length() || !line.regionMatches(true, 0, label, 0, label.length())) {
            return null;
        }
        return line.substring(label.length()).trim();
    }

    /** True when the lore shows the buy lock of a new BIN listing ("Can buy in: 14s"). */
    static Long buyIn(List<String> lore) {
        for (String raw : lore) {
            String v = after(plain(raw).trim(), "Can buy in:");
            if (v != null) {
                return seconds(v);
            }
        }
        return null;
    }

    /** One menu item: a listing when the lore has a seller line and a price
     *  line, else null (and nothing else about the item is looked at). */
    static Item read(List<String> lore, String id, String uuid, int count) {
        if (lore == null || lore.isEmpty()) {
            return null;
        }
        String sellerText = null;
        Long bin = null;
        Long top = null;
        Long start = null;
        boolean priceLine = false;
        Long buyIn = null;
        Long endsIn = null;
        for (String raw : lore) {
            String line = plain(raw).trim();
            String v;
            if ((v = after(line, "Seller:")) != null) {
                sellerText = v;
            } else if ((v = after(line, "Buy it now:")) != null) {
                priceLine = true;
                bin = coins(v);
            } else if ((v = after(line, "Top bid:")) != null) {
                priceLine = true;
                top = coins(v);
            } else if ((v = after(line, "Starting bid:")) != null) {
                priceLine = true;
                start = coins(v);
            } else if ((v = after(line, "Can buy in:")) != null) {
                buyIn = seconds(v);
            } else if ((v = after(line, "Ends in:")) != null) {
                endsIn = seconds(v);
            }
        }
        if (sellerText == null || !priceLine) {
            return null;
        }
        String seller = seller(sellerText);
        Long price = bin != null ? bin : top != null ? top : start;
        if (seller == null || price == null) {
            return null;
        }
        if (buyIn != null && buyIn > 3_600L) {
            buyIn = null;
        }
        if (endsIn != null && endsIn > 31L * 86_400L) {
            endsIn = null;
        }
        return new Item(seller, price, bin != null, buyIn, endsIn, cleanId(id), cleanUuid(uuid),
                Math.max(1, Math.min(count, 999)));
    }

    static String cleanId(String id) {
        return id != null && ITEM_ID.matcher(id).matches() ? id : null;
    }

    static String cleanUuid(String uuid) {
        return uuid != null && ITEM_UUID.matcher(uuid).matches() ? uuid.toLowerCase(Locale.ROOT) : null;
    }

    /** The player's own new listing: a system line that starts with
     *  "BIN Auction started for" or "Auction started for". A player's message
     *  starts with a rank or name, never with that. */
    static boolean selfListing(String text) {
        if (text == null) {
            return false;
        }
        for (String line : LINES.split(plain(text))) {
            if (SELF.matcher(line.strip()).matches()) {
                return true;
            }
        }
        return false;
    }

    /* ── state ── */

    synchronized boolean isOn() {
        return on;
    }

    /** The server switched the sensor on for this connection. */
    synchronized void serverOn() {
        if (!on) {
            log.accept("auction sensor on");
        }
        on = true;
    }

    /** The connection ended or was stopped: off until the next switch. */
    synchronized void off(String why) {
        if (on) {
            log.accept("auction sensor off (" + why + ")" + (sentItems > 0 ? ", " + sentItems + " listings sent" : ""));
        }
        on = false;
        screen = null;
        forget();
    }

    synchronized long sentItems() {
        return sentItems;
    }

    private void forget() {
        title = null;
        items = List.of();
        contentKey = null;
        changedAt = 0L;
        handled = false;
    }

    /** One read of the open screen: title null when no auction menu is open.
     *  Returns the messages to send now (often none). */
    synchronized List<String> menu(Object screenId, String menuTitle, List<Item> read, long now) {
        List<String> out = new ArrayList<>();
        if (!on) {
            return out;
        }
        if (screenId != screen) {
            // The menu closed or changed: what it showed still counts.
            if (!handled && !items.isEmpty()) {
                out.addAll(emit(title, items, now));
            }
            screen = screenId;
            forget();
        }
        if (menuTitle == null || read == null) {
            return out;
        }
        String k = menuTitle + "|" + read;
        if (!k.equals(contentKey)) {
            title = menuTitle;
            items = List.copyOf(read);
            contentKey = k;
            changedAt = now;
            handled = false;
            return out;
        }
        if (!handled && now - changedAt >= QUIET_MS) {
            handled = true;
            out.addAll(emit(title, items, now));
        }
        return out;
    }

    /** Own listing started: {"type":"ah_self"} with the player's uuid (dashes removed). */
    synchronized String self(String playerUuid, long now) {
        if (!on || playerUuid == null) {
            return null;
        }
        String u = playerUuid.replace("-", "").toLowerCase(Locale.ROOT);
        if (!u.matches("^[0-9a-f]{32}$") || now - lastSelfAt < SELF_GAP_MS || !allow(now)) {
            return null;
        }
        lastSelfAt = now;
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "ah_self");
        msg.put("v", 1);
        msg.put("uuid", u);
        return gson.toJson(msg);
    }

    /** New or changed listings of one menu, as messages of at most MAX_ITEMS
     *  items and MAX_BYTES bytes. Listings sent in the last 10 minutes are skipped. */
    private List<String> emit(String menuTitle, List<Item> list, long now) {
        List<String> out = new ArrayList<>();
        expire(now);
        List<String> fresh = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<Item> picked = new ArrayList<>();
        for (Item it : list) {
            String key = it.key();
            if (sent.containsKey(key) || keys.contains(key)) {
                continue;
            }
            keys.add(key);
            picked.add(it);
            fresh.add(gson.toJson(itemMap(it)));
        }
        if (fresh.isEmpty()) {
            return out;
        }
        String t = menuTitle.length() > TITLE_MAX ? menuTitle.substring(0, TITLE_MAX) : menuTitle;
        String head = "{\"type\":\"ah\",\"v\":1,\"title\":" + gson.toJson(t) + ",\"items\":[";
        String tail = "]}";
        int base = bytes(head) + bytes(tail);
        int i = 0;
        while (i < fresh.size()) {
            StringBuilder sb = new StringBuilder(head);
            int size = base;
            int n = 0;
            int from = i;
            while (i < fresh.size() && n < MAX_ITEMS) {
                String item = fresh.get(i);
                int add = bytes(item) + (n > 0 ? 1 : 0);
                if (size + add > MAX_BYTES) {
                    if (n == 0) {
                        i++; // one item larger than a message: never happens with the field caps
                        from = i;
                        continue;
                    }
                    break;
                }
                if (n > 0) {
                    sb.append(',');
                }
                sb.append(item);
                size += add;
                n++;
                i++;
            }
            if (n == 0) {
                continue;
            }
            if (!allow(now)) {
                if (!capLogged) {
                    capLogged = true;
                    log.accept("auction sensor: more than " + MAX_MESSAGES_PER_MIN + " messages a minute, rest skipped");
                }
                return out;
            }
            out.add(sb.append(tail).toString());
            int locked = 0;
            for (int j = from; j < i; j++) {
                remember(keys.get(j), now);
                if (picked.get(j).buyInS() != null) {
                    locked++;
                }
            }
            sentItems += n;
            log.accept("auction sensor: " + n + " listings from '" + t + "'" + (locked > 0 ? ", " + locked + " new (buy lock)" : ""));
        }
        return out;
    }

    private static Map<String, Object> itemMap(Item it) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seller", it.seller());
        m.put("price", it.price());
        m.put("bin", it.bin());
        m.put("buy_in_s", it.buyInS());
        m.put("ends_in_s", it.endsInS());
        m.put("id", it.id());
        m.put("uuid", it.uuid());
        m.put("count", it.count());
        return m;
    }

    private boolean allow(long now) {
        if (now - windowStart >= 60_000L) {
            windowStart = now;
            windowCount = 0;
            capLogged = false;
        }
        if (windowCount >= MAX_MESSAGES_PER_MIN) {
            return false;
        }
        windowCount++;
        return true;
    }

    private void remember(String key, long now) {
        sent.remove(key);
        sent.put(key, now);
        while (sent.size() > SENT_CAP) {
            Iterator<String> it = sent.keySet().iterator();
            it.next();
            it.remove();
        }
    }

    private void expire(long now) {
        Iterator<Map.Entry<String, Long>> it = sent.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() >= SENT_TTL_MS) {
                it.remove();
            } else {
                break; // oldest first
            }
        }
    }

    private static int bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
