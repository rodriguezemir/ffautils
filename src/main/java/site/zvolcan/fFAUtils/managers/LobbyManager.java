package site.zvolcan.fFAUtils.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public final class LobbyManager implements Listener {

    private final FFAUtils plugin;
    private final Map<Material, String> commands = new HashMap<>();

    public LobbyManager(@NotNull FFAUtils plugin) {
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

        loadItemsSection(player, config);
    }

    private File findItemsFile() {
        // Try data folder first
        File itemsFile = new File(plugin.getDataFolder(), "spawn-lobby-items.yml");
        if (itemsFile.exists()) {
            return itemsFile;
        }

        // Try resource in jar
        try {
            java.io.InputStream stream = plugin.getResource("spawn-lobby-items.yml");
            if (stream != null) {
                return new File(stream.toString());
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not load spawn-lobby-items.yml", e);
        }
        return null;
    }

    private void loadItemsSection(Player player, YamlConfiguration config) {
        if (!config.contains("items")) return;

        for (String key : Objects.requireNonNull(config.getConfigurationSection("items")).getKeys(false)) {
            try {
                Material mat = Material.getMaterial(config.getString("items" + "." + key + ".material", "COMPASS"));
                if (mat == null) return;
                int slot = Integer.parseInt(key);
                final ItemStack item = plugin.getUtils().ib(mat).
                        name(config.getString("items" + "." + key + ".name")).build();

                if (config.isString("items" + "." + key + ".command")) {
                    commands.put(mat, "items" + "." + key + ".command");
                }

                if (item != null) {
                    player.getInventory().setItem(slot, item);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().log(Level.WARNING, "Invalid slot key: " + key);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        final ItemStack item = event.getItem();
        if (item == null) return;

        if (commands.containsKey(item.getType())) {
            event.getPlayer().performCommand("/" + commands.get(item.getType()));
        }
    }
}