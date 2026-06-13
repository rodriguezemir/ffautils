package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.LobbyManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;

public final class SpawnCommand implements CommandExecutor {

    private final SpawnManager spawnManager;
    private final LobbyManager lobbyManager;

    public SpawnCommand(SpawnManager spawnManager, LobbyManager lobbyManager) {
        this.spawnManager = spawnManager;
        this.lobbyManager = lobbyManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("spawn")
                .executes(ctx -> {
                    final CommandSourceStack source = ctx.getSource();

                    if (source.getSender() instanceof Player player) {
                        player.teleport(spawnManager.getLobbySpawn());
                        player.setHealth(20);
                        lobbyManager.addLobbyItems(player);
                    }

                    return 1;
                });

        literal.requires(ctx -> ctx.getSender().hasPermission("ffautils.commands.spawn"));
        return literal.build();
    }
}
