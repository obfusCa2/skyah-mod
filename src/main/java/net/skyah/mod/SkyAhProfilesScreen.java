package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/** Profiles: the built-in ones every player has, then your own (activate,
 *  save current, delete, export/import codes). One list with pages, so many
 *  profiles never push the buttons off the screen. */
public class SkyAhProfilesScreen extends SkyAhScreen {
    private static final int MAX_PROFILES = 10;

    private String typedName = "";
    private String status = "";
    private int page = 0;

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
        y += para(X, y, W, Component.literal("Click a profile to use it. Export copies a code to share, "
                + "Import reads one from the clipboard.").withStyle(ChatFormatting.GRAY)) + 6;

        int sideW = 64;
        int delW = 20;
        int listW = W - sideW - delW - 12;
        List<Object> items = new ArrayList<>(SkyAhPresets.ALL);
        items.addAll(cfg.profiles);
        // Rows that fit above the name box, the import row and the status line.
        int fit = Math.max(1, (this.height - 8 - 12 - 2 * GAP - y) / GAP);
        int pages = 1;
        List<Object> shown = items;
        if (items.size() > fit) {
            int per = Math.max(1, fit - 1);
            pages = (items.size() + per - 1) / per;
            page = Math.min(page, pages - 1);
            shown = items.subList(page * per, Math.min(items.size(), page * per + per));
        }
        for (Object it : shown) {
            if (it instanceof SkyAhPresets.Preset p) {
                button(X, y, W, presetLabel(p, cfg), p.tip(), b -> {
                    SkyAhPresets.apply(p, cfg);
                    changed();
                    SkyAhMod.sendSettings();
                    status = "Using " + p.name();
                    this.rebuildWidgets();
                });
            } else if (it instanceof SkyAhProfile p) {
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
            }
            y += GAP;
        }
        if (pages > 1) {
            int total = pages;
            button(X, y, 40, "<", null, b -> {
                page = (page + total - 1) % total;
                this.rebuildWidgets();
            });
            int pageNo = page + 1;
            text(X + 46, y + (H - 8) / 2, () -> Component.literal("Page " + pageNo + " / " + total).withStyle(ChatFormatting.GRAY));
            button(X + W - 40, y, 40, ">", null, b -> {
                page = (page + 1) % total;
                this.rebuildWidgets();
            });
            y += GAP;
        }

        box(X, y, listW, typedName, "name for your own profile", 24, s -> typedName = s);
        button(X + listW + 6, y, W - listW - 6, "Save current", "Save your current filters and colors under this name", b -> {
            String name = typedName.trim();
            if (name.isEmpty()) {
                status = "Type a name first";
                this.rebuildWidgets();
                return;
            }
            if (SkyAhPresets.isPresetName(name)) {
                status = "That name is taken by a built-in profile";
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

    private static Component presetLabel(SkyAhPresets.Preset p, SkyAhConfig cfg) {
        Component desc = Component.literal(" · " + p.desc()).withStyle(ChatFormatting.GRAY);
        if (SkyAhPresets.matches(p, cfg)) {
            return Component.literal("▶ " + p.name()).withStyle(ChatFormatting.GREEN).append(desc);
        }
        return Component.literal(p.name()).append(desc);
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
