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
        // TODO: implement later
    }

    public void openKits(Player player, int page) {
        // TODO: implement later
    }

    public void openSpawnDetail(Player player, String spawnName) {
        // TODO: implement later
    }

    public void openKitDetail(Player player, String kitName) {
        // TODO: implement later
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public KitManager getKitManager() {
        return kitManager;
    }
}