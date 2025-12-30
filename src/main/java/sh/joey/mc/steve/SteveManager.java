package sh.joey.mc.steve;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Manages Steve AI chatbot interactions.
 * Listens for @Steve mentions in chat and responds with researched answers.
 */
public final class SteveManager implements Disposable {

    private static final Pattern STEVE_MENTION = Pattern.compile("@steve", Pattern.CASE_INSENSITIVE);

    // Player chat color from the server config
    private static final TextColor PLAYER_NAME_COLOR = TextColor.fromHexString("#eded9d");

    private final SiqiJoeyPlugin plugin;
    private final SteveConfig config;
    private final SteveApiService apiService;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // Cooldown tracking
    private final Map<UUID, Instant> cooldowns = new ConcurrentHashMap<>();
    // Prevent duplicate questions while one is pending
    private final Set<UUID> pendingQuestions = ConcurrentHashMap.newKeySet();

    public SteveManager(SiqiJoeyPlugin plugin, SteveConfig config, SteveApiService apiService) {
        this.plugin = plugin;
        this.config = config;
        this.apiService = apiService;

        // Listen for chat messages mentioning Steve
        disposables.add(plugin.watchEvent(AsyncChatEvent.class)
                .filter(e -> containsSteveMention(e.message()))
                .filter(e -> e.getPlayer().hasPermission("smp.steve"))
                .subscribe(this::handleSteveMention));
    }

    private boolean containsSteveMention(Component message) {
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        return STEVE_MENTION.matcher(text).find();
    }

    private void handleSteveMention(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check cooldown
        Instant lastUsed = cooldowns.get(playerId);
        if (lastUsed != null) {
            long secondsRemaining = config.cooldownSeconds() -
                    Duration.between(lastUsed, Instant.now()).getSeconds();
            if (secondsRemaining > 0) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        Messages.error(player, "Please wait " + secondsRemaining + "s before asking Steve again."));
                return;
            }
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
        cooldowns.put(playerId, Instant.now());

        // Send private thinking message to just the asker
        plugin.getServer().getScheduler().runTask(plugin, () ->
                sendThinking(player));

        // Call API
        apiService.ask(question)
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
     * Sends a private "thinking" message to just the asker.
     */
    private void sendThinking(Player asker) {
        // Format like a player message: "Steve: thinking..."
        Component thinking = Component.text("Steve")
                .color(PLAYER_NAME_COLOR)
                .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("thinking...").color(NamedTextColor.GRAY));

        asker.sendMessage(thinking);
    }

    /**
     * Broadcasts Steve's response to all players, formatted like a player chat message.
     */
    private void broadcastResponse(SteveResponse response) {
        // Build the message: "Steve: <answer> (sources: [1] [2] ...)"
        Component message = Component.text("Steve")
                .color(PLAYER_NAME_COLOR)
                .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(response.text()).color(NamedTextColor.WHITE));

        // Add inline sources if we have citations
        if (!response.citations().isEmpty()) {
            Component sources = Component.text(" (").color(NamedTextColor.GRAY);

            for (int i = 0; i < response.citations().size(); i++) {
                SteveResponse.Citation cite = response.citations().get(i);
                if (i > 0) {
                    sources = sources.append(Component.text(" ").color(NamedTextColor.GRAY));
                }
                sources = sources.append(
                        Component.text("[" + (i + 1) + "]").color(NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.openUrl(cite.url()))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text(cite.title()).color(NamedTextColor.WHITE))));
            }
            sources = sources.append(Component.text(")").color(NamedTextColor.GRAY));
            message = message.append(sources);
        }

        plugin.getServer().broadcast(message);
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
