package sh.joey.mc.anticheat;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.check.Check;
import sh.joey.mc.anticheat.check.FlyCheck;
import sh.joey.mc.anticheat.check.NoFallCheck;
import sh.joey.mc.anticheat.check.ReachCheck;
import sh.joey.mc.anticheat.check.ScaffoldCheck;
import sh.joey.mc.anticheat.check.SpeedCheck;
import sh.joey.mc.anticheat.check.TimerCheck;
import sh.joey.mc.anticheat.cmd.AlertsCommand;
import sh.joey.mc.anticheat.cmd.ViolationsCommand;
import sh.joey.mc.cmd.CmdExecutor;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.storage.StorageService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class AntiCheatManager implements Disposable {

    private static final int DATA_RETENTION_DAYS = 30;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final List<Check> checks = new ArrayList<>();
    private final SiqiJoeyPlugin plugin;

    public AntiCheatManager(
            SiqiJoeyPlugin plugin,
            StorageService storageService,
            PlayerResolver playerResolver,
            UUID serverSessionId
    ) {
        this.plugin = plugin;

        // Create storage layer
        var storage = new CheatViolationStorage(storageService);

        // Create player state tracker
        var stateTracker = new PlayerStateTracker(plugin);
        disposables.add(stateTracker);

        // Create moderator notifier
        var notifier = new ModeratorNotifier(plugin);
        disposables.add(notifier);

        // Create violation tracker
        var violationTracker = new ViolationTracker(plugin, storage, notifier, serverSessionId);
        disposables.add(violationTracker);

        // Create checks
        // checks.add(new SpeedCheck(plugin));
        checks.add(new FlyCheck(plugin, stateTracker));
        checks.add(new NoFallCheck(plugin, stateTracker));
        checks.add(new ReachCheck(plugin, stateTracker));
        checks.add(new TimerCheck(plugin, stateTracker));
        checks.add(new ScaffoldCheck(plugin, stateTracker));

        // Merge all detection streams and forward to violation tracker
        Observable<Detection> allDetections = Observable.merge(
                checks.stream()
                        .map(Check::detections)
                        .toList()
        );

        disposables.add(allDetections.subscribe(
                violationTracker::flag,
                err -> plugin.getLogger().warning("Detection error: " + err.getMessage())
        ));

        // Dispatch player quit events to all checks for cleanup
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    var playerId = event.getPlayer().getUniqueId();
                    for (Check check : checks) {
                        check.onPlayerQuit(playerId);
                    }
                }));

        // GrimAC integration (soft dependency - check before loading class to avoid NoClassDefFoundError)
        if (plugin.getServer().getPluginManager().isPluginEnabled("GrimAC")) {
            var grimIntegration = new GrimIntegration(plugin, violationTracker);
            disposables.add(grimIntegration);
        } else {
            plugin.getLogger().info("GrimAC not found - integration disabled");
        }

        // Register commands
        disposables.add(CmdExecutor.register(plugin, new ViolationsCommand(plugin, storage, playerResolver)));
        disposables.add(CmdExecutor.register(plugin, new AlertsCommand(notifier)));

        // Schedule periodic cleanup of old violations
        disposables.add(plugin.interval(1, TimeUnit.HOURS)
                .flatMapCompletable(tick -> storage.deleteOldViolations(DATA_RETENTION_DAYS)
                        .doOnComplete(() -> plugin.getLogger().info("Cleaned up old violation records"))
                        .doOnError(err -> plugin.getLogger().warning("Failed to clean up violations: " + err.getMessage()))
                        .onErrorComplete())
                .subscribe());

        plugin.getLogger().info("Anti-cheat system enabled with " + checks.size() + " checks");
    }

    public List<Check> getChecks() {
        return List.copyOf(checks);
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
