package site.zvolcan.fFAUtils.managers;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;
import site.zvolcan.fFAUtils.providers.McTiersProvider;
import site.zvolcan.fFAUtils.providers.PvpTiersProvider;
import site.zvolcan.fFAUtils.providers.TierProvider;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Blocks entry to configured spawns unless the player's PvP tier is good
 * enough.
 *
 * <p>
 * Rankings come from a {@link TierProvider} — MCTiers or PvPTiers — which owns
 * the HTTPS lookup, the Gson parsing and the per-UUID cache. This class is
 * concerned only with which provider to ask and what to do with the answer.
 *
 * <p>
 * A tier requirement is expressed the way both APIs report rankings: a tier
 * plus a position, so tier 3 position 0 means "HT3 or better". Whether the gate
 * is enforced at all is a runtime toggle backed by {@code tiers.enabled}.
 */
public final class TierManager {

    /** Provider selection meaning "let the player in if any provider allows it". */
    public static final String PROVIDER_ANY = "any";

    /**
     * Gamemode values that mean "judge the player on whichever gamemode they
     * rank highest in" instead of one specific gamemode.
     */
    private static final Set<String> ANY_GAMEMODE = Set.of("best", "any", "overall", "*");

    private final JavaPlugin plugin;

    /** Provider id -> provider. Insertion ordered so "any" has a stable order. */
    private final Map<String, TierProvider> providers = new LinkedHashMap<>();
    /** Lowercased spawn name -> tier requirement for that spawn. */
    private final Map<String, TierRequirement> restrictedSpawns = new ConcurrentHashMap<>();

    private HttpClient httpClient;

    @Getter
    private boolean enabled;
    @Getter
    private String defaultProvider = McTiersProvider.ID;
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
    @Getter
    private long cacheMillis = Duration.ofMinutes(30).toMillis();

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
        defaultGamemode = normalizeGamemode(section.getString("gamemode", "vanilla"));
        usePeakWhenRetired = section.getBoolean("use-peak-when-retired", true);
        allowOnError = section.getBoolean("allow-on-error", true);
        prefetchOnJoin = section.getBoolean("prefetch-on-join", true);
        bypassPermission = section.getString("bypass-permission", "ffautils.tiers.bypass");
        cacheMillis = Math.max(0L, section.getLong("cache-minutes", 30L)) * 60_000L;

        long errorCacheMillis = Math.max(0L, section.getLong("error-cache-minutes", 2L)) * 60_000L;
        Duration timeout = Duration.ofSeconds(Math.max(1L, section.getLong("timeout-seconds", 5L)));

        // The client is rebuilt because the connect timeout is baked into it.
        closeClient();
        httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        buildProviders(section, timeout, errorCacheMillis);

        String requested = normalizeId(section.getString("provider", McTiersProvider.ID));
        if (requested.equals(PROVIDER_ANY) || providers.containsKey(requested)) {
            defaultProvider = requested;
        } else {
            plugin.getLogger().log(Level.WARNING,
                    "Unknown tiers.provider '" + requested + "', falling back to " + McTiersProvider.ID);
            defaultProvider = McTiersProvider.ID;
        }

        loadRestrictedSpawns(section);
    }

    private void buildProviders(ConfigurationSection section, Duration timeout, long errorCacheMillis) {
        providers.values().forEach(TierProvider::shutdown);
        providers.clear();

        String userAgent = "FFAUtils/" + plugin.getDescription().getVersion();
        ConfigurationSection providersSection = section.getConfigurationSection("providers");

        registerProvider(new McTiersProvider(httpClient, plugin.getLogger(), providerSettings(
                providersSection, McTiersProvider.ID, McTiersProvider.DEFAULT_API_URL,
                timeout, errorCacheMillis, userAgent)));
        registerProvider(new PvpTiersProvider(httpClient, plugin.getLogger(), providerSettings(
                providersSection, PvpTiersProvider.ID, PvpTiersProvider.DEFAULT_API_URL,
                timeout, errorCacheMillis, userAgent)));
    }

    private TierProvider.ProviderSettings providerSettings(@Nullable ConfigurationSection providersSection,
            String id, String defaultUrl, Duration timeout, long errorCacheMillis, String userAgent) {
        String url = defaultUrl;
        if (providersSection != null) {
            url = providersSection.getString(id + ".api-url", defaultUrl);
        }
        return new TierProvider.ProviderSettings(
                stripTrailingSlash(url, defaultUrl), timeout, cacheMillis, errorCacheMillis, userAgent);
    }

    private void registerProvider(TierProvider provider) {
        providers.put(provider.getId(), provider);
    }

    private void loadRestrictedSpawns(ConfigurationSection section) {
        int defaultTier = clampTier(section.getInt("tier", 3));
        int defaultPos = clampPos(section.getInt("pos", TierRanking.HIGH));

        restrictedSpawns.clear();
        ConfigurationSection spawnsSection = section.getConfigurationSection("restricted-spawns");
        if (spawnsSection == null) {
            return;
        }
        for (String spawnName : spawnsSection.getKeys(false)) {
            ConfigurationSection spawnSection = spawnsSection.getConfigurationSection(spawnName);
            int tier = defaultTier;
            int pos = defaultPos;
            String gamemode = defaultGamemode;
            String provider = defaultProvider;
            if (spawnSection != null) {
                tier = clampTier(spawnSection.getInt("tier", defaultTier));
                pos = clampPos(spawnSection.getInt("pos", defaultPos));
                gamemode = normalizeGamemode(spawnSection.getString("gamemode", defaultGamemode));
                provider = normalizeId(spawnSection.getString("provider", defaultProvider));
                if (!provider.equals(PROVIDER_ANY) && !providers.containsKey(provider)) {
                    plugin.getLogger().log(Level.WARNING, "Unknown provider '" + provider + "' for spawn "
                            + spawnName + ", falling back to " + defaultProvider);
                    provider = defaultProvider;
                }
            }
            restrictedSpawns.put(spawnName.toLowerCase(Locale.ROOT),
                    new TierRequirement(spawnName, gamemode, tier, pos, provider));
        }
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

    /**
     * Seeds the message keys this manager uses. {@code messages.yml} is not
     * overwritten on update, so without this the tier messages would render as
     * "Message not found" on servers that already have the file.
     */
    public static void registerMessageDefaults(@NotNull MessagesManager messages) {
        messages.addDefault("tier-checking", "<gray>Verificando tu tier...");
        messages.addDefault("tier-blocked",
                "<red>Necesitas ser <white>{required}</white> o mejor en <white>{gamemode}</white> "
                        + "para entrar a este spawn. <gray>(tu tier en {provider}: {current})</gray>");
        messages.addDefault("tier-no-profile",
                "<red>No tienes un perfil en <white>{provider}</white>, no puedes entrar a este spawn.");
        messages.addDefault("tier-no-ranking",
                "<red>No estas rankeado en <white>{gamemode}</white> en <white>{provider}</white>, "
                        + "no puedes entrar a este spawn.");
        messages.addDefault("tier-lookup-failed",
                "<red>No se pudo verificar tu tier en <white>{provider}</white>. Intentalo de nuevo.");
        messages.addDefault("tiers-enabled", "<green>El bloqueo de spawns por tiers ha sido activado.");
        messages.addDefault("tiers-disabled", "<yellow>El bloqueo de spawns por tiers ha sido desactivado.");
        messages.addDefault("tiers-status",
                "<gray>Bloqueo por tiers: {status} <dark_gray>|</dark_gray> <gray>proveedor: "
                        + "<white>{provider}</white> <dark_gray>|</dark_gray> <gray>gamemode: "
                        + "<white>{gamemode}</white>");
        messages.addDefault("tiers-status-provider",
                "<dark_gray> - <white>{provider}</white> <gray>{url} <dark_gray>(cache: {cache})</dark_gray>");
        messages.addDefault("tiers-status-spawn",
                "<dark_gray> - <white>{spawn}</white> <gray>requiere <white>{required}</white> "
                        + "en <white>{gamemode}</white> <dark_gray>via</dark_gray> <white>{provider}</white>");
        messages.addDefault("tiers-status-no-spawns", "<gray>No hay spawns restringidos configurados.");
        messages.addDefault("tiers-reloaded", "<green>Configuracion de tiers recargada.");
        messages.addDefault("tiers-cache-cleared", "<green>Cache de tiers limpiada ({entries} entradas).");
        messages.addDefault("tiers-lookup",
                "<gray>{player} en <white>{provider}</white>: <white>{current}</white> "
                        + "en <white>{gamemode}</white>");
        messages.addDefault("tiers-lookup-none", "<red>{player} no tiene tiers en {provider}.");
        messages.addDefault("tiers-lookup-failed", "<red>No se pudo consultar el tier de {player} en {provider}.");
        messages.addDefault("tiers-player-not-found", "<red>El jugador {player} no esta conectado.");
        messages.addDefault("tiers-unknown-provider", "<red>Proveedor desconocido: {provider}.");
    }

    // ------------------------------------------------------------------
    // Providers
    // ------------------------------------------------------------------

    /** Every registered provider, keyed by id. */
    @NotNull
    public Map<String, TierProvider> getProviders() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(providers));
    }

    /** A provider by id, or null when no such provider is registered. */
    @Nullable
    public TierProvider getProvider(@Nullable String id) {
        return id == null ? null : providers.get(normalizeId(id));
    }

    /**
     * The providers a requirement is evaluated against: one specific provider,
     * or all of them when the requirement asks for {@link #PROVIDER_ANY}.
     */
    @NotNull
    public List<TierProvider> resolveProviders(@Nullable TierRequirement requirement) {
        String id = requirement == null ? defaultProvider : requirement.getProvider();
        if (PROVIDER_ANY.equals(id)) {
            return List.copyOf(providers.values());
        }
        TierProvider provider = providers.get(id);
        return provider == null ? List.of() : List.of(provider);
    }

    // ------------------------------------------------------------------
    // Spawn requirements
    // ------------------------------------------------------------------

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
     * When every provider involved already has the profile cached — the normal
     * case — the callback runs synchronously, before this method returns.
     * Otherwise the lookups run off the main thread and the callback is
     * scheduled once they complete.
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

        List<TierProvider> targets = resolveProviders(requirement);
        if (targets.isEmpty()) {
            plugin.getLogger().log(Level.WARNING,
                    "No tier provider available for spawn " + requirement.getSpawnName());
            callback.accept(new TierAccessResult(
                    allowOnError ? Status.ALLOWED_ON_ERROR : Status.DENIED_ERROR, requirement, null, null, null));
            return;
        }

        UUID uuid = player.getUniqueId();
        if (targets.stream().allMatch(provider -> provider.isCached(uuid))) {
            callback.accept(decideAcross(targets, uuid, requirement));
            return;
        }

        // Query every provider involved in parallel, then fold the answers.
        CompletableFuture<?>[] lookups = targets.stream()
                .map(provider -> provider.fetchProfile(uuid).exceptionally(error -> null))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(lookups)
                .whenComplete((ignored, error) -> runOnMain(
                        () -> callback.accept(decideAcross(targets, uuid, requirement))));
    }

    /**
     * Folds each provider's answer into one decision: the player gets in if any
     * provider allows it, otherwise the most informative denial is reported.
     */
    TierAccessResult decideAcross(List<TierProvider> targets, UUID uuid, TierRequirement requirement) {
        TierAccessResult best = null;
        for (TierProvider provider : targets) {
            TierAccessResult result = decideFor(provider, uuid, requirement);
            if (result.isAllowed()) {
                return result;
            }
            if (best == null || result.getStatus().denialRank() < best.getStatus().denialRank()) {
                best = result;
            }
        }
        return best == null
                ? new TierAccessResult(Status.DENIED_ERROR, requirement, null, null, null)
                : best;
    }

    /** Applies the requirement to one provider's cached answer. */
    private TierAccessResult decideFor(TierProvider provider, UUID uuid, TierRequirement requirement) {
        boolean failed = !provider.isCached(uuid) || provider.isCachedFailure(uuid);
        TierProfile profile = provider.getCachedProfile(uuid);
        String gamemode = isAnyGamemode(requirement.getGamemode())
                ? requirement.getGamemode()
                : provider.resolveGamemode(requirement.getGamemode());
        return decide(profile, failed, requirement, gamemode, usePeakWhenRetired, allowOnError, provider);
    }

    /**
     * Whether the access check for this spawn can be answered without any HTTP
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
        UUID uuid = player.getUniqueId();
        return resolveProviders(requirement).stream().allMatch(provider -> provider.isCached(uuid));
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
        return decide(profile, lookupFailed, requirement, requirement.getGamemode(),
                usePeakWhenRetired, allowOnError, null);
    }

    /**
     * As {@link #decide(TierProfile, boolean, TierRequirement, boolean, boolean)},
     * but against a gamemode slug already translated into the provider's own
     * vocabulary.
     */
    @NotNull
    public static TierAccessResult decide(@Nullable TierProfile profile, boolean lookupFailed,
            @NotNull TierRequirement requirement, @Nullable String resolvedGamemode, boolean usePeakWhenRetired,
            boolean allowOnError, @Nullable TierProvider provider) {
        if (lookupFailed) {
            return new TierAccessResult(
                    allowOnError ? Status.ALLOWED_ON_ERROR : Status.DENIED_ERROR,
                    requirement, null, null, provider);
        }
        if (profile == null) {
            return new TierAccessResult(Status.DENIED_NO_PROFILE, requirement, null, null, provider);
        }

        TierRanking ranking;
        String matchedGamemode;
        if (isAnyGamemode(resolvedGamemode)) {
            ranking = profile.getBestRanking(usePeakWhenRetired);
            matchedGamemode = profile.getBestGamemode(usePeakWhenRetired);
        } else {
            ranking = profile.getRanking(resolvedGamemode);
            matchedGamemode = resolvedGamemode;
        }

        if (ranking == null || !ranking.isValid()) {
            return new TierAccessResult(Status.DENIED_NO_RANKING, requirement, null, matchedGamemode, provider);
        }

        Status status = ranking.meets(requirement.getTier(), requirement.getPos(), usePeakWhenRetired)
                ? Status.ALLOWED
                : Status.DENIED_TIER;
        return new TierAccessResult(status, requirement, ranking, matchedGamemode, provider);
    }

    /** Whether a configured gamemode means "any gamemode". */
    public static boolean isAnyGamemode(@Nullable String gamemode) {
        return gamemode == null || gamemode.isEmpty() || ANY_GAMEMODE.contains(gamemode.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------
    // Cache plumbing
    // ------------------------------------------------------------------

    /** Warms every relevant provider's cache for a player. */
    public void prefetch(@NotNull UUID uuid) {
        if (!enabled || restrictedSpawns.isEmpty()) {
            return;
        }
        for (TierProvider provider : providersInUse()) {
            provider.prefetch(uuid);
        }
    }

    /** The providers actually referenced by at least one restricted spawn. */
    @NotNull
    public List<TierProvider> providersInUse() {
        List<TierProvider> used = new ArrayList<>();
        for (TierRequirement requirement : restrictedSpawns.values()) {
            for (TierProvider provider : resolveProviders(requirement)) {
                if (!used.contains(provider)) {
                    used.add(provider);
                }
            }
        }
        return used;
    }

    /** Drops the cached profile for one player across every provider. */
    public void invalidate(@NotNull UUID uuid) {
        providers.values().forEach(provider -> provider.invalidate(uuid));
    }

    /** Clears every provider's cache and returns the total entries dropped. */
    public int clearCache() {
        int total = 0;
        for (TierProvider provider : providers.values()) {
            total += provider.clearCache();
        }
        return total;
    }

    /** Total cached entries across every provider. */
    public int getCacheSize() {
        return providers.values().stream().mapToInt(TierProvider::getCacheSize).sum();
    }

    /** Releases the HTTP client and every provider's state. */
    public void shutdown() {
        providers.values().forEach(TierProvider::shutdown);
        providers.clear();
        closeClient();
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
            plugin.getLogger().log(Level.FINE, "Dropped tier callback: " + e.getMessage());
        }
    }

    private static String stripTrailingSlash(String url, String fallback) {
        String value = url == null || url.isBlank() ? fallback : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalizeGamemode(@Nullable String gamemode) {
        return gamemode == null ? "" : gamemode.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeId(@Nullable String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
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
        private final String provider;

        public TierRequirement(@NotNull String spawnName, @Nullable String gamemode, int tier, int pos) {
            this(spawnName, gamemode, tier, pos, McTiersProvider.ID);
        }

        public TierRequirement(@NotNull String spawnName, @Nullable String gamemode, int tier, int pos,
                @Nullable String provider) {
            this.spawnName = spawnName;
            this.gamemode = normalizeGamemode(gamemode);
            this.tier = clampTier(tier);
            this.pos = clampPos(pos);
            this.provider = normalizeId(provider);
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

        /** Provider id to check against, or {@link #PROVIDER_ANY}. */
        @NotNull
        public String getProvider() {
            return provider;
        }

        /** The requirement rendered the way both sites do, e.g. {@code HT3}. */
        @NotNull
        public String display() {
            return TierRanking.display(tier, pos);
        }

        @Override
        public String toString() {
            return "TierRequirement{" + spawnName + " >= " + display() + " in " + gamemode
                    + " via " + provider + "}";
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
        /** The player has a profile but no ranking in the required gamemode. */
        DENIED_NO_RANKING,
        /** The player has no profile with this provider. */
        DENIED_NO_PROFILE,
        /** The API was unreachable and the gate is configured to fail closed. */
        DENIED_ERROR;

        /**
         * How informative this denial is, lower being more informative. Used to
         * pick which provider's denial to show when several disagree: being told
         * your tier is too low beats being told you are not on the site.
         */
        int denialRank() {
            return switch (this) {
                case DENIED_TIER -> 0;
                case DENIED_NO_RANKING -> 1;
                case DENIED_NO_PROFILE -> 2;
                default -> 3;
            };
        }
    }

    /** An access decision plus the context needed to explain it to the player. */
    public static final class TierAccessResult {
        @Getter
        private final Status status;
        private final TierRequirement requirement;
        private final TierRanking ranking;
        private final String gamemode;
        private final TierProvider provider;

        public TierAccessResult(@NotNull Status status, @Nullable TierRequirement requirement,
                @Nullable TierRanking ranking, @Nullable String gamemode, @Nullable TierProvider provider) {
            this.status = status;
            this.requirement = requirement;
            this.ranking = ranking;
            this.gamemode = gamemode;
            this.provider = provider;
        }

        static TierAccessResult notRestricted() {
            return new TierAccessResult(Status.NOT_RESTRICTED, null, null, null, null);
        }

        static TierAccessResult bypass() {
            return new TierAccessResult(Status.BYPASS, null, null, null, null);
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

        /** The provider that answered, or null when none was consulted. */
        @Nullable
        public TierProvider getProvider() {
            return provider;
        }

        /** The provider's display name, or a dash when none was consulted. */
        @NotNull
        public String getProviderName() {
            return provider == null ? "-" : provider.getDisplayName();
        }

        @Override
        public String toString() {
            return "TierAccessResult{" + status + ", ranking=" + ranking
                    + ", provider=" + getProviderName() + "}";
        }
    }
}
