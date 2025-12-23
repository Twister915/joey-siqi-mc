package sh.joey.mc.utility;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.List;
import java.util.Set;

/**
 * /warp [name] - teleport to a warp
 * /warp set <name> - create/update a warp
 * /warp delete <name> - delete a warp
 * /warp list - list all warps
 */
public final class WarpCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Warp").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final Set<String> SUBCOMMANDS = Set.of("set", "delete", "list");

    private final SiqiJoeyPlugin plugin;
    private final WarpStorage storage;
    private final SafeTeleporter teleporter;

    public WarpCommand(SiqiJoeyPlugin plugin, WarpStorage storage, SafeTeleporter teleporter) {
        this.plugin = plugin;
        this.storage = storage;
        this.teleporter = teleporter;
    }

    @Override
    public String getName() {
        return "warp";
    }

    @Override
    public String getPermission() {
        return "smp.warp";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length == 0) {
                return handleList(sender);
            }

            String first = args[0].toLowerCase();

            return switch (first) {
                case "set" -> handleSet(sender, args);
                case "delete" -> handleDelete(sender, args);
                case "list" -> handleList(sender);
                default -> handleTeleport(sender, first);
            };
        });
    }

    private Completable handleTeleport(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return Completable.complete();
        }

        return storage.getWarp(name)
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(warp -> {
                    Location loc = warp.toLocation();
                    if (loc == null) {
                        error(sender, "That warp's world is not loaded.");
                        return;
                    }
                    info(sender, "Teleporting to warp '" + warp.name() + "'...");
                    teleporter.teleport(player, loc, success -> {});
                })
                .doOnComplete(() -> error(sender, "Warp '" + name + "' not found."))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to get warp: " + err.getMessage());
                    error(sender, "Failed to load warp.");
                })
                .onErrorComplete()
                .ignoreElement();
    }

    private Completable handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return Completable.complete();
        }

        if (!sender.hasPermission("smp.warp.set")) {
            error(sender, "You don't have permission to create warps.");
            return Completable.complete();
        }

        if (args.length < 2) {
            error(sender, "Usage: /warp set <name>");
            return Completable.complete();
        }

        String name = args[1].toLowerCase();

        if (SUBCOMMANDS.contains(name)) {
            error(sender, "Cannot use '" + name + "' as a warp name.");
            return Completable.complete();
        }

        return storage.setWarp(name, player.getLocation(), player.getUniqueId())
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(sender, "Warp '" + name + "' has been set!"))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to set warp: " + err.getMessage());
                    error(sender, "Failed to set warp.");
                })
                .onErrorComplete();
    }

    private Completable handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smp.warp.set")) {
            error(sender, "You don't have permission to delete warps.");
            return Completable.complete();
        }

        if (args.length < 2) {
            error(sender, "Usage: /warp delete <name>");
            return Completable.complete();
        }

        String name = args[1].toLowerCase();

        return storage.deleteWarp(name)
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(deleted -> {
                    if (deleted) {
                        success(sender, "Warp '" + name + "' has been deleted.");
                    } else {
                        error(sender, "Warp '" + name + "' not found.");
                    }
                })
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to delete warp: " + err.getMessage());
                    error(sender, "Failed to delete warp.");
                })
                .onErrorComplete()
                .ignoreElement();
    }

    private Completable handleList(CommandSender sender) {
        return storage.getAllWarps()
                .toList()
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(warps -> {
                    if (warps.isEmpty()) {
                        info(sender, "No warps have been created yet.");
                        return;
                    }

                    sender.sendMessage(PREFIX.append(Component.text("Available Warps (" + warps.size() + "):")
                            .color(NamedTextColor.WHITE)));

                    for (var warp : warps) {
                        Component entry = Component.text("  ")
                                .append(Component.text(warp.name())
                                        .color(NamedTextColor.AQUA)
                                        .decorate(TextDecoration.BOLD)
                                        .clickEvent(ClickEvent.runCommand("/warp " + warp.name()))
                                        .hoverEvent(HoverEvent.showText(Component.text("Click to teleport"))));

                        sender.sendMessage(entry);
                    }
                })
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to list warps: " + err.getMessage());
                    error(sender, "Failed to load warps.");
                })
                .onErrorComplete()
                .ignoreElement();
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();

                // Get warp names from database
                return storage.getAllWarps()
                        .map(WarpStorage.Warp::name)
                        .toList()
                        .map(warps -> {
                            // Add subcommands if user has permission
                            if (sender.hasPermission("smp.warp.set")) {
                                warps.addAll(List.of("set", "delete", "list"));
                            } else {
                                warps.add("list");
                            }

                            return warps.stream()
                                    .filter(name -> name.startsWith(prefix))
                                    .sorted()
                                    .map(Completion::completion)
                                    .toList();
                        })
                        .toMaybe();
            }

            if (args.length == 2 && (args[0].equalsIgnoreCase("delete"))) {
                String prefix = args[1].toLowerCase();

                return storage.getAllWarps()
                        .map(WarpStorage.Warp::name)
                        .filter(name -> name.startsWith(prefix))
                        .toList()
                        .map(names -> names.stream().map(Completion::completion).toList())
                        .toMaybe();
            }

            return Maybe.empty();
        });
    }

    private void info(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GRAY)));
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }
}
