package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Side panel shown next to the auction container after clicking a flip. Its place, size and on/off
 *  switch come from the panel settings (PanelLayout, edit mode in SkyAhPanelsScreen). */
public final class SkyAhPanel {
    private static volatile Flip pending = null;
    private static volatile long pendingAt = 0L;
    private static final long MAX_AGE_MS = 3 * 60_000L;

    private SkyAhPanel() {
    }

    public static void setPending(Flip flip) {
        pending = flip;
        pendingAt = System.currentTimeMillis();
    }

    public static void clear() {
        pending = null;
    }

    /** The flip whose auction was opened recently, if still fresh. */
    public static Flip pendingFlip() {
        Flip f = pending;
        if (f == null || System.currentTimeMillis() - pendingAt > MAX_AGE_MS) {
            return null;
        }
        return f;
    }

    public static void draw(Screen screen, GuiGraphicsExtractor g) {
        try {
            draw0(screen, g);
        } catch (Exception e) {
            SkyAhLog.add("panel error: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " " + e.getMessage()));
        }
    }

    private static void draw0(Screen screen, GuiGraphicsExtractor g) {
        Flip f = pending;
        if (f == null || !(screen instanceof AbstractContainerScreen<?> cs) || !place().shown) {
            return;
        }
        if (System.currentTimeMillis() - pendingAt > MAX_AGE_MS) {
            pending = null;
            return;
        }
        // Only show for the auction we actually sent the user to: the container
        // must contain an item whose name matches the clicked flip.
        String want = normalized(f.name);
        boolean match = showsItem(cs, f.name);
        String seen = firstItemName(cs);
        if (!match && AhSensor.auctionConfirmTitle(screen.getTitle().getString())) {
            // Auction confirm screens don't contain the auctioned item; the
            // freshly clicked auction is still the one being confirmed. Bazaar
            // confirm screens are not auction screens and never match.
            match = true;
        }
        if (!match) {
            logMiss(screen, want, seen);
            return;
        }
        logShown(screen, f);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        // The whole panel is drawn at its own scale (see PanelScale and PanelLayout); everything below is in
        // the panel's units (GUI pixels divided by sc). At 1.0 this is the old layout pixel for pixel.
        PanelLayout.Place place = place();
        float sc = PanelScale.effective(place.scale, McCompat.guiScale(mc));
        int rows = cs.getMenu().slots.size() / 9;
        Content c = content(font, f, System.currentTimeMillis());
        int[] def = PanelLayout.dealDefault(screen.width, screen.height, rows, sc, W);
        int[] at = PanelLayout.place(place, PanelLayout.units(screen.width, sc), PanelLayout.units(screen.height, sc),
                W, c.h(), def[0], def[1]);
        McCompat.pushScale(g, sc);
        try {
            paint(g, font, f, c, at[0], at[1]);
        } finally {
            McCompat.popScale(g);
        }
    }

    /** Panel width in panel units. */
    static final int W = 150;

    /** The deal panel's settings (see PanelLayout). */
    static PanelLayout.Place place() {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        return PanelLayout.get(cfg == null ? null : cfg.panels, PanelLayout.DEAL);
    }

    /** What the panel shows for a deal, measured once per frame: the name lines, the buy-in rows and the
     *  height in panel units. */
    record Content(List<FormattedCharSequence> nameLines, int nameDraw, long lockLeft, boolean lockRow,
                   boolean holdRow, int h) {
    }

    static Content content(Font font, Flip f, long now) {
        Component name = f.nameFormatted != null && !f.nameFormatted.isBlank()
                ? FlipChat.parseLegacy(f.nameFormatted)
                : Component.literal(f.name != null ? f.name : "?");
        List<FormattedCharSequence> nameLines = font.split(name, W - 12);
        int nameDraw = Math.min(nameLines.size(), 2);
        // New listing: how long until it can be bought (red bed in the view).
        long lockLeft = SkyAhOpenTracker.lockLeftMs(f, now);
        boolean bed = SkyAhOpenTracker.onBed(f);
        boolean lockRow = bed || lockLeft > 0L;
        boolean holdRow = lockRow && SkyAhHold.holding(f);
        int h = 26 + nameDraw * 12 + (lockRow ? 12 : 0) + (holdRow ? 10 : 0) + 3 * 12 + 8 + 12 + 8;
        return new Content(nameLines, nameDraw, lockLeft, lockRow, holdRow, h);
    }

    /** Draws the panel with its top left corner at x, y (panel units, inside the panel scale). */
    static void paint(GuiGraphicsExtractor g, Font font, Flip f, Content c, int x, int y) {
        int w = W;
        int h = c.h();
        String id = f.auctionId == null ? "" : f.auctionId;

        g.fill(x, y, x + w, y + h, 0xC0101010);

        if (!id.isEmpty()) {
            // Tiny side note: the full auction id at 40% scale, above the title.
            float sc = 0.4f;
            g.pose().pushMatrix();
            g.pose().scale(sc, sc);
            g.text(font, Component.literal("ID " + id),
                    (int) ((x + 5) / sc), (int) ((y + 4) / sc), 0xFFAAAAAA);
            g.pose().popMatrix();
        }
        g.text(font, Component.literal("SkyAH").withStyle(ChatFormatting.AQUA), x + 6, y + 12, 0xFFFFFFFF);
        if (f.preApi) {
            String tag = "PRE-API";
            g.text(font, Component.literal(tag).withStyle(ChatFormatting.LIGHT_PURPLE),
                    x + w - 6 - font.width(tag), y + 12, 0xFFFFFFFF);
        }

        int ly = y + 26;
        for (int i = 0; i < c.nameDraw(); i++) {
            g.text(font, c.nameLines().get(i), x + 6, ly, 0xFFFFFFFF);
            ly += 12;
        }

        if (c.lockRow()) {
            ly = row(g, font, x, ly, w, "Buy in", lockText(c.lockLeft()), ChatFormatting.YELLOW);
            if (c.holdRow()) {
                g.text(font, Component.literal("Hold mode waits for it").withStyle(ChatFormatting.GRAY),
                        x + 6, ly, 0xFFFFFFFF);
                ly += 10;
            }
        }

        long profit = FlipChat.profitOf(f, SkyAhMod.CONFIG);
        double pct = FlipChat.pctOf(f, profit);
        ChatFormatting pc = FlipChat.ruleColor(SkyAhMod.CONFIG, profit, pct);

        ly = row(g, font, x, ly, w, "Buy", FlipChat.fmt(f.price), ChatFormatting.WHITE);
        ly = row(g, font, x, ly, w, "Sell", f.targetSellPrice != null ? FlipChat.fmt(f.targetSellPrice) : "-", ChatFormatting.WHITE);
        ly = row(g, font, x, ly, w, "Floor", f.floor != null ? FlipChat.fmt(f.floor) : "-",
                "below".equals(f.floorVsTarget) ? ChatFormatting.RED : ChatFormatting.GREEN);
        g.fill(x + 6, ly + 3, x + w - 6, ly + 4, 0x50FFFFFF);
        ly += 8;
        row(g, font, x, ly, w, "Profit",
                FlipChat.signed(profit) + " (" + FlipChat.pctText(pct) + ")", pc);
    }

    /** "14s" while the lock runs, "soon" at its end or when the time is unknown. */
    static String lockText(long leftMs) {
        return leftMs >= 1_000L ? (leftMs + 999L) / 1_000L + "s" : "soon";
    }

    /** True when the container holds an item named like the flip. */
    static boolean showsItem(AbstractContainerScreen<?> cs, String flipName) {
        String want = normalized(flipName);
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String got = normalized(stack.getHoverName().getString());
            if (want.equals(got) || (want.length() >= 6 && got.length() >= 6
                    && (want.contains(got) || got.contains(want)))) {
                return true;
            }
        }
        return false;
    }

    private static String firstItemName(AbstractContainerScreen<?> cs) {
        for (net.minecraft.world.inventory.Slot slot : cs.getMenu().slots) {
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            if (stack != null && !stack.isEmpty()) {
                String got = normalized(stack.getHoverName().getString());
                if (!got.isEmpty()) {
                    return got;
                }
            }
        }
        return "";
    }

    private static volatile Object lastMissScreen = null;
    private static volatile Object lastShownScreen = null;

    /** One log line per opened container so a missing panel is diagnosable. */
    private static void logMiss(Screen screen, String want, String seen) {
        if (lastMissScreen == screen) {
            return;
        }
        lastMissScreen = screen;
        SkyAhLog.add("panel hidden: want '" + want + "' but first item is '" + seen + "'");
    }

    private static void logShown(Screen screen, Flip f) {
        if (lastShownScreen == screen) {
            return;
        }
        lastShownScreen = screen;
        SkyAhLog.add("panel shown: " + f.name);
    }

    /** Lowercase letters and digits only, so "Name ✪✪" equals "Name". */
    static String normalized(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : value.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int row(GuiGraphicsExtractor g, Font font, int x, int y, int w,
                           String label, String value, ChatFormatting valueColor) {
        g.text(font, Component.literal(label).withStyle(ChatFormatting.GRAY), x + 6, y, 0xFFFFFFFF);
        g.text(font, Component.literal(value).withStyle(valueColor), x + w - 6 - font.width(value), y, 0xFFFFFFFF);
        return y + 12;
    }
}
