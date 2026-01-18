package sh.joey.mc.anticheat;

import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.event.events.FlagEvent;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;

/**
 * Integrates with GrimAC to log its detections into our violation tracking system.
 * This class should only be instantiated after verifying GrimAC is installed.
 */
public final class GrimIntegration implements Disposable {

    private final SiqiJoeyPlugin plugin;
    private final ViolationTracker violationTracker;
    private boolean disposed = false;

    public GrimIntegration(SiqiJoeyPlugin plugin, ViolationTracker violationTracker) {
        this.plugin = plugin;
        this.violationTracker = violationTracker;

        plugin.getLogger().info("GrimAC found - enabling integration");

        // Subscribe to GrimAC's event bus asynchronously
        GrimAPIProvider.getAsync().thenAccept(this::subscribeToEvents);
    }

    private void subscribeToEvents(GrimAbstractAPI api) {
        if (disposed) return;

        api.getEventBus().subscribe(plugin, FlagEvent.class, event -> {
            if (disposed) return;

            var user = event.getUser();
            var check = event.getCheck();

            Player player = Bukkit.getPlayer(user.getUniqueId());
            if (player == null) {
                return;
            }

            var detection = new Detection(
                    user.getUniqueId(),
                    "grim:" + check.getCheckName(),
                    1.0,
                    player.getLocation(),
                    Map.of("grimViolations", check.getViolations()),
                    Detection.SOURCE_GRIM
            );

            violationTracker.flag(detection);
        });

        plugin.getLogger().info("GrimAC event bus subscription active");
    }

    @Override
    public void dispose() {
        disposed = true;
        // GrimAPI event bus automatically handles cleanup when the plugin is disabled
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
