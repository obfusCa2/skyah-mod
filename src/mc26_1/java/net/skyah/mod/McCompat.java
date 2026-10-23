package net.skyah.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Minecraft API calls that differ between versions. This copy is for
 *  26.1.x; build.gradle picks the copy that matches minecraft_version. */
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
        mc.getChatListener().handleSystemMessage(message, overlay);
    }

    /** The open screen, or null. */
    static Screen screen(Minecraft mc) {
        return mc.screen;
    }

    static boolean titlesReady(Minecraft mc) {
        return mc.gui != null;
    }

    static void setTitleTimes(Minecraft mc, int fadeIn, int stay, int fadeOut) {
        mc.gui.setTimes(fadeIn, stay, fadeOut);
    }

    static void setTitle(Minecraft mc, Component title) {
        mc.gui.setTitle(title);
    }

    static void setSubtitle(Minecraft mc, Component subtitle) {
        mc.gui.setSubtitle(subtitle);
    }

    /** 26.1 has no foreground event yet. afterExtract wraps the whole screen
     *  draw, container screens included. */
    static void afterScreenDraw(Screen screen, ScreenDraw draw) {
        ScreenEvents.afterExtract(screen).register(
                (s, g, mouseX, mouseY, delta) -> draw.draw(s, g));
    }

    /** True while the key or mouse button is held, polled so it also works
     *  inside menus (key mappings stop updating there). */
    static boolean isDown(Minecraft mc, InputConstants.Key key) {
        if (key == null || key.getValue() < 0) {
            return false;
        }
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(mc.getWindow(), key.getValue());
    }
}
