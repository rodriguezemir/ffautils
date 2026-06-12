package site.zvolcan.fFAUtils.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import site.zvolcan.fFAUtils.objects.Kit;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;

public class KitManager {

    private final JavaPlugin plugin;
    private final Map<String, Kit> kits = new HashMap<>();

    public KitManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Saves a kit to memory and persists to disk */
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

    /** Deletes a kit, removes its file, and persists the change */
    public boolean deleteKit(@NotNull String name) {
        if (name.isEmpty()) {
            return false;
        }

        boolean existed = kits.containsKey(name);
        if (existed) {
            kits.remove(name);

            File kitFile = new File(new File(plugin.getDataFolder(), "kits"), name + ".json");
            if (kitFile.exists()) {
                kitFile.delete();
            }

            persistKits();
        }
        return existed;
    }

    /** Loads all kits from the kits folder */
    public void loadAllKits() {
        kits.clear();

        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
            return;
        }

        File kitsFolder = new File(dataFolder, "kits");
        if (!kitsFolder.exists() || !kitsFolder.isDirectory()) {
            return;
        }

        File[] kitFiles = kitsFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (kitFiles == null) {
            return;
        }

        Gson gson = new GsonBuilder().create();
        Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();

        for (File kitFile : kitFiles) {
            String kitName = kitFile.getName().replace(".json", "");
            try (FileReader reader = new FileReader(kitFile)) {
                List<Map<String, Object>> itemsData = gson.fromJson(reader, listType);
                if (itemsData == null) {
                    plugin.getLogger().log(Level.WARNING, "Kit file is empty or malformed: " + kitFile.getName());
                    continue;
                }
                List<ItemStack> itemList = new ArrayList<>();
                for (Map<String, Object> data : itemsData) {
                    if (data == null) continue;
                    itemList.add(ItemStack.deserialize(normalizeTypes(data)));
                }
                kits.put(kitName, new Kit(kitName, itemList.toArray(new ItemStack[0])));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load kit file: " + kitFile.getName(), e);
            }
        }
    }

    /** Registers kits - loads all kits from file */
    public void registerKits() {
        loadAllKits();
    }

    /** Applies a kit to a player, auto-equipping armor pieces from contents */
    public void applyKit(@NotNull Player player, @NotNull Kit kit) {
        PlayerInventory inv = player.getInventory();
        inv.setContents(kit.getContents());
        equipArmorFromInventory(inv);
    }

    private void equipArmorFromInventory(@NotNull PlayerInventory inv) {
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            String typeName = item.getType().name();
            if (typeName.endsWith("_HELMET") && isEmptyOrNull(inv.getHelmet())) {
                inv.setHelmet(item);
                inv.setItem(i, null);
            } else if (typeName.endsWith("_CHESTPLATE") && isEmptyOrNull(inv.getChestplate())) {
                inv.setChestplate(item);
                inv.setItem(i, null);
            } else if (typeName.endsWith("_LEGGINGS") && isEmptyOrNull(inv.getLeggings())) {
                inv.setLeggings(item);
                inv.setItem(i, null);
            } else if (typeName.endsWith("_BOOTS") && isEmptyOrNull(inv.getBoots())) {
                inv.setBoots(item);
                inv.setItem(i, null);
            }
        }
    }

    private boolean isEmptyOrNull(@Nullable ItemStack item) {
        return item == null || item.getType().isAir();
    }

    /** Persists each kit to its own file in the kits folder */
    private void persistKits() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File kitsFolder = new File(dataFolder, "kits");
        if (!kitsFolder.exists()) {
            kitsFolder.mkdirs();
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        for (Map.Entry<String, Kit> entry : kits.entrySet()) {
            File kitFile = new File(kitsFolder, entry.getKey() + ".json");

            List<Map<String, Object>> itemsData = new ArrayList<>();
            for (ItemStack item : entry.getValue().getContents()) {
                itemsData.add(item == null ? null : item.serialize());
            }

            try (FileWriter writer = new FileWriter(kitFile)) {
                gson.toJson(itemsData, writer);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save kit file: " + kitFile.getName(), e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeTypes(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Double) {
                double d = (Double) value;
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    long l = (long) d;
                    result.put(key, l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (int) l : l);
                } else {
                    result.put(key, d);
                }
            } else if (value instanceof Map) {
                result.put(key, normalizeTypes((Map<String, Object>) value));
            } else if (value instanceof List) {
                List<Object> list = new ArrayList<>();
                for (Object element : (List<Object>) value) {
                    if (element instanceof Map) {
                        list.add(normalizeTypes((Map<String, Object>) element));
                    } else if (element instanceof Double) {
                        double d = (Double) element;
                        if (d == Math.floor(d) && !Double.isInfinite(d)) {
                            long l = (long) d;
                            list.add(l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (int) l : l);
                        } else {
                            list.add(d);
                        }
                    } else {
                        list.add(element);
                    }
                }
                result.put(key, list);
            } else {
                result.put(key, value);
            }
        }
        return result;
    }
}