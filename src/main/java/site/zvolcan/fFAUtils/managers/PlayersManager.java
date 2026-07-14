package site.zvolcan.fFAUtils.managers;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayersManager {

    private final Map<UUID, FFAPlayer> players = new HashMap<>();

    public void createPlayer(@NotNull final Player player) {
        final FFAPlayer ffaPlayer = new FFAPlayer(player.getUniqueId());
        players.putIfAbsent(player.getUniqueId(), ffaPlayer);
    }

    @NotNull
    public FFAPlayer getFFAPlayer(final @NotNull Player player) {
        if (!players.containsKey(player.getUniqueId())) {
            createPlayer(player);
        }

        return players.get(player.getUniqueId());
    }

    public void removePlayer(@NotNull final Player player) {
        players.remove(player.getUniqueId());
    }

    public boolean isPlayerInFFA(@NotNull final Player player) {
        return getFFAPlayer(player).getState() == PlayerState.IN_FFA;
    }

    @NotNull
    public Map<UUID, FFAPlayer> getAllPlayers() {
        return Collections.unmodifiableMap(players);
    }

}
