package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.Sounds;

public final class KitCommand implements CommandExecutor {

    private final FFAUtils plugin;
    private final KitManager kitManager;

    public KitCommand(FFAUtils plugin, KitManager kitManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("kit");

        literal.requires(ctx -> ctx.getSender().hasPermission("ffautils.commands.kit")); // Permission: ffautils.commands.kit
        literal.then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            CommandSender sender = source.getSender();
            if (!(sender instanceof Player player)) {
                sender.sendMessage(MessagesManager.getInstance()
                        .getMessage("only-players-execute"));
                return 1;
            }
            String name = StringArgumentType.getString(ctx, "name");
            Kit kit = kitManager.getKit(name);
            if (kit == null) {
                plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                        MessagesManager.getInstance().getMessage(
                                "kit-not-found", "{name}", name
                        )
                );
                return 1;
            }
            player.getInventory().setContents(kit.getContents());
            plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                    MessagesManager.getInstance().getMessage(
                            "kit-applied", "{name}", name
                    )
            );

            return 1;
        }));

        literal.then(Commands.literal("create")
            .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                CommandSender sender = source.getSender();
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessagesManager.getInstance()
                            .getMessage("only-players-execute"));
                    return 1;
                }
                String name = StringArgumentType.getString(ctx, "name");
                if (kitManager.getKit(name) != null) {
                    plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                            MessagesManager.getInstance().getMessage(
                                    "kit-already-exists", "{name}", name
                            )
                    );
                    return 1;
                }
                ItemStack[] contents = player.getInventory().getContents();
                Kit kit = new Kit(name, contents);
                kitManager.saveKit(name, kit);
                plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                        MessagesManager.getInstance().getMessage(
                                "kit-created", "{name}", name
                        )
                );
                return 1;
            }))
        );

        literal.then(Commands.literal("edit")
            .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                CommandSender sender = source.getSender();
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MessagesManager.getInstance()
                            .getMessage("only-players-execute"));
                    return 1;
                }
                String name = StringArgumentType.getString(ctx, "name");
                if (kitManager.getKit(name) == null) {
                    plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                            MessagesManager.getInstance().getMessage(
                                    "kit-not-found", "{name}", name
                            )
                    );
                    return 1;
                }
                ItemStack[] contents = player.getInventory().getContents();
                Kit kit = new Kit(name, contents);
                kitManager.saveKit(name, kit);
                plugin.getUtils().message(player, Sounds.SUCCESS_SOUND,
                        MessagesManager.getInstance().getMessage(
                                "kit-edited", "{name}", name
                        )
                );
                return 1;
            }))
        );

        literal.then(Commands.literal("delete")
            .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                CommandSender sender = source.getSender();
                String name = StringArgumentType.getString(ctx, "name");
                boolean deleted = kitManager.deleteKit(name);
                if (!deleted) {
                    plugin.getUtils().message(
                            sender,
                            MessagesManager.getInstance().getMessage(
                                    "kit-not-found",
                                    "{name}",
                                    name
                            )
                    );
                    return 1;
                }
                plugin.getUtils().message(sender, Sounds.SUCCESS_SOUND,
                        MessagesManager.getInstance().getMessage(
                                "kit-deleted", "{name}", name
                        )
                );
                return 1;
            }))
        );

        literal.then(Commands.literal("list").executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            CommandSender sender = source.getSender();
            var kits = kitManager.getAllKits();
            if (kits.isEmpty()) {
                plugin.getUtils().message(
                        sender,
                        MessagesManager.getInstance().getMessage(
                                "no-kits-available"
                        )
                );
                return 1;
            }
            plugin.getUtils().message(sender,
                    MessagesManager.getInstance().getMessage(
                            "kits-list", "{kits}", String.join(", ", kits.keySet())
                    )
            );
            return 1;
        }));

        return literal.build();
    }
}
