package sh.joey.mc.home;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.confirm.ConfirmationManager;
import sh.joey.mc.confirm.ConfirmationRequest;
import sh.joey.mc.pagination.ChatPaginator;
import sh.joey.mc.pagination.PaginatedItem;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.session.PlayerSessionStorage;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles /home command with subcommands:
 * - /home <name> - teleport to home
 * - /home set <name> - set a home
 * - /home delete <name> - delete a home
 * - /home list - list homes
 * - /home share <name> <player> - share a home
 * - /home unshare <name> <player> - unshare a home
 */
public final class HomeCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Home").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));
    private static final Set<String> RESERVED_NAMES = Set.of("set", "delete", "list", "share", "unshare", "help");
    private static final int CONFIRM_TIMEOUT_SECONDS = 30;
    private static final int MAX_COMPLETIONS = 20;

    private final SiqiJoeyPlugin plugin;
    private final HomeStorage storage;
    private final PlayerSessionStorage sessionStorage;
    private final PlayerResolver playerResolver;
    private final SafeTeleporter teleporter;
    private final ConfirmationManager confirmationManager;

    public HomeCommand(SiqiJoeyPlugin plugin, HomeStorage storage, PlayerSessionStorage sessionStorage,
                       PlayerResolver playerResolver, SafeTeleporter teleporter, ConfirmationManager confirmationManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.sessionStorage = sessionStorage;
        this.playerResolver = playerResolver;
        this.teleporter = teleporter;
        this.confirmationManager = confirmationManager;
    }

    @Override
    public String getName() {
        return "home";
    }

    @Override
    public String getPermission() {
        return "smp.home";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            if (args.length == 0) {
                return handleTeleport(player, "home");
            }

            String subcommand = args[0].toLowerCase();
            return switch (subcommand) {
                case "set" -> handleSet(player, args);
                case "delete" -> handleDelete(player, args);
                case "list" -> handleList(player, args);
                case "share" -> handleShare(player, args);
                case "unshare" -> handleUnshare(player, args);
                case "help" -> handleHelp(player);
                default -> handleTeleport(player, args[0]);
            };
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (!(sender instanceof Player player)) {
                return Maybe.empty();
            }

            UUID playerId = player.getUniqueId();
            String partial = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
            int argIndex = args.length;

            if (argIndex == 1) {
                return completeFirstArg(playerId, partial);
            } else if (argIndex == 2) {
                return completeSecondArg(playerId, args[0].toLowerCase(), partial);
            } else if (argIndex == 3) {
                return completeThirdArg(player, args[0].toLowerCase(), partial);
            }

            return Maybe.empty();
        });
    }

    // --- Tab Completion ---

    private Maybe<List<Completion>> completeFirstArg(UUID playerId, String partial) {
        return storage.getHomes(playerId)
                .toList()
                .map(homes -> {
                    List<Completion> completions = new ArrayList<>();

                    // Add matching subcommands
                    for (String cmd : RESERVED_NAMES) {
                        if (cmd.startsWith(partial)) {
                            completions.add(Completion.completion(cmd));
                        }
                    }

                    // Add matching home names
                    for (Home home : homes) {
                        String name = formatHomeCompletion(home, playerId);
                        if (name.toLowerCase().startsWith(partial)) {
                            completions.add(Completion.completion(name));
                        }
                    }

                    return completions;
                })
                .toMaybe();
    }

    private Maybe<List<Completion>> completeSecondArg(UUID playerId, String subcommand, String partial) {
        if (!Set.of("delete", "share", "unshare").contains(subcommand)) {
            return Maybe.empty();
        }

        return storage.getHomes(playerId)
                .filter(home -> home.isOwnedBy(playerId))
                .map(Home::name)
                .filter(name -> name.startsWith(partial))
                .toList()
                .map(names -> names.stream()
                        .map(Completion::completion)
                        .toList())
                .toMaybe();
    }

    private Maybe<List<Completion>> completeThirdArg(Player player, String subcommand, String partial) {
        if (!subcommand.equals("share") && !subcommand.equals("unshare")) {
            return Maybe.empty();
        }

        String senderName = player.getName();
        return playerResolver.getCompletions(partial, MAX_COMPLETIONS)
                .map(names -> names.stream()
                        .filter(name -> !name.equalsIgnoreCase(senderName))
                        .map(Completion::completion)
                        .toList())
                .filter(list -> !list.isEmpty());
    }

    private String formatHomeCompletion(Home home, UUID playerId) {
        if (home.isOwnedBy(playerId)) {
            return home.name();
        } else {
            return home.ownerDisplayName() + ":" + home.name();
        }
    }

    // --- Set Home ---

    private Completable handleSet(Player player, String[] args) {
        return Completable.defer(() -> {
            String name = args.length < 2 ? "home" : HomeStorage.normalizeName(args[1]);
            if (RESERVED_NAMES.contains(name)) {
                error(player, "Cannot use '" + name + "' as a home name.");
                return Completable.complete();
            }

            Home home = new Home(name, player.getUniqueId(), player.getLocation());
            return storage.setHome(player.getUniqueId(), home)
                    .observeOn(plugin.mainScheduler())
                    .doOnComplete(() -> success(player, "Home '" + name + "' has been set!"))
                    .doOnError(err -> logAndError(player, "Failed to set home", err))
                    .onErrorComplete();
        });
    }

    // --- Delete Home ---

    private Completable handleDelete(Player player, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 2) {
                error(player, "Usage: /home delete <name>");
                return Completable.complete();
            }

            String name = HomeStorage.normalizeName(args[1]);
            UUID playerId = player.getUniqueId();

            return storage.getHome(playerId, name)
                    .filter(home -> home.isOwnedBy(playerId))
                    .observeOn(plugin.mainScheduler())
                    .doOnSuccess(home -> requestDeleteConfirmation(player, name))
                    .doOnComplete(() -> error(player, "Home '" + name + "' not found."))
                    .doOnError(err -> logAndError(player, "Failed to check home", err))
                    .onErrorComplete()
                    .ignoreElement();
        });
    }

    private void requestDeleteConfirmation(Player player, String name) {
        UUID playerId = player.getUniqueId();

        confirmationManager.request(player, new ConfirmationRequest() {
            @Override
            public Component prefix() {
                return PREFIX;
            }

            @Override
            public String promptText() {
                return "Delete home '" + name + "'? This cannot be undone!";
            }

            @Override
            public String acceptText() {
                return "Delete";
            }

            @Override
            public String declineText() {
                return "Cancel";
            }

            @Override
            public void onAccept() {
                storage.deleteHome(playerId, name)
                        .observeOn(plugin.mainScheduler())
                        .subscribe(
                                deleted -> onDeleteResult(player, name, deleted),
                                err -> logAndError(player, "Failed to delete home", err)
                        );
            }

            @Override
            public void onDecline() {
                info(player, "Home deletion cancelled.");
            }

            @Override
            public void onTimeout() {
                info(player, "Delete confirmation expired.");
            }

            @Override
            public int timeoutSeconds() {
                return CONFIRM_TIMEOUT_SECONDS;
            }
        });
    }

    private void onDeleteResult(Player player, String homeName, boolean deleted) {
        if (deleted) {
            success(player, "Home '" + homeName + "' has been deleted.");
        } else {
            error(player, "Home '" + homeName + "' not found.");
        }
    }

    // --- List Homes ---

    private Completable handleList(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                // Invalid page number, use default
            }
        }
        int finalPage = page;
        return storage.getHomes(playerId)
                .toList()
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(homes -> displayHomeList(player, playerId, homes, finalPage))
                .doOnError(err -> logAndError(player, "Failed to list homes", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private void displayHomeList(Player player, UUID playerId, List<Home> allHomes, int page) {
        if (allHomes.isEmpty()) {
            info(player, "You don't have any homes. Use /home set <name> to create one.");
            return;
        }

        // Partition into owned and shared
        List<Home> ownedHomes = allHomes.stream().filter(h -> h.isOwnedBy(playerId)).toList();
        List<Home> sharedHomes = allHomes.stream().filter(h -> !h.isOwnedBy(playerId)).toList();

        Location playerLoc = player.getLocation();
        UUID playerWorldId = playerLoc.getWorld().getUID();

        List<Home> sortedOwned = sortHomesByDistance(ownedHomes, playerLoc, playerWorldId);

        ChatPaginator paginator = new ChatPaginator()
                .title(PREFIX.append(Component.text("Your Homes").color(NamedTextColor.WHITE)))
                .subtitle(Component.text(ownedHomes.size() + " owned, " + sharedHomes.size() + " shared").color(NamedTextColor.GRAY))
                .command(p -> "/home list " + p);

        // Add owned homes
        for (Home home : sortedOwned) {
            paginator.add(PaginatedItem.simple(formatOwnedHomeEntry(home, playerLoc, playerWorldId)));
        }

        // Add shared homes section if any
        if (!sharedHomes.isEmpty()) {
            paginator.add(PaginatedItem.empty());
            paginator.section("Shared with you");
            for (Home home : sharedHomes) {
                paginator.add(PaginatedItem.simple(formatSharedHomeEntry(home)));
            }
        }

        paginator.sendPage(player, page);
    }

    private List<Home> sortHomesByDistance(List<Home> homes, Location playerLoc, UUID playerWorldId) {
        return homes.stream()
                .sorted((a, b) -> {
                    Location locA = a.toLocation();
                    Location locB = b.toLocation();
                    boolean aLoaded = locA != null;
                    boolean bLoaded = locB != null;

                    if (aLoaded && !bLoaded) return -1;
                    if (!aLoaded && bLoaded) return 1;
                    if (!aLoaded) return a.name().compareTo(b.name());

                    boolean aInWorld = a.worldId().equals(playerWorldId);
                    boolean bInWorld = b.worldId().equals(playerWorldId);
                    if (aInWorld && !bInWorld) return -1;
                    if (!aInWorld && bInWorld) return 1;
                    if (!aInWorld) return a.name().compareTo(b.name());

                    return Double.compare(playerLoc.distance(locA), playerLoc.distance(locB));
                })
                .toList();
    }

    private Component formatOwnedHomeEntry(Home home, Location playerLoc, UUID playerWorldId) {
        Location homeLoc = home.toLocation();
        boolean worldLoaded = homeLoc != null;
        boolean sameWorld = worldLoaded && home.worldId().equals(playerWorldId);

        String distanceStr = "";
        if (sameWorld) {
            distanceStr = " (" + formatDistance(playerLoc.distance(homeLoc)) + ")";
        } else if (!worldLoaded) {
            distanceStr = " (world not loaded)";
        }

        Component entry = Component.text("  ")
                .append(Component.text(home.name())
                        .color(NamedTextColor.AQUA)
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/home " + home.name()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport").color(NamedTextColor.GRAY))))
                .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                .append(formatLocation(home))
                .append(Component.text(distanceStr).color(NamedTextColor.GOLD));

        if (!home.sharedWith().isEmpty()) {
            entry = entry.append(Component.text(" [shared]").color(NamedTextColor.YELLOW));
        }

        return entry;
    }

    private Component formatSharedHomeEntry(Home home) {
        String ownerName = home.ownerDisplayName();
        return Component.text("  ")
                .append(Component.text(home.name())
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/home " + ownerName + ":" + home.name()))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport").color(NamedTextColor.GRAY))))
                .append(Component.text(" by ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(ownerName).color(NamedTextColor.YELLOW));
    }

    private String formatDistance(double meters) {
        if (meters >= 1000) {
            return String.format("%.1f km", meters / 1000.0);
        } else {
            return String.format("%.0f m", meters);
        }
    }

    // --- Share Home ---

    private Completable handleShare(Player player, String[] args) {
        if (args.length < 3) {
            error(player, "Usage: /home share <name> <player>");
            return Completable.complete();
        }

        String name = HomeStorage.normalizeName(args[1]);
        String targetName = args[2];
        UUID playerId = player.getUniqueId();

        return playerResolver.resolvePlayerId(targetName)
                .observeOn(plugin.mainScheduler())
                .flatMapCompletable(targetId -> {
                    if (targetId.equals(playerId)) {
                        error(player, "You can't share a home with yourself.");
                        return Completable.complete();
                    }
                    return storage.shareHome(playerId, name, targetId)
                            .observeOn(plugin.mainScheduler())
                            .doOnSuccess(shared -> onShareResult(player, targetId, targetName, name, shared))
                            .ignoreElement();
                })
                .doOnComplete(() -> error(player, "Player '" + targetName + "' not found."))
                .doOnError(err -> logAndError(player, "Failed to share home", err))
                .onErrorComplete();
    }

    private void onShareResult(Player player, UUID targetId, String targetName, String name, boolean shared) {
        if (shared) {
            success(player, "Shared home '" + name + "' with " + targetName + "!");
            // Notify target if they're online
            Player target = plugin.getServer().getPlayer(targetId);
            if (target != null) {
                info(target, player.getName() + " shared their home '" + name + "' with you!");
            }
        } else {
            error(player, "Home '" + name + "' not found.");
        }
    }

    // --- Unshare Home ---

    private Completable handleUnshare(Player player, String[] args) {
        if (args.length < 3) {
            error(player, "Usage: /home unshare <name> <player>");
            return Completable.complete();
        }

        String name = HomeStorage.normalizeName(args[1]);
        String targetName = args[2];

        return playerResolver.resolvePlayerId(targetName)
                .flatMapSingle(targetId -> storage.unshareHome(player.getUniqueId(), name, targetId))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(unshared -> onUnshareResult(player, targetName, name, unshared))
                .doOnComplete(() -> error(player, "Player '" + targetName + "' not found."))
                .doOnError(err -> logAndError(player, "Failed to unshare home", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private void onUnshareResult(Player player, String targetName, String name, boolean unshared) {
        if (unshared) {
            success(player, "Unshared home '" + name + "' from " + targetName + ".");
        } else {
            error(player, "Home '" + name + "' not found.");
        }
    }

    // --- Teleport ---

    private Completable handleTeleport(Player player, String input) {
        if (input.contains(":")) {
            return handleSharedHomeTeleport(player, input);
        } else {
            return handleOwnHomeTeleport(player, HomeStorage.normalizeName(input));
        }
    }

    private Completable handleOwnHomeTeleport(Player player, String homeName) {
        return storage.getHome(player.getUniqueId(), homeName)
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(home -> teleportToHome(player, home))
                .doOnComplete(() -> error(player, "Home '" + homeName + "' not found."))
                .doOnError(err -> logAndError(player, "Failed to find home", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private Completable handleSharedHomeTeleport(Player player, String input) {
        String[] parts = input.split(":", 2);
        String ownerName = parts[0];
        String homeName = HomeStorage.normalizeName(parts[1]);

        return sessionStorage.resolvePlayerId(ownerName)
                .flatMap(ownerId -> storage.getHome(ownerId, homeName))
                .filter(home -> home.isSharedWith(player.getUniqueId()))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(home -> teleportToHome(player, home))
                .doOnComplete(() -> error(player, "Home '" + input + "' not found or not shared with you."))
                .doOnError(err -> logAndError(player, "Failed to find home", err))
                .onErrorComplete()
                .ignoreElement();
    }

    private void teleportToHome(Player player, Home home) {
        Location location = home.toLocation();
        if (location == null) {
            error(player, "The world for this home is not loaded.");
            return;
        }

        info(player, "Teleporting to '" + home.name() + "'...");
        teleporter.teleport(player, location, success -> {});
    }

    // --- Help ---

    private Completable handleHelp(Player player) {
        return Completable.fromAction(() -> showUsage(player));
    }

    private void showUsage(Player player) {
        player.sendMessage(PREFIX.append(Component.text("Home Commands:").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  /home").color(NamedTextColor.AQUA)
                .append(Component.text(" - Teleport to your default home").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /home <name>").color(NamedTextColor.AQUA)
                .append(Component.text(" - Teleport to a named home").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /home set [name]").color(NamedTextColor.AQUA)
                .append(Component.text(" - Set a home (default: 'home')").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /home delete <name>").color(NamedTextColor.AQUA)
                .append(Component.text(" - Delete a home").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /home list").color(NamedTextColor.AQUA)
                .append(Component.text(" - List your homes").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /home share <name> <player>").color(NamedTextColor.AQUA)
                .append(Component.text(" - Share a home").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("  /home unshare <name> <player>").color(NamedTextColor.AQUA)
                .append(Component.text(" - Unshare a home").color(NamedTextColor.GRAY)));
    }

    // --- Utilities ---

    private Component formatLocation(Home home) {
        var world = Bukkit.getWorld(home.worldId());
        String worldName = world != null ? world.getName() : "unknown";
        return Component.text(String.format("%s (%.0f, %.0f, %.0f)",
                worldName, home.x(), home.y(), home.z())).color(NamedTextColor.GRAY);
    }

    private void info(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GRAY)));
    }

    private void success(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    private void error(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private void logAndError(Player player, String context, Throwable err) {
        plugin.getLogger().warning(context + ": " + err.getMessage());
        error(player, context + ". Please try again.");
    }
}
