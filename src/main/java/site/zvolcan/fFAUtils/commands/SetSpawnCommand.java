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
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.Sounds;

public final class SetSpawnCommand implements CommandExecutor {

    private final SpawnManager spawnManager;
    private final KitManager kitManager;
    private final PluginUtils utils;

    public SetSpawnCommand(SpawnManager spawnManager, KitManager kitManager, PluginUtils utils) {
        this.spawnManager = spawnManager;
        this.kitManager = kitManager;
        this.utils = utils;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("setspawn");
        literal.requires(ctx -> ctx.getSender().hasPermission("ffautils.commands.setspawn"));

        // /setspawn create <name> — creates a spawn at player's location
        literal.then(Commands.literal("create")
            .then(Commands.argument("name", StringArgumentType.word())
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    CommandSender sender = source.getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(MessagesManager.getInstance()
                                .getMessage("only-players-execute"));
                        return 1;
                    }
                    String spawnName = StringArgumentType.getString(ctx, "name").toLowerCase();
                    spawnManager.saveSpawn(spawnName, player.getLocation());
                    utils.message(player,
                            Sounds.SUCCESS_SOUND,
                            MessagesManager.getInstance().getMessage(
                                    "save-spawn", "{spawn}", spawnName
                            )
                    );
                    return 0;
                })
            )
        );

        // /setspawn allowkit <spawn> <kit> — adds kit to spawn's allowed list
        literal.then(Commands.literal("allowkit")
            .then(Commands.argument("spawn", StringArgumentType.word())
                .then(Commands.argument("kit", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        CommandSender sender = source.getSender();
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(MessagesManager.getInstance()
                                    .getMessage("only-players-execute"));
                            return 1;
                        }
                        String spawnName = StringArgumentType.getString(ctx, "spawn").toLowerCase();
                        String kitName = StringArgumentType.getString(ctx, "kit").toLowerCase();

                        if (!spawnManager.spawnExists(spawnName)) {
                            utils.message(player, Sounds.ERROR_SOUND,
                                    MessagesManager.getInstance().getMessage(
                                            "spawn-not-found", "{spawn}", spawnName
                                    ));
                            return 1;
                        }
                        if (kitManager.getKit(kitName) == null) {
                            utils.message(player, Sounds.ERROR_SOUND,
                                    MessagesManager.getInstance().getMessage(
                                            "kit-not-found", "{name}", kitName
                                    )
                            );
                            return 1;
                        }

                        spawnManager.addAllowedKit(spawnName, kitName);
                        utils.message(player, Sounds.SUCCESS_SOUND,
                                MessagesManager.getInstance().getMessage(
                                        "kit-added-to-spawn", "{kit}", kitName, "{spawn}", spawnName
                                ));
                        return 0;
                    })
                )
            )
        );

        // /setspawn removekit <spawn> <kit> — removes kit from spawn's allowed list
        literal.then(Commands.literal("removekit")
            .then(Commands.argument("spawn", StringArgumentType.word())
                .then(Commands.argument("kit", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        CommandSender sender = source.getSender();
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(MessagesManager.getInstance()
                                    .getMessage("only-players-execute"));
                            return 1;
                        }
                        String spawnName = StringArgumentType.getString(ctx, "spawn").toLowerCase();
                        String kitName = StringArgumentType.getString(ctx, "kit").toLowerCase();

                        if (!spawnManager.spawnExists(spawnName)) {
                            utils.message(player, Sounds.ERROR_SOUND,
                                    MessagesManager.getInstance().getMessage(
                                            "spawn-not-found", "{spawn}", spawnName
                                    ));
                            return 1;
                        }

                        spawnManager.removeAllowedKit(spawnName, kitName);
                        utils.message(player, Sounds.SUCCESS_SOUND,
                                MessagesManager.getInstance().getMessage(
                                        "kit-removed-from-spawn", "{kit}", kitName, "{spawn}", spawnName
                                ));
                        return 0;
                    })
                )
            )
        );

        // /setspawn delete <spawn> — deletes a spawn
        literal.then(Commands.literal("delete")
            .then(Commands.argument("spawn", StringArgumentType.word())
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    CommandSender sender = source.getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(MessagesManager.getInstance()
                                .getMessage("only-players-execute"));
                        return 1;
                    }
                    String spawnName = StringArgumentType.getString(ctx, "spawn").toLowerCase();

                    if (!spawnManager.spawnExists(spawnName)) {
                        utils.message(player, Sounds.ERROR_SOUND,
                                MessagesManager.getInstance().getMessage(
                                        "spawn-not-found", "{spawn}", spawnName
                                ));
                        return 1;
                    }

                    spawnManager.deleteSpawn(spawnName);
                    utils.message(player, Sounds.SUCCESS_SOUND,
                            MessagesManager.getInstance().getMessage(
                                    "spawn-deleted", "{spawn}", spawnName
                            ));
                    return 0;
                })
            )
        );

        return literal.build();
    }
}
