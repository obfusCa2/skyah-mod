package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;

/** Settings: the key and the website (one "Paste key" button reads the key from the clipboard, checks
 *  it, saves it and connects; the stored key is never shown), and the panels' edit mode. The key is
 *  activated on the website only; this links there. Logs: /skyah logs copy. */
public class SkyAhSettingsScreen extends SkyAhScreen {
    private String msg = "";
    private boolean ok = false;

    public SkyAhSettingsScreen(Screen parent) {
        super("Settings", parent);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        boolean activate = SkyAhKey.needsActivation();
        String hintText = activate && SkyAhKey.isInactive()
                ? "Inactive does not mean broken: a new key would be inactive too. Activate this one on the website with one bid."
                : cfg.hasKey()
                ? "Key leaked or lost? Rotate it on the website, then click Paste a new key."
                : "Without a key, deals arrive " + FreeMode.delayText(FreeMode.delayS())
                + " late (free mode). The key is free and needs a linked Minecraft account on skyah.net.";
        Component hint = Component.literal(hintText).withStyle(ChatFormatting.GRAY);
        Component keys = Component.literal("K or /skyah opens this menu, /skyah help lists the commands. All SkyAH keys"
                + " are also under Options > Controls.").withStyle(ChatFormatting.DARK_GRAY);
        MenuLayout.Settings lay = MenuLayout.settings(this.height, activate, lines(hint, W), lines(keys, W));
        text(X, lay.status(), () -> cfg.hasKey() ? statusLine()
                : Component.literal("● No key: free mode, deals arrive " + FreeMode.delayText(FreeMode.delayS()) + " late")
                .withStyle(ChatFormatting.AQUA));
        text(X, lay.msg(), () -> msg.isEmpty() ? null : Component.literal(msg).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (activate) {
            button(X, lay.activate(), W, Component.literal(SkyAhKey.isInactive() ? "Activate key on skyah.net/api"
                    : "Renew key on skyah.net/api").withStyle(ChatFormatting.YELLOW), SkyAhKey.activateTip(),
                    b -> SkyAhKey.openWebsite(this));
        }
        Component paste = cfg.hasKey() ? Component.literal("Paste a new key")
                : Component.literal("Paste key").withStyle(ChatFormatting.YELLOW);
        button(X, lay.paste(), W, paste, "Reads your key from the clipboard, saves it and connects. Copy it on"
                + " skyah.net/api first.", b -> pasteKey(cfg));
        if (!activate) {
            button(X, lay.website(), W, cfg.hasKey() ? "Open skyah.net/api" : "Get a free key on skyah.net/api",
                    "Your key, your purchases and the guide on the website.",
                    b -> ConfirmLinkScreen.confirmLinkNow(this, URI.create(cfg.hasKey() ? SkyAhConfigScreen.WEBSITE
                            : SkyAhKey.ACTIVATE_URL)));
        }
        para(X, lay.hint(), W, hint);
        button(X, lay.last(), half(), "Panels...", "Move, resize or hide the panels SkyAH draws, like the deal"
                + " panel at an auction. Same as /skyah panels.", b -> open(new SkyAhPanelsScreen(this)));
        button(right(), lay.last(), half(), "Done", null, b -> onClose());
        para(X, lay.keys(), W, keys);
    }

    private void pasteKey(SkyAhConfig cfg) {
        String typed = "";
        try {
            typed = this.minecraft.keyboardHandler.getClipboard();
        } catch (Exception ignored) {
        }
        typed = typed == null ? "" : typed.trim();
        if (typed.startsWith("skyah_") && typed.length() >= 40 && typed.length() <= 80) {
            cfg.apiKey = typed;
            cfg.save();
            SkyAhKey.reset();
            ok = true;
            msg = "Key saved, connecting...";
            SkyAhLog.add("key saved (" + typed.length() + " chars)");
            SkyAhMod.SOCKET.restart();
        } else {
            ok = false;
            msg = "No SkyAH key in your clipboard. Copy it on skyah.net/api first.";
            SkyAhLog.add("paste key: clipboard holds no key (" + typed.length() + " chars)");
        }
        this.rebuildWidgets();
    }
}
