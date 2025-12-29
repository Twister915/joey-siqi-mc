package sh.joey.mc.settings;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

/**
 * Message formatting utilities for the settings system.
 */
public final class Messages {

    public static final Component PREFIX = Component.text("[Settings] ", NamedTextColor.LIGHT_PURPLE);

    private Messages() {}

    public static void info(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GRAY)));
    }

    public static void success(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }

    public static void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    public static void warning(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.YELLOW)));
    }
}
