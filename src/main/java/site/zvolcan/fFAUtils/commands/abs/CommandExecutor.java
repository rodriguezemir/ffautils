package site.zvolcan.fFAUtils.commands.abs;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;

@FunctionalInterface
public interface CommandExecutor {
    LiteralCommandNode<CommandSourceStack> execute();
}
