package sh.joey.mc.pagination;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * - Compact mode for single-page content
 */
public final class ChatPaginator {

    private final List<PaginatedItem> items = new ArrayList<>();
    private Component title;
    private Component subtitle;
    private Function<Integer, String> commandBuilder;
    private String backButtonText;
    private String backButtonCommand;

    // Fixed line counts for full pagination mode
    // Header: title + subtitle + blank = 3 lines
    // Footer: blank + navigation + back button = 3 lines
    // Content: 20 - 3 - 3 = 14 lines
    private static final int HEADER_LINES = 3;
    private static final int FOOTER_LINES = 3;
    private static final int CONTENT_LINES_PER_PAGE = ChatMetrics.CHAT_VISIBLE_LINES - HEADER_LINES - FOOTER_LINES;

    // Cached page data (built lazily)
    private List<List<PaginatedItem>> pages;

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
        this.pages = null; // invalidate cache
        return this;
    }

    /**
     * Adds multiple items to the paginator.
     */
    public ChatPaginator addAll(Collection<PaginatedItem> items) {
        this.items.addAll(items);
        this.pages = null;
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
     * Returns the total number of pages.
     */
    public int totalPages() {
        buildPagesIfNeeded();
        return Math.max(1, pages.size());
    }

    /**
     * Returns the total number of items.
     */
    public int totalItems() {
        return items.size();
    }

    /**
     * Sends the specified page to the player.
     * Always emits exactly 20 lines to fill the Minecraft chat window.
     */
    public void sendPage(Player player, int page) {
        buildPagesIfNeeded();

        int totalPages = Math.max(1, pages.size());

        // Clamp page to valid range
        page = Math.max(1, Math.min(page, totalPages));

        sendFullPage(player, page, totalPages);
    }

    /**
     * Sends a full paginated page (exactly 20 lines).
     * Padding is emitted first so content appears at the bottom of the chat window.
     */
    private void sendFullPage(Player player, int page, int totalPages) {
        List<PaginatedItem> pageContent = pages.isEmpty() ? List.of() : pages.get(page - 1);

        // Calculate content visual lines
        int contentVisualLines = 0;
        for (PaginatedItem item : pageContent) {
            contentVisualLines += item.visualLines();
        }

        // === PADDING (emitted first, so it appears at top) ===
        int paddingLines = CONTENT_LINES_PER_PAGE - contentVisualLines;
        for (int i = 0; i < paddingLines; i++) {
            player.sendMessage(Component.empty());
        }

        // === HEADER (3 lines) ===
        if (title != null) {
            // Append page indicator to title
            player.sendMessage(title.append(Component.text(" - Page " + page + "/" + totalPages)
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, false)));
        } else {
            player.sendMessage(Component.text("Page " + page + "/" + totalPages).color(NamedTextColor.GOLD));
        }
        if (subtitle != null) {
            player.sendMessage(subtitle);
        } else {
            player.sendMessage(Component.empty());
        }
        player.sendMessage(Component.empty());

        // === CONTENT ===
        for (PaginatedItem item : pageContent) {
            player.sendMessage(item.render());
        }

        // === FOOTER (3 lines) ===
        player.sendMessage(Component.empty());
        player.sendMessage(buildNavigation(page, totalPages));
        if (backButtonText != null && backButtonCommand != null) {
            player.sendMessage(Component.text("[" + backButtonText + "]").color(NamedTextColor.GRAY)
                .clickEvent(ClickEvent.runCommand(backButtonCommand))
                .hoverEvent(HoverEvent.showText(Component.text("Click to go back"))));
        } else {
            player.sendMessage(Component.empty());
        }
    }

    /**
     * Builds the navigation bar with prev/next buttons and page numbers.
     */
    private Component buildNavigation(int page, int totalPages) {
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
     * Builds pages by accumulating visual line counts.
     */
    private void buildPagesIfNeeded() {
        if (pages != null) {
            return;
        }

        pages = new ArrayList<>();
        if (items.isEmpty()) {
            return;
        }

        List<PaginatedItem> currentPage = new ArrayList<>();
        int currentPageVisualLines = 0;

        for (PaginatedItem item : items) {
            int itemVisualLines = item.visualLines();

            // If adding this item would exceed page limit, start a new page
            // (but always add at least one item per page to avoid infinite loop)
            if (currentPageVisualLines + itemVisualLines > CONTENT_LINES_PER_PAGE && !currentPage.isEmpty()) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                currentPageVisualLines = 0;
            }

            currentPage.add(item);
            currentPageVisualLines += itemVisualLines;
        }

        if (!currentPage.isEmpty()) {
            pages.add(currentPage);
        }
    }
}
