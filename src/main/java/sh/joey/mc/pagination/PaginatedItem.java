package sh.joey.mc.pagination;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Represents an item that can be displayed in a paginated chat view.
 * Each item knows how many visual chat lines it will take when rendered.
 */
public interface PaginatedItem {

    /**
     * The Adventure Component to render in chat.
     */
    Component render();

    /**
     * How many visual chat lines this item takes (accounting for word wrap).
     */
    int visualLines();

    /**
     * Creates a simple item that takes exactly one line.
     */
    static PaginatedItem simple(Component component) {
        return new SimpleLine(component);
    }

    /**
     * Creates a simple text item that takes exactly one line.
     */
    static PaginatedItem simple(String text, NamedTextColor color) {
        return new SimpleLine(Component.text(text).color(color));
    }

    /**
     * Creates an item that may wrap across multiple lines.
     * The plainText is used to calculate the visual line count.
     */
    static PaginatedItem wrapping(Component component, String plainText) {
        int lines = ChatMetrics.calculateVisualLines(plainText);
        return new WrappingLine(component, lines);
    }

    /**
     * Creates an empty line (blank separator).
     */
    static PaginatedItem empty() {
        return SimpleLine.EMPTY;
    }

    /**
     * Creates a section header (bold, colored).
     */
    static PaginatedItem header(String text) {
        return new SimpleLine(
            Component.text("▸ " + text)
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
        );
    }

    // Implementation records

    record SimpleLine(Component component) implements PaginatedItem {
        static final SimpleLine EMPTY = new SimpleLine(Component.empty());

        @Override
        public Component render() {
            return component;
        }

        @Override
        public int visualLines() {
            return 1;
        }
    }

    record WrappingLine(Component component, int lines) implements PaginatedItem {
        @Override
        public Component render() {
            return component;
        }

        @Override
        public int visualLines() {
            return lines;
        }
    }
}
