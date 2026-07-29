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
import site.zvolcan.fFAUtils.managers.TierManager;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.PlayerState;
import site.zvolcan.fFAUtils.objects.Sounds;

import java.util.List;

public final class LoadMeCommand implements CommandExecutor {

    private final FFAUtils plugin;
    private final KitManager kitManager;
    private final SpawnManager spawnManager;
    private final PlayersManager playersManager;

    public LoadMeCommand(FFAUtils plugin, KitManager kitManager, SpawnManager spawnManager,
            PlayersManager playersManager) {
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
                                    FFAPlayer ffaPlayer = playersManager.getFFAPlayer(player);
                                    // if (ffaPlayer.getState() != PlayerState.LOBBY) {
                                    // plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                                    // MessagesManager.getInstance().getMessage(
                                    // "player-already-in-ffa"));
                                    // }

                                    String kitName = StringArgumentType.getString(ctx, "kit");
                                    String spawnName = StringArgumentType.getString(ctx, "spawn");
                                    Kit kit = kitManager.getKit(kitName);
                                    if (kit == null) {
                                        plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                                                MessagesManager.getInstance().getMessage(
                                                        "kit-not-found", "{name}", kitName));
                                        return 1;
                                    }
                                    final Location spawn = spawnManager.getSpawn(spawnName);
                                    if (spawn == null) {
                                        plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                                                MessagesManager.getInstance().getMessage(
                                                        "spawn-not-found", "{spawn}", spawnName));
                                        return 1;
                                    }

                                    // Kit-list validation: check spawn's allowed-kits restriction
                                    List<String> allowedKits = spawnManager.getAllowedKits(spawnName);
                                    if (!SpawnManager.isKitAllowedAtSpawn(allowedKits, kitName)) {
                                        plugin.getUtils().message(player, Sounds.ERROR_SOUND,
                                                MessagesManager.getInstance().getMessage("kit-not-allowed-for-spawn"));
                                        return 1;
                                    }

                                    // MCTiers gate: the spawn may require a minimum tier.
                                    // Resolved from cache when possible, otherwise the
                                    // callback runs once the API lookup completes.
                                    TierManager tierManager = plugin.getTierManager();
                                    if (tierManager == null) {
                                        enterSpawn(player, ffaPlayer, kit, spawn);
                                        return 1;
                                    }
                                    if (!tierManager.isResolvedImmediately(player, spawnName)) {
                                        plugin.getUtils().message(player, false,
                                                MessagesManager.getInstance().getMessage("tier-checking"));
                                    }
                                    tierManager.checkAccess(player, spawnName, result -> {
                                        if (!result.isAllowed()) {
                                            sendTierDenial(player, result);
                                            return;
                                        }
                                        // The lookup may have taken a moment — they could be gone.
                                        if (!player.isOnline()) {
                                            return;
                                        }
                                        enterSpawn(player, ffaPlayer, kit, spawn);
                                    });
                                    return 1;
                                })));

        return literal.build();
    }

    /** Applies the kit and drops the player into the spawn. */
    private void enterSpawn(Player player, FFAPlayer ffaPlayer, Kit kit, Location spawn) {
        player.getActivePotionEffects().forEach(e -> {
            player.removePotionEffect(e.getType());
        });

        kitManager.applyKit(player, kit);
        player.teleport(spawn);
        player.setSaturation(0);

        ffaPlayer.setLastKit(kit);
        ffaPlayer.setLastSpawn(spawn);
        ffaPlayer.setState(PlayerState.IN_FFA);
    }

    /** Explains to the player why the tier gate turned them away. */
    private void sendTierDenial(Player player, TierManager.TierAccessResult result) {
        if (!player.isOnline()) {
            return;
        }
        MessagesManager messages = MessagesManager.getInstance();
        TierManager.TierRequirement requirement = result.getRequirement();
        String gamemode = result.getGamemode();
        if (gamemode == null || gamemode.isEmpty()) {
            gamemode = requirement == null ? "?" : requirement.getGamemode();
        }

        String provider = result.getProviderName();

        String message = switch (result.getStatus()) {
            case DENIED_NO_PROFILE -> messages.getMessage("tier-no-profile", "{provider}", provider);
            case DENIED_NO_RANKING -> messages.getMessage("tier-no-ranking",
                    "{gamemode}", gamemode, "{provider}", provider);
            case DENIED_ERROR -> messages.getMessage("tier-lookup-failed", "{provider}", provider);
            default -> messages.getMessage(
                    "tier-blocked",
                    "{required}", requirement == null ? "?" : requirement.display(),
                    "{gamemode}", gamemode,
                    "{provider}", provider,
                    "{current}", result.getRanking() == null
                            ? "-"
                            : result.getRanking().display(plugin.getTierManager().isUsePeakWhenRetired()));
        };

        plugin.getUtils().message(player, Sounds.ERROR_SOUND, message);
    }
}
