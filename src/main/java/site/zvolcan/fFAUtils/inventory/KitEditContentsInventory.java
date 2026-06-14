package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.objects.Kit;

import java.util.HashSet;
import java.util.Set;

public class KitEditContentsInventory extends FastInv {

    private static final Set<Integer> EDITABLE_SLOTS = computeEditableSlots();

    private static final int ARMOR_ROW_START = 36;
    private static final int SAVE_SLOT = 43;
    private static final int CANCEL_SLOT = 44;

    private final KitManager kitManager;
    private final String kitName;

    private static Set<Integer> computeEditableSlots() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i <= 35; i++) {
            slots.add(i);
        }
        for (int i = 37; i <= 41; i++) {
            slots.add(i);
        }
        return slots;
    }

    public KitEditContentsInventory(ConfigMenuManager configMenuManager, String kitName) {
        super(54, "Editing - " + kitName);
        this.kitManager = configMenuManager.getKitManager();
        this.kitName = kitName;

        Kit kit = kitManager.getKit(kitName);

        if (kit != null) {
            ItemStack[] contents = kit.getContents();
            for (int i = 0; i < Math.min(contents.length, 36); i++) {
                if (contents[i] != null) {
                    getInventory().setItem(i, contents[i].clone());
                }
            }
            if (contents.length > 36) {
                for (int kitSlot = 36; kitSlot <= 40; kitSlot++) {
                    int displaySlot = kitSlot + 1;
                    if (kitSlot < contents.length && contents[kitSlot] != null) {
                        getInventory().setItem(displaySlot, contents[kitSlot].clone());
                    }
                }
            }
        }

        ItemStack glassPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glassPane.getItemMeta();
        glassMeta.displayName(MiniMessage.miniMessage().deserialize("<dark_gray> </dark_gray>").decoration(TextDecoration.ITALIC, false));
        glassPane.setItemMeta(glassMeta);

        setItem(ARMOR_ROW_START, glassPane);
        setItem(42, glassPane);
        setItems(45, 54, glassPane);

        setArmorSlot(37, Material.IRON_HELMET, "<white>Helmet</white>", "<gray>Drop a helmet here</gray>");
        setArmorSlot(38, Material.IRON_CHESTPLATE, "<white>Chestplate</white>", "<gray>Drop a chestplate here</gray>");
        setArmorSlot(39, Material.IRON_LEGGINGS, "<white>Leggings</white>", "<gray>Drop leggings here</gray>");
        setArmorSlot(40, Material.IRON_BOOTS, "<white>Boots</white>", "<gray>Drop boots here</gray>");
        setArmorSlot(41, Material.SHIELD, "<white>Offhand</white>", "<gray>Drop an offhand item here</gray>");

        ItemStack saveItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta saveMeta = saveItem.getItemMeta();
        saveMeta.displayName(MiniMessage.miniMessage().deserialize("<green><bold>Save</bold></green>").decoration(TextDecoration.ITALIC, false));
        saveItem.setItemMeta(saveMeta);
        setItem(SAVE_SLOT, saveItem, e -> {
            e.setCancelled(true);
            saveKit((Player) e.getWhoClicked());
        });

        ItemStack cancelItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.displayName(MiniMessage.miniMessage().deserialize("<red><bold>Cancel</bold></red>").decoration(TextDecoration.ITALIC, false));
        cancelItem.setItemMeta(cancelMeta);
        setItem(CANCEL_SLOT, cancelItem, e -> {
            e.setCancelled(true);
            Player clicker = (Player) e.getWhoClicked();
            clicker.playSound(clicker, Sound.UI_BUTTON_CLICK, 1, 1);
            clicker.closeInventory();
        });

        addClickHandler(this::handleClick);
        addDragHandler(this::handleDrag);
    }

    private void setArmorSlot(int slot, Material defaultMat, String title, String description) {
        if (getInventory().getItem(slot) != null && !getInventory().getItem(slot).getType().isAir()) {
            return;
        }
        ItemStack placeholder = new ItemStack(defaultMat);
        ItemMeta meta = placeholder.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(title).decoration(TextDecoration.ITALIC, false));
        java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(MiniMessage.miniMessage().deserialize(description).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        placeholder.setItemMeta(meta);
        getInventory().setItem(slot, placeholder);
    }

    private void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        if (rawSlot < 0 || rawSlot >= getInventory().getSize()) {
            return;
        }

        if (!EDITABLE_SLOTS.contains(rawSlot)) {
            event.setCancelled(true);
        }
    }

    private void handleDrag(InventoryDragEvent event) {
        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < getInventory().getSize() && !EDITABLE_SLOTS.contains(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void saveKit(Player player) {
        ItemStack[] contents = new ItemStack[41];

        for (int i = 0; i < 36; i++) {
            ItemStack item = getInventory().getItem(i);
            contents[i] = isPlaceholder(item) ? null : item;
        }

        contents[36] = getArmorSlot(37);
        contents[37] = getArmorSlot(38);
        contents[38] = getArmorSlot(39);
        contents[39] = getArmorSlot(40);
        contents[40] = getArmorSlot(41);

        Kit kit = new Kit(kitName, contents);
        kitManager.saveKit(kitName, kit);

        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        player.sendMessage(MessagesManager.getInstance().getMessage("kit-edited", "{name}", kitName));
        player.closeInventory();
    }

    private ItemStack getArmorSlot(int displaySlot) {
        ItemStack item = getInventory().getItem(displaySlot);
        if (item == null || item.getType().isAir()) {
            return null;
        }
        int kitSlot = displaySlot - 1;
        if (kitSlot < 36 || kitSlot > 40) {
            return item;
        }
        Material defaultMat = getDefaultMaterial(kitSlot);
        if (item.getType() == defaultMat && item.getItemMeta() != null
                && item.getItemMeta().lore() != null
                && !item.getItemMeta().lore().isEmpty()) {
            return null;
        }
        return item;
    }

    private boolean isPlaceholder(ItemStack item) {
        if (item == null) return false;
        return item.getType() == Material.BLACK_STAINED_GLASS_PANE
                || item.getType() == Material.LIME_STAINED_GLASS_PANE
                || item.getType() == Material.RED_STAINED_GLASS_PANE;
    }

    private static Material getDefaultMaterial(int kitSlot) {
        switch (kitSlot) {
            case 36: return Material.IRON_HELMET;
            case 37: return Material.IRON_CHESTPLATE;
            case 38: return Material.IRON_LEGGINGS;
            case 39: return Material.IRON_BOOTS;
            case 40: return Material.SHIELD;
            default: return Material.AIR;
        }
    }
}