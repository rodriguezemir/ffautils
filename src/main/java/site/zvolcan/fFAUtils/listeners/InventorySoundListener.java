package site.zvolcan.fFAUtils.listeners;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

import fr.mrmicky.fastinv.FastInv;

public class InventorySoundListener implements org.bukkit.event.Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof FastInv && e.getClickedInventory() != null) {
            ((Player) e.getWhoClicked()).playSound(e.getWhoClicked().getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f,
                    1.0f);
        }
    }

}
