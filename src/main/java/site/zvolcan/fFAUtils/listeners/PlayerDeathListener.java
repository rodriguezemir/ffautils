package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import site.zvolcan.fFAUtils.managers.DeathEventManager;

public class PlayerDeathListener implements Listener {

    private final DeathEventManager deathEventManager;

    public PlayerDeathListener(DeathEventManager deathEventManager) {
        this.deathEventManager = deathEventManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        final Player player = event.getPlayer();
        
        var randomEvent = deathEventManager.selectRandomEvent();
        if (randomEvent != null) {
            deathEventManager.executeDeathEvent(player, randomEvent);
        }
    }
}
