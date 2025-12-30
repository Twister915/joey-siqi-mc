package sh.joey.mc.session;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.util.DurationFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * /ontime command - shows online time for self or another player.
 */
public final class OnTimeCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("⏱").color(NamedTextColor.GOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final SiqiJoeyPlugin plugin;
    private final PlayerSessionStorage storage;
    private final PlayerSessionTracker tracker;
    private final PlayerResolver playerResolver;

    public OnTimeCommand(SiqiJoeyPlugin plugin, PlayerSessionStorage storage,
                         PlayerSessionTracker tracker, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.tracker = tracker;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "ontime";
    }

    @Override
    public String getPermission() {
        return "smp.ontime";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length == 0) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Usage: /ontime <player>");
                    return Completable.complete();
                }
                return showOwnTime(player);
            } else {
                return showOtherTime(sender, args[0]);
            }
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (args.length != 1) {
                return Maybe.empty();
            }

            String prefix = args[0].toLowerCase();
            if (prefix.isEmpty()) {
                return Maybe.empty();
            }

            return playerResolver.getCompletions(prefix, 10)
                    .flatMapMaybe(list -> list.isEmpty()
                            ? Maybe.empty()
                            : Maybe.just(list.stream().map(Completion::completion).toList()));
        });
    }

    private Completable showOwnTime(Player player) {
        UUID playerId = player.getUniqueId();
        UUID serverSessionId = tracker.getServerSessionId();
        String displayName = playerResolver.getDisplayName(player);

        Single<Long> sessionSeconds = storage.getCurrentSessionStart(playerId, serverSessionId)
                .map(start -> Duration.between(start, Instant.now()).toSeconds())
                .defaultIfEmpty(0L);

        Single<Long> lifetimeSeconds = storage.getLifetimeOnlineTime(playerId)
                .defaultIfEmpty(0L);

        return Single.zip(sessionSeconds, lifetimeSeconds, (session, lifetime) ->
                    new OnTimeResult(displayName, session, lifetime, true))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(result -> displayResult(player, result))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to get online time: " + err.getMessage());
                    error(player, "Failed to retrieve online time.");
                })
                .onErrorComplete()
                .ignoreElement();
    }

    private Completable showOtherTime(CommandSender viewer, String targetName) {
        // Use playerResolver to find by username or nickname
        return playerResolver.resolveOnlinePlayer(targetName)
                .map(target -> showOnlinePlayerTime(viewer, target))
                .orElseGet(() -> showOfflinePlayerTime(viewer, targetName));
    }

    private Completable showOnlinePlayerTime(CommandSender viewer, Player target) {
        UUID playerId = target.getUniqueId();
        UUID serverSessionId = tracker.getServerSessionId();
        String displayName = playerResolver.getDisplayName(target);

        Single<Long> sessionSeconds = storage.getCurrentSessionStart(playerId, serverSessionId)
                .map(start -> Duration.between(start, Instant.now()).toSeconds())
                .defaultIfEmpty(0L);

        Single<Long> lifetimeSeconds = storage.getLifetimeOnlineTime(playerId)
                .defaultIfEmpty(0L);

        return Single.zip(sessionSeconds, lifetimeSeconds, (session, lifetime) ->
                    new OnTimeResult(displayName, session, lifetime, true))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(result -> displayResult(viewer, result))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to get online time for " + target.getName() + ": " + err.getMessage());
                    error(viewer, "Failed to retrieve online time.");
                })
                .onErrorComplete()
                .ignoreElement();
    }

    private Completable showOfflinePlayerTime(CommandSender viewer, String targetName) {
        return playerResolver.resolvePlayerId(targetName)
                .flatMapSingle(playerId -> {
                    Single<String> displayNameSingle = playerResolver.getDisplayName(playerId)
                            .defaultIfEmpty(targetName);
                    Single<Long> lifetimeSingle = storage.getLifetimeOnlineTime(playerId)
                            .defaultIfEmpty(0L);
                    return Single.zip(displayNameSingle, lifetimeSingle,
                            (displayName, lifetime) -> new OnTimeResult(displayName, 0, lifetime, false));
                })
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(result -> displayResult(viewer, result))
                .doOnComplete(() -> error(viewer, "Player '" + targetName + "' not found."))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to get online time for " + targetName + ": " + err.getMessage());
                    error(viewer, "Failed to retrieve online time.");
                })
                .onErrorComplete()
                .ignoreElement();
    }

    private void displayResult(CommandSender viewer, OnTimeResult result) {
        viewer.sendMessage(PREFIX.append(
                Component.text(result.playerName + "'s Online Time").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));

        if (result.isOnline) {
            viewer.sendMessage(PREFIX.append(
                    Component.text("This session: ").color(NamedTextColor.GRAY)
                            .append(Component.text(DurationFormat.formatShort(result.sessionSeconds)).color(NamedTextColor.AQUA))));
        }

        viewer.sendMessage(PREFIX.append(
                Component.text("Lifetime: ").color(NamedTextColor.GRAY)
                        .append(Component.text(DurationFormat.formatShort(result.lifetimeSeconds)).color(NamedTextColor.GREEN))));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private record OnTimeResult(String playerName, long sessionSeconds, long lifetimeSeconds, boolean isOnline) {}
}
