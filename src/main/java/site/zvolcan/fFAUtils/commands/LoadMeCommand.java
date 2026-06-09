package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.KitManager;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.PlayersManager;
import site.zvolcan.fFAUtils.managers.SpawnManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.util.List;

public final class LoadMeCommand implements CommandExecutor {

    private final FFAUtils plugin;
    private final KitManager kitManager;
    private final SpawnManager spawnManager;
    private final PlayersManager playersManager;

    public LoadMeCommand(FFAUtils plugin, KitManager kitManager, SpawnManager spawnManager, PlayersManager playersManager) {
        this.plugin = plugin;
        this.kitManager = kitManager;
        this.spawnManager = spawnManager;
        this.playersManager = playersManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("loadme");

        literal.then(
            Commands.argument("kit", StringArgumentType.word())
                .then(Commands.argument("spawn", StringArgumentType.word())
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        CommandSender sender = source.getSender();
                        if (!(sender instanceof Player player)) {
                            sender.sendMessage(MessagesManager.getInstance()
                                    .getMessage("only-players-execute"));
                            return 1;
                        }
                        String kitName = StringArgumentType.getString(ctx, "kit");
                        String spawnName = StringArgumentType.getString(ctx, "spawn");
                        Kit kit = kitManager.getKit(kitName);
                        if (kit == null) {
                            plugin.getUtils().message(player, Sounds.ERROR_SOUND, "<red>Kit '" + kitName + "' not found.");
                            return 1;
                        }
                        final Location spawn = spawnManager.getSpawn(spawnName);
                        if (spawn == null) {
                            plugin.getUtils().message(player, Sounds.ERROR_SOUND, "<red>Spawn '" + spawnName + "' not found.");
                            return 1;
                        }

                        // Kit-list validation: check spawn's allowed-kits restriction
                        List<String> allowedKits = spawnManager.getAllowedKits(spawnName);
                        if (!SpawnManager.isKitAllowedAtSpawn(allowedKits, kitName)) {
                            plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                                    MessagesManager.getInstance().getMessage("kit-not-allowed-for-spawn"));
                            return 1;
                        }

                        player.getInventory().setContents(kit.getContents());
                        player.teleport(spawn);

                        FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);
                        ffaPlayer.setLastKit(kit);
                        ffaPlayer.setLastSpawn(spawn);
                        return 1;
                    })
                )
        );

        return literal.build();
    }
}
