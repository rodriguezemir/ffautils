package site.zvolcan.fFAUtils.objects;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that a real MCTiers v2 profile payload maps onto {@link TierProfile}
 * and that gamemode lookups behave.
 */
class TierProfileTest {

    /** Captured verbatim from GET https://mctiers.com/api/v2/profile/{uuid}. */
    private static final String REAL_PAYLOAD = """
            {"uuid":"6553509f-66d3-4041-875f-164236e42e84","name":"uku3lig","region":"EU",\
            "points":3,"overall":66937,"discord_id":"319463560356823050",\
            "rankings":{"nethop":{"tier":4,"pos":1,"peak_tier":4,"peak_pos":1,\
            "attained":1739899018,"retired":false}},"badges":[],"tests":[]}""";

    @Test
    void gson_parsesTheRealProfilePayload() {
        TierProfile profile = new Gson().fromJson(REAL_PAYLOAD, TierProfile.class);

        assertEquals("6553509f-66d3-4041-875f-164236e42e84", profile.getUuid());
        assertEquals("uku3lig", profile.getName());
        assertEquals("EU", profile.getRegion());
        assertEquals(3, profile.getPoints());
        assertEquals(66937L, profile.getOverall());
        assertEquals("319463560356823050", profile.getDiscordId());
        assertEquals(1, profile.getRankings().size());
        assertEquals("LT4", profile.getRanking("nethop").display());
    }

    @Test
    void getRanking_returnsNullForAnUntestedGamemode() {
        TierProfile profile = new Gson().fromJson(REAL_PAYLOAD, TierProfile.class);
        assertNull(profile.getRanking("vanilla"));
    }

    @Test
    void getRanking_matchesSlugsCaseInsensitively() {
        TierProfile profile = new Gson().fromJson(REAL_PAYLOAD, TierProfile.class);
        assertNotNull(profile.getRanking("NetHoP"));
    }

    @Test
    void getRanking_toleratesNullAndEmptyInput() {
        TierProfile profile = new Gson().fromJson(REAL_PAYLOAD, TierProfile.class);
        assertNull(profile.getRanking(null));
        assertNull(profile.getRanking(""));
    }

    @Test
    void getRankings_isEmptyRatherThanNullForAProfileWithoutRankings() {
        TierProfile profile = new Gson().fromJson("{\"uuid\":\"x\",\"name\":\"y\"}", TierProfile.class);
        assertNotNull(profile.getRankings());
        assertTrue(profile.getRankings().isEmpty());
        assertNull(profile.getBestRanking(true));
        assertNull(profile.getBestGamemode(true));
    }

    @Test
    void getBestRanking_picksTheStrongestGamemode() {
        Map<String, TierRanking> rankings = new HashMap<>();
        rankings.put("sword", new TierRanking(4, TierRanking.LOW));
        rankings.put("vanilla", new TierRanking(2, TierRanking.HIGH));
        rankings.put("axe", new TierRanking(3, TierRanking.HIGH));
        TierProfile profile = new TierProfile("uuid", "name", rankings);

        assertEquals("HT2", profile.getBestRanking(true).display());
        assertEquals("vanilla", profile.getBestGamemode(true));
    }

    @Test
    void getBestRanking_usesPeakForRetiredGamemodes() {
        Map<String, TierRanking> rankings = new HashMap<>();
        rankings.put("sword", new TierRanking(4, TierRanking.HIGH));
        rankings.put("vanilla", new TierRanking(5, TierRanking.LOW, 1, TierRanking.HIGH, 0L, true));
        TierProfile profile = new TierProfile("uuid", "name", rankings);

        assertEquals("vanilla", profile.getBestGamemode(true));
        assertEquals("sword", profile.getBestGamemode(false));
    }

    @Test
    void getBestRanking_skipsOutOfRangeRankings() {
        Map<String, TierRanking> rankings = new HashMap<>();
        rankings.put("broken", new TierRanking(0, TierRanking.HIGH));
        rankings.put("sword", new TierRanking(4, TierRanking.HIGH));
        TierProfile profile = new TierProfile("uuid", "name", rankings);

        assertEquals("sword", profile.getBestGamemode(true));
    }

    @Test
    void getRankings_isImmutable() {
        TierProfile profile = new Gson().fromJson(REAL_PAYLOAD, TierProfile.class);
        assertThrows(UnsupportedOperationException.class,
                () -> profile.getRankings().put("sword", new TierRanking(1, TierRanking.HIGH)));
    }
}
