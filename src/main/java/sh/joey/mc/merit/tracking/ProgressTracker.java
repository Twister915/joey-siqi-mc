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

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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

    // In-memory delta tracking (unflushed changes)
    private final Map<UUID, Map<String, Long>> deltas = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    // Cumulative progress cache (flushed + unflushed, for online players)
    private final Map<UUID, Map<String, Long>> progressCache = new ConcurrentHashMap<>();

    // Cached weekly challenge progress (to detect milestones)
    private final Map<UUID, Map<String, Long>> weeklyProgress = new ConcurrentHashMap<>();

    // Cached player levels (for level-up detection)
    private final Map<UUID, Integer> playerLevels = new ConcurrentHashMap<>();

    // Track last notified milestone per player per challenge
    private final Map<UUID, Map<String, Integer>> lastNotifiedMilestone = new ConcurrentHashMap<>();

    // Track current week number to detect week transitions
    private volatile int currentWeekNumber;

    // Callback for level changes (to notify DisplayManager)
    @Nullable
    private Consumer<UUID> levelChangeCallback;

    public ProgressTracker(SiqiJoeyPlugin plugin, MeritStorage storage, MeritBossBarProvider bossBarProvider,
                           ChallengeAssigner assigner, LevelCalculator levelCalculator, MeritConfig config) {
        this.plugin = plugin;
        this.storage = storage;
        this.bossBarProvider = bossBarProvider;
        this.assigner = assigner;
        this.levelCalculator = levelCalculator;
        this.config = config;
        this.logger = plugin.getLogger();
        this.currentWeekNumber = assigner.getCurrentWeekNumber();

        // Periodic flush
        disposables.add(plugin.interval(config.flushIntervalSeconds(), TimeUnit.SECONDS)
                .subscribe(tick -> flushDirty()));

        // Check for week transition every minute
        disposables.add(plugin.interval(1, TimeUnit.MINUTES)
                .subscribe(tick -> checkWeekTransition()));

        // Flush on player quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> flushPlayer(event.getPlayer().getUniqueId())));
    }

    /**
     * Check if the week has changed and handle the transition.
     */
    private void checkWeekTransition() {
        int newWeekNumber = assigner.getCurrentWeekNumber();
        if (newWeekNumber == currentWeekNumber) {
            return;
        }

        logger.info("[Merit] Week transition detected: " + currentWeekNumber + " -> " + newWeekNumber);
        currentWeekNumber = newWeekNumber;

        // Flush all pending data to the OLD week before transitioning
        flushAll();

        // Broadcast to all online players
        Component message = Messages.PREFIX.append(
                Component.text("A new week has begun! Your weekly challenges have been reset.", NamedTextColor.GOLD));

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Send message and play sound
            player.sendMessage(Component.empty());
            player.sendMessage(message);
            player.sendMessage(Messages.PREFIX.append(
                    Component.text("Use ", NamedTextColor.GRAY)
                            .append(Component.text("/challenges", NamedTextColor.AQUA))
                            .append(Component.text(" to see your new challenges!", NamedTextColor.GRAY))));
            player.sendMessage(Component.empty());
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

            // Reload player data for the new week
            reloadPlayerForNewWeek(player.getUniqueId());
        }
    }

    /**
     * Reload a player's data for a new week.
     */
    private void reloadPlayerForNewWeek(UUID playerId) {
        // Clear old caches
        progressCache.remove(playerId);
        weeklyProgress.remove(playerId);
        lastNotifiedMilestone.remove(playerId);
        deltas.remove(playerId);
        dirty.remove(playerId);

        // Reload from database (will be empty for new week)
        loadPlayer(playerId);
    }

    /**
     * Increment a stat for a player.
     */
    public void increment(UUID playerId, String key, long amount) {
        // Track delta for DB flush
        deltas.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
              .merge(key, amount, Long::sum);
        dirty.add(playerId);

        // Update cumulative cache
        progressCache.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
              .merge(key, amount, Long::sum);

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

        // Load player merit/level and check for retroactive level-ups (in case curve changed)
        disposables.add(storage.getOrCreatePlayerMerit(playerId)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        merit -> handleMeritLoaded(playerId, merit),
                        err -> logger.warning("Failed to load merit for " + playerId + ": " + err.getMessage())
                ));

        // Load weekly progress from database (stats reset each week)
        disposables.add(storage.getProgress(playerId, weekNumber)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        progress -> {
                            Map<String, Long> cache = new ConcurrentHashMap<>(progress);
                            progressCache.put(playerId, cache);

                            // Initialize weeklyProgress from loaded data to prevent duplicate notifications
                            initializeWeeklyProgress(playerId);
                        },
                        err -> logger.warning("Failed to load weekly progress for " + playerId + ": " + err.getMessage())
                ));

        // Load weekly challenge completion status (for completion tracking)
        disposables.add(storage.getWeeklyChallengeProgress(playerId, weekNumber)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        progress -> {
                            // Track which challenges are already completed
                            Map<String, Integer> milestones = lastNotifiedMilestone.computeIfAbsent(playerId, k -> new HashMap<>());
                            for (var entry : progress.entrySet()) {
                                if (entry.getValue().completed()) {
                                    milestones.put(entry.getKey(), 100);
                                }
                            }
                        },
                        err -> logger.warning("Failed to load challenge completion status for " + playerId + ": " + err.getMessage())
                ));
    }

    /**
     * Handle loaded merit data and check for retroactive level-ups.
     * If the leveling curve changed, players may have earned additional levels.
     */
    private void handleMeritLoaded(UUID playerId, MeritStorage.PlayerMerit merit) {
        int storedLevel = merit.level();
        int calculatedLevel = levelCalculator.levelForMerit(merit.totalMerit());

        // Cache the correct level
        playerLevels.put(playerId, calculatedLevel);

        // Always notify level change on load so DisplayManager can show the level
        notifyLevelChange(playerId);

        // Check if player should have gained levels (curve may have changed)
        if (calculatedLevel > storedLevel) {
            // Update the stored level in the database
            disposables.add(storage.updateLevel(playerId, calculatedLevel)
                    .subscribe(
                            () -> {},
                            err -> logger.warning("Failed to update level for " + playerId + ": " + err.getMessage())
                    ));

            // Show level-up notification to the player
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                int levelsGained = calculatedLevel - storedLevel;
                if (levelsGained == 1) {
                    onLevelUp(player, calculatedLevel);
                } else {
                    // Multiple levels gained - show special message
                    onMultipleLevelUp(player, storedLevel, calculatedLevel);
                }
            }
        }
    }

    /**
     * Handle gaining multiple levels at once (e.g., from curve changes).
     */
    private void onMultipleLevelUp(Player player, int oldLevel, int newLevel) {
        // Boss bar notification
        bossBarProvider.showLevelUp(player.getUniqueId(), newLevel);

        // Chat messages
        player.sendMessage(Component.empty());
        player.sendMessage(Messages.PREFIX.append(
                Component.text("LEVEL UP!", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)));
        player.sendMessage(Messages.PREFIX.append(
                Component.text("You jumped from level ", NamedTextColor.GRAY)
                        .append(Component.text(oldLevel, NamedTextColor.WHITE))
                        .append(Component.text(" to level ", NamedTextColor.GRAY))
                        .append(Component.text(newLevel, Messages.getLevelColor(newLevel), TextDecoration.BOLD))
                        .append(Component.text("!", NamedTextColor.GRAY))));
        player.sendMessage(Component.empty());

        // Sound
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    /**
     * Initialize weeklyProgress from progressCache to prevent duplicate milestone notifications.
     * Called after loading progress from database.
     */
    private void initializeWeeklyProgress(UUID playerId) {
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);
        Map<String, Long> challengeProgress = new HashMap<>();

        for (Challenge challenge : challenges) {
            long progress = calculateChallengeProgress(playerId, challenge);
            challengeProgress.put(challenge.id(), progress);

            // Also initialize lastNotifiedMilestone based on current progress
            int percent = (int) (progress * 100 / challenge.target());
            int milestone = 0;
            if (percent >= 100) milestone = 100;
            else if (percent >= 50) milestone = 50;
            else if (percent >= 25) milestone = 25;
            else if (percent >= 10) milestone = 10;

            if (milestone > 0) {
                lastNotifiedMilestone.computeIfAbsent(playerId, k -> new HashMap<>())
                        .put(challenge.id(), milestone);
            }
        }

        weeklyProgress.put(playerId, challengeProgress);
    }

    /**
     * Unload a player's data.
     */
    public void unloadPlayer(UUID playerId) {
        flushPlayer(playerId);
        progressCache.remove(playerId);
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
     * Set a callback to be invoked when a player's level changes.
     * Used by MeritManager to notify DisplayManager.
     */
    public void setLevelChangeCallback(@Nullable Consumer<UUID> callback) {
        this.levelChangeCallback = callback;
    }

    /**
     * Notify that a player's level has changed.
     */
    private void notifyLevelChange(UUID playerId) {
        if (levelChangeCallback != null) {
            levelChangeCallback.accept(playerId);
        }
    }

    /**
     * Get current progress for a challenge (from in-memory tracking).
     */
    public long getChallengeProgress(UUID playerId, Challenge challenge) {
        return calculateChallengeProgress(playerId, challenge);
    }

    /**
     * Get all in-memory challenge progress for a player's weekly challenges.
     */
    public Map<String, Long> getAllChallengeProgress(UUID playerId) {
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);
        Map<String, Long> progress = new HashMap<>();
        for (Challenge challenge : challenges) {
            progress.put(challenge.id(), calculateChallengeProgress(playerId, challenge));
        }
        return progress;
    }

    /**
     * Check if a challenge has been completed this week.
     */
    public boolean isChallengeCompleted(UUID playerId, String challengeId) {
        Map<String, Long> playerProgress = weeklyProgress.get(playerId);
        if (playerProgress == null) return false;
        // Check if we recorded completion (milestone reached 100)
        Map<String, Integer> milestones = lastNotifiedMilestone.get(playerId);
        return milestones != null && milestones.getOrDefault(challengeId, 0) >= 100;
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
     * Uses progressCache which contains cumulative data (DB + unflushed).
     */
    private long calculateChallengeProgress(UUID playerId, Challenge challenge) {
        Map<String, Long> playerProgress = progressCache.getOrDefault(playerId, Map.of());
        long total = 0;

        for (String trackingKey : challenge.trackingKeys()) {
            // Check for special ANY key
            if (trackingKey.endsWith(":ANY")) {
                String prefix = trackingKey.substring(0, trackingKey.length() - 3);
                for (var entry : playerProgress.entrySet()) {
                    if (entry.getKey().startsWith(prefix)) {
                        total += entry.getValue();
                    }
                }
            } else {
                total += playerProgress.getOrDefault(trackingKey, 0L);
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
                            if (newLevel > oldLevel) {
                                notifyLevelChange(playerId);
                                if (player != null && player.isOnline()) {
                                    onLevelUp(player, newLevel);
                                }
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

        int weekNumber = assigner.getCurrentWeekNumber();

        // Write to database (fire and forget)
        disposables.add(storage.updateProgress(playerId, weekNumber, playerDeltas)
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
