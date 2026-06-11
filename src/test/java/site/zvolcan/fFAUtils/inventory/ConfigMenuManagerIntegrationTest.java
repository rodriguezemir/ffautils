package site.zvolcan.fFAUtils.inventory;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.Kit;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigMenuManagerIntegrationTest {

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
    void navigationFlow_mainToSpawnsToDetailAndBack() {
        // Setup spawns
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        SpawnManager.SpawnData data = new SpawnManager.SpawnData(loc, null);
        Map<String, SpawnManager.SpawnData> spawns = new LinkedHashMap<>();
        spawns.put("lobby", data);
        when(spawnManager.getAllSpawnsData()).thenReturn(spawns);
        when(spawnManager.getSpawn("lobby")).thenReturn(loc);
        when(spawnManager.getAllowedKits("lobby")).thenReturn(null);

        Player player = server.addPlayer();

        // Step 1: Open main inventory
        configMenuManager.openMain(player);
        Inventory mainInv = player.getOpenInventory().getTopInventory();
        assertEquals(27, mainInv.getSize());
        assertEquals("FFAUtils Config", player.getOpenInventory().getTitle());

        // Step 2: Simulate clicking Spawns button (slot 11) -> should open SpawnsInventory
        configMenuManager.openSpawns(player, 0);
        Inventory spawnsInv = player.getOpenInventory().getTopInventory();
        assertEquals(54, spawnsInv.getSize());
        assertTrue(player.getOpenInventory().getTitle().contains("Spawns"));

        // Step 3: Simulate clicking spawn entry -> should open SpawnDetailInventory
        configMenuManager.openSpawnDetail(player, "lobby");
        Inventory detailInv = player.getOpenInventory().getTopInventory();
        assertEquals(27, detailInv.getSize());
        assertTrue(player.getOpenInventory().getTitle().contains("lobby"));

        // Step 4: Simulate clicking back button -> should return to SpawnsInventory
        configMenuManager.openSpawns(player, 0);
        Inventory backSpawns = player.getOpenInventory().getTopInventory();
        assertEquals(54, backSpawns.getSize());

        // Step 5: Click back from spawns -> should return to main
        configMenuManager.openMain(player);
        Inventory backMain = player.getOpenInventory().getTopInventory();
        assertEquals(27, backMain.getSize());
    }

    @Test
    void navigationFlow_mainToKitsToDetailAndBack() {
        // Setup kits
        Kit kit = new Kit("archer", new ItemStack[]{new ItemStack(Material.BOW)});
        Map<String, Kit> kits = new LinkedHashMap<>();
        kits.put("archer", kit);
        when(kitManager.getAllKits()).thenReturn(kits);
        when(kitManager.getKit("archer")).thenReturn(kit);

        Player player = server.addPlayer();

        // Step 1: Open main inventory
        configMenuManager.openMain(player);
        assertEquals("FFAUtils Config", player.getOpenInventory().getTitle());

        // Step 2: Open kits
        configMenuManager.openKits(player, 0);
        assertTrue(player.getOpenInventory().getTitle().contains("Kits"));

        // Step 3: Open kit detail
        configMenuManager.openKitDetail(player, "archer");
        assertTrue(player.getOpenInventory().getTitle().contains("archer"));

        // Step 4: Back to kits
        configMenuManager.openKits(player, 0);
        assertTrue(player.getOpenInventory().getTitle().contains("Kits"));

        // Step 5: Back to main
        configMenuManager.openMain(player);
        assertEquals("FFAUtils Config", player.getOpenInventory().getTitle());
    }

    @Test
    void emptySpawnsAndKits_shouldNotCrash() {
        when(spawnManager.getAllSpawnsData()).thenReturn(Collections.emptyMap());
        when(kitManager.getAllKits()).thenReturn(Collections.emptyMap());

        Player player = server.addPlayer();

        // All these should work without crashing
        assertDoesNotThrow(() -> configMenuManager.openMain(player));
        assertDoesNotThrow(() -> configMenuManager.openSpawns(player, 0));
        assertDoesNotThrow(() -> configMenuManager.openKits(player, 0));
    }

    @Test
    void paginationFlow_multiPageSpawns() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Map<String, SpawnManager.SpawnData> spawns = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            Location loc = new Location(world, i, 64.0, 200.0);
            spawns.put("spawn" + i, new SpawnManager.SpawnData(loc, null));
        }
        when(spawnManager.getAllSpawnsData()).thenReturn(spawns);

        Player player = server.addPlayer();

        // Open page 0
        configMenuManager.openSpawns(player, 0);
        assertTrue(player.getOpenInventory().getTitle().contains("Page 1"));

        // Navigate to page 1
        configMenuManager.openSpawns(player, 1);
        assertTrue(player.getOpenInventory().getTitle().contains("Page 2"));

        // Navigate back to page 0
        configMenuManager.openSpawns(player, 0);
        assertTrue(player.getOpenInventory().getTitle().contains("Page 1"));
    }

    @Test
    void paginationFlow_multiPageKits() {
        Map<String, Kit> kits = new LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            Kit kit = new Kit("kit" + i, new ItemStack[]{new ItemStack(Material.STONE)});
            kits.put("kit" + i, kit);
        }
        when(kitManager.getAllKits()).thenReturn(kits);

        Player player = server.addPlayer();

        // Open page 0
        configMenuManager.openKits(player, 0);
        assertTrue(player.getOpenInventory().getTitle().contains("Page 1"));

        // Navigate to page 1
        configMenuManager.openKits(player, 1);
        assertTrue(player.getOpenInventory().getTitle().contains("Page 2"));
    }
}