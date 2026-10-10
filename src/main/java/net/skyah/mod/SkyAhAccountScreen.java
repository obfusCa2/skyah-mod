package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** API key, connection state and logs. The stored key is never shown. */
public class SkyAhAccountScreen extends SkyAhScreen {
    private EditBox keyBox;
    private String typedKey = "";
    private String saveMsg = "";

    public SkyAhAccountScreen(Screen parent) {
        super("Account & key", parent);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        int y = 24;
        text(X, y, SkyAhScreen::statusLine);
        y += 13;
        text(X, y, () -> {
            if (!saveMsg.isEmpty()) {
                return Component.literal(saveMsg).withStyle(saveMsg.startsWith("Key saved")
                        ? ChatFormatting.GREEN : ChatFormatting.RED);
            }
            int len = cfg.apiKey == null ? 0 : cfg.apiKey.trim().length();
            return len > 0
                    ? Component.literal("API key: saved (" + len + " characters, hidden)").withStyle(ChatFormatting.GRAY)
                    : Component.literal("API key: not set").withStyle(ChatFormatting.YELLOW);
        });
        y += 16;

        keyBox = box(X, y, W, typedKey, cfg.hasKey() ? "paste a new key to replace it" : "paste your skyah_ key here",
                80, s -> typedKey = s);
        y += GAP;
        button(X, y, half(), "Paste from clipboard", null, b -> {
            try {
                keyBox.setValue(this.minecraft.keyboardHandler.getClipboard().trim());
            } catch (Exception ignored) {
            }
        });
        button(right(), y, half(), "Save key", "Saves the key and reconnects", b -> {
            String typed = typedKey.trim();
            if (typed.startsWith("skyah_") && typed.length() >= 40 && typed.length() <= 80) {
                cfg.apiKey = typed;
                cfg.save();
                typedKey = "";
                keyBox.setValue("");
                saveMsg = "Key saved (" + typed.length() + " characters), reconnecting";
                SkyAhLog.add("key saved (" + typed.length() + " chars)");
                SkyAhMod.SOCKET.restart();
            } else {
                saveMsg = "That is not a SkyAH key: it starts with skyah_ and has 49 characters";
                SkyAhLog.add("key invalid - got " + typed.length() + " chars");
            }
        });
        y += GAP;
        button(X, y, half(), "Reconnect", "Close and reopen the connection", b -> {
            SkyAhMod.SOCKET.restart();
            saveMsg = "";
        });
        button(right(), y, half(), "Copy logs", "Copies the mod log for support", b -> {
            boolean ok = SkyAhMod.copyLogs();
            b.setMessage(Component.literal(ok ? "Logs copied" : "Copy failed"));
        });
        y += GAP + 2;
        y += para(X, y, W, Component.literal("Get a key on skyah.net/api (needs a linked Minecraft account). "
                + "If the status says the key is inactive, activate it there with one bid in game.")
                .withStyle(ChatFormatting.GRAY)) + 6;
        button(X, y, W, "Back", null, b -> onClose());
    }
}
