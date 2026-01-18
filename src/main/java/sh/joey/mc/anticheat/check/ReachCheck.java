package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.Detection;
import sh.joey.mc.anticheat.PlayerStateTracker;

import java.util.Map;

public final class ReachCheck implements Check {

    private static final String NAME = "Reach";
    private static final double MAX_REACH = 3.0;
    private static final double LAG_TOLERANCE = 0.5;
    private static final double EFFECTIVE_MAX_REACH = MAX_REACH + LAG_TOLERANCE;

    private final Observable<Detection> detections;
    private final PlayerStateTracker stateTracker;

    public ReachCheck(SiqiJoeyPlugin plugin, PlayerStateTracker stateTracker) {
        this.stateTracker = stateTracker;

        this.detections = plugin.watchEvent(EntityDamageByEntityEvent.class)
                .filter(e -> e.getDamager() instanceof Player)
                .filter(e -> e.getEntity() instanceof LivingEntity)
                .filter(e -> !(e.getEntity() instanceof Player &&
                              ((Player) e.getEntity()).getGameMode() == GameMode.CREATIVE))
                .flatMap(this::check)
                .share();
    }

    private Observable<Detection> check(EntityDamageByEntityEvent event) {
        Player attacker = (Player) event.getDamager();
        LivingEntity victim = (LivingEntity) event.getEntity();

        GameMode mode = attacker.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return Observable.empty();
        }

        Location attackerEye = attacker.getEyeLocation();

        Location victimLocation = getCompensatedLocation(attacker, victim);
        if (victimLocation == null) {
            victimLocation = victim.getLocation();
        }

        Location victimCenter = victimLocation.clone().add(0, victim.getHeight() / 2, 0);
        double distance = attackerEye.distance(victimCenter);

        double minDistance = getDistanceToHitbox(attackerEye, victimLocation, victim);
        double effectiveDistance = Math.min(distance, minDistance);

        if (effectiveDistance > EFFECTIVE_MAX_REACH) {
            double weight = Math.min(5.0, (effectiveDistance - MAX_REACH) * 2);

            return Observable.just(new Detection(
                    attacker.getUniqueId(),
                    NAME,
                    weight,
                    attacker.getLocation(),
                    Map.of(
                            "distance", effectiveDistance,
                            "maxReach", MAX_REACH,
                            "victimType", victim.getType().name()
                    )
            ));
        }

        return Observable.empty();
    }

    private Location getCompensatedLocation(Player attacker, LivingEntity victim) {
        int ping = attacker.getPing();
        int ticksAgo = Math.min(20, (ping * 20) / 1000);

        if (victim instanceof Player victimPlayer) {
            PlayerStateTracker.PlayerState state = stateTracker.getState(victimPlayer.getUniqueId());
            if (state != null) {
                Location historical = state.getHistoricalLocation(ticksAgo);
                if (historical != null) {
                    return historical;
                }
            }
        }

        return null;
    }

    private double getDistanceToHitbox(Location point, Location entityLocation, LivingEntity entity) {
        double halfWidth = entity.getWidth() / 2;
        double height = entity.getHeight();

        double closestX = clamp(point.getX(),
                entityLocation.getX() - halfWidth,
                entityLocation.getX() + halfWidth);
        double closestY = clamp(point.getY(),
                entityLocation.getY(),
                entityLocation.getY() + height);
        double closestZ = clamp(point.getZ(),
                entityLocation.getZ() - halfWidth,
                entityLocation.getZ() + halfWidth);

        return point.distance(new Location(
                entityLocation.getWorld(),
                closestX, closestY, closestZ
        ));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
