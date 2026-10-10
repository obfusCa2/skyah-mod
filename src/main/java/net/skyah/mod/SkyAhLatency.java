package net.skyah.mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Per-flip latency trace (Hypixel snapshot -> SkyAH server -> this client)
 *  for the log and /skyah latency. pipeline_ms is computed on the server
 *  clock and the network leg is RTT/2 from WebSocket pings, so the player's
 *  PC clock does not matter. */
public final class SkyAhLatency {
    private static final int MAX = 100;
    // {total, server pipeline, network, tick}
    private static final Deque<long[]> RECENT = new ArrayDeque<>();

    private SkyAhLatency() {
    }

    public static void log(Flip flip, long receivedAt, long shownAt, long rttMs) {
        long tick = Math.max(0L, shownAt - receivedAt);
        Flip.Timing t = flip.timing;
        if (t == null || t.pipelineMs == null) {
            SkyAhLog.add("lat " + name(flip) + ": no timing (poll path), shown +" + tick + "ms");
            return;
        }
        long net = rttMs >= 0 ? rttMs / 2 : -1L;
        long total = t.pipelineMs + Math.max(0L, net) + tick;
        String foundToSent = (t.foundMs != null && t.sentMs != null) ? (t.sentMs - t.foundMs) + "ms" : "?";
        SkyAhLog.add("lat " + name(flip) + ": snapshot->server " + t.pipelineMs + "ms"
                + " (found->sent " + foundToSent + "), net ~" + (net >= 0 ? net + "ms" : "?")
                + ", shown +" + tick + "ms = ~" + total + "ms after snapshot");
        synchronized (RECENT) {
            RECENT.addLast(new long[]{total, t.pipelineMs, Math.max(0L, net), tick});
            while (RECENT.size() > MAX) {
                RECENT.removeFirst();
            }
        }
    }

    public static String summary() {
        List<long[]> rows;
        synchronized (RECENT) {
            rows = new ArrayList<>(RECENT);
        }
        if (rows.isEmpty()) {
            return "no live flips with timing yet";
        }
        return "last " + rows.size() + " flips: total p50 " + pct(rows, 0, 50) + "ms / p90 " + pct(rows, 0, 90)
                + "ms | server p50 " + pct(rows, 1, 50) + "ms | net p50 " + pct(rows, 2, 50)
                + "ms | tick p50 " + pct(rows, 3, 50) + "ms";
    }

    private static long pct(List<long[]> rows, int col, int p) {
        List<Long> v = new ArrayList<>(rows.size());
        for (long[] r : rows) {
            v.add(r[col]);
        }
        Collections.sort(v);
        int i = Math.min(v.size() - 1, (int) ((long) v.size() * p / 100));
        return v.get(i);
    }

    private static String name(Flip f) {
        if (f.name != null) {
            return f.name;
        }
        return f.auctionId != null ? f.auctionId : "?";
    }
}
