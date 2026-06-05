package site.zvolcan.fFAUtils.commands.abs;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

@FunctionalInterface
public interface CommandExecutor {
    LiteralCommandNode<CommandSourceStack> execute();
}
