package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.LobbyManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;

public class PlayerConnectListener implements Listener {

    private final CombatLogManager combatLogManager;
    private final LobbyManager lobbyManager;
    private final PlayersManager playersManager;

    public PlayerConnectListener(@NotNull FFAUtils plugin, LobbyManager lobbyManager, PlayersManager playersManager) {
        this.lobbyManager = lobbyManager;
        this.playersManager = playersManager;
        this.combatLogManager = plugin.getCombatLogManager();
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        playersManager.removePlayer(player);
        if (combatLogManager.isInCombat(player.getUniqueId())) {
            player.setHealth(0);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player damaged && event.getDamager() instanceof Player) {
            combatLogManager.setInCombat(damaged.getUniqueId());
            combatLogManager.setInCombat(event.getDamager().getUniqueId());
        }
    }

    @EventHandler
    public void joinPlayer(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        playersManager.createPlayer(player);
        lobbyManager.addLobbyItems(player);
    }
}