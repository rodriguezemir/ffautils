package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.objects.Kit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Paginated list of every configured kit, entry point of /ffakiteditor */
public class KitEditorInventory extends FastInv {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int PAGE_START_SLOT = 0;
    private static final int PREV_PAGE_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int EMPTY_PLACEHOLDER_SLOT = 22;

    public KitEditorInventory(KitManager kitManager, int requestedPage) {
        super(54, "Kit Editor");

        Map<String, Kit> allKits = kitManager.getAllKits();
        List<Map.Entry<String, Kit>> kitList = new ArrayList<>(allKits.entrySet());

        int totalPages = Math.max(1, (int) Math.ceil((double) kitList.size() / ITEMS_PER_PAGE));
        final int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, kitList.size());

        if (kitList.isEmpty()) {
            ItemStack placeholder = new ItemStack(Material.BARRIER);
            ItemMeta placeholderMeta = placeholder.getItemMeta();
            placeholderMeta.displayName(text("<red>No kits configured</red>"));
            placeholder.setItemMeta(placeholderMeta);
            setItem(EMPTY_PLACEHOLDER_SLOT, placeholder);
        } else {
            for (int i = start; i < end; i++) {
                Map.Entry<String, Kit> entry = kitList.get(i);
                final String name = entry.getKey();
                Kit kit = entry.getValue();

                ItemStack item = new ItemStack(Material.CHEST);
                ItemMeta meta = item.getItemMeta();
                meta.displayName(text("<white>" + name + "</white>"));
                List<Component> lore = new ArrayList<>();
                lore.add(text("<gray>" + countItems(kit) + " items</gray>"));
                lore.add(text("<yellow>Click to edit</yellow>"));
                meta.lore(lore);
                item.setItemMeta(meta);

                setItem(PAGE_START_SLOT + (i - start), item, e -> {
                    Player clicker = (Player) e.getWhoClicked();
                    playClick(clicker);
                    new KitDetailInventory(kitManager, name).open(clicker);
                });
            }
        }

        if (page > 0) {
            ItemStack prevItem = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevItem.getItemMeta();
            prevMeta.displayName(text("<gold>Previous Page</gold>"));
            prevItem.setItemMeta(prevMeta);
            setItem(PREV_PAGE_SLOT, prevItem, e -> {
                Player clicker = (Player) e.getWhoClicked();
                playClick(clicker);
                new KitEditorInventory(kitManager, page - 1).open(clicker);
            });
        }

        if (page < totalPages - 1) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            nextMeta.displayName(text("<gold>Next Page</gold>"));
            nextItem.setItemMeta(nextMeta);
            setItem(NEXT_PAGE_SLOT, nextItem, e -> {
                Player clicker = (Player) e.getWhoClicked();
                playClick(clicker);
                new KitEditorInventory(kitManager, page + 1).open(clicker);
            });
        }

        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.displayName(text("<red>Close</red>"));
        closeItem.setItemMeta(closeMeta);
        setItem(CLOSE_SLOT, closeItem, e -> {
            Player clicker = (Player) e.getWhoClicked();
            playClick(clicker);
            clicker.closeInventory();
        });
    }

    /** Counts the non-empty stacks stored in a kit */
    static int countItems(Kit kit) {
        int count = 0;
        for (ItemStack item : kit.getContents()) {
            if (item != null && !item.getType().isAir()) {
                count++;
            }
        }
        return count;
    }

    static Component text(String miniMessage) {
        return MiniMessage.miniMessage().deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    static void playClick(Player player) {
        player.playSound(player, Sound.UI_BUTTON_CLICK, 1, 1);
    }
}
