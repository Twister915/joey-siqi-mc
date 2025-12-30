package sh.joey.mc.steve;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Message utilities for Steve chatbot.
 */
public final class Messages {

    public static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Steve").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private Messages() {}

    public static void info(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GRAY)));
    }

    public static void error(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }
}
