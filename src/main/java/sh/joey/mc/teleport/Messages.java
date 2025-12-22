package sh.joey.mc.teleport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * Utility for sending beautifully formatted messages to players.
 */
public final class Messages {
    private Messages() {}

    public static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("TP").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    public static void info(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GRAY)));
    }

    public static void success(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    public static void error(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    public static void warning(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GOLD)));
    }

    public static void teleportRequest(Player player, Player from) {
        Component acceptButton = Component.text("[Accept]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept").color(NamedTextColor.GREEN)));

        Component declineButton = Component.text("[Decline]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/decline"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to decline").color(NamedTextColor.RED)));

        player.sendMessage(PREFIX
                .append(Component.text(from.getName()).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.text(" wants to teleport to you!").color(NamedTextColor.GRAY)));
        player.sendMessage(PREFIX
                .append(acceptButton)
                .append(Component.text(" ").color(NamedTextColor.GRAY))
                .append(declineButton));
    }

    public static void countdown(Player player, int seconds) {
        String unit = seconds == 1 ? "second" : "seconds";
        player.sendMessage(PREFIX
                .append(Component.text("Teleporting in ").color(NamedTextColor.GRAY))
                .append(Component.text(seconds).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
                .append(Component.text(" " + unit + "... Don't move!").color(NamedTextColor.GRAY)));
    }

    public static void teleportCancelled(Player player) {
        player.sendMessage(PREFIX
                .append(Component.text("Teleport cancelled - you moved!").color(NamedTextColor.RED)));
    }
}
