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
import sh.joey.mc.pagination.ChatPaginator;
import sh.joey.mc.pagination.PaginatedItem;

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

    private static final String MESSAGE_PREFIX = "  • ";

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
     * Shows all messages with pagination using the ChatPaginator.
     */
    private void showAllPaginated(Player player, Map<String, List<String>> messagesByCategory, int page, int totalMessages) {
        ChatPaginator paginator = new ChatPaginator()
                .title(PREFIX.append(Component.text("All Messages").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)))
                .subtitle(Component.text("Showing " + totalMessages + " messages across " + messagesByCategory.size() + " categories").color(NamedTextColor.YELLOW))
                .command(p -> "/daymsgdebug " + p)
                .backButton("Back to Summary", "/daymsgdebug");

        boolean first = true;
        for (Map.Entry<String, List<String>> entry : messagesByCategory.entrySet()) {
            if (!first) {
                paginator.add(PaginatedItem.empty());
            }
            first = false;

            paginator.section(entry.getKey());

            for (String message : entry.getValue()) {
                String plainText = MESSAGE_PREFIX + message;
                Component component = Component.text(MESSAGE_PREFIX).color(NamedTextColor.DARK_GRAY)
                        .append(Component.text(message).color(NamedTextColor.WHITE));
                paginator.add(PaginatedItem.wrapping(component, plainText));
            }
        }

        paginator.sendPage(player, page);
    }
}
