package sh.joey.mc.whitelist;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Message formatting for the whitelist system.
 */
public final class WhitelistMessages {

    public static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Whitelist").color(NamedTextColor.WHITE))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private WhitelistMessages() {}

    /**
     * Format the kick message shown to non-whitelisted players.
     */
    public static Component formatKickMessage(String playerName) {
        return Component.text("You are not whitelisted on this server.")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.newline())
                .append(Component.newline())
                .append(Component.text("Ask a member to invite you using ")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, false))
                .append(Component.text("/invite " + playerName)
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, false));
    }

    /**
     * Format the broadcast message when a non-whitelisted player tries to join.
     */
    public static Component formatJoinAttemptBroadcast(String playerName) {
        Component inviteButton = Component.text("[Invite]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/invite " + playerName))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Click to whitelist " + playerName).color(NamedTextColor.GREEN)));

        Component ignoreButton = Component.text("[Ignore]")
                .color(NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(
                        Component.text("Dismiss this notification").color(NamedTextColor.GRAY)));

        return PREFIX
                .append(Component.text(playerName).color(NamedTextColor.YELLOW))
                .append(Component.text(" is trying to join!").color(NamedTextColor.GRAY))
                .append(Component.text(" "))
                .append(inviteButton)
                .append(Component.text(" "))
                .append(ignoreButton);
    }
}
