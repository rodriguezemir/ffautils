package site.zvolcan.fFAUtils;

import lombok.Getter;
import me.putindeer.api.util.PluginUtils;
import org.bukkit.plugin.java.JavaPlugin;
import site.zvolcan.fFAUtils.listeners.PlayerConnectListener;
import site.zvolcan.fFAUtils.listeners.PlayerDeathListener;
import site.zvolcan.fFAUtils.managers.*;

public class FFAUtils extends JavaPlugin {

    @Getter
    private static FFAUtils instance;

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
    @Getter
    private CommandManager commandManager;
    @Getter
    private StatsManager statsManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        utils = new PluginUtils(this, getConfig().getString("messages-prefix", "<red><b>FFA <reset>"));
        spawnManager = new SpawnManager(this);
        spawnManager.registerSpawns();
        kitManager = new KitManager(this);
        kitManager.registerKits();
        combatLogManager = new CombatLogManager(this, getConfig().getLong("combatlog.timeout-ticks", 300L));
        combatLogManager.startCleanupTask();
        lobbyManager = new LobbyManager(this);
        commandManager = new CommandManager(this, kitManager, spawnManager, lobbyManager);
        playersManager = new PlayersManager();
        statsManager = new StatsManager(this);
        statsManager.init();
        getServer().getPluginManager().registerEvents(new PlayerConnectListener(this, lobbyManager, playersManager, spawnManager, statsManager), this);
        getServer().getPluginManager().registerEvents(lobbyManager, this);
        
        deathEventManager = new DeathEventManager(this);
        saveResource("death-messages.yml", false);
        deathEventManager.registerDeathMessages();
        getServer().getPluginManager().registerEvents(
            new PlayerDeathListener(deathEventManager, spawnManager, combatLogManager, statsManager), this
        );
    }

    @Override
    public void onDisable() {
        statsManager.close();
    }
}
