package sh.joey.mc.permissions.cmd.group;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.pagination.ChatPaginator;
import sh.joey.mc.pagination.PaginatedItem;
import sh.joey.mc.permissions.Group;
import sh.joey.mc.permissions.ParsedPermission;
import sh.joey.mc.permissions.PermissionGrant;
import sh.joey.mc.permissions.PermissionStorage;
import sh.joey.mc.permissions.cmd.PermCommand;
import sh.joey.mc.permissions.cmd.PermEffects;
import sh.joey.mc.session.PlayerSessionStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Routes /perm group <name> <action> commands.
 */
public final class GroupSubcommand {

    private static final Set<String> ACTIONS = Set.of(
            "create", "delete", "default", "priority", "set", "unset",
            "grants", "add", "remove", "chat", "nameplate", "inspect"
    );

    private final SiqiJoeyPlugin plugin;
    private final PermissionStorage storage;
    private final PlayerSessionStorage sessionStorage;
    private final PermEffects effects;

    public GroupSubcommand(
            SiqiJoeyPlugin plugin,
            PermissionStorage storage,
            PlayerSessionStorage sessionStorage,
            PermEffects effects
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.sessionStorage = sessionStorage;
        this.effects = effects;
    }

    public Completable execute(CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 1) {
                error(sender, "Usage: /perm group <name> <action> or /perm group list");
                return Completable.complete();
            }

            // Special case: /perm group list [page]
            if (args[0].equalsIgnoreCase("list")) {
                return handleListGroups(sender, dropN(args, 1));
            }

            if (args.length < 2) {
                error(sender, "Usage: /perm group <name> <action>");
                return Completable.complete();
            }

            String groupName = args[0];
            String action = args[1].toLowerCase();
            String[] remaining = dropN(args, 2);

            return switch (action) {
                case "create" -> handleCreate(sender, groupName, remaining);
                case "delete" -> handleDelete(sender, groupName);
                case "default" -> handleDefault(sender, groupName, remaining);
                case "priority" -> handlePriority(sender, groupName, remaining);
                case "set" -> handleSet(sender, groupName, remaining);
                case "unset" -> handleUnset(sender, groupName, remaining);
                case "grants" -> handleListGrants(sender, groupName, remaining);
                case "add" -> handleAdd(sender, groupName, remaining);
                case "remove" -> handleRemove(sender, groupName, remaining);
                case "chat", "nameplate" -> handleAttribute(sender, groupName, action, remaining);
                case "inspect" -> handleInspect(sender, groupName, remaining);
                default -> {
                    error(sender, "Unknown action: " + action);
                    yield Completable.complete();
                }
            };
        });
    }

    public Maybe<List<Completion>> tabComplete(CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (args.length == 1) {
                // Complete group names + "list" command
                String partial = args[0].toLowerCase();
                return storage.getAllGroups()
                        .map(Group::displayName)
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .toList()
                        .map(names -> {
                            List<Completion> completions = new ArrayList<>();
                            // Add "list" as a special command
                            if ("list".startsWith(partial)) {
                                completions.add(Completion.completion("list"));
                            }
                            // Add group names
                            for (String name : names) {
                                completions.add(Completion.completion(name));
                            }
                            return completions;
                        })
                        .toMaybe();
            }

            // If first arg is "list", no more completions needed
            if (args[0].equalsIgnoreCase("list")) {
                return Maybe.empty();
            }

            if (args.length == 2) {
                // Complete actions
                String partial = args[1].toLowerCase();
                List<Completion> completions = new ArrayList<>();
                for (String action : ACTIONS) {
                    if (action.startsWith(partial)) {
                        completions.add(Completion.completion(action));
                    }
                }
                return Maybe.just(completions);
            }

            String action = args[1].toLowerCase();
            String[] remaining = dropN(args, 2);

            return switch (action) {
                case "add", "remove" -> completePlayerName(remaining);
                case "set" -> completePermissionOrBool(remaining);
                case "default" -> completeBool(remaining);
                case "chat", "nameplate" -> completePrefixSuffix(remaining);
                default -> Maybe.empty();
            };
        });
    }

    // ========== Handlers ==========

    private Completable handleCreate(CommandSender sender, String groupName, String[] args) {
        int priority = 0;
        if (args.length > 0) {
            try {
                priority = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                error(sender, "Invalid priority: " + args[0]);
                return Completable.complete();
            }
        }

        int finalPriority = priority;
        return storage.groupExists(groupName)
                .flatMapCompletable(exists -> {
                    if (exists) {
                        error(sender, "Group '" + groupName + "' already exists.");
                        return Completable.complete();
                    }
                    return storage.createGroup(groupName, finalPriority)
                            .andThen(effects.onGroupChanged(Group.normalize(groupName)))
                            .observeOn(plugin.mainScheduler())
                            .doOnComplete(() -> success(sender, "Created group '" + groupName + "' with priority " + finalPriority));
                })
                .doOnError(err -> logAndError(sender, "Failed to create group", err))
                .onErrorComplete();
    }

    private Completable handleDelete(CommandSender sender, String groupName) {
        return storage.deleteGroup(groupName)
                .flatMapCompletable(deleted -> {
                    if (deleted) {
                        return effects.onGroupChanged(Group.normalize(groupName))
                                .observeOn(plugin.mainScheduler())
                                .doOnComplete(() -> success(sender, "Deleted group '" + groupName + "'."));
                    } else {
                        error(sender, "Group '" + groupName + "' not found.");
                        return Completable.complete();
                    }
                })
                .doOnError(err -> logAndError(sender, "Failed to delete group", err))
                .onErrorComplete();
    }

    private Completable handleDefault(CommandSender sender, String groupName, String[] args) {
        if (args.length < 1) {
            error(sender, "Usage: /perm group " + groupName + " default true/false");
            return Completable.complete();
        }

        Boolean value = parseBoolean(args[0]);
        if (value == null) {
            error(sender, "Invalid boolean: " + args[0]);
            return Completable.complete();
        }

        return storage.setGroupDefault(groupName, value)
                .andThen(effects.onGroupChanged(Group.normalize(groupName)))
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(sender, "Set default=" + value + " for group '" + groupName + "'."))
                .doOnError(err -> logAndError(sender, "Failed to set default", err))
                .onErrorComplete();
    }

    private Completable handlePriority(CommandSender sender, String groupName, String[] args) {
        if (args.length < 1) {
            error(sender, "Usage: /perm group " + groupName + " priority <int>");
            return Completable.complete();
        }

        int priority;
        try {
            priority = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            error(sender, "Invalid priority: " + args[0]);
            return Completable.complete();
        }

        return storage.setGroupPriority(groupName, priority)
                .andThen(effects.onGroupChanged(Group.normalize(groupName)))
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(sender, "Set priority=" + priority + " for group '" + groupName + "'."))
                .doOnError(err -> logAndError(sender, "Failed to set priority", err))
                .onErrorComplete();
    }

    private Completable handleSet(CommandSender sender, String groupName, String[] args) {
        // /perm group <name> set <permission> [world] true/false
        if (args.length < 2) {
            error(sender, "Usage: /perm group " + groupName + " set <permission> [world] true/false");
            return Completable.complete();
        }

        String permission = args[0];

        // Validate permission syntax
        if (ParsedPermission.parse(permission).isEmpty()) {
            error(sender, "Invalid permission syntax: " + permission);
            return Completable.complete();
        }

        // Parse state and optional world
        UUID worldId = null;
        boolean state;

        if (args.length >= 3) {
            // Check if second arg is a world name or a boolean
            Boolean maybeState = parseBoolean(args[1]);
            if (maybeState != null) {
                // No world, args[1] is the state
                state = maybeState;
            } else {
                // args[1] is a world name
                var world = plugin.getServer().getWorld(args[1]);
                if (world == null) {
                    error(sender, "Unknown world: " + args[1]);
                    return Completable.complete();
                }
                worldId = world.getUID();

                Boolean stateArg = parseBoolean(args[2]);
                if (stateArg == null) {
                    error(sender, "Invalid boolean: " + args[2]);
                    return Completable.complete();
                }
                state = stateArg;
            }
        } else {
            Boolean stateArg = parseBoolean(args[1]);
            if (stateArg == null) {
                error(sender, "Invalid boolean: " + args[1]);
                return Completable.complete();
            }
            state = stateArg;
        }

        UUID finalWorldId = worldId;
        String worldStr = worldId == null ? "globally" : "in " + args[1];

        return storage.addGroupPermission(groupName, permission, worldId, state)
                .andThen(effects.onGroupChanged(Group.normalize(groupName)))
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(sender, "Set " + permission + "=" + state + " " + worldStr))
                .doOnError(err -> logAndError(sender, "Failed to set permission", err))
                .onErrorComplete();
    }

    private Completable handleUnset(CommandSender sender, String groupName, String[] args) {
        if (args.length < 1) {
            error(sender, "Usage: /perm group " + groupName + " unset <permission>");
            return Completable.complete();
        }

        String permission = args[0];

        return storage.removeGroupPermission(groupName, permission)
                .flatMapCompletable(removed -> {
                    if (removed) {
                        return effects.onGroupChanged(Group.normalize(groupName))
                                .observeOn(plugin.mainScheduler())
                                .doOnComplete(() -> success(sender, "Removed permission '" + permission + "'."));
                    } else {
                        error(sender, "Permission '" + permission + "' not found.");
                        return Completable.complete();
                    }
                })
                .doOnError(err -> logAndError(sender, "Failed to unset permission", err))
                .onErrorComplete();
    }

    private Completable handleListGroups(CommandSender sender, String[] args) {
        // /perm group list [page]
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        int finalPage = page;
        return storage.getAllGroups()
                .toSortedList((a, b) -> Integer.compare(b.priority(), a.priority()))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(groups -> displayGroups(sender, groups, finalPage))
                .doOnError(err -> logAndError(sender, "Failed to list groups", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private void displayGroups(CommandSender sender, List<Group> groups, int page) {
        ChatPaginator paginator = new ChatPaginator()
                .title(PermCommand.PREFIX.append(Component.text("Groups").color(NamedTextColor.WHITE)))
                .subtitle(Component.text(groups.size() + " groups").color(NamedTextColor.GRAY))
                .command(p -> "/perm group list " + p);

        for (Group group : groups) {
            String defaultStr = group.isDefault() ? " [default]" : "";
            Component entry = Component.text("  ")
                    .append(Component.text(group.displayName())
                            .color(NamedTextColor.AQUA)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.runCommand("/perm group " + group.canonicalName() + " inspect")))
                    .append(Component.text(" (priority " + group.priority() + ")").color(NamedTextColor.GRAY))
                    .append(Component.text(defaultStr).color(NamedTextColor.YELLOW));

            paginator.add(PaginatedItem.simple(entry));
        }

        if (groups.isEmpty()) {
            paginator.add(PaginatedItem.simple(
                    Component.text("  No groups defined.").color(NamedTextColor.GRAY)));
        }

        paginator.sendPage(sender, page);
    }

    private Completable handleListGrants(CommandSender sender, String groupName, String[] args) {
        // /perm group <name> grants [page]
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        int finalPage = page;
        return storage.getGroup(groupName)
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(group -> displayGrants(sender, group, finalPage))
                .doOnComplete(() -> error(sender, "Group '" + groupName + "' not found."))
                .doOnError(err -> logAndError(sender, "Failed to list grants", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private void displayGrants(CommandSender sender, Group group, int page) {
        ChatPaginator paginator = new ChatPaginator()
                .title(PermCommand.PREFIX.append(Component.text("Grants: " + group.displayName()).color(NamedTextColor.WHITE)))
                .subtitle(Component.text(group.grants().size() + " permissions").color(NamedTextColor.GRAY))
                .command(p -> "/perm group " + group.canonicalName() + " grants " + p)
                .backButton("Back", "/perm group " + group.canonicalName() + " inspect");

        // Sort grants: world-specific first (sorted by world name), then global last
        List<PermissionGrant> sorted = group.grants().stream()
                .sorted((a, b) -> {
                    // Global (null) sorts last
                    if (a.worldId() == null && b.worldId() == null) return 0;
                    if (a.worldId() == null) return 1;
                    if (b.worldId() == null) return -1;
                    // Both have worlds - sort by world name
                    return getWorldName(a.worldId()).compareToIgnoreCase(getWorldName(b.worldId()));
                })
                .toList();

        for (PermissionGrant grant : sorted) {
            String worldStr = grant.worldId() == null ? "global" : getWorldName(grant.worldId());
            String stateStr = grant.state() ? "✓ ALLOW" : "✗ DENY";
            NamedTextColor stateColor = grant.state() ? NamedTextColor.GREEN : NamedTextColor.RED;

            Component entry = Component.text("  ")
                    .append(Component.text(grant.permissionString()).color(NamedTextColor.AQUA))
                    .append(Component.text(" [" + worldStr + "] ").color(NamedTextColor.GRAY))
                    .append(Component.text(stateStr).color(stateColor));

            paginator.add(PaginatedItem.simple(entry));
        }

        if (group.grants().isEmpty()) {
            paginator.add(PaginatedItem.simple(
                    Component.text("  No permissions set.").color(NamedTextColor.GRAY)));
        }

        paginator.sendPage(sender, page);
    }

    private Completable handleAdd(CommandSender sender, String groupName, String[] args) {
        if (args.length < 1) {
            error(sender, "Usage: /perm group " + groupName + " add <player>");
            return Completable.complete();
        }

        String playerName = args[0];

        return sessionStorage.resolvePlayerId(playerName)
                .switchIfEmpty(Maybe.defer(() -> {
                    error(sender, "Player '" + playerName + "' not found.");
                    return Maybe.empty();
                }))
                .flatMapCompletable(playerId ->
                        storage.addPlayerToGroup(playerId, groupName)
                                .andThen(effects.onPlayerChanged(playerId))
                                .observeOn(plugin.mainScheduler())
                                .doOnComplete(() -> success(sender, "Added " + playerName + " to group '" + groupName + "'."))
                )
                .doOnError(err -> logAndError(sender, "Failed to add player to group", err))
                .onErrorComplete();
    }

    private Completable handleRemove(CommandSender sender, String groupName, String[] args) {
        if (args.length < 1) {
            error(sender, "Usage: /perm group " + groupName + " remove <player>");
            return Completable.complete();
        }

        String playerName = args[0];

        return sessionStorage.resolvePlayerId(playerName)
                .flatMapSingle(playerId -> storage.removePlayerFromGroup(playerId, groupName)
                        .map(removed -> new RemoveResult(playerId, playerName, removed)))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(result -> {
                    if (result.removed) {
                        effects.onPlayerChanged(result.playerId).subscribe();
                        success(sender, "Removed " + result.playerName + " from group '" + groupName + "'.");
                    } else {
                        error(sender, result.playerName + " is not in group '" + groupName + "'.");
                    }
                })
                .doOnComplete(() -> error(sender, "Player '" + playerName + "' not found."))
                .doOnError(err -> logAndError(sender, "Failed to remove player from group", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private record RemoveResult(UUID playerId, String playerName, boolean removed) {}

    private Completable handleAttribute(CommandSender sender, String groupName, String attrType, String[] args) {
        if (args.length < 2) {
            error(sender, "Usage: /perm group " + groupName + " " + attrType + " prefix|suffix <value>");
            return Completable.complete();
        }

        String prefixOrSuffix = args[0].toLowerCase();
        if (!prefixOrSuffix.equals("prefix") && !prefixOrSuffix.equals("suffix")) {
            error(sender, "Expected 'prefix' or 'suffix', got: " + args[0]);
            return Completable.complete();
        }

        // Join remaining args as the value
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        return storage.setGroupAttribute(groupName, attrType, prefixOrSuffix, value)
                .andThen(effects.onGroupChanged(Group.normalize(groupName)))
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(sender, "Set " + attrType + "_" + prefixOrSuffix + " for group '" + groupName + "'."))
                .doOnError(err -> logAndError(sender, "Failed to set attribute", err))
                .onErrorComplete();
    }

    private Completable handleInspect(CommandSender sender, String groupName, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        int finalPage = page;
        return storage.getGroup(groupName)
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(group -> displayInspect(sender, group, finalPage))
                .doOnComplete(() -> error(sender, "Group '" + groupName + "' not found."))
                .doOnError(err -> logAndError(sender, "Failed to inspect group", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private void displayInspect(CommandSender sender, Group group, int page) {
        ChatPaginator paginator = new ChatPaginator()
                .title(PermCommand.PREFIX.append(Component.text("Group: " + group.displayName()).color(NamedTextColor.WHITE)))
                .command(p -> "/perm group " + group.canonicalName() + " inspect " + p);

        // Basic info section
        paginator.section("Properties");
        paginator.add(PaginatedItem.simple(formatKeyValue("Canonical Name", group.canonicalName())));
        paginator.add(PaginatedItem.simple(formatKeyValue("Priority", String.valueOf(group.priority()))));
        paginator.add(PaginatedItem.simple(formatKeyValue("Default", String.valueOf(group.isDefault()))));

        // Attributes section
        paginator.add(PaginatedItem.empty());
        paginator.section("Display Attributes");
        paginator.add(PaginatedItem.simple(formatAttr("Chat Prefix", group.attributes().chatPrefix())));
        paginator.add(PaginatedItem.simple(formatAttr("Chat Suffix", group.attributes().chatSuffix())));
        paginator.add(PaginatedItem.simple(formatAttr("Nameplate Prefix", group.attributes().nameplatePrefix())));
        paginator.add(PaginatedItem.simple(formatAttr("Nameplate Suffix", group.attributes().nameplateSuffix())));

        // Grants summary
        paginator.add(PaginatedItem.empty());
        paginator.section("Permissions");
        paginator.add(PaginatedItem.simple(
                Component.text("  " + group.grants().size() + " grants ")
                        .color(NamedTextColor.GRAY)
                        .append(Component.text("[View]")
                                .color(NamedTextColor.YELLOW)
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.runCommand("/perm group " + group.canonicalName() + " grants")))));

        paginator.sendPage(sender, page);
    }

    // ========== Tab Completion Helpers ==========

    private Maybe<List<Completion>> completePlayerName(String[] args) {
        if (args.length != 1) return Maybe.empty();
        String partial = args[0].toLowerCase();

        return sessionStorage.findUsernamesByPrefix(partial, 20)
                .toList()
                .map(dbNames -> {
                    List<String> names = new ArrayList<>(dbNames);
                    // Add online players
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        String name = online.getName();
                        if (name.toLowerCase().startsWith(partial) && !names.contains(name)) {
                            names.add(name);
                        }
                    }
                    return names.stream()
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .limit(20)
                            .map(Completion::completion)
                            .toList();
                })
                .toMaybe();
    }

    private static Maybe<List<Completion>> completePermissionOrBool(String[] args) {
        if (args.length == 1) {
            // Complete permission roots
            String partial = args[0].toLowerCase();
            List<Completion> completions = new ArrayList<>();
            for (String perm : List.of("minecraft.", "worldedit.", "*")) {
                if (perm.startsWith(partial)) {
                    completions.add(Completion.completion(perm));
                }
            }
            return Maybe.just(completions);
        } else if (args.length == 2 || args.length == 3) {
            return completeBool(new String[]{args[args.length - 1]});
        }
        return Maybe.empty();
    }

    private static Maybe<List<Completion>> completeBool(String[] args) {
        if (args.length != 1) return Maybe.empty();
        String partial = args[0].toLowerCase();
        List<Completion> completions = new ArrayList<>();
        for (String val : List.of("true", "false")) {
            if (val.startsWith(partial)) {
                completions.add(Completion.completion(val));
            }
        }
        return Maybe.just(completions);
    }

    private static Maybe<List<Completion>> completePrefixSuffix(String[] args) {
        if (args.length != 1) return Maybe.empty();
        String partial = args[0].toLowerCase();
        List<Completion> completions = new ArrayList<>();
        for (String val : List.of("prefix", "suffix")) {
            if (val.startsWith(partial)) {
                completions.add(Completion.completion(val));
            }
        }
        return Maybe.just(completions);
    }

    // ========== Utilities ==========

    private static Component formatKeyValue(String key, String value) {
        return Component.text("  " + key + ": ").color(NamedTextColor.GRAY)
                .append(Component.text(value).color(NamedTextColor.WHITE));
    }

    private static Component formatAttr(String name, String value) {
        Component valueComponent = value != null
                ? Component.text("\"" + value + "\"").color(NamedTextColor.GREEN)
                : Component.text("(not set)").color(NamedTextColor.DARK_GRAY);
        return Component.text("  " + name + ": ").color(NamedTextColor.GRAY)
                .append(valueComponent);
    }

    private static String getWorldName(UUID worldId) {
        var world = Bukkit.getWorld(worldId);
        return world != null ? world.getName() : worldId.toString().substring(0, 8);
    }

    private static Boolean parseBoolean(String input) {
        return switch (input.toLowerCase()) {
            case "true", "yes", "1", "allow" -> true;
            case "false", "no", "0", "deny" -> false;
            default -> null;
        };
    }

    private static void success(CommandSender sender, String message) {
        sender.sendMessage(PermCommand.PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(PermCommand.PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private void logAndError(CommandSender sender, String context, Throwable err) {
        plugin.getLogger().warning(context + ": " + err.getMessage());
        error(sender, context + ". Please try again.");
    }

    private static String[] dropN(String[] args, int n) {
        if (args.length <= n) return new String[0];
        String[] result = new String[args.length - n];
        System.arraycopy(args, n, result, 0, result.length);
        return result;
    }
}
