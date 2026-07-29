package site.zvolcan.fFAUtils.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Blocks entry to configured spawns unless the player's MCTiers ranking is good
 * enough.
 *
 * <p>
 * Rankings come from v2 of the public MCTiers API over HTTPS
 * ({@code GET /profile/{uuid}}) and are parsed with Gson. Every lookup is keyed
 * by the player's UUID and cached in a {@link ConcurrentHashMap} with a TTL, so
 * a spawn gate normally costs nothing: the profile is prefetched when the
 * player joins and reused until it expires. Concurrent lookups for the same
 * UUID share a single in-flight request.
 *
 * <p>
 * A tier requirement is expressed as a tier plus a position, matching the API:
 * tier 3 position 0 means "HT3 or better". Whether the gate is enforced at all
 * is a runtime toggle backed by {@code tiers.enabled} in config.yml.
 */
public final class TierManager {

    /** Base URL of v2 of the public MCTiers API. */
    public static final String DEFAULT_API_URL = "https://mctiers.com/api/v2";

    /**
     * Gamemode values that mean "judge the player on whichever gamemode they
     * rank highest in" instead of one specific gamemode.
     */
    private static final Set<String> ANY_GAMEMODE = Set.of("best", "any", "overall", "*");

    private final JavaPlugin plugin;
    private final Gson gson = new GsonBuilder().create();

    /** UUID -> cached profile lookup. Entries carry their own expiry. */
    private final Map<UUID, CachedProfile> cache = new ConcurrentHashMap<>();
    /** UUID -> lookup already in progress, so we never fire duplicate requests. */
    private final Map<UUID, CompletableFuture<TierProfile>> inFlight = new ConcurrentHashMap<>();

    private HttpClient httpClient;

    @Getter
    private boolean enabled;
    @Getter
    private String apiUrl = DEFAULT_API_URL;
    @Getter
    private String defaultGamemode = "vanilla";
    @Getter
    private boolean usePeakWhenRetired = true;
    @Getter
    private boolean allowOnError = true;
    @Getter
    private boolean prefetchOnJoin = true;
    @Getter
    private String bypassPermission = "ffautils.tiers.bypass";
    private long cacheMillis = Duration.ofMinutes(30).toMillis();
    private long errorCacheMillis = Duration.ofMinutes(2).toMillis();
    private Duration timeout = Duration.ofSeconds(5);

    /** Lowercased spawn name -> tier requirement for that spawn. */
    private final Map<String, TierRequirement> restrictedSpawns = new ConcurrentHashMap<>();

    public TierManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
        loadSettings();
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    /** Reads the whole {@code tiers} config section. Safe to call on reload. */
    public void loadSettings() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("tiers");
        if (section == null) {
            section = plugin.getConfig().createSection("tiers");
        }

        enabled = section.getBoolean("enabled", false);
        apiUrl = stripTrailingSlash(section.getString("api-url", DEFAULT_API_URL));
        defaultGamemode = normalizeGamemode(section.getString("gamemode", "vanilla"));
        usePeakWhenRetired = section.getBoolean("use-peak-when-retired", true);
        allowOnError = section.getBoolean("allow-on-error", true);
        prefetchOnJoin = section.getBoolean("prefetch-on-join", true);
        bypassPermission = section.getString("bypass-permission", "ffautils.tiers.bypass");
        cacheMillis = Math.max(0L, section.getLong("cache-minutes", 30L)) * 60_000L;
        errorCacheMillis = Math.max(0L, section.getLong("error-cache-minutes", 2L)) * 60_000L;
        timeout = Duration.ofSeconds(Math.max(1L, section.getLong("timeout-seconds", 5L)));

        int defaultTier = clampTier(section.getInt("tier", 3));
        int defaultPos = clampPos(section.getInt("pos", TierRanking.HIGH));

        restrictedSpawns.clear();
        ConfigurationSection spawnsSection = section.getConfigurationSection("restricted-spawns");
        if (spawnsSection != null) {
            for (String spawnName : spawnsSection.getKeys(false)) {
                ConfigurationSection spawnSection = spawnsSection.getConfigurationSection(spawnName);
                int tier = defaultTier;
                int pos = defaultPos;
                String gamemode = defaultGamemode;
                if (spawnSection != null) {
                    tier = clampTier(spawnSection.getInt("tier", defaultTier));
                    pos = clampPos(spawnSection.getInt("pos", defaultPos));
                    gamemode = normalizeGamemode(spawnSection.getString("gamemode", defaultGamemode));
                }
                restrictedSpawns.put(spawnName.toLowerCase(Locale.ROOT),
                        new TierRequirement(spawnName, gamemode, tier, pos));
            }
        }

        // The client is rebuilt because the connect timeout is baked into it.
        closeClient();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Seeds the message keys this manager uses. {@code messages.yml} is not
     * overwritten on update, so without this the tier messages would render as
     * "Message not found" on servers that already have the file.
     */
    public static void registerMessageDefaults(@NotNull MessagesManager messages) {
        messages.addDefault("tier-checking", "<gray>Verificando tu tier en MCTiers...");
        messages.addDefault("tier-blocked",
                "<red>Necesitas ser <white>{required}</white> o mejor en <white>{gamemode}</white> "
                        + "para entrar a este spawn. <gray>(tu tier: {current})</gray>");
        messages.addDefault("tier-no-profile",
                "<red>No tienes un perfil en MCTiers, no puedes entrar a este spawn.");
        messages.addDefault("tier-no-ranking",
                "<red>No estas rankeado en <white>{gamemode}</white>, no puedes entrar a este spawn.");
        messages.addDefault("tier-lookup-failed",
                "<red>No se pudo verificar tu tier en MCTiers. Intentalo de nuevo.");
        messages.addDefault("tiers-enabled", "<green>El bloqueo de spawns por tiers ha sido activado.");
        messages.addDefault("tiers-disabled", "<yellow>El bloqueo de spawns por tiers ha sido desactivado.");
        messages.addDefault("tiers-status",
                "<gray>Bloqueo por tiers: {status} <dark_gray>|</dark_gray> <gray>gamemode: "
                        + "<white>{gamemode}</white> <dark_gray>|</dark_gray> <gray>cache: <white>{cache}</white>");
        messages.addDefault("tiers-status-spawn",
                "<dark_gray> - <white>{spawn}</white> <gray>requiere <white>{required}</white> "
                        + "en <white>{gamemode}</white>");
        messages.addDefault("tiers-status-no-spawns", "<gray>No hay spawns restringidos configurados.");
        messages.addDefault("tiers-reloaded", "<green>Configuracion de tiers recargada.");
        messages.addDefault("tiers-cache-cleared", "<green>Cache de tiers limpiada ({entries} entradas).");
        messages.addDefault("tiers-lookup", "<gray>{player}: <white>{current}</white> en <white>{gamemode}</white>");
        messages.addDefault("tiers-lookup-none", "<red>{player} no tiene tiers en MCTiers.");
        messages.addDefault("tiers-lookup-failed", "<red>No se pudo consultar el tier de {player}.");
        messages.addDefault("tiers-player-not-found", "<red>El jugador {player} no esta conectado.");
    }

    /** Turns the gate on or off and persists the choice to config.yml. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("tiers.enabled", enabled);
        plugin.saveConfig();
    }

    /** Flips the gate and returns the new state. */
    public boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    /** Cache TTL for successful lookups, in milliseconds. */
    public long getCacheMillis() {
        return cacheMillis;
    }

    /** Every restricted spawn keyed by its lowercased name. */
    @NotNull
    public Map<String, TierRequirement> getRestrictedSpawns() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(restrictedSpawns));
    }

    /** Whether the gate applies to a spawn right now. */
    public boolean isRestricted(@Nullable String spawnName) {
        return enabled && getRequirement(spawnName) != null;
    }

    /** The requirement configured for a spawn, or null when it is unrestricted. */
    @Nullable
    public TierRequirement getRequirement(@Nullable String spawnName) {
        if (spawnName == null || spawnName.isEmpty()) {
            return null;
        }
        return restrictedSpawns.get(spawnName.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Access checks
    // ------------------------------------------------------------------

    /**
     * Resolves whether a player may enter a spawn, then hands the result to
     * {@code callback} on the main server thread.
     *
     * <p>
     * When the profile is already cached — the normal case — the callback runs
     * synchronously, before this method returns. Otherwise the HTTP lookup runs
     * off the main thread and the callback is scheduled once it completes.
     */
    public void checkAccess(@NotNull Player player, @Nullable String spawnName,
            @NotNull Consumer<TierAccessResult> callback) {
        TierRequirement requirement = enabled ? getRequirement(spawnName) : null;
        if (requirement == null) {
            callback.accept(TierAccessResult.notRestricted());
            return;
        }
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) {
            callback.accept(TierAccessResult.bypass());
            return;
        }

        UUID uuid = player.getUniqueId();
        CachedProfile cached = readCache(uuid);
        if (cached != null) {
            callback.accept(decide(cached.profile, cached.failed, requirement, usePeakWhenRetired, allowOnError));
            return;
        }

        fetchProfile(uuid).whenComplete((profile, error) -> runOnMain(() -> {
            boolean failed = error != null;
            callback.accept(decide(profile, failed, requirement, usePeakWhenRetired, allowOnError));
        }));
    }

    /**
     * Whether the access check for this spawn can be answered without an HTTP
     * request. Callers use this to decide if a "checking…" message is worth
     * showing.
     */
    public boolean isResolvedImmediately(@NotNull Player player, @Nullable String spawnName) {
        TierRequirement requirement = enabled ? getRequirement(spawnName) : null;
        if (requirement == null) {
            return true;
        }
        if (!bypassPermission.isEmpty() && player.hasPermission(bypassPermission)) {
            return true;
        }
        return readCache(player.getUniqueId()) != null;
    }

    /**
     * The access decision itself, kept free of Bukkit and HTTP so it can be
     * reasoned about — and tested — on its own.
     *
     * @param profile            the player's profile, or null when they have none
     * @param lookupFailed       true when the API could not be reached at all
     * @param requirement        the spawn's requirement
     * @param usePeakWhenRetired judge retired players on their peak tier
     * @param allowOnError       let players through when the API is unreachable
     */
    @NotNull
    public static TierAccessResult decide(@Nullable TierProfile profile, boolean lookupFailed,
            @NotNull TierRequirement requirement, boolean usePeakWhenRetired, boolean allowOnError) {
        if (lookupFailed) {
            return allowOnError
                    ? new TierAccessResult(Status.ALLOWED_ON_ERROR, requirement, null, null)
                    : new TierAccessResult(Status.DENIED_ERROR, requirement, null, null);
        }
        if (profile == null) {
            return new TierAccessResult(Status.DENIED_NO_PROFILE, requirement, null, null);
        }

        String gamemode = requirement.getGamemode();
        TierRanking ranking;
        String matchedGamemode;
        if (isAnyGamemode(gamemode)) {
            ranking = profile.getBestRanking(usePeakWhenRetired);
            matchedGamemode = profile.getBestGamemode(usePeakWhenRetired);
        } else {
            ranking = profile.getRanking(gamemode);
            matchedGamemode = gamemode;
        }

        if (ranking == null || !ranking.isValid()) {
            return new TierAccessResult(Status.DENIED_NO_RANKING, requirement, null, matchedGamemode);
        }

        Status status = ranking.meets(requirement.getTier(), requirement.getPos(), usePeakWhenRetired)
                ? Status.ALLOWED
                : Status.DENIED_TIER;
        return new TierAccessResult(status, requirement, ranking, matchedGamemode);
    }

    /** Whether a configured gamemode means "any gamemode". */
    public static boolean isAnyGamemode(@Nullable String gamemode) {
        return gamemode == null || gamemode.isEmpty() || ANY_GAMEMODE.contains(gamemode.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Lookups and caching
    // ------------------------------------------------------------------

    /**
     * The player's cached profile, or null when nothing usable is cached. Never
     * performs a request, so it is safe to call from the main thread.
     */
    @Nullable
    public TierProfile getCachedProfile(@NotNull UUID uuid) {
        CachedProfile cached = readCache(uuid);
        return cached == null ? null : cached.profile;
    }

    /** Whether a fresh entry for this UUID is in the cache. */
    public boolean isCached(@NotNull UUID uuid) {
        return readCache(uuid) != null;
    }

    /**
     * Resolves the player's profile, hitting the API only when nothing fresh is
     * cached. The future completes with null when the player has no MCTiers
     * profile, and completes exceptionally when the API could not be reached.
     */
    @NotNull
    public CompletableFuture<TierProfile> fetchProfile(@NotNull UUID uuid) {
        CachedProfile cached = readCache(uuid);
        if (cached != null) {
            return cached.failed
                    ? CompletableFuture.failedFuture(new IllegalStateException("MCTiers lookup recently failed"))
                    : CompletableFuture.completedFuture(cached.profile);
        }
        CompletableFuture<TierProfile> future = inFlight.computeIfAbsent(uuid, this::requestProfile);
        // Registered outside computeIfAbsent: removing the entry from within the
        // mapping function would be a recursive update on the same key.
        future.whenComplete((profile, error) -> inFlight.remove(uuid, future));
        return future;
    }

    /** Warms the cache for a player without caring about the outcome. */
    public void prefetch(@NotNull UUID uuid) {
        if (!enabled || restrictedSpawns.isEmpty() || isCached(uuid)) {
            return;
        }
        fetchProfile(uuid).exceptionally(error -> null);
    }

    private CompletableFuture<TierProfile> requestProfile(UUID uuid) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/profile/" + uuid))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", "FFAUtils/" + plugin.getDescription().getVersion())
                    .GET()
                    .build();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Invalid MCTiers API URL: " + apiUrl, e);
            return CompletableFuture.failedFuture(e);
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        cacheFailure(uuid);
                        plugin.getLogger().log(Level.WARNING,
                                "MCTiers lookup failed for " + uuid + ": " + error.getMessage());
                        throw new java.util.concurrent.CompletionException(error);
                    }

                    int code = response.statusCode();
                    // 404 is a real answer: the player simply has no MCTiers profile.
                    if (code == 404) {
                        cacheProfile(uuid, null);
                        return null;
                    }
                    if (code < 200 || code >= 300) {
                        cacheFailure(uuid);
                        plugin.getLogger().log(Level.WARNING,
                                "MCTiers lookup for " + uuid + " returned HTTP " + code);
                        throw new java.util.concurrent.CompletionException(
                                new IllegalStateException("MCTiers API returned HTTP " + code));
                    }

                    TierProfile profile;
                    try {
                        profile = gson.fromJson(response.body(), TierProfile.class);
                    } catch (JsonParseException e) {
                        cacheFailure(uuid);
                        plugin.getLogger().log(Level.WARNING, "Malformed MCTiers response for " + uuid, e);
                        throw new java.util.concurrent.CompletionException(e);
                    }
                    cacheProfile(uuid, profile);
                    return profile;
                });
    }

    @Nullable
    private CachedProfile readCache(@NotNull UUID uuid) {
        CachedProfile cached = cache.get(uuid);
        if (cached == null) {
            return null;
        }
        if (System.currentTimeMillis() >= cached.expiresAt) {
            cache.remove(uuid, cached);
            return null;
        }
        return cached;
    }

    private void cacheProfile(UUID uuid, @Nullable TierProfile profile) {
        if (cacheMillis <= 0L) {
            return;
        }
        cache.put(uuid, new CachedProfile(profile, false, System.currentTimeMillis() + cacheMillis));
    }

    private void cacheFailure(UUID uuid) {
        if (errorCacheMillis <= 0L) {
            return;
        }
        // Short-lived so a blip does not lock players out (or in) for long.
        cache.put(uuid, new CachedProfile(null, true, System.currentTimeMillis() + errorCacheMillis));
    }

    /**
     * Drops the cached profile for a single player. Not called on quit on
     * purpose: entries are TTL'd, so keeping them means a reconnecting player
     * costs no extra MCTiers request.
     */
    public void invalidate(@NotNull UUID uuid) {
        cache.remove(uuid);
    }

    /** Drops every cached profile and returns how many entries were removed. */
    public int clearCache() {
        int size = cache.size();
        cache.clear();
        return size;
    }

    /** Number of entries currently held in the cache, expired ones included. */
    public int getCacheSize() {
        return cache.size();
    }

    /** Releases the HTTP client. Call from {@code onDisable()}. */
    public void shutdown() {
        closeClient();
        cache.clear();
        inFlight.clear();
    }

    private void closeClient() {
        if (httpClient == null) {
            return;
        }
        try {
            httpClient.close();
        } catch (Exception ignored) {
            // Nothing useful to do if the client refuses to shut down cleanly.
        }
        httpClient = null;
    }

    private void runOnMain(Runnable task) {
        try {
            if (plugin.getServer().isPrimaryThread()) {
                task.run();
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (IllegalStateException e) {
            // Plugin disabled while a lookup was in flight — drop the result.
            plugin.getLogger().log(Level.FINE, "Dropped MCTiers callback: " + e.getMessage());
        }
    }

    private static String stripTrailingSlash(String url) {
        String value = url == null || url.isBlank() ? DEFAULT_API_URL : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeGamemode(@Nullable String gamemode) {
        return gamemode == null ? "" : gamemode.trim().toLowerCase(Locale.ROOT);
    }

    static int clampTier(int tier) {
        return Math.min(TierRanking.WORST_TIER, Math.max(TierRanking.BEST_TIER, tier));
    }

    static int clampPos(int pos) {
        return pos == TierRanking.LOW ? TierRanking.LOW : TierRanking.HIGH;
    }

    // ------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------

    /** The tier a player must reach to enter a given spawn. */
    public static final class TierRequirement {
        private final String spawnName;
        private final String gamemode;
        private final int tier;
        private final int pos;

        public TierRequirement(@NotNull String spawnName, @Nullable String gamemode, int tier, int pos) {
            this.spawnName = spawnName;
            this.gamemode = normalizeGamemode(gamemode);
            this.tier = clampTier(tier);
            this.pos = clampPos(pos);
        }

        @NotNull
        public String getSpawnName() {
            return spawnName;
        }

        /** Gamemode slug, or a value accepted by {@link #isAnyGamemode(String)}. */
        @NotNull
        public String getGamemode() {
            return gamemode;
        }

        /** Required tier, 1-5, where lower is stricter. */
        public int getTier() {
            return tier;
        }

        /** Required position: {@link TierRanking#HIGH} or {@link TierRanking#LOW}. */
        public int getPos() {
            return pos;
        }

        /** The requirement rendered the way MCTiers does, e.g. {@code HT3}. */
        @NotNull
        public String display() {
            return TierRanking.display(tier, pos);
        }

        @Override
        public String toString() {
            return "TierRequirement{" + spawnName + " >= " + display() + " in " + gamemode + "}";
        }
    }

    /** Outcome of an access check. */
    public enum Status {
        /** The spawn has no tier requirement, or the gate is off. */
        NOT_RESTRICTED,
        /** The player holds the bypass permission. */
        BYPASS,
        /** The player's ranking meets the requirement. */
        ALLOWED,
        /** The API was unreachable and the gate is configured to fail open. */
        ALLOWED_ON_ERROR,
        /** The player's ranking is below the requirement. */
        DENIED_TIER,
        /** The player has no MCTiers profile. */
        DENIED_NO_PROFILE,
        /** The player has a profile but no ranking in the required gamemode. */
        DENIED_NO_RANKING,
        /** The API was unreachable and the gate is configured to fail closed. */
        DENIED_ERROR
    }

    /** An access decision plus the context needed to explain it to the player. */
    public static final class TierAccessResult {
        @Getter
        private final Status status;
        private final TierRequirement requirement;
        private final TierRanking ranking;
        private final String gamemode;

        public TierAccessResult(@NotNull Status status, @Nullable TierRequirement requirement,
                @Nullable TierRanking ranking, @Nullable String gamemode) {
            this.status = status;
            this.requirement = requirement;
            this.ranking = ranking;
            this.gamemode = gamemode;
        }

        static TierAccessResult notRestricted() {
            return new TierAccessResult(Status.NOT_RESTRICTED, null, null, null);
        }

        static TierAccessResult bypass() {
            return new TierAccessResult(Status.BYPASS, null, null, null);
        }

        public boolean isAllowed() {
            return status == Status.NOT_RESTRICTED
                    || status == Status.BYPASS
                    || status == Status.ALLOWED
                    || status == Status.ALLOWED_ON_ERROR;
        }

        /** The requirement that was applied, or null when none was. */
        @Nullable
        public TierRequirement getRequirement() {
            return requirement;
        }

        /** The ranking the decision was made on, or null when there was none. */
        @Nullable
        public TierRanking getRanking() {
            return ranking;
        }

        /** The gamemode the decision was made on, or null when not applicable. */
        @Nullable
        public String getGamemode() {
            return gamemode;
        }

        @Override
        public String toString() {
            return "TierAccessResult{" + status + ", ranking=" + ranking + "}";
        }
    }

    /** A cached lookup. {@code profile} is null both for 404s and for failures. */
    private static final class CachedProfile {
        private final TierProfile profile;
        private final boolean failed;
        private final long expiresAt;

        private CachedProfile(@Nullable TierProfile profile, boolean failed, long expiresAt) {
            this.profile = profile;
            this.failed = failed;
            this.expiresAt = expiresAt;
        }
    }
}
