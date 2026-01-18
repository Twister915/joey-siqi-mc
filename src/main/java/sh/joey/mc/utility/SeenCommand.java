package sh.joey.mc.utility;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.session.PlayerSessionStorage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * /seen <player> - shows when a player was last online.
 */
public final class SeenCommand implements Command {

    private static final Component PREFIX = Component.text("[Seen] ").color(NamedTextColor.GOLD);

    private final SiqiJoeyPlugin plugin;
    private final PlayerSessionStorage sessionStorage;
    private final PlayerResolver playerResolver;

    public SeenCommand(SiqiJoeyPlugin plugin, PlayerSessionStorage sessionStorage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.sessionStorage = sessionStorage;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "seen";
    }

    @Override
    public String getPermission() {
        return "smp.seen";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(PREFIX.append(
                    Component.text("Usage: /seen <player>").color(NamedTextColor.GRAY)));
            return Completable.complete();
        }

        String targetName = args[0];

        // Check if player is online first
        Player online = Bukkit.getPlayer(targetName);
        if (online != null) {
            sender.sendMessage(PREFIX
                    .append(Component.text(online.getName()).color(NamedTextColor.WHITE))
                    .append(Component.text(" is currently online.").color(NamedTextColor.GREEN)));
            return Completable.complete();
        }

        // Also check by nickname for online players
        var onlineByNickname = playerResolver.resolveOnlinePlayer(targetName);
        if (onlineByNickname.isPresent()) {
            Player p = onlineByNickname.get();
            sender.sendMessage(PREFIX
                    .append(Component.text(p.getName()).color(NamedTextColor.WHITE))
                    .append(Component.text(" is currently online.").color(NamedTextColor.GREEN)));
            return Completable.complete();
        }

        // Player not online, look up in database
        return playerResolver.resolvePlayerId(targetName)
                .observeOn(plugin.mainScheduler())
                .flatMap(playerId -> sessionStorage.getLastSeenDate(playerId)
                        .observeOn(plugin.mainScheduler())
                        .map(lastSeen -> new LastSeenResult(playerId, lastSeen)))
                .doOnSuccess(result -> {
                    playerResolver.getUsername(result.playerId)
                            .observeOn(plugin.mainScheduler())
                            .subscribe(
                                    username -> showLastSeen(sender, username, result.lastSeen),
                                    err -> showError(sender, targetName),
                                    () -> showLastSeen(sender, targetName, result.lastSeen)
                            );
                })
                .doOnComplete(() -> sender.sendMessage(PREFIX.append(
                        Component.text("Player '" + targetName + "' not found.").color(NamedTextColor.RED))))
                .doOnError(err -> {
                    plugin.getLogger().warning("Error looking up last seen for " + targetName + ": " + err.getMessage());
                    showError(sender, targetName);
                })
                .ignoreElement()
                .onErrorComplete();
    }

    private record LastSeenResult(java.util.UUID playerId, Instant lastSeen) {}

    private void showLastSeen(CommandSender sender, String playerName, Instant lastSeen) {
        Duration ago = Duration.between(lastSeen, Instant.now());
        String agoStr = formatDuration(ago);

        sender.sendMessage(PREFIX
                .append(Component.text(playerName).color(NamedTextColor.WHITE))
                .append(Component.text(" was last seen ").color(NamedTextColor.GRAY))
                .append(Component.text(agoStr + " ago").color(NamedTextColor.YELLOW))
                .append(Component.text(".").color(NamedTextColor.GRAY)));
    }

    private void showError(CommandSender sender, String playerName) {
        sender.sendMessage(PREFIX.append(
                Component.text("Failed to look up '" + playerName + "'.").color(NamedTextColor.RED)));
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Maybe.empty();
        }

        String prefix = args[0];
        return playerResolver.getCompletions(prefix, 20)
                .map(names -> names.stream()
                        .map(Completion::completion)
                        .toList())
                .filter(list -> !list.isEmpty());
    }

    /**
     * Format a duration into a human-readable string like "2h 30m" or "5d 3h".
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();

        if (seconds < 60) {
            return seconds + "s";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        if (hours < 24) {
            if (remainingMinutes == 0) {
                return hours + "h";
            }
            return hours + "h " + remainingMinutes + "m";
        }

        long days = hours / 24;
        long remainingHours = hours % 24;
        if (remainingHours == 0) {
            return days + "d";
        }
        return days + "d " + remainingHours + "h";
    }
}
