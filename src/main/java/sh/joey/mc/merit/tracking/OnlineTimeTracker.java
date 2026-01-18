package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.merit.MeritConfig;
import sh.joey.mc.merit.MeritStorage;
import sh.joey.mc.merit.challenge.ChallengeAssigner;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Tracks online time and awards periodic merit.
 */
public final class OnlineTimeTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final MeritStorage storage;
    private final ProgressTracker progressTracker;
    private final ChallengeAssigner assigner;
    private final MeritConfig config;
    private final Logger logger;

    // Track when each player joined (for calculating session time)
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();

    // Track accumulated seconds since last flush
    private final Map<UUID, Long> sessionSeconds = new ConcurrentHashMap<>();

    public OnlineTimeTracker(SiqiJoeyPlugin plugin, MeritStorage storage, ProgressTracker progressTracker,
                             ChallengeAssigner assigner, MeritConfig config) {
        this.plugin = plugin;
        this.storage = storage;
        this.progressTracker = progressTracker;
        this.assigner = assigner;
        this.config = config;
        this.logger = plugin.getLogger();

        // Track joins
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> {
                    joinTimes.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
                }));

        // Track quits
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    UUID playerId = event.getPlayer().getUniqueId();
                    flushPlayerTime(playerId);
                    joinTimes.remove(playerId);
                    sessionSeconds.remove(playerId);
                }));

        // Check for merit awards periodically (every minute)
        disposables.add(plugin.interval(1, TimeUnit.MINUTES)
                .subscribe(tick -> checkMeritAwards()));

        // Initialize already online players
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            joinTimes.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Check and award merit for online time.
     */
    private void checkMeritAwards() {
        int weekNumber = assigner.getCurrentWeekNumber();
        long intervalSeconds = config.onlineTimeIntervalMinutes() * 60L;
        int reward = config.onlineTimeReward();
        int weeklyCap = config.onlineTimeWeeklyCap();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();

            // Update session time
            Long joinTime = joinTimes.get(playerId);
            if (joinTime != null) {
                long now = System.currentTimeMillis();
                long sessionSecs = sessionSeconds.getOrDefault(playerId, 0L);
                long additionalSecs = (now - joinTime) / 1000;

                // Reset join time to now (we've counted these seconds)
                joinTimes.put(playerId, now);
                sessionSecs += additionalSecs;
                sessionSeconds.put(playerId, sessionSecs);

                // Flush to database periodically
                if (sessionSecs >= 60) {
                    flushPlayerTime(playerId);
                }
            }

            // Check if player deserves merit
            checkAndAwardMerit(playerId, weekNumber, intervalSeconds, reward, weeklyCap);
        }
    }

    /**
     * Check if a player has earned merit from online time and award it.
     */
    private void checkAndAwardMerit(UUID playerId, int weekNumber, long intervalSeconds, int reward, int weeklyCap) {
        disposables.add(storage.getWeeklyOnlineTime(playerId, weekNumber)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        onlineTime -> {
                            long totalSeconds = onlineTime.secondsOnline();
                            int alreadyClaimed = onlineTime.meritClaimed();

                            // Calculate how many intervals completed
                            int intervalsCompleted = (int) (totalSeconds / intervalSeconds);
                            int meritEarnable = Math.min(intervalsCompleted * reward, weeklyCap);

                            if (meritEarnable > alreadyClaimed) {
                                int toAward = meritEarnable - alreadyClaimed;
                                progressTracker.awardMerit(playerId, toAward);

                                // Update claimed amount
                                disposables.add(storage.claimOnlineTimeMerit(playerId, weekNumber, meritEarnable)
                                        .subscribe(
                                                () -> {},
                                                err -> logger.warning("Failed to update online time merit: " + err.getMessage())
                                        ));

                                Player player = plugin.getServer().getPlayer(playerId);
                                if (player != null) {
                                    sh.joey.mc.merit.Messages.info(player,
                                            "+" + toAward + " Merit for playing!");
                                }
                            }
                        },
                        err -> logger.warning("Failed to check online time: " + err.getMessage())
                ));
    }

    /**
     * Get the current unflushed session time for a player in seconds.
     * This is time accumulated since the last flush.
     */
    public long getCurrentSessionSeconds(UUID playerId) {
        Long joinTime = joinTimes.get(playerId);
        if (joinTime == null) {
            return 0;
        }
        long accumulated = sessionSeconds.getOrDefault(playerId, 0L);
        long sinceJoin = (System.currentTimeMillis() - joinTime) / 1000;
        return accumulated + sinceJoin;
    }

    /**
     * Flush a player's accumulated session time to the database.
     */
    private void flushPlayerTime(UUID playerId) {
        Long sessionSecs = sessionSeconds.remove(playerId);
        if (sessionSecs == null || sessionSecs <= 0) {
            return;
        }

        int weekNumber = assigner.getCurrentWeekNumber();
        disposables.add(storage.updateWeeklyOnlineTime(playerId, weekNumber, sessionSecs)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to update online time: " + err.getMessage())
                ));
    }

    @Override
    public void dispose() {
        // Flush all remaining time
        for (UUID playerId : sessionSeconds.keySet()) {
            flushPlayerTime(playerId);
        }
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
