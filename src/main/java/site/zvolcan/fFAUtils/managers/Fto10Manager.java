package site.zvolcan.fFAUtils.managers;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import site.zvolcan.fFAUtils.FFAUtils;
import site.zvolcan.fFAUtils.objects.FFAPlayer;
import site.zvolcan.fFAUtils.objects.Kit;
import site.zvolcan.fFAUtils.objects.PlayerState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Coordinates invitations and first-to-ten matches. All methods are main-thread only. */
public final class Fto10Manager {

    private static final int WINNING_SCORE = 10;
    private static final long ROUND_RESTART_DELAY_TICKS = 1L;

    private final FFAUtils plugin;
    private final PlayersManager playersManager;
    private final KitManager kitManager;
    private final LobbyManager lobbyManager;
    private final CombatLogManager combatLogManager;
    private final Map<UUID, Invitation> invitations = new HashMap<>();
    private final Map<UUID, Match> matches = new HashMap<>();

    public Fto10Manager(@NotNull FFAUtils plugin, @NotNull PlayersManager playersManager,
                        @NotNull KitManager kitManager, @NotNull LobbyManager lobbyManager) {
        this.plugin = plugin;
        this.playersManager = playersManager;
        this.kitManager = kitManager;
        this.lobbyManager = lobbyManager;
        this.combatLogManager = plugin.getCombatLogManager();
    }

    public enum Result {
        SUCCESS,
        SELF,
        NOT_IN_FFA,
        ALREADY_BUSY,
        NO_INVITATION,
        WRONG_PLAYER,
        NOT_IN_MATCH
    }

    /** Creates an invitation using the challenger's currently loaded spawn and kit. */
    public Result invite(@NotNull Player challenger, @NotNull Player target) {
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            return Result.SELF;
        }
        if (!isReadyForMatch(challenger)) {
            return Result.NOT_IN_FFA;
        }
        if (isBusy(challenger) || isBusy(target)) {
            return Result.ALREADY_BUSY;
        }
        if (invitations.containsKey(target.getUniqueId())) {
            return Result.ALREADY_BUSY;
        }

        FFAPlayer challengerData = playersManager.getFFAPlayer(challenger);
        invitations.put(target.getUniqueId(), new Invitation(
                challenger.getUniqueId(), target.getUniqueId(),
                challengerData.getLastSpawn().clone(), challengerData.getLastKit()));
        message(challenger, "fto10-invite-sent", "{player}", target.getName());
        message(target, "fto10-invite-received", "{player}", challenger.getName());
        return Result.SUCCESS;
    }

    /** Accepts an invitation sent by the named player. */
    public Result accept(@NotNull Player target, @NotNull Player challenger) {
        Invitation invitation = invitations.get(target.getUniqueId());
        if (invitation == null || !invitation.challenger().equals(challenger.getUniqueId())) {
            return Result.NO_INVITATION;
        }
        if (isBusy(target) || isBusy(challenger)) {
            return Result.ALREADY_BUSY;
        }

        invitations.remove(target.getUniqueId());
        Match match = new Match(invitation.challenger(), invitation.target(),
                invitation.spawn(), invitation.kit());
        matches.put(match.first(), match);
        matches.put(match.second(), match);
        startRound(match);
        message(challenger, "fto10-started", "{player}", target.getName());
        message(target, "fto10-started", "{player}", challenger.getName());
        return Result.SUCCESS;
    }

    /** Rejects an invitation sent by the named player. */
    public Result deny(@NotNull Player target, @NotNull Player challenger) {
        Invitation invitation = invitations.get(target.getUniqueId());
        if (invitation == null || !invitation.challenger().equals(challenger.getUniqueId())) {
            return Result.NO_INVITATION;
        }

        invitations.remove(target.getUniqueId());
        message(challenger, "fto10-invite-denied", "{player}", target.getName());
        message(target, "fto10-invite-denied-by-you", "{player}", challenger.getName());
        return Result.SUCCESS;
    }

    /** Ends a match because the command sender intentionally leaves it. */
    public Result leave(@NotNull Player quitter, @NotNull Player opponent) {
        Match match = matches.get(quitter.getUniqueId());
        if (match == null) {
            return Result.NOT_IN_MATCH;
        }
        if (!match.contains(opponent.getUniqueId()) || quitter.getUniqueId().equals(opponent.getUniqueId())) {
            return Result.WRONG_PLAYER;
        }

        finish(match, opponent, quitter);
        return Result.SUCCESS;
    }

    /** Handles a death and returns true when the normal FFA death flow must be skipped. */
    public boolean handleDeath(@NotNull PlayerDeathEvent event) {
        Player loser = event.getPlayer();
        Match match = matches.get(loser.getUniqueId());
        if (match == null) {
            return false;
        }
        if (match.awaitingRespawn != null) {
            event.deathMessage(null);
            event.setKeepInventory(true);
            event.getDrops().clear();
            return true;
        }

        event.deathMessage(null);
        event.setKeepInventory(true);
        event.getDrops().clear();

        UUID winnerId = match.opponent(loser.getUniqueId());
        match.addScore(winnerId);
        int winnerScore = match.score(winnerId);
        int loserScore = match.score(loser.getUniqueId());
        Player winner = online(winnerId);

        if (winnerScore >= WINNING_SCORE) {
            finish(match, winner, loser);
        } else {
            match.awaitingRespawn = loser.getUniqueId();
            message(match, "fto10-round-finished",
                    "{winner}", winner == null ? "?" : winner.getName(),
                    "{winner_score}", String.valueOf(winnerScore),
                    "{loser}", loser.getName(),
                    "{loser_score}", String.valueOf(loserScore));
        }
        return true;
    }

    /** Places a defeated player back in the match spawn before the next round. */
    public boolean handleRespawn(@NotNull PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Match match = matches.get(playerId);
        if (match == null || !playerId.equals(match.awaitingRespawn)) {
            return false;
        }

        event.setRespawnLocation(match.spawn().clone());
        match.awaitingRespawn = null;
        if (match.restartTask == null) {
            match.restartTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                match.restartTask = null;
                if (matches.get(match.first()) == match && matches.get(match.second()) == match) {
                    startRound(match);
                }
            }, ROUND_RESTART_DELAY_TICKS);
        }
        return true;
    }

    /** Ends a match and awards it to the remaining player when someone quits. */
    public void handleQuit(@NotNull Player player) {
        removeInvitationsFor(player.getUniqueId());
        Match match = matches.get(player.getUniqueId());
        if (match == null) {
            return;
        }

        Player winner = online(match.opponent(player.getUniqueId()));
        finish(match, winner, player);
    }

    /** Returns whether damage should be cancelled for a player participating in Fto10. */
    public boolean shouldCancelDamage(@NotNull Player damaged, @NotNull EntityDamageByEntityEvent event) {
        UUID attackerId = attackerId(event);
        if (attackerId == null) {
            return false;
        }

        Match match = matches.get(damaged.getUniqueId());
        if (match == null) {
            return false;
        }
        return !attackerId.equals(match.opponent(damaged.getUniqueId())) || match.awaitingRespawn != null;
    }

    public boolean isInMatch(@NotNull UUID playerId) {
        return matches.containsKey(playerId);
    }

    public Match getMatch(@NotNull UUID playerId) {
        return matches.get(playerId);
    }

    /** Cancels pending round tasks and clears all transient match state. */
    public void shutdown() {
        for (Match match : matches.values()) {
            if (match.restartTask != null) {
                match.restartTask.cancel();
            }
            if (match.actionbarTask != null) {
                match.actionbarTask.cancel();
            }
        }
        matches.clear();
        invitations.clear();
    }

    private void startRound(Match match) {
        Player first = online(match.first());
        Player second = online(match.second());
        if (first == null || second == null) {
            Player winner = first == null ? second : first;
            Player loser = first == null ? null : second;
            if (winner == null) {
                matches.remove(match.first());
                matches.remove(match.second());
                return;
            }
            finish(match, winner, loser);
            return;
        }

        preparePlayer(first, match.spawn(), match.kit());
        preparePlayer(second, match.spawn(), match.kit());
        startActionbar(match);
        message(match, "fto10-score",
                "{first}", first.getName(), "{first_score}", String.valueOf(match.score(match.first())),
                "{second}", second.getName(), "{second_score}", String.valueOf(match.score(match.second())));
    }

    private void preparePlayer(Player player, Location spawn, Kit kit) {
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        kitManager.applyKit(player, kit);
        player.teleport(spawn.clone());
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(0.0F);
        player.setFallDistance(0.0F);
        player.setGameMode(GameMode.SURVIVAL);
        playersManager.getFFAPlayer(player).setState(PlayerState.IN_FFA);
    }

    private void startActionbar(Match match) {
        if (match.actionbarTask != null) {
            return;
        }
        match.actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> sendActionbars(match), 0L, 20L);
    }

    private void sendActionbars(Match match) {
        Player first = online(match.first());
        Player second = online(match.second());
        if (first == null || second == null) {
            return;
        }

        sendActionbar(first, first, second, match);
        sendActionbar(second, second, first, match);
    }

    private void sendActionbar(Player viewer, Player player, Player enemy, Match match) {
        MessagesManager messages = MessagesManager.getInstance();
        String format = messages == null || !messages.hasMessage("fto10-actionbar")
                ? "<head:{player}:true> <green>{points} <gray>- <red>{enemy-points} <reset><head:{enemy}:true>"
                : messages.getMessage("fto10-actionbar");
        format = format.replace("{player}", player.getName())
                .replace("{points}", String.valueOf(match.score(player.getUniqueId())))
                .replace("{enemy-points}", String.valueOf(match.score(enemy.getUniqueId())))
                .replace("{enemy}", enemy.getName());
        viewer.sendActionBar(MiniMessage.miniMessage().deserialize(format));
    }

    private void finish(Match match, Player winner, Player loser) {
        if (match.restartTask != null) {
            match.restartTask.cancel();
            match.restartTask = null;
        }
        if (match.actionbarTask != null) {
            match.actionbarTask.cancel();
            match.actionbarTask = null;
        }
        matches.remove(match.first());
        matches.remove(match.second());

        UUID winnerId = winner == null ? match.opponent(loser.getUniqueId()) : winner.getUniqueId();
        match.setScore(winnerId, WINNING_SCORE);
        Player first = online(match.first());
        Player second = online(match.second());
        String winnerName = winner == null ? "?" : winner.getName();
        UUID loserId = match.opponent(winnerId);
        int loserScore = match.score(loserId);
        message(first, "fto10-finished", "{winner}", winnerName,
                "{winner_score}", String.valueOf(WINNING_SCORE),
                "{loser}", loserName(loser, loserId), "{loser_score}", String.valueOf(loserScore));
        if (second != first) {
            message(second, "fto10-finished", "{winner}", winnerName,
                    "{winner_score}", String.valueOf(WINNING_SCORE),
                    "{loser}", loserName(loser, loserId), "{loser_score}", String.valueOf(loserScore));
        }

        setLobbyState(first);
        setLobbyState(second);
        setLobbyState(winner);
        setLobbyState(loser);
        if (combatLogManager != null) {
            combatLogManager.removeFromCombat(match.first());
            combatLogManager.removeFromCombat(match.second());
        }
        sendToLobby(first);
        sendToLobby(second);
    }

    private String loserName(Player loser, UUID loserId) {
        if (loser != null) {
            return loser.getName();
        }
        Player player = online(loserId);
        return player == null ? "?" : player.getName();
    }

    private void sendToLobby(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        player.teleport(plugin.getSpawnManager().getLobbySpawn());
        lobbyManager.addLobbyItems(player);
    }

    private void setLobbyState(Player player) {
        if (player != null) {
            playersManager.getFFAPlayer(player).setState(PlayerState.LOBBY);
        }
    }

    private boolean isReadyForMatch(Player player) {
        FFAPlayer data = playersManager.getFFAPlayer(player);
        return data.getState() == PlayerState.IN_FFA
                && data.getLastSpawn() != null
                && data.getLastKit() != null;
    }

    private boolean isBusy(Player player) {
        return matches.containsKey(player.getUniqueId());
    }

    private Player online(UUID playerId) {
        return Bukkit.getPlayer(playerId);
    }

    private UUID attackerId(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player.getUniqueId();
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }

    private void removeInvitationsFor(UUID playerId) {
        Iterator<Map.Entry<UUID, Invitation>> iterator = invitations.entrySet().iterator();
        while (iterator.hasNext()) {
            Invitation invitation = iterator.next().getValue();
            if (invitation.challenger().equals(playerId) || invitation.target().equals(playerId)) {
                iterator.remove();
            }
        }
    }

    private void message(Player player, String key, String... placeholders) {
        if (player == null || !player.isOnline() || plugin.getUtils() == null) {
            return;
        }
        MessagesManager messages = MessagesManager.getInstance();
        String text = messages == null ? key : messages.getMessage(key, placeholders);
        plugin.getUtils().message(player, false, text);
    }

    private void message(Match match, String key, String... placeholders) {
        message(online(match.first()), key, placeholders);
        message(online(match.second()), key, placeholders);
    }

    private record Invitation(UUID challenger, UUID target, Location spawn, Kit kit) {
    }

    public static final class Match {
        private final UUID first;
        private final UUID second;
        private final Location spawn;
        private final Kit kit;
        private final Map<UUID, Integer> scores = new HashMap<>();
        private UUID awaitingRespawn;
        private BukkitTask restartTask;
        private BukkitTask actionbarTask;

        private Match(UUID first, UUID second, Location spawn, Kit kit) {
            this.first = first;
            this.second = second;
            this.spawn = spawn;
            this.kit = kit;
            scores.put(first, 0);
            scores.put(second, 0);
        }

        public UUID first() {
            return first;
        }

        public UUID second() {
            return second;
        }

        public Location spawn() {
            return spawn;
        }

        public Kit kit() {
            return kit;
        }

        public boolean contains(UUID playerId) {
            return first.equals(playerId) || second.equals(playerId);
        }

        private UUID opponent(UUID playerId) {
            if (first.equals(playerId)) {
                return second;
            }
            if (second.equals(playerId)) {
                return first;
            }
            throw new IllegalArgumentException("Player is not part of this match");
        }

        public int score(UUID playerId) {
            return scores.getOrDefault(playerId, 0);
        }

        private void addScore(UUID playerId) {
            scores.compute(playerId, (ignored, score) -> score + 1);
        }

        private void setScore(UUID playerId, int score) {
            scores.put(playerId, score);
        }

        public boolean isAwaitingRespawn() {
            return awaitingRespawn != null;
        }
    }
}
