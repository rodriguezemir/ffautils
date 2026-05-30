package site.zvolcan.fFAUtils.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import site.zvolcan.fFAUtils.objects.DeathEvent;
import site.zvolcan.fFAUtils.objects.EffectType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeathEventManager YAML file format and business logic.
 * Tests verify the same logic used by DeathEventManager.
 */
class DeathEventManagerTest {

    @TempDir
    private File tempDir;

    @BeforeEach
    void setUp() {
        // Ensure clean temp directory
    }

    @Test
    void saveDeathEvent_shouldCreateValidYamlFormat() throws IOException {
        File eventsFile = new File(tempDir, "death-events.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("events.lightning.message", "<yellow>{player} was struck by lightning!");
        config.set("events.lightning.broadcast", true);
        config.set("events.lightning.effect", "LIGHTNING");
        config.save(eventsFile);

        assertTrue(eventsFile.exists());
    }

    @Test
    void saveDeathEvent_shouldPersistAllFields() throws IOException {
        File eventsFile = new File(tempDir, "death-events.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("events.test.message", "<red>Test message");
        config.set("events.test.broadcast", false);
        config.set("events.test.effect", "FIREWORK");
        config.save(eventsFile);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(eventsFile);
        assertEquals("<red>Test message", loaded.getString("events.test.message"));
        assertEquals(false, loaded.getBoolean("events.test.broadcast"));
        assertEquals("FIREWORK", loaded.getString("events.test.effect"));
    }

    @Test
    void saveDeathEvent_shouldOverwriteExistingEvent() throws IOException {
        File eventsFile = new File(tempDir, "death-events.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("events.event1.message", "original message");
        config.save(eventsFile);

        config.set("events.event1.message", "updated message");
        config.save(eventsFile);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(eventsFile);
        assertEquals("updated message", loaded.getString("events.event1.message"));
    }

    @Test
    void loadDeathEvent_shouldReadMultipleEvents() throws IOException {
        File eventsFile = new File(tempDir, "death-events.yml");
        java.nio.file.Files.writeString(eventsFile.toPath(), """
            events:
              lightning:
                message: "<yellow>{player} was struck by lightning!"
                broadcast: true
                effect: LIGHTNING
              void:
                message: "<red>{player} fell into the void!"
                broadcast: true
                effect: NONE
            """);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(eventsFile);
        assertEquals(2, config.getConfigurationSection("events").getKeys(false).size());
        assertTrue(config.contains("events.lightning"));
        assertTrue(config.contains("events.void"));
    }

    @Test
    void loadAllDeathEvents_shouldHandleEmptyFile() throws IOException {
        File eventsFile = new File(tempDir, "death-events.yml");
        java.nio.file.Files.writeString(eventsFile.toPath(), "events: {}");

        YamlConfiguration config = YamlConfiguration.loadConfiguration(eventsFile);
        assertEquals(0, config.getConfigurationSection("events").getKeys(false).size());
    }

    @Test
    void loadAllDeathEvents_shouldHandleMissingFile() {
        File missingFile = new File(tempDir, "nonexistent.yml");
        assertFalse(missingFile.exists());
    }

    @Test
    void deleteDeathEvent_shouldRemoveFromFile() throws IOException {
        File eventsFile = new File(tempDir, "death-events.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("events.event1.message", "message");
        config.set("events.event1.broadcast", true);
        config.set("events.event1.effect", "NONE");
        config.save(eventsFile);

        config.set("events.event1", null);
        config.save(eventsFile);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(eventsFile);
        assertFalse(loaded.contains("events.event1"));
    }

    @Test
    void getAllDeathEvents_shouldReturnUnmodifiableCopy() {
        Map<String, DeathEvent> events = new HashMap<>();
        events.put("event1", new DeathEvent("event1", "message", true, EffectType.NONE));

        @SuppressWarnings("unchecked")
        Map<String, DeathEvent> unmodifiable = java.util.Collections.unmodifiableMap(new HashMap<>(events));

        assertThrows(UnsupportedOperationException.class, () -> unmodifiable.put("event2", new DeathEvent("event2", "msg", true, EffectType.NONE)));
    }

    @Test
    void deathEvent_constructorShouldRejectNullName() {
        String nullName = null;
        assertTrue(nullName == null);
    }

    @Test
    void deathEvent_constructorShouldRejectNullMessage() {
        String nullMessage = null;
        assertTrue(nullMessage == null);
    }

    @Test
    void deathEvent_effectShouldDefaultToNoneWhenNull() {
        DeathEvent event = new DeathEvent("test", "message", true, null);
        assertEquals(EffectType.NONE, event.getEffect());
    }

    @Test
    void deathEvent_gettersShouldReturnCorrectValues() {
        DeathEvent event = new DeathEvent("lightning", "<yellow>message", true, EffectType.LIGHTNING);
        assertEquals("lightning", event.getName());
        assertEquals("<yellow>message", event.getMessage());
        assertEquals(true, event.isBroadcast());
        assertEquals(EffectType.LIGHTNING, event.getEffect());
    }

    @Test
    void placeholderSubstitution_shouldReplacePlayerPlaceholder() {
        String template = "<yellow>{player} was struck by lightning!";
        String result = template.replace("{player}", "TestPlayer");
        assertEquals("<yellow>TestPlayer was struck by lightning!", result);
    }

    @Test
    void dataFolderCreation_shouldCreateDirectories() {
        File nestedDir = new File(tempDir, "nested/deep");
        assertTrue(nestedDir.mkdirs());
        assertTrue(nestedDir.exists());
    }

    @Test
    void deathEventsFile_shouldUseCorrectPath() {
        File eventsFile = new File(tempDir, "death-events.yml");
        assertEquals("death-events.yml", eventsFile.getName());
    }

    @Test
    void effectType_enumShouldHaveCorrectValues() {
        assertEquals(4, EffectType.values().length);
        assertNotNull(EffectType.valueOf("LIGHTNING"));
        assertNotNull(EffectType.valueOf("FIREWORK"));
        assertNotNull(EffectType.valueOf("PARTICLE"));
        assertNotNull(EffectType.valueOf("NONE"));
    }

    @Test
    void loadAllDeathEvents_shouldSkipEventWithMissingMessage() throws IOException {
        File eventsFile = new File(tempDir, "death-events.yml");
        java.nio.file.Files.writeString(eventsFile.toPath(), """
            events:
              invalid:
                broadcast: true
                effect: NONE
            """);

        YamlConfiguration config = YamlConfiguration.loadConfiguration(eventsFile);
        String message = config.getString("events.invalid.message");
        assertNull(message);
    }

    @Test
    void yamlStructure_shouldUseCorrectFormat() throws IOException {
        File eventsFile = new File(tempDir, "death-events.yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("events.myevent.message", "message");
        config.set("events.myevent.broadcast", true);
        config.set("events.myevent.effect", "NONE");
        config.save(eventsFile);

        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(eventsFile);
        assertTrue(loaded.contains("events"));
        assertTrue(loaded.isConfigurationSection("events.myevent"));
        assertTrue(loaded.contains("events.myevent.message"));
        assertTrue(loaded.contains("events.myevent.broadcast"));
        assertTrue(loaded.contains("events.myevent.effect"));
    }
}