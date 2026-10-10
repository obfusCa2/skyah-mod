package net.skyah.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Plays a short notification sound for new flips. */
public final class SkyAhSound {
    /** Config value and button label, in cycle order. */
    private static final String[][] SOUNDS = {
            {"NOTE_BLOCK_PLING", "Pling"}, {"NOTE_BLOCK_BELL", "Bell"},
            {"EXPERIENCE_ORB_PICKUP", "Orb"}, {"UI_BUTTON_CLICK", "Click"}};

    private SkyAhSound() {
    }

    private static int index(String name) {
        for (int i = 0; i < SOUNDS.length; i++) {
            if (SOUNDS[i][0].equals(name)) {
                return i;
            }
        }
        return 0;
    }

    static String label(String name) {
        return SOUNDS[index(name)][1];
    }

    /** The sound `delta` steps away from `name`, wrapping around. */
    static String next(String name, int delta) {
        return SOUNDS[Math.floorMod(index(name) + delta, SOUNDS.length)][0];
    }

    public static void play(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        try {
            SimpleSoundInstance sound = switch (SOUNDS[index(name)][0]) {
                case "NOTE_BLOCK_BELL" -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.0f);
                case "EXPERIENCE_ORB_PICKUP" -> SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f);
                case "UI_BUTTON_CLICK" -> SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f);
                default -> SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.0f);
            };
            mc.getSoundManager().play(sound);
        } catch (Exception ignored) {
        }
    }
}
