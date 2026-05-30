package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.objects.Kit;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class KitManager {

    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new HashMap<>();

    public KitManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Saves a kit to memory and persists to kits.yml */
    public boolean saveKit(@NotNull String name, @NotNull Kit kit) {
        if (name.isEmpty()) {
            return false;
        }

        kits.put(name, kit);
        persistKits();
        return true;
    }

    /** Retrieves a kit by name */
    public Kit getKit(@NotNull String name) {
        return kits.get(name);
    }

    /** Returns an immutable copy of all kits */
    public Map<String, Kit> getAllKits() {
        return Collections.unmodifiableMap(new HashMap<>(kits));
    }

    /** Deletes a kit and persists the change */
    public boolean deleteKit(@NotNull String name) {
        if (name.isEmpty()) {
            return false;
        }

        boolean existed = kits.containsKey(name);
        if (existed) {
            kits.remove(name);
            persistKits();
        }
        return existed;
    }

    /** Loads all kits from kits.yml */
    public void loadAllKits() {
        kits.clear();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
            return;
        }

        File kitsFile = new File(dataFolder, "kits.yml");
        if (!kitsFile.exists()) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(kitsFile);
            if (config.contains("kits")) {
                for (String key : config.getConfigurationSection("kits").getKeys(false)) {
                    String path = "kits." + key + ".items";
                    Object itemStack = config.get(path);
                    if (itemStack instanceof ItemStack[]) {
                        kits.put(key, new Kit(key, (ItemStack[]) itemStack));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load kits.yml", e);
        }
    }

    /** Registers kits - loads all kits from file */
    public void registerKits() {
        loadAllKits();
    }

    /** Persists kits to kits.yml */
    private void persistKits() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File kitsFile = new File(dataFolder, "kits.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Kit> entry : kits.entrySet()) {
            String path = "kits." + entry.getKey() + ".items";
            config.set(path, entry.getValue().getContents());
        }

        try {
            config.save(kitsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save kits.yml", e);
        }
    }
}