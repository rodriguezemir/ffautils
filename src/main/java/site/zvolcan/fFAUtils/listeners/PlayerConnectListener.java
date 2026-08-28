package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.*;

public class PlayerConnectListener implements Listener {

    private final CombatLogManager combatLogManager;
    private final LobbyManager lobbyManager;
    private final PlayersManager playersManager;
    private final SpawnManager spawnManager;
    private final StatsManager statsManager;
    private final TierManager tierManager;
    private final Fto10Manager fto10Manager;

    public PlayerConnectListener(@NotNull FFAUtils plugin, LobbyManager lobbyManager, PlayersManager playersManager,
            SpawnManager spawnManager, StatsManager statsManager) {
        this.lobbyManager = lobbyManager;
        this.playersManager = playersManager;
        this.combatLogManager = plugin.getCombatLogManager();
        this.spawnManager = spawnManager;
        this.statsManager = statsManager;
        this.tierManager = plugin.getTierManager();
        this.fto10Manager = plugin.getFto10Manager();
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        if (fto10Manager != null) {
            fto10Manager.handleQuit(player);
        }
        playersManager.removePlayer(player);
        statsManager.unloadPlayer(player.getUniqueId());
        lobbyManager.clearPendingRespawn(player.getUniqueId());
        if (combatLogManager.isInCombat(player.getUniqueId())) {
            combatLogManager.removeFromCombat(player.getUniqueId());
            player.setHealth(0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player damaged) {
            if (fto10Manager != null && fto10Manager.shouldCancelDamage(damaged, event)) {
                event.setCancelled(true);
                return;
            }
            if (event.getDamager() instanceof Player damager) {
                combatLogManager.setInCombat(damaged.getUniqueId());
                combatLogManager.setInCombat(damager.getUniqueId());
            }
        }
    }

    @EventHandler
    public void joinPlayer(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        playersManager.createPlayer(player);
        lobbyManager.addLobbyItems(player);
        player.teleport(spawnManager.getLobbySpawn());
        // Warm the MCTiers cache so the spawn gate resolves without a round trip.
        if (tierManager != null && tierManager.isPrefetchOnJoin()) {
            tierManager.prefetch(player.getUniqueId(), player.getName());
        }
    }
}
