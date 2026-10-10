package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Which deals show up. Every field writes into the live config while you
 *  type. The top line counts what the filters keep. */
public class SkyAhFiltersScreen extends SkyAhScreen {
    private static final long[] PROFIT_STEPS = {0L, 500_000L, 1_000_000L, 2_000_000L, 5_000_000L,
            10_000_000L, 25_000_000L, 50_000_000L};
    private static final long[] PCT_STEPS = {0, 5, 10, 15, 20, 30, 50};
    private static final long[] PRICE_STEPS = {0L, 5_000_000L, 10_000_000L, 50_000_000L, 100_000_000L,
            500_000_000L, 1_000_000_000L};
    private static final String[] FLOOR_MODES = {"all", "above", "below", "has"};

    private static final double[] SALES_STEPS = {0, 0.5, 1, 3, 10, 30};
    private Button purseBtn;

    public SkyAhFiltersScreen(Screen parent) {
        super("Filters", parent);
    }

    @Override
    public void tick() {
        super.tick();
        if (purseBtn != null) {
            purseBtn.setMessage(purseLabel(cfg()));
        }
    }

    private static Component purseLabel(SkyAhConfig cfg) {
        if (!cfg.maxPriceFromPurse) {
            return Component.literal("Purse limit: ").append(Component.literal("OFF").withStyle(ChatFormatting.GRAY));
        }
        long purse = SkyAhPurse.get();
        return Component.literal("Purse limit: ").append(Component.literal(
                purse >= 0 ? "ON (" + FlipChat.fmt(purse) + ")" : "ON (no purse seen)").withStyle(
                purse >= 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
    }

    static String salesLabel(SkyAhConfig cfg) {
        return cfg.minSalesPerDay > 0 ? "from " + FlipChat.rate(cfg.minSalesPerDay) : "any";
    }

    private static double nextSales(double cur) {
        for (double s : SALES_STEPS) {
            if (s > cur + 1e-9) {
                return s;
            }
        }
        return SALES_STEPS[0];
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        int y = 24;
        text(X, y, () -> coverageLine(cfg));
        y += 16;

        coinRow(y, "Min profit", "off", PROFIT_STEPS,
                "Only deals that earn at least this much after AH fees. Type 2m, 500k, 1.5b or click » for the next step.",
                () -> cfg.minProfit, v -> cfg.minProfit = v);
        y += GAP;
        pctRow(y);
        y += GAP;
        coinRow(y, "Max price", "no limit", PRICE_STEPS,
                "Hide deals that cost more than this. Empty = no limit.",
                () -> cfg.maxPrice, v -> cfg.maxPrice = v);
        y += GAP;
        purseBtn = button(X, y, half(), purseLabel(cfg),
                "Also hides deals that cost more than the coins in your purse, read from the "
                        + "Hypixel sidebar every second. Works together with Max price. "
                        + "If no purse is visible (not on SkyBlock), nothing is hidden by it.",
                b -> {
                    cfg.maxPriceFromPurse = !cfg.maxPriceFromPurse;
                    changed();
                    b.setMessage(purseLabel(cfg));
                });
        button(right(), y, half(), valued("Sales/day", salesLabel(cfg)),
                "Hide deals whose build sells less often than this per day (comparable sales, "
                        + "last 3 days). Deals without this number are never hidden. "
                        + "Most deals sell 5 to 70 times a day.",
                b -> {
                    cfg.minSalesPerDay = nextSales(cfg.minSalesPerDay);
                    changed();
                    b.setMessage(valued("Sales/day", salesLabel(cfg)));
                });
        y += GAP;

        button(X, y, half(), valued("Deals", dealType(cfg)),
                "BIN: buy instantly. Auction: you have to bid and may be outbid.",
                b -> {
                    if (cfg.showBin && cfg.showAuctions) {
                        cfg.showAuctions = false;
                    } else if (cfg.showBin) {
                        cfg.showBin = false;
                        cfg.showAuctions = true;
                    } else {
                        cfg.showBin = true;
                        cfg.showAuctions = true;
                    }
                    changed();
                    b.setMessage(valued("Deals", dealType(cfg)));
                });
        button(right(), y, half(), valued("Profit", profitMode(cfg)),
                "Safe: profit if you sell at the lower of sell target and floor, fees included. "
                        + "At target: profit at the recent sales average. Filters, colors and the chat line all use this.",
                b -> {
                    cfg.useRealProfit = !cfg.useRealProfit;
                    changed();
                    b.setMessage(valued("Profit", profitMode(cfg)));
                });
        y += GAP;
        button(X, y, W, valued("Floor", floorLabel(cfg.floorFilter)),
                "Floor = the cheapest comparable BIN listed right now. "
                        + "'Not below target': nobody undercuts your sell target (safest). "
                        + "'Below target': someone sells cheaper than the target. "
                        + "'Must exist': only deals where a comparable BIN was found.",
                b -> {
                    cfg.floorFilter = next(cfg.floorFilter);
                    changed();
                    b.setMessage(valued("Floor", floorLabel(cfg.floorFilter)));
                });
        y += GAP;

        int labelW = 70;
        label(X, y, "Only items");
        box(X + labelW, y, W - labelW, cfg.whitelist, "e.g. hyperion, juju (empty = all)", 400, s -> {
            cfg.whitelist = s;
            changed();
        }).setTooltip(Tooltip.create(Component.literal(
                "Comma separated. Item id or part of the name. Empty = every item.")));
        y += GAP;
        label(X, y, "Never items");
        box(X + labelW, y, W - labelW, cfg.blacklist, "e.g. dye, rune", 400, s -> {
            cfg.blacklist = s;
            changed();
        }).setTooltip(Tooltip.create(Component.literal(
                "Comma separated. These items never show, even if they are on the first list.")));
        y += GAP + 6;
        button(X, y, W, "Done", null, b -> onClose());
    }

    private void coinRow(int y, String label, String hint, long[] steps, String tip,
                         LongSupplier get, Consumer<Long> set) {
        int labelW = 70;
        int stepW = 20;
        label(X, y, label);
        EditBox box = box(X + labelW, y, W - labelW - stepW - 4, SkyAhNum.editCoins(get.getAsLong()), hint, 24, s -> {
        });
        box.setResponder(s -> {
            long v = SkyAhNum.parseCoins(s, -1L);
            valid(box, v >= 0);
            if (v >= 0) {
                set.accept(v);
                changed();
            }
        });
        box.setTooltip(Tooltip.create(Component.literal(tip)));
        button(X + W - stepW, y, stepW, "»", "Next step", b -> box.setValue(SkyAhNum.editCoins(SkyAhNum.nextStep(get.getAsLong(), steps))));
    }

    private void pctRow(int y) {
        SkyAhConfig cfg = cfg();
        int labelW = 70;
        int stepW = 20;
        label(X, y, "Min percent");
        EditBox box = box(X + labelW, y, W - labelW - stepW - 4, cfg.minPct > 0 ? trimPct(cfg.minPct) : "", "off", 12, s -> {
        });
        box.setResponder(s -> {
            double v = SkyAhNum.parsePct(s, -1.0);
            boolean ok = v >= 0 && v <= 10_000;
            valid(box, ok);
            if (ok) {
                cfg.minPct = v;
                changed();
            }
        });
        box.setTooltip(Tooltip.create(Component.literal(
                "Profit as a percent of the buy price. Deals must reach min profit AND min percent.")));
        button(X + W - stepW, y, stepW, "»", "Next step", b -> {
            long next = SkyAhNum.nextStep((long) Math.floor(cfg.minPct), PCT_STEPS);
            box.setValue(next > 0 ? Long.toString(next) : "");
        });
    }

    private static String dealType(SkyAhConfig cfg) {
        if (cfg.showBin && cfg.showAuctions) {
            return "BIN + auctions";
        }
        if (cfg.showBin) {
            return "BIN only";
        }
        return cfg.showAuctions ? "auctions only" : "none";
    }

    private static String profitMode(SkyAhConfig cfg) {
        return cfg.useRealProfit ? "safe (floor)" : "at target";
    }

    static String floorLabel(String mode) {
        switch (mode == null ? "all" : mode) {
            case "above": return "not below target";
            case "below": return "below target";
            case "has": return "must exist";
            default: return "any";
        }
    }

    /** Short form for the main screen summary; null when no floor filter. */
    static String floorShort(String mode) {
        switch (mode == null ? "all" : mode) {
            case "above": return "floor not below target";
            case "below": return "floor below target";
            case "has": return "has floor";
            default: return null;
        }
    }

    private static String next(String mode) {
        for (int i = 0; i < FLOOR_MODES.length; i++) {
            if (FLOOR_MODES[i].equals(mode)) {
                return FLOOR_MODES[(i + 1) % FLOOR_MODES.length];
            }
        }
        return FLOOR_MODES[0];
    }
}
