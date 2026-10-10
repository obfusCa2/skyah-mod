package net.skyah.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A button that cycles through values: left click goes forward, right
 *  click goes one step back. */
final class StepButton extends Button.Plain {
    private final Runnable back;

    StepButton(int x, int y, int w, int h, Component label, OnPress press, Runnable back) {
        super(x, y, w, h, label, press, DEFAULT_NARRATION);
        this.back = back;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (e.button() == 1 && back != null && this.active && this.visible && this.isMouseOver(e.x(), e.y())) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            back.run();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }
}
