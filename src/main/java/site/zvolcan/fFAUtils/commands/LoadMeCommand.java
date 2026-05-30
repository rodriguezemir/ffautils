package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;

public final class LoadMeCommand implements CommandExecutor {

    @Override
    public LiteralCommandNode<CommandSourceStack> execute(CommandSender sender, String[] args) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("loadme");

        literal.then(
                Commands.argument("spawn", StringArgumentType.string()).executes((ctx) -> {

                    return 0;
                })
        );

        return literal.build();
    }
}
