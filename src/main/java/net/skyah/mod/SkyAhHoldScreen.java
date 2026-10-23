package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Hold mode settings: on/off (turning it on takes a second click that names
 *  the risk), how long to hold before it starts, and how fast it moves on.
 *  Kept short on purpose: the details are in the tooltips. */
public class SkyAhHoldScreen extends SkyAhScreen {
    private boolean confirmOn = false;

    public SkyAhHoldScreen(Screen parent) {
        super("Hold mode", parent);
    }

    /** "Hold mode: ON..." on the main screen ("..." = opens this screen). */
    static Component label(SkyAhConfig cfg) {
        return onOff("Hold mode", cfg.holdMode).copy().append(Component.literal("..."));
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        confirmOn = false;
        int y = 24;
        y += para(X, y, W, Component.literal("Hold the deal hotkey: sold deals are skipped and the "
                + "next best one opens. Release to stop.").withStyle(ChatFormatting.GRAY)) + 8;
        button(X, y, W, onOff("Hold mode", cfg.holdMode),
                "Tap: opens the newest deal, as before. Hold: a sold deal (potato) makes way for "
                        + "the next best one, a buyable one (gold nugget) stays open. Next best = most "
                        + "profit, weighted by the chance it is still for sale. Turning it on takes a second click.",
                b -> {
                    if (!cfg.holdMode && !confirmOn) {
                        confirmOn = true;
                        b.setMessage(Component.literal("Click again to turn on, at your own risk")
                                .withStyle(ChatFormatting.YELLOW));
                        return;
                    }
                    confirmOn = false;
                    cfg.holdMode = !cfg.holdMode;
                    changed();
                    SkyAhLog.add("hold mode: " + (cfg.holdMode ? "on" : "off"));
                    b.setMessage(onOff("Hold mode", cfg.holdMode));
                });
        y += GAP;
        step(X, y, half(), () -> valued("Hold for", SkyAhHold.afterMs() + " ms"),
                "How long you hold the key before it starts.",
                () -> cfg.holdAfterMs = (int) SkyAhNum.nextStep(SkyAhHold.afterMs(), SkyAhHold.AFTER_STEPS),
                () -> cfg.holdAfterMs = (int) SkyAhNum.prevStep(SkyAhHold.afterMs(), SkyAhHold.AFTER_STEPS));
        step(right(), y, half(), () -> valued("Speed", SkyAhHold.speedName(cfg.holdDelayMs)),
                "Wait on a sold deal before the next one opens: Very fast ~100 ms, Fast ~200, "
                        + "Normal ~300, Calm ~400, Slow ~500. Each wait varies a little.",
                () -> cfg.holdDelayMs = (int) SkyAhNum.nextStep(SkyAhHold.delayMs(), SkyAhHold.DELAY_STEPS),
                () -> cfg.holdDelayMs = (int) SkyAhNum.prevStep(SkyAhHold.delayMs(), SkyAhHold.DELAY_STEPS));
        y += H + 4;
        text(X, y, () -> Component.literal("Use at your own risk").withStyle(ChatFormatting.GOLD));
        y += 14;
        button(X, y, W, "Back", null, b -> onClose());
    }
}
