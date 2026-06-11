package site.zvolcan.fFAUtils.managers;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatLogManager {

    private final FFAUtils plugin;
    private final long combatTimeoutTicks;
    private final Map<UUID, Long> combatEndTimes = new HashMap<>();

    public CombatLogManager(@NotNull FFAUtils plugin, long combatTimeoutTicks) {
        this.plugin = plugin;
        this.combatTimeoutTicks = combatTimeoutTicks;
    }

    /** Marks a player as in combat */
    public void setInCombat(@NotNull UUID playerId) {
        long currentTime = System.currentTimeMillis();
        Long time = combatEndTimes.put(playerId, currentTime + (combatTimeoutTicks * 50L));
        if (time == null) return;

        if (currentTime < time) {
            plugin.getUtils().message(
                    plugin.getServer().getPlayer(playerId),
                    false,
                    MessagesManager.getInstance().getMessage("combat-enter")
            );
        }
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
    public void removeFromCombat(@NotNull UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline() && combatEndTimes.containsKey(uuid)) {
            plugin.getUtils().message(
                    player,
                    false,
                    MessagesManager.getInstance().getMessage("combat-exit")
            );
        }
        combatEndTimes.remove(uuid);
    }

    /** Starts the cleanup task to remove expired combat tags */
    public void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                for (Map.Entry<UUID, Long> entry : combatEndTimes.entrySet()) {
                    if (currentTime > entry.getValue()) {
                        removeFromCombat(entry.getKey());
                    }
                }
            }
        }.runTaskTimer(plugin, combatTimeoutTicks, combatTimeoutTicks);
    }
}