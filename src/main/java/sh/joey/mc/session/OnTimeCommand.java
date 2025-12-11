package sh.joey.mc.session;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

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

    public OnTimeCommand(SiqiJoeyPlugin plugin, PlayerSessionStorage storage, PlayerSessionTracker tracker) {
        this.plugin = plugin;
        this.storage = storage;
        this.tracker = tracker;
    }

    @Override
    public String getName() {
        return "ontime";
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

            return storage.findUsernamesByPrefix(prefix, 10)
                    .map(Completion::completion)
                    .toList()
                    .flatMapMaybe(list -> list.isEmpty() ? Maybe.empty() : Maybe.just(list));
        });
    }

    private Completable showOwnTime(Player player) {
        UUID playerId = player.getUniqueId();
        UUID serverSessionId = tracker.getServerSessionId();

        Single<Long> sessionSeconds = storage.getCurrentSessionStart(playerId, serverSessionId)
                .map(start -> Duration.between(start, Instant.now()).toSeconds())
                .defaultIfEmpty(0L);

        Single<Long> lifetimeSeconds = storage.getLifetimeOnlineTime(playerId)
                .defaultIfEmpty(0L);

        return Single.zip(sessionSeconds, lifetimeSeconds, (session, lifetime) ->
                    new OnTimeResult(player.getName(), session, lifetime, true))
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
        Player onlineTarget = Bukkit.getPlayer(targetName);

        if (onlineTarget != null) {
            return showOnlinePlayerTime(viewer, onlineTarget);
        } else {
            return showOfflinePlayerTime(viewer, targetName);
        }
    }

    private Completable showOnlinePlayerTime(CommandSender viewer, Player target) {
        UUID playerId = target.getUniqueId();
        UUID serverSessionId = tracker.getServerSessionId();

        Single<Long> sessionSeconds = storage.getCurrentSessionStart(playerId, serverSessionId)
                .map(start -> Duration.between(start, Instant.now()).toSeconds())
                .defaultIfEmpty(0L);

        Single<Long> lifetimeSeconds = storage.getLifetimeOnlineTime(playerId)
                .defaultIfEmpty(0L);

        return Single.zip(sessionSeconds, lifetimeSeconds, (session, lifetime) ->
                    new OnTimeResult(target.getName(), session, lifetime, true))
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
        return storage.resolvePlayerId(targetName)
                .flatMap(playerId -> storage.getLifetimeOnlineTime(playerId)
                        .map(lifetime -> new OnTimeResult(targetName, 0, lifetime, false)))
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
                            .append(Component.text(formatDuration(result.sessionSeconds)).color(NamedTextColor.AQUA))));
        }

        viewer.sendMessage(PREFIX.append(
                Component.text("Lifetime: ").color(NamedTextColor.GRAY)
                        .append(Component.text(formatDuration(result.lifetimeSeconds)).color(NamedTextColor.GREEN))));
    }

    private static String formatDuration(long totalSeconds) {
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private record OnTimeResult(String playerName, long sessionSeconds, long lifetimeSeconds, boolean isOnline) {}
}
