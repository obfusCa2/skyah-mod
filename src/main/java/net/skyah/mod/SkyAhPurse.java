package net.skyah.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Purse coins read from Hypixel's sidebar ("Purse: 12,345,678"), once per
 *  second on the client tick: about 15 short lines, no network. */
public final class SkyAhPurse {
    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy):\\s*([\\d,]+(?:\\.\\d+)?)");
    /** A value older than this is treated as unknown (left SkyBlock, sidebar off). */
    private static final long STALE_MS = 30_000L;
    private static volatile long purse = -1L;
    private static volatile long readAt = 0L;
    private static int ticks = 0;

    private SkyAhPurse() {
    }

    public static void tick(Minecraft mc) {
        if (++ticks % 20 != 0) {
            return;
        }
        try {
            long v = read(mc);
            if (v >= 0) {
                purse = v;
                readAt = System.currentTimeMillis();
            }
        } catch (Exception ignored) {
        }
    }

    /** Coins in the purse, or -1 when unknown. */
    public static long get() {
        return System.currentTimeMillis() - readAt < STALE_MS ? purse : -1L;
    }

    private static long read(Minecraft mc) {
        if (mc == null || mc.level == null) {
            return -1L;
        }
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            return -1L;
        }
        for (PlayerScoreEntry e : sb.listPlayerScores(obj)) {
            if (e.isHidden()) {
                continue;
            }
            PlayerTeam team = sb.getPlayersTeam(e.owner());
            long v = parse(PlayerTeam.formatNameForTeam(team, e.ownerName()).getString());
            if (v >= 0) {
                return v;
            }
        }
        return -1L;
    }

    /** "Purse: 1,234,567.8 (+50)" -> 1234567; -1 when the line is no purse line. */
    static long parse(String line) {
        if (line == null) {
            return -1L;
        }
        String clean = ChatFormatting.stripFormatting(line);
        Matcher m = PURSE.matcher(clean == null ? line : clean);
        if (!m.find()) {
            return -1L;
        }
        try {
            return (long) Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
