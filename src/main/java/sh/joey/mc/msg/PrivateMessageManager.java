package sh.joey.mc.msg;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.player.PlayerResolver;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages private messaging between players.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Send messages to online players</li>
 *   <li>Queue messages for offline players</li>
 *   <li>Deliver queued messages on join</li>
 *   <li>Track last sender for /reply</li>
 *   <li>Enforce max queued messages limit</li>
 * </ul>
 */
public final class PrivateMessageManager implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("MSG").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final PrivateMessageStorage storage;
    private final MessageConfig config;
    private final PlayerResolver playerResolver;

    // Track who last messaged each player (for /reply)
    private final Map<UUID, UUID> lastSender = new ConcurrentHashMap<>();

    public PrivateMessageManager(
            SiqiJoeyPlugin plugin,
            PrivateMessageStorage storage,
            MessageConfig config,
            PlayerResolver playerResolver
    ) {
        this.plugin = plugin;
        this.storage = storage;
        this.config = config;
        this.playerResolver = playerResolver;

        // Deliver queued messages on join (with delay)
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .delay(config.queuedDeliveryDelaySeconds(), TimeUnit.SECONDS, plugin.mainScheduler())
                .subscribe(event -> deliverQueuedMessages(event.getPlayer())));

        // Clean up on player quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> lastSender.remove(event.getPlayer().getUniqueId())));
    }

    /**
     * Send a private message from one player to another.
     * If the recipient is online, delivers immediately. Otherwise, queues it.
     *
     * @param sender     the sending player
     * @param recipientId the recipient's UUID
     * @param content    the message content
     * @return Completable that completes when the message is sent/queued
     */
    public Completable sendMessage(Player sender, UUID recipientId, String content) {
        UUID senderId = sender.getUniqueId();
        Player recipient = Bukkit.getPlayer(recipientId);

        if (recipient != null && recipient.isOnline()) {
            // Deliver immediately
            return deliverMessage(sender, recipient, content);
        } else {
            // Queue for later delivery
            return queueMessage(sender, recipientId, content);
        }
    }

    /**
     * Get the UUID of the last player who sent a message to this player.
     *
     * @param playerId the player to check
     * @return the last sender's UUID, or empty if none
     */
    public Optional<UUID> getLastSender(UUID playerId) {
        return Optional.ofNullable(lastSender.get(playerId));
    }

    /**
     * Deliver a message to an online recipient.
     */
    private Completable deliverMessage(Player sender, Player recipient, String content) {
        UUID senderId = sender.getUniqueId();
        UUID recipientId = recipient.getUniqueId();

        String senderDisplayName = getDisplayName(sender);
        String recipientDisplayName = getDisplayName(recipient);

        // Update last sender for /reply
        lastSender.put(recipientId, senderId);

        // Format and send messages
        Component toSenderMsg = formatOutgoingMessage(recipientDisplayName, content);
        Component toRecipientMsg = formatIncomingMessage(senderDisplayName, content);

        sender.sendMessage(toSenderMsg);
        recipient.sendMessage(toRecipientMsg);

        return Completable.complete();
    }

    /**
     * Queue a message for an offline recipient.
     */
    private Completable queueMessage(Player sender, UUID recipientId, String content) {
        UUID senderId = sender.getUniqueId();

        return playerResolver.getUsername(recipientId)
                .defaultIfEmpty("Unknown")
                .flatMapCompletable(recipientName ->
                        storage.countPendingFromSender(senderId, recipientId)
                                .observeOn(plugin.mainScheduler())
                                .flatMapCompletable(count -> {
                                    if (count >= config.maxQueuedPerSender()) {
                                        sender.sendMessage(PREFIX.append(
                                                Component.text("You've reached the limit of queued messages for this player.")
                                                        .color(NamedTextColor.RED)));
                                        return Completable.complete();
                                    }

                                    return storage.storeMessage(senderId, recipientId, content)
                                            .observeOn(plugin.mainScheduler())
                                            .doOnComplete(() -> {
                                                // Show confirmation to sender
                                                Component msg = Component.text("To ", NamedTextColor.GRAY)
                                                        .append(Component.text(recipientName, NamedTextColor.WHITE))
                                                        .append(Component.text(" (offline)", NamedTextColor.YELLOW))
                                                        .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                                                        .append(Component.text(content, NamedTextColor.WHITE));
                                                sender.sendMessage(msg);
                                            });
                                }));
    }

    /**
     * Deliver queued messages to a player who just joined.
     */
    private void deliverQueuedMessages(Player player) {
        if (!player.isOnline()) {
            return;
        }

        UUID playerId = player.getUniqueId();

        disposables.add(storage.getUnreadMessages(playerId)
                .toList()
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        messages -> displayQueuedMessages(player, messages),
                        err -> plugin.getLogger().warning("Failed to deliver queued messages to " + player.getName() + ": " + err.getMessage())
                ));
    }

    private void displayQueuedMessages(Player player, List<PrivateMessage> messages) {
        if (messages.isEmpty() || !player.isOnline()) {
            return;
        }

        // Show header
        player.sendMessage(PREFIX.append(
                Component.text("You have " + messages.size() + " unread message" + (messages.size() == 1 ? "" : "s") + ":")
                        .color(NamedTextColor.YELLOW)));

        // Show each message
        for (PrivateMessage msg : messages) {
            // Update last sender for /reply (use the most recent message's sender)
            lastSender.put(player.getUniqueId(), msg.senderId());

            String senderName = resolveName(msg.senderId());
            String relativeTime = formatRelativeTime(msg.createdAt());

            Component msgComponent = Component.text(senderName, NamedTextColor.WHITE)
                    .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(msg.content(), NamedTextColor.WHITE))
                    .append(Component.text(" (" + relativeTime + ")", NamedTextColor.GRAY))
                    .append(Component.text("  "))
                    .append(replyButton(senderName));

            player.sendMessage(msgComponent);

            // Mark as read
            disposables.add(storage.markAsRead(msg.id()).subscribe());
        }
    }

    /**
     * Format a message being sent TO someone (shown to sender).
     */
    private Component formatOutgoingMessage(String recipientName, String content) {
        return Component.text("To ", NamedTextColor.GRAY)
                .append(Component.text(recipientName, NamedTextColor.WHITE))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(content, NamedTextColor.WHITE));
    }

    /**
     * Format a message being received FROM someone (shown to recipient).
     */
    private Component formatIncomingMessage(String senderName, String content) {
        return Component.text(senderName, NamedTextColor.WHITE)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(content, NamedTextColor.WHITE))
                .append(Component.text("  "))
                .append(replyButton(senderName));
    }

    /**
     * Create a clickable [Reply] button that suggests /t <playerName>.
     */
    private Component replyButton(String playerName) {
        return Component.text("[Reply]")
                .color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand("/t " + playerName + " "))
                .hoverEvent(HoverEvent.showText(Component.text("Click to reply", NamedTextColor.GRAY)));
    }

    /**
     * Get a player's display name (nickname if set, otherwise username).
     */
    private String getDisplayName(Player player) {
        return playerResolver.getDisplayName(player);
    }

    /**
     * Resolve a UUID to a display name synchronously.
     * Used for displaying queued messages where we need the name immediately.
     */
    private String resolveName(UUID playerId) {
        // Try to get display name - this checks nickname cache and online players synchronously
        return playerResolver.getDisplayName(playerId)
                .blockingGet("Unknown");
    }

    /**
     * Format a timestamp as relative time (e.g., "5 minutes ago").
     */
    private String formatRelativeTime(Instant timestamp) {
        Duration duration = Duration.between(timestamp, Instant.now());
        long seconds = duration.toSeconds();

        if (seconds < 60) {
            return "just now";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else {
            long days = seconds / 86400;
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        }
    }

    @Override
    public void dispose() {
        disposables.dispose();
        lastSender.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
