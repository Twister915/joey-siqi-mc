package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.Detection;
import sh.joey.mc.anticheat.PlayerStateTracker;

import java.util.Map;

public final class NoFallCheck implements Check {

    private static final String NAME = "NoFall";
    private static final float FREE_FALL_BLOCKS = 3.0f;
    private static final double DAMAGE_TOLERANCE = 0.5;

    private final Observable<Detection> detections;

    public NoFallCheck(SiqiJoeyPlugin plugin, PlayerStateTracker stateTracker) {
        this.detections = plugin.watchEvent(EntityDamageEvent.class)
                .filter(e -> e.getEntity() instanceof Player)
                .filter(e -> e.getCause() == EntityDamageEvent.DamageCause.FALL)
                .flatMap(e -> check(e, stateTracker))
                .share();
    }

    private Observable<Detection> check(EntityDamageEvent event, PlayerStateTracker stateTracker) {
        Player player = (Player) event.getEntity();

        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return Observable.empty();
        }

        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return Observable.empty();
        }

        PlayerStateTracker.PlayerState state = stateTracker.getState(player.getUniqueId());
        if (state == null) {
            return Observable.empty();
        }

        float trackedFallDistance = state.getTrackedFallDistance();
        float expectedDamage = Math.max(0, trackedFallDistance - FREE_FALL_BLOCKS);
        double actualDamage = event.getDamage();

        if (expectedDamage > 0 && actualDamage < expectedDamage * DAMAGE_TOLERANCE) {
            double weight = Math.min(5.0, (expectedDamage - actualDamage) * 0.5);

            return Observable.just(new Detection(
                    player.getUniqueId(),
                    NAME,
                    weight,
                    player.getLocation(),
                    Map.of(
                            "expectedDamage", expectedDamage,
                            "actualDamage", actualDamage,
                            "fallDistance", trackedFallDistance
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
