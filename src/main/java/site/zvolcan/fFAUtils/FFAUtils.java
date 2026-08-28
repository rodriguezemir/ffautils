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
import site.zvolcan.fFAUtils.listeners.PlayerRegionListener;
import site.zvolcan.fFAUtils.managers.*;
import site.zvolcan.fFAUtils.inventory.KitEditContentsInventory;
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
        private RegionManager regionManager;
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
        private BlockedCommandsManager blockedCommandsManager;
        @Getter
        private TierManager tierManager;
        @Getter
        private Fto10Manager fto10Manager;
        @Override
        public void onEnable() {
                instance = this;

                sendConsole("");
                sendConsole("§b███████╗███████╗ █████╗ §3██╗   ██╗████████╗██╗██╗     ███████╗");
                sendConsole("§b██╔════╝██╔════╝██╔══██╗§3██║   ██║╚══██╔══╝██║██║     ██╔════╝");
                sendConsole("§b█████╗  █████╗  ███████║§3██║   ██║   ██║   ██║██║     ███████╗");
                sendConsole("§b██╔══╝  ██╔══╝  ██╔══██║§3██║   ██║   ██║   ██║██║     ╚════██║");
                sendConsole("§b██║     ██║     ██║  ██║§3╚██████╔╝   ██║   ██║███████╗███████║");
                sendConsole("§b╚═╝     ╚═╝     ╚═╝  ╚═╝§3 ╚═════╝    ╚═╝   ╚═╝╚══════╝╚══════╝");
                sendConsole("");
                sendConsole("§7Version: §b" + getDescription().getVersion());
                sendConsole("");

                saveDefaultConfig();
                utils = new PluginUtils(this,
                                YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"))
                                                .getString(
                                                                "messages-prefix",
                                                                "<b><gradient:#5472F4:#27A2C1>FFAUTILS</gradient></b> <dark_gray>⯮</dark_gray> "));
                spawnManager = new SpawnManager(this);
                spawnManager.registerSpawns();
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading Spawns");
                kitManager = new KitManager(this);
                kitManager.registerKits();
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading Kits");
                regionManager = new RegionManager(this);
                regionManager.registerRegions();
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading Regions");
                FastInvManager.register(this);
                combatLogManager = new CombatLogManager(this, getConfig().getLong("combatlog.timeout-ticks",
                                getConfig().getLong("duration-combat-log", 15) * 20L));
                combatLogManager.startCleanupTask();
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading CombatLog");
                blockedCommandsManager = new BlockedCommandsManager(this);
                saveResource("blocked-commands.yml", false);
                blockedCommandsManager.loadBlockedCommands();
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading BlockedCommands");
                lobbyManager = new LobbyManager(this);
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading LobbyManager");
                playersManager = new PlayersManager();
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading PlayersManager");
                statsManager = new StatsManager(this);
                statsManager.init();
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading StatsManager");
                messagesManager = new MessagesManager(this);
                messagesManager.registerMessages();
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading Messages");
                messagesManager.addDefault("spawn-no-permission",
                                "<red>No tienes permiso para entrar a este spawn.");
                messagesManager.addDefault("region-corner-set",
                                "<green>Esquina <yellow>{corner}</yellow> de la región del kit <yellow>{kit}</yellow> establecida. Usa /kitregion {kit} pos{other} para completarla.");
                messagesManager.addDefault("region-saved",
                                "<green>Región del kit <yellow>{kit}</yellow> guardada.");
                messagesManager.addDefault("region-deleted",
                                "<green>Región del kit <yellow>{kit}</yellow> eliminada.");
                messagesManager.addDefault("region-not-found",
                                "<red>El kit <yellow>{kit}</yellow> no tiene región definida.");
                messagesManager.addDefault("region-world-mismatch",
                                "<red>Las dos esquinas deben estar en el mismo mundo.");
                messagesManager.addDefault("region-info",
                                "<gray>Región de <yellow>{kit}</yellow><gray>: mundo <yellow>{world}</yellow>, min (<yellow>{min}</yellow>), max (<yellow>{max}</yellow>).");
                messagesManager.addDefault("region-limit-reached",
                                "<red>¡Has llegado al límite de la zona de este kit!");
                messagesManager.addDefault("fto10-player-not-found",
                                "<red>El jugador <yellow>{player}</yellow> no esta conectado.");
                messagesManager.addDefault("fto10-invite-sent",
                                "<green>Has invitado a <yellow>{player}</yellow> a un Fto10.");
                messagesManager.addDefault("fto10-invite-received",
                                "<yellow>{player}</yellow> te ha invitado a un Fto10. Usa <white>/fto10 accept {player}</white> o <white>/fto10 deny {player}</white>.");
                messagesManager.addDefault("fto10-invite-denied",
                                "<yellow>{player}</yellow> ha rechazado la invitacion de Fto10.");
                messagesManager.addDefault("fto10-invite-denied-by-you",
                                "<green>Has rechazado la invitacion de Fto10 de <yellow>{player}</yellow>.");
                messagesManager.addDefault("fto10-started",
                                "<green>Fto10 iniciado contra <yellow>{player}</yellow>.");
                messagesManager.addDefault("fto10-score",
                                "<aqua>Fto10 <white>{first}</white> <yellow>{first_score}</yellow> - <yellow>{second_score}</yellow> <white>{second}</white>");
                messagesManager.addDefault("fto10-actionbar",
                                "<head:{player}:true> <green>{points} <gray>- <red>{enemy-points} <reset><head:{enemy}:true>");
                messagesManager.addDefault("fto10-round-finished",
                                "<gold>Ronda para <yellow>{winner}</yellow>. Marcador: <white>{winner_score} - {loser_score}</white>.");
                messagesManager.addDefault("fto10-finished",
                                "<green>Fto10 terminado: <yellow>{winner}</yellow> gano <white>{winner_score} - {loser_score}</white> contra <yellow>{loser}</yellow>.");
                messagesManager.addDefault("fto10-cannot-challenge-yourself",
                                "<red>No puedes retarte a ti mismo.");
                messagesManager.addDefault("fto10-not-in-ffa",
                                "<red>Debes estar dentro de un Spawn y Kit para invitar a un Fto10.");
                messagesManager.addDefault("fto10-already-busy",
                                "<red>Tu o el jugador seleccionado ya esta en un Fto10.");
                messagesManager.addDefault("fto10-no-invitation",
                                "<red>No tienes una invitacion de Fto10 de <yellow>{player}</yellow>.");
                messagesManager.addDefault("fto10-wrong-player",
                                "<red>Debes indicar al rival de tu Fto10.");
                messagesManager.addDefault("fto10-not-in-match",
                                "<red>No estas en un Fto10.");
                messagesManager.addDefault("fto10-command-blocked",
                                "<red>No puedes salir o cambiar tu kit durante un Fto10. Usa <white>/fto10 leave</white> con el nombre de tu rival.");
                TierManager.registerMessageDefaults(messagesManager);
                tierManager = new TierManager(this);
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading TierManager");
                if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                        ffaPlaceholders = new FFAPlaceholders(this, statsManager, playersManager);
                        ffaPlaceholders.register();
                }
                deathEventManager = new DeathEventManager(this);
                saveResource("death-messages.yml", false);
                deathEventManager.registerDeathMessages();
                fto10Manager = new Fto10Manager(this, playersManager, kitManager, lobbyManager);
                commandManager = new CommandManager(this, kitManager, spawnManager, regionManager, lobbyManager,
                                ffaPlaceholders, playersManager, deathEventManager, fto10Manager);
                sendConsole("§8[§bFFAUtils§8] §a✔ §7Loading Commands");
                getServer().getPluginManager().registerEvents(
                                new PlayerConnectListener(this, lobbyManager, playersManager, spawnManager,
                                                statsManager),
                                this);
                getServer().getPluginManager().registerEvents(lobbyManager, this);
                getServer().getPluginManager().registerEvents(
                                new PlayerDeathListener(this, deathEventManager, spawnManager, combatLogManager,
                                                statsManager,
                                                playersManager, lobbyManager, kitManager, fto10Manager),
                                this);
                getServer().getPluginManager().registerEvents(new PlayerInteractiveListener(playersManager), this);
                getServer().getPluginManager().registerEvents(
                                new PlayerCommandBlockerListener(this), this);
                getServer().getPluginManager().registerEvents(new InventorySoundListener(), this);
                getServer().getPluginManager().registerEvents(new PlayerRegionListener(this), this);
                getServer().getPluginManager().registerEvents(new KitEditContentsInventory.SessionListener(), this);
        }

        private void sendConsole(String message) {
                getServer().getConsoleSender().sendMessage(message);
        }

        @Override
        public void onDisable() {
                // Give every editing player their real inventory back before shutting down.
                KitEditContentsInventory.restoreAllSessions();
                if (fto10Manager != null) {
                        fto10Manager.shutdown();
                }
                combatLogManager.stopCleanupTask();
                statsManager.close();
                if (tierManager != null) {
                        tierManager.shutdown();
                }
                messagesManager.saveMessages();
        }
}
