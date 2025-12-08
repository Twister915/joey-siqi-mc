package sh.joey.mc.home;

import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Handles /home command with subcommands:
 * - /home <name> - teleport to home
 * - /home set <name> - set a home
 * - /home delete <name> - delete a home
 * - /home list - list homes
 * - /home share <name> <player> - share a home
 * - /home unshare <name> <player> - unshare a home
 */
public final class HomeCommand implements CommandExecutor {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Home").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));
    static final Set<String> RESERVED_NAMES = Set.of("set", "delete", "list", "share", "unshare", "help", "confirm", "cancel");
    private static final int CONFIRM_TIMEOUT_SECONDS = 30;

    private final SiqiJoeyPlugin plugin;
    private final HomeStorage storage;
    private final SafeTeleporter teleporter;
    private final Map<UUID, PendingDelete> pendingDeletes = new HashMap<>();

    private record PendingDelete(String homeName, Disposable expiryTask) {}

    public HomeCommand(SiqiJoeyPlugin plugin, HomeStorage storage, SafeTeleporter teleporter) {
        this.plugin = plugin;
        this.storage = storage;
        this.teleporter = teleporter;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            handleTeleport(player, "home");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "set" -> handleSet(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player);
            case "share" -> handleShare(player, args);
            case "unshare" -> handleUnshare(player, args);
            case "help" -> showUsage(player);
            case "confirm" -> handleConfirm(player);
            case "cancel" -> handleCancel(player);
            default -> handleTeleport(player, args[0]);
        }

        return true;
    }

    // --- Set Home ---

    private void handleSet(Player player, String[] args) {
        String name = args.length < 2 ? "home" : args[1].toLowerCase();
        if (RESERVED_NAMES.contains(name)) {
            error(player, "Cannot use '" + name + "' as a home name.");
            return;
        }

        Home home = new Home(name, player.getUniqueId(), player.getLocation());
        storage.setHome(player.getUniqueId(), home)
                .subscribe(
                        () -> success(player, "Home '" + name + "' has been set!"),
                        err -> logAndError(player, "Failed to set home", err)
                );
    }

    // --- Delete Home ---

    private void handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            error(player, "Usage: /home delete <name>");
            return;
        }

        String name = args[1].toLowerCase();
        UUID playerId = player.getUniqueId();

        storage.getHome(playerId, name)
                .filter(home -> home.isOwnedBy(playerId))
                .subscribe(
                        home -> onDeleteHomeFound(player, name),
                        err -> logAndError(player, "Failed to check home", err),
                        () -> error(player, "Home '" + name + "' not found.")
                );
    }

    private void onDeleteHomeFound(Player player, String name) {
        UUID playerId = player.getUniqueId();
        cancelPendingDelete(playerId);

        Disposable expiryTask = plugin.timer(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .subscribe(tick -> onDeleteExpiry(player, name));

        pendingDeletes.put(playerId, new PendingDelete(name, expiryTask));
        showDeleteConfirmation(player, name);
    }

    private void onDeleteExpiry(Player player, String name) {
        if (pendingDeletes.remove(player.getUniqueId()) != null) {
            info(player, "Delete confirmation for '" + name + "' expired.");
        }
    }

    private void showDeleteConfirmation(Player player, String name) {
        Component confirmButton = Component.text("[Confirm]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/home confirm"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to delete").color(NamedTextColor.RED)));

        Component cancelButton = Component.text("[Cancel]")
                .color(NamedTextColor.GRAY)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/home cancel"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to cancel").color(NamedTextColor.GRAY)));

        player.sendMessage(PREFIX
                .append(Component.text("Delete home '").color(NamedTextColor.GOLD))
                .append(Component.text(name).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.text("'? This cannot be undone!").color(NamedTextColor.GOLD)));
        player.sendMessage(PREFIX
                .append(confirmButton)
                .append(Component.text(" "))
                .append(cancelButton));
    }

    private void handleConfirm(Player player) {
        UUID playerId = player.getUniqueId();
        PendingDelete pending = pendingDeletes.remove(playerId);

        if (pending == null) {
            error(player, "You don't have any pending home deletions.");
            return;
        }

        pending.expiryTask().dispose();

        storage.deleteHome(playerId, pending.homeName())
                .subscribe(
                        deleted -> onDeleteResult(player, pending.homeName(), deleted),
                        err -> logAndError(player, "Failed to delete home", err)
                );
    }

    private void onDeleteResult(Player player, String homeName, boolean deleted) {
        if (deleted) {
            success(player, "Home '" + homeName + "' has been deleted.");
        } else {
            error(player, "Home '" + homeName + "' not found.");
        }
    }

    private void handleCancel(Player player) {
        UUID playerId = player.getUniqueId();
        PendingDelete pending = pendingDeletes.remove(playerId);

        if (pending == null) {
            error(player, "You don't have any pending home deletions.");
            return;
        }

        pending.expiryTask().dispose();
        info(player, "Home deletion cancelled.");
    }

    private void cancelPendingDelete(UUID playerId) {
        PendingDelete pending = pendingDeletes.remove(playerId);
        if (pending != null) {
            pending.expiryTask().dispose();
        }
    }

    // --- List Homes ---

    private void handleList(Player player) {
        UUID playerId = player.getUniqueId();

        storage.getHomes(playerId)
                .toList()
                .subscribe(
                        homes -> displayHomeList(player, playerId, homes),
                        err -> logAndError(player, "Failed to list homes", err)
                );
    }

    private void displayHomeList(Player player, UUID playerId, List<Home> allHomes) {
        if (allHomes.isEmpty()) {
            info(player, "You don't have any homes. Use /home set <name> to create one.");
            return;
        }

        // Partition into owned and shared
        List<Home> ownedHomes = allHomes.stream().filter(h -> h.isOwnedBy(playerId)).toList();
        List<Home> sharedHomes = allHomes.stream().filter(h -> !h.isOwnedBy(playerId)).toList();

        Location playerLoc = player.getLocation();
        UUID playerWorldId = playerLoc.getWorld().getUID();

        List<Home> sortedHomes = sortHomesByDistance(ownedHomes, playerLoc, playerWorldId);

        player.sendMessage(PREFIX.append(Component.text("Your Homes:").color(NamedTextColor.WHITE)));

        for (Home home : sortedHomes) {
            player.sendMessage(formatOwnedHomeEntry(home, playerLoc, playerWorldId));
        }

        if (!sharedHomes.isEmpty()) {
            player.sendMessage(PREFIX.append(Component.text("Shared with you:").color(NamedTextColor.WHITE)));
            for (Home home : sharedHomes) {
                player.sendMessage(formatSharedHomeEntry(home));
            }
        }
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
        String ownerName = getPlayerName(home.ownerId());

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

    private void handleShare(Player player, String[] args) {
        if (args.length < 3) {
            error(player, "Usage: /home share <name> <player>");
            return;
        }

        String name = args[1].toLowerCase();
        String targetName = args[2];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            error(player, "Player '" + targetName + "' not found.");
            return;
        }

        if (target.equals(player)) {
            error(player, "You can't share a home with yourself.");
            return;
        }

        storage.shareHome(player.getUniqueId(), name, target.getUniqueId())
                .subscribe(
                        shared -> onShareResult(player, target, name, shared),
                        err -> logAndError(player, "Failed to share home", err)
                );
    }

    private void onShareResult(Player player, Player target, String name, boolean shared) {
        if (shared) {
            success(player, "Shared home '" + name + "' with " + target.getName() + "!");
            info(target, player.getName() + " shared their home '" + name + "' with you!");
        } else {
            error(player, "Home '" + name + "' not found.");
        }
    }

    // --- Unshare Home ---

    private void handleUnshare(Player player, String[] args) {
        if (args.length < 3) {
            error(player, "Usage: /home unshare <name> <player>");
            return;
        }

        String name = args[1].toLowerCase();
        String targetName = args[2];

        UUID targetId = resolvePlayerId(targetName);
        if (targetId == null) {
            error(player, "Player '" + targetName + "' not found.");
            return;
        }

        storage.unshareHome(player.getUniqueId(), name, targetId)
                .subscribe(
                        unshared -> onUnshareResult(player, targetName, name, unshared),
                        err -> logAndError(player, "Failed to unshare home", err)
                );
    }

    private void onUnshareResult(Player player, String targetName, String name, boolean unshared) {
        if (unshared) {
            success(player, "Unshared home '" + name + "' from " + targetName + ".");
        } else {
            error(player, "Home '" + name + "' not found.");
        }
    }

    private UUID resolvePlayerId(String name) {
        Player online = Bukkit.getPlayer(name);
        if (online != null) {
            return online.getUniqueId();
        }

        @SuppressWarnings("deprecation")
        var offlinePlayer = Bukkit.getOfflinePlayer(name);
        return offlinePlayer.hasPlayedBefore() ? offlinePlayer.getUniqueId() : null;
    }

    // --- Teleport ---

    private void handleTeleport(Player player, String input) {
        UUID playerId = player.getUniqueId();

        if (input.contains(":")) {
            handleSharedHomeTeleport(player, input);
        } else {
            handleOwnHomeTeleport(player, input.toLowerCase());
        }
    }

    private void handleOwnHomeTeleport(Player player, String homeName) {
        storage.getHome(player.getUniqueId(), homeName)
                .subscribe(
                        home -> teleportToHome(player, home),
                        err -> logAndError(player, "Failed to find home", err),
                        () -> error(player, "Home '" + homeName + "' not found.")
                );
    }

    private void handleSharedHomeTeleport(Player player, String input) {
        String[] parts = input.split(":", 2);
        String ownerName = parts[0];
        String homeName = parts[1].toLowerCase();

        @SuppressWarnings("deprecation")
        var offlinePlayer = Bukkit.getOfflinePlayer(ownerName);
        if (!offlinePlayer.hasPlayedBefore()) {
            error(player, "Player '" + ownerName + "' not found.");
            return;
        }

        UUID ownerId = offlinePlayer.getUniqueId();
        storage.getHome(ownerId, homeName)
                .filter(home -> home.isSharedWith(player.getUniqueId()))
                .subscribe(
                        home -> teleportToHome(player, home),
                        err -> logAndError(player, "Failed to find home", err),
                        () -> error(player, "Home '" + input + "' not found or not shared with you.")
                );
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

    private String getPlayerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        var offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        return name != null ? name : playerId.toString().substring(0, 8);
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
