package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks movement and exploration for exploration challenges.
 * Samples movement every second to reduce performance impact.
 */
public final class ExplorationTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();

    private static final long SAMPLE_INTERVAL_MS = 1000; // 1 second
    private static final double MIN_DISTANCE = 0.1; // Minimum distance to track
    // Max reasonable distance per sample (sprint-jumping with speed effects is ~10 blocks/sec)
    private static final double MAX_WALK_DISTANCE = 20.0;

    public ExplorationTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        disposables.add(plugin.watchEvent(PlayerMoveEvent.class)
                .subscribe(event -> {
                    Player player = event.getPlayer();
                    UUID playerId = player.getUniqueId();
                    long now = System.currentTimeMillis();

                    // Sample rate limit
                    Long lastUpdate = lastUpdateTime.get(playerId);
                    if (lastUpdate != null && now - lastUpdate < SAMPLE_INTERVAL_MS) {
                        return;
                    }

                    Location from = lastLocations.get(playerId);
                    Location to = player.getLocation();

                    if (from == null || !from.getWorld().equals(to.getWorld())) {
                        lastLocations.put(playerId, to);
                        lastUpdateTime.put(playerId, now);
                        return;
                    }

                    double distance = from.distance(to);
                    if (distance < MIN_DISTANCE) {
                        return;
                    }

                    lastLocations.put(playerId, to);
                    lastUpdateTime.put(playerId, now);

                    // Skip unreasonably large movements (likely teleport or other non-walking cause)
                    if (distance > MAX_WALK_DISTANCE) {
                        return;
                    }

                    // Determine movement type
                    String movementType = getMovementType(player, from, to);
                    long blocks = (long) distance;

                    if (blocks > 0) {
                        progressTracker.increment(playerId, "distance:" + movementType, blocks);
                    }
                }));

        // Reset last location on teleport to prevent counting teleport distance
        disposables.add(plugin.watchEvent(PlayerTeleportEvent.class)
                .subscribe(event -> {
                    UUID playerId = event.getPlayer().getUniqueId();
                    Location destination = event.getTo();
                    if (destination != null) {
                        lastLocations.put(playerId, destination);
                        lastUpdateTime.put(playerId, System.currentTimeMillis());
                    }
                }));
    }

    private String getMovementType(Player player, Location from, Location to) {
        // Check if player is in a vehicle
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            if (vehicle instanceof Boat) {
                return "BOAT";
            }
            if (vehicle instanceof Horse) {
                return "HORSE";
            }
            if (vehicle instanceof Minecart) {
                return "MINECART";
            }
            if (vehicle instanceof Pig) {
                return "PIG";
            }
        }

        // Check if gliding with elytra
        if (player.isGliding()) {
            return "ELYTRA";
        }

        // Check if swimming
        if (player.isSwimming()) {
            return "SWIM";
        }

        // Check if climbing (significant Y increase)
        double yDiff = to.getY() - from.getY();
        if (yDiff > 0.5) {
            return "CLIMB";
        }

        // Check if sprinting
        if (player.isSprinting()) {
            return "SPRINT";
        }

        // Default to walking
        return "WALK";
    }

    @Override
    public void dispose() {
        disposables.dispose();
        lastLocations.clear();
        lastUpdateTime.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
