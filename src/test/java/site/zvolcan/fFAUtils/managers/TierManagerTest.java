package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.managers.TierManager.Status;
import site.zvolcan.fFAUtils.managers.TierManager.TierAccessResult;
import site.zvolcan.fFAUtils.managers.TierManager.TierRequirement;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Tests for the tier gate: config parsing and the access decision itself.
 *
 * <p>
 * {@link TierManager#decide} is deliberately free of Bukkit and HTTP, so the
 * whole allow/deny matrix can be exercised without a server or network.
 */
class TierManagerTest {

    private static final TierRequirement HT3_VANILLA = new TierRequirement("tryhard", "vanilla", 3, TierRanking.HIGH);

    private YamlConfiguration config;
    private JavaPlugin plugin;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        plugin = mock(JavaPlugin.class);
        lenient().when(plugin.getConfig()).thenReturn(config);
        lenient().when(plugin.getLogger()).thenReturn(Logger.getLogger("TierManagerTest"));
    }

    private TierProfile profileWith(String gamemode, TierRanking ranking) {
        Map<String, TierRanking> rankings = new HashMap<>();
        rankings.put(gamemode, ranking);
        return new TierProfile(UUID.randomUUID().toString(), "player", rankings);
    }

    // ------------------------------------------------------------------
    // Access decisions
    // ------------------------------------------------------------------

    @Test
    void decide_allowsTheExactRequiredRank() {
        // The case from the task: tier 3, position 0 gets into the spawn.
        TierAccessResult result = TierManager.decide(
                profileWith("vanilla", new TierRanking(3, TierRanking.HIGH)), false, HT3_VANILLA, true, true);

        assertEquals(Status.ALLOWED, result.getStatus());
        assertTrue(result.isAllowed());
        assertEquals("HT3", result.getRanking().display());
    }

    @Test
    void decide_allowsBetterRanks() {
        for (TierRanking better : new TierRanking[] {
                new TierRanking(1, TierRanking.HIGH),
                new TierRanking(2, TierRanking.HIGH),
                new TierRanking(2, TierRanking.LOW) }) {
            TierAccessResult result = TierManager.decide(
                    profileWith("vanilla", better), false, HT3_VANILLA, true, true);
            assertTrue(result.isAllowed(), better.display() + " should get in");
        }
    }

    @Test
    void decide_deniesWorseRanks() {
        for (TierRanking worse : new TierRanking[] {
                new TierRanking(3, TierRanking.LOW),
                new TierRanking(4, TierRanking.HIGH),
                new TierRanking(5, TierRanking.LOW) }) {
            TierAccessResult result = TierManager.decide(
                    profileWith("vanilla", worse), false, HT3_VANILLA, true, true);
            assertEquals(Status.DENIED_TIER, result.getStatus(), worse.display() + " should be blocked");
            assertFalse(result.isAllowed());
        }
    }

    @Test
    void decide_deniesPlayersWithoutAnMcTiersProfile() {
        TierAccessResult result = TierManager.decide(null, false, HT3_VANILLA, true, true);

        assertEquals(Status.DENIED_NO_PROFILE, result.getStatus());
        assertFalse(result.isAllowed());
    }

    @Test
    void decide_deniesPlayersUnrankedInTheRequiredGamemode() {
        TierAccessResult result = TierManager.decide(
                profileWith("sword", new TierRanking(1, TierRanking.HIGH)), false, HT3_VANILLA, true, true);

        assertEquals(Status.DENIED_NO_RANKING, result.getStatus());
        assertEquals("vanilla", result.getGamemode());
    }

    @Test
    void decide_failsOpenOrClosedOnApiErrorPerConfig() {
        assertEquals(Status.ALLOWED_ON_ERROR,
                TierManager.decide(null, true, HT3_VANILLA, true, true).getStatus());
        assertTrue(TierManager.decide(null, true, HT3_VANILLA, true, true).isAllowed());

        assertEquals(Status.DENIED_ERROR,
                TierManager.decide(null, true, HT3_VANILLA, true, false).getStatus());
        assertFalse(TierManager.decide(null, true, HT3_VANILLA, true, false).isAllowed());
    }

    @Test
    void decide_judgesRetiredPlayersOnTheirPeak() {
        TierProfile retired = profileWith("vanilla",
                new TierRanking(5, TierRanking.LOW, 2, TierRanking.HIGH, 0L, true));

        assertEquals(Status.ALLOWED, TierManager.decide(retired, false, HT3_VANILLA, true, true).getStatus());
        assertEquals(Status.DENIED_TIER, TierManager.decide(retired, false, HT3_VANILLA, false, true).getStatus());
    }

    @Test
    void decide_withAnyGamemodeUsesTheBestRanking() {
        TierRequirement anyGamemode = new TierRequirement("tryhard", "best", 3, TierRanking.HIGH);
        Map<String, TierRanking> rankings = new HashMap<>();
        rankings.put("sword", new TierRanking(5, TierRanking.LOW));
        rankings.put("axe", new TierRanking(2, TierRanking.HIGH));
        TierProfile profile = new TierProfile("uuid", "player", rankings);

        TierAccessResult result = TierManager.decide(profile, false, anyGamemode, true, true);

        assertEquals(Status.ALLOWED, result.getStatus());
        assertEquals("axe", result.getGamemode());
    }

    @Test
    void decide_deniesMalformedRankings() {
        TierAccessResult result = TierManager.decide(
                profileWith("vanilla", new TierRanking(0, TierRanking.HIGH)), false, HT3_VANILLA, true, true);

        assertEquals(Status.DENIED_NO_RANKING, result.getStatus());
    }

    @Test
    void isAnyGamemode_recognisesTheWildcardValues() {
        assertTrue(TierManager.isAnyGamemode("best"));
        assertTrue(TierManager.isAnyGamemode("ANY"));
        assertTrue(TierManager.isAnyGamemode("overall"));
        assertTrue(TierManager.isAnyGamemode("*"));
        assertTrue(TierManager.isAnyGamemode(""));
        assertTrue(TierManager.isAnyGamemode(null));
        assertFalse(TierManager.isAnyGamemode("vanilla"));
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    @Test
    void loadSettings_appliesDefaultsWhenTheSectionIsMissing() {
        TierManager manager = new TierManager(plugin);

        assertFalse(manager.isEnabled(), "the gate must be off until turned on");
        assertEquals(TierManager.DEFAULT_API_URL, manager.getApiUrl());
        assertEquals("vanilla", manager.getDefaultGamemode());
        assertTrue(manager.isUsePeakWhenRetired());
        assertTrue(manager.isAllowOnError());
        assertTrue(manager.getRestrictedSpawns().isEmpty());
    }

    @Test
    void loadSettings_readsRestrictedSpawnsWithSectionDefaults() {
        config.set("tiers.enabled", true);
        config.set("tiers.gamemode", "vanilla");
        config.set("tiers.tier", 3);
        config.set("tiers.pos", 0);
        config.set("tiers.restricted-spawns.tryhard.tier", 3);
        config.set("tiers.restricted-spawns.tryhard.pos", 0);
        config.set("tiers.restricted-spawns.casual", new java.util.HashMap<String, Object>());

        TierManager manager = new TierManager(plugin);

        assertTrue(manager.isEnabled());
        assertEquals(2, manager.getRestrictedSpawns().size());

        TierRequirement tryhard = manager.getRequirement("tryhard");
        assertNotNull(tryhard);
        assertEquals(3, tryhard.getTier());
        assertEquals(TierRanking.HIGH, tryhard.getPos());
        assertEquals("vanilla", tryhard.getGamemode());
        assertEquals("HT3", tryhard.display());

        // A spawn with no overrides inherits the section defaults.
        TierRequirement casual = manager.getRequirement("casual");
        assertNotNull(casual);
        assertEquals(3, casual.getTier());
        assertEquals("vanilla", casual.getGamemode());
    }

    @Test
    void loadSettings_allowsPerSpawnOverrides() {
        config.set("tiers.enabled", true);
        config.set("tiers.gamemode", "vanilla");
        config.set("tiers.tier", 4);
        config.set("tiers.restricted-spawns.sweats.tier", 1);
        config.set("tiers.restricted-spawns.sweats.pos", 1);
        config.set("tiers.restricted-spawns.sweats.gamemode", "sword");

        TierRequirement sweats = new TierManager(plugin).getRequirement("sweats");

        assertEquals(1, sweats.getTier());
        assertEquals(TierRanking.LOW, sweats.getPos());
        assertEquals("sword", sweats.getGamemode());
        assertEquals("LT1", sweats.display());
    }

    @Test
    void loadSettings_clampsOutOfRangeRequirements() {
        config.set("tiers.restricted-spawns.a.tier", 99);
        config.set("tiers.restricted-spawns.a.pos", 42);
        config.set("tiers.restricted-spawns.b.tier", -5);

        TierManager manager = new TierManager(plugin);

        assertEquals(TierRanking.WORST_TIER, manager.getRequirement("a").getTier());
        assertEquals(TierRanking.HIGH, manager.getRequirement("a").getPos());
        assertEquals(TierRanking.BEST_TIER, manager.getRequirement("b").getTier());
    }

    @Test
    void getRequirement_matchesSpawnNamesCaseInsensitively() {
        config.set("tiers.restricted-spawns.TryHard.tier", 3);

        TierManager manager = new TierManager(plugin);

        assertNotNull(manager.getRequirement("tryhard"));
        assertNotNull(manager.getRequirement("TRYHARD"));
        assertEquals("TryHard", manager.getRequirement("tryhard").getSpawnName(),
                "the configured casing is preserved for display");
    }

    @Test
    void getRequirement_toleratesNullAndUnknownSpawns() {
        TierManager manager = new TierManager(plugin);
        assertNull(manager.getRequirement(null));
        assertNull(manager.getRequirement(""));
        assertNull(manager.getRequirement("nope"));
    }

    @Test
    void isRestricted_isFalseWhileTheGateIsDisabled() {
        config.set("tiers.enabled", false);
        config.set("tiers.restricted-spawns.tryhard.tier", 3);

        TierManager manager = new TierManager(plugin);

        assertFalse(manager.isRestricted("tryhard"), "a disabled gate restricts nothing");
        assertNotNull(manager.getRequirement("tryhard"), "but the requirement is still configured");
    }

    @Test
    void isRestricted_isTrueOnlyForConfiguredSpawns() {
        config.set("tiers.enabled", true);
        config.set("tiers.restricted-spawns.tryhard.tier", 3);

        TierManager manager = new TierManager(plugin);

        assertTrue(manager.isRestricted("tryhard"));
        assertFalse(manager.isRestricted("lobby"));
    }

    @Test
    void setEnabled_persistsTheToggleToConfig() {
        TierManager manager = new TierManager(plugin);
        assertFalse(manager.isEnabled());

        assertTrue(manager.toggle());
        assertTrue(manager.isEnabled());
        assertTrue(config.getBoolean("tiers.enabled"));

        assertFalse(manager.toggle());
        assertFalse(config.getBoolean("tiers.enabled"));
    }

    @Test
    void loadSettings_stripsTrailingSlashesFromTheApiUrl() {
        config.set("tiers.api-url", "https://mctiers.com/api/v2///");
        assertEquals("https://mctiers.com/api/v2", new TierManager(plugin).getApiUrl());
    }

    @Test
    void loadSettings_fallsBackToTheDefaultApiUrlWhenBlank() {
        config.set("tiers.api-url", "   ");
        assertEquals(TierManager.DEFAULT_API_URL, new TierManager(plugin).getApiUrl());
    }

    // ------------------------------------------------------------------
    // Caching
    // ------------------------------------------------------------------

    @Test
    void cache_startsEmptyAndReportsMisses() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();

        assertFalse(manager.isCached(uuid));
        assertNull(manager.getCachedProfile(uuid));
        assertEquals(0, manager.getCacheSize());
    }

    @Test
    void clearCache_reportsHowManyEntriesItDropped() {
        TierManager manager = new TierManager(plugin);
        assertEquals(0, manager.clearCache());
    }

    @Test
    void loadSettings_convertsCacheMinutesToMillis() {
        config.set("tiers.cache-minutes", 15L);
        assertEquals(15L * 60_000L, new TierManager(plugin).getCacheMillis());
    }

    @Test
    void loadSettings_treatsANegativeCacheDurationAsDisabled() {
        config.set("tiers.cache-minutes", -5L);
        assertEquals(0L, new TierManager(plugin).getCacheMillis());
    }
}
