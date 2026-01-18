package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.Detection;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpeedCheck implements Check {

    private static final String NAME = "Speed";
    private static final double BASE_WALK_SPEED = 4.317;
    private static final double BASE_SPRINT_SPEED = 5.612;
    // High tolerance to account for b-hopping, momentum, and edge cases
    private static final double GROUND_TOLERANCE = 2.70;
    private static final double AIR_TOLERANCE = 3.00;

    // Track last movement time per player to calculate actual speed
    private final Map<UUID, Long> lastMoveTime = new ConcurrentHashMap<>();

    private final Observable<Detection> detections;

    public SpeedCheck(SiqiJoeyPlugin plugin) {
        // Clean up on player quit
        plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(e -> lastMoveTime.remove(e.getPlayer().getUniqueId()));

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
            // First movement, can't calculate speed yet
            return Observable.empty();
        }

        long deltaMs = now - lastTime;
        if (deltaMs <= 0) {
            // Same millisecond or clock issue, skip
            return Observable.empty();
        }

        // Skip if too much time passed (player was stationary, teleported, etc.)
        if (deltaMs > 1000) {
            return Observable.empty();
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        double horizontalDistance = Math.sqrt(
                Math.pow(to.getX() - from.getX(), 2) +
                Math.pow(to.getZ() - from.getZ(), 2)
        );

        // Calculate actual speed in blocks per second using real elapsed time
        double deltaSeconds = deltaMs / 1000.0;
        double speed = horizontalDistance / deltaSeconds;

        double maxSpeed = calculateMaxSpeed(player);

        // Use higher tolerance when airborne (jumping, falling) since movement is less predictable
        boolean onGround = player.isOnGround();
        double tolerance = onGround ? GROUND_TOLERANCE : AIR_TOLERANCE;

        if (speed > maxSpeed * tolerance) {
            double ratio = speed / maxSpeed;
            double weight = Math.min(5.0, (ratio - 1.0) * 10);

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
                            "deltaMs", deltaMs
                    )
            ));
        }

        return Observable.empty();
    }

    private double calculateMaxSpeed(Player player) {
        // Always use sprint speed as baseline - isSprinting() can flicker on landing
        // Players walking slowly won't trigger anyway since their speed is well below threshold
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
}
