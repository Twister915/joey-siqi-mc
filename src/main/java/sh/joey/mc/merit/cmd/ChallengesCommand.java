package sh.joey.mc.merit.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.merit.LevelCalculator;
import sh.joey.mc.merit.MeritManager;
import sh.joey.mc.merit.MeritStorage;
import sh.joey.mc.merit.Messages;
import sh.joey.mc.merit.challenge.Challenge;
import sh.joey.mc.merit.challenge.ChallengeAssigner;
import sh.joey.mc.player.PlayerResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command for viewing challenges and merit progress.
 * Usage: /challenges, /level, /merit
 */
public final class ChallengesCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final MeritManager meritManager;
    private final PlayerResolver playerResolver;

    public ChallengesCommand(SiqiJoeyPlugin plugin, MeritManager meritManager, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.meritManager = meritManager;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "challenges";
    }

    @Override
    public @Nullable String getPermission() {
        return null; // Available to all players
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
                return;
            }

            if (args.length == 0) {
                showWeeklyChallenges(player);
                return;
            }

            String subcommand = args[0].toLowerCase();
            switch (subcommand) {
                case "leaderboard", "lb", "top" -> showLeaderboard(player);
                case "stats", "level" -> showStats(player);
                case "history" -> showHistory(player);
                case "info", "help" -> showInfo(player);
                default -> showWeeklyChallenges(player);
            }
        });
    }

    private void showWeeklyChallenges(Player player) {
        UUID playerId = player.getUniqueId();
        ChallengeAssigner assigner = meritManager.getAssigner();
        MeritStorage storage = meritManager.getStorage();
        int weekNumber = assigner.getCurrentWeekNumber();
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);

        // Get in-memory progress (real-time)
        Map<String, Long> inMemoryProgress = meritManager.getProgressTracker().getAllChallengeProgress(playerId);

        // Get online time from database (needed for merit claimed tracking)
        storage.getWeeklyOnlineTime(playerId, weekNumber)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        onlineTime -> displayChallenges(player, challenges, inMemoryProgress, onlineTime, weekNumber),
                        err -> Messages.error(player, "Failed to load progress: " + err.getMessage())
                );
    }

    private void displayChallenges(Player player, List<Challenge> challenges, Map<String, Long> progressMap,
                                   MeritStorage.WeeklyOnlineTime onlineTime, int weekNumber) {
        player.sendMessage(Messages.PREFIX.append(
                Component.text("Weekly Challenges (Week " + weekNumber + ")").color(NamedTextColor.GOLD)));

        for (Challenge challenge : challenges) {
            long currentProgress = progressMap.getOrDefault(challenge.id(), 0L);
            boolean completed = meritManager.getProgressTracker().isChallengeCompleted(player.getUniqueId(), challenge.id());
            displayChallenge(player, challenge, currentProgress, completed);
        }

        // Online time bonus
        displayOnlineTimeBonus(player, onlineTime);

        // Summary - fetch and display
        meritManager.getStorage().getOrCreatePlayerMerit(player.getUniqueId())
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        merit -> {
                            LevelCalculator calc = meritManager.getLevelCalculator();
                            long toNext = calc.meritToNextLevel(merit.totalMerit());

                            player.sendMessage(Messages.PREFIX.append(
                                    Component.text("Level ", NamedTextColor.GRAY)
                                            .append(Component.text(merit.level(), Messages.getLevelColor(merit.level())))
                                            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                                            .append(Messages.formatMerit(merit.totalMerit()))
                                            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                                            .append(Component.text(Messages.formatNumber(toNext) + " to next", NamedTextColor.GRAY))));
                        },
                        err -> {}
                );
    }

    private void displayChallenge(Player player, Challenge challenge, long currentProgress, boolean completed) {
        Component progressBar = Messages.progressBar(currentProgress, challenge.target(), 10);
        NamedTextColor statusColor = completed ? NamedTextColor.GREEN : NamedTextColor.YELLOW;

        // Build hover tooltip with description and reward
        Component tooltip = Component.text(challenge.description(), NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text("+" + challenge.meritReward() + " Merit", NamedTextColor.LIGHT_PURPLE));

        String progressText = completed ? " \u2713" : ": " + currentProgress + "/" + challenge.target();

        Component line = Component.text(" ", NamedTextColor.GRAY)
                .append(progressBar)
                .append(Component.text(" "))
                .append(Component.text(challenge.name(), statusColor))
                .append(Component.text(progressText, completed ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(tooltip));

        player.sendMessage(line);
    }

    private void displayOnlineTimeBonus(Player player, MeritStorage.WeeklyOnlineTime onlineTime) {
        var config = meritManager.getConfig();
        int intervalMinutes = config.onlineTimeIntervalMinutes();
        int reward = config.onlineTimeReward();
        int cap = config.onlineTimeWeeklyCap();

        // Add current session time (not yet flushed to DB) to the database total
        long currentSessionSeconds = meritManager.getCurrentSessionSeconds(player.getUniqueId());
        long totalSeconds = onlineTime.secondsOnline() + currentSessionSeconds;

        long totalMinutes = totalSeconds / 60;
        long totalHours = totalMinutes / 60;
        long remainingMinutes = totalMinutes % 60;
        int earned = onlineTime.meritClaimed();

        Component progressBar = Messages.progressBar(earned, cap, 10);

        // Build hover tooltip
        Component tooltip = Component.text("Earn " + reward + " Merit every " + intervalMinutes + " minutes", NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text("Weekly cap: " + cap + " Merit", NamedTextColor.GRAY));

        Component line = Component.text(" ", NamedTextColor.GRAY)
                .append(progressBar)
                .append(Component.text(" Online Time", NamedTextColor.AQUA))
                .append(Component.text(": " + totalHours + "h " + remainingMinutes + "m", NamedTextColor.GRAY))
                .append(Component.text(" (+" + earned + "/" + cap + ")", NamedTextColor.LIGHT_PURPLE))
                .hoverEvent(HoverEvent.showText(tooltip));

        player.sendMessage(line);
    }

    private void showLeaderboard(Player player) {
        ChallengeAssigner assigner = meritManager.getAssigner();
        int weekNumber = assigner.getCurrentWeekNumber();

        meritManager.getStorage().getWeeklyLeaderboard(weekNumber, 10)
                .toList()
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        entries -> {
                            player.sendMessage(Component.empty());
                            player.sendMessage(Messages.PREFIX.append(
                                    Component.text("Weekly Leaderboard (Week " + weekNumber + ")").color(NamedTextColor.GOLD)));
                            player.sendMessage(Component.empty());

                            if (entries.isEmpty()) {
                                player.sendMessage(Component.text("  No merit earned this week yet!", NamedTextColor.GRAY));
                            } else {
                                for (int i = 0; i < entries.size(); i++) {
                                    var entry = entries.get(i);
                                    displayLeaderboardEntry(player, i + 1, entry);
                                }
                            }
                        },
                        err -> Messages.error(player, "Failed to load leaderboard: " + err.getMessage())
                );
    }

    private void displayLeaderboardEntry(Player player, int rank, MeritStorage.WeeklyLeaderboardEntry entry) {
        NamedTextColor rankColor = switch (rank) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.GRAY;
            case 3 -> NamedTextColor.GOLD;
            default -> NamedTextColor.WHITE;
        };

        String rankSymbol = switch (rank) {
            case 1 -> "\u2605 "; // star
            case 2 -> "\u2606 "; // white star
            case 3 -> "\u2606 "; // white star
            default -> "";
        };

        playerResolver.getDisplayName(entry.playerId())
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        name -> player.sendMessage(Component.text("  " + rankSymbol + rank + ". ", rankColor)
                                .append(Component.text(name, NamedTextColor.WHITE))
                                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                                .append(Messages.formatMerit(entry.weeklyMerit()))),
                        err -> player.sendMessage(Component.text("  " + rankSymbol + rank + ". ", rankColor)
                                .append(Component.text("Unknown", NamedTextColor.GRAY))
                                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                                .append(Messages.formatMerit(entry.weeklyMerit())))
                );
    }

    private void showStats(Player player) {
        meritManager.getStorage().getOrCreatePlayerMerit(player.getUniqueId())
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        merit -> {
                            LevelCalculator calc = meritManager.getLevelCalculator();

                            player.sendMessage(Component.empty());
                            player.sendMessage(Messages.PREFIX.append(
                                    Component.text("Your Stats").color(NamedTextColor.GOLD)));
                            player.sendMessage(Component.empty());

                            player.sendMessage(Component.text("  Level: ", NamedTextColor.GRAY)
                                    .append(Component.text(Messages.getMilestoneSymbol(merit.level()) + merit.level(),
                                            Messages.getLevelColor(merit.level()), TextDecoration.BOLD)));

                            player.sendMessage(Component.text("  Total Merit: ", NamedTextColor.GRAY)
                                    .append(Messages.formatMerit(merit.totalMerit())));

                            // Progress to next level
                            long currentLevelMerit = calc.meritForLevel(merit.level());
                            long nextLevelMerit = calc.meritForLevel(merit.level() + 1);
                            long progressInLevel = merit.totalMerit() - currentLevelMerit;
                            long neededForLevel = nextLevelMerit - currentLevelMerit;

                            player.sendMessage(Component.text("  Progress to Level " + (merit.level() + 1) + ": ", NamedTextColor.GRAY)
                                    .append(Messages.progressBar(progressInLevel, neededForLevel, 15)));

                            player.sendMessage(Component.text("    " + Messages.formatNumber(progressInLevel) +
                                    " / " + Messages.formatNumber(neededForLevel), NamedTextColor.DARK_GRAY));
                        },
                        err -> Messages.error(player, "Failed to load stats: " + err.getMessage())
                );
    }

    private void showHistory(Player player) {
        meritManager.getStorage().getCompletionHistory(player.getUniqueId(), 10)
                .toList()
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        completions -> {
                            player.sendMessage(Component.empty());
                            player.sendMessage(Messages.PREFIX.append(
                                    Component.text("Recent Completions").color(NamedTextColor.GOLD)));
                            player.sendMessage(Component.empty());

                            if (completions.isEmpty()) {
                                player.sendMessage(Component.text("  No challenges completed yet!", NamedTextColor.GRAY));
                            } else {
                                for (var completion : completions) {
                                    var challenge = meritManager.getRegistry().getById(completion.challengeId());
                                    String name = challenge.map(Challenge::name).orElse(completion.challengeId());

                                    player.sendMessage(Component.text("  \u2713 ", NamedTextColor.GREEN)
                                            .append(Component.text(name, NamedTextColor.WHITE))
                                            .append(Component.text(" (Week " + completion.weekNumber() + ")", NamedTextColor.DARK_GRAY))
                                            .append(Component.text(" +" + completion.meritEarned() + "M", NamedTextColor.LIGHT_PURPLE)));
                                }
                            }
                        },
                        err -> Messages.error(player, "Failed to load history: " + err.getMessage())
                );
    }

    private void showInfo(Player player) {
        player.sendMessage(Component.empty());
        player.sendMessage(Messages.PREFIX.append(
                Component.text("How Merit Works").color(NamedTextColor.GOLD)));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("  Merit is earned by completing weekly challenges.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Each week, you get 8 challenges from different categories.", NamedTextColor.GRAY));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("  Categories: ", NamedTextColor.GRAY)
                .append(Component.text("Mining, Farming, Building, PvP, PvE,", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("             Progression, Crafting, Smelting, Exploration, Time", NamedTextColor.WHITE));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("  Your level shows next to your name in chat.", NamedTextColor.GRAY));
        player.sendMessage(Component.text("  Higher levels unlock special symbols:", NamedTextColor.GRAY));
        player.sendMessage(Component.text("    Level 100+: ", NamedTextColor.GRAY)
                .append(Component.text("\u2605 Star", NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.text("    Level 250+: ", NamedTextColor.GRAY)
                .append(Component.text("\u2726 Four-pointed star", NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.text("    Level 500+: ", NamedTextColor.GRAY)
                .append(Component.text("\u272a Circled star", NamedTextColor.LIGHT_PURPLE)));
        player.sendMessage(Component.empty());

        player.sendMessage(Component.text("  Commands:", NamedTextColor.GRAY));
        player.sendMessage(Component.text("    /challenges", NamedTextColor.AQUA)
                .append(Component.text(" - View your weekly challenges", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("    /challenges leaderboard", NamedTextColor.AQUA)
                .append(Component.text(" - Weekly merit rankings", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("    /challenges stats", NamedTextColor.AQUA)
                .append(Component.text(" - Your total merit and level", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("    /challenges history", NamedTextColor.AQUA)
                .append(Component.text(" - Recent completions", NamedTextColor.GRAY)));
    }

    @Override
    public Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<AsyncTabCompleteEvent.Completion> completions = List.of(
                    AsyncTabCompleteEvent.Completion.completion("leaderboard"),
                    AsyncTabCompleteEvent.Completion.completion("stats"),
                    AsyncTabCompleteEvent.Completion.completion("history"),
                    AsyncTabCompleteEvent.Completion.completion("info")
            );
            return Maybe.just(completions);
        }
        return Maybe.empty();
    }
}
