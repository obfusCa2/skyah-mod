package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Renders one flip as a clickable chat line. The settings preview uses the
 *  same builder, so the preview always matches what chat shows. */
public class FlipChat {
    /** Recently shown flips (name -> [auction_id, shown_at_ms]) so a purchase
     *  chat message can be matched back to the auction we displayed. */
    private static final LinkedHashMap<String, Object[]> RECENT = new LinkedHashMap<>();
    /** Full flips by auction id so the click command can open the side panel. */
    private static final LinkedHashMap<String, Flip> BY_ID = new LinkedHashMap<>();
    /** Every deal received this session (live and catch-up, shown or not),
     *  oldest first: the base for "your filters keep X of the last N deals". */
    private static final LinkedHashMap<String, Flip> RECEIVED = new LinkedHashMap<>();
    private static final int RECEIVED_MAX = 300;
    /** Deals shown in chat, newest first, for the deal hotkey. */
    private static final java.util.ArrayDeque<Flip> SHOWN = new java.util.ArrayDeque<>();

    private static synchronized void shown(Flip flip) {
        flip.shownAt = System.currentTimeMillis();
        SHOWN.remove(flip);
        SHOWN.addFirst(flip);
        while (SHOWN.size() > 40) {
            SHOWN.removeLast();
        }
    }

    /** Newest shown deal not opened yet and younger than maxAgeMs, or null. */
    public static synchronized Flip nextToOpen(long maxAgeMs) {
        long now = System.currentTimeMillis();
        for (Flip f : SHOWN) {
            if (now - f.shownAt > maxAgeMs) {
                break;
            }
            if (!f.opened) {
                return f;
            }
        }
        return null;
    }

    public static synchronized boolean anyShownSince(long maxAgeMs) {
        Flip f = SHOWN.peekFirst();
        return f != null && System.currentTimeMillis() - f.shownAt <= maxAgeMs;
    }

    public static synchronized void remember(Flip flip) {
        if (flip == null || flip.auctionId == null) {
            return;
        }
        BY_ID.put(flip.auctionId, flip);
        trim(BY_ID, 60);
    }

    public static synchronized Flip byAuctionId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static synchronized void remember(String name, String auctionId) {
        if (name == null || auctionId == null) {
            return;
        }
        RECENT.put(name, new Object[]{auctionId, System.currentTimeMillis()});
        trim(RECENT, 80);
    }

    public static synchronized String lookupAuctionId(String name) {
        Object[] hit = name == null ? null : RECENT.get(name);
        if (hit == null) {
            return null;
        }
        if (System.currentTimeMillis() - (Long) hit[1] > 10 * 60_000L) {
            return null;
        }
        return (String) hit[0];
    }

    public static synchronized void received(Flip flip) {
        if (flip == null) {
            return;
        }
        String key = flip.auctionId != null ? flip.auctionId : flip.name + "|" + flip.price;
        RECEIVED.remove(key);
        RECEIVED.put(key, flip);
        trim(RECEIVED, RECEIVED_MAX);
    }

    /** {kept, total} over the received deals for the given settings. */
    public static synchronized int[] coverage(SkyAhConfig cfg) {
        int kept = 0;
        for (Flip f : RECEIVED.values()) {
            if (passes(f, cfg)) {
                kept++;
            }
        }
        return new int[]{kept, RECEIVED.size()};
    }

    private static void trim(LinkedHashMap<String, ?> map, int max) {
        Iterator<String> it = map.keySet().iterator();
        while (map.size() > max && it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    /** The profit that filters, colors and the chat line use: safe profit
     *  (sold at min(target, floor), fees included) when enabled and known. */
    public static long profitOf(Flip flip, SkyAhConfig cfg) {
        if (cfg.useRealProfit && flip.realProfit != null) {
            return flip.realProfit;
        }
        if (flip.expectedProfit != null) {
            return flip.expectedProfit;
        }
        return flip.realProfit != null ? flip.realProfit : 0L;
    }

    public static double pctOf(Flip flip, long profit) {
        return flip.price > 0 ? profit * 100.0 / flip.price : 0.0;
    }

    static boolean isBin(Flip flip) {
        return flip.bin != null && flip.bin;
    }

    public static boolean passes(Flip flip, SkyAhConfig cfg) {
        long profit = profitOf(flip, cfg);
        double pct = pctOf(flip, profit);
        if (profit < cfg.minProfit) {
            return false;
        }
        if (cfg.minPct > 0 && pct < cfg.minPct) {
            return false;
        }
        long cap = effectiveMaxPrice(cfg);
        if (cap > 0 && flip.price > cap) {
            return false;
        }
        // Unknown sell time never hides a deal.
        if (cfg.maxSellTimeS > 0 && flip.sellTimeS != null && flip.sellTimeS > cfg.maxSellTimeS) {
            return false;
        }
        if (cfg.whitelist != null && !cfg.whitelist.isBlank() && !matchesList(cfg.whitelist, flip)) {
            return false;
        }
        if (matchesList(cfg.blacklist, flip)) {
            return false;
        }
        boolean bin = isBin(flip);
        if (bin && !cfg.showBin) {
            return false;
        }
        return bin || cfg.showAuctions;
    }

    /** Max price after the purse limit; 0 = no limit. Unknown purse = no purse limit. */
    public static long effectiveMaxPrice(SkyAhConfig cfg) {
        long cap = cfg.maxPrice;
        if (cfg.maxPriceFromPurse) {
            long purse = SkyAhPurse.get();
            if (purse >= 0) {
                cap = cap > 0 ? Math.min(cap, purse) : Math.max(purse, 1L);
            }
        }
        return cap;
    }

    public static void show(Flip flip) {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        if (passes(flip, cfg)) {
            display(flip, cfg);
        }
    }

    private static long lastSoundAt = 0L;

    /** Puts the line into chat without checking filters (/skyah test). */
    public static void display(Flip flip, SkyAhConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null) {
            McCompat.systemMessage(mc, line(flip, cfg), false);
        }
        SkyAhStats.shown();
        // No sound or alert for catch-up deals (minutes old).
        boolean live = flip.replay == null || !flip.replay;
        long profit = profitOf(flip, cfg);
        // At most one sound per 500 ms.
        if (live && cfg.sound && profit >= cfg.soundMinProfit
                && System.currentTimeMillis() - lastSoundAt > 500L) {
            lastSoundAt = System.currentTimeMillis();
            SkyAhSound.play(cfg.soundName);
        }
        if (live && cfg.screenAlert && profit >= cfg.screenAlertMinProfit) {
            SkyAhAlert.show(flip, cfg, profit);
        }
        remember(flip.name, flip.auctionId);
        remember(flip);
        shown(flip);
    }

    /** One chat line: profit, item, then the parts switched on in the settings. */
    public static MutableComponent line(Flip flip, SkyAhConfig cfg) {
        long profit = profitOf(flip, cfg);
        double pct = pctOf(flip, profit);
        ChatFormatting tier = ruleColor(cfg, profit, pct);

        MutableComponent msg = Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("SkyAH").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(signed(profit)).withStyle(tier, ChatFormatting.BOLD));
        if (cfg.showPct) {
            msg.append(Component.literal(" " + pctText(pct)).withStyle(tier));
        }
        if (!isBin(flip)) {
            msg.append(Component.literal(" AUCTION").withStyle(ChatFormatting.YELLOW));
        }
        msg.append(Component.literal(" ")).append(title(flip));
        if (cfg.showPrice) {
            msg.append(Component.literal(" " + fmt(flip.price)).withStyle(ChatFormatting.WHITE));
        }
        if (cfg.showTarget && flip.targetSellPrice != null) {
            msg.append(Component.literal((cfg.showPrice ? " → " : " sell ") + fmt(flip.targetSellPrice))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (cfg.showFloor && flip.floor != null) {
            boolean below = "below".equals(flip.floorVsTarget);
            msg.append(Component.literal(" floor " + fmt(flip.floor))
                    .withStyle(below ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        }
        if (cfg.showVolume && flip.salesPerDay != null) {
            // Yellow under 1 sale a day.
            msg.append(Component.literal(" " + rate(flip.salesPerDay) + "/d")
                    .withStyle(flip.salesPerDay < 1.0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
        }
        if (cfg.showSellTime && flip.sellTimeS != null) {
            msg.append(Component.literal(" ~" + duration(flip.sellTimeS)).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (cfg.showFee && flip.fee != null) {
            msg.append(Component.literal(" fee " + fmt(flip.fee)).withStyle(ChatFormatting.DARK_GRAY));
        }

        Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(details(flip, cfg)));
        if (flip.auctionId != null && !flip.auctionId.isBlank()) {
            String command = "/viewauction " + flip.auctionId;
            style = style.withClickEvent("copy".equals(cfg.clickAction)
                    ? new ClickEvent.CopyToClipboard(command)
                    : new ClickEvent.RunCommand("/skyah view " + flip.auctionId));
        }
        msg.setStyle(style);
        return msg;
    }

    /** Hover text: every number behind the line. */
    public static Component details(Flip flip, SkyAhConfig cfg) {
        MutableComponent t = Component.empty().append(title(flip));
        row(t, "Buy", fmt(flip.price) + (isBin(flip) ? " (BIN)" : " (auction, you bid)"), ChatFormatting.WHITE);
        if (flip.targetSellPrice != null) {
            row(t, "Sell target", fmt(flip.targetSellPrice) + " (recent sales)", ChatFormatting.WHITE);
        }
        if (flip.floor != null) {
            boolean below = "below".equals(flip.floorVsTarget);
            row(t, "Floor", fmt(flip.floor) + (below ? " below target" : " at or above target"),
                    below ? ChatFormatting.RED : ChatFormatting.GREEN);
        } else {
            row(t, "Floor", "no comparable BIN listed", ChatFormatting.GRAY);
        }
        if (flip.fee != null) {
            row(t, "AH fees", fmt(flip.fee), ChatFormatting.GRAY);
        }
        if (flip.salesPerDay != null) {
            row(t, "Sales", "~" + rate(flip.salesPerDay) + " per day (this build, last 3 days)",
                    flip.salesPerDay < 1.0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
        }
        if (flip.sellTimeS != null) {
            row(t, "Sell time", "~" + duration(flip.sellTimeS) + " at the target", ChatFormatting.WHITE);
        }
        if (flip.queueAhead != null) {
            row(t, "Cheaper listings ahead", Integer.toString(flip.queueAhead),
                    flip.queueAhead > 0 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
        }
        if (flip.expectedProfit != null) {
            row(t, "Profit at target", profitText(flip, flip.expectedProfit), ChatFormatting.WHITE);
        }
        if (flip.realProfit != null) {
            row(t, "Profit at floor", profitText(flip, flip.realProfit), ChatFormatting.WHITE);
        }
        t.append(Component.literal("\n" + (cfg.useRealProfit
                        ? "The line shows the safe profit (sold at the lower of target and floor)."
                        : "The line shows the profit at the sell target."))
                .withStyle(ChatFormatting.DARK_GRAY));
        if (flip.auctionId != null && !flip.auctionId.isBlank()) {
            t.append(Component.literal("\n" + ("copy".equals(cfg.clickAction)
                            ? "Click: copy /viewauction"
                            : "Click: open the auction"))
                    .withStyle(ChatFormatting.AQUA));
        }
        return t;
    }

    private static void row(MutableComponent t, String label, String value, ChatFormatting color) {
        t.append(Component.literal("\n" + label + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(value).withStyle(color));
    }

    private static String profitText(Flip flip, long profit) {
        return signed(profit) + " (" + pctText(pctOf(flip, profit)) + ")";
    }

    static Component title(Flip flip) {
        return flip.nameFormatted != null && !flip.nameFormatted.isBlank()
                ? parseLegacy(flip.nameFormatted)
                : Component.literal(flip.name != null ? flip.name : "?");
    }

    /** Two example deals for the settings preview: a plain BIN, and an
     *  auction whose floor undercuts the sell target. */
    public static List<Flip> samples() {
        List<Flip> out = new ArrayList<>();
        Flip a = new Flip();
        a.eaId = "JUJU_SHORTBOW";
        a.name = "Juju Shortbow ✪✪✪✪✪";
        a.nameFormatted = "§5Juju Shortbow §6✪✪✪✪✪";
        a.price = 18_500_000L;
        a.targetSellPrice = 22_400_000L;
        a.fee = 672_000L;
        a.expectedProfit = 3_228_000L;
        a.floor = 23_100_000L;
        a.floorVsTarget = "above";
        a.realSell = 22_400_000L;
        a.realProfit = 3_228_000L;
        a.bin = true;
        a.salesPerDay = 31.2;
        a.sellTimeS = 2_640L;
        a.queueAhead = 0;
        out.add(a);
        Flip b = new Flip();
        b.eaId = "PET";
        b.name = "[Lvl 100] Ender Dragon";
        b.nameFormatted = "§7[Lvl 100] §6Ender Dragon";
        b.price = 540_000_000L;
        b.targetSellPrice = 610_000_000L;
        b.fee = 21_350_000L;
        b.expectedProfit = 48_650_000L;
        b.floor = 585_000_000L;
        b.floorVsTarget = "below";
        b.realSell = 585_000_000L;
        b.realProfit = 24_525_000L;
        b.bin = false;
        b.salesPerDay = 0.6;
        b.sellTimeS = 41_400L;
        b.queueAhead = 3;
        out.add(b);
        return out;
    }

    /** Whitelist/blacklist: comma separated ea_id or name fragments. */
    static boolean matchesList(String list, Flip flip) {
        if (list == null || list.isBlank()) {
            return false;
        }
        String ea = flip.eaId == null ? "" : flip.eaId.toLowerCase(Locale.ROOT);
        String name = flip.name == null ? "" : flip.name.toLowerCase(Locale.ROOT);
        for (String token : list.split(",")) {
            String t = token.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && (ea.equals(t) || ea.equals(t.replace(' ', '_')) || name.contains(t))) {
                return true;
            }
        }
        return false;
    }

    /** First matching rule colors the profit; no match = green. */
    public static ChatFormatting ruleColor(SkyAhConfig cfg, long profit, double pct) {
        if (cfg.colorRules != null) {
            for (ColorRule rule : cfg.colorRules) {
                if (rule == null || rule.color == null) {
                    continue;
                }
                if (profit >= rule.minProfit && pct >= rule.minPct) {
                    ChatFormatting f = byName(rule.color);
                    if (f != null) {
                        return f;
                    }
                }
            }
        }
        return ChatFormatting.GREEN;
    }

    public static ChatFormatting byName(String name) {
        try {
            return ChatFormatting.valueOf(String.valueOf(name).trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return null;
        }
    }

    /** Turns a §-formatted string (name_formatted) into styled components. */
    public static Component parseLegacy(String text) {
        MutableComponent root = Component.empty();
        ChatFormatting fmt = null;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                ChatFormatting next = ChatFormatting.getByCode(
                        Character.toLowerCase(text.charAt(i + 1)));
                if (next != null) {
                    if (buf.length() > 0) {
                        MutableComponent part = Component.literal(buf.toString());
                        if (fmt != null) {
                            part.withStyle(fmt);
                        }
                        root.append(part);
                        buf.setLength(0);
                    }
                    fmt = next;
                    i++;
                    continue;
                }
            }
            buf.append(c);
        }
        if (buf.length() > 0) {
            MutableComponent part = Component.literal(buf.toString());
            if (fmt != null) {
                part.withStyle(fmt);
            }
            root.append(part);
        }
        return root;
    }

    public static String signed(long coins) {
        return (coins < 0 ? "-" : "+") + fmt(Math.abs(coins));
    }

    public static String pctText(double pct) {
        return String.format(Locale.ROOT, "%.0f%%", pct);
    }

    /** Sales per day: 158, 31, 6.3, 0.6. */
    public static String rate(double perDay) {
        if (perDay >= 10) {
            return String.format(Locale.ROOT, "%.0f", perDay);
        }
        String s = String.format(Locale.ROOT, "%.1f", perDay);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Seconds as 45s, 12m, 2.5h, 3d. */
    public static String duration(long seconds) {
        if (seconds < 60) {
            return Math.max(0, seconds) + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        if (seconds < 86_400) {
            String s = String.format(Locale.ROOT, "%.1f", seconds / 3600.0);
            return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + "h";
        }
        return Math.round(seconds / 86_400.0) + "d";
    }

    /** Short coin amount, 3 significant digits: 950k, 3.23M, 22.4M, 540M, 1.25B. */
    public static String fmt(long coins) {
        if (coins < 0) {
            return "-" + fmt(-coins);
        }
        if (coins >= 999_500_000L) {
            return digits3(coins / 1e9) + "B";
        }
        if (coins >= 999_500L) {
            return digits3(coins / 1e6) + "M";
        }
        if (coins >= 1_000L) {
            return digits3(coins / 1e3) + "k";
        }
        return Long.toString(coins);
    }

    static String digits3(double v) {
        String s = v >= 99.95 ? String.format(Locale.ROOT, "%.0f", v)
                : v >= 9.995 ? String.format(Locale.ROOT, "%.1f", v)
                : String.format(Locale.ROOT, "%.2f", v);
        if (s.indexOf('.') >= 0) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }
}
