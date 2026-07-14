package site.zvolcan.fFAUtils.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import net.kyori.adventure.text.minimessage.MiniMessage;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;

public class PlayerInteractiveListener implements Listener {

    private final PlayersManager playersManager;

    public PlayerInteractiveListener(PlayersManager playersManager) {
        this.playersManager = playersManager;
    }

    @EventHandler
    public void onPlayerPing(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof Player player) {
            event.getPlayer().sendActionBar(
                    MiniMessage.miniMessage().deserialize(
                            MessagesManager.getInstance().getMessage("interact-player-actionbar", "{player}",
                                    player.getName(), "{ping}", String.valueOf(player.getPing()))));
        }

    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (playersManager.getFFAPlayer(event.getPlayer()).getState() == PlayerState.LOBBY) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlock(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE)
            return;
        FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);

        if (ffaPlayer.getLastKit() == null)
            return;

        if (ffaPlayer.getLastKit().getName().equalsIgnoreCase("uhc")) {
            Bukkit.getScheduler().runTaskLater(FFAUtils.getInstance(), () -> {
                event.getBlock().setType(Material.AIR);
            }, 160);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE)
            return;
        FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);

        if (ffaPlayer.getLastKit() == null)
            return;

        if (ffaPlayer.getLastKit().getName().equalsIgnoreCase("uhc")) {
            Material mat = event.getBlock().getType();
            if (mat != Material.COBBLESTONE && mat != Material.COBWEB) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE)
            return;

        FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);

        if (ffaPlayer.getLastKit() == null)
            return;

        if (!ffaPlayer.getLastKit().getName().equalsIgnoreCase("uhc"))
            return;

        Material bucket = event.getBucket();

        if (bucket != Material.WATER_BUCKET && bucket != Material.LAVA_BUCKET)
            return;

        Block placedBlock = event.getBlockClicked().getRelative(event.getBlockFace());

        Bukkit.getScheduler().runTaskLater(FFAUtils.getInstance(), () -> {
            Material type = placedBlock.getType();

            if (type == Material.WATER || type == Material.LAVA) {
                placedBlock.setType(Material.AIR);
            }

            // Remover obsidiana y piedra generadas por el fluido
            for (Block neighbor : new Block[]{
                    placedBlock,
                    placedBlock.getRelative(0, 1, 0),
                    placedBlock.getRelative(0, -1, 0),
                    placedBlock.getRelative(1, 0, 0),
                    placedBlock.getRelative(-1, 0, 0),
                    placedBlock.getRelative(0, 0, 1),
                    placedBlock.getRelative(0, 0, -1)
            }) {
                Material neighborType = neighbor.getType();
                if (neighborType == Material.COBBLESTONE || neighborType == Material.OBSIDIAN) {
                    neighbor.setType(Material.AIR);
                }
            }
        }, 20L * 8); // 8 segundos
    }

    // Esto hace que cuando se cree un bloque de obsidiana
    // Por el contacto del agua con la lava, se elimine despues de un momento.
    @EventHandler
    public void onBlockCreate(BlockFromToEvent event) {
        Material mat = event.getBlock().getType();
        if (mat == Material.COBBLESTONE || mat == Material.OBSIDIAN) {
            Bukkit.getScheduler().runTaskLater(FFAUtils.getInstance(), () -> {
                event.getBlock().setType(Material.AIR);
            }, 20L * 8); // 8 segundos
        }
    }

}
