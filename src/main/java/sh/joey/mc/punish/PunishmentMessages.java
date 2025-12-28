package sh.joey.mc.punish;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Formats punishment-related messages for display to players.
 */
public final class PunishmentMessages {

    /**
     * Prefix for punishment command feedback.
     */
    public static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Punish").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault());

    private PunishmentMessages() {
    }

    /**
     * Format kick message for a banned player.
     */
    public static Component formatBanKickMessage(Punishment ban) {
        var builder = Component.text();

        // Header
        if (ban.isPermanent()) {
            builder.append(Component.text("You have been banned from this server")
                    .color(NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD));
        } else {
            builder.append(Component.text("You have been temporarily banned from this server")
                    .color(NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD));
        }

        builder.append(Component.newline()).append(Component.newline());

        // Reason
        if (ban.reason() != null && !ban.reason().isBlank()) {
            builder.append(Component.text("Reason: ").color(NamedTextColor.GRAY))
                    .append(Component.text(ban.reason()).color(NamedTextColor.WHITE))
                    .append(Component.newline());
        }

        // Duration/Expiration
        if (ban.isPermanent()) {
            builder.append(Component.text("Duration: ").color(NamedTextColor.GRAY))
                    .append(Component.text("Permanent").color(NamedTextColor.RED));
        } else {
            builder.append(Component.text("Expires: ").color(NamedTextColor.GRAY))
                    .append(Component.text(formatExpiration(ban.expiresAt())).color(NamedTextColor.YELLOW));
        }

        return builder.build();
    }

    /**
     * Format kick message for an IP banned player.
     */
    public static Component formatIpBanKickMessage(Punishment ipBan) {
        var builder = Component.text();

        // Header
        builder.append(Component.text("Your IP address has been banned from this server")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD));

        builder.append(Component.newline()).append(Component.newline());

        // Reason
        if (ipBan.reason() != null && !ipBan.reason().isBlank()) {
            builder.append(Component.text("Reason: ").color(NamedTextColor.GRAY))
                    .append(Component.text(ipBan.reason()).color(NamedTextColor.WHITE));
        }

        return builder.build();
    }

    /**
     * Format notification shown to muted player when they try to chat.
     */
    public static Component formatMuteNotification(Punishment mute) {
        Component prefix = Component.text("[")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text("Muted").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

        if (mute.isPermanent()) {
            return prefix.append(Component.text("You are permanently muted.").color(NamedTextColor.GRAY));
        } else {
            Duration remaining = Duration.between(Instant.now(), mute.expiresAt());
            String remainingStr = DurationParser.formatHumanReadable(remaining);
            return prefix.append(Component.text("You are muted for " + remainingStr + ".").color(NamedTextColor.GRAY));
        }
    }

    /**
     * Format instant kick message (not a ban).
     */
    public static Component formatKickMessage(@Nullable String reason) {
        var builder = Component.text();

        builder.append(Component.text("You have been kicked from this server")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD));

        if (reason != null && !reason.isBlank()) {
            builder.append(Component.newline())
                    .append(Component.newline())
                    .append(Component.text("Reason: ").color(NamedTextColor.GRAY))
                    .append(Component.text(reason).color(NamedTextColor.WHITE));
        }

        return builder.build();
    }

    /**
     * Format a warning message shown to the warned player.
     */
    public static Component formatWarningMessage(String reason) {
        return Component.text("[")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text("Warning").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text("You have been warned: ").color(NamedTextColor.GRAY))
                .append(Component.text(reason).color(NamedTextColor.WHITE));
    }

    private static String formatExpiration(Instant expiresAt) {
        if (expiresAt == null) {
            return "Never";
        }

        Duration remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative()) {
            return "Expired";
        }

        // For short durations (< 24h), show relative time
        if (remaining.toHours() < 24) {
            return "in " + DurationParser.formatHumanReadable(remaining);
        }

        // For longer durations, show absolute date
        return DATE_FORMATTER.format(expiresAt);
    }
}
