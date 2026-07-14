package site.zvolcan.fFAUtils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.StatsManager;
import site.zvolcan.fFAUtils.objects.PlayerState;

import java.util.Locale;

public final class FFAPlaceholders extends PlaceholderExpansion {

    private final FFAUtils plugin;
    private final StatsManager statsManager;
    private final PlayersManager playersManager;

    public FFAPlaceholders(FFAUtils plugin, StatsManager statsManager, PlayersManager playersManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.playersManager = playersManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ffa";
    }

    @Override
    public @NotNull String getAuthor() {
        return "volcqnn";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String lower = params.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "kills" -> String.valueOf(statsManager.getKills(player.getUniqueId()));
            case "deaths" -> String.valueOf(statsManager.getDeaths(player.getUniqueId()));
            case "kdr" -> String.valueOf(statsManager.getKDR(player.getUniqueId()));
            default -> {
                if (lower.startsWith("kit_") && lower.endsWith("_players")) {
                    String kitName = lower.substring(4, lower.length() - 8);
                    if (kitName.isEmpty()) yield null;
                    long count = playersManager.getAllPlayers().values().stream()
                            .filter(p -> p.getState() == PlayerState.IN_FFA
                                    && p.getLastKit() != null
                                    && p.getLastKit().getName().equalsIgnoreCase(kitName))
                            .count();
                    yield String.valueOf(count);
                }
                yield null;
            }
        };
    }
}
