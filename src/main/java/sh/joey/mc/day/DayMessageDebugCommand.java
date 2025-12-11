package sh.joey.mc.day;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.messages.MessageGenerator;
import sh.joey.mc.messages.MessageGenerator.ContextProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug command to show all possible contextual messages for the current player state.
 * Uses the same registry as MessageGenerator to ensure consistency.
 *
 * Usage:
 * - /daymsgdebug - Show summary of all categories with counts
 * - /daymsgdebug <category> - Show messages for a specific category
 * - /daymsgdebug all - Show all messages (paginated)
 * - /daymsgdebug <page> - Show specific page of all messages
 */
public final class DayMessageDebugCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("DEBUG").color(NamedTextColor.RED))
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    // Minecraft chat shows exactly 20 lines. We emit exactly 20 lines per page so each page
    // completely clears the previous from the buffer.
    // Fixed overhead: 3 header lines + 3 footer lines = 6 lines
    // Content area: 20 - 6 = 14 lines for messages and category headers
    private static final int CONTENT_LINES_PER_PAGE = 14;

    // Minecraft default chat width in pixels
    private static final int CHAT_WIDTH_PIXELS = 320;

    // Message prefix "  • " width in pixels (2 spaces + bullet + space)
    private static final String MESSAGE_PREFIX = "  • ";

    // Content line types for pagination - each represents exactly one chat line
    private sealed interface ContentLine permits CategoryHeader, EmptyLine, MessageLine {}
    private record CategoryHeader(String name) implements ContentLine {}
    private record EmptyLine() implements ContentLine {}
    private record MessageLine(String message, int visualLines) implements ContentLine {}

    /**
     * Returns the pixel width of a character in Minecraft's default font.
     * Based on Minecraft 1.19+ bitmap font glyph widths.
     * Each character also has 1px spacing after it.
     */
    private static int getCharWidth(char c) {
        return switch (c) {
            // Narrow characters (1-2px)
            case 'i', 'l', '!', '|' -> 2;
            case '.', ',', ':', ';', '\'', '`' -> 2;
            // Slightly narrow (3-4px)
            case 'I', 't', 'f', 'k', '"', '(', ')', '[', ']', '{', '}', '<', '>' -> 4;
            case ' ' -> 4;
            // Wide characters (6-7px)
            case 'm', 'w', 'M', 'W', '@', '~' -> 6;
            // Bullet point
            case '•', '▸' -> 2;
            // Default for most alphanumeric (5px)
            default -> 6;
        };
    }

    /**
     * Calculates the pixel width of a string in Minecraft's default font.
     */
    private static int calculatePixelWidth(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            width += getCharWidth(c);
        }
        return width;
    }

    /**
     * Calculates how many visual lines a message will take, accounting for word wrap.
     */
    private static int calculateVisualLines(String message) {
        String fullLine = MESSAGE_PREFIX + message;
        int width = calculatePixelWidth(fullLine);
        return (int) Math.ceil((double) width / CHAT_WIDTH_PIXELS);
    }

    @Override
    public String getName() {
        return "daymsgdebug";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX.append(Component.text("This command can only be used by players.").color(NamedTextColor.RED)));
                return;
            }

            // Collect all messages by category using the registry
            Map<String, List<String>> messagesByCategory = collectMessagesByCategory(player);

            // Calculate totals
            int totalMessages = messagesByCategory.values().stream().mapToInt(List::size).sum();
            int categoryCount = (int) messagesByCategory.values().stream().filter(l -> !l.isEmpty()).count();

            // Parse arguments
            if (args.length == 0) {
                showSummary(player, messagesByCategory, totalMessages, categoryCount);
            } else {
                String arg = args[0].toLowerCase();
                if (arg.equals("all") || arg.matches("\\d+")) {
                    int page = arg.equals("all") ? 1 : Integer.parseInt(arg);
                    showAllPaginated(player, messagesByCategory, page, totalMessages);
                } else {
                    showCategory(player, messagesByCategory, arg);
                }
            }
        });
    }

    /**
     * Collects all context messages grouped by category using the registry.
     */
    private Map<String, List<String>> collectMessagesByCategory(Player player) {
        World world = player.getWorld();
        Map<String, List<String>> messagesByCategory = new LinkedHashMap<>();

        for (ContextProvider provider : MessageGenerator.getContextProviders()) {
            List<String> messages = new ArrayList<>();
            provider.method().addMessages(player, world, messages, MessageGenerator.MessageType.DAY);

            if (!messages.isEmpty()) {
                String category = provider.biomeOnly() ? "Biome (Fallback)" : provider.category();
                messagesByCategory
                        .computeIfAbsent(category, k -> new ArrayList<>())
                        .addAll(messages);
            }
        }

        return messagesByCategory;
    }

    /**
     * Shows summary view with category names and counts.
     */
    private void showSummary(Player player, Map<String, List<String>> messagesByCategory, int totalMessages, int categoryCount) {
        player.sendMessage(PREFIX.append(Component.text("Contextual Message Debug - Summary").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
        player.sendMessage(Component.text("Total: ").color(NamedTextColor.YELLOW)
                .append(Component.text(totalMessages).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(" messages across ").color(NamedTextColor.YELLOW))
                .append(Component.text(categoryCount).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
                .append(Component.text(" categories").color(NamedTextColor.YELLOW)));
        player.sendMessage(Component.empty());

        for (Map.Entry<String, List<String>> entry : messagesByCategory.entrySet()) {
            String category = entry.getKey();
            int count = entry.getValue().size();

            // Create clickable category name
            String shortName = category.toLowerCase().split(" ")[0];
            Component categoryLine = Component.text("▸ ")
                    .color(count > 0 ? NamedTextColor.AQUA : NamedTextColor.DARK_GRAY)
                    .append(Component.text(category).decorate(count > 0 ? TextDecoration.BOLD : TextDecoration.STRIKETHROUGH))
                    .append(Component.text(" (" + count + ")").color(count > 0 ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY));

            if (count > 0) {
                categoryLine = categoryLine
                        .clickEvent(ClickEvent.runCommand("/daymsgdebug " + shortName))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to view " + category + " messages")));
            }

            player.sendMessage(categoryLine);
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("Use ").color(NamedTextColor.GRAY)
                .append(Component.text("/daymsgdebug <category>").color(NamedTextColor.YELLOW))
                .append(Component.text(" to view messages").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("Use ").color(NamedTextColor.GRAY)
                .append(Component.text("/daymsgdebug all").color(NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/daymsgdebug all"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to view all messages"))))
                .append(Component.text(" to view all messages").color(NamedTextColor.GRAY)));
    }

    /**
     * Shows messages for a specific category.
     */
    private void showCategory(Player player, Map<String, List<String>> messagesByCategory, String categoryFilter) {
        // Find matching category (case-insensitive partial match)
        String matchedCategory = null;
        List<String> messages = null;

        for (Map.Entry<String, List<String>> entry : messagesByCategory.entrySet()) {
            if (entry.getKey().toLowerCase().contains(categoryFilter)) {
                matchedCategory = entry.getKey();
                messages = entry.getValue();
                break;
            }
        }

        if (matchedCategory == null || messages == null || messages.isEmpty()) {
            player.sendMessage(PREFIX.append(Component.text("No messages found for category: " + categoryFilter).color(NamedTextColor.RED)));
            player.sendMessage(Component.text("Use ").color(NamedTextColor.GRAY)
                    .append(Component.text("/daymsgdebug").color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/daymsgdebug")))
                    .append(Component.text(" to see available categories").color(NamedTextColor.GRAY)));
            return;
        }

        player.sendMessage(PREFIX.append(Component.text(matchedCategory).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
                .append(Component.text(" (" + messages.size() + " messages)").color(NamedTextColor.YELLOW)));
        player.sendMessage(Component.empty());

        for (String message : messages) {
            player.sendMessage(Component.text("  • ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(message).color(NamedTextColor.WHITE)));
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("[Back to Summary]").color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand("/daymsgdebug"))
                .hoverEvent(HoverEvent.showText(Component.text("Return to summary view"))));
    }

    /**
     * Shows all messages with pagination.
     * Emits exactly 20 lines per page to perfectly fill the Minecraft chat window.
     * Accounts for word-wrapped messages that take multiple visual lines.
     */
    private void showAllPaginated(Player player, Map<String, List<String>> messagesByCategory, int page, int totalMessages) {
        // Flatten all content into lines, calculating visual lines for each message
        List<ContentLine> allLines = new ArrayList<>();
        String currentCategory = null;
        for (Map.Entry<String, List<String>> categoryEntry : messagesByCategory.entrySet()) {
            String category = categoryEntry.getKey();
            for (String message : categoryEntry.getValue()) {
                if (!category.equals(currentCategory)) {
                    if (currentCategory != null) {
                        allLines.add(new EmptyLine()); // separator between categories
                    }
                    allLines.add(new CategoryHeader(category));
                    currentCategory = category;
                }
                int visualLines = calculateVisualLines(message);
                allLines.add(new MessageLine(message, visualLines));
            }
        }

        // Build pages by counting visual lines, not content items
        List<List<ContentLine>> pages = new ArrayList<>();
        List<ContentLine> currentPage = new ArrayList<>();
        int currentPageVisualLines = 0;

        for (ContentLine line : allLines) {
            int lineVisualLines = switch (line) {
                case CategoryHeader ignored -> 1;
                case EmptyLine ignored -> 1;
                case MessageLine(String ignored, int vl) -> vl;
            };

            // If adding this line would exceed page limit, start a new page
            // (but always add at least one item per page to avoid infinite loop)
            if (currentPageVisualLines + lineVisualLines > CONTENT_LINES_PER_PAGE && !currentPage.isEmpty()) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                currentPageVisualLines = 0;
            }

            currentPage.add(line);
            currentPageVisualLines += lineVisualLines;
        }
        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }

        int totalPages = Math.max(1, pages.size());

        // Clamp page number
        page = Math.max(1, Math.min(page, totalPages));

        List<ContentLine> pageContent = pages.isEmpty() ? List.of() : pages.get(page - 1);

        // === HEADER (3 lines) ===
        player.sendMessage(PREFIX.append(Component.text("All Messages - Page " + page + "/" + totalPages).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
        player.sendMessage(Component.text("Showing " + totalMessages + " messages across " + messagesByCategory.size() + " categories").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.empty());

        // === CONTENT (14 visual lines) ===
        int visualLinesEmitted = 0;
        for (ContentLine line : pageContent) {
            switch (line) {
                case CategoryHeader(String name) -> {
                    player.sendMessage(Component.text("▸ " + name).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
                    visualLinesEmitted += 1;
                }
                case EmptyLine() -> {
                    player.sendMessage(Component.empty());
                    visualLinesEmitted += 1;
                }
                case MessageLine(String message, int visualLines) -> {
                    player.sendMessage(Component.text("  • ").color(NamedTextColor.DARK_GRAY)
                            .append(Component.text(message).color(NamedTextColor.WHITE)));
                    visualLinesEmitted += visualLines;
                }
            }
        }

        // Pad remaining content lines to ensure exactly 14 visual lines
        while (visualLinesEmitted < CONTENT_LINES_PER_PAGE) {
            player.sendMessage(Component.empty());
            visualLinesEmitted++;
        }

        // === FOOTER (3 lines) ===
        player.sendMessage(Component.empty());
        player.sendMessage(buildNavigation(page, totalPages));
        player.sendMessage(Component.text("[Back to Summary]").color(NamedTextColor.GRAY)
                .clickEvent(ClickEvent.runCommand("/daymsgdebug"))
                .hoverEvent(HoverEvent.showText(Component.text("Return to summary view"))));
    }

    private Component buildNavigation(int page, int totalPages) {
        Component navigation = Component.empty();

        // Previous button
        if (page > 1) {
            navigation = navigation.append(Component.text("[◀ Prev] ").color(NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/daymsgdebug " + (page - 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page - 1)))));
        } else {
            navigation = navigation.append(Component.text("[◀ Prev] ").color(NamedTextColor.DARK_GRAY));
        }

        // Page numbers (show up to 5 around current)
        int startPage = Math.max(1, page - 2);
        int endPage = Math.min(totalPages, page + 2);

        for (int p = startPage; p <= endPage; p++) {
            if (p == page) {
                navigation = navigation.append(Component.text("[" + p + "] ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            } else {
                navigation = navigation.append(Component.text("[" + p + "] ").color(NamedTextColor.YELLOW)
                        .clickEvent(ClickEvent.runCommand("/daymsgdebug " + p))
                        .hoverEvent(HoverEvent.showText(Component.text("Go to page " + p))));
            }
        }

        // Next button
        if (page < totalPages) {
            navigation = navigation.append(Component.text("[Next ▶]").color(NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/daymsgdebug " + (page + 1)))
                    .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page + 1)))));
        } else {
            navigation = navigation.append(Component.text("[Next ▶]").color(NamedTextColor.DARK_GRAY));
        }

        return navigation;
    }
}
