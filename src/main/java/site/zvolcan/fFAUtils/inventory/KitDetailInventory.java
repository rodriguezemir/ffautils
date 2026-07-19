package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.util.ArrayList;
import java.util.List;

import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.countItems;
import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.playClick;
import static site.zvolcan.fFAUtils.inventory.KitEditorInventory.text;

/** Detail view of a single kit with edit, delete and back actions */
public class KitDetailInventory extends FastInv {

    private static final int SUMMARY_SLOT = 13;
    private static final int EDIT_SLOT = 11;
    private static final int DELETE_SLOT = 15;
    private static final int BACK_SLOT = 22;

    public KitDetailInventory(KitManager kitManager, String kitName) {
        super(27, "Kit: " + kitName);

        Kit kit = kitManager.getKit(kitName);

        ItemStack summary = new ItemStack(Material.CHEST);
        ItemMeta summaryMeta = summary.getItemMeta();
        summaryMeta.displayName(text("<white>" + kitName + "</white>"));
        List<Component> summaryLore = new ArrayList<>();
        summaryLore.add(text("<gray>" + (kit == null ? 0 : countItems(kit)) + " items</gray>"));
        summaryMeta.lore(summaryLore);
        summary.setItemMeta(summaryMeta);
        setItem(SUMMARY_SLOT, summary);

        ItemStack edit = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta editMeta = edit.getItemMeta();
        editMeta.displayName(text("<green>Edit contents</green>"));
        List<Component> editLore = new ArrayList<>();
        editLore.add(text("<gray>Edit this kit using your own inventory</gray>"));
        editMeta.lore(editLore);
        edit.setItemMeta(editMeta);
        setItem(EDIT_SLOT, edit, e -> {
            Player clicker = (Player) e.getWhoClicked();
            playClick(clicker);
            KitEditContentsInventory.open(FFAUtils.getInstance(), kitManager, clicker, kitName);
        });

        ItemStack delete = new ItemStack(Material.TNT);
        ItemMeta deleteMeta = delete.getItemMeta();
        deleteMeta.displayName(text("<red>Delete kit</red>"));
        List<Component> deleteLore = new ArrayList<>();
        deleteLore.add(text("<gray>Shift-click to confirm deletion</gray>"));
        deleteMeta.lore(deleteLore);
        delete.setItemMeta(deleteMeta);
        setItem(DELETE_SLOT, delete, e -> {
            Player clicker = (Player) e.getWhoClicked();
            // A plain click must never destroy a kit - only a shift-click confirms.
            if (!e.isShiftClick()) {
                FFAUtils.getInstance().getUtils().message(clicker, Sounds.ERROR_SOUND,
                        "<yellow>Shift-click to confirm deleting this kit.</yellow>");
                return;
            }
            playClick(clicker);
            kitManager.deleteKit(kitName);
            FFAUtils.getInstance().getUtils().message(clicker, Sounds.SUCCESS_SOUND,
                    "<green>Kit <white>" + kitName + "</white> deleted.</green>");
            new KitEditorInventory(kitManager, 0).open(clicker);
        });

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(text("<gray>Back</gray>"));
        back.setItemMeta(backMeta);
        setItem(BACK_SLOT, back, e -> {
            Player clicker = (Player) e.getWhoClicked();
            playClick(clicker);
            new KitEditorInventory(kitManager, 0).open(clicker);
        });
    }
}
