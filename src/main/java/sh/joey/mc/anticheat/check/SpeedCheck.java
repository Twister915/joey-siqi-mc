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

import java.util.Map;

public final class SpeedCheck implements Check {

    private static final String NAME = "Speed";
    private static final double BASE_WALK_SPEED = 4.317;
    private static final double BASE_SPRINT_SPEED = 5.612;
    private static final double TOLERANCE = 1.15;

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
        Location from = event.getFrom();
        Location to = event.getTo();

        double horizontalDistance = Math.sqrt(
                Math.pow(to.getX() - from.getX(), 2) +
                Math.pow(to.getZ() - from.getZ(), 2)
        );

        double speed = horizontalDistance * 20;
        double maxSpeed = calculateMaxSpeed(player);

        if (speed > maxSpeed * TOLERANCE) {
            double ratio = speed / maxSpeed;
            double weight = Math.min(5.0, (ratio - 1.0) * 10);

            return Observable.just(new Detection(
                    player.getUniqueId(),
                    NAME,
                    weight,
                    player.getLocation(),
                    Map.of(
                            "speed", speed,
                            "maxSpeed", maxSpeed,
                            "ratio", ratio
                    )
            ));
        }

        return Observable.empty();
    }

    private double calculateMaxSpeed(Player player) {
        double baseSpeed = player.isSprinting() ? BASE_SPRINT_SPEED : BASE_WALK_SPEED;

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
