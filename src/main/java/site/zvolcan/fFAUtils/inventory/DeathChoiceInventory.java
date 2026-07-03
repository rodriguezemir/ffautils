package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.eclipse.sisu.launch.Main;

import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.LobbyManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.PlayerState;

public class DeathChoiceInventory extends FastInv {

    private static final int RESPAWN_SLOT = 11;
    private static final int TELEPORT_SLOT = 13;
    private static final int RANDOM_TELEPORT_SLOT = 15;

    public DeathChoiceInventory(FFAUtils plugin, SpawnManager spawnManager, PlayersManager playersManager,
            LobbyManager lobbyManager) {
        super(27, "<dark_gray>Has muerto</dark_gray>");

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(MiniMessage.miniMessage().deserialize("<reset>").decoration(TextDecoration.ITALIC, false));
        pane.setItemMeta(paneMeta);
        for (int slot = 0; slot < 27; slot++) {
            if (slot == RESPAWN_SLOT || slot == TELEPORT_SLOT || slot == RANDOM_TELEPORT_SLOT)
                continue;
            setItem(slot, pane);
        }

        ItemStack respawnItem = new ItemStack(Material.WHITE_BED);
        ItemMeta respawnMeta = respawnItem.getItemMeta();
        respawnMeta.displayName(MiniMessage.miniMessage().deserialize("<green>Respawnear</green>")
                .decoration(TextDecoration.ITALIC, false));
        respawnItem.setItemMeta(respawnMeta);
        setItem(RESPAWN_SLOT, respawnItem, e -> {
            Player player = (Player) e.getWhoClicked();
            respawn(plugin, spawnManager, playersManager, lobbyManager, player);
        });

        ItemStack teleportItem = new ItemStack(Material.COMPASS);
        ItemMeta teleportMeta = teleportItem.getItemMeta();
        teleportMeta.displayName(MiniMessage.miniMessage().deserialize("<aqua>Espectear a un jugador</aqua>")
                .decoration(TextDecoration.ITALIC, false));
        teleportItem.setItemMeta(teleportMeta);
        setItem(TELEPORT_SLOT, teleportItem,
                e -> new PlayerTeleportInventory((Player) e.getWhoClicked()).open((Player) e.getWhoClicked()));

        ItemStack randomTeleportItem = new ItemStack(Material.ENDER_PEARL);
        ItemMeta randomTeleportMeta = randomTeleportItem.getItemMeta();
        randomTeleportMeta.displayName(MiniMessage.miniMessage()
                .deserialize("<red>Volver al Spawn</red>")
                .decoration(TextDecoration.ITALIC, false));
        randomTeleportItem.setItemMeta(randomTeleportMeta);
        setItem(RANDOM_TELEPORT_SLOT, randomTeleportItem, e -> {
            Player player = (Player) e.getWhoClicked();
            PlayerTeleportInventory.teleportToRandomPlayer(player);
        });
    }

    private static void respawn(FFAUtils plugin, SpawnManager spawnManager, PlayersManager playersManager,
            LobbyManager lobbyManager, Player player) {
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(spawnManager.getLobbySpawn());
        player.setHealth(20);
        player.setFoodLevel(20);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

        FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);
        ffaPlayer.setState(PlayerState.LOBBY);
        Kit kit = ffaPlayer.getLastKit();
        plugin.getKitManager().applyKit(player, kit);
        player.teleport(ffaPlayer.getLastSpawn() != null ? ffaPlayer.getLastSpawn() : spawnManager.getLobbySpawn());
    }
}
