package site.zvolcan.fFAUtils.objects;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A player's MCTiers profile, as returned by
 * {@code GET https://mctiers.com/api/v2/profile/{uuid}}.
 *
 * <p>
 * {@code rankings} is keyed by gamemode slug ({@code vanilla}, {@code sword},
 * {@code nethop}, …) and only contains the gamemodes the player has been
 * tested in, so a lookup for an untested gamemode returns null.
 */
public final class TierProfile {

    @Getter
    private String uuid;
    @Getter
    private String name;
    @Getter
    private String region;
    @Getter
    private int points;
    @Getter
    private long overall;
    @SerializedName("discord_id")
    private String discordId;
    private Map<String, TierRanking> rankings;

    /** Required by Gson. */
    public TierProfile() {
    }

    public TierProfile(String uuid, String name, @Nullable Map<String, TierRanking> rankings) {
        this.uuid = uuid;
        this.name = name;
        this.rankings = rankings;
    }

    /** The player's linked Discord ID, or null when not linked. */
    @Nullable
    public String getDiscordId() {
        return discordId;
    }

    /** All of the player's rankings by gamemode slug. Never null. */
    @NotNull
    public Map<String, TierRanking> getRankings() {
        return rankings == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(rankings));
    }

    /**
     * The player's ranking in a gamemode, or null when they have not been
     * tested in it. Gamemode slugs are matched case-insensitively.
     */
    @Nullable
    public TierRanking getRanking(@Nullable String gamemode) {
        if (rankings == null || gamemode == null || gamemode.isEmpty()) {
            return null;
        }
        TierRanking direct = rankings.get(gamemode);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, TierRanking> entry : rankings.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(gamemode)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * The player's single best ranking across every gamemode, or null when they
     * have no valid ranking at all. Used when the gate is configured to accept
     * any gamemode rather than a specific one.
     */
    @Nullable
    public TierRanking getBestRanking(boolean usePeakWhenRetired) {
        if (rankings == null) {
            return null;
        }
        TierRanking best = null;
        for (TierRanking ranking : rankings.values()) {
            if (ranking == null || !ranking.isValid()) {
                continue;
            }
            if (best == null || ranking.effectiveScore(usePeakWhenRetired) < best.effectiveScore(usePeakWhenRetired)) {
                best = ranking;
            }
        }
        return best;
    }

    /** The gamemode slug holding {@link #getBestRanking(boolean)}, or null. */
    @Nullable
    public String getBestGamemode(boolean usePeakWhenRetired) {
        if (rankings == null) {
            return null;
        }
        String bestSlug = null;
        TierRanking best = null;
        for (Map.Entry<String, TierRanking> entry : rankings.entrySet()) {
            TierRanking ranking = entry.getValue();
            if (ranking == null || !ranking.isValid()) {
                continue;
            }
            if (best == null || ranking.effectiveScore(usePeakWhenRetired) < best.effectiveScore(usePeakWhenRetired)) {
                best = ranking;
                bestSlug = entry.getKey();
            }
        }
        return bestSlug;
    }

    @Override
    public String toString() {
        return "TierProfile{name=" + name + ", uuid=" + uuid + ", rankings=" + getRankings().size() + "}";
    }
}
