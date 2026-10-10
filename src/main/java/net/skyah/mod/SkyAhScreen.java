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
import java.util.function.Supplier;

/** Base for the settings screens: one centered column. Changes go into the
 *  live config right away; the file is written a second after the last edit
 *  and on close. */
abstract class SkyAhScreen extends Screen {
    protected static final int WHITE = 0xFFFFFFFF;
    /** Chat's default width; the preview wraps like chat does. */
    private static final int CHAT_WIDTH = 320;

    protected final Screen parent;
    protected int W;
    protected int X;
    protected int H;
    protected int GAP;
    private final List<Txt> texts = new ArrayList<>();
    private long dirtyAt = 0L;

    private record Txt(int x, int y, int w, Supplier<Component> text) {
    }

    protected SkyAhScreen(String title, Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    @Override
    protected final void init() {
        texts.clear();
        W = Math.min(400, this.width - 32);
        X = (this.width - W) / 2;
        H = this.height < 300 ? 18 : 20;
        GAP = H + (this.height < 300 ? 3 : 4);
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
        texts.add(new Txt(x, y, 0, text));
    }

    /** Wrapped paragraph; returns the height it takes. */
    protected int para(int x, int y, int w, Component text) {
        texts.add(new Txt(x, y, w, () -> text));
        return this.font.split(text, w).size() * 10;
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
        for (Txt t : texts) {
            Component c = t.text.get();
            if (c == null) {
                continue;
            }
            if (t.w > 0) {
                g.textWithWordWrap(this.font, c, t.x, t.y, t.w, WHITE);
            } else {
                g.text(this.font, c, t.x, t.y, WHITE);
            }
        }
        extra(g, mouseX, mouseY);
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

    static Component statusLine() {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        FlipsSocket socket = SkyAhMod.SOCKET;
        boolean open = socket != null && socket.isOpen();
        MutableComponent out = Component.empty();
        if (!cfg.enabled) {
            return out.append(dot(ChatFormatting.GRAY)).append(Component.literal("Off: deals in chat are switched off")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!cfg.hasKey()) {
            return out.append(dot(ChatFormatting.YELLOW)).append(Component.literal("No API key yet: open Account & key")
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (!open) {
            String why = socket == null ? "" : socket.lastCloseLabel();
            return out.append(dot(ChatFormatting.RED)).append(Component.literal("Disconnected" + why)
                    .withStyle(ChatFormatting.RED));
        }
        out.append(dot(ChatFormatting.GREEN)).append(Component.literal("Connected").withStyle(ChatFormatting.GREEN));
        long last = socket.lastMessageAt;
        if (last > 0) {
            long secs = Math.max(0, (System.currentTimeMillis() - last) / 1000);
            out.append(Component.literal(" · last deal " + (secs < 60 ? secs + "s" : secs / 60 + "m") + " ago")
                    .withStyle(ChatFormatting.GRAY));
        }
        out.append(Component.literal(" · " + SkyAhStats.shownCount() + " shown").withStyle(ChatFormatting.GRAY));
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
