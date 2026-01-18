package sh.joey.mc.merit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Message formatting utilities for the merit system.
 */
public final class Messages {

    public static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Merit").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private Messages() {}

    public static void info(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GRAY)));
    }

    public static void info(Player player, Component message) {
        player.sendMessage(PREFIX.append(message));
    }

    public static void success(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    public static void error(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    /**
     * Get the color for a level based on tier.
     */
    public static NamedTextColor getLevelColor(int level) {
        if (level >= 100) return NamedTextColor.LIGHT_PURPLE;
        if (level >= 75) return NamedTextColor.AQUA;
        if (level >= 50) return NamedTextColor.GOLD;
        if (level >= 25) return NamedTextColor.YELLOW;
        if (level >= 10) return NamedTextColor.WHITE;
        return NamedTextColor.GRAY;
    }

    /**
     * Get the milestone symbol for a level.
     */
    public static String getMilestoneSymbol(int level) {
        if (level >= 500) return "\u272a"; // circled star
        if (level >= 250) return "\u2726"; // four-pointed star
        if (level >= 100) return "\u2605"; // star
        return "";
    }

    /**
     * Create a level prefix component for display in chat/nameplate.
     */
    public static Component getLevelPrefix(int level) {
        String symbol = getMilestoneSymbol(level);
        return Component.text(symbol + level + " ").color(getLevelColor(level));
    }

    /**
     * Create a clickable command component.
     */
    public static Component cmd(String command) {
        String displayText = command.startsWith("/") ? command : "/" + command;
        return Component.text(displayText)
                .color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand(displayText))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Click to run: ", NamedTextColor.GRAY)
                                .append(Component.text(displayText, NamedTextColor.WHITE))));
    }

    /**
     * Format a progress bar for display.
     */
    public static Component progressBar(long current, long max, int width) {
        double progress = max > 0 ? Math.min(1.0, (double) current / max) : 0;
        int filled = (int) (progress * width);
        int empty = width - filled;

        return Component.text("[")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text("#".repeat(filled)).color(NamedTextColor.GREEN))
                .append(Component.text("-".repeat(empty)).color(NamedTextColor.GRAY))
                .append(Component.text("]").color(NamedTextColor.DARK_GRAY));
    }

    /**
     * Format merit amount with color.
     */
    public static Component formatMerit(long amount) {
        return Component.text(formatNumber(amount) + " Merit").color(NamedTextColor.LIGHT_PURPLE);
    }

    /**
     * Format a number with commas for readability.
     */
    public static String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Format distance in meters or kilometers.
     */
    public static String formatDistance(double blocks) {
        if (blocks >= 1000) {
            return String.format("%.1fkm", blocks / 1000);
        }
        return String.format("%.0fm", blocks);
    }
}
