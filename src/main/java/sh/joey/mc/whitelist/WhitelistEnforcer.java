package sh.joey.mc.whitelist;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces the custom whitelist by:
 * <ul>
 *   <li>Blocking non-whitelisted players from logging in</li>
 *   <li>Broadcasting join attempts to players with invite permission</li>
 * </ul>
 */
public final class WhitelistEnforcer implements Disposable {

    private static final String INVITE_PERMISSION = "smp.invite";
    private static final int MAX_BROADCASTS = 5;
    private static final Duration RATE_WINDOW = Duration.ofMinutes(5);

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final WhitelistStorage storage;
    private final WhitelistConfig config;
    private final Map<UUID, Deque<Instant>> recentBroadcasts = new ConcurrentHashMap<>();

    public WhitelistEnforcer(SiqiJoeyPlugin plugin, WhitelistStorage storage, WhitelistConfig config) {
        this.plugin = plugin;
        this.storage = storage;
        this.config = config;

        if (!config.enabled()) {
            plugin.getLogger().info("Custom whitelist is disabled (whitelist.enabled=false)");
            return;
        }

        // Disable vanilla whitelist
        if (Bukkit.hasWhitelist()) {
            plugin.getLogger().info("Disabling vanilla whitelist (custom whitelist is active)");
            Bukkit.setWhitelist(false);
        }

        // Check whitelist on login (async event, safe to block)
        disposables.add(plugin.watchEvent(AsyncPlayerPreLoginEvent.class)
                .subscribe(this::checkWhitelistOnLogin));
    }

    /**
     * Check if player is whitelisted when they attempt to login.
     * This runs on an async thread, so blocking database queries are safe.
     */
    private void checkWhitelistOnLogin(AsyncPlayerPreLoginEvent event) {
        // Skip if already denied by another system (e.g., ban check)
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID playerId = event.getUniqueId();
        String playerName = event.getName();

        try {
            Boolean whitelisted = storage.isWhitelisted(playerId).blockingGet();
            if (whitelisted != null && whitelisted) {
                return; // Player is whitelisted, allow login
            }

            // Not whitelisted - deny login
            Component kickMessage = WhitelistMessages.formatKickMessage(playerName);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kickMessage);

            // Broadcast to players with invite permission (rate limited)
            if (shouldBroadcast(playerId)) {
                scheduleBroadcast(playerName);
            }
        } catch (Exception e) {
            // Fail open - allow login if database error
            plugin.getLogger().warning("Error checking whitelist for " + playerName + ": " + e.getMessage());
        }
    }

    /**
     * Check if we should broadcast for this player (rate limiting).
     */
    private boolean shouldBroadcast(UUID playerId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(RATE_WINDOW);

        Deque<Instant> times = recentBroadcasts.computeIfAbsent(playerId, k -> new LinkedList<>());

        // Remove expired entries
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst().isBefore(cutoff)) {
                times.pollFirst();
            }

            // Check if under limit
            if (times.size() < MAX_BROADCASTS) {
                times.addLast(now);
                return true;
            }
        }
        return false;
    }

    /**
     * Schedule broadcast to run on main thread.
     */
    private void scheduleBroadcast(String playerName) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Component message = WhitelistMessages.formatJoinAttemptBroadcast(playerName);

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission(INVITE_PERMISSION)) {
                    player.sendMessage(message);
                }
            }
        });
    }

    @Override
    public void dispose() {
        disposables.dispose();
        recentBroadcasts.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
