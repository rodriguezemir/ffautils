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

    public CommandManager(FFAUtils plugin, KitManager kitManager, SpawnManager spawnManager, LobbyManager lobbyManager, FFAPlaceholders ffaPlaceholders, PlayersManager playersManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.spawnManager = spawnManager;
        this.lobbyManager = lobbyManager;
        this.ffaPlaceholders = ffaPlaceholders;
        this.playersManager = playersManager;
        registerCommands();
    }

    public void registerCommands() {
        // TODO - Agregar permisos a los comandos
        final List<CommandExecutor> list = new ArrayList<>();
        list.add(new KitCommand(plugin, kitManager));
        list.add(new LoadMeCommand(plugin, kitManager, spawnManager, playersManager));
        list.add(new SpawnCommand(spawnManager, lobbyManager));
        list.add(new DeadCommand());
        list.add(new MainCommand(plugin.getUtils(), ffaPlaceholders, plugin.getMessagesManager(), kitManager, spawnManager));
        list.add(new SetSpawnCommand(spawnManager, kitManager, plugin.getUtils()));

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, (cmd) -> {
            for (CommandExecutor executor : list) {
                cmd.registrar().register(executor.execute());
            }
        });
    }

}
