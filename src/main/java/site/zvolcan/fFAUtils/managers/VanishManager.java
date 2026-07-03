package site.zvolcan.fFAUtils.managers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.PlayerState;

public class VanishManager implements Listener {

    private final FFAUtils plugin;
    private final PlayersManager playersManager;
    private BukkitRunnable refreshTask;

    public VanishManager(@NotNull FFAUtils plugin, @NotNull PlayersManager playersManager) {
        this.plugin = plugin;
        this.playersManager = playersManager;
    }

    /** Starts the periodic visibility refresh task */
    public void startRefreshTask() {
        long intervalTicks = 20L;
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshAll();
            }
        };
        refreshTask.runTaskTimer(plugin, 0L, intervalTicks);
    }

    /** Stops the periodic visibility refresh task */
    public void stopRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /** Recomputes visibility between every pair of online players */
    public void refreshAll() {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            refreshFor(viewer);
        }
    }

    /** Recomputes what a single player can see of every other online player */
    public void refreshFor(@NotNull Player viewer) {
        FFAPlayer viewerFfa = playersManager.getFFAPlayer(viewer);
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (target.equals(viewer))
                continue;

            if (canSee(viewerFfa, target)) {
                viewer.showPlayer(plugin, target);
            } else {
                viewer.hidePlayer(plugin, target);
            }
        }
    }

    private boolean canSee(@NotNull FFAPlayer viewerFfa, @NotNull Player target) {
        if (viewerFfa.getState() != PlayerState.IN_FFA)
            return true;

        FFAPlayer targetFfa = playersManager.getFFAPlayer(target);
        if (targetFfa.getState() != PlayerState.IN_FFA)
            return false;

        Kit viewerKit = viewerFfa.getLastKit();
        Kit targetKit = targetFfa.getLastKit();
        return viewerKit != null && targetKit != null && viewerKit.getName().equals(targetKit.getName());
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, this::refreshAll);
    }
}
