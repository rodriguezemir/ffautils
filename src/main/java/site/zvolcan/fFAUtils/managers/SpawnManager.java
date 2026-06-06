package site.zvolcan.fFAUtils.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class SpawnManager {

    private final JavaPlugin plugin;

    private final Map<String, Location> spawns = new HashMap<>();

    public SpawnManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Saves a spawn location to memory and persists to disk */
    public boolean saveSpawn(@NotNull String name, Location location) {
        if (name.isEmpty()) {
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

    /** Deletes a spawn, removes its file, and persists the change */
    public boolean deleteSpawn(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        boolean existed = spawns.containsKey(name);
        if (existed) {
            spawns.remove(name);

            File spawnFile = new File(new File(plugin.getDataFolder(), "spawns"), name + ".json");
            if (spawnFile.exists()) {
                spawnFile.delete();
            }

            persistSpawns();
        }
        return existed;
    }

    /** Loads all spawns from the spawns folder */
    public void loadAllSpawns() {
        spawns.clear();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
            return;
        }

        File spawnsFolder = new File(dataFolder, "spawns");
        if (!spawnsFolder.exists() || !spawnsFolder.isDirectory()) {
            return;
        }

        File[] spawnFiles = spawnsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (spawnFiles == null) {
            return;
        }

        Gson gson = new GsonBuilder().create();

        for (File spawnFile : spawnFiles) {
            String spawnName = spawnFile.getName().replace(".json", "");
            try (FileReader reader = new FileReader(spawnFile)) {
                Map<String, Object> data = gson.fromJson(reader, Map.class);
                String worldName = (String) data.get("world");
                if (worldName == null) continue;

                org.bukkit.World world = plugin.getServer().getWorld(worldName);
                if (world == null) continue;

                double x = ((Number) data.get("x")).doubleValue();
                double y = ((Number) data.get("y")).doubleValue();
                double z = ((Number) data.get("z")).doubleValue();
                float yaw = ((Number) data.get("yaw")).floatValue();
                float pitch = ((Number) data.get("pitch")).floatValue();

                spawns.put(spawnName, new Location(world, x, y, z, yaw, pitch));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load spawn file: " + spawnFile.getName(), e);
            }
        }
    }

    /** Registers spawns - loads all spawns from file */
    public void registerSpawns() {
        loadAllSpawns();
    }

    /** Persists each spawn to its own file in the spawns folder */
    private void persistSpawns() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File spawnsFolder = new File(dataFolder, "spawns");
        if (!spawnsFolder.exists()) {
            spawnsFolder.mkdirs();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        for (Map.Entry<String, Location> entry : spawns.entrySet()) {
            File spawnFile = new File(spawnsFolder, entry.getKey() + ".json");
            Location loc = entry.getValue();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("world", loc.getWorld().getName());
            data.put("x", loc.getX());
            data.put("y", loc.getY());
            data.put("z", loc.getZ());
            data.put("yaw", (double) loc.getYaw());
            data.put("pitch", (double) loc.getPitch());

            try (FileWriter writer = new FileWriter(spawnFile)) {
                gson.toJson(data, writer);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save spawn file: " + spawnFile.getName(), e);
            }
        }
    }

    @NotNull
    public Location getLobbySpawn() {
        Location lobbySpawn = getSpawn("lobby");
        if (lobbySpawn == null) {
            lobbySpawn = plugin.getServer().getWorlds().getFirst().getSpawnLocation();
        }
        return lobbySpawn;
    }
}