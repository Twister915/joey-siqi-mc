package sh.joey.mc.anticheat.check;

import io.reactivex.rxjava3.core.Observable;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.Detection;
import sh.joey.mc.anticheat.PlayerStateTracker;

import java.util.Map;

public final class FlyCheck implements Check {

    private static final String NAME = "Fly";
    private static final int MAX_AIR_TICKS = 40;

    private final Observable<Detection> detections;

    public FlyCheck(SiqiJoeyPlugin plugin, PlayerStateTracker stateTracker) {
        this.detections = plugin.watchEvent(EventPriority.MONITOR, PlayerMoveEvent.class)
                .filter(e -> !e.isCancelled())
                .filter(e -> shouldCheck(e.getPlayer()))
                .flatMap(e -> check(e, stateTracker))
                .share();
    }

    private boolean shouldCheck(Player player) {
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return false;
        }
        return !hasValidFlightSource(player);
    }

    private boolean hasValidFlightSource(Player player) {
        if (player.getAllowFlight() || player.isFlying()) {
            return true;
        }
        if (player.isGliding()) {
            return true;
        }
        if (player.isRiptiding()) {
            return true;
        }
        if (player.isInsideVehicle()) {
            return true;
        }
        if (player.hasPotionEffect(PotionEffectType.LEVITATION)) {
            return true;
        }
        if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            return true;
        }
        if (player.isSwimming() || player.isInWater()) {
            return true;
        }
        if (player.isInLava()) {
            return true;
        }
        if (player.isClimbing()) {
            return true;
        }
        return false;
    }

    private Observable<Detection> check(PlayerMoveEvent event, PlayerStateTracker stateTracker) {
        Player player = event.getPlayer();
        PlayerStateTracker.PlayerState state = stateTracker.getState(player.getUniqueId());

        if (state == null) {
            return Observable.empty();
        }

        int airTicks = state.getAirTicks();

        if (airTicks > MAX_AIR_TICKS) {
            double weight = Math.min(10.0, (airTicks - MAX_AIR_TICKS) * 0.2);

            return Observable.just(new Detection(
                    player.getUniqueId(),
                    NAME,
                    weight,
                    player.getLocation(),
                    Map.of(
                            "airTicks", airTicks,
                            "velocity_y", player.getVelocity().getY()
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
