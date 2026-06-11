package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import site.zvolcan.fFAUtils.objects.Kit;

import java.util.*;

public class KitsInventory extends FastInv {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int PAGE_START_SLOT = 0;
    private static final int BACK_SLOT = 49;
    private static final int PREV_PAGE_SLOT = 50;
    private static final int NEXT_PAGE_SLOT = 52;

    public KitsInventory(ConfigMenuManager configMenuManager, int requestedPage) {
        super(54, "Kits - Page " + (requestedPage + 1));

        Map<String, Kit> allKits = configMenuManager.getKitManager().getAllKits();
        List<Map.Entry<String, Kit>> kitList = new ArrayList<>(allKits.entrySet());

        int totalPages = Math.max(1, (int) Math.ceil((double) kitList.size() / ITEMS_PER_PAGE));
        final int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, kitList.size());

        if (kitList.isEmpty()) {
            // Empty state placeholder
            ItemStack placeholder = new ItemStack(Material.BARRIER);
            ItemMeta placeholderMeta = placeholder.getItemMeta();
            placeholderMeta.displayName(MiniMessage.miniMessage().deserialize("<red>No kits configured</red>").decoration(TextDecoration.ITALIC, false));
            placeholder.setItemMeta(placeholderMeta);
            setItem(22, placeholder);
        } else {
            for (int i = start; i < end; i++) {
                Map.Entry<String, Kit> entry = kitList.get(i);
                String name = entry.getKey();
                Kit kit = entry.getValue();

                ItemStack item = new ItemStack(Material.CHEST);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(MiniMessage.miniMessage().deserialize("<white>" + name + "</white>").decoration(TextDecoration.ITALIC, false));
                List<Component> lore = new ArrayList<>();
                lore.add(MiniMessage.miniMessage().deserialize("<gray>" + kit.getContents().length + " items</gray>").decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                item.setItemMeta(meta);

                int slot = PAGE_START_SLOT + (i - start);
                setItem(slot, item, e -> configMenuManager.openKitDetail((Player) e.getWhoClicked(), name, page));
            }
        }

        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(MiniMessage.miniMessage().deserialize("<gray>Back</gray>").decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        setItem(BACK_SLOT, backItem, e -> configMenuManager.openMain((Player) e.getWhoClicked()));

        // Previous page button
        if (page > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.displayName(MiniMessage.miniMessage().deserialize("<gold>Previous Page</gold>").decoration(TextDecoration.ITALIC, false));
            prevItem.setItemMeta(prevMeta);
            setItem(PREV_PAGE_SLOT, prevItem, e -> configMenuManager.openKits((Player) e.getWhoClicked(), page - 1));
        }

        // Next page button
        if (page < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.displayName(MiniMessage.miniMessage().deserialize("<gold>Next Page</gold>").decoration(TextDecoration.ITALIC, false));
            nextItem.setItemMeta(nextMeta);
            setItem(NEXT_PAGE_SLOT, nextItem, e -> configMenuManager.openKits((Player) e.getWhoClicked(), page + 1));
        }
    }
}