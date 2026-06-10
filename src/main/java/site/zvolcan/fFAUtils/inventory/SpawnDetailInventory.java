package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import site.zvolcan.fFAUtils.managers.SpawnManager;

import java.util.ArrayList;
import java.util.List;

public class SpawnDetailInventory extends FastInv {

    private static final int NAME_SLOT = 4;
    private static final int INFO_SLOT = 13;
    private static final int BACK_SLOT = 22;

    public SpawnDetailInventory(ConfigMenuManager configMenuManager, String spawnName, int previousPage) {
        super(27, "Spawn: " + spawnName);

        SpawnManager spawnManager = configMenuManager.getSpawnManager();
        Location loc = spawnManager.getSpawn(spawnName);

        if (loc != null) {
            World world = loc.getWorld();

            // Spawn name display
            ItemStack nameItem = new ItemStack(Material.NAME_TAG);
            ItemMeta nameMeta = nameItem.getItemMeta();
            nameMeta.setDisplayName(spawnName);
            nameItem.setItemMeta(nameMeta);
            setItem(NAME_SLOT, nameItem);

            // Info item with world and coordinates
            ItemStack infoItem = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = infoItem.getItemMeta();
            infoMeta.setDisplayName("Spawn Info");
            List<String> lore = new ArrayList<>();
            lore.add("World: " + (world != null ? world.getName() : "Unknown"));
            lore.add(String.format("X: %.1f Y: %.1f Z: %.1f", loc.getX(), loc.getY(), loc.getZ()));

            List<String> allowedKits = spawnManager.getAllowedKits(spawnName);
            if (allowedKits != null && !allowedKits.isEmpty()) {
                lore.add("Allowed kits: " + String.join(", ", allowedKits));
            } else {
                lore.add("Allowed kits: All kits");
            }
            infoMeta.setLore(lore);
            infoItem.setItemMeta(infoMeta);
            setItem(INFO_SLOT, infoItem);
        }

        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("Back");
        backItem.setItemMeta(backMeta);
        setItem(BACK_SLOT, backItem, e -> configMenuManager.openSpawns((Player) e.getWhoClicked(), previousPage));
    }
}