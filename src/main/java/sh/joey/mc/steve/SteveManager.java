package sh.joey.mc.steve;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.permissions.DisplayManager;
import sh.joey.mc.steve.SteveAnswer.Citation;
import sh.joey.mc.steve.SteveModel.ConversationTurn;
import sh.joey.mc.steve.provider.RateLimitException;
import sh.joey.mc.util.DurationFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Manages Steve AI chatbot interactions.
 * Listens for @Steve mentions in chat and responds with researched answers.
 */
public final class SteveManager implements Disposable {

    private static final Pattern STEVE_MENTION = Pattern.compile("@steve", Pattern.CASE_INSENSITIVE);
    private static final String PERMISSION = "smp.steve";

    // Conversation chain settings
    private static final Duration CHAIN_GAP = Duration.ofSeconds(60);
    private static final Duration CLEANUP_AGE = Duration.ofMinutes(10);
    private static final int MAX_CHAIN_LENGTH = 5;

    private final SiqiJoeyPlugin plugin;
    private final SteveConfig config;
    private volatile SteveModel model;
    private final SteveStorage storage;
    private final DisplayManager displayManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // In-memory cooldowns for fast checks (cooldown end times)
    private final Map<UUID, Instant> cooldownEndTimes = new HashMap<>();
    // Prevent duplicate questions while one is pending
    private final Set<UUID> pendingQuestions = ConcurrentHashMap.newKeySet();
    // Recent conversations for chain detection (player -> list of entries, oldest first)
    private final Map<UUID, List<ConversationEntry>> recentConversations = new ConcurrentHashMap<>();

    /**
     * A conversation entry for chain tracking.
     */
    private record ConversationEntry(String question, String answer, Instant timestamp) {}

    public SteveManager(SiqiJoeyPlugin plugin, SteveConfig config, SteveModel model,
                        SteveStorage storage, DisplayManager displayManager) {
        this.plugin = plugin;
        this.config = config;
        this.model = model;
        this.storage = storage;
        this.displayManager = displayManager;

        // Restore cooldowns from database on startup
        restoreCooldownsFromDatabase();

        // Listen for chat messages mentioning Steve
        disposables.add(plugin.watchEvent(AsyncChatEvent.class)
                .filter(e -> containsSteveMention(e.message()))
                .subscribe(event -> {
                    if (event.getPlayer().hasPermission(PERMISSION)) {
                        handleSteveMention(event);
                    } else {
                        sendNoPermission(event.getPlayer());
                    }
                }));
    }

    private void restoreCooldownsFromDatabase() {
        Duration cooldownDuration = Duration.ofSeconds(config.cooldownSeconds());
        storage.getActiveCooldowns(cooldownDuration)
                .toList()
                .observeOn(plugin.mainScheduler())
                .subscribe(entries -> {
                    for (var entry : entries) {
                        Instant endTime = entry.lastUsedAt().plus(cooldownDuration);
                        if (endTime.isAfter(Instant.now())) {
                            cooldownEndTimes.put(entry.playerId(), endTime);
                        }
                    }
                    plugin.getLogger().info("Restored " + cooldownEndTimes.size() + " Steve cooldowns from database");
                }, err -> plugin.getLogger().warning("Failed to restore Steve cooldowns: " + err.getMessage()));
    }

    private boolean containsSteveMention(Component message) {
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        return STEVE_MENTION.matcher(text).find();
    }

    private void handleSteveMention(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check cooldown
        Instant cooldownEnd = cooldownEndTimes.get(playerId);
        if (cooldownEnd != null && cooldownEnd.isAfter(Instant.now())) {
            Duration remaining = Duration.between(Instant.now(), cooldownEnd);
            String formatted = DurationFormat.formatShort(remaining);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    Messages.error(player, "Please wait " + formatted + " before asking Steve again."));
            return;
        }

        // Prevent duplicate questions while one is pending
        if (!pendingQuestions.add(playerId)) {
            return;
        }

        // Extract question (the full message, removing @steve)
        String question = extractQuestion(event.message());
        if (question.isBlank()) {
            pendingQuestions.remove(playerId);
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    Messages.error(player, "What would you like to know? Try: @Steve how do I make a diamond sword?"));
            return;
        }

        // Set cooldown
        startCooldown(playerId);

        // Get conversation chain for context
        List<ConversationTurn> conversationChain = getConversationChain(playerId);
        int contextCount = conversationChain.size();

        // Track whether API call has completed (to avoid showing "thinking..." after error/success)
        var completed = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Send private thinking message after a short delay (so it appears after the chat message)
        // Only show if the API call hasn't already completed
        plugin.timer(500, TimeUnit.MILLISECONDS)
                .subscribe(tick -> {
                    if (!completed.get()) {
                        sendThinking(player);
                    }
                });

        // Call API with conversation history and track response time
        long startTime = System.currentTimeMillis();
        SteveModel currentModel = model; // Capture for lambda
        currentModel.ask(question, conversationChain)
                .observeOn(plugin.mainScheduler())
                .doFinally(() -> {
                    completed.set(true);
                    pendingQuestions.remove(playerId);
                })
                .subscribe(
                        response -> {
                            long elapsedMs = System.currentTimeMillis() - startTime;
                            broadcastResponse(response, currentModel.info(), elapsedMs);

                            // Record conversation for future chain detection
                            recordConversation(playerId, question, response.text());

                            // Save to history with context count
                            storage.saveHistory(playerId, question, response, currentModel.info().displayName(), contextCount)
                                    .subscribe(
                                            () -> {},
                                            err -> plugin.getLogger().warning("Failed to save Steve history: " + err.getMessage())
                                    );
                        },
                        error -> {
                            // Check for rate limit or wrapped rate limit exception
                            Throwable cause = error.getCause() != null ? error.getCause() : error;
                            if (cause instanceof RateLimitException rle) {
                                plugin.getLogger().info("Steve rate limited: " + rle.getMessage());
                                Messages.error(player, rle.getMessage());
                            } else {
                                plugin.getLogger().warning("Steve API error: " + error.getMessage());
                                error.printStackTrace();
                                Messages.error(player, "Sorry, I couldn't find an answer. Try again later!");
                            }
                        }
                );
    }

    /**
     * Gets the conversation chain for a player based on message timing.
     * Messages within 60 seconds of each other form a chain.
     *
     * @param playerId the player to get the chain for
     * @return list of conversation turns (oldest first), max 5 entries
     */
    private List<ConversationTurn> getConversationChain(UUID playerId) {
        List<ConversationEntry> history = recentConversations.get(playerId);
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        // Clean up old entries first
        cleanupOldEntries(history);
        if (history.isEmpty()) {
            return List.of();
        }

        List<ConversationEntry> chain = new ArrayList<>();
        Instant referenceTime = Instant.now();

        // Walk backwards from most recent
        for (int i = history.size() - 1; i >= 0; i--) {
            ConversationEntry entry = history.get(i);
            Duration gap = Duration.between(entry.timestamp(), referenceTime);

            if (gap.compareTo(CHAIN_GAP) <= 0) {
                chain.add(0, entry);
                referenceTime = entry.timestamp();
            } else {
                break;
            }
        }

        // Limit chain length
        if (chain.size() > MAX_CHAIN_LENGTH) {
            chain = chain.subList(chain.size() - MAX_CHAIN_LENGTH, chain.size());
        }

        // Convert to ConversationTurn
        return chain.stream()
                .map(e -> new ConversationTurn(e.question(), e.answer()))
                .toList();
    }

    /**
     * Records a conversation for future chain detection.
     */
    private void recordConversation(UUID playerId, String question, String answer) {
        recentConversations.computeIfAbsent(playerId, k -> new ArrayList<>())
                .add(new ConversationEntry(question, answer, Instant.now()));
    }

    /**
     * Removes entries older than 10 minutes from the list.
     */
    private void cleanupOldEntries(List<ConversationEntry> entries) {
        Instant cutoff = Instant.now().minus(CLEANUP_AGE);
        entries.removeIf(e -> e.timestamp().isBefore(cutoff));
    }

    private String extractQuestion(Component message) {
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        // Remove all @steve mentions
        return STEVE_MENTION.matcher(text).replaceAll("").trim();
    }

    /**
     * Sends a private "no permission" message to the player.
     */
    private void sendNoPermission(Player player) {
        plugin.timer(500, TimeUnit.MILLISECONDS)
                .subscribe(tick -> {
                    Component message = Component.text("Steve")
                            .color(displayManager.getDefaultNameColor())
                            .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                            .append(Component.text("You do not have permission, sorry.").color(NamedTextColor.RED));
                    player.sendMessage(message);
                });
    }

    /**
     * Sends a private "thinking" message to just the asker.
     */
    private void sendThinking(Player asker) {
        // Format like a player message: "Steve: thinking..."
        Component thinking = Component.text("Steve")
                .color(displayManager.getDefaultNameColor())
                .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("thinking...").color(NamedTextColor.GRAY));

        asker.sendMessage(thinking);
    }

    /**
     * Broadcasts Steve's response to all players, formatted like a player chat message.
     */
    private void broadcastResponse(SteveAnswer response, SteveModelInfo modelInfo, long elapsedMs) {
        // Build hover text with model info and response time
        String timeFormatted = formatResponseTime(elapsedMs);
        Component hoverText = Component.text("Model: ").color(NamedTextColor.GRAY)
                .append(Component.text(modelInfo.displayName()).color(NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Response time: ").color(NamedTextColor.GRAY))
                .append(Component.text(timeFormatted).color(NamedTextColor.WHITE));

        // Build the message: "Steve: <answer> (sources: [1] [2] ...)"
        // Use Component.empty() as base to prevent hover from propagating
        Component steveName = Component.text("Steve")
                .color(displayManager.getDefaultNameColor())
                .hoverEvent(HoverEvent.showText(hoverText));

        Component message = Component.empty()
                .append(steveName)
                .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(response.text()).color(NamedTextColor.WHITE));

        // Add inline sources and cost
        boolean hasCitations = !response.citations().isEmpty();
        boolean hasCost = response.costCents() > 0;

        if (hasCitations || hasCost) {
            Component suffix = Component.text(" (").color(NamedTextColor.GRAY);

            // Add citations
            for (int i = 0; i < response.citations().size(); i++) {
                Citation cite = response.citations().get(i);
                if (i > 0) {
                    suffix = suffix.append(Component.text(" ").color(NamedTextColor.GRAY));
                }
                suffix = suffix.append(
                        Component.text("[" + (i + 1) + "]").color(NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.openUrl(cite.url()))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text(cite.title()).color(NamedTextColor.WHITE))));
            }

            // Add cost
            if (hasCost) {
                if (hasCitations) {
                    suffix = suffix.append(Component.text(" | ").color(NamedTextColor.DARK_GRAY));
                }
                String costStr = String.format("%.1f¢", response.costCents());
                suffix = suffix.append(Component.text(costStr).color(NamedTextColor.GRAY));
            }

            suffix = suffix.append(Component.text(")").color(NamedTextColor.GRAY));
            message = message.append(suffix);
        }

        plugin.getServer().broadcast(message);
    }

    /**
     * Start the cooldown for a player - updates in-memory cache and persists to database.
     */
    private void startCooldown(UUID playerId) {
        Instant endTime = Instant.now().plus(Duration.ofSeconds(config.cooldownSeconds()));
        cooldownEndTimes.put(playerId, endTime);

        // Persist to database
        storage.recordSteveUsage(playerId).subscribe(
                () -> {},
                err -> plugin.getLogger().warning("Failed to record Steve usage: " + err.getMessage())
        );
    }

    /**
     * Gets the current model.
     */
    public SteveModel getModel() {
        return model;
    }

    /**
     * Sets the active model.
     */
    public void setModel(SteveModel model) {
        this.model = model;
    }

    /**
     * Gets the storage for history queries.
     */
    public SteveStorage getStorage() {
        return storage;
    }

    private static String formatResponseTime(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        } else {
            return String.format("%.1fs", ms / 1000.0);
        }
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
