package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.inventory.ConfigMenuManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.PlayerState;
import site.zvolcan.fFAUtils.objects.Sounds;
import site.zvolcan.fFAUtils.FFAUtils;

public final class KitEditorCommand implements CommandExecutor {

    private final FFAUtils plugin;
    private final ConfigMenuManager configMenuManager;
    private final PlayersManager playersManager;

    public KitEditorCommand(FFAUtils plugin, ConfigMenuManager configMenuManager, PlayersManager playersManager) {
        this.plugin = plugin;
        this.configMenuManager = configMenuManager;
        this.playersManager = playersManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("ffakiteditor");

        literal.requires(ctx -> ctx.getSender() instanceof Player
                && ctx.getSender().hasPermission("ffautils.commands.ffakiteditor"));

        literal.executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            CommandSender sender = source.getSender();
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessagesManager.getInstance()
                        .getMessage("only-players-execute"));
                return 1;
            }

            FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);
            if (ffaPlayer.getState() == PlayerState.IN_FFA) {
                plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                        MessagesManager.getInstance().getMessage("player-already-in-ffa"));
                return 1;
            }

            configMenuManager.openKitEditor(player, 0);
            return 1;
        });

        return literal.build();
    }
}