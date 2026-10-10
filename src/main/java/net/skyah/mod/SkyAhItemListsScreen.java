package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Only/never item lists, kept out of the main filters. */
public class SkyAhItemListsScreen extends SkyAhScreen {
    public SkyAhItemListsScreen(Screen parent) {
        super("Item lists", parent);
    }

    @Override
    protected void build() {
        SkyAhConfig cfg = cfg();
        int y = 24;
        y += para(X, y, W, Component.literal("Optional. Comma separated, item id or part of the name, e.g. hyperion, juju.")
                .withStyle(ChatFormatting.GRAY)) + 6;
        int labelW = 70;
        label(X, y, "Only items");
        box(X + labelW, y, W - labelW, cfg.whitelist, "empty = every item", 400, s -> {
            cfg.whitelist = s;
            changed();
        }).setTooltip(Tooltip.create(Component.literal("Only these items show up. Empty = every item.")));
        y += GAP;
        label(X, y, "Never items");
        box(X + labelW, y, W - labelW, cfg.blacklist, "e.g. dye, rune", 400, s -> {
            cfg.blacklist = s;
            changed();
        }).setTooltip(Tooltip.create(Component.literal("These items never show, even if they are on the first list.")));
        y += GAP + 6;
        button(X, y, W, "Done", null, b -> onClose());
    }
}
