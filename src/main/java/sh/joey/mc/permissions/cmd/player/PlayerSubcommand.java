package sh.joey.mc.permissions.cmd.player;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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
import sh.joey.mc.permissions.PermissibleAttributes;
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
 * Routes /perm player <name> <action> commands.
 */
public final class PlayerSubcommand {

    private static final Set<String> ACTIONS = Set.of(
            "set", "unset", "chat", "nameplate", "inspect"
    );

    private final SiqiJoeyPlugin plugin;
    private final PermissionStorage storage;
    private final PlayerSessionStorage sessionStorage;
    private final PermEffects effects;

    public PlayerSubcommand(
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
            if (args.length < 2) {
                error(sender, "Usage: /perm player <name> <action>");
                return Completable.complete();
            }

            String playerName = args[0];
            String action = args[1].toLowerCase();
            String[] remaining = dropN(args, 2);

            // Resolve player UUID first
            return sessionStorage.resolvePlayerId(playerName)
                    .switchIfEmpty(Maybe.defer(() -> {
                        error(sender, "Player '" + playerName + "' not found.");
                        return Maybe.empty();
                    }))
                    .flatMapCompletable(playerId -> executeForPlayer(sender, playerId, playerName, action, remaining))
                    .doOnError(err -> logAndError(sender, "Failed to execute command", err))
                    .onErrorComplete();
        });
    }

    private Completable executeForPlayer(CommandSender sender, UUID playerId, String playerName, String action, String[] args) {
        return switch (action) {
            case "set" -> handleSet(sender, playerId, playerName, args);
            case "unset" -> handleUnset(sender, playerId, playerName, args);
            case "chat", "nameplate" -> handleAttribute(sender, playerId, playerName, action, args);
            case "inspect" -> handleInspect(sender, playerId, playerName, args);
            default -> {
                error(sender, "Unknown action: " + action);
                yield Completable.complete();
            }
        };
    }

    public Maybe<List<Completion>> tabComplete(CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (args.length == 1) {
                // Complete player names
                return completePlayerName(args);
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
                case "set" -> completePermissionOrBool(remaining);
                case "chat", "nameplate" -> completePrefixSuffix(remaining);
                default -> Maybe.empty();
            };
        });
    }

    // ========== Handlers ==========

    private Completable handleSet(CommandSender sender, UUID playerId, String playerName, String[] args) {
        // /perm player <name> set <permission> [world] true/false
        if (args.length < 2) {
            error(sender, "Usage: /perm player " + playerName + " set <permission> [world] true/false");
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

        String worldStr = worldId == null ? "globally" : "in " + args[1];

        return storage.addPlayerPermission(playerId, permission, worldId, state)
                .andThen(effects.onPlayerChanged(playerId))
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(sender, "Set " + permission + "=" + state + " " + worldStr + " for " + playerName))
                .doOnError(err -> logAndError(sender, "Failed to set permission", err))
                .onErrorComplete();
    }

    private Completable handleUnset(CommandSender sender, UUID playerId, String playerName, String[] args) {
        if (args.length < 1) {
            error(sender, "Usage: /perm player " + playerName + " unset <permission>");
            return Completable.complete();
        }

        String permission = args[0];

        return storage.removePlayerPermission(playerId, permission)
                .flatMapCompletable(removed -> {
                    if (removed) {
                        return effects.onPlayerChanged(playerId)
                                .observeOn(plugin.mainScheduler())
                                .doOnComplete(() -> success(sender, "Removed permission '" + permission + "' from " + playerName + "."));
                    } else {
                        error(sender, "Permission '" + permission + "' not found for " + playerName + ".");
                        return Completable.complete();
                    }
                })
                .doOnError(err -> logAndError(sender, "Failed to unset permission", err))
                .onErrorComplete();
    }

    private Completable handleAttribute(CommandSender sender, UUID playerId, String playerName, String attrType, String[] args) {
        if (args.length < 2) {
            error(sender, "Usage: /perm player " + playerName + " " + attrType + " prefix|suffix <value>");
            return Completable.complete();
        }

        String prefixOrSuffix = args[0].toLowerCase();
        if (!prefixOrSuffix.equals("prefix") && !prefixOrSuffix.equals("suffix")) {
            error(sender, "Expected 'prefix' or 'suffix', got: " + args[0]);
            return Completable.complete();
        }

        // Join remaining args as the value
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        return storage.setPlayerAttribute(playerId, attrType, prefixOrSuffix, value)
                .andThen(effects.onPlayerChanged(playerId))
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(sender, "Set " + attrType + "_" + prefixOrSuffix + " for " + playerName + "."))
                .doOnError(err -> logAndError(sender, "Failed to set attribute", err))
                .onErrorComplete();
    }

    private Completable handleInspect(CommandSender sender, UUID playerId, String playerName, String[] args) {
        int page = 1;
        if (args.length >= 1) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        int finalPage = page;

        // Gather all player data
        return Single.zip(
                storage.getPlayerAttributes(playerId)
                        .defaultIfEmpty(PermissibleAttributes.EMPTY),
                storage.getPlayerPermissions(playerId).toList(),
                storage.getPlayerGroups(playerId).toList(),
                storage.getPlayerExplicitGroups(playerId).toList(),
                (attrs, perms, groups, explicitGroupNames) ->
                        new PlayerInspectData(playerId, playerName, attrs, perms, groups, explicitGroupNames)
        )
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(data -> displayInspect(sender, data, finalPage))
                .doOnError(err -> logAndError(sender, "Failed to inspect player", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private record PlayerInspectData(
            UUID playerId,
            String playerName,
            PermissibleAttributes attributes,
            List<PermissionGrant> permissions,
            List<Group> groups,
            List<String> explicitGroupNames
    ) {}

    private void displayInspect(CommandSender sender, PlayerInspectData data, int page) {
        ChatPaginator paginator = new ChatPaginator()
                .title(PermCommand.PREFIX.append(Component.text("Player: " + data.playerName).color(NamedTextColor.WHITE)))
                .command(p -> "/perm player " + data.playerName + " inspect " + p);

        // Basic info section
        paginator.section("Info");
        paginator.add(PaginatedItem.simple(formatKeyValue("UUID", data.playerId.toString())));

        Player onlinePlayer = Bukkit.getPlayer(data.playerId);
        String status = onlinePlayer != null ? "Online" : "Offline";
        NamedTextColor statusColor = onlinePlayer != null ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        paginator.add(PaginatedItem.simple(
                Component.text("  Status: ").color(NamedTextColor.GRAY)
                        .append(Component.text(status).color(statusColor))));

        // Attributes section
        paginator.add(PaginatedItem.empty());
        paginator.section("Display Overrides");
        paginator.add(PaginatedItem.simple(formatAttr("Chat Prefix", data.attributes.chatPrefix())));
        paginator.add(PaginatedItem.simple(formatAttr("Chat Suffix", data.attributes.chatSuffix())));
        paginator.add(PaginatedItem.simple(formatAttr("Nameplate Prefix", data.attributes.nameplatePrefix())));
        paginator.add(PaginatedItem.simple(formatAttr("Nameplate Suffix", data.attributes.nameplateSuffix())));

        // Groups section
        paginator.add(PaginatedItem.empty());
        paginator.section("Groups");
        if (data.groups.isEmpty()) {
            paginator.add(PaginatedItem.simple(
                    Component.text("  No groups.").color(NamedTextColor.DARK_GRAY)));
        } else {
            for (Group group : data.groups) {
                boolean isExplicit = data.explicitGroupNames.contains(group.canonicalName());
                String membershipType = isExplicit ? "explicit" : "default";
                NamedTextColor typeColor = isExplicit ? NamedTextColor.GREEN : NamedTextColor.YELLOW;

                Component entry = Component.text("  ")
                        .append(Component.text(group.displayName())
                                .color(NamedTextColor.AQUA)
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.runCommand("/perm group " + group.canonicalName() + " inspect")))
                        .append(Component.text(" [" + membershipType + "]").color(typeColor))
                        .append(Component.text(" (priority " + group.priority() + ")").color(NamedTextColor.GRAY));

                paginator.add(PaginatedItem.simple(entry));
            }
        }

        // Permissions section
        paginator.add(PaginatedItem.empty());
        paginator.section("Direct Permissions");
        if (data.permissions.isEmpty()) {
            paginator.add(PaginatedItem.simple(
                    Component.text("  No direct permissions.").color(NamedTextColor.DARK_GRAY)));
        } else {
            for (PermissionGrant grant : data.permissions) {
                String worldStr = grant.worldId() == null ? "global" : getWorldName(grant.worldId());
                String stateStr = grant.state() ? "✓ ALLOW" : "✗ DENY";
                NamedTextColor stateColor = grant.state() ? NamedTextColor.GREEN : NamedTextColor.RED;

                Component entry = Component.text("  ")
                        .append(Component.text(grant.permissionString()).color(NamedTextColor.AQUA))
                        .append(Component.text(" [" + worldStr + "] ").color(NamedTextColor.GRAY))
                        .append(Component.text(stateStr).color(stateColor));

                paginator.add(PaginatedItem.simple(entry));
            }
        }

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
