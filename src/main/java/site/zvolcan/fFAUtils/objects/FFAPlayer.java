package site.zvolcan.fFAUtils.objects;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAUtils;

import java.util.UUID;

public final class FFAPlayer {

    @Getter
    private final UUID uuid;
    @Getter
    @Setter
    private Kit lastKit = null;
    @Getter
    @Setter
    private Location lastSpawn = null;
    @Getter
    @Setter
    private int kills = 0;
    @Getter
    @Setter
    private int deaths = 0;
    @Getter
    @Setter
    private PlayerState state = PlayerState.LOBBY;
    public FFAPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    public double getKDR() {
        if (deaths == 0) {
            return kills;
        }
        return (double) kills / deaths;
    }

    public void teleportToSpawn() {
        if (state == PlayerState.LOBBY) {
            return;
        }

        setState(PlayerState.LOBBY);
        final Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            FFAUtils.getInstance().getLobbyManager().addLobbyItems(player);
            player.teleport(FFAUtils.getInstance().getSpawnManager().getLobbySpawn());
        }
    }

}
