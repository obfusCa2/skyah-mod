package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Base for the settings screens: one centered column. Changes go into the
 *  live config right away; the file is written a second after the last edit
 *  and on close. */
abstract class SkyAhScreen extends Screen {
    protected static final int WHITE = 0xFFFFFFFF;
    protected static final String BACK_TIP = " Right click: one step back.";
    /** Chat's default width; the preview wraps like chat does. */
    private static final int CHAT_WIDTH = 320;

    protected final Screen parent;
    protected int W;
    protected int X;
    protected int H;
    protected int GAP;
    private final List<Txt> texts = new ArrayList<>();
    private final List<Rule> rules = new ArrayList<>();
    private final List<Tip> tips = new ArrayList<>();
    private long dirtyAt = 0L;

    /** right: x is the right edge of the text. */
    private record Txt(int x, int y, int w, Supplier<Component> text, boolean right) {
    }

    /** A thin line from x1 to x2 (a block title's rule). */
    private record Rule(int y, IntSupplier x1, IntSupplier x2) {
    }

    /** A hover area with a tooltip (rows of text that are no widget). */
    private record Tip(int x, int y, int w, int h, Supplier<String> text) {
    }

    protected SkyAhScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    @Override
    protected final void init() {
        texts.clear();
        rules.clear();
        tips.clear();
        W = MenuLayout.width(this.width);
        X = (this.width - W) / 2;
        H = MenuLayout.buttonH(this.height);
        GAP = MenuLayout.gap(this.height);
        build();
    }

    /** Adds the widgets; called on open and on every resize. */
    protected abstract void build();

    protected SkyAhConfig cfg() {
        return SkyAhMod.CONFIG;
    }

    protected int half() {
        return (W - 6) / 2;
    }

    protected int right() {
        return X + W - half();
    }

    protected void open(Screen screen) {
        cfg().save();
        this.minecraft.setScreenAndShow(screen);
    }

    /** One line of text, re-evaluated every frame. */
    protected void text(int x, int y, Supplier<Component> text) {
        texts.add(new Txt(x, y, 0, text, false));
    }

    /** One line of text whose right edge is at x. */
    protected void textRight(int x, int y, Supplier<Component> text) {
        texts.add(new Txt(x, y, 0, text, true));
    }

    /** Wrapped paragraph; returns the height it takes. */
    protected int para(int x, int y, int w, Component text) {
        texts.add(new Txt(x, y, w, () -> text, false));
        return lines(text, w) * 10;
    }

    /** The lines a paragraph takes at this width. */
    protected int lines(Component text, int w) {
        return Math.max(1, this.font.split(text, w).size());
    }

    /** A block title like the side panels: a gray label, then a thin line up to the right part (or up
     *  to stopAt when given, e.g. column titles). */
    protected void head(int y, String label, Supplier<Component> right, int stopAt) {
        text(X, y, () -> Component.literal(label).withStyle(ChatFormatting.GRAY));
        if (right != null) {
            textRight(X + W, y, right);
        }
        int from = X + this.font.width(label) + 4;
        rules.add(new Rule(y + 4, () -> from, () -> stopAt > 0 ? stopAt
                : X + W - (right == null ? 0 : this.font.width(right.get()) + 4)));
    }

    /** A tooltip for an area of plain text. */
    protected void tipArea(int x, int y, int w, int h, Supplier<String> text) {
        tips.add(new Tip(x, y, w, h, text));
    }

    /** Label left of a widget row, vertically centered on it. */
    protected void label(int x, int y, String text) {
        text(x, y + (H - 8) / 2, () -> Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    protected Button button(int x, int y, int w, Component label, String tip, Button.OnPress press) {
        Button.Builder b = Button.builder(label, press).bounds(x, y, w, H);
        if (tip != null) {
            b.tooltip(Tooltip.create(Component.literal(tip)));
        }
        return addRenderableWidget(b.build());
    }

    protected Button button(int x, int y, int w, String label, String tip, Button.OnPress press) {
        return button(x, y, w, Component.literal(label), tip, press);
    }

    /** Button that cycles a value: left click next, right click back. */
    protected Button step(int x, int y, int w, Supplier<Component> label, String tip, Runnable next, Runnable back) {
        StepButton[] self = new StepButton[1];
        self[0] = new StepButton(x, y, w, H, label.get(), b -> {
            next.run();
            changed();
            b.setMessage(label.get());
        }, () -> {
            back.run();
            changed();
            self[0].setMessage(label.get());
        });
        if (tip != null) {
            self[0].setTooltip(Tooltip.create(Component.literal(tip + BACK_TIP)));
        }
        return addRenderableWidget(self[0]);
    }

    protected Button toggle(int x, int y, int w, String label, String tip,
                            BooleanSupplier get, Consumer<Boolean> set) {
        return button(x, y, w, onOff(label, get.getAsBoolean()), tip, b -> {
            set.accept(!get.getAsBoolean());
            changed();
            b.setMessage(onOff(label, get.getAsBoolean()));
        });
    }

    protected static Component onOff(String label, boolean on) {
        return Component.literal(label + ": ").append(on
                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.GRAY));
    }

    protected static Component valued(String label, String value) {
        return Component.literal(label + ": ").append(Component.literal(value).withStyle(ChatFormatting.YELLOW));
    }

    /** Edit box whose responder fires on every keystroke (not on the initial fill). */
    protected EditBox box(int x, int y, int w, String value, String hint, int maxLen, Consumer<String> onChange) {
        EditBox e = new EditBox(this.font, x, y, w, H, Component.literal(hint));
        e.setMaxLength(maxLen);
        e.setValue(value == null ? "" : value);
        e.setHint(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
        e.setResponder(onChange);
        return addRenderableWidget(e);
    }

    protected static void valid(EditBox box, boolean ok) {
        box.setTextColor(ok ? 0xFFE0E0E0 : 0xFFFF5555);
    }

    /** Marks the config dirty; tick() writes it after a short pause. */
    protected void changed() {
        dirtyAt = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        if (dirtyAt != 0L && System.currentTimeMillis() - dirtyAt > 1000L) {
            dirtyAt = 0L;
            cfg().save();
        }
    }

    /** Last chance to copy screen state into the config. */
    protected void beforeClose() {
    }

    /** Builds the page again (labels and rows that depend on a switch or new data). */
    void refresh() {
        this.rebuildWidgets();
    }

    /** Closes the whole menu (back to the game), whatever the parent. */
    protected void closeMenu() {
        beforeClose();
        cfg().save();
        SkyAhMod.sendSettings();
        super.onClose();
    }

    @Override
    public void onClose() {
        beforeClose();
        cfg().save();
        SkyAhMod.sendSettings();
        if (parent == null) {
            super.onClose();
        } else {
            this.minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(this.font, this.title.copy().withStyle(ChatFormatting.GOLD), this.width / 2, 8, WHITE);
        for (Rule r : rules) {
            int x1 = r.x1().getAsInt();
            int x2 = r.x2().getAsInt();
            if (x2 > x1) {
                g.fill(x1, r.y(), x2, r.y() + 1, 0x50FFFFFF);
            }
        }
        for (Txt t : texts) {
            Component c = t.text.get();
            if (c == null) {
                continue;
            }
            if (t.w > 0) {
                g.textWithWordWrap(this.font, c, t.x, t.y, t.w, WHITE);
            } else {
                g.text(this.font, c, t.right ? t.x - this.font.width(c) : t.x, t.y, WHITE);
            }
        }
        extra(g, mouseX, mouseY);
        for (Tip t : tips) {
            if (mouseX >= t.x() && mouseX < t.x() + t.w() && mouseY >= t.y() && mouseY < t.y() + t.h()) {
                String s = t.text().get();
                if (s != null && !s.isEmpty()) {
                    g.setTooltipForNextFrame(this.font.split(Component.literal(s), 170), mouseX, mouseY);
                }
                break;
            }
        }
    }

    /** Extra drawing on top of the widgets (previews). */
    protected void extra(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }

    /** Chat-like box with the given lines, wrapped like chat; returns its height. */
    protected int drawChatBox(GuiGraphicsExtractor g, int x, int y, int w, List<? extends Component> lines) {
        int wrap = Math.min(w - 8, CHAT_WIDTH);
        int h = chatBoxHeight(w, lines);
        g.fill(x, y, x + w, y + h, 0xB0000000);
        int ly = y + 4;
        for (Component c : lines) {
            for (FormattedCharSequence s : this.font.split(c, wrap)) {
                g.text(this.font, s, x + 4, ly, WHITE);
                ly += 10;
            }
        }
        return h;
    }

    protected int chatBoxHeight(int w, List<? extends Component> lines) {
        int wrap = Math.min(w - 8, CHAT_WIDTH);
        int n = 0;
        for (Component c : lines) {
            n += Math.max(1, this.font.split(c, wrap).size());
        }
        return n * 10 + 6;
    }

    /** The connection as the menu pages see it (see HomeProblems). */
    static HomeProblems.Conn conn() {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        FlipsSocket socket = SkyAhMod.SOCKET;
        boolean open = socket != null && socket.isOpen();
        int code = socket == null ? 0 : socket.lastCloseCode;
        if (!cfg.hasKey()) {
            if (open) {
                return HomeProblems.Conn.FREE;
            }
            return code == FreeMode.CLOSED_CODE ? HomeProblems.Conn.FREE_CLOSED : HomeProblems.Conn.DOWN;
        }
        // Refused by the keyed feed: the free feed may be open meanwhile, the key still needs a fix.
        if (socket != null && socket.keyedRefused == 4401) {
            return HomeProblems.Conn.REJECTED;
        }
        if (SkyAhKey.isInactive()) {
            return HomeProblems.Conn.INACTIVE;
        }
        if (open) {
            return HomeProblems.Conn.OPEN;
        }
        return code == 4401 ? HomeProblems.Conn.REJECTED : code == 4409 ? HomeProblems.Conn.ELSEWHERE
                : HomeProblems.Conn.DOWN;
    }

    /** "● Connected · key 1h 12m left · last deal 5s ago", or what is wrong. Short enough for the
     *  narrowest page; the line under it names the fix. */
    static Component statusLine() {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        FlipsSocket socket = SkyAhMod.SOCKET;
        MutableComponent out = Component.empty();
        if (!SkyAhMod.connectionWanted()) {
            return out.append(dot(ChatFormatting.GRAY)).append(Component.literal("Off: deals in chat are switched off")
                    .withStyle(ChatFormatting.GRAY));
        }
        switch (conn()) {
            case FREE -> {
                out.append(dot(ChatFormatting.AQUA)).append(Component.literal("Free mode").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" · deals arrive " + FreeMode.delayText(FreeMode.delayS()) + " late")
                                .withStyle(ChatFormatting.GRAY));
                return lastDeal(out, socket);
            }
            case FREE_CLOSED -> {
                return out.append(dot(ChatFormatting.YELLOW)).append(Component.literal("Free mode closed")
                        .withStyle(ChatFormatting.YELLOW));
            }
            case INACTIVE -> {
                if (onFreeFeed(socket)) {
                    return lastDeal(freeNote(out.append(dot(ChatFormatting.RED))
                            .append(Component.literal("Key inactive").withStyle(ChatFormatting.RED))), socket);
                }
                return out.append(dot(ChatFormatting.RED)).append(Component.literal("Key inactive: no deals until you activate it")
                        .withStyle(ChatFormatting.RED));
            }
            case REJECTED -> {
                out.append(dot(ChatFormatting.RED)).append(Component.literal("Key not accepted")
                        .withStyle(ChatFormatting.RED));
                return onFreeFeed(socket) ? lastDeal(freeNote(out), socket) : out;
            }
            case ELSEWHERE -> {
                return out.append(dot(ChatFormatting.RED)).append(Component.literal("Disconnected: key in use in another game")
                        .withStyle(ChatFormatting.RED));
            }
            case DOWN -> {
                return out.append(dot(ChatFormatting.RED)).append(Component.literal(cfg.hasKey() || socket == null
                        || socket.lastCloseCode == 0 ? "Disconnected, trying again" : "Disconnected"
                        + socket.lastCloseLabel()).withStyle(ChatFormatting.RED));
            }
            default -> {
            }
        }
        out.append(dot(ChatFormatting.GREEN)).append(Component.literal("Connected").withStyle(ChatFormatting.GREEN));
        String left = SkyAhKey.leftText();
        if (left != null) {
            out.append(Component.literal(" · key ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(left + " left").withStyle(SkyAhKey.dueSoon() ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
        return lastDeal(out, socket);
    }

    /** The free feed is open (its hello came): deals arrive late, data still flows. */
    private static boolean onFreeFeed(FlipsSocket socket) {
        return socket != null && socket.isOpen() && FreeMode.active();
    }

    /** " · free mode, deals 60s late". */
    private static MutableComponent freeNote(MutableComponent out) {
        return out.append(Component.literal(" · free mode, deals " + FreeMode.delayText(FreeMode.delayS()) + " late")
                .withStyle(ChatFormatting.GRAY));
    }

    /** " · last deal 5s ago", or " · deals in chat off". */
    private static MutableComponent lastDeal(MutableComponent out, FlipsSocket socket) {
        if (!SkyAhMod.CONFIG.enabled) {
            return out.append(Component.literal(" · deals in chat off").withStyle(ChatFormatting.GRAY));
        }
        long last = socket.lastMessageAt;
        if (last > 0) {
            long secs = Math.max(0, (System.currentTimeMillis() - last) / 1000);
            out.append(Component.literal(" · last deal " + (secs < 60 ? secs + "s" : secs / 60 + "m") + " ago")
                    .withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static Component dot(ChatFormatting color) {
        return Component.literal("● ").withStyle(color);
    }

    /** "Your filters keep 31 of the last 200 deals (16%)", red when 0. */
    static Component coverageLine(SkyAhConfig cfg) {
        int[] c = FlipChat.coverage(cfg);
        if (c[1] < 10) {
            return Component.literal("Counting incoming deals (" + c[1] + " so far) to show what your filters keep.")
                    .withStyle(ChatFormatting.DARK_GRAY);
        }
        double share = c[0] * 100.0 / c[1];
        ChatFormatting col = c[0] == 0 ? ChatFormatting.RED : share < 5 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        MutableComponent out = Component.literal("Your filters keep ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(c[0] + " of the last " + c[1] + " deals").withStyle(col))
                .append(Component.literal(" (" + FlipChat.pctText(share) + ")").withStyle(ChatFormatting.GRAY));
        if (c[0] == 0) {
            out.append(Component.literal(": nothing would show").withStyle(ChatFormatting.RED));
        }
        return out;
    }

    static String trimPct(double v) {
        return v == Math.floor(v) ? Long.toString((long) v) : String.valueOf(v);
    }
}
