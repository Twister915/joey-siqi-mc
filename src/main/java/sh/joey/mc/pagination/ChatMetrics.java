package sh.joey.mc.pagination;

/**
 * Static utilities for calculating Minecraft chat pixel widths and visual line counts.
 * Based on Minecraft 1.19+ bitmap font glyph widths.
 */
public final class ChatMetrics {

    /**
     * Minecraft default chat width in pixels.
     */
    public static final int CHAT_WIDTH_PIXELS = 320;

    /**
     * Number of visible lines in the Minecraft chat window.
     */
    public static final int CHAT_VISIBLE_LINES = 20;

    private ChatMetrics() {}

    /**
     * Returns the pixel width of a character in Minecraft's default font.
     * Each character also has 1px spacing after it (included in these values).
     */
    public static int getCharWidth(char c) {
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
    public static int calculatePixelWidth(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            width += getCharWidth(c);
        }
        return width;
    }

    /**
     * Calculates how many visual lines a string will take, accounting for word wrap.
     */
    public static int calculateVisualLines(String text) {
        int width = calculatePixelWidth(text);
        return Math.max(1, (int) Math.ceil((double) width / CHAT_WIDTH_PIXELS));
    }
}
