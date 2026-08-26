package site.zvolcan.fFAUtils.managers;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import site.zvolcan.fFAUtils.objects.Region;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for RegionManager JSON persistence and region business logic.
 */
class RegionManagerTest {

    @TempDir
    private File tempDir;

    private JavaPlugin plugin;
    private RegionManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir);
        manager = new RegionManager(plugin);
    }

    @Test
    void setRegion_shouldStoreAndRetrieveCaseInsensitive() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        assertTrue(manager.setRegion("NoobKit",
                new Location(world, 0, 64, 0),
                new Location(world, 50, 80, 50)));

        assertNotNull(manager.getRegion("noobkit"));
        assertNotNull(manager.getRegion("NOOBKIT"));
        assertTrue(manager.hasRegion("NoobKit"));
    }

    @Test
    void setRegion_shouldRejectDifferentWorlds() {
        World w1 = mock(World.class);
        when(w1.getName()).thenReturn("world");
        World w2 = mock(World.class);
        when(w2.getName()).thenReturn("other_world");

        assertFalse(manager.setRegion("kit",
                new Location(w1, 0, 64, 0),
                new Location(w2, 10, 64, 10)));
        assertNull(manager.getRegion("kit"));
    }

    @Test
    void setRegion_shouldRejectEmptyName() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        assertFalse(manager.setRegion("", new Location(world, 0, 64, 0), new Location(world, 5, 64, 5)));
    }

    @Test
    void setRegion_shouldNormalizeCorners() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");

        manager.setRegion("kit",
                new Location(world, 50, 80, 50),
                new Location(world, 0, 64, 0));

        Region region = manager.getRegion("kit");
        assertEquals(0, region.getMinX());
        assertEquals(64, region.getMinY());
        assertEquals(0, region.getMinZ());
        assertEquals(50, region.getMaxX());
        assertEquals(80, region.getMaxY());
        assertEquals(50, region.getMaxZ());
    }

    @Test
    void region_contains_checksBoundsAndWorld() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        World other = mock(World.class);
        when(other.getName()).thenReturn("other");

        Region region = new Region(new Location(world, 0, 64, 0), new Location(world, 10, 70, 10));

        assertTrue(region.contains(new Location(world, 5, 65, 5)));
        assertTrue(region.contains(new Location(world, 0, 64, 0)));
        assertTrue(region.contains(new Location(world, 10.9, 64, 10)));
        assertFalse(region.contains(new Location(world, 11, 64, 5)));
        assertFalse(region.contains(new Location(other, 5, 65, 5)));
        assertFalse(region.contains(null));
    }

    @Test
    void deleteRegion_shouldRemoveAndReturnFalseWhenMissing() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        manager.setRegion("kit", new Location(world, 0, 64, 0), new Location(world, 5, 64, 5));

        assertTrue(manager.deleteRegion("KIT"));
        assertFalse(manager.hasRegion("kit"));
        assertFalse(manager.deleteRegion("kit"));
    }

    @Test
    void regions_shouldPersistToJsonAndReload() throws IOException {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        manager.setRegion("kit",
                new Location(world, -10, 60, -20),
                new Location(world, 30, 90, 40));

        RegionManager reloaded = new RegionManager(plugin);
        reloaded.registerRegions();

        Region region = reloaded.getRegion("kit");
        assertNotNull(region);
        assertEquals("world", region.getWorld());
        assertEquals(-10, region.getMinX());
        assertEquals(-20, region.getMinZ());
        assertEquals(30, region.getMaxX());
        assertEquals(90, region.getMaxY());

        File file = new File(tempDir, "regions.json");
        assertTrue(file.exists());
        assertTrue(Files.readString(file.toPath()).contains("\"kit\""));
    }

    @Test
    void registerRegions_shouldTolerateMissingFile() {
        assertDoesNotThrow(() -> manager.registerRegions());
        assertTrue(manager.getAllRegions().isEmpty());
    }

    @Test
    void getAllRegions_shouldBeImmutableCopy() {
        assertTrue(manager.getAllRegions().isEmpty());

        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        manager.setRegion("kit", new Location(world, 0, 64, 0), new Location(world, 5, 64, 5));

        assertThrows(UnsupportedOperationException.class,
                () -> manager.getAllRegions().put("other", manager.getRegion("kit")));
    }
}
