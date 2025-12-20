package sh.joey.mc.multiworld;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.List;
import java.util.Optional;

/**
 * /world command for navigating between configured worlds.
 * <p>
 * Usage:
 * - /world - lists available worlds
 * - /world [name] - teleports to the specified world
 */
public final class WorldCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("World").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final SiqiJoeyPlugin plugin;
    private final WorldManager worldManager;
    private final SafeTeleporter safeTeleporter;
    private final PlayerWorldPositionStorage positionStorage;

    public WorldCommand(
            SiqiJoeyPlugin plugin,
            WorldManager worldManager,
            SafeTeleporter safeTeleporter,
            PlayerWorldPositionStorage positionStorage
    ) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.safeTeleporter = safeTeleporter;
        this.positionStorage = positionStorage;
    }

    @Override
    public String getName() {
        return "world";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return Completable.complete();
        }

        if (args.length == 0) {
            showWorldList(player);
            return Completable.complete();
        }

        String worldName = args[0].toLowerCase();
        return teleportToWorld(player, worldName);
    }

    private void showWorldList(Player player) {
        if (worldManager.getWorldNames().isEmpty()) {
            player.sendMessage(PREFIX.append(
                    Component.text("No custom worlds are configured.").color(NamedTextColor.GRAY)));
            return;
        }

        player.sendMessage(PREFIX.append(
                Component.text("Available Worlds:").color(NamedTextColor.WHITE)));

        for (String name : worldManager.getWorldNames()) {
            Optional<WorldConfig> configOpt = worldManager.getConfig(name);
            if (configOpt.isEmpty()) continue;

            WorldConfig config = configOpt.get();
            String group = config.inventoryGroup();
            GameMode gamemode = config.gamemode();

            boolean isCurrentWorld = player.getWorld().getName().equalsIgnoreCase(name);

            Component worldName = Component.text(name)
                    .color(isCurrentWorld ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/world " + name))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to teleport")));

            Component entry = Component.text("  ")
                    .append(worldName)
                    .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(gamemode.name().toLowerCase()).color(NamedTextColor.GRAY))
                    .append(Component.text(" [" + group + "]").color(NamedTextColor.YELLOW));

            if (isCurrentWorld) {
                entry = entry.append(Component.text(" (current)").color(NamedTextColor.GREEN));
            }

            player.sendMessage(entry);
        }
    }

    private Completable teleportToWorld(Player player, String worldName) {
        return Completable.defer(() -> {
            Optional<World> worldOpt = worldManager.getWorld(worldName);

            if (worldOpt.isEmpty() || worldManager.isHidden(worldName)) {
                player.sendMessage(PREFIX.append(
                        Component.text("World '").color(NamedTextColor.RED)
                                .append(Component.text(worldName).color(NamedTextColor.WHITE))
                                .append(Component.text("' not found.").color(NamedTextColor.RED))));
                return Completable.complete();
            }

            World world = worldOpt.get();

            if (player.getWorld().equals(world)) {
                player.sendMessage(PREFIX.append(
                        Component.text("You are already in this world!").color(NamedTextColor.YELLOW)));
                return Completable.complete();
            }

            if (safeTeleporter.hasPendingTeleport(player.getUniqueId())) {
                player.sendMessage(PREFIX.append(
                        Component.text("You already have a teleport in progress!").color(NamedTextColor.RED)));
                return Completable.complete();
            }

            // Look up stored position, fall back to world spawn
            return positionStorage.getPosition(player.getUniqueId(), world)
                    .defaultIfEmpty(world.getSpawnLocation())
                    .observeOn(plugin.mainScheduler())
                    .flatMapCompletable(destination -> Completable.create(emitter -> {
                        player.sendMessage(PREFIX.append(
                                Component.text("Teleporting to ").color(NamedTextColor.WHITE)
                                        .append(Component.text(world.getName()).color(NamedTextColor.AQUA))
                                        .append(Component.text("...").color(NamedTextColor.WHITE))));

                        // SafeTeleporter handles saving departure position for cross-world teleports
                        safeTeleporter.teleport(player, destination, success -> {
                            if (!success) {
                                player.sendMessage(PREFIX.append(
                                        Component.text("Teleport was cancelled.").color(NamedTextColor.RED)));
                            }
                            emitter.onComplete();
                        });
                    }));
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length != 1) {
                return null;
            }

            String prefix = args[0].toLowerCase();
            List<Completion> completions = worldManager.getWorldNames().stream()
                    .filter(name -> name.startsWith(prefix))
                    .map(Completion::completion)
                    .toList();

            return completions.isEmpty() ? null : completions;
        });
    }
}
