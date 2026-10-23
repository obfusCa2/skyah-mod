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
    private static final Pattern LABEL = Pattern.compile("(?:Purse|Piggy):");
    /** Hypixel splits long sidebar lines into team prefix, entry name and team
     *  suffix, and the entry name can be an emoji, so the line reads
     *  "Purse: 250,834(emoji),112". Everything outside printable ASCII goes. */
    private static final Pattern NOT_ASCII = Pattern.compile("[^\\x20-\\x7E]");
    /** What is left of the entry name can also be plain text (a space, the
     *  letter of a color code), as in "Purse: 234,567 ,890" for 234M: after
     *  the label only digits and the decimal point count, up to "(+50)". */
    private static final Pattern NOT_NUMBER = Pattern.compile("[^0-9.]");
    /** A value older than this is treated as unknown (left SkyBlock, sidebar off). */
    private static final long STALE_MS = 30_000L;
    private static volatile long purse = -1L;
    private static volatile long readAt = 0L;
    private static int ticks = 0;
    /** The raw purse line of the last read, logged once per session. */
    private static String lastLine = null;
    private static boolean lineLogged = false;

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
                if (!lineLogged) {
                    lineLogged = true;
                    SkyAhLog.add("purse line: '" + escaped(lastLine) + "' -> " + FlipChat.fmt(v));
                }
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
            String line = PlayerTeam.formatNameForTeam(team, e.ownerName()).getString();
            long v = parse(line);
            if (v >= 0) {
                lastLine = line;
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
        clean = NOT_ASCII.matcher(clean == null ? line : clean).replaceAll("");
        Matcher m = LABEL.matcher(clean);
        if (!m.find()) {
            return -1L;
        }
        String rest = clean.substring(m.end());
        int paren = rest.indexOf('(');
        String number = NOT_NUMBER.matcher(paren >= 0 ? rest.substring(0, paren) : rest).replaceAll("");
        if (number.isEmpty()) {
            return -1L;
        }
        try {
            return (long) Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /** Non-ASCII characters as \\uXXXX, so a log shows what splits the line. */
    static String escaped(String line) {
        if (line == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c >= 0x20 && c <= 0x7E) {
                out.append(c);
            } else {
                out.append(String.format("\\u%04x", (int) c));
            }
        }
        return out.toString();
    }
}
