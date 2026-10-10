package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/** Named filter profiles: activate, save current, delete, export/import codes.
 *  A profile holds filters + color rules only, never key or url. */
public class SkyAhProfilesScreen extends SkyAhScreen {
    private static final int MAX_PROFILES = 10;

    private EditBox nameBox;
    private String typedName = "";
    private String status = "";

    public SkyAhProfilesScreen(Screen parent) {
        super("Profiles", parent);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        if (cfg.profiles == null) {
            cfg.profiles = new ArrayList<>();
        }
        int y = 24;
        y += para(X, y, W, Component.literal("A profile stores filters and profit colors. Click one to use it. "
                + "Export copies a code you can paste in Discord; Import reads it from the clipboard.")
                .withStyle(ChatFormatting.GRAY)) + 6;

        int sideW = 64;
        int delW = 20;
        int listW = W - sideW - delW - 12;
        if (cfg.profiles.isEmpty()) {
            text(X, y + (H - 8) / 2, () -> Component.literal("No profiles yet. Type a name below and save your current filters.")
                    .withStyle(ChatFormatting.DARK_GRAY));
            y += GAP;
        }
        for (int i = 0; i < cfg.profiles.size(); i++) {
            SkyAhProfile p = cfg.profiles.get(i);
            button(X, y, listW, profileLabel(p, cfg), "Use this profile", b -> {
                p.applyTo(cfg);
                changed();
                SkyAhMod.sendSettings();
                status = "Using " + p.name;
                this.rebuildWidgets();
            });
            button(X + listW + 6, y, sideW, "Export", "Copy a share code to the clipboard", b -> {
                String code = ProfileCode.encode(p);
                this.minecraft.keyboardHandler.setClipboard(code);
                SkyAhLog.add("profile code copied: " + p.name + " (" + code.length() + " chars)");
                status = "Code for " + p.name + " copied";
                this.rebuildWidgets();
            });
            button(X + W - delW, y, delW, Component.literal("X").withStyle(ChatFormatting.RED), "Delete " + p.name, b -> {
                cfg.profiles.remove(p);
                if (p.name != null && p.name.equals(cfg.activeProfile)) {
                    cfg.activeProfile = "";
                }
                changed();
                status = "Deleted " + p.name;
                this.rebuildWidgets();
            });
            y += GAP;
        }

        nameBox = box(X, y, listW, typedName, "new profile name", 24, s -> typedName = s);
        button(X + listW + 6, y, W - listW - 6, "Save current", "Save your current filters and colors under this name", b -> {
            String name = typedName.trim();
            if (name.isEmpty()) {
                status = "Type a name first";
                this.rebuildWidgets();
                return;
            }
            SkyAhProfile p = SkyAhProfile.fromConfig(cfg, name);
            int existing = indexOf(cfg.profiles, name);
            if (existing >= 0) {
                cfg.profiles.set(existing, p);
            } else if (cfg.profiles.size() >= MAX_PROFILES) {
                status = "Max " + MAX_PROFILES + " profiles, delete one first";
                this.rebuildWidgets();
                return;
            } else {
                cfg.profiles.add(p);
            }
            cfg.activeProfile = name;
            typedName = "";
            changed();
            status = "Saved " + name;
            this.rebuildWidgets();
        });
        y += GAP;
        button(X, y, half(), "Import from clipboard", "Paste a profile code someone shared", b -> {
            SkyAhProfile p = ProfileCode.decode(this.minecraft.keyboardHandler.getClipboard());
            if (p == null) {
                status = "No profile code in the clipboard";
            } else {
                int existing = indexOf(cfg.profiles, p.name);
                if (existing >= 0) {
                    cfg.profiles.set(existing, p);
                    status = "Imported " + p.name + " (replaced)";
                } else if (cfg.profiles.size() >= MAX_PROFILES) {
                    status = "Max " + MAX_PROFILES + " profiles, delete one first";
                } else {
                    cfg.profiles.add(p);
                    status = "Imported " + p.name + ", click it to use it";
                }
                changed();
            }
            this.rebuildWidgets();
        });
        button(right(), y, half(), "Back", null, b -> onClose());
        y += GAP + 2;
        text(X, y, () -> status.isEmpty() ? null : Component.literal(status).withStyle(ChatFormatting.YELLOW));
    }

    /** "▶ name" while the settings equal the profile, "name (changed)" after edits. */
    private static Component profileLabel(SkyAhProfile p, SkyAhConfig cfg) {
        boolean active = p.name != null && p.name.equals(cfg.activeProfile);
        if (p.matches(cfg)) {
            return Component.literal("▶ " + p.name).withStyle(ChatFormatting.GREEN);
        }
        MutableComponent out = Component.literal(p.name == null ? "?" : p.name);
        if (active) {
            out.append(Component.literal(" (changed)").withStyle(ChatFormatting.GRAY));
        }
        return out;
    }

    private static int indexOf(List<SkyAhProfile> profiles, String name) {
        for (int i = 0; i < profiles.size(); i++) {
            SkyAhProfile p = profiles.get(i);
            if (p != null && p.name != null && p.name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }
}
