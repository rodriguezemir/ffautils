package site.zvolcan.fFAUtils;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import me.putindeer.api.util.PluginUtils;
import site.zvolcan.fFAUtils.listeners.PlayerConnectListener;
import site.zvolcan.fFAUtils.listeners.PlayerDeathListener;
import site.zvolcan.fFAUtils.managers.*;

public class FFAUtils extends JavaPlugin {

    @Getter
    private PluginUtils utils;
    @Getter
    private SpawnManager spawnManager;
    @Getter
    private KitManager kitManager;
    @Getter
    private CombatLogManager combatLogManager;
    @Getter
    private LobbyManager lobbyManager;
    @Getter
    private PlayersManager playersManager;
    @Getter
    private DeathEventManager deathEventManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        utils = new PluginUtils(this, getConfig().getString("messages-prefix", "<red><b>FFA <reset>"));
        spawnManager = new SpawnManager(this);
        spawnManager.registerSpawns();
        kitManager = new KitManager(this);
        kitManager.registerKits();
        combatLogManager = new CombatLogManager(this, getConfig().getLong("combatlog.timeout-ticks", 300L));
        combatLogManager.startCleanupTask();
        lobbyManager = new LobbyManager(this);
        getServer().getPluginManager().registerEvents(new PlayerConnectListener(this, lobbyManager, playersManager), this);
        getServer().getPluginManager().registerEvents(lobbyManager, this);
        
        deathEventManager = new DeathEventManager(this);
        saveResource("death-events.yml", false);
        deathEventManager.registerDeathMessages();
        getServer().getPluginManager().registerEvents(
            new PlayerDeathListener(deathEventManager, combatLogManager), this
        );
    }

    @Override
    public void onDisable() {
    }
}
