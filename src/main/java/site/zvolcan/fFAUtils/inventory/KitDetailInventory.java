package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.objects.Kit;

public class KitDetailInventory extends FastInv {

    private static final int NAME_SLOT = 4;
    private static final int CONTENTS_START_SLOT = 9;
    private static final int BACK_SLOT = 22;

    public KitDetailInventory(ConfigMenuManager configMenuManager, String kitName, int previousPage) {
        super(27, "Kit: " + kitName);

        KitManager kitManager = configMenuManager.getKitManager();
        Kit kit = kitManager.getKit(kitName);

        if (kit != null) {
            // Kit name display
            ItemStack nameItem = new ItemStack(Material.NAME_TAG);
            ItemMeta nameMeta = nameItem.getItemMeta();
            nameMeta.displayName(MiniMessage.miniMessage().deserialize("<white>" + kitName + "</white>").decoration(TextDecoration.ITALIC, false));
            nameItem.setItemMeta(nameMeta);
            setItem(NAME_SLOT, nameItem);

            // Kit contents as ItemStack previews (up to 18 items to fit in the grid)
            ItemStack[] contents = kit.getContents();
            int maxItems = Math.min(contents.length, 18);
            for (int i = 0; i < maxItems; i++) {
                if (contents[i] != null) {
                    int slot = CONTENTS_START_SLOT + (i % 9) + (i / 9) * 9;
                    if (slot < 22) { // Don't overwrite back button
                        setItem(slot, contents[i].clone());
                    }
                }
            }
        }

        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(MiniMessage.miniMessage().deserialize("<gray>Back</gray>").decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        setItem(BACK_SLOT, backItem, e -> configMenuManager.openKits((Player) e.getWhoClicked(), previousPage));
    }
}