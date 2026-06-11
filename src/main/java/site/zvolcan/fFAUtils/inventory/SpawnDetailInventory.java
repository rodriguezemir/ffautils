package site.zvolcan.fFAUtils.inventory;

import fr.mrmicky.fastinv.FastInv;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
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
            nameMeta.displayName(MiniMessage.miniMessage().deserialize("<white>" + spawnName + "</white>")
                    .decoration(TextDecoration.ITALIC, false));
            nameItem.setItemMeta(nameMeta);
            setItem(NAME_SLOT, nameItem);

            // Info item with world and coordinates
            ItemStack infoItem = new ItemStack(Material.PAPER);
            ItemMeta infoMeta = infoItem.getItemMeta();
            infoMeta.displayName(MiniMessage.miniMessage().deserialize("<gray>Spawn Info</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(MiniMessage.miniMessage()
                    .deserialize("<gray>World: " + (world != null ? world.getName() : "Unknown") + "</gray>")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage
                    .miniMessage().deserialize("<gray>"
                            + String.format("X: %.1f Y: %.1f Z: %.1f", loc.getX(), loc.getY(), loc.getZ()) + "</gray>")
                    .decoration(TextDecoration.ITALIC, false));

            List<String> allowedKits = spawnManager.getAllowedKits(spawnName);
            if (allowedKits != null && !allowedKits.isEmpty()) {
                lore.add(MiniMessage.miniMessage()
                        .deserialize("<gray>Allowed kits: " + String.join(", ", allowedKits) + "</gray>")
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(MiniMessage.miniMessage().deserialize("<gray>Allowed kits: All kits</gray>")
                        .decoration(TextDecoration.ITALIC, false));
            }
            infoMeta.lore(lore);
            infoItem.setItemMeta(infoMeta);
            setItem(INFO_SLOT, infoItem);
        }

        // Back button
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.displayName(
                MiniMessage.miniMessage().deserialize("<gray>Back</gray>").decoration(TextDecoration.ITALIC, false));
        backItem.setItemMeta(backMeta);
        setItem(BACK_SLOT, backItem, e -> {
            final Player player = (Player) e.getWhoClicked();
            player.playSound(player, Sound.UI_BUTTON_CLICK, 1, 1);
            configMenuManager.openSpawns(player, previousPage);
        });
    }
}