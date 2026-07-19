package site.zvolcan.fFAUtils.managers;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import site.zvolcan.fFAUtils.FFAPlaceholders;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.*;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;

import java.util.ArrayList;
import java.util.List;

public final class CommandManager {

    private final FFAUtils plugin;
    private final KitManager kitManager;
    private final SpawnManager spawnManager;
    private final LobbyManager lobbyManager;
    private final FFAPlaceholders ffaPlaceholders;
    private final PlayersManager playersManager;
    private final DeathEventManager deathEventManager;

    public CommandManager(FFAUtils plugin, KitManager kitManager, SpawnManager spawnManager, LobbyManager lobbyManager,
            FFAPlaceholders ffaPlaceholders, PlayersManager playersManager,
            DeathEventManager deathEventManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.spawnManager = spawnManager;
        this.lobbyManager = lobbyManager;
        this.ffaPlaceholders = ffaPlaceholders;
        this.playersManager = playersManager;
        this.deathEventManager = deathEventManager;
        registerCommands();
    }

    public void registerCommands() {
        // TODO - Agregar permisos a los comandos
        final List<CommandExecutor> list = new ArrayList<>();
        list.add(new KitCommand(plugin, kitManager));
        list.add(new LoadMeCommand(plugin, kitManager, spawnManager, playersManager));
        list.add(new SpawnCommand(spawnManager, lobbyManager, playersManager));
        list.add(new DeadCommand());
        list.add(new MainCommand(plugin.getUtils(), ffaPlaceholders, plugin.getMessagesManager(), kitManager,
                spawnManager, deathEventManager));
        list.add(new SetSpawnCommand(spawnManager, kitManager, plugin.getUtils()));
        list.add(new KitEditorCommand(plugin, kitManager, playersManager));

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, (cmd) -> {
            for (CommandExecutor executor : list) {
                cmd.registrar().register(executor.execute());
            }
        });
    }

}
