package sh.joey.mc.confirm;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages pending confirmation requests for players.
 * Players can have multiple concurrent requests, each identified by a unique token.
 */
public final class ConfirmationManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    // Outer map: playerId -> (token -> request). LinkedHashMap preserves insertion order for getLatestToken().
    private final Map<UUID, LinkedHashMap<String, PendingRequest>> pending = new ConcurrentHashMap<>();

    private record PendingRequest(
        String token,
        ConfirmationRequest request,
        Disposable lifecycle
    ) {}

    public ConfirmationManager(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Automatically invalidate requests when the receiver disconnects
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
            .subscribe(event -> handleReceiverQuit(event.getPlayer().getUniqueId())));
    }

    private void handleReceiverQuit(UUID playerId) {
        LinkedHashMap<String, PendingRequest> playerRequests = pending.remove(playerId);
        if (playerRequests != null) {
            for (PendingRequest req : playerRequests.values()) {
                req.lifecycle().dispose();
                safeCall("onInvalidate", req.request()::onInvalidate);
            }
        }
    }

    /**
     * Sends a confirmation request to a player.
     * Multiple requests can coexist for the same player, each identified by a unique token.
     */
    public void request(Player player, ConfirmationRequest request) {
        UUID playerId = player.getUniqueId();
        String token = generateToken();

        // Build lifecycle observable (timeout + custom invalidation)
        // Note: Receiver quit is handled separately via PlayerQuitEvent subscription
        Completable timeout = plugin.timer(request.timeoutSeconds(), TimeUnit.SECONDS)
            .ignoreElements()
            .doOnComplete(() -> handleTimeout(playerId, token));

        Completable customInvalidation = request.invalidation()
            .doOnComplete(() -> handleInvalidate(playerId, token));

        // Race between timeout and custom invalidation (Completable.never() won't complete)
        Completable lifecycle = Completable.ambArray(timeout, customInvalidation);

        Disposable lifecycleSubscription = lifecycle.subscribe(
            () -> {},  // Completed (handled in doOnComplete)
            err -> plugin.getLogger().warning("Confirmation lifecycle error: " + err.getMessage())
        );

        pending.computeIfAbsent(playerId, k -> new LinkedHashMap<>())
            .put(token, new PendingRequest(token, request, lifecycleSubscription));

        // Send formatted message to player
        sendPrompt(player, request, token);
    }

    /**
     * Called by /accept command without a token. Accepts the most recent pending request.
     */
    public void accept(Player player) {
        UUID playerId = player.getUniqueId();
        String latestToken = getLatestToken(playerId);

        if (latestToken == null) {
            player.sendMessage(Component.text("You don't have anything to accept.")
                .color(NamedTextColor.RED));
            return;
        }

        accept(player, latestToken);
    }

    /**
     * Called by /accept command with a specific token.
     */
    public void accept(Player player, String token) {
        UUID playerId = player.getUniqueId();
        PendingRequest req = removeRequest(playerId, token);

        if (req == null) {
            player.sendMessage(Component.text("That request has expired.")
                .color(NamedTextColor.RED));
            return;
        }

        req.lifecycle().dispose();
        safeCall("onAccept", req.request()::onAccept);
    }

    /**
     * Called by /decline command without a token. Declines the most recent pending request.
     */
    public void decline(Player player) {
        UUID playerId = player.getUniqueId();
        String latestToken = getLatestToken(playerId);

        if (latestToken == null) {
            player.sendMessage(Component.text("You don't have anything to decline.")
                .color(NamedTextColor.RED));
            return;
        }

        decline(player, latestToken);
    }

    /**
     * Called by /decline command with a specific token.
     */
    public void decline(Player player, String token) {
        UUID playerId = player.getUniqueId();
        PendingRequest req = removeRequest(playerId, token);

        if (req == null) {
            player.sendMessage(Component.text("That request has expired.")
                .color(NamedTextColor.RED));
            return;
        }

        req.lifecycle().dispose();
        safeCall("onDecline", req.request()::onDecline);
    }

    /**
     * Returns true if the player has at least one pending request.
     */
    public boolean hasPending(UUID playerId) {
        LinkedHashMap<String, PendingRequest> playerRequests = pending.get(playerId);
        return playerRequests != null && !playerRequests.isEmpty();
    }

    private String generateToken() {
        String token;
        do {
            token = UUID.randomUUID().toString().substring(0, 8);
        } while (tokenExists(token));
        return token;
    }

    private boolean tokenExists(String token) {
        return pending.values().stream()
            .anyMatch(map -> map.containsKey(token));
    }

    private String getLatestToken(UUID playerId) {
        LinkedHashMap<String, PendingRequest> playerRequests = pending.get(playerId);
        if (playerRequests == null || playerRequests.isEmpty()) {
            return null;
        }
        // LinkedHashMap preserves insertion order; iterate to find the last key
        String latest = null;
        for (String t : playerRequests.keySet()) {
            latest = t;
        }
        return latest;
    }

    private PendingRequest removeRequest(UUID playerId, String token) {
        LinkedHashMap<String, PendingRequest> playerRequests = pending.get(playerId);
        if (playerRequests == null) {
            return null;
        }
        PendingRequest req = playerRequests.remove(token);
        if (playerRequests.isEmpty()) {
            pending.remove(playerId);
        }
        return req;
    }

    private void handleTimeout(UUID playerId, String token) {
        PendingRequest req = removeRequest(playerId, token);
        if (req != null) {
            req.lifecycle().dispose();
            safeCall("onTimeout", req.request()::onTimeout);
        }
    }

    private void handleInvalidate(UUID playerId, String token) {
        PendingRequest req = removeRequest(playerId, token);
        if (req != null) {
            req.lifecycle().dispose();
            safeCall("onInvalidate", req.request()::onInvalidate);
        }
    }

    /**
     * Safely invokes a callback, logging any exceptions without propagating them.
     */
    private void safeCall(String callbackName, Runnable callback) {
        try {
            callback.run();
        } catch (Exception e) {
            plugin.getLogger().warning("ConfirmationRequest." + callbackName + " threw exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendPrompt(Player player, ConfirmationRequest request, String token) {
        // Line 1: prefix + prompt text
        Component promptLine = request.prefix()
            .append(Component.text(request.promptText()).color(NamedTextColor.WHITE));

        // Line 2: buttons with token in click events
        Component acceptButton = Component.text("[" + request.acceptText() + "]")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/accept " + token))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to " + request.acceptText().toLowerCase())
                    .color(NamedTextColor.GREEN)));

        Component declineButton = Component.text("[" + request.declineText() + "]")
            .color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/decline " + token))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to " + request.declineText().toLowerCase())
                    .color(NamedTextColor.RED)));

        Component buttonLine = request.prefix()
            .append(acceptButton)
            .append(Component.text(" "))
            .append(declineButton);

        player.sendMessage(promptLine);
        player.sendMessage(buttonLine);
    }

    @Override
    public void dispose() {
        disposables.dispose();
        // Invalidate all pending requests
        for (LinkedHashMap<String, PendingRequest> playerRequests : pending.values()) {
            for (PendingRequest req : playerRequests.values()) {
                req.lifecycle().dispose();
                safeCall("onInvalidate", req.request()::onInvalidate);
            }
        }
        pending.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
