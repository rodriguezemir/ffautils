package site.zvolcan.fFAUtils.inventory;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpawnsInventoryTest {

    private ServerMock server;
    private SpawnManager spawnManager;
    private KitManager kitManager;
    private ConfigMenuManager configMenuManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        spawnManager = mock(SpawnManager.class, withSettings().lenient());
        kitManager = mock(KitManager.class, withSettings().lenient());
        configMenuManager = new ConfigMenuManager(spawnManager, kitManager);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openSpawns_withNoSpawns_shouldShowPlaceholder() {
        when(spawnManager.getAllSpawnsData()).thenReturn(Collections.emptyMap());
        Player player = server.addPlayer();
        configMenuManager.openSpawns(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(54, inv.getSize());
        assertNotNull(inv.getItem(22), "Placeholder item should be present");
        assertEquals("No spawns configured", inv.getItem(22).getItemMeta().getDisplayName());
    }

    @Test
    void openSpawns_withOneSpawn_shouldShowSpawnItem() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, null);
        Map<String, SpawnManager.SpawnData> spawns = new LinkedHashMap<>();
        spawns.put("lobby", data);
        when(spawnManager.getAllSpawnsData()).thenReturn(spawns);

        Player player = server.addPlayer();
        configMenuManager.openSpawns(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(54, inv.getSize());
        // First spawn item should be at slot 0
        assertNotNull(inv.getItem(0), "Spawn item should be present");
        assertEquals("lobby", inv.getItem(0).getItemMeta().getDisplayName());
        assertTrue(inv.getItem(0).getItemMeta().getLore().contains("world"));
    }

    @Test
    void openSpawns_shouldHaveBackButtonAtSlot49() {
        when(spawnManager.getAllSpawnsData()).thenReturn(Collections.emptyMap());
        Player player = server.addPlayer();
        configMenuManager.openSpawns(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(49), "Back button should be present");
        assertEquals(Material.ARROW, inv.getItem(49).getType());
    }

    @Test
    void openSpawns_withMoreThan45Spawns_shouldShowNextPageButton() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Map<String, SpawnManager.SpawnData> spawns = new LinkedHashMap<>();
        for (int i = 0; i < 46; i++) {
            Location loc = new Location(world, i, 64.0, 200.0);
            spawns.put("spawn" + i, new SpawnManager.SpawnData(loc, null));
        }
        when(spawnManager.getAllSpawnsData()).thenReturn(spawns);

        Player player = server.addPlayer();
        configMenuManager.openSpawns(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(52), "Next page button should be present");
        assertEquals(Material.ARROW, inv.getItem(52).getType());
    }

    @Test
    void openSpawns_onSecondPage_shouldShowPrevPageButton() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Map<String, SpawnManager.SpawnData> spawns = new LinkedHashMap<>();
        for (int i = 0; i < 46; i++) {
            Location loc = new Location(world, i, 64.0, 200.0);
            spawns.put("spawn" + i, new SpawnManager.SpawnData(loc, null));
        }
        when(spawnManager.getAllSpawnsData()).thenReturn(spawns);

        Player player = server.addPlayer();
        configMenuManager.openSpawns(player, 1); // page 1
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(50), "Prev page button should be present");
        assertEquals(Material.ARROW, inv.getItem(50).getType());
    }

    @Test
    void openSpawns_onFirstPage_shouldNotShowPrevPageButton() {
        when(spawnManager.getAllSpawnsData()).thenReturn(Collections.emptyMap());
        Player player = server.addPlayer();
        configMenuManager.openSpawns(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNull(inv.getItem(50), "Prev page button should not be present on first page");
    }

    @Test
    void openSpawns_withAllowedKits_shouldShowKitsInLore() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, Arrays.asList("archer", "warrior"));
        Map<String, SpawnManager.SpawnData> spawns = new LinkedHashMap<>();
        spawns.put("arena", data);
        when(spawnManager.getAllSpawnsData()).thenReturn(spawns);

        Player player = server.addPlayer();
        configMenuManager.openSpawns(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(0));
        List<String> lore = inv.getItem(0).getItemMeta().getLore();
        assertNotNull(lore);
        // Should show allowed kits info
        boolean hasKitsInfo = lore.stream().anyMatch(l -> l.contains("archer") || l.contains("Allowed kits"));
        assertTrue(hasKitsInfo, "Lore should contain allowed kits info");
    }
}