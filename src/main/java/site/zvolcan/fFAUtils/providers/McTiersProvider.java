package site.zvolcan.fFAUtils.providers;

import org.jetbrains.annotations.NotNull;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tier rankings from MCTiers (<a href="https://mctiers.com/docs/v2">v2 API</a>).
 *
 * <p>
 * Takes the UUID in its dashed form and answers HTTP 404 for a player it has
 * never tested.
 */
public final class McTiersProvider extends TierProvider {

    public static final String ID = "mctiers";
    public static final String DEFAULT_API_URL = "https://mctiers.com/api/v2";

    /**
     * PvPTiers slugs mapped onto MCTiers' vocabulary, so a single spawn
     * requirement can be pointed at either site. {@code crystal} has no MCTiers
     * equivalent and is deliberately absent — a player simply has no ranking
     * for it here.
     */
    private static final Map<String, String> GAMEMODE_ALIASES = Map.of(
            "neth_pot", "nethop",
            "nethpot", "nethop",
            "netherite", "nethop");

    public McTiersProvider(@NotNull HttpClient httpClient, @NotNull Logger logger,
            @NotNull ProviderSettings settings) {
        super(httpClient, logger, settings);
    }

    @Override
    @NotNull
    public String getId() {
        return ID;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return "MCTiers";
    }

    @Override
    @NotNull
    public String getDefaultApiUrl() {
        return DEFAULT_API_URL;
    }

    /** MCTiers expects the dashed UUID; the bare form is rejected. */
    @Override
    @NotNull
    protected String formatUuid(@NotNull UUID uuid) {
        return uuid.toString();
    }

    @Override
    protected boolean isNoProfileStatus(int statusCode) {
        return statusCode == 404;
    }

    @Override
    @NotNull
    protected Map<String, String> getGamemodeAliases() {
        return GAMEMODE_ALIASES;
    }
}
