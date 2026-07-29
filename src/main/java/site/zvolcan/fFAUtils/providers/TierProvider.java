package site.zvolcan.fFAUtils.providers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.objects.TierProfile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A source of Minecraft PvP tier rankings, looked up by player UUID.
 *
 * <p>
 * Everything that is the same for every tier site lives here and is final:
 * the HTTPS request, Gson parsing, the per-UUID cache with its TTL, and the
 * de-duplication of concurrent lookups for the same player. A subclass only
 * describes how its own API differs.
 *
 * <p>
 * The two shipped implementations differ in exactly four ways, which is what
 * the abstract methods below carve out:
 *
 * <table border="1">
 * <caption>Differences between the supported APIs</caption>
 * <tr>
 * <th></th>
 * <th>{@link McTiersProvider}</th>
 * <th>{@link PvpTiersProvider}</th>
 * </tr>
 * <tr>
 * <td>Endpoint</td>
 * <td>{@code mctiers.com/api/v2/profile/{uuid}}</td>
 * <td>{@code pvptiers.com/api/profile/{uuid}}</td>
 * </tr>
 * <tr>
 * <td>UUID format</td>
 * <td>with dashes</td>
 * <td>without dashes</td>
 * </tr>
 * <tr>
 * <td>"no profile"</td>
 * <td>HTTP 404</td>
 * <td>HTTP 422</td>
 * </tr>
 * <tr>
 * <td>Gamemode slugs</td>
 * <td>{@code nethop}, {@code vanilla}</td>
 * <td>{@code neth_pot}, {@code crystal}</td>
 * </tr>
 * </table>
 *
 * <p>
 * Their JSON payloads happen to share a shape, so {@link #parseProfile} has a
 * working default and neither subclass overrides it.
 */
public abstract class TierProvider {

    protected final Gson gson = new GsonBuilder().create();

    private final HttpClient httpClient;
    private final Logger logger;
    @Getter
    private final ProviderSettings settings;

    /** UUID -> cached lookup, each entry carrying its own expiry. */
    private final Map<UUID, CachedProfile> cache = new ConcurrentHashMap<>();
    /** UUID -> lookup already running, so duplicate requests are never sent. */
    private final Map<UUID, CompletableFuture<TierProfile>> inFlight = new ConcurrentHashMap<>();

    protected TierProvider(@NotNull HttpClient httpClient, @NotNull Logger logger,
            @NotNull ProviderSettings settings) {
        this.httpClient = httpClient;
        this.logger = logger;
        this.settings = settings;
    }

    // ------------------------------------------------------------------
    // What each API does differently
    // ------------------------------------------------------------------

    /** Stable lowercase id used in config and commands, e.g. {@code mctiers}. */
    @NotNull
    public abstract String getId();

    /** Human readable name for messages, e.g. {@code MCTiers}. */
    @NotNull
    public abstract String getDisplayName();

    /** The default base URL, used when config does not override it. */
    @NotNull
    public abstract String getDefaultApiUrl();

    /**
     * Renders a UUID the way this API expects it in the request path. MCTiers
     * wants the dashed form; PvPTiers rejects it with HTTP 400 and wants the
     * bare 32 hex characters.
     */
    @NotNull
    protected abstract String formatUuid(@NotNull UUID uuid);

    /**
     * Whether a status code means "this player has no profile here" as opposed
     * to "the request failed". The distinction matters: the first is a real
     * answer worth caching, the second is an outage.
     */
    protected abstract boolean isNoProfileStatus(int statusCode);

    /**
     * Maps a gamemode slug from another provider's vocabulary onto this one's.
     * Returning the input unchanged is fine, and a slug this site does not have
     * at all simply yields no ranking for the player.
     */
    @NotNull
    protected Map<String, String> getGamemodeAliases() {
        return Map.of();
    }

    /**
     * Parses a successful response body. Both supported APIs return the same
     * JSON shape, so the Gson default covers them; override only if a future
     * provider disagrees.
     */
    @Nullable
    protected TierProfile parseProfile(@NotNull String body) {
        return gson.fromJson(body, TierProfile.class);
    }

    /** The full profile URL for a player. Built from the configured base URL. */
    @NotNull
    protected URI buildProfileUri(@NotNull UUID uuid) {
        return URI.create(settings.getApiUrl() + "/profile/" + formatUuid(uuid));
    }

    /**
     * Translates a configured gamemode into this provider's slug, so one spawn
     * requirement can be evaluated against either site.
     */
    @NotNull
    public final String resolveGamemode(@Nullable String gamemode) {
        if (gamemode == null) {
            return "";
        }
        String normalized = gamemode.trim().toLowerCase(Locale.ROOT);
        return getGamemodeAliases().getOrDefault(normalized, normalized);
    }

    // ------------------------------------------------------------------
    // Shared lookup behaviour
    // ------------------------------------------------------------------

    /**
     * Resolves a player's profile, hitting the API only when nothing fresh is
     * cached.
     *
     * <p>
     * The future completes with {@code null} when the player has no profile on
     * this site, and completes exceptionally when the API could not be reached.
     */
    @NotNull
    public final CompletableFuture<TierProfile> fetchProfile(@NotNull UUID uuid) {
        CachedProfile cached = readCache(uuid);
        if (cached != null) {
            return cached.failed
                    ? CompletableFuture.failedFuture(
                            new IllegalStateException(getDisplayName() + " lookup recently failed"))
                    : CompletableFuture.completedFuture(cached.profile);
        }
        CompletableFuture<TierProfile> future = inFlight.computeIfAbsent(uuid, this::requestProfile);
        // Registered outside computeIfAbsent: removing the entry from within the
        // mapping function would be a recursive update on the same key.
        future.whenComplete((profile, error) -> inFlight.remove(uuid, future));
        return future;
    }

    /** Warms the cache for a player, ignoring the outcome. */
    public final void prefetch(@NotNull UUID uuid) {
        if (isCached(uuid)) {
            return;
        }
        fetchProfile(uuid).exceptionally(error -> null);
    }

    private CompletableFuture<TierProfile> requestProfile(UUID uuid) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(buildProfileUri(uuid))
                    .timeout(settings.getTimeout())
                    .header("Accept", "application/json")
                    .header("User-Agent", settings.getUserAgent())
                    .GET()
                    .build();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Invalid " + getDisplayName() + " API URL: " + settings.getApiUrl(), e);
            return CompletableFuture.failedFuture(e);
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        cacheFailure(uuid);
                        logger.log(Level.WARNING, getDisplayName() + " lookup failed for " + uuid
                                + ": " + error.getMessage());
                        throw new CompletionException(error);
                    }

                    int code = response.statusCode();
                    if (isNoProfileStatus(code)) {
                        // A real answer, not a failure: cache it like any other.
                        cacheProfile(uuid, null);
                        return null;
                    }
                    if (code < 200 || code >= 300) {
                        cacheFailure(uuid);
                        logger.log(Level.WARNING,
                                getDisplayName() + " lookup for " + uuid + " returned HTTP " + code);
                        throw new CompletionException(
                                new IllegalStateException(getDisplayName() + " returned HTTP " + code));
                    }

                    TierProfile profile;
                    try {
                        profile = parseProfile(response.body());
                    } catch (JsonParseException e) {
                        cacheFailure(uuid);
                        logger.log(Level.WARNING, "Malformed " + getDisplayName() + " response for " + uuid, e);
                        throw new CompletionException(e);
                    }
                    cacheProfile(uuid, profile);
                    return profile;
                });
    }

    // ------------------------------------------------------------------
    // Cache
    // ------------------------------------------------------------------

    /** The cached profile for a player, or null when nothing fresh is cached. */
    @Nullable
    public final TierProfile getCachedProfile(@NotNull UUID uuid) {
        CachedProfile cached = readCache(uuid);
        return cached == null ? null : cached.profile;
    }

    /** Whether a fresh entry for this UUID is cached, hit or miss. */
    public final boolean isCached(@NotNull UUID uuid) {
        return readCache(uuid) != null;
    }

    /** Whether the cached entry for this UUID records a failed lookup. */
    public final boolean isCachedFailure(@NotNull UUID uuid) {
        CachedProfile cached = readCache(uuid);
        return cached != null && cached.failed;
    }

    /**
     * Drops the cached profile for one player. Not called on quit on purpose:
     * entries are TTL'd, so keeping them means a reconnecting player costs no
     * extra request.
     */
    public final void invalidate(@NotNull UUID uuid) {
        cache.remove(uuid);
    }

    /** Drops every cached profile and returns how many entries were removed. */
    public final int clearCache() {
        int size = cache.size();
        cache.clear();
        return size;
    }

    /** Entries currently held, expired-but-not-yet-evicted ones included. */
    public final int getCacheSize() {
        return cache.size();
    }

    /** Clears all state. The HTTP client is owned by the caller, not closed here. */
    public final void shutdown() {
        cache.clear();
        inFlight.clear();
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
        if (settings.getCacheMillis() <= 0L) {
            return;
        }
        cache.put(uuid, new CachedProfile(profile, false, System.currentTimeMillis() + settings.getCacheMillis()));
    }

    private void cacheFailure(UUID uuid) {
        if (settings.getErrorCacheMillis() <= 0L) {
            return;
        }
        // Short-lived, so a blip does not lock players out (or in) for long.
        cache.put(uuid, new CachedProfile(null, true, System.currentTimeMillis() + settings.getErrorCacheMillis()));
    }

    /**
     * Seeds the cache directly. Exists so the behaviour built on top of the
     * cache can be tested without a network.
     */
    public final void seedCache(@NotNull UUID uuid, @Nullable TierProfile profile, boolean failed, long ttlMillis) {
        cache.put(uuid, new CachedProfile(profile, failed, System.currentTimeMillis() + ttlMillis));
    }

    @Override
    public String toString() {
        return getDisplayName() + "{" + settings.getApiUrl() + "}";
    }

    // ------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------

    /** HTTP and cache tuning, read from config and handed to a provider. */
    public static final class ProviderSettings {
        @Getter
        private final String apiUrl;
        @Getter
        private final Duration timeout;
        @Getter
        private final long cacheMillis;
        @Getter
        private final long errorCacheMillis;
        @Getter
        private final String userAgent;

        public ProviderSettings(@NotNull String apiUrl, @NotNull Duration timeout, long cacheMillis,
                long errorCacheMillis, @NotNull String userAgent) {
            this.apiUrl = apiUrl;
            this.timeout = timeout;
            this.cacheMillis = Math.max(0L, cacheMillis);
            this.errorCacheMillis = Math.max(0L, errorCacheMillis);
            this.userAgent = userAgent;
        }
    }

    /** A cached lookup. {@code profile} is null both for misses and failures. */
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

    /** Convenience for callers that want a plain callback instead of a future. */
    public final void fetchProfile(@NotNull UUID uuid, @NotNull Consumer<TierProfile> onSuccess,
            @NotNull Consumer<Throwable> onFailure) {
        fetchProfile(uuid).whenComplete((profile, error) -> {
            if (error != null) {
                onFailure.accept(error);
            } else {
                onSuccess.accept(profile);
            }
        });
    }
}
