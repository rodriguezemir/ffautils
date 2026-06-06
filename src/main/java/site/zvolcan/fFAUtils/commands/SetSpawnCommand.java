package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.putindeer.api.util.PluginUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.Sounds;

public final class SetSpawnCommand implements CommandExecutor {

    private final SpawnManager spawnManager;
    private final PluginUtils utils;

    public SetSpawnCommand(SpawnManager spawnManager, PluginUtils utils) {
        this.spawnManager = spawnManager;
        this.utils = utils;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("setspawn");
        literal.then(Commands.argument("name", StringArgumentType.string()).executes((ctx) -> {
            CommandSourceStack source = ctx.getSource();
            CommandSender sender = source.getSender();
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessagesManager.getInstance()
                        .getMessage("only-players-execute"));
                return 1;
            }
            String spawnName = StringArgumentType.getString(ctx, "name");
            spawnManager.saveSpawn(spawnName, player.getLocation());
            utils.message(player,
                    Sounds.SUCCESS_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "save-spawn", "{spawn}", spawnName
                    )
            );
            return 0;
        }));

        return literal.build();
    }
}
