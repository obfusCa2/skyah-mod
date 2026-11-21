package net.skyah.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bazaar tracking (record only): while the server has switched it on for
 *  this connection ({"type":"bz_on"}), records your own bazaar chat lines and
 *  four bazaar menus and sends them to the server, which works out orders and
 *  profit. Never clicks, never sends anything to the game. */
public final class SkyAhBazaarRecorder {
    static final Set<String> MENUS = Set.of(
            "Confirm Buy Order", "Confirm Sell Offer", "Order options", "Your Bazaar Orders");
    /** A menu counts once its slots stayed the same this long (they arrive after the screen opens). */
    private static final long SETTLE_MS = 250L;
    private static final Pattern ITEM_ID = Pattern.compile("^[A-Za-z0-9_:;.\\-]{1,80}$");

    private static final BazaarRecorder REC = new BazaarRecorder(
            UUID.randomUUID().toString().replace("-", "").substring(0, 16), SkyAhLog::add);

    private static Screen screen;
    private static String title;
    private static List<Map<String, Object>> slots;
    private static String key;
    private static String sentKey;
    private static long changedAt;

    private SkyAhBazaarRecorder() {
    }

    /** {"type":"bz_on"} from the server (socket thread). */
    static void serverOn() {
        REC.serverOn();
    }

    static void connected() {
        REC.connected(System.currentTimeMillis());
    }

    static void disconnected() {
        REC.disconnected(System.currentTimeMillis());
    }

    /** The socket was stopped on purpose (mod turned off, new key, reconnect). */
    static void stopped() {
        REC.off("connection stopped");
    }

    /** Every game chat message (not the action bar). */
    static void chat(String text) {
        if (!REC.recording()) {
            return;
        }
        for (String line : REC.chat(text, System.currentTimeMillis(), SkyAhPurse.get())) {
            SkyAhLog.add("bazaar chat: " + line);
        }
    }

    /** Client tick: menu snapshots and sending. */
    static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        REC.timers(now);
        if (!REC.recording()) {
            screen = null;
            forget();
            return;
        }
        try {
            readMenu(mc, now);
        } catch (Exception e) {
            SkyAhLog.add("bazaar menu read failed: " + e.getClass().getSimpleName());
        }
        FlipsSocket socket = SkyAhMod.SOCKET;
        if (socket != null) {
            int sent = REC.flush(now, socket::send);
            if (sent > 0) {
                SkyAhLog.add("bazaar: sent " + sent + " events (" + REC.queued() + " queued)");
            }
        }
    }

    private static void readMenu(Minecraft mc, long now) {
        Screen s = McCompat.screen(mc);
        if (s != screen) {
            finish();
            screen = s;
            forget();
        }
        if (!(s instanceof AbstractContainerScreen<?> cs)) {
            return;
        }
        String t = BazaarRecorder.plain(s.getTitle().getString()).trim();
        if (!MENUS.contains(t)) {
            return;
        }
        List<Map<String, Object>> read = readSlots(cs);
        String k = t + "|" + read;
        if (!k.equals(key)) {
            title = t;
            slots = read;
            key = k;
            changedAt = now;
            return;
        }
        if (now - changedAt >= SETTLE_MS && !k.equals(sentKey)) {
            record();
        }
    }

    /** The screen closed or changed: keep its last state if it was not sent yet. */
    private static void finish() {
        if (key != null && !key.equals(sentKey)) {
            record();
        }
    }

    private static void record() {
        sentKey = key;
        if (slots == null || slots.isEmpty()) {
            return;
        }
        if (REC.menu(title, slots, changedAt, SkyAhPurse.get())) {
            SkyAhLog.add("bazaar menu: " + title + " (" + slots.size() + " items)");
        }
    }

    private static void forget() {
        title = null;
        slots = null;
        key = null;
        sentKey = null;
        changedAt = 0L;
    }

    /** The menu part of the screen (not your inventory), without filler panes. */
    private static List<Map<String, Object>> readSlots(AbstractContainerScreen<?> cs) {
        List<Slot> all = cs.getMenu().slots;
        int n = all.size() > 36 ? all.size() - 36 : all.size();
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ItemStack stack = all.get(i).getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String name = BazaarRecorder.plain(stack.getHoverName().getString());
            String mcId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (mcId.endsWith("glass_pane") && name.isBlank()) {
                continue;
            }
            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("i", i);
            String id = McCompat.skyblockId(stack);
            if (id != null && ITEM_ID.matcher(id).matches()) {
                slot.put("id", id);
            }
            slot.put("name", BazaarRecorder.cut(name, BazaarRecorder.NAME_MAX));
            slot.put("count", stack.getCount());
            List<String> lore = new ArrayList<>();
            for (String line : McCompat.lore(stack)) {
                if (lore.size() >= BazaarRecorder.LORE_LINES) {
                    break;
                }
                lore.add(BazaarRecorder.cut(BazaarRecorder.plain(line), BazaarRecorder.LORE_LINE_MAX));
            }
            slot.put("lore", lore);
            out.add(slot);
        }
        return out;
    }
}
