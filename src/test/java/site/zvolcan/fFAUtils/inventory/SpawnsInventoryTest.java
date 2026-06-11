package site.zvolcan.fFAUtils.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

    private static String plainName(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private static List<String> plainLore(ItemStack item) {
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null) return Collections.emptyList();
        return lore.stream()
                .map(c -> PlainTextComponentSerializer.plainText().serialize(c))
                .toList();
    }

    @Test
    void openSpawns_withNoSpawns_shouldShowPlaceholder() {
        when(spawnManager.getAllSpawnsData()).thenReturn(Collections.emptyMap());
        Player player = server.addPlayer();
        configMenuManager.openSpawns(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(54, inv.getSize());
        assertNotNull(inv.getItem(22), "Placeholder item should be present");
        assertEquals("No spawns configured", plainName(inv.getItem(22)));
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
        assertNotNull(inv.getItem(0), "Spawn item should be present");
        assertEquals("lobby", plainName(inv.getItem(0)));
        assertTrue(plainLore(inv.getItem(0)).contains("world"));
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
    void openSpawnDetail_shouldPreservePreviousPage() {
        ConfigMenuManager spyManager = spy(configMenuManager);
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
        new SpawnDetailInventory(spyManager, "lobby", 2).open(player);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(27, inv.getSize());
        assertNotNull(inv.getItem(22), "Back button should be present");
        assertEquals("lobby", plainName(inv.getItem(4)));
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
        List<String> lore = plainLore(inv.getItem(0));
        assertNotNull(lore);
        // Should show allowed kits info
        boolean hasKitsInfo = lore.stream().anyMatch(l -> l.contains("archer") || l.contains("Allowed kits"));
        assertTrue(hasKitsInfo, "Lore should contain allowed kits info");
    }
}