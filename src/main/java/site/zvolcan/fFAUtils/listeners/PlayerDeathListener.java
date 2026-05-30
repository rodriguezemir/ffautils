package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.DeathEventManager;

public class PlayerDeathListener implements Listener {

    private final DeathEventManager deathEventManager;
    private final CombatLogManager combatLogManager;

    public PlayerDeathListener(DeathEventManager deathEventManager, CombatLogManager combatLogManager) {
        this.deathEventManager = deathEventManager;
        this.combatLogManager = combatLogManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getPlayer();
        deathEventManager.broadcastDeathEvent(player, player.getKiller());
        combatLogManager.removeFromCombat(player);
        combatLogManager.removeFromCombat(player);
    }
}
