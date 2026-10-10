package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Optional screen alert: "DEAL +24.5M" as a title, item and hotkey below.
 *  While one is up, a weaker deal only raises "+N more" and a better one
 *  replaces it. The deal hotkey opens the one on screen (pending()). */
public final class SkyAhAlert {
    /** Ticks: short fade in, 2 s on screen, short fade out. */
    private static final int FADE_IN = 3;
    private static final int STAY = 40;
    private static final int FADE_OUT = 10;
    /** An alert counts as "on screen" for this long after it was shown. */
    private static final long VISIBLE_MS = (FADE_IN + STAY + FADE_OUT) * 50L;

    private static Flip current;
    private static long currentProfit;
    private static long shownAt;
    private static int more;

    private SkyAhAlert() {
    }

    public static synchronized void show(Flip flip, SkyAhConfig cfg, long profit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null || mc.gui.hud == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean up = current != null && !current.opened && now - shownAt < VISIBLE_MS;
        if (up && profit <= currentProfit) {
            more++;
            mc.gui.hud.setSubtitle(subtitle(current));
            return;
        }
        more = up ? more + 1 : 0;
        current = flip;
        currentProfit = profit;
        shownAt = now;
        ChatFormatting tier = FlipChat.ruleColor(cfg, profit, FlipChat.pctOf(flip, profit));
        Component title = Component.literal("DEAL ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(FlipChat.signed(profit)).withStyle(tier, ChatFormatting.BOLD));
        mc.gui.hud.setTimes(FADE_IN, STAY, FADE_OUT);
        mc.gui.hud.setSubtitle(subtitle(flip));
        mc.gui.hud.setTitle(title);
    }

    /** The deal on screen right now, if it was not opened yet. */
    public static synchronized Flip pending() {
        if (current == null || current.opened || System.currentTimeMillis() - shownAt >= VISIBLE_MS) {
            return null;
        }
        return current;
    }

    private static Component subtitle(Flip flip) {
        MutableComponent sub = Component.empty()
                .append(FlipChat.title(flip))
                .append(Component.literal(" for " + FlipChat.fmt(flip.price)).withStyle(ChatFormatting.GRAY));
        KeyMapping key = SkyAhMod.OPEN_KEY;
        if (key != null && !key.isUnbound()) {
            sub.append(Component.literal("  [").withStyle(ChatFormatting.DARK_GRAY))
                    .append(key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("] open").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (more > 0) {
            sub.append(Component.literal("  +" + more + " more").withStyle(ChatFormatting.GOLD));
        }
        return sub;
    }
}
