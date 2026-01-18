package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.merit.LevelCalculator;
import sh.joey.mc.merit.MeritBossBarProvider;
import sh.joey.mc.merit.MeritConfig;
import sh.joey.mc.merit.MeritStorage;
import sh.joey.mc.merit.Messages;
import sh.joey.mc.merit.challenge.Challenge;
import sh.joey.mc.merit.challenge.ChallengeAssigner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Central coordinator for tracking player progress and managing batch writes.
 * Tracks stat deltas in memory and flushes them periodically to the database.
 */
public final class ProgressTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final MeritStorage storage;
    private final MeritBossBarProvider bossBarProvider;
    private final ChallengeAssigner assigner;
    private final LevelCalculator levelCalculator;
    private final MeritConfig config;
    private final Logger logger;

    // In-memory delta tracking
    private final Map<UUID, Map<String, Long>> deltas = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    // Cached weekly challenge progress (to detect milestones)
    private final Map<UUID, Map<String, Long>> weeklyProgress = new ConcurrentHashMap<>();

    // Cached player levels (for level-up detection)
    private final Map<UUID, Integer> playerLevels = new ConcurrentHashMap<>();

    // Track last notified milestone per player per challenge
    private final Map<UUID, Map<String, Integer>> lastNotifiedMilestone = new ConcurrentHashMap<>();

    public ProgressTracker(SiqiJoeyPlugin plugin, MeritStorage storage, MeritBossBarProvider bossBarProvider,
                           ChallengeAssigner assigner, LevelCalculator levelCalculator, MeritConfig config) {
        this.plugin = plugin;
        this.storage = storage;
        this.bossBarProvider = bossBarProvider;
        this.assigner = assigner;
        this.levelCalculator = levelCalculator;
        this.config = config;
        this.logger = plugin.getLogger();

        // Periodic flush
        disposables.add(plugin.interval(config.flushIntervalSeconds(), TimeUnit.SECONDS)
                .subscribe(tick -> flushDirty()));

        // Flush on player quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> flushPlayer(event.getPlayer().getUniqueId())));
    }

    /**
     * Increment a stat for a player.
     */
    public void increment(UUID playerId, String key, long amount) {
        deltas.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
              .merge(key, amount, Long::sum);
        dirty.add(playerId);

        // Check challenge progress on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> checkChallengeProgress(playerId, key, amount));
    }

    /**
     * Increment a stat by 1.
     */
    public void increment(UUID playerId, String key) {
        increment(playerId, key, 1);
    }

    /**
     * Load a player's data when they join.
     */
    public void loadPlayer(UUID playerId) {
        int weekNumber = assigner.getCurrentWeekNumber();

        // Load player merit/level
        disposables.add(storage.getOrCreatePlayerMerit(playerId)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        merit -> playerLevels.put(playerId, merit.level()),
                        err -> logger.warning("Failed to load merit for " + playerId + ": " + err.getMessage())
                ));

        // Load weekly challenge progress
        disposables.add(storage.getWeeklyChallengeProgress(playerId, weekNumber)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        progress -> {
                            Map<String, Long> progressMap = new HashMap<>();
                            for (var entry : progress.entrySet()) {
                                progressMap.put(entry.getKey(), entry.getValue().progress());
                            }
                            weeklyProgress.put(playerId, progressMap);
                        },
                        err -> logger.warning("Failed to load weekly progress for " + playerId + ": " + err.getMessage())
                ));
    }

    /**
     * Unload a player's data.
     */
    public void unloadPlayer(UUID playerId) {
        flushPlayer(playerId);
        weeklyProgress.remove(playerId);
        playerLevels.remove(playerId);
        lastNotifiedMilestone.remove(playerId);
    }

    /**
     * Get cached level for a player.
     */
    public int getCachedLevel(UUID playerId) {
        return playerLevels.getOrDefault(playerId, 1);
    }

    /**
     * Check if a stat update triggers any challenge progress.
     */
    private void checkChallengeProgress(UUID playerId, String key, long delta) {
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);
        int weekNumber = assigner.getCurrentWeekNumber();

        for (Challenge challenge : challenges) {
            if (!challenge.trackingKeys().contains(key)) {
                continue;
            }

            // Calculate total progress for this challenge
            long totalProgress = calculateChallengeProgress(playerId, challenge);

            // Get previous progress
            Map<String, Long> playerProgress = weeklyProgress.computeIfAbsent(playerId, k -> new HashMap<>());
            long previousProgress = playerProgress.getOrDefault(challenge.id(), 0L);

            // Update cached progress
            playerProgress.put(challenge.id(), totalProgress);

            // Check for milestone notifications
            checkMilestoneNotification(playerId, challenge, previousProgress, totalProgress);

            // Check for completion
            if (totalProgress >= challenge.target() && previousProgress < challenge.target()) {
                onChallengeComplete(playerId, challenge, weekNumber);
            }
        }
    }

    /**
     * Calculate total progress for a challenge across all its tracking keys.
     */
    private long calculateChallengeProgress(UUID playerId, Challenge challenge) {
        Map<String, Long> playerDeltas = deltas.getOrDefault(playerId, Map.of());
        long total = 0;

        for (String trackingKey : challenge.trackingKeys()) {
            // Check for special ANY key
            if (trackingKey.endsWith(":ANY")) {
                String prefix = trackingKey.substring(0, trackingKey.length() - 3);
                for (var entry : playerDeltas.entrySet()) {
                    if (entry.getKey().startsWith(prefix)) {
                        total += entry.getValue();
                    }
                }
            } else {
                total += playerDeltas.getOrDefault(trackingKey, 0L);
            }
        }

        return total;
    }

    /**
     * Check if we should show a milestone notification.
     */
    private void checkMilestoneNotification(UUID playerId, Challenge challenge, long previousProgress, long currentProgress) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;

        long target = challenge.target();
        int previousPercent = (int) (previousProgress * 100 / target);
        int currentPercent = (int) (currentProgress * 100 / target);

        // Milestones: 10%, 25%, 50%, 100%
        int[] milestones = {10, 25, 50, 100};

        Map<String, Integer> playerMilestones = lastNotifiedMilestone.computeIfAbsent(playerId, k -> new HashMap<>());
        int lastMilestone = playerMilestones.getOrDefault(challenge.id(), 0);

        for (int milestone : milestones) {
            if (previousPercent < milestone && currentPercent >= milestone && milestone > lastMilestone) {
                // Show notification
                bossBarProvider.showProgress(playerId, challenge.name(), milestone);

                // Play sound
                if (milestone == 100) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
                } else if (milestone == 50) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                }

                playerMilestones.put(challenge.id(), milestone);
                break; // Only one notification per update
            }
        }
    }

    /**
     * Handle challenge completion.
     */
    private void onChallengeComplete(UUID playerId, Challenge challenge, int weekNumber) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;

        int meritReward = challenge.meritReward();

        // Award merit
        awardMerit(playerId, meritReward);

        // Record completion
        disposables.add(storage.completeWeeklyChallenge(playerId, weekNumber, challenge.id(), challenge.target())
                .andThen(storage.recordCompletion(playerId, challenge.id(), weekNumber, meritReward))
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to record completion: " + err.getMessage())
                ));

        // Send chat messages
        Messages.success(player, "Challenge Complete: " + challenge.name());
        Messages.info(player, "+" + meritReward + " Merit earned!");

        // Play completion sound
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    /**
     * Award merit to a player and check for level up.
     */
    public void awardMerit(UUID playerId, long amount) {
        Player player = plugin.getServer().getPlayer(playerId);
        int oldLevel = playerLevels.getOrDefault(playerId, 1);

        disposables.add(storage.getOrCreatePlayerMerit(playerId)
                .flatMap(merit -> {
                    long newTotal = merit.totalMerit() + amount;
                    int newLevel = levelCalculator.levelForMerit(newTotal);
                    return storage.addMerit(playerId, amount, newLevel)
                            .toSingleDefault(newLevel);
                })
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        newLevel -> {
                            playerLevels.put(playerId, newLevel);
                            if (newLevel > oldLevel && player != null && player.isOnline()) {
                                onLevelUp(player, newLevel);
                            }
                        },
                        err -> logger.warning("Failed to award merit: " + err.getMessage())
                ));
    }

    /**
     * Handle level up.
     */
    private void onLevelUp(Player player, int newLevel) {
        UUID playerId = player.getUniqueId();

        // Boss bar notification
        bossBarProvider.showLevelUp(playerId, newLevel);

        // Chat messages
        player.sendMessage(Component.empty());
        player.sendMessage(Messages.PREFIX.append(
                Component.text("LEVEL UP!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)));
        player.sendMessage(Messages.PREFIX.append(
                Component.text("You are now level ", NamedTextColor.GRAY)
                        .append(Component.text(newLevel, Messages.getLevelColor(newLevel), TextDecoration.BOLD))
                        .append(Component.text("!", NamedTextColor.GRAY))));
        player.sendMessage(Component.empty());

        // Sound
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    /**
     * Flush all dirty players to the database.
     */
    private void flushDirty() {
        for (UUID playerId : dirty) {
            flushPlayer(playerId);
        }
    }

    /**
     * Flush a specific player's data to the database.
     */
    private void flushPlayer(UUID playerId) {
        Map<String, Long> playerDeltas = deltas.remove(playerId);
        dirty.remove(playerId);

        if (playerDeltas == null || playerDeltas.isEmpty()) {
            return;
        }

        // Write to database (fire and forget)
        disposables.add(storage.updateProgress(playerId, playerDeltas)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to flush progress for " + playerId + ": " + err.getMessage())
                ));
    }

    /**
     * Flush all data immediately (for shutdown).
     */
    public void flushAll() {
        for (UUID playerId : deltas.keySet()) {
            flushPlayer(playerId);
        }
    }

    @Override
    public void dispose() {
        flushAll();
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
