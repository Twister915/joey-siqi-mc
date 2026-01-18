package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.Detection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpeedCheck implements Check {

    private static final String NAME = "Speed";
    private static final double BASE_WALK_SPEED = 4.317;
    private static final double BASE_SPRINT_SPEED = 5.612;
    // Tolerance before counting as a violation
    private static final double GROUND_TOLERANCE = 1.50;
    private static final double AIR_TOLERANCE = 1.80;

    // Debouncing: require sustained violations over time window
    private static final long WINDOW_MS = 8000; // 8 seconds
    private static final double AVERAGE_THRESHOLD = 1.0; // Average weight needed to flag

    // Track last movement time per player to calculate actual speed
    private final Map<UUID, Long> lastMoveTime = new ConcurrentHashMap<>();
    // Track violations over time window for debouncing
    private final Map<UUID, Deque<ViolationSample>> violationWindows = new ConcurrentHashMap<>();

    private final Observable<Detection> detections;

    public SpeedCheck(SiqiJoeyPlugin plugin) {
        this.detections = plugin.watchEvent(EventPriority.MONITOR, PlayerMoveEvent.class)
                .filter(e -> !e.isCancelled())
                .filter(e -> shouldCheck(e.getPlayer()))
                .filter(this::hasMovedHorizontally)
                .flatMap(this::check)
                .share();
    }

    private boolean shouldCheck(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return false;
        }
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
            return false;
        }
        if (player.isRiptiding()) {
            return false;
        }
        return true;
    }

    private boolean hasMovedHorizontally(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return from.getX() != to.getX() || from.getZ() != to.getZ();
    }

    private Observable<Detection> check(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Get time since last movement
        Long lastTime = lastMoveTime.put(playerId, now);
        if (lastTime == null) {
            return Observable.empty();
        }

        long deltaMs = now - lastTime;
        if (deltaMs <= 0 || deltaMs > 1000) {
            return Observable.empty();
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        double horizontalDistance = Math.sqrt(
                Math.pow(to.getX() - from.getX(), 2) +
                Math.pow(to.getZ() - from.getZ(), 2)
        );

        double deltaSeconds = deltaMs / 1000.0;
        double speed = horizontalDistance / deltaSeconds;
        double maxSpeed = calculateMaxSpeed(player);

        boolean onGround = player.isOnGround();
        double tolerance = onGround ? GROUND_TOLERANCE : AIR_TOLERANCE;

        // Get or create violation window for this player
        Deque<ViolationSample> window = violationWindows.computeIfAbsent(playerId, k -> new ArrayDeque<>());

        // Prune old samples outside the time window
        while (!window.isEmpty() && (now - window.peekFirst().timestamp) > WINDOW_MS) {
            window.pollFirst();
        }

        // Check if this movement is a violation
        if (speed > maxSpeed * tolerance) {
            double ratio = speed / maxSpeed;
            double weight = Math.min(5.0, (ratio - 1.0) * 10);

            // Add to window
            window.addLast(new ViolationSample(now, weight));

            // Calculate average weight over the window
            double totalWeight = 0;
            for (ViolationSample sample : window) {
                totalWeight += sample.weight;
            }
            double avgWeight = totalWeight / (WINDOW_MS / 1000.0); // Weight per second

            // Only flag if average exceeds threshold
            if (avgWeight >= AVERAGE_THRESHOLD) {
                return Observable.just(new Detection(
                        playerId,
                        NAME,
                        weight,
                        player.getLocation(),
                        Map.of(
                                "speed", speed,
                                "maxSpeed", maxSpeed,
                                "ratio", ratio,
                                "onGround", onGround,
                                "avgWeight", avgWeight,
                                "samplesInWindow", window.size()
                        )
                ));
            }
        }

        return Observable.empty();
    }

    private double calculateMaxSpeed(Player player) {
        // Always use sprint speed as baseline - isSprinting() can flicker on landing
        double baseSpeed = BASE_SPRINT_SPEED;

        var speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            baseSpeed *= 1.0 + (0.2 * (speedEffect.getAmplifier() + 1));
        }

        var slownessEffect = player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slownessEffect != null) {
            baseSpeed *= 1.0 - (0.15 * (slownessEffect.getAmplifier() + 1));
        }

        Block below = player.getLocation().subtract(0, 0.5, 0).getBlock();
        Material belowType = below.getType();
        if (belowType == Material.SOUL_SAND || belowType == Material.SOUL_SOIL) {
            var boots = player.getInventory().getBoots();
            if (boots != null && boots.containsEnchantment(org.bukkit.enchantments.Enchantment.SOUL_SPEED)) {
                int level = boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SOUL_SPEED);
                baseSpeed *= 1.0 + (0.03 * level);
            }
        }

        if (belowType == Material.ICE || belowType == Material.PACKED_ICE || belowType == Material.BLUE_ICE) {
            baseSpeed *= 1.4;
        }

        var dolphinsGrace = player.getPotionEffect(PotionEffectType.DOLPHINS_GRACE);
        if (dolphinsGrace != null && player.isInWater()) {
            baseSpeed *= 2.0;
        }

        if (player.isInWater() && !player.isSwimming()) {
            baseSpeed *= 0.5;
        }

        if (player.isSneaking()) {
            baseSpeed *= 0.3;
        }

        return baseSpeed;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Observable<Detection> detections() {
        return detections;
    }

    @Override
    public void onPlayerQuit(UUID playerId) {
        lastMoveTime.remove(playerId);
        violationWindows.remove(playerId);
    }

    private record ViolationSample(long timestamp, double weight) {}
}
