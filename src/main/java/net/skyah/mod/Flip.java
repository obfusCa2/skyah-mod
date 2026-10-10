package net.skyah.mod;

import com.google.gson.Gson;
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
            Flip f = GSON.fromJson(json, Flip.class);
            return (f != null && (f.auctionId != null || f.name != null)) ? f : null;
        } catch (Exception e) {
            return null;
        }
    }
}
