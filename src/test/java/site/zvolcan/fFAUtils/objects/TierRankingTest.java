package site.zvolcan.fFAUtils.objects;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the MCTiers tier/position ordering.
 *
 * <p>
 * Per the v2 API, {@code tier} is 1-5 where lower is better and {@code pos} is
 * 0 for high / 1 for low, so HT1 is the best rank and LT5 the worst.
 */
class TierRankingTest {

    @Test
    void score_ordersHighTierAboveLowTierOfSameNumber() {
        assertTrue(TierRanking.score(3, TierRanking.HIGH) < TierRanking.score(3, TierRanking.LOW));
    }

    @Test
    void score_ordersBetterTierAboveWorseTier() {
        assertTrue(TierRanking.score(2, TierRanking.LOW) < TierRanking.score(3, TierRanking.HIGH));
    }

    @Test
    void score_isStrictlyOrderedAcrossEveryRank() {
        int previous = Integer.MIN_VALUE;
        for (int tier = TierRanking.BEST_TIER; tier <= TierRanking.WORST_TIER; tier++) {
            for (int pos : new int[] { TierRanking.HIGH, TierRanking.LOW }) {
                int score = TierRanking.score(tier, pos);
                assertTrue(score > previous, "ranks must be strictly ordered at " + TierRanking.display(tier, pos));
                previous = score;
            }
        }
    }

    @Test
    void meets_allowsExactRequirement() {
        // The requirement from the task: tier 3, position 0 (HT3).
        assertTrue(new TierRanking(3, TierRanking.HIGH).meets(3, TierRanking.HIGH, true));
    }

    @Test
    void meets_allowsBetterThanRequirement() {
        assertTrue(new TierRanking(1, TierRanking.HIGH).meets(3, TierRanking.HIGH, true));
        assertTrue(new TierRanking(2, TierRanking.LOW).meets(3, TierRanking.HIGH, true));
    }

    @Test
    void meets_rejectsLowTierWhenHighTierRequired() {
        // LT3 is worse than HT3, so an HT3 gate must turn it away.
        assertFalse(new TierRanking(3, TierRanking.LOW).meets(3, TierRanking.HIGH, true));
    }

    @Test
    void meets_rejectsWorseTier() {
        assertFalse(new TierRanking(4, TierRanking.HIGH).meets(3, TierRanking.HIGH, true));
        assertFalse(new TierRanking(5, TierRanking.LOW).meets(3, TierRanking.HIGH, true));
    }

    @Test
    void meets_allowsLowTierWhenLowTierRequired() {
        assertTrue(new TierRanking(3, TierRanking.LOW).meets(3, TierRanking.LOW, true));
    }

    @Test
    void meets_usesPeakForRetiredPlayers() {
        TierRanking retired = new TierRanking(5, TierRanking.LOW, 2, TierRanking.HIGH, 0L, true);
        assertTrue(retired.meets(3, TierRanking.HIGH, true), "retired players are judged on their peak");
        assertFalse(retired.meets(3, TierRanking.HIGH, false), "and on their current tier when configured to");
    }

    @Test
    void meets_ignoresPeakForActivePlayers() {
        TierRanking active = new TierRanking(4, TierRanking.LOW, 1, TierRanking.HIGH, 0L, false);
        assertFalse(active.meets(3, TierRanking.HIGH, true));
    }

    @Test
    void meets_ignoresPeakWhenApiReportsNone() {
        TierRanking retired = new TierRanking(4, TierRanking.LOW, null, null, 0L, true);
        assertFalse(retired.meets(3, TierRanking.HIGH, true));
    }

    @Test
    void meets_rejectsOutOfRangeValues() {
        assertFalse(new TierRanking(0, TierRanking.HIGH).meets(3, TierRanking.HIGH, true));
        assertFalse(new TierRanking(6, TierRanking.HIGH).meets(3, TierRanking.HIGH, true));
        assertFalse(new TierRanking(3, 7).meets(3, TierRanking.HIGH, true));
    }

    @Test
    void isValid_acceptsTheFullDocumentedRange() {
        for (int tier = 1; tier <= 5; tier++) {
            assertTrue(new TierRanking(tier, TierRanking.HIGH).isValid());
            assertTrue(new TierRanking(tier, TierRanking.LOW).isValid());
        }
    }

    @Test
    void display_matchesMcTiersNotation() {
        assertEquals("HT3", TierRanking.display(3, TierRanking.HIGH));
        assertEquals("LT3", TierRanking.display(3, TierRanking.LOW));
        assertEquals("HT1", new TierRanking(1, TierRanking.HIGH).display());
    }

    @Test
    void display_marksRetiredPlayersWithTheirPeak() {
        TierRanking retired = new TierRanking(5, TierRanking.LOW, 2, TierRanking.HIGH, 0L, true);
        assertEquals("HT2 (retired)", retired.display(true));
        assertEquals("LT5", retired.display(false));
    }

    @Test
    void parse_readsTierLabels() {
        // EliteStorm reports tiers as labels rather than a tier/position pair.
        assertEquals(3, TierRanking.parse("HT3").getTier());
        assertEquals(TierRanking.HIGH, TierRanking.parse("HT3").getPos());
        assertEquals(4, TierRanking.parse("LT4").getTier());
        assertEquals(TierRanking.LOW, TierRanking.parse("LT4").getPos());
    }

    @Test
    void parse_roundTripsEveryValidLabel() {
        for (int tier = TierRanking.BEST_TIER; tier <= TierRanking.WORST_TIER; tier++) {
            for (int pos : new int[] { TierRanking.HIGH, TierRanking.LOW }) {
                String label = TierRanking.display(tier, pos);
                TierRanking parsed = TierRanking.parse(label);
                assertNotNull(parsed, label + " should parse");
                assertEquals(label, parsed.display());
                assertTrue(parsed.isValid());
            }
        }
    }

    @Test
    void parse_isCaseAndWhitespaceInsensitive() {
        assertEquals("HT3", TierRanking.parse(" ht3 ").display());
        assertEquals("LT5", TierRanking.parse("lT5").display());
    }

    @Test
    void parse_carriesTheRetiredFlag() {
        TierRanking retired = TierRanking.parse("LT5", true);

        assertTrue(retired.isRetired());
        assertFalse(TierRanking.parse("LT5", false).isRetired());
    }

    @Test
    void parse_rejectsAnythingThatIsNotATierLabel() {
        for (String bad : new String[] { null, "", "T3", "HT", "HT0", "HT6", "XT3", "HT33", "high tier 3" }) {
            assertNull(TierRanking.parse(bad), "should not parse: " + bad);
        }
    }

    @Test
    void gson_parsesTheApiRankingShape() {
        // Verbatim from GET https://mctiers.com/api/v2/profile/{uuid}
        String json = "{\"tier\":4,\"pos\":1,\"peak_tier\":3,\"peak_pos\":0,"
                + "\"attained\":1739899018,\"retired\":false}";

        TierRanking ranking = new Gson().fromJson(json, TierRanking.class);

        assertEquals(4, ranking.getTier());
        assertEquals(TierRanking.LOW, ranking.getPos());
        assertEquals(3, ranking.getPeakTier());
        assertEquals(TierRanking.HIGH, ranking.getPeakPos());
        assertEquals(1739899018L, ranking.getAttained());
        assertFalse(ranking.isRetired());
        assertEquals("LT4", ranking.display());
    }

    @Test
    void gson_parsesNullPeakFields() {
        TierRanking ranking = new Gson().fromJson(
                "{\"tier\":2,\"pos\":0,\"peak_tier\":null,\"peak_pos\":null,\"attained\":0,\"retired\":false}",
                TierRanking.class);

        assertNull(ranking.getPeakTier());
        assertNull(ranking.getPeakPos());
        assertTrue(ranking.isValid());
    }

    @Test
    void gson_parsesARankingsMapKeyedByGamemodeSlug() {
        Map<String, TierRanking> rankings = new Gson().fromJson(
                "{\"vanilla\":{\"tier\":1,\"pos\":0},\"sword\":{\"tier\":4,\"pos\":1}}",
                new com.google.gson.reflect.TypeToken<Map<String, TierRanking>>() {
                }.getType());

        assertEquals(2, rankings.size());
        assertEquals("HT1", rankings.get("vanilla").display());
        assertEquals("LT4", rankings.get("sword").display());
    }
}
