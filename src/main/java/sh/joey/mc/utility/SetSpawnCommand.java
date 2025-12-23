package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

/**
 * /setspawn - set spawn point for the current world
 */
public final class SetSpawnCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Spawn").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final SiqiJoeyPlugin plugin;
    private final SpawnStorage storage;

    public SetSpawnCommand(SiqiJoeyPlugin plugin, SpawnStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @Override
    public String getName() {
        return "setspawn";
    }

    @Override
    public String getPermission() {
        return "smp.setspawn";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            return storage.setSpawn(player.getWorld().getUID(), player.getLocation(), player.getUniqueId())
                    .observeOn(plugin.mainScheduler())
                    .doOnComplete(() -> success(sender, "Spawn point set for " + player.getWorld().getName() + "!"))
                    .doOnError(err -> {
                        plugin.getLogger().warning("Failed to set spawn: " + err.getMessage());
                        error(sender, "Failed to set spawn point.");
                    })
                    .onErrorComplete();
        });
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }
}
