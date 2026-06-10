package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import site.zvolcan.fFAUtils.managers.SpawnManager;

import java.util.*;

public class SpawnsInventory extends FastInv {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int PAGE_START_SLOT = 0;
    private static final int BACK_SLOT = 49;
    private static final int PREV_PAGE_SLOT = 50;
    private static final int NEXT_PAGE_SLOT = 52;

    public SpawnsInventory(ConfigMenuManager configMenuManager, int requestedPage) {
        super(54, "Spawns - Page " + (requestedPage + 1));

        Map<String, SpawnManager.SpawnData> allSpawns = configMenuManager.getSpawnManager().getAllSpawnsData();
        List<Map.Entry<String, SpawnManager.SpawnData>> spawnList = new ArrayList<>(allSpawns.entrySet());

        int totalPages = Math.max(1, (int) Math.ceil((double) spawnList.size() / ITEMS_PER_PAGE));
        final int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, spawnList.size());

        if (spawnList.isEmpty()) {
            // Empty state placeholder
            ItemStack placeholder = new ItemStack(Material.BARRIER);
            ItemMeta placeholderMeta = placeholder.getItemMeta();
            placeholderMeta.setDisplayName("No spawns configured");
            placeholder.setItemMeta(placeholderMeta);
            setItem(22, placeholder);
        } else {
            for (int i = start; i < end; i++) {
                Map.Entry<String, SpawnManager.SpawnData> entry = spawnList.get(i);
                String name = entry.getKey();
                SpawnManager.SpawnData data = entry.getValue();
                World world = data.getLocation().getWorld();

                ItemStack item = new ItemStack(Material.COMPASS);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(name);
                List<String> lore = new ArrayList<>();
                lore.add(world != null ? world.getName() : "Unknown world");
                List<String> allowedKits = data.getAllowedKits();
                if (allowedKits != null && !allowedKits.isEmpty()) {
                    lore.add("Allowed kits: " + String.join(", ", allowedKits));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);

                int slot = PAGE_START_SLOT + (i - start);
                setItem(slot, item, e -> configMenuManager.openSpawnDetail((Player) e.getWhoClicked(), name));
            }
        }

        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("Back");
        backItem.setItemMeta(backMeta);
        setItem(BACK_SLOT, backItem, e -> configMenuManager.openMain((Player) e.getWhoClicked()));

        // Previous page button
        if (page > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.setDisplayName("Previous Page");
            prevItem.setItemMeta(prevMeta);
            setItem(PREV_PAGE_SLOT, prevItem, e -> configMenuManager.openSpawns((Player) e.getWhoClicked(), page - 1));
        }

        // Next page button
        if (page < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.setDisplayName("Next Page");
            nextItem.setItemMeta(nextMeta);
            setItem(NEXT_PAGE_SLOT, nextItem, e -> configMenuManager.openSpawns((Player) e.getWhoClicked(), page + 1));
        }
    }
}