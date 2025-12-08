package sh.joey.mc.teleport.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import sh.joey.mc.teleport.LocationTracker;
import sh.joey.mc.teleport.Messages;
import sh.joey.mc.teleport.SafeTeleporter;

/**
 * /back command - returns player to death location or last teleport-from location.
 */
public final class BackCommand implements CommandExecutor {
    private final LocationTracker locationTracker;
    private final SafeTeleporter safeTeleporter;

    public BackCommand(LocationTracker locationTracker, SafeTeleporter safeTeleporter) {
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

        var backLocation = locationTracker.getBackLocation(player.getUniqueId());

        if (backLocation.isEmpty()) {
            Messages.error(player, "You don't have anywhere to go back to!");
            return true;
        }

        Location destination = backLocation.get();
        boolean isDeathLocation = locationTracker.hasDeathLocation(player.getUniqueId());

        if (isDeathLocation) {
            Messages.info(player, "Returning to your death location...");
        } else {
            Messages.info(player, "Returning to your previous location...");
        }

        safeTeleporter.teleport(player, destination, success -> {
            if (success && isDeathLocation) {
                // Clear death location after successful return
                locationTracker.clearDeathLocation(player.getUniqueId());
            }
        });

        return true;
    }
}
