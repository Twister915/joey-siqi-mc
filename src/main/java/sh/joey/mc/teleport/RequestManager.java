package sh.joey.mc.teleport;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages teleport requests between players.
 * Handles /tp requests and /yes /no responses.
 */
public final class RequestManager implements Disposable {
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final PluginConfig config;
    private final SafeTeleporter safeTeleporter;
    private final Map<UUID, TeleportRequest> pendingRequests = new HashMap<>();

    private record TeleportRequest(
            UUID requesterId,
            Disposable expiryTask
    ) {}

    public RequestManager(SiqiJoeyPlugin plugin, PluginConfig config, SafeTeleporter safeTeleporter) {
        this.plugin = plugin;
        this.config = config;
        this.safeTeleporter = safeTeleporter;

        // Player quit cleanup
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(this::handlePlayerQuit));
    }

    @Override
    public void dispose() {
        disposables.dispose();
        // Cancel all pending requests
        pendingRequests.values().forEach(req -> req.expiryTask().dispose());
        pendingRequests.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private void handlePlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        // Cancel any request TO this player
        cancelRequest(playerId, false);

        // Cancel any requests FROM this player
        pendingRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().requesterId().equals(playerId)) {
                entry.getValue().expiryTask().dispose();
                return true;
            }
            return false;
        });
    }

    /**
     * Sends a teleport request from one player to another.
     */
    public void sendRequest(Player requester, Player target) {
        UUID targetId = target.getUniqueId();
        UUID requesterId = requester.getUniqueId();

        // Cancel any existing request to this target
        cancelRequest(targetId, false);

        // Schedule expiry
        Disposable expiryTask = plugin.timer(config.requestTimeoutSeconds(), TimeUnit.SECONDS)
                .subscribe(tick -> {
                    if (pendingRequests.remove(targetId) != null) {
                        Messages.warning(requester, "Your teleport request to " + target.getName() + " expired.");
                        Messages.info(target, "Teleport request from " + requester.getName() + " expired.");
                    }
                });

        pendingRequests.put(targetId, new TeleportRequest(requesterId, expiryTask));

        Messages.success(requester, "Teleport request sent to " + target.getName() + "!");
        Messages.teleportRequest(target, requester);
    }

    /**
     * Accepts a pending teleport request.
     */
    public void acceptRequest(Player target) {
        UUID targetId = target.getUniqueId();
        TeleportRequest request = pendingRequests.remove(targetId);

        if (request == null) {
            Messages.error(target, "You don't have any pending teleport requests.");
            return;
        }

        request.expiryTask().dispose();

        Player requester = plugin.getServer().getPlayer(request.requesterId());
        if (requester == null || !requester.isOnline()) {
            Messages.error(target, "The player who requested is no longer online.");
            return;
        }

        Messages.success(target, "Accepted teleport request from " + requester.getName() + "!");
        Messages.success(requester, target.getName() + " accepted your teleport request!");

        // Use safe teleporter - requester teleports to target
        safeTeleporter.teleport(requester, target.getLocation(), success -> {
            // Callback if needed
        });
    }

    /**
     * Declines a pending teleport request.
     */
    public void declineRequest(Player target) {
        UUID targetId = target.getUniqueId();
        TeleportRequest request = pendingRequests.remove(targetId);

        if (request == null) {
            Messages.error(target, "You don't have any pending teleport requests.");
            return;
        }

        request.expiryTask().dispose();

        Player requester = plugin.getServer().getPlayer(request.requesterId());
        Messages.info(target, "Teleport request declined.");

        if (requester != null && requester.isOnline()) {
            Messages.warning(requester, target.getName() + " declined your teleport request.");
        }
    }

    /**
     * Gets the requester of a pending request to the given target, if any.
     */
    public Optional<Player> getPendingRequester(UUID targetId) {
        TeleportRequest request = pendingRequests.get(targetId);
        if (request == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plugin.getServer().getPlayer(request.requesterId()));
    }

    private void cancelRequest(UUID targetId, boolean notify) {
        TeleportRequest request = pendingRequests.remove(targetId);
        if (request != null) {
            request.expiryTask().dispose();
        }
    }
}
