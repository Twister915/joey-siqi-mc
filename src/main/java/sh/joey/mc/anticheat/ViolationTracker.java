package sh.joey.mc.anticheat;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ViolationTracker implements Disposable {

    private static final double DECAY_RATE = 0.10;
    private static final double ALERT_THRESHOLD = 10.0;
    private static final double MIN_VL = 0.01;

    private final Map<UUID, Map<String, Double>> violationLevels = new ConcurrentHashMap<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final CheatViolationStorage storage;
    private final ModeratorNotifier notifier;
    private final UUID serverSessionId;

    public ViolationTracker(
            SiqiJoeyPlugin plugin,
            CheatViolationStorage storage,
            ModeratorNotifier notifier,
            UUID serverSessionId
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.notifier = notifier;
        this.serverSessionId = serverSessionId;

        // Decay VL every second
        disposables.add(plugin.interval(1, TimeUnit.SECONDS)
                .subscribe(tick -> decayAll()));

        // Clean up on player quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> violationLevels.remove(event.getPlayer().getUniqueId())));
    }

    public void flag(Detection detection) {
        double current = getVL(detection.playerId(), detection.checkName());
        double newVL = current + detection.weight();
        setVL(detection.playerId(), detection.checkName(), newVL);

        // Log to database
        storage.recordViolation(
                        detection.playerId(),
                        serverSessionId,
                        detection.checkName(),
                        detection.weight(),
                        newVL,
                        ViolationLocation.from(detection.location()),
                        detection.data(),
                        detection.source()
                )
                .subscribe(
                        () -> {},
                        err -> plugin.getLogger().warning("Failed to record violation: " + err.getMessage())
                );

        // Alert moderators if threshold exceeded
        if (newVL >= ALERT_THRESHOLD) {
            notifier.alert(detection.playerId(), detection.checkName(), newVL, detection.data());
        }
    }

    public double getVL(UUID playerId, String checkName) {
        var playerVLs = violationLevels.get(playerId);
        if (playerVLs == null) return 0.0;
        return playerVLs.getOrDefault(checkName, 0.0);
    }

    private void setVL(UUID playerId, String checkName, double vl) {
        violationLevels
                .computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(checkName, vl);
    }

    private void decayAll() {
        violationLevels.forEach((playerId, checkVLs) -> {
            checkVLs.replaceAll((check, vl) -> {
                double newVL = vl * (1.0 - DECAY_RATE);
                return newVL < MIN_VL ? 0.0 : newVL;
            });

            checkVLs.values().removeIf(vl -> vl <= 0.0);
        });

        violationLevels.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public double getAlertThreshold() {
        return ALERT_THRESHOLD;
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
