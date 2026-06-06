package site.zvolcan.fFAUtils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.managers.StatsManager;

import java.util.Locale;

public final class FFAPlaceholders extends PlaceholderExpansion {

    private final FFAUtils plugin;
    private final StatsManager statsManager;

    public FFAPlaceholders(FFAUtils plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
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
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "kills" -> String.valueOf(statsManager.getKills(player.getUniqueId()));
            case "deaths" -> String.valueOf(statsManager.getDeaths(player.getUniqueId()));
            case "kdr" -> String.valueOf(statsManager.getKDR(player.getUniqueId()));
            default -> null;
        };
    }
}
