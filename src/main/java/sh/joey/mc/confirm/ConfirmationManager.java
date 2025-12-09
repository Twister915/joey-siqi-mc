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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages pending confirmation requests for players.
 * Each player can have at most one pending request at a time.
 */
public final class ConfirmationManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final Map<UUID, PendingRequest> pending = new HashMap<>();

    private record PendingRequest(
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
        PendingRequest req = pending.remove(playerId);
        if (req != null) {
            req.lifecycle().dispose();
            safeCall("onInvalidate", req.request()::onInvalidate);
        }
    }

    /**
     * Sends a confirmation request to a player.
     * Any existing pending request for this player is replaced.
     */
    public void request(Player player, ConfirmationRequest request) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing request (triggers onReplaced)
        replace(playerId);

        // Build lifecycle observable (timeout + custom invalidation)
        // Note: Receiver quit is handled separately via PlayerQuitEvent subscription
        Completable timeout = plugin.timer(request.timeoutSeconds(), TimeUnit.SECONDS)
            .ignoreElements()
            .doOnComplete(() -> handleTimeout(playerId));

        Completable customInvalidation = request.invalidation()
            .doOnComplete(() -> handleInvalidate(playerId));

        // Race between timeout and custom invalidation (Completable.never() won't complete)
        Completable lifecycle = Completable.ambArray(timeout, customInvalidation);

        Disposable lifecycleSubscription = lifecycle.subscribe(
            () -> {},  // Completed (handled in doOnComplete)
            err -> plugin.getLogger().warning("Confirmation lifecycle error: " + err.getMessage())
        );

        pending.put(playerId, new PendingRequest(request, lifecycleSubscription));

        // Send formatted message to player
        sendPrompt(player, request);
    }

    /**
     * Called by /accept command.
     */
    public void accept(Player player) {
        UUID playerId = player.getUniqueId();
        PendingRequest req = pending.remove(playerId);

        if (req == null) {
            player.sendMessage(Component.text("You don't have anything to accept.")
                .color(NamedTextColor.RED));
            return;
        }

        req.lifecycle().dispose();
        safeCall("onAccept", req.request()::onAccept);
    }

    /**
     * Called by /decline command.
     */
    public void decline(Player player) {
        UUID playerId = player.getUniqueId();
        PendingRequest req = pending.remove(playerId);

        if (req == null) {
            player.sendMessage(Component.text("You don't have anything to decline.")
                .color(NamedTextColor.RED));
            return;
        }

        req.lifecycle().dispose();
        safeCall("onDecline", req.request()::onDecline);
    }

    /**
     * Returns true if the player has a pending request.
     */
    public boolean hasPending(UUID playerId) {
        return pending.containsKey(playerId);
    }

    private void handleTimeout(UUID playerId) {
        PendingRequest req = pending.remove(playerId);
        if (req != null) {
            req.lifecycle().dispose();
            safeCall("onTimeout", req.request()::onTimeout);
        }
    }

    private void handleInvalidate(UUID playerId) {
        PendingRequest req = pending.remove(playerId);
        if (req != null) {
            req.lifecycle().dispose();
            safeCall("onInvalidate", req.request()::onInvalidate);
        }
    }

    private void replace(UUID playerId) {
        PendingRequest req = pending.remove(playerId);
        if (req != null) {
            req.lifecycle().dispose();
            safeCall("onReplaced", req.request()::onReplaced);
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

    private void sendPrompt(Player player, ConfirmationRequest request) {
        // Line 1: prefix + prompt text
        Component promptLine = request.prefix()
            .append(Component.text(request.promptText()).color(NamedTextColor.WHITE));

        // Line 2: buttons
        Component acceptButton = Component.text("[" + request.acceptText() + "]")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/accept"))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to " + request.acceptText().toLowerCase())
                    .color(NamedTextColor.GREEN)));

        Component declineButton = Component.text("[" + request.declineText() + "]")
            .color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/decline"))
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
        pending.values().forEach(req -> {
            req.lifecycle().dispose();
            safeCall("onInvalidate", req.request()::onInvalidate);
        });
        pending.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
