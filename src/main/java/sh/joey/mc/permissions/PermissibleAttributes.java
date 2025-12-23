package sh.joey.mc.permissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * Display attributes (prefixes and suffixes) for a permissible entity.
 * Supports both MiniMessage and legacy color code formatting.
 */
public record PermissibleAttributes(
        @Nullable String chatPrefix,
        @Nullable String chatSuffix,
        @Nullable String nameplatePrefix,
        @Nullable String nameplateSuffix
) {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacyAmpersand();

    public static final PermissibleAttributes EMPTY = new PermissibleAttributes(null, null, null, null);

    /**
     * Merges this attributes with another, preferring non-null values from this.
     * Used to layer player overrides on top of group defaults.
     */
    public PermissibleAttributes merge(PermissibleAttributes fallback) {
        return new PermissibleAttributes(
                chatPrefix != null ? chatPrefix : fallback.chatPrefix,
                chatSuffix != null ? chatSuffix : fallback.chatSuffix,
                nameplatePrefix != null ? nameplatePrefix : fallback.nameplatePrefix,
                nameplateSuffix != null ? nameplateSuffix : fallback.nameplateSuffix
        );
    }

    /**
     * Returns true if all attributes are null (empty/unset).
     */
    public boolean isEmpty() {
        return chatPrefix == null && chatSuffix == null
                && nameplatePrefix == null && nameplateSuffix == null;
    }

    /**
     * Returns the chat prefix as an Adventure Component.
     */
    public Component chatPrefixComponent() {
        return chatPrefix != null ? parseFormatted(chatPrefix) : Component.empty();
    }

    /**
     * Returns the chat suffix as an Adventure Component.
     */
    public Component chatSuffixComponent() {
        return chatSuffix != null ? parseFormatted(chatSuffix) : Component.empty();
    }

    /**
     * Returns the nameplate prefix as an Adventure Component.
     */
    public Component nameplatePrefixComponent() {
        return nameplatePrefix != null ? parseFormatted(nameplatePrefix) : Component.empty();
    }

    /**
     * Returns the nameplate suffix as an Adventure Component.
     */
    public Component nameplateSuffixComponent() {
        return nameplateSuffix != null ? parseFormatted(nameplateSuffix) : Component.empty();
    }

    /**
     * Parse a formatted string, auto-detecting MiniMessage vs legacy format.
     * <p>
     * MiniMessage format: contains {@code <} (e.g., {@code <green>Goat</green>})
     * Legacy format: contains {@code &} (e.g., {@code &aGoat})
     */
    public static Component parseFormatted(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        // Detect format: if it contains '<' followed by a letter, assume MiniMessage
        if (input.contains("<") && input.matches(".*<[a-zA-Z].*")) {
            return MINI_MESSAGE.deserialize(input);
        }

        // Otherwise, assume legacy ampersand format
        return LEGACY_SERIALIZER.deserialize(input);
    }

    /**
     * Converts an Adventure Component to a legacy string for Bukkit APIs that need it.
     * Used for Scoreboard team prefix/suffix.
     */
    public static String toLegacy(Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }
}
