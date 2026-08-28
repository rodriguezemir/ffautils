package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.Fto10Manager;
import site.zvolcan.fFAUtils.managers.MessagesManager;

/** Brigadier command for Fto10 invitations and match abandonment. */
public final class Fto10Command implements CommandExecutor {

    private final FFAUtils plugin;
    private final Fto10Manager fto10Manager;

    public Fto10Command(FFAUtils plugin, Fto10Manager fto10Manager) {
        this.plugin = plugin;
        this.fto10Manager = fto10Manager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("fto10");
        literal.then(action("invite", this::invite));
        literal.then(action("accept", this::accept));
        literal.then(action("deny", this::deny));
        literal.then(action("leave", this::leave));
        return literal.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> action(String name, Action action) {
        return Commands.literal(name)
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> action.run(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"))));
    }

    private int invite(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance().getMessage("only-players-execute"));
            return 1;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            return error(player, "fto10-player-not-found", "{player}", name);
        }
        return result(player, fto10Manager.invite(player, target), name);
    }

    private int accept(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance().getMessage("only-players-execute"));
            return 1;
        }
        Player challenger = Bukkit.getPlayerExact(name);
        if (challenger == null) {
            return error(player, "fto10-player-not-found", "{player}", name);
        }
        return result(player, fto10Manager.accept(player, challenger), name);
    }

    private int deny(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance().getMessage("only-players-execute"));
            return 1;
        }
        Player challenger = Bukkit.getPlayerExact(name);
        if (challenger == null) {
            return error(player, "fto10-player-not-found", "{player}", name);
        }
        return result(player, fto10Manager.deny(player, challenger), name);
    }

    private int leave(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessagesManager.getInstance().getMessage("only-players-execute"));
            return 1;
        }
        Player opponent = Bukkit.getPlayerExact(name);
        if (opponent == null) {
            return error(player, "fto10-player-not-found", "{player}", name);
        }
        return result(player, fto10Manager.leave(player, opponent), name);
    }

    private int result(Player player, Fto10Manager.Result result, String name) {
        if (result == Fto10Manager.Result.SUCCESS) {
            return 1;
        }
        String key = switch (result) {
            case SELF -> "fto10-cannot-challenge-yourself";
            case NOT_IN_FFA -> "fto10-not-in-ffa";
            case ALREADY_BUSY -> "fto10-already-busy";
            case NO_INVITATION -> "fto10-no-invitation";
            case WRONG_PLAYER -> "fto10-wrong-player";
            case NOT_IN_MATCH -> "fto10-not-in-match";
            case SUCCESS -> "fto10-not-in-match";
        };
        return error(player, key, "{player}", name);
    }

    private int error(Player player, String key, String placeholder, String value) {
        player.sendMessage(MessagesManager.getInstance().getMessage(key, placeholder, value));
        return 1;
    }

    @FunctionalInterface
    private interface Action {
        int run(CommandSender sender, String name);
    }
}
