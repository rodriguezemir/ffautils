package site.zvolcan.fFAUtils.inventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import site.zvolcan.fFAUtils.objects.Kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KitEditorInventoryTest {

    @Test
    void countItems_ignoresEmptySlotsAndAirStacks() {
        ItemStack air = mock(ItemStack.class);
        when(air.getType()).thenReturn(Material.AIR);
        ItemStack stone = mock(ItemStack.class);
        when(stone.getType()).thenReturn(Material.STONE);
        Kit kit = new Kit("test", new ItemStack[]{null, air, stone});

        assertEquals(1, KitEditorInventory.countItems(kit));
    }

    @Test
    void plainText_doesNotInterpretKitNamesAsMiniMessage() {
        assertEquals(Component.text("<red>admin</red>").decoration(TextDecoration.ITALIC, false),
                KitEditorInventory.plainText("<red>admin</red>"));
    }
}
