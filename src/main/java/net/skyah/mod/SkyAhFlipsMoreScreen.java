package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Auction flips > More: the chat line, profiles, the item lists (blacklist and whitelist), example deals,
 *  the reset, and the commands that do the same. */
public class SkyAhFlipsMoreScreen extends SkyAhScreen {
    static final String HELP = "Also by command: /skyah flip, /skyah minprofit 2m, /skyah bl add <item>,"
            + " /skyah profile <name>. /skyah help lists them all.";
    private boolean confirmReset;

    public SkyAhFlipsMoreScreen(Screen parent) {
        super("Auction flips: more", parent);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        confirmReset = false;
        Component help = Component.literal(HELP).withStyle(ChatFormatting.DARK_GRAY);
        MenuLayout.FlipsMore lay = MenuLayout.flipsMore(this.height, lines(help, W));
        int[] r = lay.rows();
        button(X, r[0], half(), "Chat line...", "What a deal line shows, with a live example. Sound, click, profit colors.",
                b -> open(new SkyAhChatScreen(this)));
        button(right(), r[0], half(), "Profiles...", "Ready-made and your own filter sets: switch, save and share.",
                b -> open(new SkyAhProfilesScreen(this)));
        button(X, r[1], half(), "Blacklist / whitelist...",
                "Never show some items (blacklist), or show only some (whitelist). Same as /skyah bl and /skyah wl.",
                b -> open(new SkyAhItemListsScreen(this)));
        button(right(), r[1], half(), "Show example deals",
                "Closes this menu and puts two example deals in chat, like /skyah test.",
                b -> {
                    closeMenu();
                    for (Flip f : FlipChat.samples()) {
                        f.auctionId = "test";
                        FlipChat.display(f, cfg);
                    }
                });
        para(X, lay.help(), W, help);
        button(X, lay.last(), half(), "Reset to recommended",
                "Sets filters, chat line, colors and sound back to the recommended setup (Balanced, sound from 10M)."
                        + " Item lists, profiles, hotkey and key stay. Click twice.",
                b -> {
                    if (!confirmReset) {
                        confirmReset = true;
                        b.setMessage(Component.literal("Click again to reset").withStyle(ChatFormatting.YELLOW));
                        return;
                    }
                    confirmReset = false;
                    cfg.applyRecommended();
                    changed();
                    b.setMessage(Component.literal("Recommended setup applied").withStyle(ChatFormatting.GREEN));
                });
        button(right(), lay.last(), half(), "Back", null, b -> onClose());
    }
}
