package sh.joey.mc.teleport.commands;

import io.reactivex.rxjava3.core.Completable;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.teleport.BackLocation;
import sh.joey.mc.teleport.LocationTracker;
import sh.joey.mc.teleport.Messages;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.logging.Logger;

/**
 * /back command - returns player to death location or last teleport-from location.
 */
public final class BackCommand implements Command {
    private final Logger logger;
    private final LocationTracker locationTracker;
    private final SafeTeleporter safeTeleporter;

    public BackCommand(SiqiJoeyPlugin plugin, LocationTracker locationTracker, SafeTeleporter safeTeleporter) {
        this.logger = plugin.getLogger();
        this.locationTracker = locationTracker;
        this.safeTeleporter = safeTeleporter;
    }

    @Override
    public String getName() {
        return "back";
    }

    @Override
    public String getPermission() {
        return "smp.back";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            if (safeTeleporter.hasPendingTeleport(player.getUniqueId())) {
                Messages.warning(player, "You already have a teleport in progress!");
                return Completable.complete();
            }

            return locationTracker.getBackLocation(player.getUniqueId())
                    .observeOn(plugin.mainScheduler())
                    .doOnSuccess(backLocation -> handleBackLocation(player, backLocation))
                    .doOnComplete(() -> Messages.error(player, "You don't have anywhere to go back to!"))
                    .doOnError(err -> {
                        logger.warning("Failed to get back location: " + err.getMessage());
                        Messages.error(player, "Failed to retrieve your back location.");
                    })
                    .onErrorComplete()
                    .ignoreElement();
        });
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

        safeTeleporter.teleport(player, destination, success -> {});
    }
}
