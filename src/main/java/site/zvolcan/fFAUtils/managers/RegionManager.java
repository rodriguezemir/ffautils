package site.zvolcan.fFAUtils.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.objects.Region;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class RegionManager {

    private static final String REGIONS_FILE = "regions.json";

    private final JavaPlugin plugin;
    private final Map<String, Region> regions = new HashMap<>();

    public RegionManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerRegions() {
        loadAllRegions();
    }

    /** Loads all kit regions from regions.json */
    public void loadAllRegions() {
        regions.clear();

        File file = new File(plugin.getDataFolder(), REGIONS_FILE);
        if (!file.exists()) {
            return;
        }

        Gson gson = new GsonBuilder().create();
        Type mapType = new TypeToken<Map<String, Region>>(){}.getType();
        try (FileReader reader = new FileReader(file)) {
            Map<String, Region> loaded = gson.fromJson(reader, mapType);
            if (loaded != null) {
                loaded.forEach((kit, region) -> {
                    if (region != null && region.getWorld() != null) {
                        regions.put(kit.toLowerCase(Locale.ROOT), region);
                    }
                });
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load " + REGIONS_FILE, e);
        }
    }

    /**
     * Saves the cuboid region for a kit from two corner locations.
     * Returns false if any location lacks a world or the kit name is empty.
     */
    public boolean setRegion(@NotNull String kitName, @NotNull Location pos1, @NotNull Location pos2) {
        if (kitName.isEmpty()) {
            return false;
        }
        if (pos1.getWorld() == null || pos2.getWorld() == null) {
            return false;
        }
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            return false;
        }

        regions.put(kitName.toLowerCase(Locale.ROOT), new Region(pos1, pos2));
        persistRegions();
        return true;
    }

    /** Returns the region for a kit, or null if none is defined */
    @Nullable
    public Region getRegion(@NotNull String kitName) {
        return regions.get(kitName.toLowerCase(Locale.ROOT));
    }

    public boolean hasRegion(@NotNull String kitName) {
        return regions.containsKey(kitName.toLowerCase(Locale.ROOT));
    }

    /** Deletes a kit's region and persists the change. Returns true if a region existed. */
    public boolean deleteRegion(@NotNull String kitName) {
        if (kitName.isEmpty()) {
            return false;
        }
        boolean existed = regions.remove(kitName.toLowerCase(Locale.ROOT)) != null;
        if (existed) {
            persistRegions();
        }
        return existed;
    }

    /** Returns an immutable copy of all kit regions keyed by lowercase kit name */
    @NotNull
    public Map<String, Region> getAllRegions() {
        return Collections.unmodifiableMap(new HashMap<>(regions));
    }

    /** Persists every region to regions.json */
    private void persistRegions() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        File file = new File(dataFolder, REGIONS_FILE);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(regions, writer);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save " + REGIONS_FILE, e);
        }
    }
}
