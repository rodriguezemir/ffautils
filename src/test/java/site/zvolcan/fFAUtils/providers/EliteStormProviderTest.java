package site.zvolcan.fFAUtils.providers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the EliteStorm provider, which departs from the other two in every
 * way the {@link TierProvider} abstraction allows: query parameters instead of
 * a path segment, name-first lookup, and a payload shape that has to be
 * translated by hand.
 */
class EliteStormProviderTest {

    /** Verbatim from GET https://api.elitestorm.es/v2/users?nickname=RayoES */
    private static final String REAL_PAYLOAD = """
            {"id":"1475204780270817354","nickname":"RayoES","isPremium":true,"region":"EU",\
            "country":"AD","points":0,"top":null,\
            "guilds":[{"guildId":"1530616084426653769","points":0,"top":1},\
            {"guildId":"990623865174642718","points":25,"top":49}],\
            "tiers":[{"isRetired":false,"mode":1,"tier":"LT4"},{"isRetired":false,"mode":2,"tier":"HT5"},\
            {"isRetired":false,"mode":3,"tier":"HT4"},{"isRetired":false,"mode":4,"tier":"LT4"},\
            {"isRetired":false,"mode":5,"tier":"HT5"},{"isRetired":false,"mode":6,"tier":"HT5"},\
            {"isRetired":false,"mode":7,"tier":"LT5"},{"isRetired":false,"mode":8,"tier":"LT5"}]}""";

    /** Verbatim from GET https://api.elitestorm.es/v2/modes?guildId=990623865174642718 */
    private static final String REAL_MODES = """
            [{"id":1,"name":"UHC","guildId":"990623865174642718"},\
            {"id":2,"name":"Axe","guildId":"990623865174642718"},\
            {"id":3,"name":"Sword","guildId":"990623865174642718"},\
            {"id":4,"name":"Diamond Pot","guildId":"990623865174642718"},\
            {"id":5,"name":"Netherite Pot","guildId":"990623865174642718"},\
            {"id":6,"name":"SMP","guildId":"990623865174642718"},\
            {"id":7,"name":"Mace","guildId":"990623865174642718"},\
            {"id":8,"name":"Vanilla","guildId":"990623865174642718"}]""";

    private EliteStormProvider provider;

    @BeforeEach
    void setUp() {
        provider = newProvider(true);
    }

    private EliteStormProvider newProvider(boolean lookupByName) {
        return new EliteStormProvider(
                HttpClient.newHttpClient(),
                Logger.getLogger("EliteStormProviderTest"),
                new TierProvider.ProviderSettings(EliteStormProvider.DEFAULT_API_URL,
                        Duration.ofSeconds(5), 60_000L, 5_000L, "FFAUtils/test"),
                EliteStormProvider.DEFAULT_GUILD_ID,
                lookupByName);
    }

    // ------------------------------------------------------------------
    // Identity and request shaping
    // ------------------------------------------------------------------

    @Test
    void provider_hasItsOwnIdAndName() {
        assertEquals("elitestorm", provider.getId());
        assertEquals("EliteStorm", provider.getDisplayName());
    }

    @Test
    void buildsANicknameQueryUrl() {
        assertEquals("https://api.elitestorm.es/v2/users?nickname=RayoES",
                provider.buildProfileUriByName("RayoES").toString());
    }

    @Test
    void buildsAUuidQueryUrlWithoutDashes() {
        UUID uuid = UUID.fromString("f120e1a0-fc76-4c9b-8a1b-7a8db9509ed6");

        // The dashed form is rejected by the API with HTTP 422.
        assertEquals("https://api.elitestorm.es/v2/users?uuid=f120e1a0fc764c9b8a1b7a8db9509ed6",
                provider.buildProfileUri(uuid).toString());
    }

    @Test
    void encodesNicknamesSafely() {
        assertEquals("https://api.elitestorm.es/v2/users?nickname=a+b%26c",
                provider.buildProfileUriByName("a b&c").toString());
    }

    @Test
    void prefersNameLookupByDefault() {
        assertTrue(provider.supportsNameLookup());
        assertEquals(TierProvider.LookupMode.NAME, provider.getPreferredLookup());
    }

    @Test
    void canBeConfiguredToPreferUuidLookup() {
        assertEquals(TierProvider.LookupMode.UUID, newProvider(false).getPreferredLookup());
    }

    @Test
    void theOtherProvidersDoNotSupportNameLookup() {
        HttpClient http = HttpClient.newHttpClient();
        Logger log = Logger.getLogger("EliteStormProviderTest");
        TierProvider.ProviderSettings settings = new TierProvider.ProviderSettings(
                "https://example.test", Duration.ofSeconds(5), 0L, 0L, "FFAUtils/test");

        assertFalse(new McTiersProvider(http, log, settings).supportsNameLookup());
        assertFalse(new PvpTiersProvider(http, log, settings).supportsNameLookup());
        assertThrows(UnsupportedOperationException.class,
                () -> new McTiersProvider(http, log, settings).buildProfileUriByName("x"));
    }

    @Test
    void treatsOnly404AsNoProfile() {
        assertTrue(provider.isNoProfileStatus(404));
        // 422 is EliteStorm's "invalid query parameters", a genuine error —
        // unlike PvPTiers, where 422 means the player is unknown.
        assertFalse(provider.isNoProfileStatus(422));
        assertFalse(provider.isNoProfileStatus(500));
    }

    // ------------------------------------------------------------------
    // Payload translation
    // ------------------------------------------------------------------

    @Test
    void parsesTheRealPayloadIntoTheSharedShape() {
        TierProfile profile = provider.parseProfile(REAL_PAYLOAD);

        assertEquals("RayoES", profile.getName());
        assertEquals("EU", profile.getRegion());
        assertEquals(8, profile.getRankings().size());
    }

    @Test
    void mapsNumericModesOntoGamemodeSlugs() {
        TierProfile profile = provider.parseProfile(REAL_PAYLOAD);

        // mode 1 = UHC, 3 = Sword, 5 = Netherite Pot, 8 = Vanilla
        assertEquals("LT4", profile.getRanking("uhc").display());
        assertEquals("HT4", profile.getRanking("sword").display());
        assertEquals("HT5", profile.getRanking("nethop").display());
        assertEquals("LT5", profile.getRanking("vanilla").display());
    }

    @Test
    void parsesStringTierLabelsIntoTierAndPosition() {
        TierRanking uhc = provider.parseProfile(REAL_PAYLOAD).getRanking("uhc");

        assertEquals(4, uhc.getTier());
        assertEquals(TierRanking.LOW, uhc.getPos());
        assertFalse(uhc.isRetired());
        assertTrue(uhc.isValid());
    }

    @Test
    void appliesTheGateToATranslatedProfile() {
        TierProfile profile = provider.parseProfile(REAL_PAYLOAD);

        // Best across all gamemodes is HT4 (sword), which does not reach HT3.
        assertEquals("sword", profile.getBestGamemode(true));
        assertFalse(profile.getBestRanking(true).meets(3, TierRanking.HIGH, true));
        assertTrue(profile.getBestRanking(true).meets(4, TierRanking.HIGH, true));
    }

    @Test
    void carriesTheRetiredFlagThrough() {
        String body = """
                {"nickname":"x","region":"EU","points":0,\
                "tiers":[{"isRetired":true,"mode":8,"tier":"HT1"}]}""";

        TierRanking vanilla = provider.parseProfile(body).getRanking("vanilla");

        assertTrue(vanilla.isRetired());
        assertEquals("HT1", vanilla.display());
    }

    @Test
    void skipsModesTheGuildVocabularyDoesNotDescribe() {
        // Mode 15 is Creeper, which belongs to a different guild.
        String body = """
                {"nickname":"x","region":"EU","points":0,\
                "tiers":[{"isRetired":false,"mode":15,"tier":"HT1"},\
                {"isRetired":false,"mode":3,"tier":"HT2"}]}""";

        TierProfile profile = provider.parseProfile(body);

        assertEquals(1, profile.getRankings().size());
        assertEquals("HT2", profile.getRanking("sword").display());
    }

    @Test
    void skipsUnrecognisedTierLabels() {
        String body = """
                {"nickname":"x","region":"EU","points":0,\
                "tiers":[{"isRetired":false,"mode":3,"tier":"WAT"},\
                {"isRetired":false,"mode":8,"tier":"HT2"}]}""";

        TierProfile profile = provider.parseProfile(body);

        assertEquals(1, profile.getRankings().size());
        assertNull(profile.getRanking("sword"));
        assertEquals("HT2", profile.getRanking("vanilla").display());
    }

    @Test
    void toleratesAProfileWithNoTiers() {
        TierProfile profile = provider.parseProfile("{\"nickname\":\"x\",\"region\":\"EU\",\"points\":0}");

        assertEquals("x", profile.getName());
        assertTrue(profile.getRankings().isEmpty());
        assertNull(profile.getBestRanking(true));
    }

    // ------------------------------------------------------------------
    // Mode vocabulary
    // ------------------------------------------------------------------

    @Test
    void defaultModeVocabularyMatchesTheDefaultGuild() {
        Map<Integer, String> modes = provider.getModeSlugs();

        assertEquals("uhc", modes.get(1));
        assertEquals("sword", modes.get(3));
        assertEquals("pot", modes.get(4));
        assertEquals("nethop", modes.get(5));
        assertEquals("vanilla", modes.get(8));
    }

    @Test
    void parseModes_readsTheLiveVocabulary() {
        Map<Integer, String> modes = provider.parseModes(REAL_MODES);

        assertEquals(8, modes.size());
        assertEquals("uhc", modes.get(1));
        assertEquals("axe", modes.get(2));
        assertEquals("sword", modes.get(3));
        assertEquals("smp", modes.get(6));
        assertEquals("mace", modes.get(7));
        assertEquals("vanilla", modes.get(8));
    }

    @Test
    void parseModes_canonicalisesNamesOntoTheSharedVocabulary() {
        Map<Integer, String> modes = provider.parseModes(REAL_MODES);

        // "Diamond Pot" and "Netherite Pot" must line up with the slugs the
        // other two sites use, or a shared config gamemode would not match.
        assertEquals("pot", modes.get(4));
        assertEquals("nethop", modes.get(5));
    }

    @Test
    void parseModes_returnsEmptyForAMalformedPayload() {
        assertTrue(provider.parseModes("not json at all").isEmpty());
        assertTrue(provider.parseModes("[]").isEmpty());
    }

    @Test
    void canonicalSlug_normalisesSpacingAndCase() {
        assertEquals("nethop", EliteStormProvider.canonicalSlug("Netherite Pot"));
        assertEquals("pot", EliteStormProvider.canonicalSlug("Diamond Pot"));
        assertEquals("smp", EliteStormProvider.canonicalSlug("SMP"));
        assertEquals("creeper", EliteStormProvider.canonicalSlug("Creeper"));
    }

    // ------------------------------------------------------------------
    // Gamemode aliases
    // ------------------------------------------------------------------

    @Test
    void resolvesTheOtherSitesNetheriteSlugs() {
        assertEquals("nethop", provider.resolveGamemode("neth_pot"));
        assertEquals("nethop", provider.resolveGamemode("nethop"));
        assertEquals("pot", provider.resolveGamemode("diamond_pot"));
    }

    @Test
    void leavesSharedSlugsAlone() {
        for (String slug : new String[] { "sword", "axe", "pot", "uhc", "smp", "mace", "vanilla" }) {
            assertEquals(slug, provider.resolveGamemode(slug));
        }
    }
}
