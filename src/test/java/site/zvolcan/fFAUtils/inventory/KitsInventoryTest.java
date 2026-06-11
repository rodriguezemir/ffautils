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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KitsInventoryTest {

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
    void openKits_withNoKits_shouldShowPlaceholder() {
        when(kitManager.getAllKits()).thenReturn(Collections.emptyMap());
        Player player = server.addPlayer();
        configMenuManager.openKits(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(54, inv.getSize());
        assertNotNull(inv.getItem(22), "Placeholder item should be present");
        assertEquals("No kits configured", plainName(inv.getItem(22)));
    }

    @Test
    void openKits_withOneKit_shouldShowKitItem() {
        Kit kit = new Kit("archer", new ItemStack[]{new ItemStack(Material.BOW)});
        Map<String, Kit> kits = new LinkedHashMap<>();
        kits.put("archer", kit);
        when(kitManager.getAllKits()).thenReturn(kits);

        Player player = server.addPlayer();
        configMenuManager.openKits(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(54, inv.getSize());
        assertNotNull(inv.getItem(0), "Kit item should be present");
        assertEquals("archer", plainName(inv.getItem(0)));
        assertTrue(inv.getItem(0).getItemMeta().getLore().stream()
                .anyMatch(l -> l.contains("1") || l.contains("item")));
    }

    @Test
    void openKits_shouldHaveBackButtonAtSlot49() {
        when(kitManager.getAllKits()).thenReturn(Collections.emptyMap());
        Player player = server.addPlayer();
        configMenuManager.openKits(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(49), "Back button should be present");
        assertEquals(Material.ARROW, inv.getItem(49).getType());
    }

    @Test
    void openKits_withMoreThan45Kits_shouldShowNextPageButton() {
        Map<String, Kit> kits = new LinkedHashMap<>();
        for (int i = 0; i < 46; i++) {
            Kit kit = new Kit("kit" + i, new ItemStack[]{new ItemStack(Material.STONE)});
            kits.put("kit" + i, kit);
        }
        when(kitManager.getAllKits()).thenReturn(kits);

        Player player = server.addPlayer();
        configMenuManager.openKits(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(52), "Next page button should be present");
        assertEquals(Material.ARROW, inv.getItem(52).getType());
    }

    @Test
    void openKits_onSecondPage_shouldShowPrevPageButton() {
        Map<String, Kit> kits = new LinkedHashMap<>();
        for (int i = 0; i < 46; i++) {
            Kit kit = new Kit("kit" + i, new ItemStack[]{new ItemStack(Material.STONE)});
            kits.put("kit" + i, kit);
        }
        when(kitManager.getAllKits()).thenReturn(kits);

        Player player = server.addPlayer();
        configMenuManager.openKits(player, 1);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(50), "Prev page button should be present");
        assertEquals(Material.ARROW, inv.getItem(50).getType());
    }

    @Test
    void openKits_onFirstPage_shouldNotShowPrevPageButton() {
        when(kitManager.getAllKits()).thenReturn(Collections.emptyMap());
        Player player = server.addPlayer();
        configMenuManager.openKits(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNull(inv.getItem(50), "Prev page button should not be present on first page");
    }

    @Test
    void openKitDetail_shouldPreservePreviousPage() {
        Kit kit = new Kit("archer", new ItemStack[]{new ItemStack(Material.BOW)});
        Map<String, Kit> kits = new LinkedHashMap<>();
        kits.put("archer", kit);
        when(kitManager.getAllKits()).thenReturn(kits);
        when(kitManager.getKit("archer")).thenReturn(kit);

        Player player = server.addPlayer();
        new KitDetailInventory(configMenuManager, "archer", 3).open(player);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertEquals(27, inv.getSize());
        assertNotNull(inv.getItem(22), "Back button should be present");
        assertEquals("archer", plainName(inv.getItem(4)));
    }

    @Test
    void openKits_withMultipleItems_shouldShowItemCount() {
        Kit kit = new Kit("warrior", new ItemStack[]{
                new ItemStack(Material.IRON_SWORD),
                new ItemStack(Material.IRON_CHESTPLATE),
                new ItemStack(Material.SHIELD)
        });
        Map<String, Kit> kits = new LinkedHashMap<>();
        kits.put("warrior", kit);
        when(kitManager.getAllKits()).thenReturn(kits);

        Player player = server.addPlayer();
        configMenuManager.openKits(player, 0);
        Inventory inv = player.getOpenInventory().getTopInventory();
        assertNotNull(inv.getItem(0));
        assertTrue(inv.getItem(0).getItemMeta().getLore().stream()
                .anyMatch(l -> l.contains("3") || l.contains("items")),
                "Lore should contain item count");
    }
}