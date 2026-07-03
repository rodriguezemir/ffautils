package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class PlayerTeleportInventory extends FastInv {

    private static final int BACK_SLOT = 49;

    public PlayerTeleportInventory(Player viewer) {
        super(54, "<dark_gray>Teletransportarse</dark_gray>");

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(MiniMessage.miniMessage().deserialize("<reset>").decoration(TextDecoration.ITALIC, false));
        pane.setItemMeta(paneMeta);
        for (int slot = 45; slot < 54; slot++) {
            setItem(slot, pane);
        }

        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(viewer)) {
                targets.add(online);
            }
        }

        int slot = 0;
        for (Player target : targets) {
            if (slot >= 45)
                break;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(target);
            meta.displayName(MiniMessage.miniMessage().deserialize("<yellow>" + target.getName() + "</yellow>")
                    .decoration(TextDecoration.ITALIC, false));
            head.setItemMeta(meta);

            setItem(slot, head, e -> teleportToPlayer(viewer, target));
            slot++;
        }

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(MiniMessage.miniMessage().deserialize("<gray>Volver</gray>")
                .decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        setItem(BACK_SLOT, backItem, e -> new DeathChoiceInventory(
                site.zvolcan.fFAUtils.FFAUtils.getInstance(),
                site.zvolcan.fFAUtils.FFAUtils.getInstance().getSpawnManager(),
                site.zvolcan.fFAUtils.FFAUtils.getInstance().getPlayersManager(),
                site.zvolcan.fFAUtils.FFAUtils.getInstance().getLobbyManager()).open(viewer));
    }

    public static void teleportToRandomPlayer(Player viewer) {
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(viewer)) {
                targets.add(online);
            }
        }

        if (targets.isEmpty())
            return;

        Player target = targets.get((int) (Math.random() * targets.size()));
        teleportToPlayer(viewer, target);
    }

    private static void teleportToPlayer(Player viewer, Player target) {
        if (viewer.getGameMode() != GameMode.SPECTATOR)
            return;

        viewer.closeInventory();
        viewer.teleport(target.getLocation());
    }
}
