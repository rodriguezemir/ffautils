package site.zvolcan.fFAUtils.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatLogManager {

    private final FFAUtils plugin;
    private final long combatTimeoutTicks;
    private final Map<UUID, Long> combatEndTimes = new ConcurrentHashMap<>();
    private BukkitRunnable actionbarTask;

    public CombatLogManager(@NotNull FFAUtils plugin, long combatTimeoutTicks) {
        this.plugin = plugin;
        this.combatTimeoutTicks = combatTimeoutTicks;
    }

    /** Marks a player as in combat */
    public void setInCombat(@NotNull UUID playerId) {
        long currentTime = System.currentTimeMillis();
        Long time = combatEndTimes.put(playerId, currentTime + (combatTimeoutTicks * 50L));
        if (time == null)
            return;

        if (!combatEndTimes.containsKey(playerId)) {
            plugin.getUtils().message(
                    plugin.getServer().getPlayer(playerId),
                    false,
                    MessagesManager.getInstance().getMessage("combat-enter"));
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
                    MessagesManager.getInstance().getMessage("combat-exit"));
        }
        combatEndTimes.remove(uuid);
    }

    /** Starts the actionbar display task and cleanup task */
    public void startCleanupTask() {
        long intervalTicks = 20L;
        actionbarTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                var iterator = combatEndTimes.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<UUID, Long> entry = iterator.next();
                    long remaining = entry.getValue() - currentTime;
                    if (remaining <= 0) {
                        removeFromCombat(entry.getKey());
                        continue;
                    }
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        long seconds = (remaining + 999L) / 1000L;
                        String text = MessagesManager.getInstance().getMessage(
                                "combat-actionbar", "{seconds}", String.valueOf(seconds));
                        Component component = MiniMessage.miniMessage().deserialize(text);
                        player.sendActionBar(component);
                    }
                }
            }
        };
        actionbarTask.runTaskTimer(plugin, 0L, intervalTicks);
    }

    /** Stops the actionbar task */
    public void stopCleanupTask() {
        if (actionbarTask != null) {
            actionbarTask.cancel();
            actionbarTask = null;
        }
    }
}