package net.skyah.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Auction house sensor (see AhSensor): while the server has switched it on
 *  for this connection, sends the seller, price and item id of listings in
 *  auction house menus, so the server can look at brand new listings sooner.
 *  Only auction menus on the main Hypixel network, only the menu part of the
 *  screen (never your inventory), only items with a seller and a price line.
 *  Never clicks, never sends anything to the game. */
public final class SkyAhListings {
    private static final AhSensor SENSOR = new AhSensor(SkyAhLog::add);
    /** Read the menu every other tick (100 ms): enough for the 300 ms quiet period. */
    private static final int EVERY_TICKS = 2;
    private static int ticks;
    private static String offNetworkLogged;

    private SkyAhListings() {
    }

    /** {"type":"ah_on"} from the server (socket thread). */
    static void serverOn() {
        SENSOR.serverOn();
    }

    /** The connection closed, was stopped or a new one opened. */
    static void ended(String why) {
        SENSOR.off(why);
    }

    /** Every game chat message (not the action bar): your own new listing. */
    static void chat(Minecraft mc, String text) {
        if (!SENSOR.isOn() || mc == null || mc.player == null || !AhSensor.selfListing(text)
                || !AhSensor.hypixelMain(McCompat.serverAddress(mc))) {
            return;
        }
        String msg = SENSOR.self(mc.player.getUUID().toString(), System.currentTimeMillis());
        FlipsSocket socket = SkyAhMod.SOCKET;
        if (msg != null && socket != null && socket.send(msg)) {
            SkyAhLog.add("auction sensor: own listing sent");
        }
    }

    /** Client tick. */
    static void tick(Minecraft mc) {
        if (!SENSOR.isOn() || ++ticks % EVERY_TICKS != 0) {
            return;
        }
        List<String> frames;
        try {
            frames = read(mc, System.currentTimeMillis());
        } catch (Exception e) {
            SkyAhLog.add("auction sensor read failed: " + e.getClass().getSimpleName());
            return;
        }
        FlipsSocket socket = SkyAhMod.SOCKET;
        for (String frame : frames) {
            if (socket == null || !socket.send(frame)) {
                SkyAhLog.add("auction sensor: not sent (offline), dropped");
            }
        }
    }

    private static List<String> read(Minecraft mc, long now) {
        Screen s = McCompat.screen(mc);
        if (!(s instanceof AbstractContainerScreen<?> cs) || mc.player == null) {
            return SENSOR.menu(s, null, null, now);
        }
        String title = AhSensor.plain(s.getTitle().getString()).trim();
        if (!AhSensor.titleAllowed(title)) {
            return SENSOR.menu(s, null, null, now);
        }
        String address = McCompat.serverAddress(mc);
        if (!AhSensor.hypixelMain(address)) {
            String why = address == null ? "singleplayer" : "other server";
            if (!why.equals(offNetworkLogged)) {
                offNetworkLogged = why;
                SkyAhLog.add("auction sensor: auction menu ignored (" + why + ")");
            }
            return SENSOR.menu(s, null, null, now);
        }
        return SENSOR.menu(s, title, listings(cs, mc.player.getInventory()), now);
    }

    /** Listings in the menu part of the screen. The last 36 slots and every
     *  slot of your own inventory are skipped before anything is read. */
    private static List<AhSensor.Item> listings(AbstractContainerScreen<?> cs, Container own) {
        List<Slot> all = cs.getMenu().slots;
        int n = all.size() - 36;
        List<AhSensor.Item> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Slot slot = all.get(i);
            if (slot.container == own) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            AhSensor.Item item = AhSensor.read(McCompat.lore(stack), null, null, stack.getCount());
            if (item != null) {
                // The item data is read for listings only.
                out.add(item.withIds(McCompat.skyblockId(stack), McCompat.skyblockUuid(stack)));
            }
        }
        return out;
    }
}
