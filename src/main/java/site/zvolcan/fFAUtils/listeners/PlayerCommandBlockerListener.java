package site.zvolcan.fFAUtils.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.BlockedCommandsManager;
import site.zvolcan.fFAUtils.managers.CombatLogManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;

/**
 * Cancels {@link PlayerCommandPreprocessEvent} for command labels that are
 * blocked for players currently in combat, unless the player holds the
 * {@code ffautils.bypass-combat-block} permission.
 */
public class PlayerCommandBlockerListener implements Listener {

    private static final String BYPASS_PERMISSION = "ffautils.bypass-combat-block";
    private static final String BLOCKED_MESSAGE_KEY = "combat-command-blocked";

    private final FFAUtils plugin;
    private final CombatLogManager combatLogManager;
    private final BlockedCommandsManager blockedCommandsManager;

    public PlayerCommandBlockerListener(@NotNull FFAUtils plugin) {
        this.plugin = plugin;
        this.combatLogManager = plugin.getCombatLogManager();
        this.blockedCommandsManager = plugin.getBlockedCommandsManager();
    }

    @EventHandler
    public void onPlayerCommand(@NotNull PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.isEmpty()) {
            return;
        }

        // Strip leading '/' and take the first whitespace-delimited token.
        String body = message.startsWith("/") ? message.substring(1) : message;
        int space = body.indexOf(' ');
        String label = (space == -1 ? body : body.substring(0, space)).toLowerCase();

        Player player = event.getPlayer();
        if (!combatLogManager.isInCombat(player.getUniqueId())) {
            return;
        }
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        if (!blockedCommandsManager.isBlocked(label)) {
            return;
        }

        event.setCancelled(true);
        String errorMsg = MessagesManager.getInstance().getMessage(BLOCKED_MESSAGE_KEY);
        plugin.getUtils().message(player, false, errorMsg);
    }
}
