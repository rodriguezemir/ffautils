package site.zvolcan.fFAUtils.listeners;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.PlayerState;
import site.zvolcan.fFAUtils.objects.Region;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cancels movement that would take a player outside the region defined for
 * their current kit. Players with {@code ffautils.bypass-region} are exempt.
 */
public class PlayerRegionListener implements Listener {

    private static final String BYPASS_PERMISSION = "ffautils.bypass-region";
    private static final long MESSAGE_COOLDOWN_MS = 1500L;

    private final FFAUtils plugin;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public PlayerRegionListener(@NotNull FFAUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getLocation() == null ? event.getTo() : event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }

        FFAPlayer ffaPlayer = plugin.getPlayersManager().getFFAPlayer(player);
        if (ffaPlayer.getState() != PlayerState.IN_FFA) {
            return;
        }
        Kit kit = ffaPlayer.getLastKit();
        if (kit == null) {
            return;
        }
        Region region = plugin.getRegionManager().getRegion(kit.getName());
        if (region == null || region.contains(to)) {
            return;
        }

        event.setCancelled(true);
        notifyLimited(player);
    }

    private void notifyLimited(Player player) {
        long now = System.currentTimeMillis();
        Long previous = lastNotice.get(player.getUniqueId());
        if (previous != null && now - previous < MESSAGE_COOLDOWN_MS) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                MessagesManager.getInstance().getMessage("region-limit-reached"));
    }
}
