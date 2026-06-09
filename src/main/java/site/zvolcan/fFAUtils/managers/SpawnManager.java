package site.zvolcan.fFAUtils.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class SpawnManager {

    private final JavaPlugin plugin;

    private final Map<String, SpawnData> spawns = new HashMap<>();

    public SpawnManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Value object holding a spawn's location and optional allowed-kit restriction list.
     * Kit names are normalized to lowercase on construction.
     * Null or empty {@code allowedKits} means all kits are permitted.
     */
    public static class SpawnData {
        private final Location location;
        @Nullable
        private final List<String> allowedKits;

        public SpawnData(@NotNull Location location, @Nullable List<String> allowedKits) {
            this.location = location;
            this.allowedKits = allowedKits == null ? null :
                allowedKits.stream().map(String::toLowerCase).collect(Collectors.toList());
        }

        @NotNull
        public Location getLocation() { return location; }

        @Nullable
        public List<String> getAllowedKits() {
            return allowedKits == null ? null : Collections.unmodifiableList(allowedKits);
        }
    }

    /** Saves a spawn location to memory and persists to disk */
    public boolean saveSpawn(@NotNull String name, Location location) {
        if (name.isEmpty()) {
            return false;
        }
        if (location == null || location.getWorld() == null) {
            return false;
        }

        spawns.put(name, new SpawnData(location, null));
        persistSpawns();
        return true;
    }

    /** Retrieves a spawn location by name */
    public Location getSpawn(String name) {
        SpawnData data = spawns.get(name);
        return data == null ? null : data.getLocation();
    }

    /**
     * Returns an immutable copy of all spawns with full SpawnData.
     * Includes allowed-kits information where present.
     */
    public Map<String, SpawnData> getAllSpawnsData() {
        return Collections.unmodifiableMap(new HashMap<>(spawns));
    }

    /** Returns the allowed-kits for a spawn, or null if unrestricted */
    @Nullable
    public List<String> getAllowedKits(String name) {
        SpawnData data = spawns.get(name);
        return data == null ? null : data.getAllowedKits();
    }

    /** Adds a kit to the spawn's allowed list (lowercased). No-op if already present. */
    public void addAllowedKit(String spawnName, String kitName) {
        SpawnData data = spawns.get(spawnName);
        if (data == null) return;

        String lowerKit = kitName.toLowerCase();
        List<String> current = data.getAllowedKits();
        List<String> newList;
        if (current == null) {
            newList = new ArrayList<>();
        } else {
            newList = new ArrayList<>(current);
        }
        if (!newList.contains(lowerKit)) {
            newList.add(lowerKit);
        }
        spawns.put(spawnName, new SpawnData(data.getLocation(), newList));
        persistSpawns();
    }

    /** Removes a kit from the spawn's allowed list. No-op if not present. */
    public void removeAllowedKit(String spawnName, String kitName) {
        SpawnData data = spawns.get(spawnName);
        if (data == null) return;

        String lowerKit = kitName.toLowerCase();
        List<String> current = data.getAllowedKits();
        if (current == null) return;

        List<String> newList = new ArrayList<>(current);
        newList.remove(lowerKit);
        spawns.put(spawnName, new SpawnData(data.getLocation(), newList));
        persistSpawns();
    }

    /** Checks if a spawn exists by name */
    public boolean spawnExists(String name) {
        return spawns.containsKey(name);
    }

    /**
     * Pure containment check: determines if a kit is allowed at a spawn
     * based on the spawn's allowed-kits list.
     * @param allowedKits the spawn's allowed-kits (null or empty = all allowed)
     * @param kitName the kit name to check (lowercased internally)
     * @return true if the kit is allowed
     */
    public static boolean isKitAllowedAtSpawn(@Nullable List<String> allowedKits, String kitName) {
        if (allowedKits == null || allowedKits.isEmpty()) return true;
        return allowedKits.contains(kitName.toLowerCase());
    }

    /** Returns an immutable copy of all spawns (Location-only) */
    public Map<String, Location> getAllSpawns() {
        Map<String, Location> result = new HashMap<>();
        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getLocation());
        }
        return Collections.unmodifiableMap(result);
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
                @SuppressWarnings("unchecked")
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

                // Read allowed-kits if present (backward compatible — null means all kits allowed)
                List<String> allowedKits = null;
                if (data.containsKey("allowed-kits")) {
                    Object raw = data.get("allowed-kits");
                    if (raw instanceof List) {
                        allowedKits = new ArrayList<>();
                        for (Object item : (List<?>) raw) {
                            allowedKits.add(String.valueOf(item));
                        }
                    }
                }

                spawns.put(spawnName, new SpawnData(new Location(world, x, y, z, yaw, pitch), allowedKits));
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
        for (Map.Entry<String, SpawnData> entry : spawns.entrySet()) {
            File spawnFile = new File(spawnsFolder, entry.getKey() + ".json");
            SpawnData data = entry.getValue();
            Location loc = data.getLocation();

            Map<String, Object> jsonData = new LinkedHashMap<>();
            jsonData.put("world", loc.getWorld().getName());
            jsonData.put("x", loc.getX());
            jsonData.put("y", loc.getY());
            jsonData.put("z", loc.getZ());
            jsonData.put("yaw", (double) loc.getYaw());
            jsonData.put("pitch", (double) loc.getPitch());

            // Conditionally write allowed-kits only when non-null and non-empty
            List<String> allowedKits = data.getAllowedKits();
            if (allowedKits != null && !allowedKits.isEmpty()) {
                jsonData.put("allowed-kits", allowedKits);
            }

            try (FileWriter writer = new FileWriter(spawnFile)) {
                gson.toJson(jsonData, writer);
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