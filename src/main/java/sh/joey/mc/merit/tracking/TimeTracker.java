package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.World;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Tracks in-game time events (sunrises, day survival, night survival).
 */
public final class TimeTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    // Track the last time state for each player (to detect transitions)
    private final Map<UUID, TimeState> playerTimeStates = new ConcurrentHashMap<>();

    // Time thresholds
    private static final long SUNRISE_START = 23000; // Just before dawn
    private static final long SUNRISE_END = 1000;    // After sunrise
    private static final long DAY_START = 1000;
    private static final long DAY_END = 12000;       // Before sunset
    private static final long NIGHT_START = 13000;
    private static final long NIGHT_END = 23000;

    private enum TimeState {
        SUNRISE, DAY, NIGHT, UNKNOWN
    }

    public TimeTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        // Check time every 5 seconds (100 ticks)
        disposables.add(plugin.interval(5, TimeUnit.SECONDS)
                .subscribe(tick -> {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        World world = player.getWorld();

                        // Only track in overworld
                        if (world.getEnvironment() != World.Environment.NORMAL) {
                            continue;
                        }

                        UUID playerId = player.getUniqueId();
                        long time = world.getTime();
                        TimeState currentState = getTimeState(time);
                        TimeState previousState = playerTimeStates.getOrDefault(playerId, TimeState.UNKNOWN);

                        // Detect sunrise
                        if (previousState != TimeState.SUNRISE && currentState == TimeState.SUNRISE) {
                            progressTracker.increment(playerId, "sunrises_witnessed");
                        }

                        // Detect surviving a full day (transitioned from day to sunset/night)
                        // Use UNKNOWN as intermediate since there's a gap between DAY_END and NIGHT_START
                        if (previousState == TimeState.DAY &&
                            (currentState == TimeState.UNKNOWN || currentState == TimeState.NIGHT)) {
                            progressTracker.increment(playerId, "days_survived");
                        }

                        // Detect surviving a full night (transitioned from night to sunrise/day)
                        if (previousState == TimeState.NIGHT &&
                            (currentState == TimeState.SUNRISE || currentState == TimeState.DAY)) {
                            progressTracker.increment(playerId, "nights_survived");
                        }

                        playerTimeStates.put(playerId, currentState);
                    }
                }));
    }

    private TimeState getTimeState(long time) {
        // Normalize time to 0-24000 range
        time = time % 24000;

        // Sunrise window (wraps around midnight)
        if (time >= SUNRISE_START || time < SUNRISE_END) {
            return TimeState.SUNRISE;
        }

        // Day
        if (time >= DAY_START && time < DAY_END) {
            return TimeState.DAY;
        }

        // Night
        if (time >= NIGHT_START && time < NIGHT_END) {
            return TimeState.NIGHT;
        }

        // Transition periods (sunset, etc.)
        return TimeState.UNKNOWN;
    }

    @Override
    public void dispose() {
        disposables.dispose();
        playerTimeStates.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
