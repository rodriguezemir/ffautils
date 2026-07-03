package site.zvolcan.fFAUtils;

import lombok.Getter;
import me.putindeer.api.util.PluginUtils;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import site.zvolcan.fFAUtils.listeners.InventorySoundListener;
import site.zvolcan.fFAUtils.listeners.PlayerCommandBlockerListener;
import site.zvolcan.fFAUtils.listeners.PlayerConnectListener;
import site.zvolcan.fFAUtils.listeners.PlayerDeathListener;
import site.zvolcan.fFAUtils.listeners.PlayerInteractiveListener;
import site.zvolcan.fFAUtils.listeners.PlayerTotemDeathListener;
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
        @Getter
        private BlockedCommandsManager blockedCommandsManager;
        @Getter
        private VanishManager vanishManager;

        @Override
        public void onEnable() {
                instance = this;

                getLogger().info("████████████████████████████████████████████████");
                getLogger().info("█▓▄▄▓█▓▄▄▓██▀▓██▓▓██▓▄█▓▄▓▄▓█▓▄█▓▄▓███▓▄▄▄▄█");
                getLogger().info("██▓▄████▓▄████▓▀▓███▓██▓████▓████▓███▓██▀█▄▄▄▄▓█");
                getLogger().info("▀▄▄▄▀▀▀▄▄▄▀▀▀▄▄▀▄▄▀▀▄▄▄▄▀▀▀▄▄▄▀▀▄▄▄▀▄▄▄▄▄▀▄▄▄▄▄▀");

                saveDefaultConfig();
                utils = new PluginUtils(this,
                                YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"))
                                                .getString(
                                                                "messages-prefix",
                                                                "<b><gradient:#5472F4:#27A2C1>FFAUTILS</gradient></b> <dark_gray>⯮</dark_gray> "));
                spawnManager = new SpawnManager(this);
                spawnManager.registerSpawns();
                getLogger().info("Loading Spawns");
                kitManager = new KitManager(this);
                kitManager.registerKits();
                getLogger().info("Loading Kits");
                FastInvManager.register(this);
                configMenuManager = new ConfigMenuManager(spawnManager, kitManager);
                getLogger().info("Loading ConfigMenu");
                combatLogManager = new CombatLogManager(this, getConfig().getLong("combatlog.timeout-ticks",
                                getConfig().getLong("duration-combat-log", 15) * 20L));
                combatLogManager.startCleanupTask();
                getLogger().info("Loading CombatLog");
                blockedCommandsManager = new BlockedCommandsManager(this);
                saveResource("blocked-commands.yml", false);
                blockedCommandsManager.loadBlockedCommands();
                getLogger().info("Loading BlockedCommands");
                lobbyManager = new LobbyManager(this);
                getLogger().info("Loading LobbyManager");
                playersManager = new PlayersManager();
                getLogger().info("Loading PlayersManager");
                statsManager = new StatsManager(this);
                statsManager.init();
                getLogger().info("Loading StatsManager");
                messagesManager = new MessagesManager(this);
                messagesManager.registerMessages();
                getLogger().info("Loading Messages");
                if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                        ffaPlaceholders = new FFAPlaceholders(this, statsManager);
                        ffaPlaceholders.register();
                }
                deathEventManager = new DeathEventManager(this);
                saveResource("death-messages.yml", false);
                deathEventManager.registerDeathMessages();
                commandManager = new CommandManager(this, kitManager, spawnManager, lobbyManager, ffaPlaceholders,
                                playersManager, configMenuManager, deathEventManager);
                getLogger().info("Loading Commands");
                getServer().getPluginManager().registerEvents(
                                new PlayerConnectListener(this, lobbyManager, playersManager, spawnManager,
                                                statsManager),
                                this);
                getServer().getPluginManager().registerEvents(lobbyManager, this);
                getServer().getPluginManager().registerEvents(
                                new PlayerDeathListener(this, deathEventManager, spawnManager, combatLogManager,
                                                statsManager,
                                                playersManager, lobbyManager, kitManager),
                                this);
                getServer().getPluginManager().registerEvents(new PlayerInteractiveListener(playersManager), this);
                getServer().getPluginManager().registerEvents(
                                new PlayerCommandBlockerListener(this), this);
                getServer().getPluginManager().registerEvents(
                                new PlayerTotemDeathListener(this, spawnManager, playersManager, lobbyManager),
                                this);
                getServer().getPluginManager().registerEvents(new InventorySoundListener(), this);
                vanishManager = new VanishManager(this, playersManager);
                getServer().getPluginManager().registerEvents(vanishManager, this);
                vanishManager.startRefreshTask();
                getLogger().info("Loading VanishManager");
        }

        @Override
        public void onDisable() {
                combatLogManager.stopCleanupTask();
                if (vanishManager != null) {
                        vanishManager.stopRefreshTask();
                }
                statsManager.close();
                messagesManager.saveMessages();
        }
}
