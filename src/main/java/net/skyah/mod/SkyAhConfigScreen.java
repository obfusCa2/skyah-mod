package net.skyah.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/** Main screen (K): status, the filters in one line, and what players change
 *  most. The key is activated on the website only; this links there. */
public class SkyAhConfigScreen extends SkyAhScreen {
    private static final long[] ALERT_STEPS = {-1L, 0L, 5_000_000L, 10_000_000L, 25_000_000L, 50_000_000L, 100_000_000L};
    private boolean confirmReset = false;
    /** True after clicking the hotkey button: the next key or mouse button is the hotkey. */
    private boolean capturing = false;

    public SkyAhConfigScreen() {
        super("SkyAH", null);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        confirmReset = false;
        capturing = false;
        int y = 24;
        text(X, y, SkyAhScreen::statusLine);
        y += 13;
        text(X, y, () -> filterSummary(cfg));
        y += 13;
        text(X, y, () -> coverageLine(cfg));
        y += 18;

        if (SkyAhKey.needsActivation()) {
            button(X, y, W, Component.literal(SkyAhKey.isInactive() ? "Activate key on skyah.net/api" : "Renew key on skyah.net/api")
                    .withStyle(ChatFormatting.YELLOW), SkyAhKey.ACTIVATE_TIP, b -> SkyAhKey.openWebsite(this));
            y += GAP;
        }
        toggle(X, y, half(), "Deals in chat",
                "Turns the SkyAH deal feed on or off. Off also closes the connection.",
                () -> cfg.enabled, v -> {
                    cfg.enabled = v;
                    if (v) {
                        SkyAhMod.SOCKET.start();
                    } else {
                        SkyAhMod.SOCKET.stop();
                    }
                });
        button(right(), y, half(), "Filters...",
                "Which deals show up: profit, percent, price, sell time, item lists.",
                b -> open(new SkyAhFiltersScreen(this)));
        y += GAP;
        button(X, y, half(), "Chat line...",
                "What a deal line shows, with a live example. Colors, sound and click.",
                b -> open(new SkyAhChatScreen(this)));
        button(right(), y, half(), "Profiles...",
                "Ready-made and your own filter sets: switch and share.",
                b -> open(new SkyAhProfilesScreen(this)));
        y += GAP;
        Component account = cfg.hasKey()
                ? Component.literal("Account & key...")
                : Component.literal("Account & key (!)").withStyle(ChatFormatting.YELLOW);
        button(X, y, half(), account, "Your key and the website.",
                b -> open(new SkyAhAccountScreen(this)));
        button(right(), y, half(), "Show example deals",
                "Closes this menu and puts two example deals in chat, like /skyah test.",
                b -> {
                    onClose();
                    for (Flip f : FlipChat.samples()) {
                        f.auctionId = "test";
                        FlipChat.display(f, cfg);
                    }
                });
        y += GAP;
        button(X, y, W, hotkeyLabel(),
                "Opens the newest deal from chat you have not opened yet (last 3 minutes). "
                        + "Works in game and inside menus like the AH. Click, then press any key or "
                        + "mouse button. Left click cancels, ESC removes the hotkey. "
                        + "Also under Options > Controls > Key Binds. Use at your own risk.",
                b -> {
                    capturing = true;
                    b.setMessage(hotkeyLabel());
                });
        y += GAP;
        // Right under the hotkey: the alert shows the deal, the hotkey opens it.
        step(X, y, W, () -> valued("Screen alert", alertLabel(cfg)),
                "Big text in the middle of the screen when a deal arrives, e.g. DEAL +24.5M, "
                        + "with the item and your deal hotkey below. Pick a profit so it only fires "
                        + "for the big ones, then press the hotkey to open it. Several deals at once: "
                        + "it shows the best one with +N more. Use at your own risk.",
                () -> setAlert(cfg, SkyAhNum.nextStep(alertValue(cfg), ALERT_STEPS)),
                () -> setAlert(cfg, SkyAhNum.prevStep(alertValue(cfg), ALERT_STEPS)));
        y += H + 3;
        text(X, y, this::hotkeyHint);
        y += 12;
        text(X, y, () -> Component.literal("Use at your own risk").withStyle(ChatFormatting.GOLD));
        y += 14;
        button(X, y, half(), "Reset to recommended",
                "Sets filters, chat line, colors and sound back to the recommended setup "
                        + "(1M+ profit, 5%+, sound from 10M). Item lists, profiles and your key stay. Click twice.",
                b -> {
                    if (!confirmReset) {
                        confirmReset = true;
                        b.setMessage(Component.literal("Click again to reset").withStyle(ChatFormatting.YELLOW));
                        return;
                    }
                    confirmReset = false;
                    cfg.applyRecommended();
                    changed();
                    b.setMessage(Component.literal("Recommended setup applied").withStyle(ChatFormatting.GREEN));
                });
        button(right(), y, half(), "Done", null, b -> onClose());
    }

    /** Version on the right of the title row; yellow with the newest one when outdated. */
    @Override
    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Component v = versionText();
        g.text(this.font, v, X + W - this.font.width(v), 8, WHITE);
    }

    static Component versionText() {
        String latest = SkyAhMod.LATEST;
        if (latest != null && SkyAhNum.isNewer(latest, SkyAhMod.VERSION)) {
            return Component.literal("v" + SkyAhMod.VERSION + " · new: v" + latest).withStyle(ChatFormatting.YELLOW);
        }
        return Component.literal("v" + SkyAhMod.VERSION).withStyle(ChatFormatting.DARK_GRAY);
    }

    private static long alertValue(SkyAhConfig cfg) {
        return cfg.screenAlert ? Math.max(0L, cfg.screenAlertMinProfit) : -1L;
    }

    private static void setAlert(SkyAhConfig cfg, long v) {
        cfg.screenAlert = v >= 0;
        if (v >= 0) {
            cfg.screenAlertMinProfit = v;
        }
    }

    private static String alertLabel(SkyAhConfig cfg) {
        if (!cfg.screenAlert) {
            return "off";
        }
        return cfg.screenAlertMinProfit <= 0 ? "every deal" : "from " + FlipChat.fmt(cfg.screenAlertMinProfit);
    }

    private Component hotkeyLabel() {
        if (capturing) {
            return Component.literal("> Press a key or mouse button <").withStyle(ChatFormatting.YELLOW);
        }
        KeyMapping key = SkyAhMod.OPEN_KEY;
        if (key == null || key.isUnbound()) {
            return Component.literal("Deal hotkey: ").append(
                    Component.literal("not set, click to choose").withStyle(ChatFormatting.YELLOW));
        }
        return Component.literal("Deal hotkey: ").append(key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GREEN));
    }

    private Component hotkeyHint() {
        if (capturing) {
            return Component.literal("Left click cancels, ESC removes the hotkey.").withStyle(ChatFormatting.GRAY);
        }
        List<Component> other = SkyAhHotkey.conflicts(this.minecraft, SkyAhMod.OPEN_KEY);
        if (!other.isEmpty()) {
            MutableComponent out = Component.literal("Also used by: ").withStyle(ChatFormatting.RED);
            for (int i = 0; i < other.size(); i++) {
                if (i > 0) {
                    out.append(Component.literal(", ").withStyle(ChatFormatting.RED));
                }
                out.append(other.get(i).copy().withStyle(ChatFormatting.RED));
            }
            return out.append(Component.literal(". Pick another key.").withStyle(ChatFormatting.RED));
        }
        return Component.literal("Each press opens the next newest deal you have not opened yet.")
                .withStyle(ChatFormatting.DARK_GRAY);
    }

    private void setHotkey(InputConstants.Key key) {
        capturing = false;
        if (key != null) {
            SkyAhMod.OPEN_KEY.setKey(key);
            KeyMapping.resetMapping();
            this.minecraft.options.save();
            SkyAhLog.add("deal hotkey: " + SkyAhMod.OPEN_KEY.saveString());
        }
        this.rebuildWidgets();
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (capturing) {
            setHotkey(e.key() == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : InputConstants.getKey(e));
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (capturing) {
            // Left click is attack/destroy and picks items in menus: it cancels instead.
            setHotkey(e.button() == 0 ? null : InputConstants.Type.MOUSE.getOrCreate(e.button()));
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    /** "Filters (Balanced): 1M+ profit · 5%+ · any price". */
    static Component filterSummary(SkyAhConfig cfg) {
        List<String> parts = new ArrayList<>();
        parts.add(cfg.minProfit > 0 ? FlipChat.fmt(cfg.minProfit) + "+ profit" : "any profit");
        if (cfg.minPct > 0) {
            parts.add(trimPct(cfg.minPct) + "%+");
        }
        if (cfg.maxPriceFromPurse && cfg.maxPrice <= 0) {
            parts.add("within purse");
        } else {
            parts.add(cfg.maxPrice > 0 ? "up to " + FlipChat.fmt(cfg.maxPrice) : "any price");
            if (cfg.maxPriceFromPurse) {
                parts.add("within purse");
            }
        }
        if (cfg.maxSellTimeS > 0) {
            parts.add("sells within " + SkyAhFiltersScreen.sellWithinLabel(cfg.maxSellTimeS));
        }
        if (cfg.showBin && !cfg.showAuctions) {
            parts.add("BIN only");
        } else if (!cfg.showBin && cfg.showAuctions) {
            parts.add("auctions only");
        } else if (!cfg.showBin) {
            parts.add("no deal type");
        }
        if (cfg.whitelist != null && !cfg.whitelist.isBlank()) {
            parts.add("whitelist");
        }
        if (cfg.blacklist != null && !cfg.blacklist.isBlank()) {
            parts.add("blacklist");
        }
        String profile = SkyAhPresets.activeName(cfg);
        return Component.literal(profile == null ? "Filters: " : "Filters (" + profile + "): ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.join(" · ", parts)).withStyle(ChatFormatting.WHITE));
    }
}
