package net.skyah.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/** Auction flips: the deals switch, the profile, min profit, the filters, the deal hotkey, the screen
 *  alert and hold mode. Rarer things (chat line, profiles, item lists, example deals, reset) are behind
 *  More. */
public class SkyAhFlipsScreen extends SkyAhScreen {
    private static final long[] ALERT_STEPS = {-1L, 0L, 5_000_000L, 10_000_000L, 25_000_000L, 50_000_000L, 100_000_000L};
    static final long[] PROFIT_STEPS = {0L, 500_000L, 1_000_000L, 2_000_000L, 5_000_000L, 10_000_000L, 25_000_000L,
            50_000_000L};
    /** True after clicking the hotkey button: the next key or mouse button is the hotkey. */
    private boolean capturing;

    public SkyAhFlipsScreen(Screen parent) {
        this(parent, false);
    }

    /** capture: start with the hotkey button waiting for a key (from the first page's hotkey line). */
    public SkyAhFlipsScreen(Screen parent, boolean capture) {
        super("Auction flips", parent);
        this.capturing = capture;
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        MenuLayout.Flips lay = MenuLayout.flips(this.height);
        text(X, lay.coverage(), () -> cfg.enabled ? coverageLine(cfg)
                : Component.literal("Deals in chat are off: nothing shows until you turn them on.").withStyle(ChatFormatting.GRAY));
        int[] r = lay.rows();
        SkyAhConfigScreen.dealsToggle(this, X, r[0], half());
        step(right(), r[0], half(), this::profileLabel, PROFILE_TIP,
                () -> useProfile(cfg, profileIndex(cfg) + 1),
                () -> useProfile(cfg, profileIndex(cfg) < 0 ? 0 : profileIndex(cfg) - 1));
        step(X, r[1], half(), () -> cfg.minProfit > 0 ? valued("Min profit", FlipChat.fmt(cfg.minProfit))
                        : valued("Min profit", "off"),
                "Only deals that earn at least this much after AH fees. Type any amount under Filters, or /skyah"
                        + " minprofit 2m.",
                () -> cfg.minProfit = SkyAhNum.nextStep(cfg.minProfit, PROFIT_STEPS),
                () -> cfg.minProfit = SkyAhNum.prevStep(cfg.minProfit, PROFIT_STEPS));
        button(X + W - half(), r[1], half(), "Filters...",
                "Min profit, min percent, max price, deal type, sell time, item lists.",
                b -> open(new SkyAhFiltersScreen(this)));
        button(X, r[2], half(), hotkeyLabel(),
                "Opens the newest deal from chat you have not opened yet (last 3 minutes), in game and inside menus"
                        + " like the AH. Click, then press a key or mouse button. Left click cancels, ESC removes it."
                        + " Also under Options > Controls > Key Binds. Use at your own risk.",
                b -> {
                    capturing = true;
                    b.setMessage(hotkeyLabel());
                });
        step(right(), r[2], half(), () -> valued("Screen alert", alertLabel(cfg)),
                "Big text in the middle of the screen when a deal arrives, e.g. DEAL +24.5M, with the item and your deal"
                        + " hotkey below. Pick a profit so it only fires for the big ones. Several deals at once: it shows"
                        + " the best one with +N more. Use at your own risk.",
                () -> setAlert(cfg, SkyAhNum.nextStep(alertValue(cfg), ALERT_STEPS)),
                () -> setAlert(cfg, SkyAhNum.prevStep(alertValue(cfg), ALERT_STEPS)));
        button(X, r[3], half(), SkyAhHoldScreen.label(cfg), "Hold the deal hotkey to skip sold deals. Use at your own risk.",
                b -> open(new SkyAhHoldScreen(this)));
        button(right(), r[3], half(), "More...", "Chat line, profiles, item lists, example deals, reset.",
                b -> open(new SkyAhFlipsMoreScreen(this)));
        text(X, lay.hint(), this::hotkeyHint);
        text(X, lay.risk(), () -> Component.literal("Use at your own risk: hotkey, alert and hold mode.")
                .withStyle(ChatFormatting.GOLD));
        button(X, lay.last(), W, "Done", null, b -> onClose());
    }

    private Component profileLabel() {
        String name = SkyAhPresets.activeName(cfg());
        return name == null ? Component.literal("Profile: ").append(Component.literal("your own").withStyle(ChatFormatting.GRAY))
                : valued("Profile", name);
    }

    private static final String PROFILE_TIP = "Click: the next profile (Starter, Balanced, Big flips, then your own)."
            + " Save and share them under More > Profiles. Same as /skyah profile <name>.";

    /** The built-in profiles, then the player's own. */
    private static List<Object> profiles(SkyAhConfig cfg) {
        List<Object> all = new ArrayList<>(SkyAhPresets.ALL);
        if (cfg.profiles != null) {
            for (SkyAhProfile p : cfg.profiles) {
                if (p != null && p.name != null) {
                    all.add(p);
                }
            }
        }
        return all;
    }

    private static int profileIndex(SkyAhConfig cfg) {
        String name = SkyAhPresets.activeName(cfg);
        List<Object> all = profiles(cfg);
        for (int i = 0; i < all.size(); i++) {
            Object o = all.get(i);
            String n = o instanceof SkyAhPresets.Preset p ? p.name() : ((SkyAhProfile) o).name;
            if (n.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private void useProfile(SkyAhConfig cfg, int i) {
        List<Object> all = profiles(cfg);
        Object o = all.get(Math.floorMod(i, all.size()));
        if (o instanceof SkyAhPresets.Preset p) {
            SkyAhPresets.apply(p, cfg);
        } else {
            ((SkyAhProfile) o).applyTo(cfg);
        }
        SkyAhMod.sendSettings();
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
            return Component.literal("> Press a key <").withStyle(ChatFormatting.YELLOW);
        }
        KeyMapping key = SkyAhMod.OPEN_KEY;
        if (key == null || key.isUnbound()) {
            return valued("Deal hotkey", "not set");
        }
        boolean taken = !SkyAhHotkey.conflicts(this.minecraft, key).isEmpty();
        return Component.literal("Deal hotkey: ").append(key.getTranslatedKeyMessage().copy()
                .withStyle(taken ? ChatFormatting.RED : ChatFormatting.GREEN));
    }

    private Component hotkeyHint() {
        if (capturing) {
            return Component.literal("Press the key or mouse button. Left click cancels, ESC removes it.")
                    .withStyle(ChatFormatting.GRAY);
        }
        KeyMapping key = SkyAhMod.OPEN_KEY;
        List<Component> other = SkyAhHotkey.conflicts(this.minecraft, key);
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
        if (key == null || key.isUnbound()) {
            return Component.literal("No hotkey: click a deal line in chat to open it.").withStyle(ChatFormatting.DARK_GRAY);
        }
        Component name = key.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GRAY);
        if (SkyAhHold.enabled()) {
            return Component.literal("Tap ").withStyle(ChatFormatting.DARK_GRAY).append(name)
                    .append(Component.literal(": newest deal. Hold: best deals in a row, skips sold ones.")
                            .withStyle(ChatFormatting.DARK_GRAY));
        }
        return Component.empty().append(name).append(Component.literal(" opens the next newest deal you have not"
                + " opened yet.").withStyle(ChatFormatting.DARK_GRAY));
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
}
