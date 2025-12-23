package sh.joey.mc.pagination;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * Builder for creating paginated chat views.
 * <p>
 * Handles the complexity of:
 * - Calculating visual line counts (accounting for word wrap)
 * - Building pages that fit exactly in Minecraft's 20-line chat window
 * - Rendering clickable navigation
 * - Console-friendly output (no padding, item-count pagination)
 */
public final class ChatPaginator {

    private final List<PaginatedItem> items = new ArrayList<>();
    private Component title;
    private Component subtitle;
    private Function<Integer, String> commandBuilder;
    private String backButtonText;
    private String backButtonCommand;

    // Fixed line counts for full pagination mode (players only)
    // Header: title + subtitle + blank = 3 lines
    // Footer: blank + navigation + back button = 3 lines
    // Content: 20 - 3 - 3 = 14 lines
    private static final int HEADER_LINES = 3;
    private static final int FOOTER_LINES = 3;
    private static final int CONTENT_LINES_PER_PAGE = ChatMetrics.CHAT_VISIBLE_LINES - HEADER_LINES - FOOTER_LINES;

    // Console uses simpler item-count pagination
    private static final int CONSOLE_ITEMS_PER_PAGE = 15;

    // Cached page data (built lazily, separate for player vs console)
    private List<List<PaginatedItem>> playerPages;
    private List<List<PaginatedItem>> consolePages;

    /**
     * Sets the title shown at the top of each page.
     */
    public ChatPaginator title(Component title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the subtitle shown below the title.
     */
    public ChatPaginator subtitle(Component subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    /**
     * Sets the command pattern for navigation.
     * The function receives a page number and returns the full command string.
     * Example: {@code p -> "/home list " + p}
     */
    public ChatPaginator command(Function<Integer, String> commandBuilder) {
        this.commandBuilder = commandBuilder;
        return this;
    }

    /**
     * Adds an optional back button shown at the bottom of paginated views.
     */
    public ChatPaginator backButton(String text, String command) {
        this.backButtonText = text;
        this.backButtonCommand = command;
        return this;
    }

    /**
     * Adds a single item to the paginator.
     */
    public ChatPaginator add(PaginatedItem item) {
        this.items.add(item);
        this.playerPages = null;
        this.consolePages = null;
        return this;
    }

    /**
     * Adds multiple items to the paginator.
     */
    public ChatPaginator addAll(Collection<PaginatedItem> items) {
        this.items.addAll(items);
        this.playerPages = null;
        this.consolePages = null;
        return this;
    }

    /**
     * Adds a section header with the standard formatting.
     * Does NOT add an empty line before - caller should do that if needed.
     */
    public ChatPaginator section(String headerText) {
        return add(PaginatedItem.header(headerText));
    }

    /**
     * Returns the total number of items.
     */
    public int totalItems() {
        return items.size();
    }

    /**
     * Sends the specified page to the sender.
     * For players: fills the 20-line chat window with padding.
     * For console: simple output without padding or clickable elements.
     */
    public void sendPage(CommandSender sender, int page) {
        boolean isPlayer = sender instanceof Player;
        List<List<PaginatedItem>> pages = isPlayer ? buildPlayerPages() : buildConsolePages();

        int totalPages = Math.max(1, pages.size());
        page = Math.max(1, Math.min(page, totalPages));

        if (isPlayer) {
            sendPlayerPage(sender, pages, page, totalPages);
        } else {
            sendConsolePage(sender, pages, page, totalPages);
        }
    }

    /**
     * Sends a page formatted for players (with padding and clickable navigation).
     */
    private void sendPlayerPage(CommandSender sender, List<List<PaginatedItem>> pages, int page, int totalPages) {
        List<PaginatedItem> pageContent = pages.isEmpty() ? List.of() : pages.get(page - 1);

        // Calculate content visual lines
        int contentVisualLines = 0;
        for (PaginatedItem item : pageContent) {
            contentVisualLines += item.visualLines();
        }

        // === PADDING (emitted first, so it appears at top) ===
        int paddingLines = CONTENT_LINES_PER_PAGE - contentVisualLines;
        for (int i = 0; i < paddingLines; i++) {
            sender.sendMessage(Component.empty());
        }

        // === HEADER (3 lines) ===
        if (title != null) {
            sender.sendMessage(title.append(Component.text(" - Page " + page + "/" + totalPages)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, false)));
        } else {
            sender.sendMessage(Component.text("Page " + page + "/" + totalPages).color(NamedTextColor.GOLD));
        }
        if (subtitle != null) {
            sender.sendMessage(subtitle);
        } else {
            sender.sendMessage(Component.empty());
        }
        sender.sendMessage(Component.empty());

        // === CONTENT ===
        for (PaginatedItem item : pageContent) {
            sender.sendMessage(item.render());
        }

        // === FOOTER (3 lines) ===
        sender.sendMessage(Component.empty());
        sender.sendMessage(buildPlayerNavigation(page, totalPages));
        if (backButtonText != null && backButtonCommand != null) {
            sender.sendMessage(Component.text("[" + backButtonText + "]").color(NamedTextColor.GRAY)
                .clickEvent(ClickEvent.runCommand(backButtonCommand))
                .hoverEvent(HoverEvent.showText(Component.text("Click to go back"))));
        } else {
            sender.sendMessage(Component.empty());
        }
    }

    /**
     * Sends a page formatted for console (no padding, no clickable elements).
     */
    private void sendConsolePage(CommandSender sender, List<List<PaginatedItem>> pages, int page, int totalPages) {
        List<PaginatedItem> pageContent = pages.isEmpty() ? List.of() : pages.get(page - 1);

        // === HEADER ===
        if (title != null) {
            sender.sendMessage(title.append(Component.text(" - Page " + page + "/" + totalPages)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, false)));
        } else {
            sender.sendMessage(Component.text("Page " + page + "/" + totalPages).color(NamedTextColor.GOLD));
        }
        if (subtitle != null) {
            sender.sendMessage(subtitle);
        }

        // === CONTENT ===
        for (PaginatedItem item : pageContent) {
            sender.sendMessage(item.render());
        }

        // === FOOTER (only if multiple pages) ===
        if (totalPages > 1 && commandBuilder != null) {
            sender.sendMessage(Component.text("Use " + commandBuilder.apply(page) + " <page> to navigate")
                .color(NamedTextColor.GRAY));
        }
    }

    /**
     * Builds the navigation bar with prev/next buttons for players.
     */
    private Component buildPlayerNavigation(int page, int totalPages) {
        if (commandBuilder == null) {
            return Component.text("Page " + page + " of " + totalPages).color(NamedTextColor.GRAY);
        }

        Component navigation = Component.empty();

        // Previous button
        if (page > 1) {
            navigation = navigation.append(Component.text("[◀ Prev] ").color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(commandBuilder.apply(page - 1)))
                .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page - 1)))));
        } else {
            navigation = navigation.append(Component.text("[◀ Prev] ").color(NamedTextColor.DARK_GRAY));
        }

        // Page numbers (show up to 5 around current)
        int startPage = Math.max(1, page - 2);
        int endPage = Math.min(totalPages, page + 2);

        for (int p = startPage; p <= endPage; p++) {
            if (p == page) {
                navigation = navigation.append(Component.text("[" + p + "] ")
                    .color(NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD));
            } else {
                navigation = navigation.append(Component.text("[" + p + "] ").color(NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand(commandBuilder.apply(p)))
                    .hoverEvent(HoverEvent.showText(Component.text("Go to page " + p))));
            }
        }

        // Next button
        if (page < totalPages) {
            navigation = navigation.append(Component.text("[Next ▶]").color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(commandBuilder.apply(page + 1)))
                .hoverEvent(HoverEvent.showText(Component.text("Go to page " + (page + 1)))));
        } else {
            navigation = navigation.append(Component.text("[Next ▶]").color(NamedTextColor.DARK_GRAY));
        }

        return navigation;
    }

    /**
     * Builds pages for players by accumulating visual line counts.
     */
    private List<List<PaginatedItem>> buildPlayerPages() {
        if (playerPages != null) {
            return playerPages;
        }

        playerPages = new ArrayList<>();
        if (items.isEmpty()) {
            return playerPages;
        }

        List<PaginatedItem> currentPage = new ArrayList<>();
        int currentPageVisualLines = 0;

        for (PaginatedItem item : items) {
            int itemVisualLines = item.visualLines();

            // If adding this item would exceed page limit, start a new page
            // (but always add at least one item per page to avoid infinite loop)
            if (currentPageVisualLines + itemVisualLines > CONTENT_LINES_PER_PAGE && !currentPage.isEmpty()) {
                playerPages.add(currentPage);
                currentPage = new ArrayList<>();
                currentPageVisualLines = 0;
            }

            currentPage.add(item);
            currentPageVisualLines += itemVisualLines;
        }

        if (!currentPage.isEmpty()) {
            playerPages.add(currentPage);
        }

        return playerPages;
    }

    /**
     * Builds pages for console by simple item count.
     */
    private List<List<PaginatedItem>> buildConsolePages() {
        if (consolePages != null) {
            return consolePages;
        }

        consolePages = new ArrayList<>();
        if (items.isEmpty()) {
            return consolePages;
        }

        List<PaginatedItem> currentPage = new ArrayList<>();

        for (PaginatedItem item : items) {
            if (currentPage.size() >= CONSOLE_ITEMS_PER_PAGE) {
                consolePages.add(currentPage);
                currentPage = new ArrayList<>();
            }
            currentPage.add(item);
        }

        if (!currentPage.isEmpty()) {
            consolePages.add(currentPage);
        }

        return consolePages;
    }
}
