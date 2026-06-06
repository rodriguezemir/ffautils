package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.putindeer.api.util.PluginUtils;
import org.bukkit.command.CommandSender;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.objects.Sounds;

public final class MainCommand implements CommandExecutor {

    private final PluginUtils utils;

    public MainCommand(PluginUtils utils) {
        this.utils = utils;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("ffautils");

        literal.then(Commands.literal("reload").executes((ctx) -> {
            CommandSourceStack source = ctx.getSource();
            CommandSender sender = source.getSender();

            // TODO - agregar reinicios

            utils.message(
                    sender,
                    Sounds.SUCCESS_SOUND,
                    "<green>FFAUtils has been reloaded."
            );
            return 0;
        }));

        return literal.build();
    }
}
