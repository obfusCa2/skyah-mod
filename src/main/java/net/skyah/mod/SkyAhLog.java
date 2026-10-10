package net.skyah.mod;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Last 1000 log lines for /skyah logs and Copy logs; also written to the game log. */
public final class SkyAhLog {
    private static final int MAX = 1000;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Deque<String> LINES = new ArrayDeque<>();

    private SkyAhLog() {
    }

    public static synchronized void add(String message) {
        LINES.addLast(LocalTime.now().format(FMT) + "  " + message);
        while (LINES.size() > MAX) {
            LINES.removeFirst();
        }
        SkyAhMod.LOGGER.info(message);
    }

    public static synchronized List<String> snapshot() {
        return new ArrayList<>(LINES);
    }

    public static synchronized void clear() {
        LINES.clear();
    }
}
