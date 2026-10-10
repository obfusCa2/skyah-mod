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
 *  type. The top line counts what the filters keep. Profit always uses the
 *  real sell price (recent sales, or the cheapest listing when lower). */
public class SkyAhFiltersScreen extends SkyAhScreen {
    private static final long[] PROFIT_STEPS = {0L, 500_000L, 1_000_000L, 2_000_000L, 5_000_000L,
            10_000_000L, 25_000_000L, 50_000_000L};
    private static final long[] PCT_STEPS = {0, 5, 10, 15, 20, 30, 50};
    /** Max price steps; the second one is "my purse". */
    private static final long[] PRICE_STEPS = {0L, -1L, 10_000_000L, 50_000_000L, 100_000_000L, 500_000_000L, 1_000_000_000L};
    private static final long[] SELL_WITHIN = {0L, 86_400L, 21_600L, 7_200L, 1_800L};
    private static final boolean[][] DEAL_TYPES = {{true, true}, {true, false}, {false, true}};
    private Button priceBtn;

    public SkyAhFiltersScreen(Screen parent) {
        super("Filters", parent);
    }

    @Override
    public void tick() {
        super.tick();
        if (priceBtn != null) {
            priceBtn.setMessage(valued("Max price", maxPriceLabel(cfg())));   // the purse changes
        }
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
        priceBtn = step(X, y, half(), () -> valued("Max price", maxPriceLabel(cfg)),
                "Hides deals that cost more. My purse: reads the coins in your sidebar every second, "
                        + "so you only see what you can buy right now.",
                () -> setPriceStep(cfg, priceIndex(cfg) + 1),
                () -> setPriceStep(cfg, priceIndex(cfg) < 0 ? 0 : priceIndex(cfg) - 1));
        step(right(), y, half(), () -> valued("Deals", dealType(cfg)),
                "BIN: buy instantly. Auction: you have to bid and may be outbid.",
                () -> setDealType(cfg, typeIndex(cfg) + 1),
                () -> setDealType(cfg, typeIndex(cfg) + DEAL_TYPES.length - 1));
        y += GAP;
        step(X, y, half(), () -> valued("Sells within", sellWithinLabel(cfg.maxSellTimeS)),
                "Hides items that usually take longer to sell at the sell price. Rough estimate from past "
                        + "sales, so real sell times can be quite different. Deals without an estimate are never hidden.",
                () -> cfg.maxSellTimeS = SkyAhNum.nextStep(cfg.maxSellTimeS, SELL_WITHIN),
                () -> cfg.maxSellTimeS = SkyAhNum.prevStep(cfg.maxSellTimeS, SELL_WITHIN));
        button(right(), y, half(), itemListsLabel(cfg), "Optional: show only some items, or never show others.",
                b -> open(new SkyAhItemListsScreen(this)));
        y += GAP + 2;
        y += para(X, y, W, Component.literal("Profit counts the real sell price: recent sales, or just under the cheapest "
                + "listing if someone sells cheaper. AH fees included.").withStyle(ChatFormatting.GRAY)) + 4;
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
        step(X + W - stepW, y, stepW, () -> Component.literal("»"), "Next step.",
                () -> box.setValue(SkyAhNum.editCoins(SkyAhNum.nextStep(get.getAsLong(), steps))),
                () -> box.setValue(SkyAhNum.editCoins(SkyAhNum.prevStep(get.getAsLong(), steps))));
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
        step(X + W - stepW, y, stepW, () -> Component.literal("»"), "Next step.",
                () -> {
                    long next = SkyAhNum.nextStep((long) Math.floor(cfg.minPct), PCT_STEPS);
                    box.setValue(next > 0 ? Long.toString(next) : "");
                },
                () -> {
                    long prev = SkyAhNum.prevStep((long) Math.ceil(cfg.minPct), PCT_STEPS);
                    box.setValue(prev > 0 ? Long.toString(prev) : "");
                });
    }

    /** Index in PRICE_STEPS, or -1 for a value set elsewhere (old config, command). */
    private static int priceIndex(SkyAhConfig cfg) {
        if (cfg.maxPriceFromPurse) {
            return cfg.maxPrice <= 0 ? 1 : -1;
        }
        for (int i = 0; i < PRICE_STEPS.length; i++) {
            if (PRICE_STEPS[i] >= 0 && PRICE_STEPS[i] == cfg.maxPrice) {
                return i;
            }
        }
        return -1;
    }

    private static void setPriceStep(SkyAhConfig cfg, int i) {
        long v = PRICE_STEPS[(i % PRICE_STEPS.length + PRICE_STEPS.length) % PRICE_STEPS.length];
        cfg.maxPriceFromPurse = v < 0;
        cfg.maxPrice = Math.max(0L, v);
    }

    static String maxPriceLabel(SkyAhConfig cfg) {
        if (cfg.maxPriceFromPurse) {
            long purse = SkyAhPurse.get();
            String p = purse >= 0 ? "my purse (" + FlipChat.fmt(purse) + ")" : "my purse (not seen)";
            return cfg.maxPrice > 0 ? FlipChat.fmt(cfg.maxPrice) + " or purse" : p;
        }
        return cfg.maxPrice > 0 ? FlipChat.fmt(cfg.maxPrice) : "no limit";
    }

    private static int typeIndex(SkyAhConfig cfg) {
        for (int i = 0; i < DEAL_TYPES.length; i++) {
            if (DEAL_TYPES[i][0] == cfg.showBin && DEAL_TYPES[i][1] == cfg.showAuctions) {
                return i;
            }
        }
        return 0;
    }

    private static void setDealType(SkyAhConfig cfg, int i) {
        boolean[] t = DEAL_TYPES[i % DEAL_TYPES.length];
        cfg.showBin = t[0];
        cfg.showAuctions = t[1];
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

    /** "~6h": a rough estimate, hence the tilde. */
    static String sellWithinLabel(long seconds) {
        if (seconds <= 0) {
            return "any";
        }
        return "~" + (seconds >= 86_400 ? "1 day" : FlipChat.duration(seconds));
    }

    private static int count(String list) {
        int n = 0;
        if (list != null) {
            for (String t : list.split(",")) {
                if (!t.isBlank()) {
                    n++;
                }
            }
        }
        return n;
    }

    private static Component itemListsLabel(SkyAhConfig cfg) {
        int only = count(cfg.whitelist);
        int never = count(cfg.blacklist);
        if (only == 0 && never == 0) {
            return Component.literal("Item lists: ").append(Component.literal("none").withStyle(ChatFormatting.GRAY));
        }
        String v = only > 0 && never > 0 ? "only " + only + ", never " + never : only > 0 ? "only " + only : "never " + never;
        return valued("Item lists", v);
    }
}
