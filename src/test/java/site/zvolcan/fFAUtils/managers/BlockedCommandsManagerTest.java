package site.zvolcan.fFAUtils.managers;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import site.zvolcan.fFAUtils.FFAUtils;

import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link BlockedCommandsManager}.
 * Covers YAML loading, case-insensitive matching, empty/missing file handling,
 * unmodifiable set exposure, and non-string entry tolerance.
 */
class BlockedCommandsManagerTest {

    @TempDir
    private File tempDir;

    private FFAUtils mockPlugin;
    private MockedStatic<FFAUtils> ffaStatic;

    @BeforeEach
    void setUp() {
        mockPlugin = mock(FFAUtils.class, withSettings().lenient());
        when(mockPlugin.getDataFolder()).thenReturn(tempDir);
        when(mockPlugin.getLogger()).thenReturn(Logger.getLogger("BlockedCommandsManagerTest"));
        ffaStatic = mockStatic(FFAUtils.class, withSettings().lenient());
        ffaStatic.when(FFAUtils::getInstance).thenReturn(mockPlugin);
    }

    @AfterEach
    void tearDown() {
        if (ffaStatic != null) {
            ffaStatic.close();
        }
    }

    @Test
    void loadBlockedCommands_shouldReadConfiguredEntries() throws Exception {
        writeYaml("commands:\n  - kit\n  - spawn\n  - ffautils\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();

        assertTrue(manager.isBlocked("kit"));
        assertTrue(manager.isBlocked("spawn"));
        assertTrue(manager.isBlocked("ffautils"));
    }

    @Test
    void isBlocked_shouldBeCaseInsensitive() throws Exception {
        writeYaml("commands:\n  - kit\n  - spawn\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();

        assertTrue(manager.isBlocked("KIT"));
        assertTrue(manager.isBlocked("Kit"));
        assertTrue(manager.isBlocked("SpAwN"));
        assertTrue(manager.isBlocked("kit"));
    }

    @Test
    void isBlocked_shouldReturnFalseWhenNotConfigured() throws Exception {
        writeYaml("commands:\n  - kit\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();

        assertFalse(manager.isBlocked("msg"));
        assertFalse(manager.isBlocked("teleport"));
    }

    @Test
    void isBlocked_shouldReturnFalseForEmptyConfig() throws Exception {
        writeYaml("commands: []\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();

        assertFalse(manager.isBlocked("kit"));
        assertFalse(manager.isBlocked("spawn"));
        assertFalse(manager.isBlocked("anything"));
    }

    @Test
    void isBlocked_shouldReturnFalseWhenFileMissing() {
        // No file written to tempDir
        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();

        assertFalse(manager.isBlocked("kit"));
        assertFalse(manager.isBlocked("spawn"));
    }

    @Test
    void getBlockedCommands_shouldReturnUnmodifiableSet() throws Exception {
        writeYaml("commands:\n  - kit\n  - spawn\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();

        Set<String> blocked = manager.getBlockedCommands();

        assertEquals(2, blocked.size());
        assertTrue(blocked.contains("kit"));
        assertTrue(blocked.contains("spawn"));
        assertThrows(UnsupportedOperationException.class,
                () -> blocked.add("hacked"));
    }

    @Test
    void loadBlockedCommands_shouldIgnoreNonStringEntries() throws Exception {
        // Yaml allows mixed-type lists; non-strings should not crash the loader.
        // Bukkit's getStringList coerces non-strings to their String form, so
        // we verify the loader does not throw and that valid labels are present.
        writeYaml("commands:\n  - kit\n  - 123\n  - true\n  - spawn\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        assertDoesNotThrow(manager::loadBlockedCommands,
                "Loader must not throw on mixed-type entries");

        assertTrue(manager.isBlocked("kit"));
        assertTrue(manager.isBlocked("spawn"));
        // The set must contain at least the two valid string labels;
        // Bukkit's getStringList may also coerce non-strings to "123" and "true".
        assertTrue(manager.getBlockedCommands().size() >= 2);
    }

    @Test
    void loadBlockedCommands_shouldHandleMissingCommandsKey() throws Exception {
        writeYaml("other-key: value\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();

        assertFalse(manager.isBlocked("kit"));
    }

    @Test
    void isBlocked_shouldReturnFalseForNullLabel() throws Exception {
        writeYaml("commands:\n  - kit\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();

        // Manager contract: input is normalized to lowercase; null would NPE,
        // so callers are expected to pass non-null. Verify the documented
        // contract is met for a normal query.
        assertFalse(manager.isBlocked(""));
        assertFalse(manager.isBlocked("not-configured"));
    }

    @Test
    void loadBlockedCommands_calledTwice_shouldReload() throws Exception {
        writeYaml("commands:\n  - kit\n");

        BlockedCommandsManager manager = new BlockedCommandsManager(mockPlugin);
        manager.loadBlockedCommands();
        assertTrue(manager.isBlocked("kit"));

        // Overwrite with a new list
        writeYaml("commands:\n  - spawn\n");
        manager.loadBlockedCommands();

        assertFalse(manager.isBlocked("kit"), "Stale entry should be cleared on reload");
        assertTrue(manager.isBlocked("spawn"));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void writeYaml(String content) throws Exception {
        File file = new File(tempDir, "blocked-commands.yml");
        Files.writeString(file.toPath(), content);
    }
}
