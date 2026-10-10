package net.skyah.mod;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileCodeTest {
    @Test
    void roundTrip() {
        SkyAhProfile p = new SkyAhProfile();
        p.name = "High Margin";
        p.minProfit = 5_000_000L;
        p.minPct = 25;
        p.maxPrice = 500_000_000L;
        p.whitelist = "hyperion, terminator";
        p.floorFilter = "above";
        p.colorRules = List.of(new ColorRule(10_000_000L, 50, "GOLD"));

        SkyAhProfile back = ProfileCode.decode(ProfileCode.encode(p));
        assertNotNull(back);
        assertEquals("High Margin", back.name);
        assertEquals(5_000_000L, back.minProfit);
        assertEquals(25, back.minPct);
        assertEquals(500_000_000L, back.maxPrice);
        assertEquals("hyperion, terminator", back.whitelist);
        assertEquals("above", back.floorFilter);
        assertEquals(1, back.colorRules.size());
        assertEquals(10_000_000L, back.colorRules.get(0).minProfit);
    }

    @Test
    void rejectsGarbage() {
        assertNull(ProfileCode.decode("nonsense"));
        assertNull(ProfileCode.decode(ProfileCode.PREFIX + "!!!"));
        SkyAhProfile p = new SkyAhProfile();
        p.name = "x";
        assertNull(ProfileCode.decode(ProfileCode.encode(p).replace(ProfileCode.PREFIX, "")));
        assertNull(ProfileCode.decode(ProfileCode.encode(new SkyAhProfile())));
    }

    @Test
    void clampsBadValues() {
        SkyAhProfile evil = new SkyAhProfile();
        evil.name = "evil";
        evil.minProfit = -5L;
        evil.minPct = -1;
        evil.floorFilter = "nonsense";
        evil.colorRules = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            evil.colorRules.add(new ColorRule(-1L, -1, null));
        }
        SkyAhProfile clean = ProfileCode.decode(ProfileCode.encode(evil));
        assertNotNull(clean);
        assertEquals(0L, clean.minProfit);
        assertEquals(0.0, clean.minPct);
        assertEquals("all", clean.floorFilter);
        assertEquals(8, clean.colorRules.size());
        assertEquals("GREEN", clean.colorRules.get(0).color);
    }
}
