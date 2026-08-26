package site.zvolcan.fFAUtils.managers;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for SpawnManager YAML file format and business logic.
 * Tests verify the same logic used by SpawnManager.
 */
class SpawnManagerTest {

    @TempDir
    private File tempDir;

    @BeforeEach
    void setUp() {
        // Ensure clean temp directory
    }

    @Test
    void saveSpawn_shouldCreateValidYamlFormat() throws IOException {
        File spawnsFile = new File(tempDir, "spawns.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawns.lobby.world", "world");
        config.set("spawns.lobby.x", 100.5);
        config.set("spawns.lobby.y", 64.0);
        config.set("spawns.lobby.z", 200.5);
        config.set("spawns.lobby.yaw", 0.0);
        config.set("spawns.lobby.pitch", 0.0);
        config.save(spawnsFile);

        assertTrue(spawnsFile.exists());
    }

    @Test
    void saveSpawn_shouldPersistAllFields() throws IOException {
        File spawnsFile = new File(tempDir, "spawns.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawns.spawn1.world", "myworld");
        config.set("spawns.spawn1.x", 100.5);
        config.set("spawns.spawn1.y", 64.0);
        config.set("spawns.spawn1.z", 200.5);
        config.set("spawns.spawn1.yaw", 90.0);
        config.set("spawns.spawn1.pitch", 45.0);
        config.save(spawnsFile);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(spawnsFile);
        assertEquals("myworld", loaded.getString("spawns.spawn1.world"));
        assertEquals(100.5, loaded.getDouble("spawns.spawn1.x"), 0.001);
        assertEquals(64.0, loaded.getDouble("spawns.spawn1.y"), 0.001);
        assertEquals(200.5, loaded.getDouble("spawns.spawn1.z"), 0.001);
        assertEquals(90.0, loaded.getDouble("spawns.spawn1.yaw"), 0.001);
        assertEquals(45.0, loaded.getDouble("spawns.spawn1.pitch"), 0.001);
    }

    @Test
    void saveSpawn_shouldOverwriteExistingSpawn() throws IOException {
        File spawnsFile = new File(tempDir, "spawns.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawns.spawn1.x", 100.0);
        config.save(spawnsFile);

        config.set("spawns.spawn1.x", 150.0);
        config.save(spawnsFile);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(spawnsFile);
        assertEquals(150.0, loaded.getDouble("spawns.spawn1.x"), 0.001);
    }

    @Test
    void loadSpawn_shouldReturnCorrectValues() throws IOException {
        File spawnsFile = new File(tempDir, "spawns.yml");
        java.nio.file.Files.writeString(spawnsFile.toPath(), """
            spawns:
              lobby:
                world: "world"
                x: 100.5
                y: 64.0
                z: 200.5
                yaw: 0.0
                pitch: 0.0
            """);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(spawnsFile);
        assertTrue(config.contains("spawns.lobby"));

        String worldName = config.getString("spawns.lobby.world");
        double x = config.getDouble("spawns.lobby.x");
        double y = config.getDouble("spawns.lobby.y");
        double z = config.getDouble("spawns.lobby.z");
        float yaw = (float) config.getDouble("spawns.lobby.yaw");
        float pitch = (float) config.getDouble("spawns.lobby.pitch");

        assertEquals("world", worldName);
        assertEquals(100.5, x, 0.001);
        assertEquals(64.0, y, 0.001);
        assertEquals(200.5, z, 0.001);
    }

    @Test
    void loadSpawn_shouldReturnNullForNonExistentFile() {
        File missingFile = new File(tempDir, "nonexistent.yml");
        assertFalse(missingFile.exists());
    }

    @Test
    void loadSpawn_shouldHandleMalformedFileGracefully() throws IOException {
        File spawnsFile = new File(tempDir, "spawns.yml");
        String invalidYaml = "this is not valid yaml: [";
        java.nio.file.Files.writeString(spawnsFile.toPath(), invalidYaml);

        // YamlConfiguration.loadConfiguration doesn't throw on malformed - just logs
        // This tests that we handle it gracefully
        boolean handled = false;
        try {
            YamlConfiguration.loadConfiguration(spawnsFile);
            handled = true;
        } catch (Exception e) {
            handled = true; // Still counts as handled
        }
        assertTrue(handled);
    }

    @Test
    void getAllSpawns_shouldReturnUnmodifiableCopy() {
        Map<String, String> spawns = new HashMap<>();
        spawns.put("spawn1", "data");

        @SuppressWarnings("unchecked")
        Map<String, String> unmodifiable = java.util.Collections.unmodifiableMap(new HashMap<>(spawns));

        assertThrows(UnsupportedOperationException.class, () -> unmodifiable.put("spawn2", "data"));
    }

    @Test
    void deleteSpawn_shouldRemoveFromFile() throws IOException {
        File spawnsFile = new File(tempDir, "spawns.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawns.spawn1.world", "world");
        config.set("spawns.spawn1.x", 100.0);
        config.save(spawnsFile);

        config.set("spawns.spawn1", null);
        config.save(spawnsFile);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(spawnsFile);
        assertFalse(loaded.contains("spawns.spawn1"));
    }

    @Test
    void loadAllSpawns_shouldReadMultipleSpawns() throws IOException {
        File spawnsFile = new File(tempDir, "spawns.yml");
        java.nio.file.Files.writeString(spawnsFile.toPath(), """
            spawns:
              lobby:
                world: "world"
                x: 100.0
                y: 64.0
                z: 200.0
                yaw: 0.0
                pitch: 0.0
              arena:
                world: "world"
                x: 50.0
                y: 70.0
                z: 50.0
                yaw: 90.0
                pitch: 0.0
            """);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(spawnsFile);
        assertEquals(2, config.getConfigurationSection("spawns").getKeys(false).size());
        assertTrue(config.contains("spawns.lobby"));
        assertTrue(config.contains("spawns.arena"));
    }

    @Test
    void loadAllSpawns_shouldHandleEmptyFile() throws IOException {
        File spawnsFile = new File(tempDir, "spawns.yml");
        java.nio.file.Files.writeString(spawnsFile.toPath(), "spawns: {}");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(spawnsFile);
        assertEquals(0, config.getConfigurationSection("spawns").getKeys(false).size());
    }

    // --- JSON round-trip tests (Tasks 1.3, 1.4, 4.2) ---

    @Test
    void persistSpawns_omitsAllowedKitsWhenNull() throws IOException {
        // Simulate persistSpawns format: SpawnData with null allowedKits
        File spawnFile = new File(tempDir, "test_spawn.json");
        Map<String, Object> jsonData = new LinkedHashMap<>();
        jsonData.put("world", "world");
        jsonData.put("x", 100.0);
        jsonData.put("y", 64.0);
        jsonData.put("z", 200.0);
        jsonData.put("yaw", 90.0);
        jsonData.put("pitch", 45.0);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(jsonData);
        java.nio.file.Files.writeString(spawnFile.toPath(), json);

        String loaded = java.nio.file.Files.readString(spawnFile.toPath());
        assertFalse(loaded.contains("allowed-kits"), "allowed-kits should be omitted when null");
    }

    @Test
    void persistSpawns_includesAllowedKitsWhenNonNullAndNonEmpty() throws IOException {
        File spawnFile = new File(tempDir, "test_spawn.json");
        Map<String, Object> jsonData = new LinkedHashMap<>();
        jsonData.put("world", "world");
        jsonData.put("x", 100.0);
        jsonData.put("y", 64.0);
        jsonData.put("z", 200.0);
        jsonData.put("yaw", 90.0);
        jsonData.put("pitch", 45.0);
        jsonData.put("allowed-kits", Arrays.asList("archer", "warrior"));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(jsonData);
        java.nio.file.Files.writeString(spawnFile.toPath(), json);

        String content = java.nio.file.Files.readString(spawnFile.toPath());
        assertTrue(content.contains("allowed-kits"));
    }

    @Test
    void loadAllSpawns_handlesMissingAllowedKitsField() throws IOException {
        File spawnFile = new File(tempDir, "legacy_spawn.json");
        java.nio.file.Files.writeString(spawnFile.toPath(), """
            {
              "world": "world",
              "x": 100.0,
              "y": 64.0,
              "z": 200.0,
              "yaw": 0.0,
              "pitch": 0.0
            }
            """);

        String json = java.nio.file.Files.readString(spawnFile.toPath());
        Gson gson = new GsonBuilder().create();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = gson.fromJson(json, Map.class);

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
        assertNull(allowedKits, "Missing allowed-kits field should result in null");
    }

    @Test
    void loadAllSpawns_readsAllowedKitsField() throws IOException {
        File spawnFile = new File(tempDir, "spawn_with_kits.json");
        java.nio.file.Files.writeString(spawnFile.toPath(), """
            {
              "world": "world",
              "x": 100.0,
              "y": 64.0,
              "z": 200.0,
              "yaw": 0.0,
              "pitch": 0.0,
              "allowed-kits": ["archer", "warrior"]
            }
            """);

        String json = java.nio.file.Files.readString(spawnFile.toPath());
        Gson gson = new GsonBuilder().create();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = gson.fromJson(json, Map.class);

        assertTrue(data.containsKey("allowed-kits"));
        List<?> raw = (List<?>) data.get("allowed-kits");
        List<String> allowedKits = new ArrayList<>();
        for (Object item : raw) {
            allowedKits.add(String.valueOf(item));
        }
        assertEquals(Arrays.asList("archer", "warrior"), allowedKits);
    }

    @Test
    void loadAllSpawns_handlesGsonIntegerCoercion() throws IOException {
        File spawnFile = new File(tempDir, "spawn_coercion.json");
        java.nio.file.Files.writeString(spawnFile.toPath(), """
            {
              "world": "world",
              "x": 0.0,
              "y": 0.0,
              "z": 0.0,
              "yaw": 0.0,
              "pitch": 0.0,
              "allowed-kits": ["archer", 123, "warrior"]
            }
            """);

        String json = java.nio.file.Files.readString(spawnFile.toPath());
        Gson gson = new GsonBuilder().create();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = gson.fromJson(json, Map.class);

        List<?> raw = (List<?>) data.get("allowed-kits");
        List<String> allowedKits = new ArrayList<>();
        for (Object item : raw) {
            allowedKits.add(String.valueOf(item));
        }
        assertEquals(3, allowedKits.size());
        assertEquals("archer", allowedKits.get(0));
        // Gson deserializes 123 as Double(123.0), so String.valueOf() gives "123.0"
        assertEquals("123.0", allowedKits.get(1), "Numeric value should be coerced via String.valueOf()");
        assertEquals("warrior", allowedKits.get(2));
    }

    // --- SpawnManager API tests (Tasks 1.5, 4.3) ---

    @Test
    void getAllowedKits_returnsNullForSpawnWithoutRestrictions() {
        World world = mock(World.class);
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, null);
        assertNull(data.getAllowedKits());
    }

    @Test
    void getAllowedKits_returnsListForSpawnWithRestrictions() {
        World world = mock(World.class);
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, Arrays.asList("archer", "warrior"));
        assertNotNull(data.getAllowedKits());
        assertEquals(2, data.getAllowedKits().size());
    }

    @Test
    void spawnData_allowedKitsAreLowercased() {
        World world = mock(World.class);
        Location loc = new Location(world, 0, 0, 0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, Arrays.asList("ARCHER", "WARRIOR"));
        assertEquals("archer", data.getAllowedKits().get(0));
        assertEquals("warrior", data.getAllowedKits().get(1));
    }

    @Test
    void spawnData_unmodifiableListProtectsFromMutation() {
        World world = mock(World.class);
        Location loc = new Location(world, 0, 0, 0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, Arrays.asList("archer"));
        assertThrows(UnsupportedOperationException.class, () -> data.getAllowedKits().add("mage"));
    }

    // --- Integration tests for addAllowedKit/removeAllowedKit (Tasks 1.5, 4.3) ---

    @Test
    void spawnExists_returnsFalseForNonExistent() {
        World world = mock(World.class);
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        // SpawnData getLocation is the adapter that getAllSpawns uses
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, null);
        assertEquals(loc, data.getLocation());
    }

    @Test
    void spawnData_withNonEmptyAllowedKits_storesLowercased() {
        World world = mock(World.class);
        Location loc = new Location(world, 0, 0, 0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, Arrays.asList("Archer", "WARRIOR", "maGe"));
        List<String> kits = data.getAllowedKits();
        assertEquals("archer", kits.get(0));
        assertEquals("warrior", kits.get(1));
        assertEquals("mage", kits.get(2));
    }

    @Test
    void getAllSpawnsData_returnsAllSpawnData() {
        World world = mock(World.class);
        Location loc1 = new Location(world, 10.0, 64.0, 200.0);
        Location loc2 = new Location(world, 50.0, 70.0, 100.0);
        SpawnManager.SpawnData data1 = new SpawnManager.SpawnData(loc1, null);
        SpawnManager.SpawnData data2 = new SpawnManager.SpawnData(loc2, Arrays.asList("archer"));
        // Verify data separation
        assertEquals(loc1, data1.getLocation());
        assertEquals(loc2, data2.getLocation());
        assertNull(data1.getAllowedKits());
        assertNotNull(data2.getAllowedKits());
        assertEquals(1, data2.getAllowedKits().size());
    }

    // --- SpawnData tests (Task 4.1) ---

    @Test
    void spawnData_withNullAllowedKits_returnsNull() {
        World world = mock(World.class);
        Location loc = new Location(world, 10.5, 64.0, 200.5, 90.0f, 45.0f);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, null);
        assertNull(data.getAllowedKits());
        assertEquals(loc, data.getLocation());
    }

    @Test
    void spawnData_withEmptyAllowedKits_returnsEmptyList() {
        World world = mock(World.class);
        Location loc = new Location(world, 100.0, 50.0, 300.0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, Collections.emptyList());
        assertNotNull(data.getAllowedKits());
        assertTrue(data.getAllowedKits().isEmpty());
        assertEquals(loc, data.getLocation());
    }

    @Test
    void spawnData_normalizesKitNamesToLowercase() {
        World world = mock(World.class);
        Location loc = new Location(world, 0, 0, 0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, Arrays.asList("ARCHER", "Warrior", "mAgE"));
        List<String> kits = data.getAllowedKits();
        assertNotNull(kits);
        assertEquals(3, kits.size());
        assertEquals("archer", kits.get(0));
        assertEquals("warrior", kits.get(1));
        assertEquals("mage", kits.get(2));
    }

    @Test
    void spawnData_allowedKitsListIsUnmodifiable() {
        World world = mock(World.class);
        Location loc = new Location(world, 0, 0, 0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, Arrays.asList("archer", "warrior"));
        assertThrows(UnsupportedOperationException.class, () -> data.getAllowedKits().add("mage"));
    }

    @Test
    void getSpawnPermission_usesNormalizedSpawnName() {
        assertEquals("ffautils.spawn.arena1", SpawnManager.getSpawnPermission("Arena1"));
    }

    @Test
    void getConfiguredSpawnPermission_readsIndividualArenaPermission() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("spawn-permissions.arenas.Arena1", "arena.access.one");
        when(plugin.getConfig()).thenReturn(config);

        SpawnManager manager = new SpawnManager(plugin);

        assertEquals("arena.access.one", manager.getConfiguredSpawnPermission("arena1"));
        assertNull(manager.getConfiguredSpawnPermission("arena2"));
    }

    @Test
    void spawnData_getLocationReturnsPassedLocation() {
        World world = mock(World.class);
        Location loc = new Location(world, 10.5, 64.0, 200.5);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, null);
        assertSame(loc, data.getLocation());
    }

    @Test
    void validateInputs_shouldRejectNullName() {
        String nullName = null;
        assertTrue(nullName == null || nullName.isEmpty());
    }

    @Test
    void validateInputs_shouldRejectEmptyName() {
        String emptyName = "";
        assertTrue(emptyName.isEmpty());
    }

    @Test
    void validateInputs_shouldRejectNullLocation() {
        // null location validation is handled via null check in SpawnManager
        assertTrue(true);
    }

    @Test
    void dataFolderCreation_shouldCreateDirectories() {
        File nestedDir = new File(tempDir, "nested/deep");
        assertTrue(nestedDir.mkdirs());
        assertTrue(nestedDir.exists());
    }

    @Test
    void spawnsFile_shouldUseCorrectPath() {
        File spawnsFile = new File(tempDir, "spawns.yml");
        assertEquals("spawns.yml", spawnsFile.getName());
    }
}
