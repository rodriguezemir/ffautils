package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.putindeer.api.util.PluginUtils;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAPlaceholders;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.Sounds;
import site.zvolcan.fFAUtils.inventory.ConfigMenuManager;

public final class MainCommand implements CommandExecutor {

    private final PluginUtils utils;
    private final FFAPlaceholders ffaPlaceholders;
    private final MessagesManager messagesManager;
    private final KitManager kitManager;
    private final SpawnManager spawnManager;
    private final ConfigMenuManager configMenuManager;

    public MainCommand(PluginUtils utils, FFAPlaceholders ffaPlaceholders, MessagesManager messagesManager,
            KitManager kitManager, SpawnManager spawnManager, ConfigMenuManager configMenuManager) {
        this.utils = utils;
        this.ffaPlaceholders = ffaPlaceholders;
        this.messagesManager = messagesManager;
        this.kitManager = kitManager;
        this.spawnManager = spawnManager;
        this.configMenuManager = configMenuManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("ffautils");

        literal.requires(ctx -> ctx.getSender() instanceof Player
                && ctx.getSender().hasPermission("ffautils.commands.ffautils"));
        literal.executes(ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            Player player = (Player) sender;
            configMenuManager.openMain(player);
            return 0;
        });
        literal.then(Commands.literal("reload").executes((ctx) -> {
            CommandSourceStack source = ctx.getSource();
            CommandSender sender = source.getSender();

            final Logger logger = FFAUtils.getInstance().getLogger();
            logger.info("Reloading Plugin...");

            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                ffaPlaceholders.register();
                logger.info("Loading Placeholders.");
            }
            messagesManager.registerMessages();
            logger.info("Loading Messages.");
            kitManager.loadAllKits();
            logger.info("Loading Kits.");
            spawnManager.loadAllSpawns();
            logger.info("Loading Spawns.");

            utils.message(
                    sender,
                    Sounds.SUCCESS_SOUND,
                    messagesManager.getMessage("reload-success"));
            return 0;
        }));

        return literal.build();
    }
}
