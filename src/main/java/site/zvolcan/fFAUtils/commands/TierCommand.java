package site.zvolcan.fFAUtils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.commands.abs.CommandExecutor;
import site.zvolcan.fFAUtils.managers.MessagesManager;
import site.zvolcan.fFAUtils.managers.TierManager;
import site.zvolcan.fFAUtils.objects.Sounds;
import site.zvolcan.fFAUtils.objects.TierProfile;
import site.zvolcan.fFAUtils.objects.TierRanking;

import java.util.Map;

/**
 * {@code /tiers} — turns the MCTiers spawn gate on and off and inspects it.
 *
 * <ul>
 * <li>{@code /tiers} — current state and the restricted spawns</li>
 * <li>{@code /tiers on|off|toggle} — enable or disable the gate</li>
 * <li>{@code /tiers reload} — re-read the {@code tiers} config section</li>
 * <li>{@code /tiers cache clear} — drop every cached tier</li>
 * <li>{@code /tiers check <player>} — look up an online player's tier</li>
 * </ul>
 */
public final class TierCommand implements CommandExecutor {

    private final FFAUtils plugin;
    private final TierManager tierManager;

    public TierCommand(FFAUtils plugin, TierManager tierManager) {
        this.plugin = plugin;
        this.tierManager = tierManager;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> execute() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal("tiers");

        literal.requires(ctx -> ctx.getSender().hasPermission("ffautils.commands.tiers"));
        literal.executes(ctx -> sendStatus(ctx.getSource().getSender()));

        literal.then(Commands.literal("status")
                .executes(ctx -> sendStatus(ctx.getSource().getSender())));

        literal.then(Commands.literal("on")
                .executes(ctx -> setEnabled(ctx.getSource().getSender(), true)));

        literal.then(Commands.literal("off")
                .executes(ctx -> setEnabled(ctx.getSource().getSender(), false)));

        literal.then(Commands.literal("toggle")
                .executes(ctx -> setEnabled(ctx.getSource().getSender(), !tierManager.isEnabled())));

        literal.then(Commands.literal("reload").executes(ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            tierManager.loadSettings();
            tierManager.clearCache();
            message(sender, Sounds.SUCCESS_SOUND, messages().getMessage("tiers-reloaded"));
            return 1;
        }));

        literal.then(Commands.literal("cache").then(Commands.literal("clear").executes(ctx -> {
            CommandSender sender = ctx.getSource().getSender();
            int cleared = tierManager.clearCache();
            message(sender, Sounds.SUCCESS_SOUND,
                    messages().getMessage("tiers-cache-cleared", "{entries}", String.valueOf(cleared)));
            return 1;
        })));

        literal.then(Commands.literal("check")
                .then(Commands.argument("player", StringArgumentType.word()).executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    String targetName = StringArgumentType.getString(ctx, "player");
                    Player target = plugin.getServer().getPlayerExact(targetName);
                    if (target == null) {
                        message(sender, Sounds.ERROR_SOUND,
                                messages().getMessage("tiers-player-not-found", "{player}", targetName));
                        return 1;
                    }
                    lookup(sender, target);
                    return 1;
                })));

        return literal.build();
    }

    private int setEnabled(CommandSender sender, boolean enabled) {
        tierManager.setEnabled(enabled);
        message(sender, enabled ? Sounds.SUCCESS_SOUND : Sounds.ERROR_SOUND,
                messages().getMessage(enabled ? "tiers-enabled" : "tiers-disabled"));
        return 1;
    }

    private int sendStatus(CommandSender sender) {
        MessagesManager messages = messages();
        message(sender, Sounds.SUCCESS_SOUND, messages.getMessage(
                "tiers-status",
                "{status}", tierManager.isEnabled() ? "<green>activado</green>" : "<red>desactivado</red>",
                "{gamemode}", tierManager.getDefaultGamemode(),
                "{cache}", String.valueOf(tierManager.getCacheSize())));

        Map<String, TierManager.TierRequirement> restricted = tierManager.getRestrictedSpawns();
        if (restricted.isEmpty()) {
            message(sender, null, messages.getMessage("tiers-status-no-spawns"));
            return 1;
        }
        for (TierManager.TierRequirement requirement : restricted.values()) {
            message(sender, null, messages.getMessage(
                    "tiers-status-spawn",
                    "{spawn}", requirement.getSpawnName(),
                    "{required}", requirement.display(),
                    "{gamemode}", TierManager.isAnyGamemode(requirement.getGamemode())
                            ? "cualquiera"
                            : requirement.getGamemode()));
        }
        return 1;
    }

    /** Resolves the target's tier — from cache when possible — and reports it. */
    private void lookup(CommandSender sender, Player target) {
        String gamemode = tierManager.getDefaultGamemode();
        tierManager.fetchProfile(target.getUniqueId()).whenComplete((profile, error) -> plugin.getServer()
                .getScheduler().runTask(plugin, () -> {
                    MessagesManager messages = messages();
                    if (error != null) {
                        message(sender, Sounds.ERROR_SOUND,
                                messages.getMessage("tiers-lookup-failed", "{player}", target.getName()));
                        return;
                    }
                    reportProfile(sender, target.getName(), profile, gamemode);
                }));
    }

    private void reportProfile(CommandSender sender, String targetName, TierProfile profile, String gamemode) {
        MessagesManager messages = messages();
        if (profile == null) {
            message(sender, Sounds.ERROR_SOUND,
                    messages.getMessage("tiers-lookup-none", "{player}", targetName));
            return;
        }

        boolean usePeak = tierManager.isUsePeakWhenRetired();
        TierRanking ranking;
        String matchedGamemode;
        if (TierManager.isAnyGamemode(gamemode)) {
            ranking = profile.getBestRanking(usePeak);
            matchedGamemode = profile.getBestGamemode(usePeak);
        } else {
            ranking = profile.getRanking(gamemode);
            matchedGamemode = gamemode;
        }

        if (ranking == null) {
            message(sender, Sounds.ERROR_SOUND,
                    messages.getMessage("tiers-lookup-none", "{player}", targetName));
            return;
        }
        message(sender, Sounds.SUCCESS_SOUND, messages.getMessage(
                "tiers-lookup",
                "{player}", targetName,
                "{current}", ranking.display(usePeak),
                "{gamemode}", matchedGamemode == null ? gamemode : matchedGamemode));
    }

    private void message(CommandSender sender, net.kyori.adventure.sound.Sound sound, String message) {
        if (sound == null) {
            plugin.getUtils().message(sender, false, message);
            return;
        }
        plugin.getUtils().message(sender, sound, message);
    }

    private MessagesManager messages() {
        return MessagesManager.getInstance();
    }
}
