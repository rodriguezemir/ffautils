package site.zvolcan.fFAUtils;

import lombok.Getter;
import me.putindeer.api.util.PluginUtils;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import site.zvolcan.fFAUtils.listeners.PlayerConnectListener;
import site.zvolcan.fFAUtils.listeners.PlayerDeathListener;
import site.zvolcan.fFAUtils.managers.*;
import site.zvolcan.fFAUtils.inventory.ConfigMenuManager;
import fr.mrmicky.fastinv.FastInvManager;

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
    @Getter
    private FFAPlaceholders ffaPlaceholders;
    @Getter
    private MessagesManager messagesManager;
    @Getter
    private ConfigMenuManager configMenuManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        utils = new PluginUtils(this, YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml")).getString("messages-prefix", "<b><gradient:#5472F4:#27A2C1>FFAUTILS</gradient></b> <dark_gray>▶️</dark_gray> "));
        spawnManager = new SpawnManager(this);
        spawnManager.registerSpawns();
        kitManager = new KitManager(this);
        kitManager.registerKits();
        FastInvManager.register(this);
        configMenuManager = new ConfigMenuManager(spawnManager, kitManager);
        combatLogManager = new CombatLogManager(this, getConfig().getLong("combatlog.timeout-ticks", 300L));
        combatLogManager.startCleanupTask();
        lobbyManager = new LobbyManager(this);
        playersManager = new PlayersManager();
        statsManager = new StatsManager(this);
        statsManager.init();
        messagesManager = new MessagesManager(this);
        messagesManager.registerMessages();
        ffaPlaceholders = new FFAPlaceholders(this, statsManager);
        ffaPlaceholders.register();
        commandManager = new CommandManager(this, kitManager, spawnManager, lobbyManager, ffaPlaceholders, playersManager, configMenuManager);
        getServer().getPluginManager().registerEvents(new PlayerConnectListener(this, lobbyManager, playersManager, spawnManager, statsManager), this);
        getServer().getPluginManager().registerEvents(lobbyManager, this);
        
        deathEventManager = new DeathEventManager(this);
        saveResource("death-messages.yml", false);
        deathEventManager.registerDeathMessages();
        getServer().getPluginManager().registerEvents(
            new PlayerDeathListener(deathEventManager, spawnManager, combatLogManager, statsManager, playersManager), this
        );
    }

    @Override
    public void onDisable() {
        statsManager.close();
        messagesManager.saveMessages();
    }
}
