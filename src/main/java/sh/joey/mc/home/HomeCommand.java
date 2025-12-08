package sh.joey.mc.home;

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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.teleport.SafeTeleporter;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
public final class HomeCommand implements CommandExecutor, TabCompleter {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Home").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final Set<String> SUBCOMMANDS = Set.of("set", "delete", "list", "share", "unshare", "help", "confirm", "cancel");
    private static final int CONFIRM_TIMEOUT_SECONDS = 30;

    private final JavaPlugin plugin;
    private final HomeStorage storage;
    private final SafeTeleporter teleporter;
    private final Map<UUID, PendingDelete> pendingDeletes = new HashMap<>();

    private record PendingDelete(String homeName, BukkitTask expiryTask) {}

    public HomeCommand(JavaPlugin plugin, HomeStorage storage, SafeTeleporter teleporter) {
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
            // Default to teleporting to home named "home"
            return handleTeleport(player, "home");
        }

        String subcommand = args[0].toLowerCase();

        return switch (subcommand) {
            case "set" -> handleSet(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player);
            case "share" -> handleShare(player, args);
            case "unshare" -> handleUnshare(player, args);
            case "help" -> { showUsage(player); yield true; }
            case "confirm" -> handleConfirm(player);
            case "cancel" -> handleCancel(player);
            default -> handleTeleport(player, args[0]);
        };
    }

    private boolean handleSet(Player player, String[] args) {
        // Default to "home" if no name provided
        String name = args.length < 2 ? "home" : args[1].toLowerCase();
        if (SUBCOMMANDS.contains(name)) {
            error(player, "Cannot use '" + name + "' as a home name.");
            return true;
        }

        Home home = new Home(name, player.getLocation());
        storage.setHome(player.getUniqueId(), home);
        success(player, "Home '" + name + "' has been set!");
        return true;
    }

    private boolean handleDelete(Player player, String[] args) {
        if (args.length < 2) {
            error(player, "Usage: /home delete <name>");
            return true;
        }

        String name = args[1].toLowerCase();
        UUID playerId = player.getUniqueId();

        // Check if home exists
        if (storage.getHome(playerId, name).isEmpty()) {
            error(player, "Home '" + name + "' not found.");
            return true;
        }

        // Cancel any existing pending delete
        cancelPendingDelete(playerId);

        // Schedule expiry
        BukkitTask expiryTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingDeletes.remove(playerId) != null) {
                info(player, "Delete confirmation for '" + name + "' expired.");
            }
        }, CONFIRM_TIMEOUT_SECONDS * 20L);

        pendingDeletes.put(playerId, new PendingDelete(name, expiryTask));

        // Show confirmation prompt with clickable buttons
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

        return true;
    }

    private boolean handleConfirm(Player player) {
        UUID playerId = player.getUniqueId();
        PendingDelete pending = pendingDeletes.remove(playerId);

        if (pending == null) {
            error(player, "You don't have any pending home deletions.");
            return true;
        }

        pending.expiryTask().cancel();

        if (storage.deleteHome(playerId, pending.homeName())) {
            success(player, "Home '" + pending.homeName() + "' has been deleted.");
        } else {
            error(player, "Home '" + pending.homeName() + "' not found.");
        }
        return true;
    }

    private boolean handleCancel(Player player) {
        UUID playerId = player.getUniqueId();
        PendingDelete pending = pendingDeletes.remove(playerId);

        if (pending == null) {
            error(player, "You don't have any pending home deletions.");
            return true;
        }

        pending.expiryTask().cancel();
        info(player, "Home deletion cancelled.");
        return true;
    }

    private void cancelPendingDelete(UUID playerId) {
        PendingDelete pending = pendingDeletes.remove(playerId);
        if (pending != null) {
            pending.expiryTask().cancel();
        }
    }

    private boolean handleList(Player player) {
        UUID playerId = player.getUniqueId();
        List<Home> ownedHomes = storage.getOwnedHomes(playerId);
        List<HomeStorage.SharedHomeEntry> sharedHomes = storage.getSharedWithPlayer(playerId);

        if (ownedHomes.isEmpty() && sharedHomes.isEmpty()) {
            info(player, "You don't have any homes. Use /home set <name> to create one.");
            return true;
        }

        Location playerLoc = player.getLocation();
        UUID playerWorldId = playerLoc.getWorld().getUID();

        // Sort owned homes: same world by distance, then other worlds
        List<Home> sortedHomes = ownedHomes.stream()
                .sorted((a, b) -> {
                    boolean aInWorld = a.worldId().equals(playerWorldId);
                    boolean bInWorld = b.worldId().equals(playerWorldId);
                    if (aInWorld && !bInWorld) return -1;
                    if (!aInWorld && bInWorld) return 1;
                    if (!aInWorld) return a.name().compareTo(b.name()); // Both in other worlds, sort by name
                    // Both in same world, sort by distance
                    double distA = playerLoc.distance(a.toLocation());
                    double distB = playerLoc.distance(b.toLocation());
                    return Double.compare(distA, distB);
                })
                .toList();

        player.sendMessage(PREFIX.append(Component.text("Your Homes:").color(NamedTextColor.WHITE)));

        for (Home home : sortedHomes) {
            boolean sameWorld = home.worldId().equals(playerWorldId);
            String distanceStr = "";
            if (sameWorld) {
                double dist = playerLoc.distance(home.toLocation());
                distanceStr = " (" + formatDistance(dist) + ")";
            }

            Component homeEntry = Component.text("  ")
                    .append(Component.text(home.name())
                            .color(NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/home " + home.name()))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to teleport").color(NamedTextColor.GRAY))))
                    .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                    .append(formatLocation(home))
                    .append(Component.text(distanceStr).color(NamedTextColor.GOLD));

            if (!home.sharedWith().isEmpty()) {
                homeEntry = homeEntry.append(Component.text(" [shared]").color(NamedTextColor.YELLOW));
            }

            player.sendMessage(homeEntry);
        }

        if (!sharedHomes.isEmpty()) {
            player.sendMessage(PREFIX.append(Component.text("Shared with you:").color(NamedTextColor.WHITE)));

            for (HomeStorage.SharedHomeEntry entry : sharedHomes) {
                String ownerName = getPlayerName(entry.ownerId());
                Home home = entry.home();

                Component homeEntry = Component.text("  ")
                        .append(Component.text(home.name())
                                .color(NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/home " + ownerName + ":" + home.name()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to teleport").color(NamedTextColor.GRAY))))
                        .append(Component.text(" by ").color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(ownerName).color(NamedTextColor.YELLOW));

                player.sendMessage(homeEntry);
            }
        }

        return true;
    }

    private String formatDistance(double meters) {
        if (meters >= 1000) {
            return String.format("%.1f km", meters / 1000.0);
        } else {
            return String.format("%.0f m", meters);
        }
    }

    private boolean handleShare(Player player, String[] args) {
        if (args.length < 3) {
            error(player, "Usage: /home share <name> <player>");
            return true;
        }

        String name = args[1].toLowerCase();
        String targetName = args[2];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            error(player, "Player '" + targetName + "' not found.");
            return true;
        }

        if (target.equals(player)) {
            error(player, "You can't share a home with yourself.");
            return true;
        }

        if (storage.shareHome(player.getUniqueId(), name, target.getUniqueId())) {
            success(player, "Shared home '" + name + "' with " + target.getName() + "!");
            info(target, player.getName() + " shared their home '" + name + "' with you!");
        } else {
            error(player, "Home '" + name + "' not found.");
        }
        return true;
    }

    private boolean handleUnshare(Player player, String[] args) {
        if (args.length < 3) {
            error(player, "Usage: /home unshare <name> <player>");
            return true;
        }

        String name = args[1].toLowerCase();
        String targetName = args[2];

        Player target = Bukkit.getPlayer(targetName);
        UUID targetId = target != null ? target.getUniqueId() : null;

        // Try to find offline player by name
        if (targetId == null) {
            @SuppressWarnings("deprecation")
            var offlinePlayer = Bukkit.getOfflinePlayer(targetName);
            if (offlinePlayer.hasPlayedBefore()) {
                targetId = offlinePlayer.getUniqueId();
            }
        }

        if (targetId == null) {
            error(player, "Player '" + targetName + "' not found.");
            return true;
        }

        if (storage.unshareHome(player.getUniqueId(), name, targetId)) {
            success(player, "Unshared home '" + name + "' from " + targetName + ".");
        } else {
            error(player, "Home '" + name + "' not found.");
        }
        return true;
    }

    private boolean handleTeleport(Player player, String input) {
        UUID playerId = player.getUniqueId();
        String homeName;
        UUID ownerId;

        // Check for owner:name syntax (for shared homes)
        if (input.contains(":")) {
            String[] parts = input.split(":", 2);
            String ownerName = parts[0];
            homeName = parts[1].toLowerCase();

            @SuppressWarnings("deprecation")
            var offlinePlayer = Bukkit.getOfflinePlayer(ownerName);
            if (!offlinePlayer.hasPlayedBefore()) {
                error(player, "Player '" + ownerName + "' not found.");
                return true;
            }
            ownerId = offlinePlayer.getUniqueId();

            // Check if shared with player
            var home = storage.getHome(ownerId, homeName);
            if (home.isEmpty() || !home.get().isSharedWith(playerId)) {
                error(player, "Home '" + input + "' not found or not shared with you.");
                return true;
            }
        } else {
            homeName = input.toLowerCase();
            ownerId = playerId;
        }

        var homeOpt = storage.getHome(ownerId, homeName);
        if (homeOpt.isEmpty()) {
            error(player, "Home '" + homeName + "' not found.");
            return true;
        }

        Home home = homeOpt.get();
        Location location = home.toLocation();
        if (location == null) {
            error(player, "The world for this home no longer exists.");
            return true;
        }

        info(player, "Teleporting to '" + home.name() + "'...");
        teleporter.teleport(player, location, success -> {});
        return true;
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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        UUID playerId = player.getUniqueId();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> completions = new ArrayList<>(SUBCOMMANDS);
            // Add home names for direct teleport
            storage.getOwnedHomes(playerId).stream()
                    .map(Home::name)
                    .forEach(completions::add);
            // Add shared homes
            storage.getSharedWithPlayer(playerId).stream()
                    .map(e -> getPlayerName(e.ownerId()) + ":" + e.home().name())
                    .forEach(completions::add);
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(partial))
                    .toList();
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            if (subcommand.equals("set")) {
                return List.of("<name>");
            }

            if (subcommand.equals("delete") || subcommand.equals("share") || subcommand.equals("unshare")) {
                return storage.getOwnedHomes(playerId).stream()
                        .map(Home::name)
                        .filter(s -> s.startsWith(partial))
                        .toList();
            }
        }

        if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            String partial = args[2].toLowerCase();

            if (subcommand.equals("share") || subcommand.equals("unshare")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(s -> s.toLowerCase().startsWith(partial))
                        .filter(s -> !s.equalsIgnoreCase(player.getName()))
                        .toList();
            }
        }

        return List.of();
    }

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
}
