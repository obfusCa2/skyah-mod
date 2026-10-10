package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Profit colors: the first rule whose profit and percent are reached wins. */
public class SkyAhRulesScreen extends SkyAhScreen {
    private static final String[] COLORS = {
            "GREEN", "DARK_GREEN", "YELLOW", "GOLD", "RED", "LIGHT_PURPLE",
            "DARK_PURPLE", "AQUA", "BLUE", "WHITE"};
    private static final int MAX_RULES = 5;

    public SkyAhRulesScreen(Screen parent) {
        super("Profit colors", parent);
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        cfg.colorRules = cfg.colorRules == null || cfg.colorRules.isEmpty()
                ? ColorRule.defaults() : new ArrayList<>(cfg.colorRules);
        cfg.colorRules.removeIf(r -> r == null);
    }

    @Override
    protected void build() {
        List<ColorRule> rules = cfg().colorRules;
        int y = 24;
        y += para(X, y, W, Component.literal("Checked top to bottom: the first rule the deal reaches colors its profit. "
                + "No match = green.").withStyle(ChatFormatting.GRAY)) + 6;

        int delW = 20;
        int colorW = 96;
        int pctW = 64;
        int coinW = W - colorW - pctW - delW - 18;
        text(X, y, () -> Component.literal("Profit from").withStyle(ChatFormatting.DARK_GRAY));
        text(X + coinW + 6, y, () -> Component.literal("and % from").withStyle(ChatFormatting.DARK_GRAY));
        text(X + coinW + pctW + 12, y, () -> Component.literal("Color").withStyle(ChatFormatting.DARK_GRAY));
        y += 11;

        for (int i = 0; i < rules.size(); i++) {
            ColorRule r = rules.get(i);
            EditBox coins = box(X, y, coinW, SkyAhNum.editCoins(r.minProfit), "0", 24, s -> {
            });
            coins.setResponder(s -> {
                long v = SkyAhNum.parseCoins(s, -1L);
                valid(coins, v >= 0);
                if (v >= 0) {
                    r.minProfit = v;
                    changed();
                }
            });
            EditBox pct = box(X + coinW + 6, y, pctW, r.minPct > 0 ? trimPct(r.minPct) : "", "0", 12, s -> {
            });
            pct.setResponder(s -> {
                double v = SkyAhNum.parsePct(s, -1.0);
                boolean ok = v >= 0 && v <= 10_000;
                valid(pct, ok);
                if (ok) {
                    r.minPct = v;
                    changed();
                }
            });
            button(X + coinW + pctW + 12, y, colorW, colorLabel(r.color), "Click for the next color", b -> {
                r.color = nextColor(r.color);
                changed();
                b.setMessage(colorLabel(r.color));
            });
            button(X + W - delW, y, delW, Component.literal("X").withStyle(ChatFormatting.RED), "Delete this rule", b -> {
                if (rules.size() > 1) {
                    rules.remove(r);
                    changed();
                    this.rebuildWidgets();
                }
            });
            y += GAP;
        }
        y += 4;
        button(X, y, half(), "Add rule", "Up to " + MAX_RULES + " rules.", b -> {
            if (rules.size() < MAX_RULES) {
                rules.add(new ColorRule(0L, 0, "GREEN"));
                changed();
                this.rebuildWidgets();
            }
        }).active = rules.size() < MAX_RULES;
        button(right(), y, half(), "Done", null, b -> onClose());
    }

    private static Component colorLabel(String color) {
        ChatFormatting f = FlipChat.byName(color);
        String name = color == null ? "?" : color.toLowerCase().replace('_', ' ');
        return f == null ? Component.literal(name) : Component.literal(name).withStyle(f);
    }

    private static String nextColor(String c) {
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i].equalsIgnoreCase(c)) {
                return COLORS[(i + 1) % COLORS.length];
            }
        }
        return COLORS[0];
    }
}
