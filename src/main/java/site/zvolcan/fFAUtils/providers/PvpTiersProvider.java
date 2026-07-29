package site.zvolcan.fFAUtils.providers;

import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Tier rankings from PvPTiers ({@code https://pvptiers.com/api/profile/<uuid>}).
 *
 * <p>
 * Two things set it apart from MCTiers: it wants the UUID as bare hex — the
 * dashed form is rejected with HTTP 400 — and it reports an unknown player as
 * HTTP 422 rather than 404. Its JSON payload otherwise matches, so the inherited
 * Gson parsing is reused as-is.
 */
public final class PvpTiersProvider extends TierProvider {

    public static final String ID = "pvptiers";
    public static final String DEFAULT_API_URL = "https://pvptiers.com/api";

    /**
     * MCTiers slugs mapped onto PvPTiers' vocabulary. {@code vanilla} has no
     * PvPTiers equivalent and is deliberately absent — a player simply has no
     * ranking for it here.
     */
    private static final Map<String, String> GAMEMODE_ALIASES = Map.of(
            "nethop", "neth_pot",
            "nethpot", "neth_pot",
            "netherite", "neth_pot");

    public PvpTiersProvider(@NotNull HttpClient httpClient, @NotNull Logger logger,
            @NotNull ProviderSettings settings) {
        super(httpClient, logger, settings);
    }

    public PvpTiersProvider(@NotNull HttpClient httpClient, @NotNull Logger logger,
            @NotNull ProviderSettings settings, @NotNull Executor executor) {
        super(httpClient, logger, settings, executor);
    }

    @Override
    @NotNull
    public String getId() {
        return ID;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return "PvPTiers";
    }

    @Override
    @NotNull
    public String getDefaultApiUrl() {
        return DEFAULT_API_URL;
    }

    /** PvPTiers rejects the dashed UUID with HTTP 400, so strip the dashes. */
    @Override
    @NotNull
    protected String formatUuid(@NotNull UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    /** PvPTiers signals "no such player" with 422 rather than 404. */
    @Override
    protected boolean isNoProfileStatus(int statusCode) {
        return statusCode == 422 || statusCode == 404;
    }

    @Override
    @NotNull
    protected Map<String, String> getGamemodeAliases() {
        return GAMEMODE_ALIASES;
    }
}
