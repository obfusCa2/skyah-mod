package net.skyah.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Settings, stored as JSON in config/skyah.json. */
public class SkyAhConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("skyah.json");
    public static final String DEFAULT_URL = "wss://skyah.net/item-api/ws/v1/flips";
    /** Older files are migrated in load(). */
    static final int CURRENT_VERSION = 3;

    /** 0 = written by a mod before versioned configs. */
    public int configVersion = 0;
    public String wsUrl = DEFAULT_URL;
    public String apiKey = "";
    public boolean enabled = true;
    public long minProfit = 1_000_000L;
    public double minPct = 5;
    public long maxPrice = 0;
    /** Also cap the price at the coins in your purse (read from the sidebar). */
    public boolean maxPriceFromPurse = false;
    /** Hide deals whose estimated sell time is longer than this (seconds, 0 = off). */
    public long maxSellTimeS = 0;
    /** No longer used by the filters (kept so old files and profile codes load). */
    public double minSalesPerDay = 0;
    public String whitelist = "";
    public String blacklist = "";
    public boolean sound = true;
    /** Sound only for deals with at least this profit (0 = every deal). */
    public long soundMinProfit = 10_000_000L;
    public String soundName = "NOTE_BLOCK_PLING";
    /** Big title in the middle of the screen for new deals (opt-in). */
    public boolean screenAlert = false;
    /** Screen alert only from this profit (0 = every deal). */
    public long screenAlertMinProfit = 10_000_000L;
    /** Hold mode for the deal hotkey (opt-in, see SkyAhHold). */
    public boolean holdMode = false;
    /** Hold the key this long before hold mode takes over (ms). */
    public int holdAfterMs = 300;
    /** Wait about this long on a sold deal before the next one opens (ms). */
    public int holdDelayMs = 300;
    // run | copy
    public String clickAction = "run";
    public boolean showBin = true;
    public boolean showAuctions = true;
    public boolean showReplay = false;
    /** No longer used by the filters: the profit already counts the cheapest listing. */
    public String floorFilter = "all";

    // What the chat line shows. Profit is always shown.
    public boolean showPrice = true;
    public boolean showTarget = true;
    public boolean showProfit = true;
    public boolean showPct = true;
    public boolean showFee = false;
    public boolean showFloor = true;
    public boolean showVolume = true;
    public boolean showSellTime = false;
    /** Filter/rules/display use real_profit (min of target and floor) when set. */
    public boolean useRealProfit = true;

    /** Color rules, first match wins. Condition = profit AND percent. */
    public List<ColorRule> colorRules = ColorRule.defaults();

    /** Named filter/color profiles (shareable), plus the active one's name. */
    public List<SkyAhProfile> profiles = new ArrayList<>();
    public String activeProfile = "";

    /** Connect URL. The key travels in the Authorization header (see
     *  FlipsSocket), never in the URL: URLs end up in server access logs. */
    public String effectiveUrl() {
        return wsUrl == null || wsUrl.isBlank() ? DEFAULT_URL : wsUrl.trim();
    }

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Recommended setup for filters, chat line, colors and sound. Key, url,
     *  item lists and profiles stay as they are. */
    public void applyRecommended() {
        SkyAhConfig d = new SkyAhConfig();
        minProfit = d.minProfit;
        minPct = d.minPct;
        maxPrice = d.maxPrice;
        maxPriceFromPurse = d.maxPriceFromPurse;
        minSalesPerDay = d.minSalesPerDay;
        maxSellTimeS = d.maxSellTimeS;
        showVolume = d.showVolume;
        showSellTime = d.showSellTime;
        floorFilter = d.floorFilter;
        showBin = d.showBin;
        showAuctions = d.showAuctions;
        useRealProfit = d.useRealProfit;
        showPrice = d.showPrice;
        showTarget = d.showTarget;
        showProfit = d.showProfit;
        showPct = d.showPct;
        showFee = d.showFee;
        showFloor = d.showFloor;
        showReplay = d.showReplay;
        clickAction = d.clickAction;
        sound = d.sound;
        soundMinProfit = d.soundMinProfit;
        colorRules = ColorRule.defaults();
        activeProfile = "";
    }

    /** Version 1: the old default color rules lose their percent condition,
     *  and sound keeps playing for every deal. Version 2: filters without a
     *  button any more (floor, sales per day, profit at target) go neutral, so
     *  nothing hides deals invisibly. */
    void migrate() {
        if (configVersion >= CURRENT_VERSION) {
            return;
        }
        if (configVersion < 2) {
            if (colorRules != null && colorRules.size() == 3
                    && isRule(colorRules.get(0), 50_000_000L, 200)
                    && isRule(colorRules.get(1), 10_000_000L, 100)
                    && isRule(colorRules.get(2), 1_000_000L, 50)) {
                for (ColorRule r : colorRules) {
                    r.minPct = 0;
                }
                SkyAhLog.add("config: old color rules migrated (percent condition removed)");
            }
            soundMinProfit = 0L;
        }
        if (configVersion < 3) {
            floorFilter = "all";
            minSalesPerDay = 0;
            useRealProfit = true;
            SkyAhLog.add("config: floor, sales per day and profit mode reset (no longer in the menu)");
        }
        configVersion = CURRENT_VERSION;
    }

    private static boolean isRule(ColorRule r, long profit, double pct) {
        return r != null && r.minProfit == profit && r.minPct == pct;
    }

    public static SkyAhConfig load() {
        try {
            if (Files.exists(PATH)) {
                SkyAhConfig cfg = GSON.fromJson(Files.readString(PATH), SkyAhConfig.class);
                if (cfg != null) {
                    if (cfg.wsUrl == null || cfg.wsUrl.isBlank()) {
                        cfg.wsUrl = DEFAULT_URL;
                    }
                    if (cfg.colorRules == null || cfg.colorRules.isEmpty()) {
                        cfg.colorRules = ColorRule.defaults();
                    }
                    cfg.colorRules = new ArrayList<>(cfg.colorRules);
                    if (cfg.profiles == null) {
                        cfg.profiles = new ArrayList<>();
                    }
                    if (cfg.activeProfile == null) {
                        cfg.activeProfile = "";
                    }
                    if (cfg.whitelist == null) {
                        cfg.whitelist = "";
                    }
                    if (cfg.blacklist == null) {
                        cfg.blacklist = "";
                    }
                    int before = cfg.configVersion;
                    cfg.migrate();
                    if (before != cfg.configVersion) {
                        cfg.save();
                    }
                    SkyAhLog.add("config loaded: key "
                            + (cfg.apiKey == null ? 0 : cfg.apiKey.trim().length()) + " chars");
                    return cfg;
                }
            }
        } catch (Exception ignored) {
        }
        SkyAhConfig cfg = new SkyAhConfig();
        cfg.configVersion = CURRENT_VERSION;
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception e) {
            SkyAhLog.add("config save failed: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " " + e.getMessage()));
        }
    }
}
