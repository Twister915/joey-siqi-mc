package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.Detection;
import sh.joey.mc.anticheat.PlayerStateTracker;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class TimerCheck implements Check {

    private static final String NAME = "Timer";
    private static final int EXPECTED_MOVES_PER_SECOND = 20;
    private static final double TOLERANCE = 1.25;

    private final Observable<Detection> detections;

    public TimerCheck(SiqiJoeyPlugin plugin, PlayerStateTracker stateTracker) {
        this.detections = plugin.interval(1, TimeUnit.SECONDS)
                .flatMap(tick -> checkAllPlayers(plugin, stateTracker))
                .share();
    }

    private Observable<Detection> checkAllPlayers(SiqiJoeyPlugin plugin, PlayerStateTracker stateTracker) {
        return Observable.fromIterable(plugin.getServer().getOnlinePlayers())
                .filter(this::shouldCheck)
                .flatMap(player -> check(player, stateTracker));
    }

    private boolean shouldCheck(Player player) {
        GameMode mode = player.getGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    private Observable<Detection> check(Player player, PlayerStateTracker stateTracker) {
        PlayerStateTracker.PlayerState state = stateTracker.getState(player.getUniqueId());
        if (state == null) {
            return Observable.empty();
        }

        int moveCount = state.getMovePacketCount();
        long timeSinceReset = System.currentTimeMillis() - state.getLastMoveCountResetTime();

        // Reset for next second
        state.resetMovePacketCount();

        // Skip if not enough time has passed
        if (timeSinceReset < 900) { // Less than 0.9 seconds
            return Observable.empty();
        }

        // Normalize to per-second rate
        double movesPerSecond = (moveCount * 1000.0) / timeSinceReset;
        double maxAllowed = EXPECTED_MOVES_PER_SECOND * TOLERANCE;

        if (movesPerSecond > maxAllowed) {
            double ratio = movesPerSecond / EXPECTED_MOVES_PER_SECOND;
            double weight = Math.min(5.0, (ratio - 1.0) * 5);

            return Observable.just(new Detection(
                    player.getUniqueId(),
                    NAME,
                    weight,
                    player.getLocation(),
                    Map.of(
                            "movesPerSecond", movesPerSecond,
                            "expected", EXPECTED_MOVES_PER_SECOND,
                            "ratio", ratio
                    )
            ));
        }

        return Observable.empty();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Observable<Detection> detections() {
        return detections;
    }
}
