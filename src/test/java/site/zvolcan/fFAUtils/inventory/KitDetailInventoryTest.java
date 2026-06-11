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
import site.zvolcan.fFAUtils.objects.Kit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KitDetailInventoryTest {

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
    void openKitDetail_shouldShowKitName() {
        Kit kit = new Kit("archer", new ItemStack[]{new ItemStack(Material.BOW)});
        when(kitManager.getKit("archer")).thenReturn(kit);

        Player player = server.addPlayer();
        configMenuManager.openKitDetail(player, "archer");
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(27, inv.getSize());
        assertNotNull(inv.getItem(4));
        assertEquals("archer", plainName(inv.getItem(4)));
    }

    @Test
    void openKitDetail_shouldShowItemPreviews() {
        Kit kit = new Kit("warrior", new ItemStack[]{
                new ItemStack(Material.IRON_SWORD),
                new ItemStack(Material.IRON_CHESTPLATE)
        });
        when(kitManager.getKit("warrior")).thenReturn(kit);

        Player player = server.addPlayer();
        configMenuManager.openKitDetail(player, "warrior");
        Inventory inv = player.getOpenInventory().getTopInventory();
        // Items should be displayed as previews starting from slot 9
        assertNotNull(inv.getItem(9), "First kit item should be at slot 9");
        assertEquals(Material.IRON_SWORD, inv.getItem(9).getType());
        assertNotNull(inv.getItem(10), "Second kit item should be at slot 10");
        assertEquals(Material.IRON_CHESTPLATE, inv.getItem(10).getType());
    }

    @Test
    void openKitDetail_withEmptyKit_shouldNotCrash() {
        Kit kit = new Kit("empty", new ItemStack[]{});
        when(kitManager.getKit("empty")).thenReturn(kit);

        Player player = server.addPlayer();
        assertDoesNotThrow(() -> configMenuManager.openKitDetail(player, "empty"));
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(27, inv.getSize());
        // Name should still be shown
        assertNotNull(inv.getItem(4));
        assertEquals("empty", plainName(inv.getItem(4)));
    }

    @Test
    void openKitDetail_shouldHaveBackButtonAtSlot22() {
        Kit kit = new Kit("test", new ItemStack[]{new ItemStack(Material.STONE)});
        when(kitManager.getKit("test")).thenReturn(kit);

        Player player = server.addPlayer();
        configMenuManager.openKitDetail(player, "test");
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(22), "Back button should be present at slot 22");
        assertEquals(Material.ARROW, inv.getItem(22).getType());
    }

    @Test
    void openKitDetail_withMoreThan18Items_shouldNotOverflow() {
        ItemStack[] items = new ItemStack[20];
        for (int i = 0; i < 20; i++) {
            items[i] = new ItemStack(Material.STONE);
        }
        Kit kit = new Kit("bigkit", items);
        when(kitManager.getKit("bigkit")).thenReturn(kit);

        Player player = server.addPlayer();
        assertDoesNotThrow(() -> configMenuManager.openKitDetail(player, "bigkit"));
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(27, inv.getSize());
    }
}