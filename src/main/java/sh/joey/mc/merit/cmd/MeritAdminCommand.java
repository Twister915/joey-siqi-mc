package sh.joey.mc.merit.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.merit.LevelCalculator;
import sh.joey.mc.merit.MeritManager;
import sh.joey.mc.merit.Messages;
import sh.joey.mc.merit.challenge.Challenge;
import sh.joey.mc.player.PlayerResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin command for managing merit.
 * Usage: /meritadmin grant|set|reset <player> <amount>
 */
public final class MeritAdminCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final MeritManager meritManager;
    private final PlayerResolver playerResolver;

    public MeritAdminCommand(SiqiJoeyPlugin plugin, MeritManager meritManager, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.meritManager = meritManager;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "meritadmin";
    }

    @Override
    public @Nullable String getPermission() {
        return "smp.merit.admin";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (args.length < 2) {
                showUsage(sender);
                return;
            }

            String subcommand = args[0].toLowerCase();
            String playerName = args[1];

            switch (subcommand) {
                case "grant" -> {
                    if (args.length < 3) {
                        error(sender, "Usage: /meritadmin grant <player> <amount>");
                        return;
                    }
                    handleGrant(sender, playerName, args[2]);
                }
                case "set" -> {
                    if (args.length < 3) {
                        error(sender, "Usage: /meritadmin set <player> <amount>");
                        return;
                    }
                    handleSet(sender, playerName, args[2]);
                }
                case "reset" -> handleReset(sender, playerName);
                case "inspect" -> handleInspect(sender, playerName);
                case "reload" -> handleReload(sender);
                default -> showUsage(sender);
            }
        });
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(Messages.PREFIX.append(Component.text("Merit Admin Commands:", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("  /meritadmin inspect <player>", NamedTextColor.AQUA)
                .append(Component.text(" - View player's merit and challenges", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /meritadmin grant <player> <amount>", NamedTextColor.AQUA)
                .append(Component.text(" - Add merit", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /meritadmin set <player> <amount>", NamedTextColor.AQUA)
                .append(Component.text(" - Set total merit", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /meritadmin reset <player>", NamedTextColor.AQUA)
                .append(Component.text(" - Reset to 0", NamedTextColor.GRAY)));
    }

    private void handleGrant(CommandSender sender, String playerName, String amountStr) {
        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            error(sender, "Invalid amount: " + amountStr);
            return;
        }

        if (amount <= 0) {
            error(sender, "Amount must be positive.");
            return;
        }

        playerResolver.resolvePlayerId(playerName)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        playerId -> {
                            meritManager.getProgressTracker().awardMerit(playerId, amount);
                            success(sender, "Granted " + amount + " merit to " + playerName);
                        },
                        err -> error(sender, "Failed to resolve player: " + err.getMessage()),
                        () -> error(sender, "Player not found: " + playerName)
                );
    }

    private void handleSet(CommandSender sender, String playerName, String amountStr) {
        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            error(sender, "Invalid amount: " + amountStr);
            return;
        }

        if (amount < 0) {
            error(sender, "Amount cannot be negative.");
            return;
        }

        LevelCalculator calc = meritManager.getLevelCalculator();
        int level = calc.levelForMerit(amount);

        playerResolver.resolvePlayerId(playerName)
                .flatMapCompletable(playerId ->
                        meritManager.getStorage().setMerit(playerId, amount, level))
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        () -> success(sender, "Set " + playerName + "'s merit to " + amount + " (Level " + level + ")"),
                        err -> error(sender, "Failed: " + err.getMessage())
                );
    }

    private void handleReset(CommandSender sender, String playerName) {
        playerResolver.resolvePlayerId(playerName)
                .flatMapCompletable(playerId ->
                        meritManager.getStorage().setMerit(playerId, 0, 1))
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        () -> success(sender, "Reset " + playerName + "'s merit to 0"),
                        err -> error(sender, "Failed: " + err.getMessage())
                );
    }

    private void handleInspect(CommandSender sender, String playerName) {
        playerResolver.resolvePlayerId(playerName)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        playerId -> displayInspect(sender, playerName, playerId),
                        err -> error(sender, "Failed to resolve player: " + err.getMessage()),
                        () -> error(sender, "Player not found: " + playerName)
                );
    }

    private void displayInspect(CommandSender sender, String playerName, UUID playerId) {
        var assigner = meritManager.getAssigner();
        int weekNumber = assigner.getCurrentWeekNumber();
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);

        // Get player merit
        meritManager.getStorage().getOrCreatePlayerMerit(playerId)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        merit -> {
                            sender.sendMessage(Component.empty());
                            sender.sendMessage(Messages.PREFIX.append(
                                    Component.text("Inspecting: ", NamedTextColor.GOLD)
                                            .append(Component.text(playerName, NamedTextColor.WHITE))));

                            // Level and merit
                            LevelCalculator calc = meritManager.getLevelCalculator();
                            long toNext = calc.meritToNextLevel(merit.totalMerit());
                            sender.sendMessage(Component.text("  Level: ", NamedTextColor.GRAY)
                                    .append(Component.text(merit.level(), Messages.getLevelColor(merit.level())))
                                    .append(Component.text(" | Total Merit: ", NamedTextColor.GRAY))
                                    .append(Messages.formatMerit(merit.totalMerit()))
                                    .append(Component.text(" | " + Messages.formatNumber(toNext) + " to next", NamedTextColor.DARK_GRAY)));

                            // Weekly challenges
                            sender.sendMessage(Component.text("  Weekly Challenges (Week " + weekNumber + "):", NamedTextColor.GRAY));

                            // Get progress for this player
                            meritManager.getStorage().getProgress(playerId, weekNumber)
                                    .observeOn(plugin.mainScheduler())
                                    .subscribe(
                                            progress -> displayInspectChallenges(sender, playerId, challenges, progress),
                                            err -> error(sender, "Failed to load progress: " + err.getMessage())
                                    );
                        },
                        err -> error(sender, "Failed to load merit: " + err.getMessage())
                );
    }

    private void displayInspectChallenges(CommandSender sender, UUID playerId, List<Challenge> challenges, Map<String, Long> rawProgress) {
        for (Challenge challenge : challenges) {
            long progress = calculateChallengeProgress(challenge, rawProgress);
            boolean completed = progress >= challenge.target();
            int percent = (int) (progress * 100 / challenge.target());

            NamedTextColor color = completed ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            String status = completed ? " \u2713" : " " + percent + "%";

            Component tooltip = Component.text(challenge.description(), NamedTextColor.GRAY)
                    .append(Component.newline())
                    .append(Component.text("Progress: " + progress + "/" + challenge.target(), NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(Component.text("Tracks: " + String.join(", ", challenge.trackingKeys()), NamedTextColor.DARK_GRAY));

            sender.sendMessage(Component.text("    ", NamedTextColor.GRAY)
                    .append(Component.text(challenge.name(), color))
                    .append(Component.text(status, completed ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                    .append(Component.text(" +" + challenge.meritReward() + "M", NamedTextColor.LIGHT_PURPLE))
                    .hoverEvent(HoverEvent.showText(tooltip)));
        }
    }

    private long calculateChallengeProgress(Challenge challenge, Map<String, Long> rawProgress) {
        long total = 0;
        for (String trackingKey : challenge.trackingKeys()) {
            if (trackingKey.endsWith(":ANY")) {
                String prefix = trackingKey.substring(0, trackingKey.length() - 3);
                for (var entry : rawProgress.entrySet()) {
                    if (entry.getKey().startsWith(prefix)) {
                        total += entry.getValue();
                    }
                }
            } else {
                total += rawProgress.getOrDefault(trackingKey, 0L);
            }
        }
        return total;
    }

    private void handleReload(CommandSender sender) {
        success(sender, "Merit system reloaded.");
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(Messages.PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(Messages.PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }

    @Override
    public Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<AsyncTabCompleteEvent.Completion> completions = List.of(
                    AsyncTabCompleteEvent.Completion.completion("inspect"),
                    AsyncTabCompleteEvent.Completion.completion("grant"),
                    AsyncTabCompleteEvent.Completion.completion("set"),
                    AsyncTabCompleteEvent.Completion.completion("reset")
            );
            return Maybe.just(completions);
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            List<AsyncTabCompleteEvent.Completion> completions = plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .map(AsyncTabCompleteEvent.Completion::completion)
                    .collect(Collectors.toList());
            return Maybe.just(completions);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("set"))) {
            List<AsyncTabCompleteEvent.Completion> completions = List.of(
                    AsyncTabCompleteEvent.Completion.completion("100"),
                    AsyncTabCompleteEvent.Completion.completion("500"),
                    AsyncTabCompleteEvent.Completion.completion("1000"),
                    AsyncTabCompleteEvent.Completion.completion("5000"),
                    AsyncTabCompleteEvent.Completion.completion("10000")
            );
            return Maybe.just(completions);
        }
        return Maybe.empty();
    }
}
