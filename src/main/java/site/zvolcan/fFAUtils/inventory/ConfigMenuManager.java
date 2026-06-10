package site.zvolcan.fFAUtils.inventory;

import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;

public class ConfigMenuManager {

    private final SpawnManager spawnManager;
    private final KitManager kitManager;

    public ConfigMenuManager(SpawnManager spawnManager, KitManager kitManager) {
        this.spawnManager = spawnManager;
        this.kitManager = kitManager;
    }

    public void openMain(Player player) {
        new MainInventory(this).open(player);
    }

    public void openSpawns(Player player, int page) {
        new SpawnsInventory(this, page).open(player);
    }

    public void openKits(Player player, int page) {
        new KitsInventory(this, page).open(player);
    }

    public void openSpawnDetail(Player player, String spawnName) {
        // Default to page 0 when opening detail from outside
        new SpawnDetailInventory(this, spawnName, 0).open(player);
    }

    public void openSpawnDetail(Player player, String spawnName, int previousPage) {
        new SpawnDetailInventory(this, spawnName, previousPage).open(player);
    }

    public void openKitDetail(Player player, String kitName) {
        // Default to page 0 when opening detail from outside
        new KitDetailInventory(this, kitName, 0).open(player);
    }

    public void openKitDetail(Player player, String kitName, int previousPage) {
        new KitDetailInventory(this, kitName, previousPage).open(player);
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }
}