package site.zvolcan.fFAUtils.inventory;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigMenuManagerTest {

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
    void constructor_shouldAcceptManagers() {
        assertNotNull(configMenuManager);
    }

    @Test
    void openMain_shouldOpenInventoryForPlayer() {
        Player player = server.addPlayer();
        configMenuManager.openMain(player);
        Inventory openInventory = player.getOpenInventory().getTopInventory();
        assertEquals(27, openInventory.getSize());
        assertEquals("FFAUtils Config", player.getOpenInventory().getTitle());
    }

    private static String plainName(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    @Test
    void openMain_shouldHaveSpawnsButtonAtSlot11() {
        Player player = server.addPlayer();
        configMenuManager.openMain(player);
        Inventory openInventory = player.getOpenInventory().getTopInventory();
        assertNotNull(openInventory.getItem(11), "Spawns button should be present");
        assertEquals("Spawns", plainName(openInventory.getItem(11)));
    }

    @Test
    void openMain_shouldHaveKitsButtonAtSlot15() {
        Player player = server.addPlayer();
        configMenuManager.openMain(player);
        Inventory openInventory = player.getOpenInventory().getTopInventory();
        assertNotNull(openInventory.getItem(15), "Kits button should be present");
        assertEquals("Kits", plainName(openInventory.getItem(15)));
    }

    @Test
    void openMain_calledTwice_shouldNotThrow() {
        Player player = server.addPlayer();
        configMenuManager.openMain(player);
        assertDoesNotThrow(() -> configMenuManager.openMain(player));
    }

    @Test
    void openSpawns_stubShouldNotThrow() {
        Player player = server.addPlayer();
        assertDoesNotThrow(() -> configMenuManager.openSpawns(player, 0));
    }

    @Test
    void openKits_stubShouldNotThrow() {
        Player player = server.addPlayer();
        assertDoesNotThrow(() -> configMenuManager.openKits(player, 0));
    }

    @Test
    void getSpawnManager_shouldReturnInjectedInstance() {
        assertSame(spawnManager, configMenuManager.getSpawnManager());
    }

    @Test
    void getKitManager_shouldReturnInjectedInstance() {
        assertSame(kitManager, configMenuManager.getKitManager());
    }
}