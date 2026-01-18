package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.session.PlayerSessionStorage;
import sh.joey.mc.session.PlayerSessionStorage.RecentSessionEntry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * /last - lists the 10 most recent player sessions.
 */
public final class LastCommand implements Command {

    private static final Component PREFIX = Component.text("[Last] ").color(NamedTextColor.GOLD);
    private static final int DEFAULT_LIMIT = 10;

    private final SiqiJoeyPlugin plugin;
    private final PlayerSessionStorage sessionStorage;

    public LastCommand(SiqiJoeyPlugin plugin, PlayerSessionStorage sessionStorage) {
        this.plugin = plugin;
        this.sessionStorage = sessionStorage;
    }

    @Override
    public String getName() {
        return "last";
    }

    @Override
    public String getPermission() {
        return "smp.last";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return sessionStorage.getRecentSessions(DEFAULT_LIMIT)
                .toList()
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(sessions -> displaySessions(sender, sessions))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to fetch recent sessions: " + err.getMessage());
                    sender.sendMessage(PREFIX.append(
                            Component.text("Failed to fetch recent sessions.").color(NamedTextColor.RED)));
                })
                .ignoreElement()
                .onErrorComplete();
    }

    private void displaySessions(CommandSender sender, List<RecentSessionEntry> sessions) {
        if (sessions.isEmpty()) {
            sender.sendMessage(PREFIX.append(
                    Component.text("No recent sessions found.").color(NamedTextColor.GRAY)));
            return;
        }

        sender.sendMessage(PREFIX.append(
                Component.text("Recent Sessions:").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));

        Instant now = Instant.now();

        for (RecentSessionEntry session : sessions) {
            String playerName = session.username() != null ? session.username() : "Unknown";
            Duration joinedAgo = Duration.between(session.connectedAt(), now);

            Component line;
            if (session.isOnline()) {
                // Player still online
                Duration onlineFor = Duration.between(session.connectedAt(), now);
                line = Component.text("  ")
                        .append(Component.text(playerName).color(NamedTextColor.WHITE))
                        .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(formatDuration(joinedAgo) + " ago").color(NamedTextColor.GRAY))
                        .append(Component.text(" (").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text("still online").color(NamedTextColor.GREEN))
                        .append(Component.text(")").color(NamedTextColor.DARK_GRAY));
            } else {
                // Player offline - show session duration
                Duration sessionDuration = Duration.between(session.connectedAt(), session.disconnectedAt());
                line = Component.text("  ")
                        .append(Component.text(playerName).color(NamedTextColor.WHITE))
                        .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(formatDuration(joinedAgo) + " ago").color(NamedTextColor.GRAY))
                        .append(Component.text(" (for ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(formatDuration(sessionDuration)).color(NamedTextColor.YELLOW))
                        .append(Component.text(")").color(NamedTextColor.DARK_GRAY));
            }

            sender.sendMessage(line);
        }
    }

    /**
     * Format a duration into a human-readable string like "2h 30m" or "45m 12s".
     */
    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();

        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes < 60) {
            if (seconds == 0) {
                return minutes + "m";
            }
            return minutes + "m " + seconds + "s";
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
