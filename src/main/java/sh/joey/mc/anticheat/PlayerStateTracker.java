package sh.joey.mc.anticheat;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStateTracker implements Disposable {

    private static final int MAX_HISTORY_SIZE = 40; // 2 seconds at 20 TPS

    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final CompositeDisposable disposables = new CompositeDisposable();

    public PlayerStateTracker(SiqiJoeyPlugin plugin) {
        // Update state on move
        disposables.add(plugin.watchEvent(PlayerMoveEvent.class)
                .subscribe(this::handleMove));

        // Track damage for fall distance reset
        disposables.add(plugin.watchEvent(EntityDamageEvent.class)
                .filter(e -> e.getEntity() instanceof Player)
                .filter(e -> e.getCause() == EntityDamageEvent.DamageCause.FALL)
                .subscribe(e -> {
                    Player player = (Player) e.getEntity();
                    PlayerState state = states.get(player.getUniqueId());
                    if (state != null) {
                        state.resetFallDistance();
                    }
                }));

        // Clean up on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(e -> states.remove(e.getPlayer().getUniqueId())));
    }

    private void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        PlayerState state = states.computeIfAbsent(
                player.getUniqueId(),
                k -> new PlayerState()
        );

        state.update(from, to, player.isOnGround(), player.getFallDistance());
    }

    public PlayerState getState(UUID playerId) {
        return states.get(playerId);
    }

    public PlayerState getOrCreateState(UUID playerId) {
        return states.computeIfAbsent(playerId, k -> new PlayerState());
    }

    public void recordAttack(UUID playerId, long timestamp) {
        PlayerState state = getOrCreateState(playerId);
        state.recordAttack(timestamp);
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    public static final class PlayerState {
        private final Deque<TimestampedLocation> locationHistory = new ArrayDeque<>();
        private final Deque<Long> attackTimestamps = new ArrayDeque<>();
        private int airTicks = 0;
        private int groundTicks = 0;
        private float trackedFallDistance = 0;
        private long lastMoveTime = 0;
        private int movePacketCount = 0;
        private long lastMoveCountReset = System.currentTimeMillis();
        private Location lastLocation = null;

        public synchronized void update(Location from, Location to, boolean onGround, float fallDistance) {
            long now = System.currentTimeMillis();

            // Track location history
            locationHistory.addLast(new TimestampedLocation(to.clone(), now));
            while (locationHistory.size() > MAX_HISTORY_SIZE) {
                locationHistory.removeFirst();
            }

            // Track air/ground ticks
            if (onGround) {
                groundTicks++;
                airTicks = 0;
            } else {
                airTicks++;
                groundTicks = 0;
            }

            // Track fall distance
            if (fallDistance > trackedFallDistance) {
                trackedFallDistance = fallDistance;
            }

            // Track move packet rate
            movePacketCount++;
            lastMoveTime = now;
            lastLocation = to.clone();
        }

        public synchronized void resetFallDistance() {
            trackedFallDistance = 0;
        }

        public synchronized void recordAttack(long timestamp) {
            attackTimestamps.addLast(timestamp);
            while (attackTimestamps.size() > 20) {
                attackTimestamps.removeFirst();
            }
        }

        public synchronized int getAirTicks() {
            return airTicks;
        }

        public synchronized int getGroundTicks() {
            return groundTicks;
        }

        public synchronized float getTrackedFallDistance() {
            return trackedFallDistance;
        }

        public synchronized int getMovePacketCount() {
            return movePacketCount;
        }

        public synchronized void resetMovePacketCount() {
            movePacketCount = 0;
            lastMoveCountReset = System.currentTimeMillis();
        }

        public synchronized long getLastMoveCountResetTime() {
            return lastMoveCountReset;
        }

        public synchronized Location getLastLocation() {
            return lastLocation != null ? lastLocation.clone() : null;
        }

        public synchronized Location getHistoricalLocation(int ticksAgo) {
            if (locationHistory.isEmpty()) return null;

            int index = Math.max(0, locationHistory.size() - 1 - ticksAgo);
            int i = 0;
            for (TimestampedLocation loc : locationHistory) {
                if (i == index) {
                    return loc.location().clone();
                }
                i++;
            }
            return null;
        }

        public synchronized Location getLocationAtTime(long targetTime) {
            TimestampedLocation closest = null;
            long closestDiff = Long.MAX_VALUE;

            for (TimestampedLocation loc : locationHistory) {
                long diff = Math.abs(loc.timestamp() - targetTime);
                if (diff < closestDiff) {
                    closestDiff = diff;
                    closest = loc;
                }
            }

            return closest != null ? closest.location().clone() : null;
        }
    }

    public record TimestampedLocation(Location location, long timestamp) {}
}
