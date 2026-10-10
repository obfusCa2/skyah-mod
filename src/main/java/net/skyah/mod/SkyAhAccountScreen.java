package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;

/** API key and website. One "Paste key" button reads the key from the
 *  clipboard, checks it, saves it and connects; nobody types 49 characters.
 *  The stored key is never shown. Logs: /skyah logs (copy). */
public class SkyAhAccountScreen extends SkyAhScreen {
    private String msg = "";
    private boolean ok = false;

    public SkyAhAccountScreen(Screen parent) {
        super("Account & key", parent);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        int y = 24;
        text(X, y, () -> cfg.hasKey() ? statusLine()
                : Component.literal("● No key yet: get one, copy it, click Paste key").withStyle(ChatFormatting.YELLOW));
        y += 13;
        text(X, y, () -> msg.isEmpty() ? null : Component.literal(msg).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        y += 16;

        // An inactive key is not broken: activating comes first, a new key would be inactive too.
        boolean activate = SkyAhKey.needsActivation();
        if (activate) {
            button(X, y, W, Component.literal(SkyAhKey.isInactive() ? "Activate key on skyah.net/api" : "Renew key on skyah.net/api")
                    .withStyle(ChatFormatting.YELLOW), SkyAhKey.ACTIVATE_TIP, b -> SkyAhKey.openWebsite(this));
            y += GAP;
        }
        Component paste = cfg.hasKey()
                ? Component.literal("Paste a new key")
                : Component.literal("Paste key").withStyle(ChatFormatting.YELLOW);
        button(X, y, W, paste, "Reads your key from the clipboard, saves it and connects. Copy it on skyah.net/api first.",
                b -> pasteKey(cfg));
        y += GAP;
        if (!activate) {   // the Activate button already leads to the website
            button(X, y, W, cfg.hasKey() ? "Open skyah.net/api" : "Get a free key on skyah.net/api",
                    "Your key, your purchases and the guide on the website.",
                    b -> ConfirmLinkScreen.confirmLinkNow(this, URI.create("https://skyah.net/api")));
            y += GAP;
        }
        y += 2;
        String hint = activate && SkyAhKey.isInactive()
                ? "Inactive does not mean broken: a new key would be inactive too. Activate this one on the website with one bid."
                : cfg.hasKey()
                ? "Key leaked or lost? Rotate it on the website, then click Paste a new key."
                : "You need a linked Minecraft account on skyah.net. The key is free.";
        y += para(X, y, W, Component.literal(hint).withStyle(ChatFormatting.GRAY)) + 6;
        button(X, y, W, "Back", null, b -> onClose());
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
