package sh.joey.mc.resourcepack;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.resourcepack.ResourcePackConfig.ResourcePackEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * /rp or /resourcepack - manage resource pack preferences
 *
 * Subcommands:
 * - /rp or /rp list - show available packs
 * - /rp select <id> - select a pack
 * - /rp clear - clear saved pack
 */
public final class ResourcePackCommand implements Command {

    private static final Component PREFIX = ResourcePackManager.prefix();

    private final SiqiJoeyPlugin plugin;
    private final ResourcePackConfig config;
    private final ResourcePackStorage storage;
    private final ResourcePackManager manager;

    public ResourcePackCommand(
            SiqiJoeyPlugin plugin,
            ResourcePackConfig config,
            ResourcePackStorage storage,
            ResourcePackManager manager) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "resourcepack";
    }

    @Override
    public String getPermission() {
        return "smp.resourcepack";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                return handleList(player);
            }

            String first = args[0].toLowerCase();

            return switch (first) {
                case "select" -> handleSelect(player, args);
                case "clear" -> handleClear(player);
                default -> {
                    error(player, "Unknown subcommand. Use /rp for help.");
                    yield Completable.complete();
                }
            };
        });
    }

    private Completable handleList(Player player) {
        if (config.packs().isEmpty()) {
            info(player, "No resource packs are configured.");
            return Completable.complete();
        }

        return storage.getPlayerPack(player.getUniqueId())
                .observeOn(plugin.mainScheduler())
                .defaultIfEmpty("")
                .flatMapCompletable(currentPack -> {
                    player.sendMessage(PREFIX.append(
                            Component.text("Available Resource Packs:").color(NamedTextColor.WHITE)));
                    player.sendMessage(Component.empty());

                    for (ResourcePackEntry pack : config.packs().values()) {
                        boolean isSelected = pack.id().equals(currentPack);

                        Component name = Component.text("  ")
                                .append(Component.text(pack.name())
                                        .color(isSelected ? NamedTextColor.GREEN : NamedTextColor.AQUA)
                                        .clickEvent(ClickEvent.runCommand("/rp select " + pack.id()))
                                        .hoverEvent(HoverEvent.showText(
                                                Component.text("Click to select this pack").color(NamedTextColor.GRAY))));

                        if (isSelected) {
                            name = name.append(Component.text(" (current)").color(NamedTextColor.DARK_GREEN));
                        }

                        player.sendMessage(name);

                        if (!pack.description().isEmpty()) {
                            player.sendMessage(Component.text("    " + pack.description())
                                    .color(NamedTextColor.GRAY));
                        }
                    }

                    // Show clear option if player has a saved pack
                    if (!currentPack.isEmpty()) {
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("  ")
                                .append(Component.text("[Clear Selection]")
                                        .color(NamedTextColor.RED)
                                        .clickEvent(ClickEvent.runCommand("/rp clear"))
                                        .hoverEvent(HoverEvent.showText(
                                                Component.text("Click to remove your saved pack")
                                                        .color(NamedTextColor.GRAY)))));
                    }

                    return Completable.complete();
                })
                .onErrorResumeNext(err -> {
                    plugin.getLogger().warning("Failed to get player pack: " + err.getMessage());
                    error(player, "Failed to load your settings.");
                    return Completable.complete();
                });
    }

    private Completable handleSelect(Player player, String[] args) {
        if (args.length < 2) {
            error(player, "Usage: /rp select <pack>");
            return Completable.complete();
        }

        String packId = args[1].toLowerCase();
        ResourcePackEntry pack = config.get(packId);

        if (pack == null) {
            error(player, "Resource pack '" + packId + "' not found.");
            return Completable.complete();
        }

        info(player, "Sending resource pack '" + pack.name() + "'...");
        manager.sendPack(player, pack);

        return Completable.complete();
    }

    private Completable handleClear(Player player) {
        return storage.clearPlayerPack(player.getUniqueId())
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(player, "Resource pack preference cleared."))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to clear pack for " + player.getName() + ": " + err.getMessage());
                    error(player, "Failed to clear preference.");
                })
                .onErrorComplete();
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                List<String> options = List.of("list", "select", "clear");

                return Maybe.just(options.stream()
                        .filter(opt -> opt.startsWith(prefix))
                        .map(Completion::completion)
                        .toList());
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
                String prefix = args[1].toLowerCase();

                List<Completion> completions = new ArrayList<>();
                for (ResourcePackEntry pack : config.packs().values()) {
                    if (pack.id().startsWith(prefix)) {
                        completions.add(Completion.completion(
                                pack.id(),
                                Component.text(pack.name()).color(NamedTextColor.GREEN)));
                    }
                }

                return Maybe.just(completions);
            }

            return Maybe.empty();
        });
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
