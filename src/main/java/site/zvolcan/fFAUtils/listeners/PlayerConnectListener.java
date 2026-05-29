package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.CombatLogManager;

public class PlayerConnectListener implements Listener {

    private final CombatLogManager combatLogManager;

    public PlayerConnectListener(@NotNull JavaPlugin plugin) {
        if (plugin instanceof FFAUtils) {
            this.combatLogManager = ((FFAUtils) plugin).getCombatLogManager();
        } else {
            throw new IllegalArgumentException("Plugin must be FFAUtils instance");
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
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
}