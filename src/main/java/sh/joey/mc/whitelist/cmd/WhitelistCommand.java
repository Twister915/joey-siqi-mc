package sh.joey.mc.whitelist.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.pagination.ChatPaginator;
import sh.joey.mc.pagination.PaginatedItem;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.whitelist.WhitelistEntry;
import sh.joey.mc.whitelist.WhitelistMessages;
import sh.joey.mc.whitelist.WhitelistStorage;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * /whitelist add <player> - Add player to whitelist
 * /whitelist remove <player> - Remove player from whitelist
 * /whitelist list [page] - List whitelisted players
 * /whitelist audit [player] [page] - View invite audit log
 */
public final class WhitelistCommand implements Command {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault());

    private static final Set<String> SUBCOMMANDS = Set.of("add", "remove", "list", "audit");

    private final SiqiJoeyPlugin plugin;
    private final WhitelistStorage storage;
    private final PlayerResolver playerResolver;

    public WhitelistCommand(SiqiJoeyPlugin plugin, WhitelistStorage storage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "whitelist";
    }

    @Override
    public String getPermission() {
        return "smp.whitelist";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length == 0) {
                showUsage(sender);
                return Completable.complete();
            }

            String subcommand = args[0].toLowerCase();

            return switch (subcommand) {
                case "add" -> handleAdd(sender, args);
                case "remove" -> handleRemove(sender, args);
                case "list" -> handleList(sender, args);
                case "audit" -> handleAudit(sender, args);
                default -> {
                    showUsage(sender);
                    yield Completable.complete();
                }
            };
        });
    }

    private Completable handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            error(sender, "Usage: /whitelist add <player>");
            return Completable.complete();
        }

        String targetName = args[1];

        return playerResolver.resolvePlayerIdWithMojang(targetName)
                .switchIfEmpty(Maybe.defer(() -> {
                    error(sender, "Player '" + targetName + "' not found (checked Mojang API).");
                    return Maybe.empty();
                }))
                .flatMapCompletable(targetId ->
                        storage.isWhitelisted(targetId)
                                .flatMapCompletable(alreadyWhitelisted -> {
                                    if (alreadyWhitelisted) {
                                        warn(sender, targetName + " is already whitelisted.");
                                        return Completable.complete();
                                    }
                                    return storage.addPlayer(targetId, targetName, null)
                                            .observeOn(plugin.mainScheduler())
                                            .doOnComplete(() -> success(sender, "Added " + targetName + " to the whitelist."));
                                })
                )
                .doOnError(err -> logAndError(sender, "Failed to add player", err))
                .onErrorComplete();
    }

    private Completable handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            error(sender, "Usage: /whitelist remove <player>");
            return Completable.complete();
        }

        String targetName = args[1];

        return playerResolver.resolvePlayerIdWithMojang(targetName)
                .switchIfEmpty(Maybe.defer(() -> {
                    error(sender, "Player '" + targetName + "' not found (checked Mojang API).");
                    return Maybe.empty();
                }))
                .flatMapSingle(targetId -> storage.removePlayer(targetId))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(removed -> {
                    if (removed) {
                        success(sender, "Removed " + targetName + " from the whitelist.");
                    } else {
                        warn(sender, targetName + " is not whitelisted.");
                    }
                })
                .ignoreElement()
                .doOnError(err -> logAndError(sender, "Failed to remove player", err))
                .onErrorComplete();
    }

    private Completable handleList(CommandSender sender, String[] args) {
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

        return storage.getAllEntries()
                .toList()
                .flatMap(entries -> lookupUsernames(entries)
                        .map(usernameMap -> new ListData(entries, usernameMap)))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(data -> displayList(sender, data.entries, data.usernameMap, finalPage))
                .ignoreElement()
                .doOnError(err -> logAndError(sender, "Failed to list whitelist", err))
                .onErrorComplete();
    }

    private record ListData(List<WhitelistEntry> entries, Map<UUID, String> usernameMap) {}

    private void displayList(CommandSender sender, List<WhitelistEntry> entries, Map<UUID, String> usernameMap, int page) {
        if (entries.isEmpty()) {
            info(sender, "The whitelist is empty.");
            return;
        }

        ChatPaginator paginator = new ChatPaginator()
                .title(WhitelistMessages.PREFIX.append(Component.text("Whitelisted Players").color(NamedTextColor.WHITE)))
                .subtitle(Component.text(entries.size() + " player(s)").color(NamedTextColor.GRAY))
                .command(p -> "/whitelist list " + p);

        for (WhitelistEntry entry : entries) {
            String playerName = usernameMap.getOrDefault(entry.playerId(), entry.playerName());
            String inviterName = entry.invitedBy() != null
                    ? usernameMap.getOrDefault(entry.invitedBy(), "Unknown")
                    : "Admin";

            Component line = Component.text(playerName)
                    .color(NamedTextColor.AQUA)
                    .append(Component.text(" - invited by ").color(NamedTextColor.GRAY))
                    .append(Component.text(inviterName).color(NamedTextColor.YELLOW))
                    .append(Component.text(" on " + DATE_FORMATTER.format(entry.createdAt())).color(NamedTextColor.DARK_GRAY));

            paginator.add(PaginatedItem.simple(line));
        }

        paginator.sendPage(sender, page);
    }

    private Completable handleAudit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            // Show all invites
            return handleAuditAll(sender, args);
        }

        // Try to parse as page number first
        try {
            int page = Integer.parseInt(args[1]);
            return handleAuditAll(sender, new String[]{"audit", String.valueOf(page)});
        } catch (NumberFormatException e) {
            // It's a player name, show who they invited
            return handleAuditPlayer(sender, args);
        }
    }

    private Completable handleAuditAll(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        int finalPage = page;

        return storage.getAllEntries()
                .filter(e -> e.invitedBy() != null)
                .toList()
                .flatMap(entries -> lookupUsernames(entries)
                        .map(usernameMap -> new ListData(entries, usernameMap)))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(data -> displayAuditAll(sender, data.entries, data.usernameMap, finalPage))
                .ignoreElement()
                .doOnError(err -> logAndError(sender, "Failed to load audit log", err))
                .onErrorComplete();
    }

    private void displayAuditAll(CommandSender sender, List<WhitelistEntry> entries, Map<UUID, String> usernameMap, int page) {
        if (entries.isEmpty()) {
            info(sender, "No invite history found.");
            return;
        }

        ChatPaginator paginator = new ChatPaginator()
                .title(WhitelistMessages.PREFIX.append(Component.text("Invite Audit Log").color(NamedTextColor.WHITE)))
                .subtitle(Component.text(entries.size() + " invite(s)").color(NamedTextColor.GRAY))
                .command(p -> "/whitelist audit " + p);

        for (WhitelistEntry entry : entries) {
            String inviterName = usernameMap.getOrDefault(entry.invitedBy(), "Unknown");
            String playerName = usernameMap.getOrDefault(entry.playerId(), entry.playerName());

            Component line = Component.text(inviterName)
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text(" invited ").color(NamedTextColor.GRAY))
                    .append(Component.text(playerName).color(NamedTextColor.AQUA))
                    .append(Component.text(" on " + DATE_FORMATTER.format(entry.createdAt())).color(NamedTextColor.DARK_GRAY));

            paginator.add(PaginatedItem.simple(line));
        }

        paginator.sendPage(sender, page);
    }

    private Completable handleAuditPlayer(CommandSender sender, String[] args) {
        String targetName = args[1];
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                error(sender, "Invalid page number: " + args[2]);
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
                        storage.getInvitedBy(targetId)
                                .toList()
                                .flatMap(entries -> lookupUsernames(entries)
                                        .map(usernameMap -> new ListData(entries, usernameMap)))
                                .observeOn(plugin.mainScheduler())
                                .doOnSuccess(data -> displayAuditPlayer(sender, targetName, data.entries, data.usernameMap, finalPage))
                                .ignoreElement()
                )
                .doOnError(err -> logAndError(sender, "Failed to load audit log", err))
                .onErrorComplete();
    }

    private void displayAuditPlayer(CommandSender sender, String inviterName, List<WhitelistEntry> entries, Map<UUID, String> usernameMap, int page) {
        if (entries.isEmpty()) {
            info(sender, inviterName + " hasn't invited anyone.");
            return;
        }

        ChatPaginator paginator = new ChatPaginator()
                .title(WhitelistMessages.PREFIX.append(Component.text("Invited by " + inviterName).color(NamedTextColor.WHITE)))
                .subtitle(Component.text(entries.size() + " player(s)").color(NamedTextColor.GRAY))
                .command(p -> "/whitelist audit " + inviterName + " " + p);

        for (WhitelistEntry entry : entries) {
            String playerName = usernameMap.getOrDefault(entry.playerId(), entry.playerName());

            Component line = Component.text(playerName)
                    .color(NamedTextColor.AQUA)
                    .append(Component.text(" on " + DATE_FORMATTER.format(entry.createdAt())).color(NamedTextColor.DARK_GRAY));

            paginator.add(PaginatedItem.simple(line));
        }

        paginator.sendPage(sender, page);
    }

    private Single<Map<UUID, String>> lookupUsernames(List<WhitelistEntry> entries) {
        // Collect all UUIDs that need lookup: player IDs and inviter IDs
        Set<UUID> uuids = new HashSet<>();
        for (WhitelistEntry entry : entries) {
            uuids.add(entry.playerId());
            if (entry.invitedBy() != null) {
                uuids.add(entry.invitedBy());
            }
        }

        if (uuids.isEmpty()) {
            return Single.just(Collections.emptyMap());
        }

        return io.reactivex.rxjava3.core.Flowable.fromIterable(uuids)
                .flatMapMaybe(uuid -> playerResolver.getUsername(uuid)
                        .map(name -> Map.entry(uuid, name)))
                .toList()
                .map(list -> list.stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(WhitelistMessages.PREFIX.append(Component.text("Whitelist Commands:").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("  /whitelist add <player>").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /whitelist remove <player>").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /whitelist list [page]").color(NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  /whitelist audit [player] [page]").color(NamedTextColor.GRAY));
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                List<Completion> completions = SUBCOMMANDS.stream()
                        .filter(s -> s.startsWith(prefix))
                        .sorted()
                        .map(Completion::completion)
                        .toList();
                return completions.isEmpty() ? Maybe.empty() : Maybe.just(completions);
            }

            if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("audit"))) {
                return playerResolver.getCompletions(args[1], 20)
                        .map(names -> names.stream()
                                .map(Completion::completion)
                                .toList())
                        .filter(list -> !list.isEmpty());
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                String prefix = args[1].toLowerCase();
                return storage.getAllEntries()
                        .map(WhitelistEntry::playerName)
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .toList()
                        .map(names -> names.stream().map(Completion::completion).toList())
                        .filter(list -> !list.isEmpty());
            }

            return Maybe.empty();
        });
    }

    private void info(CommandSender sender, String message) {
        sender.sendMessage(WhitelistMessages.PREFIX.append(Component.text(message).color(NamedTextColor.GRAY)));
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(WhitelistMessages.PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    private void warn(CommandSender sender, String message) {
        sender.sendMessage(WhitelistMessages.PREFIX.append(Component.text(message).color(NamedTextColor.YELLOW)));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(WhitelistMessages.PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private void logAndError(CommandSender sender, String context, Throwable err) {
        plugin.getLogger().warning(context + ": " + err.getMessage());
        error(sender, context + ".");
    }
}
