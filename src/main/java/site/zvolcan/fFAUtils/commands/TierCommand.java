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
import site.zvolcan.fFAUtils.providers.TierProvider;

import java.util.List;
import java.util.Map;

/**
 * {@code /tiers} — turns the tier spawn gate on and off and inspects it.
 *
 * <ul>
 * <li>{@code /tiers} — current state, providers and restricted spawns</li>
 * <li>{@code /tiers on|off|toggle} — enable or disable the gate</li>
 * <li>{@code /tiers reload} — re-read the {@code tiers} config section</li>
 * <li>{@code /tiers cache clear} — drop every cached tier</li>
 * <li>{@code /tiers check <player> [provider]} — look up a player's tier</li>
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
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> check(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"), null))
                        .then(Commands.argument("provider", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    tierManager.getProviders().keySet().forEach(builder::suggest);
                                    builder.suggest(TierManager.PROVIDER_ANY);
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> check(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "provider"))))));

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
                "{provider}", tierManager.getDefaultProvider(),
                "{gamemode}", tierManager.getDefaultGamemode()));

        for (TierProvider provider : tierManager.getProviders().values()) {
            message(sender, null, messages.getMessage(
                    "tiers-status-provider",
                    "{provider}", provider.getDisplayName(),
                    "{url}", provider.getSettings().getApiUrl(),
                    "{cache}", String.valueOf(provider.getCacheSize())));
        }

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
                            : requirement.getGamemode(),
                    "{provider}", requirement.getProvider()));
        }
        return 1;
    }

    /** Reports a player's tier on one provider, or on all of them. */
    private int check(CommandSender sender, String targetName, String providerId) {
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) {
            message(sender, Sounds.ERROR_SOUND,
                    messages().getMessage("tiers-player-not-found", "{player}", targetName));
            return 1;
        }

        List<TierProvider> targets;
        if (providerId == null) {
            targets = tierManager.resolveProviders(null);
        } else if (TierManager.PROVIDER_ANY.equalsIgnoreCase(providerId)) {
            targets = List.copyOf(tierManager.getProviders().values());
        } else {
            TierProvider provider = tierManager.getProvider(providerId);
            if (provider == null) {
                message(sender, Sounds.ERROR_SOUND,
                        messages().getMessage("tiers-unknown-provider", "{provider}", providerId));
                return 1;
            }
            targets = List.of(provider);
        }

        String gamemode = tierManager.getDefaultGamemode();
        for (TierProvider provider : targets) {
            provider.fetchProfile(target.getUniqueId(), target.getName())
                    .whenComplete((profile, error) -> plugin.getServer()
                    .getScheduler().runTask(plugin, () -> {
                        if (error != null) {
                            message(sender, Sounds.ERROR_SOUND, messages().getMessage(
                                    "tiers-lookup-failed",
                                    "{player}", target.getName(),
                                    "{provider}", provider.getDisplayName()));
                            return;
                        }
                        reportProfile(sender, target.getName(), profile, gamemode, provider);
                    }));
        }
        return 1;
    }

    private void reportProfile(CommandSender sender, String targetName, TierProfile profile, String gamemode,
            TierProvider provider) {
        MessagesManager messages = messages();
        if (profile == null) {
            message(sender, Sounds.ERROR_SOUND, messages.getMessage(
                    "tiers-lookup-none", "{player}", targetName, "{provider}", provider.getDisplayName()));
            return;
        }

        boolean usePeak = tierManager.isUsePeakWhenRetired();
        // Translate the configured gamemode into this provider's own slug.
        String resolved = TierManager.isAnyGamemode(gamemode) ? gamemode : provider.resolveGamemode(gamemode);

        TierRanking ranking;
        String matchedGamemode;
        if (TierManager.isAnyGamemode(resolved)) {
            ranking = profile.getBestRanking(usePeak);
            matchedGamemode = profile.getBestGamemode(usePeak);
        } else {
            ranking = profile.getRanking(resolved);
            matchedGamemode = resolved;
        }

        if (ranking == null) {
            message(sender, Sounds.ERROR_SOUND, messages.getMessage(
                    "tiers-lookup-none", "{player}", targetName, "{provider}", provider.getDisplayName()));
            return;
        }
        message(sender, Sounds.SUCCESS_SOUND, messages.getMessage(
                "tiers-lookup",
                "{player}", targetName,
                "{provider}", provider.getDisplayName(),
                "{current}", ranking.display(usePeak),
                "{gamemode}", matchedGamemode == null ? resolved : matchedGamemode));
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
