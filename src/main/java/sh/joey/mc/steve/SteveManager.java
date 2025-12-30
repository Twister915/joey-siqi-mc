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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static sh.joey.mc.steve.Messages.PREFIX;

/**
 * Manages Steve AI chatbot interactions.
 * Listens for @Steve mentions in chat and responds with researched answers.
 */
public final class SteveManager implements Disposable {

    private static final Pattern STEVE_MENTION = Pattern.compile("@steve", Pattern.CASE_INSENSITIVE);
    private static final int MAX_LINE_LENGTH = 250;

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

        // Broadcast thinking message on main thread
        plugin.getServer().getScheduler().runTask(plugin, () ->
                broadcastThinking(player, question));

        // Call API
        apiService.ask(question)
                .observeOn(plugin.mainScheduler())
                .doFinally(() -> pendingQuestions.remove(playerId))
                .subscribe(
                        response -> broadcastResponse(player, response),
                        error -> {
                            plugin.getLogger().warning("Steve API error: " + error.getMessage());
                            error.printStackTrace();
                            Messages.error(player, "Sorry, I couldn't research that right now. Try again later!");
                        }
                );
    }

    private String extractQuestion(Component message) {
        String text = PlainTextComponentSerializer.plainText().serialize(message);
        // Remove all @steve mentions
        return STEVE_MENTION.matcher(text).replaceAll("").trim();
    }

    private void broadcastThinking(Player asker, String question) {
        // [Steve] PlayerName asked: "question..."
        Component header = PREFIX
                .append(Component.text(asker.getName()).color(NamedTextColor.WHITE))
                .append(Component.text(" asked: ").color(NamedTextColor.GRAY))
                .append(Component.text("\"" + truncate(question, 60) + "\"").color(NamedTextColor.WHITE)
                        .decorate(TextDecoration.ITALIC));

        // [Steve] Researching...
        Component thinking = PREFIX
                .append(Component.text("Researching...").color(NamedTextColor.YELLOW)
                        .decorate(TextDecoration.ITALIC));

        plugin.getServer().broadcast(header);
        plugin.getServer().broadcast(thinking);
    }

    private void broadcastResponse(Player asker, SteveResponse response) {
        // Split long responses into multiple messages
        List<String> lines = splitResponse(response.text(), MAX_LINE_LENGTH);

        for (String line : lines) {
            Component msg = PREFIX.append(Component.text(line).color(NamedTextColor.WHITE));
            plugin.getServer().broadcast(msg);
        }

        // Add clickable source links if we have citations
        if (!response.citations().isEmpty()) {
            Component sources = PREFIX.append(Component.text("Sources: ").color(NamedTextColor.GRAY));

            for (int i = 0; i < response.citations().size(); i++) {
                SteveResponse.Citation cite = response.citations().get(i);
                if (i > 0) {
                    sources = sources.append(Component.text(" ").color(NamedTextColor.GRAY));
                }
                sources = sources.append(
                        Component.text("[" + (i + 1) + "]").color(NamedTextColor.AQUA)
                                .decorate(TextDecoration.UNDERLINED)
                                .clickEvent(ClickEvent.openUrl(cite.url()))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text(cite.title()).color(NamedTextColor.WHITE)
                                                .append(Component.newline())
                                                .append(Component.text("Click to open").color(NamedTextColor.GRAY)))));
            }
            plugin.getServer().broadcast(sources);
        }
    }

    private List<String> splitResponse(String text, int maxLength) {
        List<String> lines = new ArrayList<>();

        // Split on sentence boundaries
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (current.length() + sentence.length() + 1 > maxLength) {
                if (current.length() > 0) {
                    lines.add(current.toString().trim());
                    current = new StringBuilder();
                }
                // If single sentence is too long, split by words
                if (sentence.length() > maxLength) {
                    lines.addAll(splitByWords(sentence, maxLength));
                } else {
                    current.append(sentence).append(" ");
                }
            } else {
                current.append(sentence).append(" ");
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString().trim());
        }

        return lines;
    }

    private List<String> splitByWords(String text, int maxLength) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() + word.length() + 1 > maxLength) {
                if (current.length() > 0) {
                    lines.add(current.toString().trim());
                    current = new StringBuilder();
                }
            }
            current.append(word).append(" ");
        }

        if (current.length() > 0) {
            lines.add(current.toString().trim());
        }

        return lines;
    }

    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
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
