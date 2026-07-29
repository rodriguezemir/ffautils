package site.zvolcan.fFAUtils.objects;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player's ranking in a single MCTiers gamemode, as returned by
 * {@code GET https://mctiers.com/api/v2/profile/{uuid}}.
 *
 * <p>
 * {@code tier} and {@code pos} always go together: {@code tier} is 1-5 where
 * <b>lower is better</b>, and {@code pos} is {@code 0} for high or {@code 1}
 * for low. So {@code tier = 3, pos = 0} is HT3 and {@code tier = 3, pos = 1}
 * is LT3, with HT3 being the better of the two.
 */
public final class TierRanking {

    /** {@code pos} value meaning the player is at the high end of the tier. */
    public static final int HIGH = 0;
    /** {@code pos} value meaning the player is at the low end of the tier. */
    public static final int LOW = 1;

    /** Best tier the API can report. */
    public static final int BEST_TIER = 1;
    /** Worst tier the API can report. */
    public static final int WORST_TIER = 5;

    @Getter
    private int tier;
    @Getter
    private int pos;
    @SerializedName("peak_tier")
    private Integer peakTier;
    @SerializedName("peak_pos")
    private Integer peakPos;
    /** Unix timestamp (UTC seconds) at which the ranking was attained. */
    @Getter
    private long attained;
    @Getter
    private boolean retired;

    /** Required by Gson. */
    public TierRanking() {
    }

    public TierRanking(int tier, int pos) {
        this(tier, pos, null, null, 0L, false);
    }

    public TierRanking(int tier, int pos, @Nullable Integer peakTier, @Nullable Integer peakPos, long attained,
            boolean retired) {
        this.tier = tier;
        this.pos = pos;
        this.peakTier = peakTier;
        this.peakPos = peakPos;
        this.attained = attained;
        this.retired = retired;
    }

    /** The player's highest achieved tier, or null if the API did not report one. */
    @Nullable
    public Integer getPeakTier() {
        return peakTier;
    }

    /** Position associated with {@link #getPeakTier()}, null iff peak tier is null. */
    @Nullable
    public Integer getPeakPos() {
        return peakPos;
    }

    /**
     * Collapses a tier/position pair into a single comparable score where
     * <b>lower is better</b>: HT1 is 2 (the best) and LT5 is 11 (the worst).
     * Being contiguous, this makes "at least HT3" a plain {@code <=} comparison.
     */
    public static int score(int tier, int pos) {
        return tier * 2 + pos;
    }

    /** The score of this ranking's current tier. See {@link #score(int, int)}. */
    public int score() {
        return score(tier, pos);
    }

    /**
     * The tier this ranking should be judged on. For retired players the API
     * advises showing the peak tier, since the current one is frozen — so when
     * {@code usePeakWhenRetired} is set and a peak is present, the peak wins.
     */
    public int effectiveScore(boolean usePeakWhenRetired) {
        if (usePeakWhenRetired && retired && peakTier != null && peakPos != null) {
            return score(peakTier, peakPos);
        }
        return score();
    }

    /**
     * Whether this ranking is at least as good as the given requirement.
     *
     * @param requiredTier       required tier, 1-5, lower is stricter
     * @param requiredPos        required position, {@link #HIGH} or {@link #LOW}
     * @param usePeakWhenRetired judge retired players on their peak tier
     * @return true when the player may pass the gate
     */
    public boolean meets(int requiredTier, int requiredPos, boolean usePeakWhenRetired) {
        if (!isValid()) {
            return false;
        }
        return effectiveScore(usePeakWhenRetired) <= score(requiredTier, requiredPos);
    }

    /** Guards against out-of-range values from an unexpected API response. */
    public boolean isValid() {
        return tier >= BEST_TIER && tier <= WORST_TIER && (pos == HIGH || pos == LOW);
    }

    /** Renders a tier/position pair the way MCTiers does, e.g. {@code HT3}. */
    @NotNull
    public static String display(int tier, int pos) {
        return (pos == HIGH ? "HT" : "LT") + tier;
    }

    /** Renders this ranking's current tier, e.g. {@code LT4}. */
    @NotNull
    public String display() {
        return display(tier, pos);
    }

    /** Renders the tier this ranking is judged on, marking retired players. */
    @NotNull
    public String display(boolean usePeakWhenRetired) {
        if (usePeakWhenRetired && retired && peakTier != null && peakPos != null) {
            return display(peakTier, peakPos) + " (retired)";
        }
        return display();
    }

    @Override
    public String toString() {
        return "TierRanking{" + display() + ", retired=" + retired + "}";
    }
}
