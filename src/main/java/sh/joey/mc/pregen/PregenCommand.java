package sh.joey.mc.pregen;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.List;
import java.util.Map;

import static sh.joey.mc.pregen.Messages.formatBytes;

/**
 * Admin command for controlling chunk pre-generation.
 * Usage: /pregen [status|start|stop|pause]
 */
public final class PregenCommand implements Command {

    private final PregenManager manager;
    private final PregenBossBarProvider bossBarProvider;

    public PregenCommand(PregenManager manager, PregenBossBarProvider bossBarProvider) {
        this.manager = manager;
        this.bossBarProvider = bossBarProvider;
    }

    @Override
    public String getName() {
        return "pregen";
    }

    @Override
    public String getPermission() {
        return "smp.pregen";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
                showStatus(sender);
                return;
            }

            switch (args[0].toLowerCase()) {
                case "start" -> {
                    manager.start();
                    Messages.success(sender, "Pre-generation started.");
                }
                case "stop" -> {
                    manager.stop();
                    Messages.success(sender, "Pre-generation stopped and reset.");
                }
                case "pause" -> {
                    manager.pause();
                    Messages.success(sender, "Pre-generation paused.");
                }
                case "force" -> {
                    manager.toggleForce();
                    if (manager.isForced()) {
                        Messages.success(sender, "Forced mode enabled - running at SLOW speed.");
                    } else {
                        Messages.info(sender, "Forced mode disabled.");
                    }
                }
                case "monitor" -> {
                    if (!(sender instanceof org.bukkit.entity.Player player)) {
                        Messages.error(sender, "This command can only be used by players.");
                        return;
                    }
                    boolean enabled = bossBarProvider.toggleMonitoring(player.getUniqueId());
                    if (enabled) {
                        Messages.success(sender, "Boss bar monitoring enabled.");
                    } else {
                        Messages.info(sender, "Boss bar monitoring disabled.");
                    }
                }
                default -> Messages.error(sender, "Usage: /pregen [status|start|stop|pause|force|monitor]");
            }
        });
    }

    private void showStatus(CommandSender sender) {
        PregenManager.State state = manager.getState();
        String currentWorld = manager.getCurrentWorld();
        Map<String, PregenManager.WorldProgress> progress = manager.getWorldProgress();
        PregenConfig config = manager.getConfig();

        sender.sendMessage(Messages.PREFIX.append(
                Component.text("Pre-generation Status").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.empty());

        // State
        Component stateComponent = switch (state) {
            case RUNNING -> manager.isForced()
                    ? Component.text("RUNNING (FORCED)").color(NamedTextColor.LIGHT_PURPLE)
                    : Component.text("RUNNING").color(NamedTextColor.GREEN);
            case PAUSED -> Component.text("PAUSED").color(NamedTextColor.YELLOW);
            case WAITING -> Component.text("WAITING (players online)").color(NamedTextColor.YELLOW);
            case IDLE -> Component.text("IDLE").color(NamedTextColor.GRAY);
        };
        sender.sendMessage(Component.text("State: ").color(NamedTextColor.GRAY)
                .append(stateComponent));

        // Rate (show effective rate, which is SLOW in forced mode)
        PregenRate effectiveRate = manager.isForced() ? PregenRate.SLOW : config.rate();
        Component rateComponent = Component.text("Rate: ").color(NamedTextColor.GRAY)
                .append(Component.text(effectiveRate.name()).color(NamedTextColor.AQUA))
                .append(Component.text(" (" + effectiveRate.getMinChunksPerTick() +
                                "-" + effectiveRate.getMaxChunksPerTick() + " chunks/tick)")
                        .color(NamedTextColor.DARK_GRAY));
        if (manager.isForced() && config.rate() != PregenRate.SLOW) {
            rateComponent = rateComponent.append(
                    Component.text(" [forced from " + config.rate().name() + "]")
                            .color(NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(rateComponent);

        // Area
        sender.sendMessage(Component.text("Area: ").color(NamedTextColor.GRAY)
                .append(Component.text(String.format("%,d x %,d blocks (%,d chunks/world)",
                                config.sideLength(), config.sideLength(), config.totalChunks()))
                        .color(NamedTextColor.DARK_GRAY)));

        // Estimated map size (average ~8KB per chunk for overworld terrain)
        long bytesPerChunk = 8 * 1024;
        int worldCount = Math.max(1, progress.size());
        long totalChunksAllWorlds = config.totalChunks() * worldCount;
        long projectedTotalBytes = totalChunksAllWorlds * bytesPerChunk;

        long generatedChunksAllWorlds = progress.values().stream()
                .mapToLong(PregenManager.WorldProgress::generatedChunks)
                .sum();
        long generatedBytes = generatedChunksAllWorlds * bytesPerChunk;

        sender.sendMessage(Component.text("Est. Size: ").color(NamedTextColor.GRAY)
                .append(Component.text(formatBytes(generatedBytes))
                        .color(NamedTextColor.WHITE))
                .append(Component.text(" / ")
                        .color(NamedTextColor.DARK_GRAY))
                .append(Component.text(formatBytes(projectedTotalBytes))
                        .color(NamedTextColor.WHITE))
                .append(Component.text(" (~8KB/chunk)")
                        .color(NamedTextColor.DARK_GRAY)));

        sender.sendMessage(Component.empty());

        // Per-world progress
        if (progress.isEmpty()) {
            sender.sendMessage(Component.text("No worlds configured or initialized.")
                    .color(NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Configure worlds in config.yml under pregen.worlds")
                    .color(NamedTextColor.DARK_GRAY));
        } else {
            for (var entry : progress.entrySet()) {
                PregenManager.WorldProgress wp = entry.getValue();
                boolean isCurrent = entry.getKey().equals(currentWorld);

                NamedTextColor nameColor = wp.complete() ? NamedTextColor.GREEN
                        : (isCurrent ? NamedTextColor.YELLOW : NamedTextColor.GRAY);

                String statusIcon = wp.complete() ? " [DONE]"
                        : (isCurrent ? " [ACTIVE]" : "");

                sender.sendMessage(
                        Component.text(wp.worldName() + statusIcon).color(nameColor));

                // Progress bar
                Component progressBar = buildProgressBar(wp.getProgressPercent());
                sender.sendMessage(Component.text("  ").append(progressBar));

                // Stats
                sender.sendMessage(Component.text("  ")
                        .append(Component.text(String.format(
                                        "%.1f%% (%,d/%,d) - Gen: %,d, Skip: %,d",
                                        wp.getProgressPercent(),
                                        wp.getProcessedChunks(),
                                        wp.totalChunks(),
                                        wp.generatedChunks(),
                                        wp.skippedChunks()))
                                .color(NamedTextColor.DARK_GRAY)));

                if (isCurrent && !wp.complete() && state == PregenManager.State.RUNNING) {
                    sender.sendMessage(Component.text("  ETA: ")
                            .color(NamedTextColor.GRAY)
                            .append(Component.text(wp.getEtaFormatted())
                                    .color(NamedTextColor.WHITE)));
                }
            }
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text("Commands: /pregen start|stop|pause|force|monitor")
                .color(NamedTextColor.DARK_GRAY));
    }

    private Component buildProgressBar(double percent) {
        int filled = (int) (percent / 5);  // 20 chars total
        int empty = 20 - filled;

        return Component.text("[").color(NamedTextColor.DARK_GRAY)
                .append(Component.text("|".repeat(Math.max(0, filled))).color(NamedTextColor.GREEN))
                .append(Component.text("|".repeat(Math.max(0, empty))).color(NamedTextColor.DARK_GRAY))
                .append(Component.text("]").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(String.format(" %.1f%%", percent))
                        .color(NamedTextColor.WHITE));
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                return List.of("status", "start", "stop", "pause", "force", "monitor").stream()
                        .filter(s -> s.startsWith(prefix))
                        .map(Completion::completion)
                        .toList();
            }
            return null;
        });
    }
}
