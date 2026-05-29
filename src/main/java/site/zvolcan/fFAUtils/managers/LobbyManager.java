package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.logging.Level;

public final class LobbyManager {

    private final JavaPlugin plugin;

    public LobbyManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Clears player inventory and loads lobby items from spawn-lobby-items.yml.
     * Clears armor and restores it from config.
     */
    public void addLobbyItems(@NotNull Player player) {
        // Clear inventory and armor
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);

        File itemsFile = findItemsFile();
        if (itemsFile == null) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);

        loadItemsSection(player, config, "items");
        loadArmorSection(player, config);
    }

    private File findItemsFile() {
        // Try data folder first
        File itemsFile = new File(plugin.getDataFolder(), "spawn-lobby-items.yml");
        if (itemsFile.exists()) {
            return itemsFile;
        }

        // Try resource in jar
        try {
            java.net.URL resource = plugin.getClassLoader().getResource("spawn-lobby-items.yml");
            if (resource != null) {
                return new File(resource.toURI());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not load spawn-lobby-items.yml", e);
        }
        return null;
    }

    private void loadItemsSection(Player player, YamlConfiguration config, String section) {
        if (!config.contains(section)) return;

        for (String key : config.getConfigurationSection(section).getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                ItemStack item = config.getItemStack(section + "." + key);
                if (item != null) {
                    player.getInventory().setItem(slot, item);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid slot key: " + key);
            }
        }
    }

    private void loadArmorSection(Player player, YamlConfiguration config) {
        if (!config.contains("armor")) return;

        ItemStack helmet = config.getItemStack("armor.helmet");
        ItemStack chestplate = config.getItemStack("armor.chestplate");
        ItemStack leggings = config.getItemStack("armor.leggings");
        ItemStack boots = config.getItemStack("armor.boots");

        if (helmet != null) player.getInventory().setHelmet(helmet);
        if (chestplate != null) player.getInventory().setChestplate(chestplate);
        if (leggings != null) player.getInventory().setLeggings(leggings);
        if (boots != null) player.getInventory().setBoots(boots);
    }
}