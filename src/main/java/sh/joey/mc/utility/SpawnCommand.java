package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.teleport.SafeTeleporter;

/**
 * /spawn - teleport to world spawn
 */
public final class SpawnCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Spawn").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final SiqiJoeyPlugin plugin;
    private final SpawnStorage storage;
    private final SafeTeleporter teleporter;

    public SpawnCommand(SiqiJoeyPlugin plugin, SpawnStorage storage, SafeTeleporter teleporter) {
        this.plugin = plugin;
        this.storage = storage;
        this.teleporter = teleporter;
    }

    @Override
    public String getName() {
        return "spawn";
    }

    @Override
    public String getPermission() {
        return "smp.spawn";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            return storage.getSpawn(player.getWorld().getUID())
                    .observeOn(plugin.mainScheduler())
                    .doOnSuccess(spawn -> {
                        Location loc = spawn.toLocation();
                        if (loc == null) {
                            // Fallback to world spawn
                            loc = player.getWorld().getSpawnLocation();
                        }
                        info(sender, "Teleporting to spawn...");
                        teleporter.teleport(player, loc, success -> {});
                    })
                    .doOnComplete(() -> {
                        // No custom spawn, use world spawn
                        Location loc = player.getWorld().getSpawnLocation();
                        info(sender, "Teleporting to spawn...");
                        teleporter.teleport(player, loc, success -> {});
                    })
                    .doOnError(err -> {
                        plugin.getLogger().warning("Failed to get spawn: " + err.getMessage());
                        error(sender, "Failed to load spawn point.");
                    })
                    .onErrorComplete()
                    .ignoreElement();
        });
    }

    private void info(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GRAY)));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }
}
