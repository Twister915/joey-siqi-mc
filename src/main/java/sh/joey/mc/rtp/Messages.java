package sh.joey.mc.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Message formatting utilities for the RTP system.
 */
public final class Messages {

    public static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("RTP").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private Messages() {}

    public static void info(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GRAY)));
    }

    public static void success(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    public static void error(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    public static void warn(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.YELLOW)));
    }
}
