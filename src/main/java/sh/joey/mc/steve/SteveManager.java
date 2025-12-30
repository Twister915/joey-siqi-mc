package sh.joey.mc.steve;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.permissions.DisplayManager;
import sh.joey.mc.steve.SteveAnswer.Citation;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
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

    private final SiqiJoeyPlugin plugin;
    private final SteveConfig config;
    private final SteveModel model;
    private final SteveStorage storage;
    private final DisplayManager displayManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // In-memory cooldowns for fast checks (cooldown end times)
    private final Map<UUID, Instant> cooldownEndTimes = new HashMap<>();
    // Prevent duplicate questions while one is pending
    private final Set<UUID> pendingQuestions = ConcurrentHashMap.newKeySet();

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
            long secondsRemaining = Duration.between(Instant.now(), cooldownEnd).getSeconds();
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    Messages.error(player, "Please wait " + secondsRemaining + "s before asking Steve again."));
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

        // Send private thinking message after a short delay (so it appears after the chat message)
        plugin.timer(500, TimeUnit.MILLISECONDS)
                .subscribe(tick -> sendThinking(player));

        // Call API
        model.ask(question)
                .observeOn(plugin.mainScheduler())
                .doFinally(() -> pendingQuestions.remove(playerId))
                .subscribe(
                        response -> broadcastResponse(response),
                        error -> {
                            plugin.getLogger().warning("Steve API error: " + error.getMessage());
                            error.printStackTrace();
                            Messages.error(player, "Sorry, I couldn't find an answer. Try again later!");
                        }
                );
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
    private void broadcastResponse(SteveAnswer response) {
        // Build the message: "Steve: <answer> (sources: [1] [2] ...)"
        Component message = Component.text("Steve")
                .color(displayManager.getDefaultNameColor())
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

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
