package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MainInventory extends FastInv {

    public MainInventory(ConfigMenuManager configMenuManager) {
        super(27, "FFAUtils Config");
        // Spawns button
        ItemStack spawnsItem = new ItemStack(Material.COMPASS);
        ItemMeta spawnsMeta = spawnsItem.getItemMeta();
        spawnsMeta.displayName(MiniMessage.miniMessage().deserialize("<gradient:#5472F4:#27A2C1>Spawns</gradient>")
                .decoration(TextDecoration.ITALIC, false));
        spawnsItem.setItemMeta(spawnsMeta);
        setItem(11, spawnsItem, e -> {
            final Player player = (Player) e.getWhoClicked();
            player.playSound(player, Sound.UI_BUTTON_CLICK, 1, 1);
            configMenuManager.openSpawns(player, 0);
        });
        // Kits button
        ItemStack kitsItem = new ItemStack(Material.CHEST);
        ItemMeta kitsMeta = kitsItem.getItemMeta();
        kitsMeta.displayName(
                MiniMessage.miniMessage().deserialize("<green>Kits</green>").decoration(TextDecoration.ITALIC, false));
        kitsItem.setItemMeta(kitsMeta);
        setItem(15, kitsItem, e -> {
            final Player player = (Player) e.getWhoClicked();
            player.playSound(player, Sound.UI_BUTTON_CLICK, 1, 1);
            configMenuManager.openKits(player, 0);
        });
    }
}