package site.zvolcan.fFAUtils.listeners;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.inventory.DeathChoiceInventory;
import site.zvolcan.fFAUtils.managers.LobbyManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;

public class PlayerTotemDeathListener implements Listener {

    private static final long GUI_DELAY_TICKS = 60L;

    private final FFAUtils plugin;
    private final SpawnManager spawnManager;
    private final PlayersManager playersManager;
    private final LobbyManager lobbyManager;

    public PlayerTotemDeathListener(FFAUtils plugin, SpawnManager spawnManager, PlayersManager playersManager,
            LobbyManager lobbyManager) {
        this.plugin = plugin;
        this.spawnManager = spawnManager;
        this.playersManager = playersManager;
        this.lobbyManager = lobbyManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player))
            return;
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
            return;
        if (event.getFinalDamage() < player.getHealth())
            return;
        if (hasTotem(player))
            return;

        event.setCancelled(true);
        player.setGameMode(GameMode.SPECTATOR);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && player.getGameMode() == GameMode.SPECTATOR) {
                new DeathChoiceInventory(plugin, spawnManager, playersManager, lobbyManager).open(player);
            }
        }, GUI_DELAY_TICKS);
    }

    private boolean hasTotem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return mainHand.getType() == Material.TOTEM_OF_UNDYING || offHand.getType() == Material.TOTEM_OF_UNDYING;
    }
}
