package net.skyah.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;

/** Key state from the server ({"type":"key"}): when the 2-hour activation
 *  runs out. Activating and renewing happen on skyah.net/api only; the mod
 *  shows the time left, warns 10 minutes before and links to the website. */
public final class SkyAhKey {
    public static final String ACTIVATE_URL = "https://skyah.net/api#api-keys";
    static final String ACTIVATE_TIP = "Activation runs on the website: one bid on the auction shown there unlocks deals "
            + "for 2 hours. Same key, nothing to paste.";
    private static final long WARN_MS = 10 * 60_000L;

    /** Epoch ms when the key runs out: 0 = unknown, Long.MAX_VALUE = no limit. */
    private static volatile long activeUntil = 0L;
    private static volatile boolean renewed = false;
    private static long warnedFor = 0L;
    private static long lastRequest = 0L;
    private static long pollUntil = 0L;
    private static long lastInactiveReminder = 0L;
    private static boolean welcomed = false;
    private static int ticks = 0;

    private SkyAhKey() {
    }

    static boolean isKeyMessage(String json) {
        return json.contains("\"type\":\"key\"");
    }

    /** Called on the socket thread. */
    static void update(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            JsonElement until = o.get("active_until_ms");
            boolean permanent = o.has("permanent") && o.get("permanent").getAsBoolean();
            long next = permanent ? Long.MAX_VALUE : until != null && !until.isJsonNull() ? until.getAsLong() : 0L;
            long prev = activeUntil;
            long now = System.currentTimeMillis();
            if (prev > 0 && prev != Long.MAX_VALUE && next != Long.MAX_VALUE
                    && next - now > 60 * 60_000L && prev - now < 15 * 60_000L) {
                renewed = true;
            }
            activeUntil = next;
        } catch (Exception e) {
            SkyAhLog.add("key message ignored: " + e.getClass().getSimpleName());
        }
    }

    /** A new key was saved: the old timer no longer applies. */
    static void reset() {
        activeUntil = 0L;
        warnedFor = 0L;
    }

    /** Ms left; Long.MAX_VALUE = no limit, -1 = unknown. */
    static long remainingMs() {
        long until = activeUntil;
        if (until == 0L) {
            return -1L;
        }
        return until == Long.MAX_VALUE ? Long.MAX_VALUE : until - System.currentTimeMillis();
    }

    /** "1h 12m", "8m", or null when unknown or without limit. */
    static String leftText() {
        long ms = remainingMs();
        if (ms < 0 || ms == Long.MAX_VALUE) {
            return ms < 0 && activeUntil != 0L ? "0m" : null;
        }
        long m = ms / 60_000L;
        return m >= 60 ? (m / 60) + "h " + (m % 60) + "m" : m + "m";
    }

    static boolean isInactive() {
        FlipsSocket s = SkyAhMod.SOCKET;
        return s != null && !s.isOpen() && s.lastCloseCode == 4402;
    }

    static boolean dueSoon() {
        long ms = remainingMs();
        return activeUntil != 0L && ms != Long.MAX_VALUE && ms < WARN_MS;
    }

    /** Show the Activate/Renew button: inactive, or running out in 10 minutes. */
    static boolean needsActivation() {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        FlipsSocket s = SkyAhMod.SOCKET;
        if (cfg == null || !cfg.enabled || !cfg.hasKey() || s == null) {
            return false;
        }
        return isInactive() || (s.isOpen() && dueSoon());
    }

    /** Opens the website (Minecraft's link prompt) and checks back more often for a while. */
    static void openWebsite(Screen from) {
        pollUntil = System.currentTimeMillis() + 10 * 60_000L;
        ConfirmLinkScreen.confirmLinkNow(from, URI.create(ACTIVATE_URL));
    }

    static MutableComponent websiteLink(String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(ACTIVATE_URL))));
    }

    private static MutableComponent tag() {
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("SkyAH").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Client tick: welcome without a key, inactive reminder, expiry warning, key polling. */
    static void tick(Minecraft mc) {
        if (++ticks % 20 != 0 || mc.player == null) {
            return;
        }
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        FlipsSocket s = SkyAhMod.SOCKET;
        if (cfg == null || s == null || !cfg.enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!cfg.hasKey()) {
            if (!welcomed) {
                welcomed = true;
                Component key = SkyAhMod.MENU_KEY == null ? Component.literal("K")
                        : SkyAhMod.MENU_KEY.getTranslatedKeyMessage();
                mc.player.sendSystemMessage(tag()
                        .append(Component.literal("Welcome! Press ").withStyle(ChatFormatting.GRAY))
                        .append(key.copy().withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" to paste your key.").withStyle(ChatFormatting.GRAY)));
            }
            return;
        }
        if (isInactive()) {
            if (lastInactiveReminder == 0L || now - lastInactiveReminder >= 5 * 60_000L) {
                lastInactiveReminder = now;
                mc.player.sendSystemMessage(tag()
                        .append(Component.literal("Key inactive. ").withStyle(ChatFormatting.YELLOW))
                        .append(websiteLink("[Activate on skyah.net/api]"))
                        .append(Component.literal(" one bid, same key.").withStyle(ChatFormatting.GRAY)));
            }
            return;
        }
        lastInactiveReminder = 0L;
        if (!s.isOpen()) {
            return;
        }
        if (renewed) {
            renewed = false;
            mc.player.sendSystemMessage(tag().append(Component.literal("Key active for 2 hours.").withStyle(ChatFormatting.GREEN)));
        }
        long left = remainingMs();
        long until = activeUntil;
        if (until != 0L && until != Long.MAX_VALUE && left > 0 && left <= WARN_MS && warnedFor != until) {
            warnedFor = until;
            mc.player.sendSystemMessage(tag()
                    .append(Component.literal("Your key runs out in " + Math.max(1, left / 60_000L) + " min. ").withStyle(ChatFormatting.YELLOW))
                    .append(websiteLink("[Renew on skyah.net/api]")));
        }
        // Ask for the key state while it matters: near or after the end, or
        // after the player opened the website to renew it.
        long every = now < pollUntil ? 20_000L : 60_000L;
        boolean relevant = now < pollUntil || (until != 0L && until != Long.MAX_VALUE && left < 15 * 60_000L);
        if (relevant && now - lastRequest >= every) {
            lastRequest = now;
            s.send("{\"type\":\"key\"}");
        }
    }
}
