package net.skyah.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A named, shareable set of filter/color settings. Never contains key or url.
 *  minSalesPerDay and floorFilter are only read from old codes; the filters no
 *  longer use them. */
public class SkyAhProfile {
    public String name = "";
    public long minProfit;
    public double minPct;
    public long maxPrice;
    public boolean maxPriceFromPurse;
    public double minSalesPerDay;
    public long maxSellTimeS;
    public boolean showBin = true;
    public boolean showAuctions = true;
    public String whitelist = "";
    public String blacklist = "";
    public String floorFilter = "all";
    public List<ColorRule> colorRules = ColorRule.defaults();

    public SkyAhProfile() {
    }

    public static SkyAhProfile fromConfig(SkyAhConfig cfg, String name) {
        SkyAhProfile p = new SkyAhProfile();
        p.name = name;
        p.minProfit = cfg.minProfit;
        p.minPct = cfg.minPct;
        p.maxPrice = cfg.maxPrice;
        p.maxPriceFromPurse = cfg.maxPriceFromPurse;
        p.maxSellTimeS = cfg.maxSellTimeS;
        p.showBin = cfg.showBin;
        p.showAuctions = cfg.showAuctions;
        p.whitelist = cfg.whitelist == null ? "" : cfg.whitelist;
        p.blacklist = cfg.blacklist == null ? "" : cfg.blacklist;
        p.colorRules = copyRules(cfg.colorRules);
        return p;
    }

    public void applyTo(SkyAhConfig cfg) {
        cfg.minProfit = minProfit;
        cfg.minPct = minPct;
        cfg.maxPrice = maxPrice;
        cfg.maxPriceFromPurse = maxPriceFromPurse;
        cfg.maxSellTimeS = maxSellTimeS;
        cfg.showBin = showBin;
        cfg.showAuctions = showAuctions;
        cfg.whitelist = whitelist == null ? "" : whitelist;
        cfg.blacklist = blacklist == null ? "" : blacklist;
        cfg.colorRules = copyRules(colorRules);
        cfg.activeProfile = name;
    }

    /** True while the current settings still equal this profile. */
    public boolean matches(SkyAhConfig cfg) {
        if (minProfit != cfg.minProfit || minPct != cfg.minPct || maxPrice != cfg.maxPrice
                || maxPriceFromPurse != cfg.maxPriceFromPurse || maxSellTimeS != cfg.maxSellTimeS
                || showBin != cfg.showBin || showAuctions != cfg.showAuctions
                || !Objects.equals(nz(whitelist), nz(cfg.whitelist))
                || !Objects.equals(nz(blacklist), nz(cfg.blacklist))) {
            return false;
        }
        List<ColorRule> a = colorRules == null ? List.of() : colorRules;
        List<ColorRule> b = cfg.colorRules == null ? List.of() : cfg.colorRules;
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            ColorRule x = a.get(i);
            ColorRule y = b.get(i);
            if (x == null || y == null) {
                if (x != y) {
                    return false;
                }
                continue;
            }
            if (x.minProfit != y.minProfit || x.minPct != y.minPct
                    || !String.valueOf(x.color).equalsIgnoreCase(String.valueOf(y.color))) {
                return false;
            }
        }
        return true;
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    public static List<ColorRule> copyRules(List<ColorRule> in) {
        List<ColorRule> out = new ArrayList<>();
        if (in != null) {
            for (ColorRule r : in) {
                if (r != null) {
                    out.add(new ColorRule(r.minProfit, r.minPct, r.color));
                }
            }
        }
        return out;
    }
}
