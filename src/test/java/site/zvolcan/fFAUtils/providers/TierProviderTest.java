package site.zvolcan.fFAUtils.providers;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link TierProvider} abstraction and its two implementations.
 *
 * <p>
 * The point of the abstract class is that MCTiers and PvPTiers differ in only
 * a handful of ways; these tests pin down each of those differences and the
 * caching behaviour they share.
 */
class TierProviderTest {

    private HttpClient httpClient;
    private Logger logger;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        logger = Logger.getLogger("TierProviderTest");
    }

    private TierProvider.ProviderSettings settings(String url) {
        return settings(url, 60_000L, 5_000L);
    }

    private TierProvider.ProviderSettings settings(String url, long cacheMillis, long errorCacheMillis) {
        return new TierProvider.ProviderSettings(
                url, Duration.ofSeconds(5), cacheMillis, errorCacheMillis, "FFAUtils/test");
    }

    private McTiersProvider mcTiers() {
        return new McTiersProvider(httpClient, logger, settings(McTiersProvider.DEFAULT_API_URL));
    }

    private PvpTiersProvider pvpTiers() {
        return new PvpTiersProvider(httpClient, logger, settings(PvpTiersProvider.DEFAULT_API_URL));
    }

    // ------------------------------------------------------------------
    // Identity
    // ------------------------------------------------------------------

    @Test
    void providers_haveDistinctIdsAndNames() {
        assertEquals("mctiers", mcTiers().getId());
        assertEquals("pvptiers", pvpTiers().getId());
        assertEquals("MCTiers", mcTiers().getDisplayName());
        assertEquals("PvPTiers", pvpTiers().getDisplayName());
        assertNotEquals(mcTiers().getId(), pvpTiers().getId());
    }

    // ------------------------------------------------------------------
    // UUID formatting — the difference that breaks requests if got wrong
    // ------------------------------------------------------------------

    @Test
    void mcTiers_buildsProfileUrlWithADashedUuid() {
        UUID uuid = UUID.fromString("6553509f-66d3-4041-875f-164236e42e84");

        assertEquals("https://mctiers.com/api/v2/profile/6553509f-66d3-4041-875f-164236e42e84",
                mcTiers().buildProfileUri(uuid).toString());
    }

    @Test
    void pvpTiers_buildsProfileUrlWithAnUndashedUuid() {
        UUID uuid = UUID.fromString("6553509f-66d3-4041-875f-164236e42e84");

        // PvPTiers answers HTTP 400 for the dashed form.
        assertEquals("https://pvptiers.com/api/profile/6553509f66d34041875f164236e42e84",
                pvpTiers().buildProfileUri(uuid).toString());
    }

    @Test
    void pvpTiers_uuidIsBare32HexCharacters() {
        String formatted = pvpTiers().formatUuid(UUID.randomUUID());

        assertEquals(32, formatted.length());
        assertFalse(formatted.contains("-"));
        assertTrue(formatted.matches("[0-9a-f]{32}"));
    }

    @Test
    void buildProfileUrl_honoursAConfiguredBaseUrl() {
        McTiersProvider provider = new McTiersProvider(httpClient, logger, settings("https://mirror.example/v2"));
        UUID uuid = UUID.fromString("6553509f-66d3-4041-875f-164236e42e84");

        assertTrue(provider.buildProfileUri(uuid).toString().startsWith("https://mirror.example/v2/profile/"));
    }

    // ------------------------------------------------------------------
    // "No profile" status codes
    // ------------------------------------------------------------------

    @Test
    void mcTiers_treats404AsNoProfile() {
        McTiersProvider provider = mcTiers();

        assertTrue(provider.isNoProfileStatus(404));
        assertFalse(provider.isNoProfileStatus(422));
        assertFalse(provider.isNoProfileStatus(500));
        assertFalse(provider.isNoProfileStatus(200));
    }

    @Test
    void pvpTiers_treats422AsNoProfile() {
        PvpTiersProvider provider = pvpTiers();

        // PvPTiers answers 422 for an unknown player rather than 404.
        assertTrue(provider.isNoProfileStatus(422));
        assertTrue(provider.isNoProfileStatus(404));
        assertFalse(provider.isNoProfileStatus(500), "500 is an outage, not an answer");
        assertFalse(provider.isNoProfileStatus(200));
    }

    // ------------------------------------------------------------------
    // Gamemode slug translation
    // ------------------------------------------------------------------

    @Test
    void mcTiers_translatesPvpTiersNetheriteSlug() {
        assertEquals("nethop", mcTiers().resolveGamemode("neth_pot"));
        assertEquals("nethop", mcTiers().resolveGamemode("nethop"));
    }

    @Test
    void pvpTiers_translatesMcTiersNetheriteSlug() {
        assertEquals("neth_pot", pvpTiers().resolveGamemode("nethop"));
        assertEquals("neth_pot", pvpTiers().resolveGamemode("neth_pot"));
    }

    @Test
    void resolveGamemode_leavesSharedSlugsAlone() {
        for (String slug : new String[] { "sword", "axe", "pot", "uhc", "smp", "mace" }) {
            assertEquals(slug, mcTiers().resolveGamemode(slug));
            assertEquals(slug, pvpTiers().resolveGamemode(slug));
        }
    }

    @Test
    void resolveGamemode_passesThroughSlugsExclusiveToOneSite() {
        // vanilla exists only on MCTiers, crystal only on PvPTiers. Neither is
        // rewritten; the player simply has no ranking for it on the other site.
        assertEquals("vanilla", pvpTiers().resolveGamemode("vanilla"));
        assertEquals("crystal", mcTiers().resolveGamemode("crystal"));
    }

    @Test
    void resolveGamemode_normalisesCaseAndWhitespace() {
        assertEquals("neth_pot", pvpTiers().resolveGamemode("  NethOp  "));
        assertEquals("", mcTiers().resolveGamemode(null));
    }

    // ------------------------------------------------------------------
    // Shared parsing — both APIs return the same JSON shape
    // ------------------------------------------------------------------

    @Test
    void bothProviders_parseTheirRealPayloadsIdentically() {
        // Captured from https://mctiers.com/api/v2/profile/<dashed-uuid>
        String mcTiersBody = """
                {"uuid":"5e24102f-a2b8-46a3-92e0-932a52dc90f2","name":"K1RBE","region":"NA",\
                "points":98,"overall":61,"discord_id":null,\
                "rankings":{"vanilla":{"tier":1,"pos":0,"peak_tier":1,"peak_pos":0,\
                "attained":1730245596,"retired":false}},"badges":[],"tests":[]}""";

        // Captured from https://pvptiers.com/api/profile/<undashed-uuid>
        String pvpTiersBody = """
                {"uuid":"5e24102fa2b846a392e0932a52dc90f2","name":"K1RBE",\
                "rankings":{"crystal":{"tier":1,"pos":0,"peak_tier":1,"peak_pos":0,\
                "attained":1770836101,"retired":true}},"region":"NA","points":68,\
                "overall":130,"badges":[{"title":"Crystal Expert","desc":"..."}]}""";

        TierProfile fromMcTiers = mcTiers().parseProfile(mcTiersBody);
        TierProfile fromPvpTiers = pvpTiers().parseProfile(pvpTiersBody);

        assertEquals("K1RBE", fromMcTiers.getName());
        assertEquals("K1RBE", fromPvpTiers.getName());
        assertEquals("NA", fromMcTiers.getRegion());
        assertEquals("NA", fromPvpTiers.getRegion());
        assertEquals("HT1", fromMcTiers.getRanking("vanilla").display());
        assertEquals("HT1", fromPvpTiers.getRanking("crystal").display());
        assertTrue(fromPvpTiers.getRanking("crystal").isRetired());
    }

    @Test
    void parseProfile_readsPvpTiersNullPeakFields() {
        String body = """
                {"uuid":"6553509f66d34041875f164236e42e84","name":"uku3lig",\
                "rankings":{"neth_pot":{"tier":4,"pos":1,"peak_tier":null,"peak_pos":null,\
                "attained":1687831523,"retired":false}},"region":"NA","points":3,\
                "overall":56969,"badges":[]}""";

        TierProfile profile = pvpTiers().parseProfile(body);
        TierRanking ranking = profile.getRanking("neth_pot");

        assertEquals("LT4", ranking.display());
        assertNull(ranking.getPeakTier());
        assertFalse(ranking.meets(3, TierRanking.HIGH, true));
    }

    // ------------------------------------------------------------------
    // Caching, which the abstract class provides to both
    // ------------------------------------------------------------------

    @Test
    void cache_startsEmpty() {
        TierProvider provider = mcTiers();
        UUID uuid = UUID.randomUUID();

        assertFalse(provider.isCached(uuid));
        assertNull(provider.getCachedProfile(uuid));
        assertEquals(0, provider.getCacheSize());
    }

    @Test
    void cache_servesASeededProfileWithoutARequest() {
        TierProvider provider = mcTiers();
        UUID uuid = UUID.randomUUID();
        TierProfile profile = new Gson().fromJson(
                "{\"name\":\"x\",\"rankings\":{\"vanilla\":{\"tier\":2,\"pos\":0}}}", TierProfile.class);

        provider.seedCache(uuid, profile, false, 60_000L);

        assertTrue(provider.isCached(uuid));
        assertFalse(provider.isCachedFailure(uuid));
        assertEquals("HT2", provider.getCachedProfile(uuid).getRanking("vanilla").display());
        // Already cached, so the future is complete and no network is touched.
        assertTrue(provider.fetchProfile(uuid).isDone());
        assertSame(profile, provider.fetchProfile(uuid).join());
    }

    @Test
    void cache_distinguishesAMissFromAFailure() {
        TierProvider provider = mcTiers();
        UUID missing = UUID.randomUUID();
        UUID broken = UUID.randomUUID();

        provider.seedCache(missing, null, false, 60_000L);
        provider.seedCache(broken, null, true, 60_000L);

        assertTrue(provider.isCached(missing));
        assertFalse(provider.isCachedFailure(missing));
        assertNull(provider.fetchProfile(missing).join(), "a cached miss resolves to null");

        assertTrue(provider.isCachedFailure(broken));
        assertTrue(provider.fetchProfile(broken).isCompletedExceptionally(),
                "a cached failure stays a failure until it expires");
    }

    @Test
    void cache_expiresEntriesOnceTheTtlPasses() {
        TierProvider provider = mcTiers();
        UUID uuid = UUID.randomUUID();

        provider.seedCache(uuid, null, false, -1L);

        assertFalse(provider.isCached(uuid), "an entry past its expiry is a miss");
        assertEquals(0, provider.getCacheSize(), "and is evicted when read");
    }

    @Test
    void cache_isPerProviderNotShared() {
        TierProvider mc = mcTiers();
        TierProvider pvp = pvpTiers();
        UUID uuid = UUID.randomUUID();

        mc.seedCache(uuid, null, false, 60_000L);

        assertTrue(mc.isCached(uuid));
        assertFalse(pvp.isCached(uuid), "each site ranks a player differently, so caches must not be shared");
    }

    @Test
    void invalidateAndClear_dropEntries() {
        TierProvider provider = mcTiers();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        provider.seedCache(first, null, false, 60_000L);
        provider.seedCache(second, null, false, 60_000L);

        provider.invalidate(first);
        assertFalse(provider.isCached(first));
        assertTrue(provider.isCached(second));

        assertEquals(1, provider.clearCache());
        assertEquals(0, provider.getCacheSize());
    }

    @Test
    void settings_clampNegativeCacheDurationsToZero() {
        TierProvider.ProviderSettings s = settings("https://example.test", -10L, -20L);

        assertEquals(0L, s.getCacheMillis());
        assertEquals(0L, s.getErrorCacheMillis());
    }
}
