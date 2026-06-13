package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.DeathEventManager;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.LobbyManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.managers.StatsManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;

public class PlayerDeathListener implements Listener {

    private final FFAUtils plugin;
    private final DeathEventManager deathEventManager;
    private final SpawnManager spawnManager;
    private final CombatLogManager combatLogManager;
    private final StatsManager statsManager;
    private final PlayersManager playersManager;
    private final LobbyManager lobbyManager;

    public PlayerDeathListener(FFAUtils plugin, DeathEventManager deathEventManager, SpawnManager spawnManager,
            CombatLogManager combatLogManager, StatsManager statsManager, PlayersManager playersManager,
            LobbyManager lobbyManager) {
        this.plugin = plugin;
        this.deathEventManager = deathEventManager;
        this.spawnManager = spawnManager;
        this.combatLogManager = combatLogManager;
        this.statsManager = statsManager;
        this.playersManager = playersManager;
        this.lobbyManager = lobbyManager;
    }

    static boolean isMilestone(int killstreak) {
        return killstreak > 0 && killstreak % 5 == 0;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getPlayer();
        event.deathMessage(null);
        deathEventManager.broadcastDeathEvent(player, player.getKiller());
        combatLogManager.removeFromCombat(player.getUniqueId());
        statsManager.addDeath(player.getUniqueId());

        FFAPlayer victimFfa = playersManager.getFFAPlayer(player);
        if (victimFfa.getState() != null) {
            if (victimFfa.getLastKit() != null && victimFfa.getLastSpawn() != null) {
                lobbyManager.markForRespawn(player.getUniqueId());
            }
        }

        if (victimFfa.getState() == site.zvolcan.fFAUtils.objects.PlayerState.IN_FFA) {
            int lostStreak = victimFfa.getKillstreak();
            if (lostStreak > 0) {
                FFAUtils.getInstance().getUtils().broadcast(false,
                        MessagesManager.getInstance().getMessage(
                                "killstreak-lost",
                                "{player}", player.getName(),
                                "{kills}", String.valueOf(lostStreak)));
            }
            victimFfa.setKillstreak(0);
        }

        if (player.getKiller() != null) {
            final Player killer = player.getKiller();
            killer.setHealth(20);
            combatLogManager.removeFromCombat(killer.getUniqueId());
            statsManager.addKill(killer.getUniqueId());

            FFAPlayer killerFfa = playersManager.getFFAPlayer(killer);
            if (killerFfa.getState() == site.zvolcan.fFAUtils.objects.PlayerState.IN_FFA) {
                int newStreak = killerFfa.getKillstreak() + 1;
                killerFfa.setKillstreak(newStreak);

                if (isMilestone(newStreak)) {
                    FFAUtils.getInstance().getUtils().broadcast(false,
                            MessagesManager.getInstance().getMessage(
                                    "killstreak-gained",
                                    "{player}", killer.getName(),
                                    "{kills}", String.valueOf(newStreak)));
                }

                Kit kit = killerFfa.getLastKit();
                if (kit != null) {
                    player.getInventory().setContents(kit.getContents());
                }
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(spawnManager.getLobbySpawn());
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            lobbyManager.addLobbyItems(player);
        }, 1L);
    }
}