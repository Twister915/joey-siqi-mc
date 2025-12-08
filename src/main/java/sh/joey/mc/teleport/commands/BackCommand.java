package sh.joey.mc.teleport.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.teleport.BackLocation;
import sh.joey.mc.teleport.LocationTracker;
import sh.joey.mc.teleport.Messages;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.logging.Logger;

/**
 * /back command - returns player to death location or last teleport-from location.
 */
public final class BackCommand implements CommandExecutor {
    private final Logger logger;
    private final LocationTracker locationTracker;
    private final SafeTeleporter safeTeleporter;

    public BackCommand(SiqiJoeyPlugin plugin, LocationTracker locationTracker, SafeTeleporter safeTeleporter) {
        this.logger = plugin.getLogger();
        this.locationTracker = locationTracker;
        this.safeTeleporter = safeTeleporter;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (safeTeleporter.hasPendingTeleport(player.getUniqueId())) {
            Messages.warning(player, "You already have a teleport in progress!");
            return true;
        }

        locationTracker.getBackLocation(player.getUniqueId())
                .subscribe(
                        backLocation -> handleBackLocation(player, backLocation),
                        err -> {
                            logger.warning("Failed to get back location: " + err.getMessage());
                            Messages.error(player, "Failed to retrieve your back location.");
                        },
                        () -> Messages.error(player, "You don't have anywhere to go back to!")
                );

        return true;
    }

    private void handleBackLocation(Player player, BackLocation backLocation) {
        var destinationOpt = backLocation.toBukkitLocation();

        if (destinationOpt.isEmpty()) {
            Messages.error(player, "That world is no longer loaded!");
            return;
        }

        Location destination = destinationOpt.get();
        boolean isDeathLocation = backLocation.type() == BackLocation.LocationType.DEATH;

        if (isDeathLocation) {
            Messages.info(player, "Returning to your death location...");
        } else {
            Messages.info(player, "Returning to your previous location...");
        }

        safeTeleporter.teleport(player, destination, success -> {
            if (success) {
                // Clear back location after successful return
                locationTracker.clearBackLocation(player.getUniqueId())
                        .subscribe(
                                () -> {},
                                err -> logger.warning("Failed to clear back location: " + err.getMessage())
                        );
            }
        });
    }
}
