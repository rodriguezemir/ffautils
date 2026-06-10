package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MainInventory extends FastInv {

    public MainInventory(ConfigMenuManager configMenuManager) {
        super(27, "FFAUtils Config");
        // Spawns button
        ItemStack spawnsItem = new ItemStack(Material.COMPASS);
        ItemMeta spawnsMeta = spawnsItem.getItemMeta();
        spawnsMeta.setDisplayName("Spawns");
        spawnsItem.setItemMeta(spawnsMeta);
        setItem(11, spawnsItem, e -> configMenuManager.openSpawns((Player) e.getWhoClicked(), 0));
        // Kits button
        ItemStack kitsItem = new ItemStack(Material.CHEST);
        ItemMeta kitsMeta = kitsItem.getItemMeta();
        kitsMeta.setDisplayName("Kits");
        kitsItem.setItemMeta(kitsMeta);
        setItem(15, kitsItem, e -> configMenuManager.openKits((Player) e.getWhoClicked(), 0));
    }
}