package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.DeathEventManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.managers.StatsManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;

public class PlayerDeathListener implements Listener {

    private final DeathEventManager deathEventManager;
    private final SpawnManager spawnManager;
    private final CombatLogManager combatLogManager;
    private final StatsManager statsManager;
    private final PlayersManager playersManager;

    public PlayerDeathListener(DeathEventManager deathEventManager, SpawnManager spawnManager, CombatLogManager combatLogManager, StatsManager statsManager, PlayersManager playersManager) {
        this.deathEventManager = deathEventManager;
        this.spawnManager = spawnManager;
        this.combatLogManager = combatLogManager;
        this.statsManager = statsManager;
        this.playersManager = playersManager;
    }

    /**
     * Checks if the given killstreak is a milestone (every 5 kills).
     */
    static boolean isMilestone(int killstreak) {
        return killstreak > 0 && killstreak % 5 == 0;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getPlayer();
        deathEventManager.broadcastDeathEvent(player, player.getKiller());
        combatLogManager.removeFromCombat(player.getUniqueId());
        statsManager.addDeath(player.getUniqueId());

        // Victim killstreak path
        FFAPlayer victimFfa = playersManager.getFFAPlayer(player);
        if (victimFfa.getState() == PlayerState.IN_FFA) {
            int lostStreak = victimFfa.getKillstreak();
            if (lostStreak > 0) {
                FFAUtils.getInstance().getUtils().broadcast(false,
                        MessagesManager.getInstance().getMessage(
                                "killstreak-lost",
                                "{player}", player.getName(),
                                "{kills}", String.valueOf(lostStreak)
                        )
                );
            }
            victimFfa.setKillstreak(0);
        }

        if (player.getKiller() != null) {
            final Player killer = player.getKiller();
            combatLogManager.removeFromCombat(killer.getUniqueId());
            statsManager.addKill(killer.getUniqueId());

            // Killer killstreak path
            FFAPlayer killerFfa = playersManager.getFFAPlayer(killer);
            if (killerFfa.getState() == PlayerState.IN_FFA) {
                int newStreak = killerFfa.getKillstreak() + 1;
                killerFfa.setKillstreak(newStreak);

                if (isMilestone(newStreak)) {
                    FFAUtils.getInstance().getUtils().broadcast(false,
                            MessagesManager.getInstance().getMessage(
                                    "killstreak-gained",
                                    "{player}", killer.getName(),
                                    "{kills}", String.valueOf(newStreak)
                            )
                    );
                }
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        event.setRespawnLocation(spawnManager.getLobbySpawn());
    }
}
