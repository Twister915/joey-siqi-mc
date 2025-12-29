package sh.joey.mc.rtp;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /rtp - Random teleport command
 */
public final class RtpCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final RtpManager manager;
    private final SafeTeleporter teleporter;
    private final RtpConfig config;

    public RtpCommand(SiqiJoeyPlugin plugin, RtpManager manager, SafeTeleporter teleporter, RtpConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.teleporter = teleporter;
        this.config = config;
    }

    @Override
    public String getName() {
        return "rtp";
    }

    @Override
    public String getPermission() {
        return "smp.rtp";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            // Handle /rtp select <number>
            if (args.length >= 2 && args[0].equalsIgnoreCase("select")) {
                return handleSelect(player, args[1]);
            }

            // Handle /rtp (generate candidates)
            return handleGenerate(player);
        });
    }

    private Completable handleGenerate(Player player) {
        UUID playerId = player.getUniqueId();

        // Check if in overworld
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            Messages.error(player, "RTP only works in the Overworld.");
            return Completable.complete();
        }

        // Check cooldown (unless player has bypass permission)
        if (!player.hasPermission("smp.rtp.bypass") && manager.isOnCooldown(playerId)) {
            String remaining = manager.formatRemainingCooldown(playerId);
            Messages.error(player, "You can use RTP again in " + remaining + ".");
            return Completable.complete();
        }

        // Show searching message
        Messages.info(player, "Finding locations...");

        // Generate candidates
        return manager.generateCandidates(player.getWorld())
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(candidates -> {
                    if (candidates.size() < 3) {
                        Messages.error(player, "Could not find enough safe locations. Try again or move closer to explored areas.");
                        return;
                    }

                    // Store candidates
                    manager.storeCandidates(playerId, candidates);

                    // Display the list
                    displayCandidates(player, candidates);
                })
                .doOnError(err -> {
                    plugin.getLogger().warning("RTP generation failed for " + player.getName() + ": " + err.getMessage());
                    Messages.error(player, "Something went wrong. Please try again.");
                })
                .onErrorComplete()
                .ignoreElement();
    }

    private void displayCandidates(Player player, List<RtpCandidate> candidates) {
        player.sendMessage(Messages.PREFIX.append(
                Component.text(candidates.size() + " locations found:").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());

        for (RtpCandidate candidate : candidates) {
            Component entry = Component.text("[" + candidate.index() + "] ")
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD)
                    .append(Component.text(candidate.biomeName()).color(NamedTextColor.GREEN))
                    .append(Component.text(" - ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(formatDistance(candidate.distanceFromSpawn())).color(NamedTextColor.GRAY))
                    .append(Component.text(" " + candidate.direction()).color(NamedTextColor.AQUA))
                    .clickEvent(ClickEvent.runCommand("/rtp select " + candidate.index()))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to teleport\n").color(NamedTextColor.WHITE)
                                    .append(Component.text(candidate.hint()).color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC))));

            player.sendMessage(entry);
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Messages.PREFIX.append(
                Component.text("Click a location to teleport!").color(NamedTextColor.GRAY)));
    }

    private String formatDistance(int meters) {
        if (meters >= 1000) {
            return String.format("%.1fkm", meters / 1000.0);
        }
        return meters + "m";
    }

    private Completable handleSelect(Player player, String indexStr) {
        UUID playerId = player.getUniqueId();

        // Parse index
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            Messages.error(player, "Invalid option. Use /rtp to generate new locations.");
            return Completable.complete();
        }

        // Get candidate
        Optional<RtpCandidate> candidateOpt = manager.getCandidate(playerId, index);
        if (candidateOpt.isEmpty()) {
            Messages.error(player, "Your locations have expired. Use /rtp to generate new ones.");
            return Completable.complete();
        }

        RtpCandidate candidate = candidateOpt.get();

        // Clear candidates and start cooldown
        manager.clearCandidates(playerId);

        // Only start cooldown if player doesn't have bypass
        if (!player.hasPermission("smp.rtp.bypass")) {
            manager.startCooldown(playerId);
        }

        // Teleport using SafeTeleporter
        Messages.success(player, "Teleporting to " + candidate.biomeName() + "...");
        teleporter.teleport(player, candidate.location(), success -> {
            if (success) {
                Messages.success(player, "Welcome to your new location!");
            }
        });

        return Completable.complete();
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                if ("select".startsWith(prefix)) {
                    return List.of(Completion.completion("select"));
                }
            }

            if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
                String prefix = args[1];
                return List.of("1", "2", "3", "4", "5").stream()
                        .filter(n -> n.startsWith(prefix))
                        .map(Completion::completion)
                        .toList();
            }

            return null;
        });
    }
}
