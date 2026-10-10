package net.skyah.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Plays a short notification sound for new flips. */
public final class SkyAhSound {
    public static final String[] NAMES = {"NOTE_BLOCK_PLING", "UI_BUTTON_CLICK"};

    private SkyAhSound() {
    }

    public static void play(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        Holder<SoundEvent> event = "UI_BUTTON_CLICK".equals(name)
                ? SoundEvents.UI_BUTTON_CLICK
                : SoundEvents.NOTE_BLOCK_PLING;
        try {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0f));
        } catch (Exception ignored) {
        }
    }
}
