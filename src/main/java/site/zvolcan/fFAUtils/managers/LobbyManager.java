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
    private final Map<Integer, ItemStack> items = new HashMap<>();

    public LobbyManager(@NotNull FFAUtils plugin) {
        this.plugin = plugin;
        loadItemsSection();
    }

    /**
     * Clears player inventory and loads lobby items from spawn-lobby-items.yml.
     * Clears armor and restores it from config.
     */
    public void addLobbyItems(@NotNull Player player) {
        if (plugin.getConfig().getBoolean("disable-lobby-items", false)) return;
        player.getInventory().clear();
        loadPlayerItems(player);
    }

    public void loadPlayerItems(@NotNull Player player) {
        for (int i : items.keySet()) {
            final ItemStack item = items.get(i);

            if (item == null) continue;
            player.getInventory().setItem(i, item);
        }
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

    private void loadItemsSection() {
        File itemsFile = findItemsFile();
        if (itemsFile == null) {
            plugin.getLogger().log(Level.WARNING, "spawn-lobby-items.yml not found in data folder or resources");
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        if (!config.contains("items")) return;

        for (String key : Objects.requireNonNull(config.getConfigurationSection("items")).getKeys(false)) {
            try {
                Material mat = Material.getMaterial(config.getString("items" + "." + key + ".material", "COMPASS"));
                if (mat == null) return;
                int slot = Integer.parseInt(key);
                final ItemStack item = plugin.getUtils().ib(mat).
                        name(config.getString("items" + "." + key + ".name")).build();

                if (config.isString("items" + "." + key + ".command")) {
                    items.put(slot, item);
                    commands.put(item.getType(), config.getString("items" + "." + key + ".command"));
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