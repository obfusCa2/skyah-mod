package net.skyah.mod;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Copy-paste profile sharing: "skyah-profile:" + base64url(JSON). */
public final class ProfileCode {
    public static final String PREFIX = "skyah-profile:";
    private static final Gson GSON = new Gson();
    private static final int MAX_NAME = 24;
    private static final int MAX_LIST = 500;
    private static final int MAX_RULES = 8;

    private ProfileCode() {
    }

    public static String encode(SkyAhProfile p) {
        String json = GSON.toJson(p);
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns a validated profile, or null when the text is not a profile code. */
    public static SkyAhProfile decode(String code) {
        if (code == null) {
            return null;
        }
        String s = code.trim();
        if (!s.startsWith(PREFIX)) {
            return null;
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(s.substring(PREFIX.length()).trim());
            SkyAhProfile p = GSON.fromJson(new String(raw, StandardCharsets.UTF_8), SkyAhProfile.class);
            return validate(p);
        } catch (Exception e) {
            return null;
        }
    }

    private static SkyAhProfile validate(SkyAhProfile p) {
        if (p == null) {
            return null;
        }
        String name = p.name == null ? "" : p.name.trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            return null;
        }
        p.name = name;
        p.minProfit = Math.max(0L, p.minProfit);
        p.minPct = Math.max(0.0, Math.min(10_000.0, p.minPct));
        p.maxPrice = Math.max(0L, p.maxPrice);
        p.minSalesPerDay = Math.max(0.0, Math.min(100_000.0, p.minSalesPerDay));
        p.maxSellTimeS = Math.max(0L, Math.min(7 * 86_400L, p.maxSellTimeS));
        p.whitelist = clip(p.whitelist);
        p.blacklist = clip(p.blacklist);
        if (p.floorFilter == null
                || !List.of("all", "above", "below", "has").contains(p.floorFilter)) {
            p.floorFilter = "all";
        }
        List<ColorRule> rules = SkyAhProfile.copyRules(p.colorRules);
        if (rules.size() > MAX_RULES) {
            rules = new ArrayList<>(rules.subList(0, MAX_RULES));
        }
        for (ColorRule r : rules) {
            r.minProfit = Math.max(0L, r.minProfit);
            r.minPct = Math.max(0.0, Math.min(10_000.0, r.minPct));
            if (r.color == null || r.color.isBlank()) {
                r.color = "GREEN";
            }
        }
        p.colorRules = rules;
        return p;
    }

    private static String clip(String v) {
        if (v == null) {
            return "";
        }
        String s = v.trim();
        return s.length() > MAX_LIST ? s.substring(0, MAX_LIST) : s;
    }
}
