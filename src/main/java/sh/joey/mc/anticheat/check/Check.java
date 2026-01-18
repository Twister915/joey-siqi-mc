package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import sh.joey.mc.anticheat.Detection;

import java.util.UUID;

public interface Check {

    String getName();

    Observable<Detection> detections();

    /**
     * Called when a player quits to clean up any player-specific state.
     * Default implementation does nothing.
     */
    default void onPlayerQuit(UUID playerId) {
        // No-op by default
    }
}
