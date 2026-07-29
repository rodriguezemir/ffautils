package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.managers.TierManager.Status;
import site.zvolcan.fFAUtils.managers.TierManager.TierAccessResult;
import site.zvolcan.fFAUtils.managers.TierManager.TierRequirement;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;
import site.zvolcan.fFAUtils.providers.EliteStormProvider;
import site.zvolcan.fFAUtils.providers.McTiersProvider;
import site.zvolcan.fFAUtils.providers.PvpTiersProvider;
import site.zvolcan.fFAUtils.providers.TierProvider;

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

        // Providers build their User-Agent from the plugin version.
        PluginDescriptionFile description = new PluginDescriptionFile("FFAUtils", "1.0.0-TEST", "Main");
        lenient().when(plugin.getDescription()).thenReturn(description);
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
        assertEquals(McTiersProvider.ID, manager.getDefaultProvider());
        assertEquals("vanilla", manager.getDefaultGamemode());
        assertTrue(manager.isUsePeakWhenRetired());
        assertTrue(manager.isAllowOnError());
        assertTrue(manager.getRestrictedSpawns().isEmpty());
    }

    @Test
    void loadSettings_registersEveryProviderWithItsDefaultUrl() {
        TierManager manager = new TierManager(plugin);

        assertEquals(3, manager.getProviders().size());
        assertEquals(McTiersProvider.DEFAULT_API_URL,
                manager.getProvider(McTiersProvider.ID).getSettings().getApiUrl());
        assertEquals(PvpTiersProvider.DEFAULT_API_URL,
                manager.getProvider(PvpTiersProvider.ID).getSettings().getApiUrl());
        assertEquals(EliteStormProvider.DEFAULT_API_URL,
                manager.getProvider(EliteStormProvider.ID).getSettings().getApiUrl());
    }

    @Test
    void loadSettings_configuresEliteStormNameLookupAndGuild() {
        EliteStormProvider provider = (EliteStormProvider) new TierManager(plugin)
                .getProvider(EliteStormProvider.ID);

        // Names by default, so the gate works on offline-mode servers.
        assertEquals(TierProvider.LookupMode.NAME, provider.getPreferredLookup());
        assertEquals(EliteStormProvider.DEFAULT_GUILD_ID, provider.getGuildId());
    }

    @Test
    void loadSettings_honoursEliteStormOverrides() {
        config.set("tiers.providers.elitestorm.lookup-by", "uuid");
        config.set("tiers.providers.elitestorm.guild-id", "123456789");

        EliteStormProvider provider = (EliteStormProvider) new TierManager(plugin)
                .getProvider(EliteStormProvider.ID);

        assertEquals(TierProvider.LookupMode.UUID, provider.getPreferredLookup());
        assertEquals("123456789", provider.getGuildId());
    }

    @Test
    void loadSettings_selectsTheConfiguredProvider() {
        config.set("tiers.provider", "pvptiers");
        assertEquals(PvpTiersProvider.ID, new TierManager(plugin).getDefaultProvider());
    }

    @Test
    void loadSettings_fallsBackToMcTiersForAnUnknownProvider() {
        config.set("tiers.provider", "not-a-site");
        assertEquals(McTiersProvider.ID, new TierManager(plugin).getDefaultProvider());
    }

    @Test
    void loadSettings_acceptsTheAnyProvider() {
        config.set("tiers.provider", "any");
        TierManager manager = new TierManager(plugin);

        assertEquals(TierManager.PROVIDER_ANY, manager.getDefaultProvider());
        assertEquals(3, manager.resolveProviders(null).size(), "\"any\" spans every provider");
    }

    @Test
    void resolveProviders_returnsJustTheRequestedProvider() {
        TierManager manager = new TierManager(plugin);
        TierRequirement requirement = new TierRequirement("s", "sword", 3, TierRanking.HIGH, PvpTiersProvider.ID);

        assertEquals(1, manager.resolveProviders(requirement).size());
        assertEquals(PvpTiersProvider.ID, manager.resolveProviders(requirement).get(0).getId());
    }

    @Test
    void loadSettings_allowsAPerSpawnProviderOverride() {
        config.set("tiers.provider", "mctiers");
        config.set("tiers.restricted-spawns.crystalspawn.provider", "pvptiers");
        config.set("tiers.restricted-spawns.crystalspawn.gamemode", "crystal");
        config.set("tiers.restricted-spawns.vanillaspawn.tier", 2);

        TierManager manager = new TierManager(plugin);

        assertEquals(PvpTiersProvider.ID, manager.getRequirement("crystalspawn").getProvider());
        assertEquals("crystal", manager.getRequirement("crystalspawn").getGamemode());
        // No override, so the spawn inherits the section default.
        assertEquals(McTiersProvider.ID, manager.getRequirement("vanillaspawn").getProvider());
    }

    @Test
    void loadSettings_fallsBackToTheDefaultForAnUnknownPerSpawnProvider() {
        config.set("tiers.provider", "pvptiers");
        config.set("tiers.restricted-spawns.a.provider", "nope");

        assertEquals(PvpTiersProvider.ID, new TierManager(plugin).getRequirement("a").getProvider());
    }

    @Test
    void loadSettings_readsPerProviderApiUrlOverrides() {
        config.set("tiers.providers.mctiers.api-url", "https://mirror.example/api/v2//");
        config.set("tiers.providers.pvptiers.api-url", "https://mirror.example/pvp/");

        TierManager manager = new TierManager(plugin);

        assertEquals("https://mirror.example/api/v2",
                manager.getProvider(McTiersProvider.ID).getSettings().getApiUrl());
        assertEquals("https://mirror.example/pvp",
                manager.getProvider(PvpTiersProvider.ID).getSettings().getApiUrl());
    }

    @Test
    void loadSettings_fallsBackToTheDefaultApiUrlWhenBlank() {
        config.set("tiers.providers.mctiers.api-url", "   ");
        assertEquals(McTiersProvider.DEFAULT_API_URL,
                new TierManager(plugin).getProvider(McTiersProvider.ID).getSettings().getApiUrl());
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

    // ------------------------------------------------------------------
    // "any" provider: fold several answers into one decision
    // ------------------------------------------------------------------

    /** A requirement of HT3 in {@code gamemode}, spanning every provider. */
    private TierRequirement anyProviderRequirement(String gamemode) {
        return new TierRequirement("tryhard", gamemode, 3, TierRanking.HIGH, TierManager.PROVIDER_ANY);
    }

    /**
     * Evaluates an "any provider" requirement the way
     * {@link TierManager#checkAccess} does: the provider list comes from the
     * requirement, not from the manager's default.
     */
    private TierAccessResult decideAny(TierManager manager, UUID uuid, String gamemode) {
        TierRequirement requirement = anyProviderRequirement(gamemode);
        return manager.decideAcross(manager.resolveProviders(requirement), uuid, requirement);
    }

    @Test
    void decideAcross_letsThePlayerInIfAnyProviderAllows() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        // Ranked on PvPTiers only — as happens for crystal, which MCTiers lacks.
        manager.getProvider(McTiersProvider.ID).seedCache(uuid, null, false, 60_000L);
        manager.getProvider(PvpTiersProvider.ID).seedCache(uuid,
                profileWith("crystal", new TierRanking(1, TierRanking.HIGH)), false, 60_000L);

        TierAccessResult result = decideAny(manager, uuid, "crystal");

        assertTrue(result.isAllowed());
        assertEquals(Status.ALLOWED, result.getStatus());
        assertEquals("PvPTiers", result.getProviderName());
    }

    /** Seeds a definitive "this player is unknown here" on every provider. */
    private void seedAllAsMisses(TierManager manager, UUID uuid) {
        manager.getProviders().values().forEach(p -> p.seedCache(uuid, null, false, 60_000L));
    }

    @Test
    void decideAcross_deniesOnlyWhenEveryProviderDenies() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        seedAllAsMisses(manager, uuid);
        manager.getProvider(McTiersProvider.ID).seedCache(uuid,
                profileWith("vanilla", new TierRanking(4, TierRanking.HIGH)), false, 60_000L);
        manager.getProvider(PvpTiersProvider.ID).seedCache(uuid,
                profileWith("vanilla", new TierRanking(5, TierRanking.LOW)), false, 60_000L);

        TierAccessResult result = decideAny(manager, uuid, "vanilla");

        assertFalse(result.isAllowed());
        assertEquals(Status.DENIED_TIER, result.getStatus());
    }

    @Test
    void decideAcross_reportsTheMostInformativeDenial() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        // No profile on the other sites, but a too-low tier on PvPTiers. Telling
        // the player their tier is too low beats telling them they are unknown.
        seedAllAsMisses(manager, uuid);
        manager.getProvider(PvpTiersProvider.ID).seedCache(uuid,
                profileWith("sword", new TierRanking(5, TierRanking.LOW)), false, 60_000L);

        TierAccessResult result = decideAny(manager, uuid, "sword");

        assertEquals(Status.DENIED_TIER, result.getStatus());
        assertEquals("PvPTiers", result.getProviderName());
        assertEquals("LT5", result.getRanking().display());
    }

    @Test
    void decideAcross_aDefinitiveDenialOutranksAnUnreachableProvider() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        // MCTiers answered and said no; the other two could not be reached.
        manager.getProvider(McTiersProvider.ID).seedCache(uuid,
                profileWith("vanilla", new TierRanking(5, TierRanking.LOW)), false, 60_000L);

        TierAccessResult result = decideAny(manager, uuid, "vanilla");

        assertEquals(Status.DENIED_TIER, result.getStatus(),
                "an outage on one site must not hand out a free pass");
        assertFalse(result.isAllowed());
    }

    @Test
    void decideAcross_failsOpenOnlyWhenNoProviderAnswered() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        // Nothing cached anywhere, so every provider counts as unreachable.

        TierAccessResult result = decideAny(manager, uuid, "vanilla");

        assertEquals(Status.ALLOWED_ON_ERROR, result.getStatus());
        assertTrue(result.isAllowed());
    }

    @Test
    void decideAcross_aDefinitiveAllowStillWinsOverEverything() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        manager.getProvider(McTiersProvider.ID).seedCache(uuid,
                profileWith("vanilla", new TierRanking(5, TierRanking.LOW)), false, 60_000L);
        manager.getProvider(EliteStormProvider.ID).seedCache(uuid,
                profileWith("vanilla", new TierRanking(1, TierRanking.HIGH)), false, 60_000L);

        TierAccessResult result = decideAny(manager, uuid, "vanilla");

        assertEquals(Status.ALLOWED, result.getStatus());
        assertEquals("EliteStorm", result.getProviderName());
    }

    @Test
    void decideAcross_translatesTheGamemodeSlugPerProvider() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        // The same player, the same gamemode, spelled differently by each site.
        manager.getProvider(McTiersProvider.ID).seedCache(uuid,
                profileWith("nethop", new TierRanking(4, TierRanking.LOW)), false, 60_000L);
        manager.getProvider(PvpTiersProvider.ID).seedCache(uuid,
                profileWith("neth_pot", new TierRanking(2, TierRanking.HIGH)), false, 60_000L);

        // Configured with the MCTiers spelling; PvPTiers must still match.
        TierAccessResult result = decideAny(manager, uuid, "nethop");

        assertEquals(Status.ALLOWED, result.getStatus());
        assertEquals("PvPTiers", result.getProviderName());
        assertEquals("HT2", result.getRanking().display());
    }

    @Test
    void decideAcross_treatsAnUncachedProviderAsAFailedLookup() {
        config.set("tiers.allow-on-error", false);
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        // Nothing seeded anywhere, so every provider counts as unreachable.

        TierAccessResult result = decideAny(manager, uuid, "vanilla");

        assertEquals(Status.DENIED_ERROR, result.getStatus());
        assertFalse(result.isAllowed());
    }

    @Test
    void decideAcross_failsOpenWhenConfiguredTo() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();

        TierAccessResult result = decideAny(manager, uuid, "vanilla");

        assertEquals(Status.ALLOWED_ON_ERROR, result.getStatus());
        assertTrue(result.isAllowed());
    }

    @Test
    void decideAcross_withOneProviderUsesOnlyThatProvider() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        manager.getProvider(McTiersProvider.ID).seedCache(uuid,
                profileWith("vanilla", new TierRanking(4, TierRanking.HIGH)), false, 60_000L);
        manager.getProvider(PvpTiersProvider.ID).seedCache(uuid,
                profileWith("vanilla", new TierRanking(1, TierRanking.HIGH)), false, 60_000L);

        TierRequirement mcOnly = new TierRequirement("tryhard", "vanilla", 3, TierRanking.HIGH, McTiersProvider.ID);
        TierAccessResult result = manager.decideAcross(
                manager.resolveProviders(mcOnly), uuid, mcOnly);

        assertEquals(Status.DENIED_TIER, result.getStatus(),
                "a pinned provider must not be rescued by the other site");
        assertEquals("MCTiers", result.getProviderName());
    }

    // ------------------------------------------------------------------
    // Caching
    // ------------------------------------------------------------------

    @Test
    void cache_startsEmptyAcrossEveryProvider() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();

        assertEquals(0, manager.getCacheSize());
        manager.getProviders().values().forEach(provider -> {
            assertFalse(provider.isCached(uuid));
            assertNull(provider.getCachedProfile(uuid));
        });
    }

    @Test
    void getCacheSize_sumsAcrossProviders() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();

        manager.getProvider(McTiersProvider.ID).seedCache(uuid, null, false, 60_000L);
        manager.getProvider(PvpTiersProvider.ID).seedCache(uuid, null, false, 60_000L);

        assertEquals(2, manager.getCacheSize());
        assertEquals(2, manager.clearCache());
        assertEquals(0, manager.getCacheSize());
    }

    @Test
    void invalidate_dropsThePlayerFromEveryProvider() {
        TierManager manager = new TierManager(plugin);
        UUID uuid = UUID.randomUUID();
        manager.getProviders().values().forEach(p -> p.seedCache(uuid, null, false, 60_000L));

        manager.invalidate(uuid);

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
