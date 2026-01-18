package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.Detection;
import sh.joey.mc.anticheat.PlayerStateTracker;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ScaffoldCheck implements Check {

    private static final String NAME = "Scaffold";
    private static final int SUSPICIOUS_BRIDGE_SPEED = 8;
    private static final double MIN_LOOK_DOWN_PITCH = 70.0;

    private final Observable<Detection> detections;
    private final Map<UUID, BridgeState> bridgeStates = new ConcurrentHashMap<>();

    public ScaffoldCheck(SiqiJoeyPlugin plugin, PlayerStateTracker stateTracker) {
        this.detections = plugin.watchEvent(BlockPlaceEvent.class)
                .filter(e -> !e.isCancelled())
                .filter(e -> shouldCheck(e.getPlayer()))
                .flatMap(e -> check(e, stateTracker))
                .share();
    }

    private boolean shouldCheck(Player player) {
        GameMode mode = player.getGameMode();
        return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
    }

    private Observable<Detection> check(BlockPlaceEvent event, PlayerStateTracker stateTracker) {
        Player player = event.getPlayer();
        Block placed = event.getBlockPlaced();
        Location playerLoc = player.getLocation();

        // Check if block is placed below the player's feet
        int playerBlockY = playerLoc.getBlockY();
        int placedY = placed.getY();

        if (placedY != playerBlockY - 1) {
            // Not bridging
            bridgeStates.remove(player.getUniqueId());
            return Observable.empty();
        }

        BridgeState state = bridgeStates.computeIfAbsent(
                player.getUniqueId(),
                k -> new BridgeState()
        );

        long now = System.currentTimeMillis();

        // Check if player is moving horizontally
        PlayerStateTracker.PlayerState playerState = stateTracker.getState(player.getUniqueId());
        boolean isMoving = false;
        if (playerState != null) {
            Location lastLoc = playerState.getLastLocation();
            if (lastLoc != null) {
                double horizontalDist = Math.sqrt(
                        Math.pow(playerLoc.getX() - lastLoc.getX(), 2) +
                        Math.pow(playerLoc.getZ() - lastLoc.getZ(), 2)
                );
                isMoving = horizontalDist > 0.05;
            }
        }

        // Check if player is looking down enough
        boolean lookingDown = player.getLocation().getPitch() >= MIN_LOOK_DOWN_PITCH;

        // Update bridge state
        if (now - state.lastBridgeTime < 1000) {
            state.consecutiveBlocks++;
        } else {
            state.consecutiveBlocks = 1;
        }
        state.lastBridgeTime = now;

        // Suspicious: bridging fast while moving and NOT looking down
        if (isMoving && !lookingDown && state.consecutiveBlocks >= SUSPICIOUS_BRIDGE_SPEED) {
            double weight = Math.min(10.0, state.consecutiveBlocks * 0.5);

            return Observable.just(new Detection(
                    player.getUniqueId(),
                    NAME,
                    weight,
                    player.getLocation(),
                    Map.of(
                            "consecutiveBlocks", state.consecutiveBlocks,
                            "pitch", player.getLocation().getPitch(),
                            "isMoving", isMoving
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

    private static class BridgeState {
        long lastBridgeTime = 0;
        int consecutiveBlocks = 0;
    }
}
