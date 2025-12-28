package sh.joey.mc.punish.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.pagination.ChatPaginator;
import sh.joey.mc.pagination.PaginatedItem;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.punish.Punishment;
import sh.joey.mc.punish.PunishmentMessages;
import sh.joey.mc.punish.PunishmentStorage;
import sh.joey.mc.punish.PunishmentType;
import sh.joey.mc.session.PlayerSessionStorage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /punishments [player] - View punishment history.
 */
public final class PunishmentsCommand implements Command {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault());

    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;
    private final PlayerResolver playerResolver;
    private final PlayerSessionStorage sessionStorage;

    public PunishmentsCommand(SiqiJoeyPlugin plugin, PunishmentStorage storage,
                              PlayerResolver playerResolver, PlayerSessionStorage sessionStorage) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
        this.sessionStorage = sessionStorage;
    }

    @Override
    public String getName() {
        return "punishments";
    }

    @Override
    public String getPermission() {
        return "smp.punish.view";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 1) {
                error(sender, "Usage: /punishments <player> [page]");
                return Completable.complete();
            }

            String targetName = args[0];
            int page = 1;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    error(sender, "Invalid page number: " + args[1]);
                    return Completable.complete();
                }
            }

            int finalPage = page;

            return playerResolver.resolvePlayerIdWithMojang(targetName)
                    .switchIfEmpty(Maybe.defer(() -> {
                        error(sender, "Player '" + targetName + "' not found (checked Mojang API).");
                        return Maybe.empty();
                    }))
                    .flatMapCompletable(targetId ->
                            storage.getPunishmentHistory(targetId)
                                    .toList()
                                    .flatMap(punishments -> lookupUsernames(punishments)
                                            .map(usernameMap -> new HistoryData(punishments, usernameMap)))
                                    .observeOn(plugin.mainScheduler())
                                    .doOnSuccess(data -> displayHistory(sender, targetName, data.punishments, data.usernameMap, finalPage))
                                    .ignoreElement()
                    )
                    .doOnError(err -> logAndError(sender, "Failed to fetch punishment history", err))
                    .onErrorComplete();
        });
    }

    private record HistoryData(List<Punishment> punishments, Map<UUID, String> usernameMap) {}

    /**
     * Look up usernames for all issuers and revokers in the punishment list.
     */
    private Single<Map<UUID, String>> lookupUsernames(List<Punishment> punishments) {
        // Collect all unique UUIDs that need lookup
        Set<UUID> uuids = punishments.stream()
                .flatMap(p -> {
                    List<UUID> ids = new ArrayList<>();
                    if (p.issuedByPlayerId() != null) ids.add(p.issuedByPlayerId());
                    if (p.revokedByPlayerId() != null) ids.add(p.revokedByPlayerId());
                    return ids.stream();
                })
                .collect(Collectors.toSet());

        if (uuids.isEmpty()) {
            return Single.just(Collections.emptyMap());
        }

        // Look up each UUID and collect results into a map
        return Flowable.fromIterable(uuids)
                .flatMapMaybe(uuid -> playerResolver.getUsername(uuid)
                        .map(name -> Map.entry(uuid, name)))
                .toList()
                .map(entries -> entries.stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private void displayHistory(CommandSender sender, String targetName, List<Punishment> punishments,
                                Map<UUID, String> usernameMap, int page) {
        if (punishments.isEmpty()) {
            sender.sendMessage(PunishmentMessages.PREFIX.append(
                    Component.text(targetName + " has no punishment history.").color(NamedTextColor.GRAY)));
            return;
        }

        ChatPaginator paginator = new ChatPaginator()
                .title(PunishmentMessages.PREFIX.append(
                        Component.text("Punishment History: " + targetName).color(NamedTextColor.WHITE)))
                .subtitle(Component.text(punishments.size() + " total punishment(s) ").color(NamedTextColor.GRAY)
                        .append(Component.text("(hover for details)").color(NamedTextColor.DARK_GRAY)))
                .command(p -> "/punishments " + targetName + " " + p);

        for (Punishment punishment : punishments) {
            paginator.add(formatPunishmentEntry(punishment, usernameMap));
        }

        paginator.sendPage(sender, page);
    }

    private PaginatedItem formatPunishmentEntry(Punishment p, Map<UUID, String> usernameMap) {
        TextColor typeColor = switch (p.type()) {
            case BAN, IP_BAN -> NamedTextColor.RED;
            case MUTE -> NamedTextColor.GOLD;
            case KICK -> NamedTextColor.YELLOW;
            case WARN -> NamedTextColor.AQUA;
        };

        String typeLabel = switch (p.type()) {
            case BAN -> "BAN";
            case IP_BAN -> "IP BAN";
            case MUTE -> "MUTE";
            case KICK -> "KICK";
            case WARN -> "WARN";
        };

        // Build the main display line
        Component line = Component.text("[" + typeLabel + "] ")
                .color(typeColor)
                .decorate(TextDecoration.BOLD);

        // Reason or "No reason"
        String reason = p.reason() != null && !p.reason().isBlank() ? p.reason() : "No reason";
        line = line.append(Component.text(reason).color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));

        // Date
        String dateStr = DATE_FORMATTER.format(p.createdAt());
        line = line.append(Component.text(" - " + dateStr).color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false));

        // Status
        line = line.append(Component.text(" ").decoration(TextDecoration.BOLD, false));
        line = line.append(formatStatus(p));

        // Build hover text with full details
        Component hoverText = buildHoverText(p, usernameMap);
        line = line.hoverEvent(HoverEvent.showText(hoverText));

        return PaginatedItem.simple(line);
    }

    private Component buildHoverText(Punishment p, Map<UUID, String> usernameMap) {
        Component hover = Component.empty();

        // Type with color
        TextColor typeColor = switch (p.type()) {
            case BAN, IP_BAN -> NamedTextColor.RED;
            case MUTE -> NamedTextColor.GOLD;
            case KICK -> NamedTextColor.YELLOW;
            case WARN -> NamedTextColor.AQUA;
        };
        String typeLabel = switch (p.type()) {
            case BAN -> p.isPermanent() ? "Permanent Ban" : "Temporary Ban";
            case IP_BAN -> "IP Ban";
            case MUTE -> p.isPermanent() ? "Permanent Mute" : "Temporary Mute";
            case KICK -> "Kick";
            case WARN -> "Warning";
        };
        hover = hover.append(Component.text(typeLabel).color(typeColor).decorate(TextDecoration.BOLD));

        // Reason
        String reason = p.reason() != null && !p.reason().isBlank() ? p.reason() : "No reason given";
        hover = hover.append(Component.newline())
                .append(Component.text("Reason: ").color(NamedTextColor.GRAY))
                .append(Component.text(reason).color(NamedTextColor.WHITE));

        // Issued by
        String issuerName = p.issuedByPlayerId() != null
                ? usernameMap.getOrDefault(p.issuedByPlayerId(), p.issuedByPlayerId().toString())
                : "Console";
        hover = hover.append(Component.newline())
                .append(Component.text("Issued by: ").color(NamedTextColor.GRAY))
                .append(Component.text(issuerName).color(NamedTextColor.YELLOW));

        // Created at
        hover = hover.append(Component.newline())
                .append(Component.text("Date: ").color(NamedTextColor.GRAY))
                .append(Component.text(DATE_FORMATTER.format(p.createdAt())).color(NamedTextColor.WHITE));

        // Expires at (for temporary punishments)
        if (p.expiresAt() != null) {
            hover = hover.append(Component.newline())
                    .append(Component.text("Expires: ").color(NamedTextColor.GRAY))
                    .append(Component.text(DATE_FORMATTER.format(p.expiresAt())).color(NamedTextColor.WHITE));
        }

        // IP address (for IP bans)
        if (p.targetIp() != null) {
            hover = hover.append(Component.newline())
                    .append(Component.text("IP: ").color(NamedTextColor.GRAY))
                    .append(Component.text(p.targetIp()).color(NamedTextColor.WHITE));
        }

        // Status
        hover = hover.append(Component.newline())
                .append(Component.text("Status: ").color(NamedTextColor.GRAY));
        if (p.isRevoked()) {
            String revokerName = p.revokedByPlayerId() != null
                    ? usernameMap.getOrDefault(p.revokedByPlayerId(), p.revokedByPlayerId().toString())
                    : "Console";
            hover = hover.append(Component.text("Revoked").color(NamedTextColor.DARK_GRAY))
                    .append(Component.newline())
                    .append(Component.text("Revoked by: ").color(NamedTextColor.GRAY))
                    .append(Component.text(revokerName).color(NamedTextColor.YELLOW))
                    .append(Component.newline())
                    .append(Component.text("Revoked at: ").color(NamedTextColor.GRAY))
                    .append(Component.text(DATE_FORMATTER.format(p.revokedAt())).color(NamedTextColor.WHITE));
        } else if (p.isExpired()) {
            hover = hover.append(Component.text("Expired").color(NamedTextColor.DARK_GRAY));
        } else if (p.type() == PunishmentType.KICK || p.type() == PunishmentType.WARN) {
            hover = hover.append(Component.text("Recorded").color(NamedTextColor.GRAY));
        } else {
            hover = hover.append(Component.text("Active").color(NamedTextColor.GREEN));
        }

        return hover;
    }

    private Component formatStatus(Punishment p) {
        if (p.isRevoked()) {
            return Component.text("[Revoked]").color(NamedTextColor.DARK_GRAY);
        }
        if (p.isExpired()) {
            return Component.text("[Expired]").color(NamedTextColor.DARK_GRAY);
        }
        if (p.type() == PunishmentType.KICK || p.type() == PunishmentType.WARN) {
            // Kicks and warns are one-time, no active status
            return Component.empty();
        }
        if (p.isPermanent()) {
            return Component.text("[Active]").color(NamedTextColor.RED).decorate(TextDecoration.BOLD);
        }
        // Temporary and active
        return Component.text("[Active]").color(NamedTextColor.YELLOW);
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return playerResolver.getCompletions(args[0], 20)
                    .map(names -> names.stream()
                            .map(Completion::completion)
                            .toList())
                    .filter(list -> !list.isEmpty());
        }
        return Maybe.empty();
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PunishmentMessages.PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private void logAndError(CommandSender sender, String context, Throwable err) {
        plugin.getLogger().warning(context + ": " + err.getMessage());
        error(sender, context + ".");
    }
}
