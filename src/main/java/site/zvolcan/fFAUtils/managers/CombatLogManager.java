package site.zvolcan.fFAUtils.managers;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatLogManager {

    private final JavaPlugin plugin;
    private final long combatTimeoutTicks;
    private final Map<UUID, Long> combatEndTimes = new HashMap<>();

    public CombatLogManager(@NotNull JavaPlugin plugin, long combatTimeoutTicks) {
        this.plugin = plugin;
        this.combatTimeoutTicks = combatTimeoutTicks;
    }

    /** Marks a player as in combat */
    public void setInCombat(@NotNull UUID playerId) {
        combatEndTimes.put(playerId, System.currentTimeMillis() + (combatTimeoutTicks * 50L));
    }

    /** Checks if player is currently in combat */
    public boolean isInCombat(@NotNull UUID playerId) {
        Long endTime = combatEndTimes.get(playerId);
        if (endTime == null) {
            return false;
        }
        if (System.currentTimeMillis() > endTime) {
            combatEndTimes.remove(playerId);
            return false;
        }
        return true;
    }

    /** Removes player from combat tracking */
    public void removeFromCombat(@Nullable Player player) {
         if (player != null) {
             combatEndTimes.remove(player.getUniqueId());
         }
    }

    /** Starts the cleanup task to remove expired combat tags */
    public void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                combatEndTimes.entrySet().removeIf(entry -> entry.getValue() < currentTime);
            }
        }.runTaskTimer(plugin, combatTimeoutTicks, combatTimeoutTicks);
    }
}