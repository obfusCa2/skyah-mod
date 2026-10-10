package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Deal hotkey: each press opens the newest deal from chat you have not
 *  opened yet. Key mappings do not fire while a screen is open, so inside
 *  chest menus (the AH) the screen events handle it. */
public final class SkyAhHotkey {
    /** Older deals are most likely sold already. */
    static final long MAX_AGE_MS = 3 * 60_000L;
    private static long lastPressAt = 0L;

    private SkyAhHotkey() {
    }

    /** In a chest menu: keyboard keys always, mouse only beyond left/right
     *  click (those move items). */
    static boolean matchesInMenu(KeyMapping key, KeyEvent e) {
        return key != null && !key.isUnbound() && key.matches(e);
    }

    static boolean matchesInMenu(KeyMapping key, MouseButtonEvent e) {
        return key != null && !key.isUnbound() && e.button() > 1 && key.matchesMouse(e);
    }

    public static void openNext(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastPressAt < 300L) {
            return;
        }
        lastPressAt = now;
        // The deal on the screen alert first, then the newest unopened one from chat.
        Flip f = SkyAhAlert.pending();
        if (f == null) {
            f = FlipChat.nextToOpen(MAX_AGE_MS);
        }
        if (f == null) {
            feedback(mc, FlipChat.anyShownSince(MAX_AGE_MS)
                    ? "No new deal since the last one you opened"
                    : "No deal in the last 3 minutes");
            return;
        }
        f.opened = true;
        if (f.auctionId == null || f.auctionId.isBlank() || "test".equals(f.auctionId)) {
            feedback(mc, "Hotkey works: it would open " + f.name + " (test deal)");
            return;
        }
        SkyAhPanel.setPending(f);
        SkyAhOpenTracker.requested(f, "hotkey");
        ClientPacketListener conn = mc.getConnection();
        if (conn != null) {
            conn.sendCommand("viewauction " + f.auctionId);
        }
        SkyAhLog.add("hotkey: open " + f.name + " (" + (now - f.shownAt) / 1000 + "s old)");
    }

    /** Action bar line, so the hotkey never spams chat. */
    private static void feedback(Minecraft mc, String text) {
        if (mc != null && mc.gui != null) {
            mc.gui.chatListener().handleSystemMessage(
                    Component.literal("[SkyAH] " + text).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Names of other key bindings on the same key or button. */
    static List<Component> conflicts(Minecraft mc, KeyMapping key) {
        List<Component> out = new ArrayList<>();
        if (mc == null || mc.options == null || key == null || key.isUnbound()) {
            return out;
        }
        for (KeyMapping other : mc.options.keyMappings) {
            if (other != key && other.same(key)) {
                out.add(Component.translatable(other.getName()));
            }
        }
        return out;
    }
}
