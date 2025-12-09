package sh.joey.mc.session;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Tracks player session lifecycle: joins, disconnects, and periodic heartbeat.
 * Generates a unique server session ID per server run to identify sessions.
 */
public final class PlayerSessionTracker implements Disposable {

    private final UUID serverSessionId = UUID.randomUUID();
    private final CompositeDisposable disposables = new CompositeDisposable();

    public PlayerSessionTracker(SiqiJoeyPlugin plugin, PlayerSessionStorage storage) {

        // Fix orphaned sessions from previous server runs (blocking on startup)
        int fixed = storage.fixOrphanedSessions(serverSessionId).blockingGet();
        if (fixed > 0) {
            plugin.getLogger().info("Fixed " + fixed + " orphaned player session(s) from previous server run");
        }

        // Record joins
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
            .flatMapCompletable(event -> {
                Player player = event.getPlayer();
                return storage.recordJoin(
                        player.getUniqueId(),
                        player.getName(),
                        getRemoteIp(player),
                        isOnlineMode(),
                        serverSessionId)
                    .doOnError(err -> plugin.getLogger().warning(
                        "Failed to record player join for " + player.getName() + ": " + err.getMessage()))
                    .onErrorComplete();
            })
            .subscribe());

        // Record disconnects
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
            .flatMapCompletable(event -> {
                Player player = event.getPlayer();
                return storage.recordDisconnect(player.getUniqueId(), serverSessionId)
                    .doOnError(err -> plugin.getLogger().warning(
                        "Failed to record disconnect for " + player.getName() + ": " + err.getMessage()))
                    .onErrorComplete();
            })
            .subscribe());

        // Periodic heartbeat (every 30 seconds)
        disposables.add(plugin.interval(30, TimeUnit.SECONDS)
            .flatMapCompletable(tick -> storage.updateLastSeen(serverSessionId)
                .doOnError(err -> plugin.getLogger().warning("Failed to update last seen: " + err.getMessage()))
                .onErrorComplete())
            .subscribe());
    }

    private static String getRemoteIp(Player player) {
        InetSocketAddress address = player.getAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }

    private boolean isOnlineMode() {
        return Bukkit.getOnlineMode();
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
