package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Edit mode for the panels the mod draws (Settings > Panels, /skyah panels): every panel with a frame and
 *  its name, drag to move, mouse wheel or - / + to resize, a switch to show or hide it, Reset for the
 *  selected panel and for all. Changes apply at once; Done (or Esc) saves them. The auction menu is drawn
 *  as an outline, so the deal panel's place next to it can be judged. */
public class SkyAhPanelsScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    /** Size of the auction menu the deal panel sits next to (6 rows). */
    private static final int CHEST_W = 176;
    private static final int CHEST_H = 114 + 6 * 18;

    private final Screen parent;
    private final Flip sample;
    private String selected = PanelLayout.IDS.get(0);
    private String dragging = null;
    /** Where the panel was grabbed, in panel units from its corner. */
    private int grabX;
    private int grabY;
    private Button shownButton;
    private Button smaller;
    private Button larger;

    public SkyAhPanelsScreen(Screen parent) {
        super(Component.literal("Panels"));
        this.parent = parent;
        this.sample = FlipChat.samples().get(0);
    }

    private static SkyAhConfig cfg() {
        return SkyAhMod.CONFIG;
    }

    private PanelLayout.Place place(String id) {
        return PanelLayout.get(cfg().panels, id);
    }

    private PanelLayout.Place edit(String id) {
        return PanelLayout.edit(cfg().panels, id);
    }

    private int guiScale() {
        return McCompat.guiScale(this.minecraft);
    }

    /** One panel as drawn now: its scale, size and corner in panel units. */
    private record Drawn(String id, float sc, int x, int y, int w, int h, SkyAhPanel.Content content) {
        int left() {
            return (int) Math.floor(x * sc);
        }

        int top() {
            return (int) Math.floor(y * sc);
        }

        int right() {
            return (int) Math.ceil((x + w) * sc);
        }

        int bottom() {
            return (int) Math.ceil((y + h) * sc);
        }

        boolean hit(double mx, double my) {
            return mx >= left() && mx < right() && my >= top() && my < bottom();
        }
    }

    private Drawn drawn(String id) {
        PanelLayout.Place p = place(id);
        float sc = PanelScale.effective(p.scale, guiScale());
        // Only the deal panel exists today; a new panel adds its size and default place here.
        SkyAhPanel.Content c = SkyAhPanel.content(this.font, sample, System.currentTimeMillis());
        int w = SkyAhPanel.W;
        int[] def = PanelLayout.dealDefault(this.width, this.height, 6, sc, w);
        int[] at = PanelLayout.place(p, PanelLayout.units(this.width, sc), PanelLayout.units(this.height, sc),
                w, c.h(), def[0], def[1]);
        return new Drawn(id, sc, at[0], at[1], w, c.h(), c);
    }

    @Override
    protected void init() {
        int h = MenuLayout.buttonH(this.height);
        int y = this.height - h - 4;
        int gap = 4;
        int[] widths = {84, 20, 20, 50, 60, 54};
        int total = gap * (widths.length - 1);
        for (int w : widths) {
            total += w;
        }
        int x = (this.width - total) / 2;
        shownButton = add(x, y, widths[0], h, shownLabel(),
                "Shows or hides this panel. A hidden panel stays here, dimmed, so it can be shown again.", b -> {
                    PanelLayout.Place p = edit(selected);
                    p.shown = !p.shown;
                    update();
                });
        x += widths[0] + gap;
        smaller = add(x, y, widths[1], h, Component.literal("-"), "Smaller. The mouse wheel over a panel works too.",
                b -> resize(selected, -1));
        x += widths[1] + gap;
        larger = add(x, y, widths[2], h, Component.literal("+"), "Larger. The mouse wheel over a panel works too.",
                b -> resize(selected, 1));
        x += widths[2] + gap;
        add(x, y, widths[3], h, Component.literal("Reset"), "Puts this panel back to its default place and size, shown.",
                b -> {
                    cfg().panels.remove(selected);
                    update();
                });
        x += widths[3] + gap;
        add(x, y, widths[4], h, Component.literal("Reset all"), "Puts every panel back to its default place and size.",
                b -> {
                    cfg().panels.clear();
                    update();
                });
        x += widths[4] + gap;
        add(x, y, widths[5], h, Component.literal("Done"), null, b -> onClose());
        update();
    }

    private Button add(int x, int y, int w, int h, Component label, String tip, Button.OnPress press) {
        Button.Builder b = Button.builder(label, press).bounds(x, y, w, h);
        if (tip != null) {
            b.tooltip(Tooltip.create(Component.literal(tip)));
        }
        return addRenderableWidget(b.build());
    }

    private Component shownLabel() {
        return SkyAhScreen.onOff("Shown", place(selected).shown);
    }

    /** Button labels and states after a change. */
    private void update() {
        if (shownButton == null) {
            return;
        }
        shownButton.setMessage(shownLabel());
        double s = place(selected).scale;
        smaller.active = PanelScale.canNudge(s, -1, guiScale());
        larger.active = PanelScale.canNudge(s, 1, guiScale());
    }

    private void resize(String id, int dir) {
        PanelLayout.Place p = edit(id);
        p.scale = PanelScale.nudge(p.scale, dir, guiScale());
        update();
    }

    /** The panel under the mouse, the selected one first when they overlap; null for none. */
    private String panelAt(double mx, double my) {
        if (drawn(selected).hit(mx, my)) {
            return selected;
        }
        for (String id : PanelLayout.IDS) {
            if (drawn(id).hit(mx, my)) {
                return id;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) {
            return true;
        }
        String id = e.button() == 0 ? panelAt(e.x(), e.y()) : null;
        if (id == null) {
            return false;
        }
        selected = id;
        Drawn d = drawn(id);
        dragging = id;
        grabX = (int) Math.floor(e.x() / d.sc()) - d.x();
        grabY = (int) Math.floor(e.y() / d.sc()) - d.y();
        update();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging == null) {
            return super.mouseDragged(e, dx, dy);
        }
        Drawn d = drawn(dragging);
        int x = (int) Math.floor(e.x() / d.sc()) - grabX;
        int y = (int) Math.floor(e.y() / d.sc()) - grabY;
        PanelLayout.moveTo(edit(dragging), x, y, PanelLayout.units(this.width, d.sc()),
                PanelLayout.units(this.height, d.sc()), d.w(), d.h());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        String id = panelAt(mx, my);
        if (id == null || scrollY == 0) {
            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }
        selected = id;
        resize(id, scrollY > 0 ? 1 : -1);
        return true;
    }

    @Override
    public void onClose() {
        cfg().save();
        SkyAhLog.add("panels saved: " + PanelLayout.describe(cfg().panels));
        if (parent == null) {
            super.onClose();
        } else {
            this.minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        // The auction menu as an outline: panels next to it must not cover it.
        int cx = (this.width - CHEST_W) / 2;
        int cy = (this.height - CHEST_H) / 2;
        g.fill(cx, cy, cx + CHEST_W, cy + CHEST_H, 0x30FFFFFF);
        frame(g, cx, cy, cx + CHEST_W, cy + CHEST_H, 0x60FFFFFF);
        Component chest = Component.literal("Auction menu").withStyle(ChatFormatting.DARK_GRAY);
        g.text(this.font, chest, cx + (CHEST_W - this.font.width(chest)) / 2, cy + CHEST_H / 2 - 4, WHITE);

        for (String id : PanelLayout.IDS) {
            if (!id.equals(selected)) {
                drawPanel(g, drawn(id));
            }
        }
        drawPanel(g, drawn(selected));

        g.centeredText(this.font, Component.literal("Drag a panel to move it, scroll over it to resize")
                .withStyle(ChatFormatting.GOLD), this.width / 2, 6, WHITE);
        Drawn d = drawn(selected);
        Component info = Component.literal(PanelLayout.name(selected) + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(PanelScale.percent(d.sc())).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(place(selected).moved() ? ", moved" : ", default place")
                        .withStyle(ChatFormatting.GRAY));
        g.centeredText(this.font, info, this.width / 2, 17, WHITE);
    }

    private void drawPanel(GuiGraphicsExtractor g, Drawn d) {
        PanelLayout.Place p = place(d.id());
        McCompat.pushScale(g, d.sc());
        try {
            SkyAhPanel.paint(g, this.font, sample, d.content(), d.x(), d.y());
        } finally {
            McCompat.popScale(g);
        }
        int l = d.left();
        int t = d.top();
        int r = d.right();
        int b = d.bottom();
        if (!p.shown) {
            g.fill(l, t, r, b, 0xA0000000);
        }
        boolean sel = d.id().equals(selected);
        frame(g, l - 1, t - 1, r + 1, b + 1, sel ? 0xFFFFAA00 : 0xFFAAAAAA);
        Component label = Component.literal(PanelLayout.name(d.id()) + (p.shown ? "" : " (hidden)"))
                .withStyle(sel ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        int ly = t - 11 >= 28 ? t - 11 : b + 3;
        g.text(this.font, label, l, ly, WHITE);
    }

    private static void frame(GuiGraphicsExtractor g, int l, int t, int r, int b, int color) {
        g.fill(l, t, r, t + 1, color);
        g.fill(l, b - 1, r, b, color);
        g.fill(l, t, l + 1, b, color);
        g.fill(r - 1, t, r, b, color);
    }

    /** Opens the edit mode (the /skyah panels command). */
    static void open(net.minecraft.client.Minecraft mc, Screen parent) {
        mc.setScreenAndShow(new SkyAhPanelsScreen(parent));
    }
}
