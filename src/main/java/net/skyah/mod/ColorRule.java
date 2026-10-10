package net.skyah.mod;

import java.util.ArrayList;
import java.util.List;

/** One color rule: profit AND percent threshold -> chat/panel color. */
public class ColorRule {
    public long minProfit;
    public double minPct;
    public String color;

    public ColorRule() {
    }

    public ColorRule(long minProfit, double minPct, String color) {
        this.minProfit = minProfit;
        this.minPct = minPct;
        this.color = color;
    }

    public static List<ColorRule> defaults() {
        List<ColorRule> rules = new ArrayList<>();
        rules.add(new ColorRule(50_000_000L, 0, "LIGHT_PURPLE"));
        rules.add(new ColorRule(10_000_000L, 0, "GOLD"));
        rules.add(new ColorRule(0L, 0, "GREEN"));
        return rules;
    }
}
