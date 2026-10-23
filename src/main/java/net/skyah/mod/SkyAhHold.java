package net.skyah.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToLongFunction;

/** Hold mode for the deal hotkey (opt-in). A tap works as before. Held past
 *  the threshold it keeps going: when the open deal shows a potato (sold, or
 *  you cannot buy it), the next best deal opens after a short random wait. The
 *  command goes out while the sold view is still open, like pressing the
 *  hotkey inside the AH: the server replaces the view (a command right after
 *  closing a menu is not answered). A gold nugget (buyable) stays open. It
 *  stops when the key is released, no deal is left (the sold view closes), or
 *  a view cannot be read.
 *
 *  Main thread only: key events, client ticks and screen frames. */
public final class SkyAhHold {
    static final long[] AFTER_STEPS = {150L, 200L, 250L, 300L, 400L, 500L, 750L, 1000L};
    static final long[] DELAY_STEPS = {100L, 200L, 300L, 400L, 500L};
    private static final String[] DELAY_NAMES = {"Very fast", "Fast", "Normal", "Calm", "Slow"};
    /** No auction view this long after the command: the auction is gone. */
    private static final long VIEW_TIMEOUT_MS = 3_000L;
    /** At least this long between two commands, whatever the speed. */
    private static final long MIN_COMMAND_GAP_MS = 250L;

    private static long armedAt;
    private static InputConstants.Key key;
    private static boolean stopped;
    private static Flip current;
    private static long currentAt;
    /** The screen that was open when the watched deal was requested. */
    private static Screen currentFrom;
    private static String currentState;
    private static long nextAt;
    private static long lastCommandAt;
    private static boolean stepQueued;
    private static int opened;
    /** Counts holds, so a step queued in one hold never runs in the next. */
    private static int hold;

    private SkyAhHold() {
    }

    /** Build time of a test jar ("2026-10-23 21:20 UTC"), "" for releases. */
    static String stamp() {
        return FabricLoader.getInstance().getModContainer("skyah")
                .map(c -> c.getMetadata().getCustomValue("skyah:build_stamp"))
                .filter(v -> v.getType() == CustomValue.CvType.STRING)
                .map(CustomValue::getAsString)
                .orElse("");
    }

    static boolean enabled() {
        return SkyAhMod.CONFIG != null && SkyAhMod.CONFIG.holdMode;
    }

    /** A press of the deal hotkey, before the tap opens a deal. True = it is
     *  the held key repeating, so the tap is skipped. */
    public static boolean pressed(Minecraft mc) {
        if (!enabled()) {
            return false;
        }
        if (armedAt > 0L && keyDown(mc)) {
            return true;
        }
        reset();
        hold++;
        key = KeyMappingHelper.getBoundKeyOf(SkyAhMod.OPEN_KEY);
        armedAt = System.currentTimeMillis();
        return false;
    }

    /** Any deal being opened (tap, chat click or hold): the one to watch. */
    static void requested(Flip f) {
        current = f;
        currentAt = System.currentTimeMillis();
        currentFrom = McCompat.screen(Minecraft.getInstance());
        lastCommandAt = currentAt;
        currentState = null;
        nextAt = 0L;
    }

    /** Buy button of the watched deal: "buyable" (gold) or "sold" (potato). */
    static void state(Flip f, String state) {
        if (f != current) {
            return;
        }
        currentState = state;
        if ("sold".equals(state) && armedAt > 0L && !stopped) {
            long now = System.currentTimeMillis();
            nextAt = Math.max(now + jitter(delayMs()), lastCommandAt + MIN_COMMAND_GAP_MS);
            SkyAhLog.add("hold: " + f.name + " sold, next in " + (nextAt - now) + "ms if still held");
        }
    }

    /** The auction view of the watched deal shows no button we know. */
    static void unreadable(Flip f) {
        if (f != current) {
            return;
        }
        currentState = "unknown";
        if (armedAt > 0L && !stopped) {
            stopped = true;
            SkyAhLog.add("hold: stopped, view not readable for " + f.name);
        }
    }

    /** Every client tick and every frame of a container screen. */
    public static void poll(Minecraft mc) {
        if (armedAt == 0L) {
            return;
        }
        if (!enabled() || !keyDown(mc)) {
            if (opened > 0) {
                SkyAhLog.add("hold: released after " + opened + " more deal(s)");
            }
            reset();
            return;
        }
        long now = System.currentTimeMillis();
        if (stopped || stepQueued || now - armedAt < afterMs()) {
            return;
        }
        if (current == null) {
            // The tap opened nothing (for example pressed again within 300 ms).
            queueStep(mc, false);
            return;
        }
        if (!FlipChat.isBin(current)) {
            stopped = true; // auctions need a bid: the player decides
            return;
        }
        if (currentState == null) {
            if (now - currentAt < VIEW_TIMEOUT_MS) {
                return;
            }
            Screen open = McCompat.screen(mc);
            if (open instanceof AbstractContainerScreen<?> && open != currentFrom) {
                // A new menu we cannot read is open: leave it to the player.
                stopped = true;
                SkyAhLog.add("hold: stopped, no auction view for " + current.name
                        + " ('" + open.getTitle().getString() + "')");
                return;
            }
            // No new view: the auction is gone. The next one replaces what is open.
            SkyAhLog.add("hold: no view for " + current.name + " within 3s, next");
            queueStep(mc, false);
            return;
        }
        if ("sold".equals(currentState) && nextAt > 0L && now >= nextAt) {
            queueStep(mc, true);
        }
    }

    /** Runs on the next frame, outside the screen's draw: the last step may
     *  close the screen, which is not safe while it draws. */
    private static void queueStep(Minecraft mc, boolean closeSold) {
        stepQueued = true;
        int queuedIn = hold;
        mc.schedule(() -> step(mc, closeSold, queuedIn));
    }

    private static void step(Minecraft mc, boolean closeSold, int queuedIn) {
        if (queuedIn != hold) {
            return;
        }
        stepQueued = false;
        if (armedAt == 0L || stopped || !keyDown(mc)) {
            return;
        }
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        List<Flip> shown = FlipChat.shownSince(SkyAhHotkey.MAX_AGE_MS);
        Flip next = best(shown, System.currentTimeMillis(), f -> FlipChat.profitOf(f, cfg));
        if (next == null) {
            stopped = true;
            long done = shown.stream().filter(f -> f.opened).count();
            long bids = shown.stream().filter(f -> !f.opened && !FlipChat.isBin(f)).count();
            SkyAhLog.add("hold: no deal left after " + opened + " more deal(s) (" + shown.size()
                    + " in chat: " + done + " opened, " + bids + " auctions)");
            Screen s = McCompat.screen(mc);
            if (closeSold && mc.player != null && s instanceof AbstractContainerScreen<?>
                    && s.getTitle().getString().contains("Auction View")) {
                mc.player.closeContainer();
            }
            return;
        }
        opened++;
        SkyAhHotkey.open(mc, next, "hold");
    }

    /** The unopened BIN deal worth the most on average: profit times the
     *  chance it is still for sale. No purse check here: the sidebar value
     *  can be off, and "within purse" already filters what chat shows. */
    static Flip best(List<Flip> flips, long now, ToLongFunction<Flip> profit) {
        Flip best = null;
        double bestScore = -1;
        for (Flip f : flips) {
            if (f.opened || !FlipChat.isBin(f) || f.auctionId == null || f.auctionId.isBlank()
                    || "test".equals(f.auctionId)) {
                continue;
            }
            long at = f.receivedAt > 0 ? f.receivedAt : f.shownAt;
            double s = score(profit.applyAsLong(f), now - at);
            if (s > bestScore) {
                best = f;
                bestScore = s;
            }
        }
        return best;
    }

    static double score(long profit, long ageMs) {
        return Math.max(0L, profit) * chance(profit, ageMs);
    }

    /** Chance a deal is still buyable when its view opens, measured on deals
     *  opened through the mod: about 73% under 2M profit, 34% at 15M, 16% from
     *  about 50M (the big ones go fast), and a little lower the older it is
     *  (about 70% of that after a minute). */
    static double chance(long profit, long ageMs) {
        double p = 0.73 - 0.155 * Math.log(Math.max(profit, 1_200_000L) / 1_200_000.0);
        double age = 1.0 - 0.06 * Math.log1p(Math.max(0L, ageMs) / 1000.0);
        return Math.max(0.16, Math.min(0.73, p)) * Math.max(0.65, age);
    }

    /** base ±25%, more often near the middle (sum of two random numbers). */
    static long jitter(long base) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return Math.round(base * (0.75 + 0.25 * (r.nextDouble() + r.nextDouble())));
    }

    static long afterMs() {
        return nearest(SkyAhMod.CONFIG.holdAfterMs, AFTER_STEPS);
    }

    static long delayMs() {
        return nearest(SkyAhMod.CONFIG.holdDelayMs, DELAY_STEPS);
    }

    static String speedName(long delayMs) {
        long v = nearest(delayMs, DELAY_STEPS);
        for (int i = 0; i < DELAY_STEPS.length; i++) {
            if (DELAY_STEPS[i] == v) {
                return DELAY_NAMES[i];
            }
        }
        return "Normal";
    }

    /** The step closest to v (hand-edited config files). */
    static long nearest(long v, long[] steps) {
        long best = steps[0];
        for (long s : steps) {
            if (Math.abs(s - v) < Math.abs(best - v)) {
                best = s;
            }
        }
        return best;
    }

    private static boolean keyDown(Minecraft mc) {
        return mc != null && mc.getWindow() != null && McCompat.isDown(mc, key);
    }

    private static void reset() {
        armedAt = 0L;
        key = null;
        stopped = false;
        current = null;
        currentAt = 0L;
        currentFrom = null;
        currentState = null;
        nextAt = 0L;
        stepQueued = false;
        opened = 0;
    }
}
