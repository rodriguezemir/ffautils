package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.LobbyManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;

public final class SpawnCommand implements CommandExecutor {

    private final SpawnManager spawnManager;
    private final LobbyManager lobbyManager;
    private final PlayersManager playersManager;

    public SpawnCommand(SpawnManager spawnManager, LobbyManager lobbyManager, PlayersManager playersManager) {
        this.spawnManager = spawnManager;
        this.lobbyManager = lobbyManager;
        this.playersManager = playersManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("spawn")
                .executes(ctx -> {
                    final CommandSourceStack source = ctx.getSource();

                    if (source.getSender() instanceof Player player) {
                        player.teleport(spawnManager.getLobbySpawn());
                        player.setHealth(20);
                        player.getActivePotionEffects().forEach(e -> {
                            player.removePotionEffect(e.getType());
                        });
                        FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);
                        ffaPlayer.setState(PlayerState.LOBBY);
                        lobbyManager.addLobbyItems(player);
                    }

                    return 1;
                });

        literal.requires(ctx -> ctx.getSender().hasPermission("ffautils.commands.spawn"));
        return literal.build();
    }
}
