package net.skyah.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** The SkyAH menu's first page (K, /skyah): is everything working and what to do. The status line, the
 *  problems with their fix (at most three), then the areas: Auction flips with its main switch and one
 *  gray line, and Settings. The key is activated on the website only; this links there. */
public class SkyAhConfigScreen extends SkyAhScreen {
    static final String WEBSITE = "https://skyah.net/api";

    public SkyAhConfigScreen() {
        super("SkyAH", null);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        List<HomeProblems.Problem> problems = HomeProblems.shown(state());
        List<Boolean> buttons = new ArrayList<>();
        for (HomeProblems.Problem p : problems) {
            buttons.add(p.fix() != HomeProblems.Fix.NONE);
        }
        MenuLayout.Home lay = MenuLayout.home(this.height, buttons);
        text(X, lay.status(), SkyAhScreen::statusLine);
        if (lay.allSet() >= 0) {
            text(X, lay.allSet(), () -> allSet(cfg));
        }
        for (int i = 0; i < problems.size(); i++) {
            HomeProblems.Problem p = problems.get(i);
            int y = lay.problems().get(i);
            Component label = Component.literal(p.text()).withStyle(color(p.color()));
            String tip = tip(p);
            if (p.fix() == HomeProblems.Fix.NONE) {
                text(X, y + 1, () -> label);
                tipArea(X, y, Math.min(W, this.font.width(label)), 10, () -> tip);
            } else {
                button(X, y, W, label, tip, b -> fix(p.fix()));
            }
        }
        button(X, lay.flips(), half(), "Auction flips...",
                "Profile, min profit, filters, deal hotkey, screen alert. More: chat line, profiles, item lists.",
                b -> open(new SkyAhFlipsScreen(this)));
        dealsToggle(this, right(), lay.flips(), half());
        text(X, lay.flipsLine(), () -> flipsLine(cfg));
        button(X, lay.last(), half(), "Settings...", "Your key, the website, the panels on screen.",
                b -> open(new SkyAhSettingsScreen(this)));
        button(right(), lay.last(), half(), "Done", null, b -> onClose());
    }

    /** The "Deals in chat" switch, the same on this page and on Auction flips. */
    static void dealsToggle(SkyAhScreen s, int x, int y, int w) {
        s.toggle(x, y, w, "Deals in chat",
                "Shows or hides deals in chat. Same as /skyah flip. The mod stays connected and keeps sharing market data.", () -> SkyAhMod.CONFIG.enabled, v -> {
            SkyAhMod.setDeals(v);
            s.refresh();
        });
    }

    /** A chat color by name ("yellow", "gray", ...), white for anything else. */
    static ChatFormatting color(String name) {
        if (name == null) {
            return ChatFormatting.WHITE;
        }
        return switch (name) {
            case "aqua" -> ChatFormatting.AQUA;
            case "gold" -> ChatFormatting.GOLD;
            case "red" -> ChatFormatting.RED;
            case "green" -> ChatFormatting.GREEN;
            case "gray" -> ChatFormatting.GRAY;
            case "yellow" -> ChatFormatting.YELLOW;
            case "dark_gray" -> ChatFormatting.DARK_GRAY;
            default -> ChatFormatting.WHITE;
        };
    }

    static HomeProblems.State state() {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        int[] c = FlipChat.coverage(cfg);
        KeyMapping key = SkyAhMod.OPEN_KEY;
        String hotkey = key == null || key.isUnbound() ? null : key.getTranslatedKeyMessage().getString();
        List<Component> other = SkyAhHotkey.conflicts(net.minecraft.client.Minecraft.getInstance(), key);
        return new HomeProblems.State(SkyAhMod.connectionWanted(), conn(), SkyAhKey.dueSoon(), FreeMode.delayS(),
                cfg.enabled, c[0], c[1], hotkey, other.isEmpty() ? null : other.get(0).getString(),
                SkyAhMod.VERSION, SkyAhMod.LATEST);
    }

    private static String tip(HomeProblems.Problem p) {
        return switch (p.kind()) {
            case KEY_INACTIVE, KEY_DUE -> SkyAhKey.activateTip();
            case FREE, FREE_CLOSED -> "Without a key you are in free mode: every deal shows up "
                    + FreeMode.delayText(FreeMode.delayS()) + " after players with a key see it. The key is free and"
                    + " needs a linked Minecraft account. Copy it there, then Settings > Paste key.";
            case KEY_REJECTED -> "The saved key does not work (deleted or rotated on the website). Copy the key on"
                    + " skyah.net/api, then click Paste key under Settings.";
            case KEY_ELSEWHERE -> "Your key is connected in another Minecraft. This one connects again by itself once"
                    + " the other one is closed.";
            case FILTERS_EMPTY -> "None of the last deals passes your filters, so nothing would show.";
            case NO_HOTKEY -> "Sets " + HomeProblems.DEFAULT_HOTKEY + " as the deal hotkey: it opens the newest deal"
                    + " from chat. Pick another key under Auction flips. Use at your own risk.";
            case HOTKEY_TAKEN -> "Two actions on one key. Click, then press another key.";
            case UPDATE -> "Your version: v" + SkyAhMod.VERSION + ". The download is on the website.";
        };
    }

    private void fix(HomeProblems.Fix f) {
        switch (f) {
            case ACTIVATE -> SkyAhKey.openWebsite(this);
            case WEBSITE, UPDATE -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(f == HomeProblems.Fix.UPDATE
                    ? WEBSITE : SkyAhKey.ACTIVATE_URL));
            case SETTINGS -> open(new SkyAhSettingsScreen(this));
            case FILTERS -> open(new SkyAhFiltersScreen(new SkyAhFlipsScreen(this)));
            case HOTKEY_DEFAULT -> {
                SkyAhMod.OPEN_KEY.setKey(InputConstants.getKey("key.keyboard." + HomeProblems.DEFAULT_HOTKEY.toLowerCase()));
                KeyMapping.resetMapping();
                this.minecraft.options.save();
                SkyAhLog.add("deal hotkey: " + SkyAhMod.OPEN_KEY.saveString() + " (menu)");
                this.rebuildWidgets();
            }
            case HOTKEY_PICK -> open(new SkyAhFlipsScreen(this, true));
            default -> {
            }
        }
    }

    private static Component allSet(SkyAhConfig cfg) {
        if (!cfg.enabled) {
            return Component.literal("Deals in chat are off.").withStyle(ChatFormatting.GRAY);
        }
        HomeProblems.Conn c = conn();
        if (c == HomeProblems.Conn.DOWN || c == HomeProblems.Conn.ELSEWHERE) {
            return Component.literal("Nothing to do here: it connects again by itself.").withStyle(ChatFormatting.GRAY);
        }
        KeyMapping key = SkyAhMod.OPEN_KEY;
        String k = key == null || key.isUnbound() ? "your hotkey" : key.getTranslatedKeyMessage().getString();
        return Component.literal("All set. ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("A deal shows in chat; ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(k).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" opens the newest one.").withStyle(ChatFormatting.GRAY));
    }

    /** "Balanced: 1M+ profit, 5%+, any price · keeps 46% of deals". */
    static Component flipsLine(SkyAhConfig cfg) {
        if (!cfg.enabled) {
            return Component.literal("Off: no deal lines, sounds or alerts").withStyle(ChatFormatting.DARK_GRAY);
        }
        List<String> parts = filterParts(cfg);
        String profile = SkyAhPresets.activeName(cfg);
        MutableComponent out = Component.literal((profile == null ? "" : profile + ": ")
                + String.join(", ", parts.subList(0, Math.min(3, parts.size())))).withStyle(ChatFormatting.GRAY);
        int[] c = FlipChat.coverage(cfg);
        if (c[1] >= 10) {
            out.append(Component.literal(" · keeps " + Math.round(c[0] * 100.0 / c[1]) + "% of deals")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    static Component versionText() {
        MutableComponent v = Component.literal("v" + SkyAhMod.VERSION).withStyle(ChatFormatting.DARK_GRAY);
        String latest = SkyAhMod.LATEST;
        if (latest != null && SkyAhNum.isNewer(latest, SkyAhMod.VERSION)) {
            v.append(Component.literal(" · "))
                    .append(Component.literal("Update: v" + latest).withStyle(ChatFormatting.GREEN));
        }
        return v;
    }

    /** Version on the right of the title row; a green "Update: v..." after it when outdated. */
    @Override
    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Component v = versionText();
        g.text(this.font, v, X + W - this.font.width(v), 8, WHITE);
    }

    /** The filters in a few words: "1M+ profit", "5%+", "any price", ... */
    static List<String> filterParts(SkyAhConfig cfg) {
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
        return parts;
    }
}
