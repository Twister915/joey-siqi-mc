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
import sh.joey.mc.nickname.NicknameManager;
import sh.joey.mc.player.PlayerResolver;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * /whois command - look up player information.
 * <p>
 * Normal permission (smp.whois): Shows username, nickname, and online status.
 * Admin permission (smp.whois.admin): Also shows UUID, IP, first joined, last seen,
 * total playtime, and username history.
 */
public final class WhoisCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Whois").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final Component DIVIDER = Component.text(" ─────────────────────")
            .color(NamedTextColor.DARK_GRAY);

    private static final String PERMISSION = "smp.whois";
    private static final String PERMISSION_ADMIN = "smp.whois.admin";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("MMM d, yyyy")
            .withZone(ZoneId.systemDefault());

    private final SiqiJoeyPlugin plugin;
    private final PlayerSessionStorage storage;
    private final NicknameManager nicknameManager;
    private final PlayerResolver playerResolver;

    public WhoisCommand(SiqiJoeyPlugin plugin, PlayerSessionStorage storage,
                        NicknameManager nicknameManager, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.nicknameManager = nicknameManager;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "whois";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length == 0) {
                sender.sendMessage(PREFIX.append(Component.text("Usage: /whois <player>")
                        .color(NamedTextColor.RED)));
                return Completable.complete();
            }

            String targetName = args[0];
            boolean isAdmin = sender.hasPermission(PERMISSION_ADMIN);

            return playerResolver.resolvePlayerId(targetName)
                    .flatMap(playerId -> buildWhoisInfo(playerId, isAdmin))
                    .observeOn(plugin.mainScheduler())
                    .doOnSuccess(info -> displayWhoisInfo(sender, info))
                    .doOnComplete(() -> sender.sendMessage(PREFIX.append(
                            Component.text("Player '" + targetName + "' not found.")
                                    .color(NamedTextColor.RED))))
                    .doOnError(err -> {
                        plugin.getLogger().warning("Failed to lookup player: " + err.getMessage());
                        sender.sendMessage(PREFIX.append(Component.text("Failed to look up player.")
                                .color(NamedTextColor.RED)));
                    })
                    .onErrorComplete()
                    .ignoreElement();
        });
    }

    private Maybe<WhoisInfo> buildWhoisInfo(UUID playerId, boolean includeAdmin) {
        // Get username from storage
        Maybe<String> usernameMaybe = storage.findUsernameById(playerId);

        return usernameMaybe.flatMap(username -> {
            Player onlinePlayer = Bukkit.getPlayer(playerId);
            boolean isOnline = onlinePlayer != null;
            String nickname = nicknameManager.getNickname(playerId);

            if (!includeAdmin) {
                // Basic info only
                return Maybe.just(new WhoisInfo(
                        playerId, username, nickname, isOnline,
                        null, null, null, null, null));
            }

            // Admin info - combine multiple async queries
            Single<String> ipSingle = storage.getLastIpAddress(playerId)
                    .defaultIfEmpty("Unknown");

            Single<Instant> firstJoinSingle = storage.getFirstJoinDate(playerId)
                    .defaultIfEmpty(Instant.EPOCH);

            Single<Instant> lastSeenSingle = storage.getLastSeenDate(playerId)
                    .defaultIfEmpty(Instant.EPOCH);

            Single<Long> playtimeSingle = storage.getLifetimeOnlineTime(playerId)
                    .defaultIfEmpty(0L);

            Single<List<PlayerSessionStorage.UsernameHistoryEntry>> historySingle =
                    storage.getUsernameHistory(playerId).toList();

            return Single.zip(ipSingle, firstJoinSingle, lastSeenSingle, playtimeSingle, historySingle,
                    (ip, firstJoin, lastSeen, playtime, history) -> new WhoisInfo(
                            playerId, username, nickname, isOnline,
                            ip, firstJoin, lastSeen, playtime, history))
                    .toMaybe();
        });
    }

    private void displayWhoisInfo(CommandSender sender, WhoisInfo info) {
        sender.sendMessage(PREFIX.append(DIVIDER));

        // Username
        sender.sendMessage(line("Username", Component.text(info.username).color(NamedTextColor.WHITE)));

        // Nickname
        if (info.nickname != null) {
            sender.sendMessage(line("Nickname", Component.text(info.nickname).color(NamedTextColor.AQUA)));
        } else {
            sender.sendMessage(line("Nickname", Component.text("None").color(NamedTextColor.DARK_GRAY)));
        }

        // Status
        Component status = info.isOnline
                ? Component.text("Online").color(NamedTextColor.GREEN)
                : Component.text("Offline").color(NamedTextColor.GRAY);
        sender.sendMessage(line("Status", status));

        // Admin-only info
        if (info.uuid != null && info.ip != null) {
            // UUID
            sender.sendMessage(line("UUID", Component.text(info.uuid.toString()).color(NamedTextColor.GRAY)));

            // IP
            sender.sendMessage(line("IP", Component.text(info.ip).color(NamedTextColor.YELLOW)));

            // First joined
            if (info.firstJoin != null && !info.firstJoin.equals(Instant.EPOCH)) {
                sender.sendMessage(line("First joined", Component.text(DATE_FORMAT.format(info.firstJoin))
                        .color(NamedTextColor.WHITE)));
            }

            // Last seen
            if (info.isOnline) {
                sender.sendMessage(line("Last seen", Component.text("Now (online)").color(NamedTextColor.GREEN)));
            } else if (info.lastSeen != null && !info.lastSeen.equals(Instant.EPOCH)) {
                sender.sendMessage(line("Last seen", Component.text(DATE_FORMAT.format(info.lastSeen))
                        .color(NamedTextColor.WHITE)));
            }

            // Playtime
            if (info.playtimeSeconds != null && info.playtimeSeconds > 0) {
                sender.sendMessage(line("Total playtime",
                        Component.text(formatDuration(info.playtimeSeconds)).color(NamedTextColor.GREEN)));
            }

            // Username history
            if (info.usernameHistory != null && !info.usernameHistory.isEmpty()) {
                sender.sendMessage(Component.empty());
                sender.sendMessage(Component.text(" Username History:").color(NamedTextColor.GRAY)
                        .decorate(TextDecoration.BOLD));

                for (var entry : info.usernameHistory) {
                    String dateRange;
                    if (entry.until() == null) {
                        dateRange = DATE_FORMAT.format(entry.from()) + " - present";
                    } else {
                        dateRange = DATE_FORMAT.format(entry.from()) + " - " + DATE_FORMAT.format(entry.until());
                    }

                    sender.sendMessage(Component.text("  • ")
                            .color(NamedTextColor.DARK_GRAY)
                            .append(Component.text(entry.username()).color(NamedTextColor.WHITE))
                            .append(Component.text(" (" + dateRange + ")").color(NamedTextColor.GRAY)));
                }
            }
        }
    }

    private Component line(String label, Component value) {
        return Component.text(" " + label + ": ")
                .color(NamedTextColor.GRAY)
                .append(value);
    }

    private static String formatDuration(long totalSeconds) {
        if (totalSeconds < 60) {
            return totalSeconds + "s";
        }

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        sb.append(minutes).append("m");

        return sb.toString().trim();
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Maybe.empty();
        }

        String prefix = args[0];
        if (prefix.isEmpty()) {
            return Maybe.empty();
        }

        return playerResolver.getCompletions(prefix, 20)
                .map(names -> names.stream()
                        .map(Completion::completion)
                        .toList())
                .filter(list -> !list.isEmpty());
    }

    private record WhoisInfo(
            UUID uuid,
            String username,
            String nickname,
            boolean isOnline,
            String ip,
            Instant firstJoin,
            Instant lastSeen,
            Long playtimeSeconds,
            List<PlayerSessionStorage.UsernameHistoryEntry> usernameHistory
    ) {}
}
