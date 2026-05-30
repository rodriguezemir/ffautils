package site.zvolcan.fFAUtils.managers;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class SpawnManager {

    private final JavaPlugin plugin;

    private final Map<String, Location> spawns = new HashMap<>();

    public SpawnManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Saves a spawn location to memory and persists to spawns.yml */
    public boolean saveSpawn(String name, Location location) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (location == null || location.getWorld() == null) {
            return false;
        }

        spawns.put(name, location);
        persistSpawns();
        return true;
    }

    /** Retrieves a spawn location by name */
    public Location getSpawn(String name) {
        return spawns.get(name);
    }

    /** Returns an immutable copy of all spawns */
    public Map<String, Location> getAllSpawns() {
        return Collections.unmodifiableMap(new HashMap<>(spawns));
    }

    /** Deletes a spawn and persists the change */
    public boolean deleteSpawn(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        boolean existed = spawns.containsKey(name);
        if (existed) {
            spawns.remove(name);
            persistSpawns();
        }
        return existed;
    }

    /** Loads all spawns from spawns.yml */
    public void loadAllSpawns() {
        spawns.clear();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
            return;
        }

        File spawnsFile = new File(dataFolder, "spawns.yml");
        if (!spawnsFile.exists()) {
            return;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(spawnsFile);
            if (config.contains("spawns")) {
                for (String key : config.getConfigurationSection("spawns").getKeys(false)) {
                    String path = "spawns." + key;
                    String worldName = config.getString(path + ".world");
                    if (worldName == null) continue;

                    org.bukkit.World world = plugin.getServer().getWorld(worldName);
                    if (world == null) continue;

                    double x = config.getDouble(path + ".x");
                    double y = config.getDouble(path + ".y");
                    double z = config.getDouble(path + ".z");
                    float yaw = (float) config.getDouble(path + ".yaw");
                    float pitch = (float) config.getDouble(path + ".pitch");

                    Location location = new Location(world, x, y, z, yaw, pitch);
                    spawns.put(key, location);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load spawns.yml", e);
        }
    }

    /** Registers spawns - loads all spawns from file */
    public void registerSpawns() {
        loadAllSpawns();
    }

    /** Persists spawns to spawns.yml */
    private void persistSpawns() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File spawnsFile = new File(dataFolder, "spawns.yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Location> entry : spawns.entrySet()) {
            Location loc = entry.getValue();
            String path = "spawns." + entry.getKey();
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getX());
            config.set(path + ".y", loc.getY());
            config.set(path + ".z", loc.getZ());
            config.set(path + ".yaw", loc.getYaw());
            config.set(path + ".pitch", loc.getPitch());
        }

        try {
            config.save(spawnsFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save spawns.yml", e);
        }
    }
}