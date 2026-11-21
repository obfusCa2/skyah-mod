package net.skyah.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** Minecraft API calls that differ between versions. This copy is for 26.3
 *  and newer; build.gradle picks the copy that matches minecraft_version. */
final class McCompat {
    private McCompat() {
    }

    /** Called after a screen has drawn its contents. */
    @FunctionalInterface
    interface ScreenDraw {
        void draw(Screen screen, GuiGraphicsExtractor g);
    }


    /** System message in chat, or in the action bar when overlay is true. */
    static void systemMessage(Minecraft mc, Component message, boolean overlay) {
        mc.gui.chatListener().handleSystemMessage(message, overlay);
    }

    /** The open screen, or null. */
    static Screen screen(Minecraft mc) {
        return mc.gui.screen();
    }

    static boolean titlesReady(Minecraft mc) {
        return mc.gui != null && mc.gui.hud != null;
    }

    static void setTitleTimes(Minecraft mc, int fadeIn, int stay, int fadeOut) {
        mc.gui.hud.setTimes(fadeIn, stay, fadeOut);
    }

    static void setTitle(Minecraft mc, Component title) {
        mc.gui.hud.setTitle(title);
    }

    static void setSubtitle(Minecraft mc, Component subtitle) {
        mc.gui.hud.setSubtitle(subtitle);
    }

    /** Runs after the screen's foreground, which also fires inside container
     *  screens such as the auction house. */
    static void afterScreenDraw(Screen screen, ScreenDraw draw) {
        ScreenEvents.afterForeground(screen).register(
                (s, g, mouseX, mouseY, delta) -> draw.draw(s, g));
    }

    /** The GUI scale (screen pixels per GUI pixel). */
    static int guiScale(Minecraft mc) {
        return mc.getWindow() == null ? 0 : mc.getWindow().getGuiScale();
    }

    /** Draws what follows scaled by s around the origin, until popScale. */
    static void pushScale(GuiGraphicsExtractor g, float s) {
        g.pose().pushMatrix();
        g.pose().scale(s, s);
    }

    static void popScale(GuiGraphicsExtractor g) {
        g.pose().popMatrix();
    }

    /** True while the key or mouse button is held, polled so it also works
     *  inside menus (key mappings stop updating there). 26.3 only reports the
     *  left, right and middle mouse button. */
    static boolean isDown(Minecraft mc, InputConstants.Key key) {
        if (key == null || key.getValue() < 0) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return switch (key.getValue()) {
                case 0 -> mc.mouseHandler.isLeftPressed();
                case 1 -> mc.mouseHandler.isRightPressed();
                case 2 -> mc.mouseHandler.isMiddlePressed();
                default -> false;
            };
        }
        return key.getType() == InputConstants.Type.KEYBOARD && InputConstants.isKeyDown(key.getValue());
    }

    /** Lore lines of an item as text (formatting removed by the caller). */
    static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        List<String> out = new ArrayList<>();
        if (lore != null) {
            for (Component line : lore.lines()) {
                out.add(line.getString());
            }
        }
        return out;
    }

    /** The SkyBlock item id from the item's custom data, or null. */
    static String skyblockId(ItemStack stack) {
        return customString(stack, "id");
    }

    /** The item's own uuid from its custom data (unique items only), or null. */
    static String skyblockUuid(ItemStack stack) {
        return customString(stack, "uuid");
    }

    private static String customString(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        String value = data.copyTag().getStringOr(key, "");
        return value.isEmpty() ? null : value;
    }

    /** Address of the server you play on, null in singleplayer. */
    static String serverAddress(Minecraft mc) {
        ServerData server = mc.getCurrentServer();
        return server == null ? null : server.ip;
    }
}
