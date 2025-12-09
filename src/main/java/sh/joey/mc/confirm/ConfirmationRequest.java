package sh.joey.mc.confirm;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;

/**
 * Represents a confirmation request shown to a player.
 * Implement this interface to create custom confirmation prompts.
 */
public interface ConfirmationRequest {

    // --- Display ---

    /**
     * Prefix component shown before the prompt (e.g., [TP], [Home]).
     * Default: no prefix.
     */
    default Component prefix() {
        return Component.empty();
    }

    /**
     * The prompt message text shown to the player.
     */
    String promptText();

    /**
     * Text shown on the accept button.
     * Default: "Accept"
     */
    default String acceptText() {
        return "Accept";
    }

    /**
     * Text shown on the decline button.
     * Default: "Decline"
     */
    default String declineText() {
        return "Decline";
    }

    // --- Callbacks ---

    /**
     * Called when the player clicks accept.
     */
    void onAccept();

    /**
     * Called when the player clicks decline.
     * Default: no-op.
     */
    default void onDecline() {}

    /**
     * Called when the request times out.
     * Default: no-op.
     */
    default void onTimeout() {}

    /**
     * Called when the request is invalidated (e.g., a player quit).
     * Default: no-op.
     */
    default void onInvalidate() {}

    /**
     * Called when the request is replaced by a new request.
     * This happens when a new confirmation is sent to the same player
     * before they respond to this one.
     * Default: no-op.
     */
    default void onReplaced() {}

    // --- Lifecycle ---

    /**
     * A Completable that, when it completes, invalidates this request.
     * Use this to cancel the request when external conditions change,
     * such as the request sender (not receiver) disconnecting.
     *
     * Note: The ConfirmationManager automatically tracks if the request
     * receiver disconnects - you don't need to handle that case here.
     *
     * Example for teleport requests (invalidate if requester quits):
     * <pre>
     * return plugin.watchEvent(PlayerQuitEvent.class)
     *     .filter(e -> e.getPlayer().getUniqueId().equals(requesterId))
     *     .take(1)
     *     .ignoreElements();
     * </pre>
     *
     * Default: Completable.never() (only timeout or receiver quit ends the request).
     */
    default Completable invalidation() {
        return Completable.never();
    }

    /**
     * How long the request remains valid, in seconds.
     * Default: 60 seconds.
     */
    default int timeoutSeconds() {
        return 60;
    }
}
