package net.skyah.mod;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkyAhMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("skyah");
    public static final String VERSION = "0.2.1";
    /** Build tag in the log, so a log shows which jar was running. */
    public static final String BUILD = "gen20";
    public static SkyAhConfig CONFIG;
    public static FlipsSocket SOCKET;
    /** Deal hotkey, unbound until the player picks a key (K menu or Controls). */
    public static KeyMapping OPEN_KEY;

    private static final Gson GSON = new Gson();
    private static final Pattern PURCHASE_RE = Pattern.compile(
            "(?i)^(?:\\[Auction\\]\\s*)?(?:You\\s+)?(?:bought|purchased)\\s+(.+?)\\s+for\\s+([\\d,]+)\\s+coins");
    private static volatile String lastPurchaseKey = "";
    private static volatile long lastPurchaseAt = 0L;

    @Override
    public void onInitializeClient() {
        CONFIG = SkyAhConfig.load();
        SOCKET = new FlipsSocket();
        SkyAhLog.add("skyah " + VERSION + " " + BUILD + " started (enabled=" + CONFIG.enabled + ")");
        if (CONFIG.enabled) {
            SOCKET.start();
        }
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyah.menu", keyboardType(), InputConstants.KEY_K,
                KeyMapping.Category.MISC));
        OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyah.open_deal", keyboardType(), InputConstants.UNKNOWN.getValue(),
                KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.consumeClick()) {
                client.setScreenAndShow(new SkyAhConfigScreen());
            }
            while (OPEN_KEY.consumeClick()) {
                SkyAhHotkey.openNext(client);
            }
            SkyAhPurse.tick(client);
            SkyAhOpenTracker.tick();
            maybeActivationReminder(client);
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                maybePurchase(message.getString());
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterForeground(screen).register(
                    (s, extractor, mouseX, mouseY, delta) -> SkyAhPanel.draw(s, extractor));
            SkyAhOpenTracker.screenInit(screen);
            if (screen instanceof AbstractContainerScreen<?>) {
                // Open measurement for BIN deals: checked every frame while tracked.
                ScreenEvents.afterForeground(screen).register(
                        (s, extractor, mouseX, mouseY, delta) -> SkyAhOpenTracker.frame(s));
                ScreenEvents.remove(screen).register(SkyAhOpenTracker::removed);
                // Key mappings do not fire while a menu is open; the AH is one.
                ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> {
                    if (!SkyAhHotkey.matchesInMenu(OPEN_KEY, e)) {
                        return true;
                    }
                    SkyAhHotkey.openNext(client);
                    return false;
                });
                ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> {
                    if (!SkyAhHotkey.matchesInMenu(OPEN_KEY, e)) {
                        return true;
                    }
                    SkyAhHotkey.openNext(client);
                    return false;
                });
            }
        });
        registerCommands();
        LOGGER.info("SkyAH mod initialized (ws={}, enabled={})", CONFIG.effectiveUrl(), CONFIG.enabled);
    }

    /** 26.3 renamed the enum constant KEYSYM -> KEYBOARD; accept both. */
    private static InputConstants.Type keyboardType() {
        try {
            return InputConstants.Type.valueOf("KEYBOARD");
        } catch (IllegalArgumentException e) {
            return InputConstants.Type.valueOf("KEYSYM");
        }
    }

    /** Your purchases on skyah.net/api: match Hypixel buy messages and send
     *  them with the auction we recently displayed for that item, when we
     *  have one. Nothing else from chat is used. */
    private static void maybePurchase(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher m = PURCHASE_RE.matcher(text.trim());
        if (!m.find()) {
            return;
        }
        String name = m.group(1).trim();
        long price;
        try {
            price = Long.parseLong(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return;
        }
        String key = name + "|" + price;
        long now = System.currentTimeMillis();
        if (key.equals(lastPurchaseKey) && now - lastPurchaseAt < 30_000L) {
            return;
        }
        lastPurchaseKey = key;
        lastPurchaseAt = now;
        String aid = FlipChat.lookupAuctionId(name);
        // Exact path: if the user clicked a flip recently and the name roughly
        // matches, we know the auction id for sure.
        if (aid == null) {
            Flip pending = SkyAhPanel.pendingFlip();
            if (pending != null && pending.auctionId != null
                    && SkyAhPanel.normalized(pending.name).equals(SkyAhPanel.normalized(name))) {
                aid = pending.auctionId;
            }
        }
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("type", "purchase");
        evt.put("name", name);
        evt.put("price", price);
        if (aid != null) {
            evt.put("auction_id", aid);
        }
        boolean sent = SOCKET.send(GSON.toJson(evt));
        SkyAhLog.add((sent ? "purchase sent: " : "purchase (offline): ") + name + " " + FlipChat.fmt(price));
    }

    /** Filter and chat settings, for usage stats. */
    public static void sendSettings() {
        if (SOCKET == null || !SOCKET.isOpen()) {
            return;
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "settings");
        msg.put("version", VERSION);
        msg.put("minProfit", CONFIG.minProfit);
        msg.put("minPct", CONFIG.minPct);
        msg.put("maxPrice", CONFIG.maxPrice);
        msg.put("maxPriceFromPurse", CONFIG.maxPriceFromPurse);
        msg.put("minSalesPerDay", CONFIG.minSalesPerDay);
        msg.put("whitelist", CONFIG.whitelist);
        msg.put("blacklist", CONFIG.blacklist);
        msg.put("sound", CONFIG.sound);
        msg.put("soundMinProfit", CONFIG.soundMinProfit);
        msg.put("screenAlert", CONFIG.screenAlert);
        msg.put("screenAlertMinProfit", CONFIG.screenAlertMinProfit);
        msg.put("clickAction", CONFIG.clickAction);
        msg.put("showBin", CONFIG.showBin);
        msg.put("showAuctions", CONFIG.showAuctions);
        msg.put("showTarget", CONFIG.showTarget);
        msg.put("showReplay", CONFIG.showReplay);
        msg.put("showPrice", CONFIG.showPrice);
        msg.put("showProfit", CONFIG.showProfit);
        msg.put("showPct", CONFIG.showPct);
        msg.put("showFee", CONFIG.showFee);
        msg.put("showFloor", CONFIG.showFloor);
        msg.put("showVolume", CONFIG.showVolume);
        msg.put("showSellTime", CONFIG.showSellTime);
        msg.put("useRealProfit", CONFIG.useRealProfit);
        msg.put("floorFilter", CONFIG.floorFilter);
        msg.put("colorRules", CONFIG.colorRules);
        if (SOCKET.send(GSON.toJson(msg))) {
            SkyAhLog.add("settings synced");
        }
    }

    /** Chat reminder while the key is inactive (4402), at most every 5 min. */
    private static long lastActivationReminder = 0L;

    private static void maybeActivationReminder(Minecraft client) {
        FlipsSocket socket = SOCKET;
        if (socket == null || client.player == null) {
            return;
        }
        if (socket.isOpen()) {
            lastActivationReminder = 0L;
            return;
        }
        if (socket.lastCloseCode != 4402) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastActivationReminder != 0L && now - lastActivationReminder < 5 * 60_000L) {
            return;
        }
        lastActivationReminder = now;
        Component link = Component.literal("activate on skyah.net/api")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.OpenUrl(
                                java.net.URI.create("https://skyah.net/api"))));
        client.player.sendSystemMessage(
                Component.literal("[SkyAH] Key inactive - ").withStyle(ChatFormatting.YELLOW)
                        .append(link)
                        .append(Component.literal(" (one bid, no new key)").withStyle(ChatFormatting.GRAY)));
    }

    /** Copy the whole in-memory log to the clipboard (chat is optional). */
    public static boolean copyLogs() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.keyboardHandler == null) {
                return false;
            }
            java.util.List<String> lines = SkyAhLog.snapshot();
            mc.keyboardHandler.setClipboard(String.join(System.lineSeparator(), lines));
            if (mc.gui != null) {
                mc.gui.chatListener().handleSystemMessage(Component.literal(
                                "SkyAH logs copied (" + lines.size() + " lines)")
                        .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Logs go to chat instead of a custom screen: 26.3 moved the render
     *  pipeline (GuiGraphicsExtractor), and chat is version-stable. */
    public static void printLogs() {
        java.util.List<String> lines = SkyAhLog.snapshot();
        int from = Math.max(0, lines.size() - 60);
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return;
        }
        mc.gui.chatListener().handleSystemMessage(Component.literal(
                        "SkyAH logs (" + (lines.size() - from) + "/" + lines.size() + "):")
                .withStyle(net.minecraft.ChatFormatting.GOLD), false);
        for (int i = from; i < lines.size(); i++) {
            mc.gui.chatListener().handleSystemMessage(
                    Component.literal(lines.get(i)).withStyle(net.minecraft.ChatFormatting.GRAY), false);
        }
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("skyah")
                        .then(ClientCommands.literal("status").executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "SkyAH: " + (SOCKET.isOpen() ? "connected" : "disconnected")
                                            + " | " + CONFIG.effectiveUrl().replaceAll("key=[^&]+", "key=***")
                                            + " | minProfit " + FlipChat.fmt(CONFIG.minProfit)
                                            + " | " + (CONFIG.enabled ? "on" : "off")));
                            return 1;
                        }))
                        .then(ClientCommands.literal("menu").executes(ctx -> {
                            ctx.getSource().getClient().setScreenAndShow(new SkyAhConfigScreen());
                            return 1;
                        }))
                        .then(ClientCommands.literal("logs").executes(ctx -> {
                            printLogs();
                            return 1;
                        }))
                        .then(ClientCommands.literal("latency").executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal(
                                    "SkyAH latency: " + SkyAhLatency.summary()
                                            + " | rtt " + (SOCKET.rttMs >= 0 ? SOCKET.rttMs + "ms" : "?")));
                            return 1;
                        }))
                        .then(ClientCommands.literal("reconnect").executes(ctx -> {
                            SOCKET.restart();
                            ctx.getSource().sendFeedback(Component.literal("SkyAH: reconnecting"));
                            return 1;
                        }))
                        .then(ClientCommands.literal("toggle").executes(ctx -> {
                            CONFIG.enabled = !CONFIG.enabled;
                            CONFIG.save();
                            if (CONFIG.enabled) {
                                SOCKET.start();
                            } else {
                                SOCKET.stop();
                            }
                            ctx.getSource().sendFeedback(Component.literal(
                                    "SkyAH: " + (CONFIG.enabled ? "on" : "off")));
                            return 1;
                        }))
                        .then(ClientCommands.literal("minprofit")
                                .then(ClientCommands.argument("coins", LongArgumentType.longArg(0))
                                        .executes(ctx -> {
                                            CONFIG.minProfit = LongArgumentType.getLong(ctx, "coins");
                                            CONFIG.save();
                                            sendSettings();
                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "SkyAH: minProfit " + FlipChat.fmt(CONFIG.minProfit)));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("url")
                                .then(ClientCommands.argument("url", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            CONFIG.wsUrl = StringArgumentType.getString(ctx, "url");
                                            CONFIG.save();
                                            SOCKET.restart();
                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "SkyAH: url set, reconnecting"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("view")
                                .then(ClientCommands.argument("id", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "id");
                                            Flip f = FlipChat.byAuctionId(id);
                                            if (f != null) {
                                                f.opened = true;
                                                SkyAhPanel.setPending(f);
                                                SkyAhOpenTracker.requested(f, "chat");
                                                SkyAhLog.add("view: flip known (" + f.name + "), panel armed");
                                            } else {
                                                SkyAhLog.add("view: no flip cached for " + id
                                                        + " (old chat line or restart)");
                                            }
                                            net.minecraft.client.multiplayer.ClientPacketListener conn =
                                                    ctx.getSource().getClient().getConnection();
                                            if (conn != null) {
                                                conn.sendCommand("viewauction " + id);
                                            }
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("profile")
                                .executes(ctx -> {
                                    StringBuilder names = new StringBuilder();
                                    if (CONFIG.profiles != null) {
                                        for (SkyAhProfile p : CONFIG.profiles) {
                                            if (p == null || p.name == null) {
                                                continue;
                                            }
                                            if (names.length() > 0) {
                                                names.append(", ");
                                            }
                                            names.append(p.name);
                                        }
                                    }
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "SkyAH profiles: " + (names.length() == 0 ? "(none)" : names)));
                                    return 1;
                                })
                                .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name").trim();
                                            SkyAhProfile hit = null;
                                            if (CONFIG.profiles != null) {
                                                for (SkyAhProfile p : CONFIG.profiles) {
                                                    if (p != null && p.name != null && name.equalsIgnoreCase(p.name)) {
                                                        hit = p;
                                                    }
                                                }
                                            }
                                            if (hit == null) {
                                                ctx.getSource().sendFeedback(Component.literal(
                                                        "SkyAH: no profile named '" + name + "'"));
                                                return 0;
                                            }
                                            hit.applyTo(CONFIG);
                                            CONFIG.save();
                                            sendSettings();
                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "SkyAH: profile applied - " + hit.name));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("key")
                                .then(ClientCommands.argument("key", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String k = StringArgumentType.getString(ctx, "key").trim();
                                            CONFIG.apiKey = k;
                                            CONFIG.save();
                                            SOCKET.restart();
                                            ctx.getSource().sendFeedback(Component.literal(
                                                    "SkyAH: key set (" + k.length() + " chars), reconnecting"));
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("test").executes(ctx -> {
                            // Example deals, shown even when the filters would hide them.
                            for (Flip f : FlipChat.samples()) {
                                f.auctionId = "test";
                                FlipChat.display(f, CONFIG);
                            }
                            return 1;
                        }))));
    }
}
