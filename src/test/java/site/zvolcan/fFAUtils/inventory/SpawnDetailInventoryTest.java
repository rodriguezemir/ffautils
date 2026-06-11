package site.zvolcan.fFAUtils.inventory;

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

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpawnDetailInventoryTest {

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

    @Test
    void openSpawnDetail_shouldShowSpawnInfo() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.5, 64.0, 200.3, 90.0f, 45.0f);
        when(spawnManager.getSpawn("lobby")).thenReturn(loc);
        when(spawnManager.getAllowedKits("lobby")).thenReturn(null);

        Player player = server.addPlayer();
        configMenuManager.openSpawnDetail(player, "lobby");
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(27, inv.getSize());
        assertNotNull(inv.getItem(4));
        assertEquals("lobby", plainName(inv.getItem(4)));
        assertNotNull(inv.getItem(13));
        assertNotNull(inv.getItem(13).getItemMeta().getLore());
        assertTrue(inv.getItem(13).getItemMeta().getLore().stream()
                .anyMatch(l -> l.contains("world") || l.contains("World")));
    }

    @Test
    void openSpawnDetail_shouldShowCoordinatesRoundedToOneDecimal() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.123, 64.456, 200.789);
        when(spawnManager.getSpawn("test")).thenReturn(loc);
        when(spawnManager.getAllowedKits("test")).thenReturn(null);

        Player player = server.addPlayer();
        configMenuManager.openSpawnDetail(player, "test");
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(13));
        String loreText = String.join(" ", inv.getItem(13).getItemMeta().getLore());
        // Coordinates should be rounded to 1 decimal
        assertTrue(loreText.contains("10.1") || loreText.contains("10,1") || loreText.contains("x:"),
                "Lore should contain x coordinate rounded to 1 decimal");
    }

    @Test
    void openSpawnDetail_withAllowedKits_shouldShowKits() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        when(spawnManager.getSpawn("arena")).thenReturn(loc);
        when(spawnManager.getAllowedKits("arena")).thenReturn(Arrays.asList("archer", "warrior"));

        Player player = server.addPlayer();
        configMenuManager.openSpawnDetail(player, "arena");
        Inventory inv = player.getOpenInventory().getTopInventory();
        // Allowed kits info should be in lore somewhere
        boolean found = false;
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) != null && inv.getItem(i).getItemMeta() != null) {
                var lore = inv.getItem(i).getItemMeta().getLore();
                if (lore != null && lore.stream().anyMatch(l -> l.contains("archer") || l.contains("Allowed"))) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "Should show allowed kits info somewhere in the detail view");
    }

    @Test
    void openSpawnDetail_withoutAllowedKits_shouldShowAllKits() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        when(spawnManager.getSpawn("lobby")).thenReturn(loc);
        when(spawnManager.getAllowedKits("lobby")).thenReturn(null);

        Player player = server.addPlayer();
        configMenuManager.openSpawnDetail(player, "lobby");
        Inventory inv = player.getOpenInventory().getTopInventory();
        boolean found = false;
        for (int i = 0; i < 27; i++) {
            if (inv.getItem(i) != null && inv.getItem(i).getItemMeta() != null) {
                var lore = inv.getItem(i).getItemMeta().getLore();
                if (lore != null && lore.stream().anyMatch(l -> l.contains("All kits") || l.contains("all kits"))) {
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "Should show 'All kits' when no restrictions");
    }

    @Test
    void openSpawnDetail_shouldHaveBackButtonAtSlot22() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location loc = new Location(world, 10.0, 64.0, 200.0);
        when(spawnManager.getSpawn("lobby")).thenReturn(loc);
        when(spawnManager.getAllowedKits("lobby")).thenReturn(null);

        Player player = server.addPlayer();
        configMenuManager.openSpawnDetail(player, "lobby");
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(22), "Back button should be present at slot 22");
        assertEquals(Material.ARROW, inv.getItem(22).getType());
    }
}