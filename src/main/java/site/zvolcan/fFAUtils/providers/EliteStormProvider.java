package site.zvolcan.fFAUtils.providers;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tier rankings from EliteStorm ({@code https://api.elitestorm.es/v2/users}).
 *
 * <p>
 * This one departs from the other providers in every way the abstraction
 * allows:
 *
 * <ul>
 * <li>The player goes in a <b>query parameter</b> rather than the path:
 * {@code ?nickname=<name>} or {@code ?uuid=<uuid>}.</li>
 * <li>It looks players up <b>by name by default</b>, which is what makes it
 * usable on offline-mode servers where Bukkit's UUID is generated locally and
 * never matches the Mojang UUID the other sites are keyed by.</li>
 * <li>The {@code ?uuid=} form wants the UUID <b>without dashes</b>; the dashed
 * form is rejected with HTTP 422.</li>
 * <li>Its payload shape is different enough that {@link #parseProfile} has to
 * be overridden — tiers arrive as an array of {@code {mode, tier, isRetired}}
 * with the tier as a string like {@code "LT4"}, rather than a map of gamemode
 * slug to a tier/position pair.</li>
 * <li>Gamemodes are numeric ids whose meaning is scoped to a Discord guild, so
 * the id-to-slug vocabulary is loaded from {@code /modes?guildId=...}.</li>
 * </ul>
 */
public final class EliteStormProvider extends TierProvider {

    public static final String ID = "elitestorm";
    public static final String DEFAULT_API_URL = "https://api.elitestorm.es/v2";

    /** The guild whose mode ids the built-in defaults describe. */
    public static final String DEFAULT_GUILD_ID = "990623865174642718";

    /**
     * Mode ids for {@link #DEFAULT_GUILD_ID}, used until (or instead of) the
     * live vocabulary. Mode ids mean different things in different guilds, so
     * these are only correct for that guild.
     */
    private static final Map<Integer, String> DEFAULT_MODE_SLUGS = Map.of(
            1, "uhc",
            2, "axe",
            3, "sword",
            4, "pot",
            5, "nethop",
            6, "smp",
            7, "mace",
            8, "vanilla");

    /**
     * EliteStorm mode names slugify differently from the other sites' names for
     * the same thing; these bring them into the shared vocabulary.
     */
    private static final Map<String, String> CANONICAL_SLUGS = Map.of(
            "diamond_pot", "pot",
            "netherite_pot", "nethop",
            "neth_pot", "nethop");

    /** Config slugs mapped onto the vocabulary this provider reports. */
    private static final Map<String, String> GAMEMODE_ALIASES = Map.of(
            "neth_pot", "nethop",
            "nethpot", "nethop",
            "netherite", "nethop",
            "netherite_pot", "nethop",
            "diamond_pot", "pot",
            "crystal", "crystal");

    private final HttpClient httpClient;
    private final Logger logger;
    private final String guildId;
    private final boolean lookupByName;

    /** Mode id -> gamemode slug. Replaced wholesale once the live list loads. */
    private volatile Map<Integer, String> modeSlugs = DEFAULT_MODE_SLUGS;

    public EliteStormProvider(@NotNull HttpClient httpClient, @NotNull Logger logger,
            @NotNull ProviderSettings settings, @NotNull String guildId, boolean lookupByName) {
        super(httpClient, logger, settings);
        this.httpClient = httpClient;
        this.logger = logger;
        this.guildId = guildId;
        this.lookupByName = lookupByName;
    }

    @Override
    @NotNull
    public String getId() {
        return ID;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return "EliteStorm";
    }

    @Override
    @NotNull
    public String getDefaultApiUrl() {
        return DEFAULT_API_URL;
    }

    /** The Discord guild whose mode vocabulary this provider reads. */
    @NotNull
    public String getGuildId() {
        return guildId;
    }

    // ------------------------------------------------------------------
    // Request shaping — query parameters rather than a path segment
    // ------------------------------------------------------------------

    @Override
    public boolean supportsNameLookup() {
        return true;
    }

    /**
     * Names are preferred by default: on an offline-mode server the UUID is
     * generated locally and would never match anyone.
     */
    @Override
    @NotNull
    public LookupMode getPreferredLookup() {
        return lookupByName ? LookupMode.NAME : LookupMode.UUID;
    }

    /** The {@code ?uuid=} form wants bare hex; the dashed form returns 422. */
    @Override
    @NotNull
    protected String formatUuid(@NotNull UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    @Override
    @NotNull
    protected URI buildProfileUri(@NotNull UUID uuid) {
        return URI.create(getSettings().getApiUrl() + "/users?uuid=" + formatUuid(uuid));
    }

    @Override
    @NotNull
    protected URI buildProfileUriByName(@NotNull String name) {
        return URI.create(getSettings().getApiUrl() + "/users?nickname="
                + URLEncoder.encode(name, StandardCharsets.UTF_8));
    }

    /**
     * Only 404 means "no such player". 422 is EliteStorm's "invalid query
     * parameters" and is a genuine error here, unlike on PvPTiers where it
     * means the player is unknown.
     */
    @Override
    protected boolean isNoProfileStatus(int statusCode) {
        return statusCode == 404;
    }

    @Override
    @NotNull
    protected Map<String, String> getGamemodeAliases() {
        return GAMEMODE_ALIASES;
    }

    // ------------------------------------------------------------------
    // Payload translation
    // ------------------------------------------------------------------

    /**
     * Translates EliteStorm's payload into the shared {@link TierProfile}.
     *
     * <p>
     * Its tiers are an array keyed by numeric mode with the tier as a label
     * ({@code "LT4"}), so neither the container nor the tier representation
     * matches the other providers and the inherited Gson default cannot be used.
     */
    @Override
    @Nullable
    protected TierProfile parseProfile(@NotNull String body) {
        EliteStormUser user = gson.fromJson(body, EliteStormUser.class);
        if (user == null) {
            return null;
        }

        Map<Integer, String> modes = modeSlugs;
        Map<String, TierRanking> rankings = new LinkedHashMap<>();
        if (user.tiers != null) {
            for (EliteStormTier entry : user.tiers) {
                if (entry == null || entry.mode == null) {
                    continue;
                }
                String slug = modes.get(entry.mode);
                if (slug == null) {
                    // A mode this guild's vocabulary does not describe.
                    continue;
                }
                TierRanking ranking = TierRanking.parse(entry.tier, Boolean.TRUE.equals(entry.isRetired));
                if (ranking == null) {
                    logger.log(Level.FINE, "Unrecognised EliteStorm tier label: " + entry.tier);
                    continue;
                }
                rankings.put(slug, ranking);
            }
        }

        return new TierProfile(null, user.nickname, user.region,
                user.points == null ? 0 : user.points, rankings);
    }

    // ------------------------------------------------------------------
    // Mode vocabulary
    // ------------------------------------------------------------------

    /** Mode id -> gamemode slug as currently known. */
    @NotNull
    public Map<Integer, String> getModeSlugs() {
        return Map.copyOf(modeSlugs);
    }

    /**
     * Loads the guild's mode vocabulary. Mode ids are only meaningful within a
     * guild, so hardcoding them would silently mis-map gamemodes for any server
     * pointed at a different one. Failure is not fatal — the built-in defaults
     * stay in place.
     */
    @Override
    public void warmUp() {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(getSettings().getApiUrl() + "/modes?guildId="
                            + URLEncoder.encode(guildId, StandardCharsets.UTF_8)))
                    .timeout(getSettings().getTimeout())
                    .header("Accept", "application/json")
                    .header("User-Agent", getSettings().getUserAgent())
                    .GET()
                    .build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Invalid EliteStorm modes URL, keeping default gamemode ids", e);
            return;
        }

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    if (error != null || response.statusCode() < 200 || response.statusCode() >= 300) {
                        logger.log(Level.WARNING, "Could not load EliteStorm gamemodes for guild " + guildId
                                + ", keeping defaults"
                                + (error == null ? " (HTTP " + response.statusCode() + ")" : ": "
                                        + error.getMessage()));
                        return;
                    }
                    Map<Integer, String> loaded = parseModes(response.body());
                    if (!loaded.isEmpty()) {
                        modeSlugs = loaded;
                        logger.log(Level.INFO, "Loaded " + loaded.size()
                                + " EliteStorm gamemodes for guild " + guildId);
                    }
                });
    }

    /** Turns the {@code /modes} payload into an id -> slug map. */
    @NotNull
    Map<Integer, String> parseModes(@NotNull String body) {
        Map<Integer, String> result = new LinkedHashMap<>();
        List<EliteStormMode> modes;
        try {
            modes = gson.fromJson(body, new TypeToken<List<EliteStormMode>>() {
            }.getType());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Malformed EliteStorm modes response", e);
            return result;
        }
        if (modes == null) {
            return result;
        }
        for (EliteStormMode mode : modes) {
            if (mode == null || mode.id == null || mode.name == null) {
                continue;
            }
            result.put(mode.id, canonicalSlug(mode.name));
        }
        return result;
    }

    /** {@code "Netherite Pot"} becomes {@code nethop}, matching the other sites. */
    @NotNull
    static String canonicalSlug(@NotNull String modeName) {
        String slug = modeName.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return CANONICAL_SLUGS.getOrDefault(slug, slug);
    }

    // ------------------------------------------------------------------
    // Wire format
    // ------------------------------------------------------------------

    /** The {@code /users} payload. */
    private static final class EliteStormUser {
        private String id;
        private String nickname;
        @SerializedName("isPremium")
        private Boolean premium;
        private String region;
        private String country;
        private Integer points;
        private Integer top;
        private List<EliteStormTier> tiers;
    }

    /** One entry of the {@code tiers} array. */
    private static final class EliteStormTier {
        @SerializedName("isRetired")
        private Boolean isRetired;
        private Integer mode;
        private String tier;
    }

    /** One entry of the {@code /modes} payload. */
    private static final class EliteStormMode {
        private Integer id;
        private String name;
        private String guildId;
    }
}
