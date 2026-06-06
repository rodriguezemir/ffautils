package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
                plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<red>Kit '" + name + "' not found.");
                return 1;
            }
            player.getInventory().setContents(kit.getContents());
            plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<green>Kit '" + name + "' has been applied.");

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
                    plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<red>Kit '" + name + "' already exists.");
                    return 1;
                }
                ItemStack[] contents = player.getInventory().getContents();
                Kit kit = new Kit(name, contents);
                kitManager.saveKit(name, kit);
                plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<green>Kit '" + name + "' has been created.");
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
                    plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<red>Kit '" + name + "' not found.");
                    return 1;
                }
                ItemStack[] contents = player.getInventory().getContents();
                Kit kit = new Kit(name, contents);
                kitManager.saveKit(name, kit);
                plugin.getUtils().message(player, Sounds.SUCCESS_SOUND, "<green>Kit '" + name + "' has been edited.");
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
                                    "{kit}",
                                    name
                            )
                    );
                    sender.sendMessage(Component.text().color(NamedTextColor.RED));
                    return 1;
                }
                sender.sendMessage(Component.text("Kit '" + name + "' has been deleted.").color(NamedTextColor.GREEN));
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
            sender.sendMessage(Component.text("Kits: " + String.join(", ", kits.keySet())).color(NamedTextColor.GREEN));
            return 1;
        }));

        return literal.build();
    }
}
