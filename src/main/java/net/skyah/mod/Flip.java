package net.skyah.mod;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;

/** One deal from the SkyAH WebSocket feed. */
public class Flip {
    private static final Gson GSON = new Gson();

    @SerializedName("auction_id") public String auctionId;
    @SerializedName("ea_id") public String eaId;
    public String name;
    @SerializedName("name_formatted") public String nameFormatted;
    public long price;
    @SerializedName("target_sell_price") public Long targetSellPrice;
    @SerializedName("expected_profit") public Long expectedProfit;
    public Boolean bin;
    public Boolean replay;
    public Long fee;
    @SerializedName("profit_pct") public Double profitPct;
    @SerializedName("real_profit") public Long realProfit;
    @SerializedName("real_sell") public Long realSell;
    public Long floor;
    @SerializedName("floor_vs_target") public String floorVsTarget;
    @SerializedName("found_at") public String foundAt;
    /** Comparable sales per day for this build (optional). */
    @SerializedName("sales_per_day") public Double salesPerDay;
    /** Realistic seconds until it sells at the target (optional). */
    @SerializedName("sell_time_s") public Long sellTimeS;
    /** Comparable cheaper listings ahead of a listing at the target (optional). */
    @SerializedName("queue_ahead") public Integer queueAhead;
    /** Optional server timestamps, used for /skyah latency. */
    public Timing timing;
    /** A new listing still in its buy lock, sent before the public auction
     *  list shows it (optional, read leniently in parse()). */
    public transient boolean preApi;
    /** Server time (epoch ms) from which it can be bought, 0 = unknown. */
    public transient long buyableAtMs;
    /** Sent by the free feed, delayedS seconds after the keyed feed. */
    public transient boolean free;
    public transient long delayedS;
    /** Client-side only (transient: never read from or written to JSON). */
    public transient long shownAt;
    /** When the frame arrived at the mod (epoch ms). */
    public transient long receivedAt;
    public transient boolean opened;

    public static class Timing {
        @SerializedName("snapshot_ms") public Long snapshotMs;
        @SerializedName("found_ms") public Long foundMs;
        @SerializedName("sent_ms") public Long sentMs;
        @SerializedName("pipeline_ms") public Long pipelineMs;
    }

    public static Flip parse(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject o = root.getAsJsonObject();
            Flip f = GSON.fromJson(o, Flip.class);
            if (f == null || (f.auctionId == null && f.name == null)) {
                return null;
            }
            f.preApi = bool(o, "pre_api");
            f.buyableAtMs = epochMs(o, "buyable_at_ms");
            f.free = bool(o, "free");
            f.delayedS = Math.max(0L, Math.min(3_600L, number(o, "delayed_s")));
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /** When it can be bought on this computer's clock: the server time moved
     *  by the clock difference seen on this frame (sent_ms vs arrival, which
     *  includes the network delay, so it errs on the late side). 0 = unknown. */
    public long buyableAtLocal() {
        if (buyableAtMs <= 0L) {
            return 0L;
        }
        long offset = 0L;
        if (timing != null && timing.sentMs != null && timing.sentMs > 0L && receivedAt > 0L) {
            offset = receivedAt - timing.sentMs;
            if (Math.abs(offset) > 86_400_000L) {
                offset = 0L;
            }
        }
        return buyableAtMs + offset;
    }

    /** Still in its buy lock at local time now. */
    public boolean locked(long now) {
        long at = buyableAtLocal();
        return at > 0L && now < at;
    }

    /** Optional fields are read one by one: a value of an unexpected type is
     *  ignored instead of dropping the whole deal. */
    private static boolean bool(JsonObject o, String name) {
        try {
            JsonElement e = o.get(name);
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
        } catch (Exception ex) {
            return false;
        }
    }

    private static long number(JsonObject o, String name) {
        try {
            JsonElement e = o.get(name);
            return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0L;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static long epochMs(JsonObject o, String name) {
        long v = number(o, name);
        return v >= 1_600_000_000_000L && v <= 4_000_000_000_000L ? v : 0L;
    }
}
