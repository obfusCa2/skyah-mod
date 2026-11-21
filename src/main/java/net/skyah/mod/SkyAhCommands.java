package net.skyah.mod;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /skyah help and the item list commands (/skyah bl add|remove|list, /skyah wl ...). The list editing
 *  works on the same comma separated lists as the menu's item lists. */
final class SkyAhCommands {
    private SkyAhCommands() {
    }

    /** The lines of /skyah help. */
    static List<String> help() {
        List<String> out = new ArrayList<>();
        out.add("SkyAH commands:");
        out.add("/skyah  opens the menu (or press K)");
        out.add("/skyah flip  deals in chat on or off");
        out.add("/skyah minprofit 2m  minimum profit");
        out.add("/skyah profile <name>  use a profile: Starter, Balanced, Big flips or your own");
        out.add("/skyah bl add <item>  never show this item (bl remove, bl list)");
        out.add("/skyah wl add <item>  only show these items (wl remove, wl list)");
        out.add("/skyah test  two example deals in chat");
        out.add("/skyah panels  move, resize or hide the panels on screen");
        out.add("/skyah status, /skyah reconnect, /skyah logs copy");
        return out;
    }

    /** The entries of a comma separated list, trimmed, empty ones left out. */
    static List<String> entries(String list) {
        List<String> out = new ArrayList<>();
        if (list != null) {
            for (String t : list.split(",")) {
                if (!t.isBlank()) {
                    out.add(t.strip());
                }
            }
        }
        return out;
    }

    /** The list with the item added (unchanged when it is there already, case ignored). */
    static String add(String list, String item) {
        List<String> e = entries(list);
        String it = item == null ? "" : item.strip();
        if (it.isEmpty() || indexOf(e, it) >= 0) {
            return String.join(", ", e);
        }
        e.add(it);
        return String.join(", ", e);
    }

    /** The list without the item (case ignored). */
    static String remove(String list, String item) {
        List<String> e = entries(list);
        int i = indexOf(e, item == null ? "" : item.strip());
        if (i >= 0) {
            e.remove(i);
        }
        return String.join(", ", e);
    }

    private static int indexOf(List<String> e, String item) {
        String want = item.toLowerCase(Locale.ROOT);
        for (int i = 0; i < e.size(); i++) {
            if (e.get(i).toLowerCase(Locale.ROOT).equals(want)) {
                return i;
            }
        }
        return -1;
    }

    /** /skyah bl|blacklist|wl|whitelist [list] | add <item> | remove <item>. never: the "never" list. */
    static LiteralArgumentBuilder<FabricClientCommandSource> listCommand(String name, boolean never) {
        String title = never ? "Never items" : "Only items";
        return ClientCommands.literal(name)
                .executes(ctx -> show(ctx.getSource(), never, title))
                .then(ClientCommands.literal("list").executes(ctx -> show(ctx.getSource(), never, title)))
                .then(ClientCommands.literal("add").then(ClientCommands.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> change(ctx.getSource(), never, StringArgumentType.getString(ctx, "item"), true))))
                .then(ClientCommands.literal("remove").then(ClientCommands.argument("item", StringArgumentType.greedyString())
                        .executes(ctx -> change(ctx.getSource(), never, StringArgumentType.getString(ctx, "item"), false))));
    }

    private static int show(FabricClientCommandSource source, boolean never, String title) {
        List<String> e = entries(never ? SkyAhMod.CONFIG.blacklist : SkyAhMod.CONFIG.whitelist);
        source.sendFeedback(Component.literal("SkyAH " + title.toLowerCase(Locale.ROOT) + " (" + e.size() + "): "
                + (e.isEmpty() ? "none" : String.join(", ", e))));
        return 1;
    }

    private static int change(FabricClientCommandSource source, boolean never, String item, boolean add) {
        SkyAhConfig cfg = SkyAhMod.CONFIG;
        String before = never ? cfg.blacklist : cfg.whitelist;
        String after = add ? add(before, item) : remove(before, item);
        if (never) {
            cfg.blacklist = after;
        } else {
            cfg.whitelist = after;
        }
        cfg.save();
        SkyAhMod.sendSettings();
        boolean changed = !entries(before).equals(entries(after));
        String what = never ? "never items" : "only items";
        source.sendFeedback(Component.literal("SkyAH: " + item.strip() + (changed
                ? (add ? " added to " : " removed from ") + what : (add ? " is already on " : " is not on ") + what)));
        return 1;
    }
}
