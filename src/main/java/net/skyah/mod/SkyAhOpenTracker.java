package net.skyah.mod;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Measures what happens after you open a BIN deal (chat click or hotkey):
 *
 *  - recv_to_click_ms: deal arrived at the mod -> you clicked / pressed the key
 *  - click_to_open_ms: click -> the auction view shows its buy button
 *  - state: buy button at first sight: gold = "buyable", potato = "sold"
 *  - sold_after_ms: it was buyable and turned into a potato while open, that
 *    many ms after opening (someone else bought it first)
 *
 *  Checked every rendered frame (a few ms resolution), only while one deal is
 *  being tracked. Auctions (bids) are skipped. One "open" message per deal,
 *  sent when the result is known or the view closes.
 *
 *  An auction view that shows no buy button we know for a while is reported
 *  once (title and item names only), at most 3 times per session. */
public final class SkyAhOpenTracker {
    private static final Gson GSON = new Gson();
    private static final long OPEN_TIMEOUT_MS = 15_000L;
    /** Wait this long after the view closed for the next screen (confirm?). */
    private static final long CLOSE_GRACE_MS = 300L;
    /** Auction view on screen this long without a known buy button: unreadable. */
    private static final long UNREADABLE_MS = 1_500L;
    private static final int UNREADABLE_MAX = 3;
    private static final long UNREADABLE_GAP_MS = 10 * 60_000L;
    private static int unreadableSent = 0;
    private static long unreadableAt = 0L;

    private static Flip flip;
    private static String via = "";
    private static long clickAt;
    private static Screen screen;
    private static long openAt;
    private static String state;
    private static long soldAt;
    private static long closedAt;
    private static String buyItem;
    private static String buyName;
    private static int buySlot = -1;
    /** The screen that was open when the deal was requested (never reported). */
    private static Screen openBefore;
    private static long viewSeenAt;
    private static boolean unreadable;

    private SkyAhOpenTracker() {
    }

    /** A deal is being opened. Starts a measurement for BIN deals. */
    public static synchronized void requested(Flip f, String how) {
        if (flip != null && screen != null) {
            send(closedAt > 0 ? "closed" : "replaced");
        }
        reset();
        SkyAhHold.requested(f);
        if (f == null || !FlipChat.isBin(f) || f.auctionId == null || f.auctionId.isBlank()
                || "test".equals(f.auctionId)) {
            return;
        }
        flip = f;
        via = how;
        clickAt = System.currentTimeMillis();
        openBefore = McCompat.screen(Minecraft.getInstance());
    }

    /** Every rendered frame of a container screen. */
    public static synchronized void frame(Screen s) {
        if (flip == null || !(s instanceof AbstractContainerScreen<?> cs)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (screen == null) {
            if (now - clickAt > OPEN_TIMEOUT_MS) {
                SkyAhLog.add("open: no auction view within 15s for " + flip.name);
                reset();
                return;
            }
            // Hypixel: "BIN Auction View"; the item must be the deal we opened.
            String title = s.getTitle().getString();
            if (!title.contains("Auction View")) {
                return;
            }
            boolean item = SkyAhPanel.showsItem(cs, flip.name);
            String first = item ? scan(cs, flip.name) : null;
            if (first == null) {
                // Buy button not sent yet, or one we do not know. A view that was
                // already open stays when the auction is gone: not unreadable.
                if (s != openBefore) {
                    if (viewSeenAt == 0L) {
                        viewSeenAt = now;
                    } else if (!unreadable && now - viewSeenAt > UNREADABLE_MS) {
                        unreadable = true;
                        reportUnreadable(cs, title, item);
                        SkyAhHold.unreadable(flip);
                    }
                }
                return;
            }
            screen = s;
            openAt = now;
            state = first;
            SkyAhLog.add("open: " + flip.name + " " + first + " after " + (openAt - clickAt) + "ms ("
                    + buyItem + " '" + buyName + "' slot " + buySlot + ")");
            SkyAhHold.state(flip, first);
            if ("sold".equals(first)) {
                send("sold");
            }
            return;
        }
        if (s != screen || soldAt > 0) {
            return;
        }
        if ("sold".equals(scan(cs, flip.name))) {
            soldAt = now;
            SkyAhLog.add("open: " + flip.name + " sold " + (soldAt - openAt) + "ms after opening");
            SkyAhHold.state(flip, "sold");
            send("sold");
        }
    }

    public static synchronized void removed(Screen s) {
        if (flip != null && s == screen && closedAt == 0) {
            closedAt = System.currentTimeMillis();
        }
    }

    /** Next screen after the view closed: the confirm screen means you clicked buy. */
    public static synchronized void screenInit(Screen s) {
        if (flip == null || closedAt == 0 || s == screen) {
            return;
        }
        String title = s.getTitle().getString();
        if (title.contains("Auction View")) {
            // The server re-opened the view (e.g. after the sale): keep watching it.
            screen = s;
            closedAt = 0L;
            return;
        }
        send(title.toLowerCase().contains("confirm") ? "confirm" : "left");
    }

    public static synchronized void tick() {
        if (flip != null && closedAt > 0 && System.currentTimeMillis() - closedAt > CLOSE_GRACE_MS) {
            send("closed");
        }
    }

    /** "buyable" (gold), "sold" (potato) or null (no buy button yet). Fills buyItem/Name/Slot. */
    private static String scan(AbstractContainerScreen<?> cs, String dealName) {
        String deal = SkyAhPanel.normalized(dealName);
        List<Slot> slots = cs.getMenu().slots;
        // The last 36 slots are the player's own inventory.
        int n = slots.size() > 36 ? slots.size() - 36 : slots.size();
        for (int i = 0; i < n; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!deal.isEmpty() && deal.equals(SkyAhPanel.normalized(stack.getHoverName().getString()))) {
                continue; // the auctioned item itself (it may be gold or a potato)
            }
            Item item = stack.getItem();
            String result = null;
            if (item == Items.GOLD_INGOT || item == Items.GOLD_NUGGET || item == Items.GOLD_BLOCK) {
                result = "buyable";
            } else if (item == Items.POTATO || item == Items.POISONOUS_POTATO || item == Items.BAKED_POTATO) {
                result = "sold";
            }
            if (result != null) {
                buyItem = String.valueOf(BuiltInRegistries.ITEM.getKey(item));
                buyName = stack.getHoverName().getString();
                buySlot = i;
                return result;
            }
        }
        return null;
    }

    /** Title and the distinct item names in the menu (no panes), so a changed
     *  auction view can be fixed. Small and rare: see UNREADABLE_MAX. */
    private static void reportUnreadable(AbstractContainerScreen<?> cs, String title, boolean itemShown) {
        long now = System.currentTimeMillis();
        SkyAhLog.add("open: no known buy button for " + flip.name + " ('" + title + "')");
        if (unreadableSent >= UNREADABLE_MAX || (unreadableAt > 0 && now - unreadableAt < UNREADABLE_GAP_MS)) {
            return;
        }
        List<String> items = new ArrayList<>();
        List<Slot> slots = cs.getMenu().slots;
        int n = slots.size() > 36 ? slots.size() - 36 : slots.size();
        for (int i = 0; i < n && items.size() < 12; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (id.endsWith("glass_pane")) {
                continue;
            }
            String entry = id + "|" + stack.getHoverName().getString();
            if (!items.contains(entry)) {
                items.add(entry);
            }
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "unreadable");
        msg.put("auction_id", flip.auctionId);
        msg.put("via", via);
        msg.put("title", title);
        msg.put("item_shown", itemShown);
        msg.put("items", items);
        msg.put("waited_ms", now - clickAt);
        FlipsSocket socket = SkyAhMod.SOCKET;
        if (socket != null && socket.send(GSON.toJson(msg))) {
            unreadableSent++;
            unreadableAt = now;
        }
    }

    private static void send(String ended) {
        if (flip == null || screen == null) {
            reset();
            return;
        }
        long end = soldAt > 0 ? soldAt : closedAt > 0 ? closedAt : System.currentTimeMillis();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "open");
        msg.put("auction_id", flip.auctionId);
        msg.put("state", state);
        msg.put("via", via);
        msg.put("ended", ended);
        if (flip.receivedAt > 0) {
            msg.put("recv_to_click_ms", Math.max(0, clickAt - flip.receivedAt));
            msg.put("recv_to_open_ms", Math.max(0, openAt - flip.receivedAt));
        }
        msg.put("click_to_open_ms", Math.max(0, openAt - clickAt));
        if ("buyable".equals(state) && soldAt > 0) {
            msg.put("sold_after_ms", soldAt - openAt);
        }
        msg.put("watched_ms", Math.max(0, end - openAt));
        msg.put("client_ms", openAt);
        if (flip.timing != null && flip.timing.sentMs != null) {
            msg.put("frame_sent_ms", flip.timing.sentMs);
        }
        FlipsSocket socket = SkyAhMod.SOCKET;
        if (socket != null && socket.rttMs >= 0) {
            msg.put("rtt_ms", socket.rttMs);
        }
        msg.put("buy_item", buyItem);
        msg.put("buy_name", buyName);
        msg.put("buy_slot", buySlot);
        String json = GSON.toJson(msg);
        boolean sent = socket != null && socket.send(json);
        SkyAhLog.add((sent ? "open sent: " : "open (offline): ") + json);
        reset();
    }

    private static void reset() {
        flip = null;
        via = "";
        clickAt = 0L;
        screen = null;
        openAt = 0L;
        state = null;
        soldAt = 0L;
        closedAt = 0L;
        buyItem = null;
        buyName = null;
        buySlot = -1;
        openBefore = null;
        viewSeenAt = 0L;
        unreadable = false;
    }
}
