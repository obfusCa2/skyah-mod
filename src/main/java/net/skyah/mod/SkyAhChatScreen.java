package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Chat line settings with a live preview, built by the same code as the real line. */
public class SkyAhChatScreen extends SkyAhScreen {
    private static final long[] SOUND_STEPS = {-1L, 0L, 5_000_000L, 10_000_000L, 50_000_000L};
    private int previewY;

    public SkyAhChatScreen(Screen parent) {
        super("Chat line", parent);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        int y = 24;
        previewY = y;
        y += reservedPreviewHeight() + 3;
        para(X, y, W, Component.literal("Live example. Hover a deal in chat for every number; click opens the auction.")
                .withStyle(ChatFormatting.DARK_GRAY));
        y += 14;

        toggle(X, y, half(), "Buy price", "The price you pay.", () -> cfg.showPrice, v -> cfg.showPrice = v);
        toggle(right(), y, half(), "Sell target", "What it sold for recently: the price to list at.",
                () -> cfg.showTarget, v -> cfg.showTarget = v);
        y += GAP;
        toggle(X, y, half(), "Percent", "Profit as a percent of the buy price.", () -> cfg.showPct, v -> cfg.showPct = v);
        toggle(right(), y, half(), "Floor",
                "Cheapest comparable BIN listed right now. Red when it undercuts your sell target.",
                () -> cfg.showFloor, v -> cfg.showFloor = v);
        y += GAP;
        toggle(X, y, half(), "Sales/day",
                "How often this build sold per day (last 3 days), e.g. 14/d. Yellow under 1 a day.",
                () -> cfg.showVolume, v -> cfg.showVolume = v);
        toggle(right(), y, half(), "Sell time",
                "Realistic time until it sells at the target, e.g. ~2h. Same number as on the website.",
                () -> cfg.showSellTime, v -> cfg.showSellTime = v);
        y += GAP;
        toggle(X, y, half(), "AH fees", "Listing fee and claim tax. Already taken off the profit.",
                () -> cfg.showFee, v -> cfg.showFee = v);
        button(right(), y, half(), "Profit colors...",
                "Which color the profit gets, e.g. gold from 10M.",
                b -> open(new SkyAhRulesScreen(this)));
        y += GAP;
        button(X, y, half(), valued("Sound", soundLabel(cfg)),
                "Ping when a deal shows. 'from 10M' keeps it for the big ones.",
                b -> {
                    long next = nextSound(cfg);
                    cfg.sound = next >= 0;
                    if (next >= 0) {
                        cfg.soundMinProfit = next;
                        SkyAhSound.play(cfg.soundName);
                    }
                    changed();
                    b.setMessage(valued("Sound", soundLabel(cfg)));
                });
        button(right(), y, half(), valued("Screen alert", alertLabel(cfg)),
                "Big text in the middle of the screen when a deal arrives, e.g. DEAL +24.5M, "
                        + "with the item and your deal hotkey below. Pick a profit so it only fires "
                        + "for the big ones, then press the hotkey to open it. Several deals at once: "
                        + "it shows the best one with +N more. Try it with /skyah test. Use at your own risk.",
                b -> {
                    long next = SkyAhNum.nextStep(cfg.screenAlert ? Math.max(0L, cfg.screenAlertMinProfit) : -1L, ALERT_STEPS);
                    cfg.screenAlert = next >= 0;
                    if (next >= 0) {
                        cfg.screenAlertMinProfit = next;
                    }
                    changed();
                    b.setMessage(valued("Screen alert", alertLabel(cfg)));
                });
        y += GAP;
        button(X, y, half(), valued("Click", clickLabel(cfg)),
                "Open auction: runs /viewauction and shows the SkyAH panel next to it. "
                        + "Copy: puts the command on your clipboard.",
                b -> {
                    cfg.clickAction = "copy".equals(cfg.clickAction) ? "run" : "copy";
                    changed();
                    b.setMessage(valued("Click", clickLabel(cfg)));
                });
        toggle(right(), y, half(), "Missed deals",
                "When you connect, also show deals from the last 15 minutes that pass your filters. "
                        + "Many of them are already sold.",
                () -> cfg.showReplay, v -> cfg.showReplay = v);
        y += GAP;
        button(X, y, W, "Done", null, b -> onClose());
    }

    private static final long[] ALERT_STEPS = {-1L, 0L, 5_000_000L, 10_000_000L, 25_000_000L,
            50_000_000L, 100_000_000L};

    private static String alertLabel(SkyAhConfig cfg) {
        if (!cfg.screenAlert) {
            return "off";
        }
        return cfg.screenAlertMinProfit <= 0 ? "every deal" : "from " + FlipChat.fmt(cfg.screenAlertMinProfit);
    }

    private List<Component> previewLines(SkyAhConfig cfg) {
        List<Component> out = new ArrayList<>();
        for (Flip f : FlipChat.samples()) {
            out.add(FlipChat.line(f, cfg));
        }
        return out;
    }

    /** Room for the longest preview (every part on), so toggling never
     *  pushes the preview under the buttons. */
    private int reservedPreviewHeight() {
        SkyAhConfig all = new SkyAhConfig();
        all.showPrice = all.showTarget = all.showPct = all.showFloor = all.showFee = true;
        all.showVolume = all.showSellTime = true;
        all.colorRules = cfg().colorRules;
        all.useRealProfit = cfg().useRealProfit;
        return chatBoxHeight(W, previewLines(all));
    }

    @Override
    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        drawChatBox(g, X, previewY, W, previewLines(cfg()));
    }

    private static String soundLabel(SkyAhConfig cfg) {
        if (!cfg.sound) {
            return "off";
        }
        return cfg.soundMinProfit <= 0 ? "every deal" : "from " + FlipChat.fmt(cfg.soundMinProfit);
    }

    private static long nextSound(SkyAhConfig cfg) {
        return SkyAhNum.nextStep(cfg.sound ? Math.max(0L, cfg.soundMinProfit) : -1L, SOUND_STEPS);
    }

    private static String clickLabel(SkyAhConfig cfg) {
        return "copy".equals(cfg.clickAction) ? "copy command" : "open auction";
    }
}
