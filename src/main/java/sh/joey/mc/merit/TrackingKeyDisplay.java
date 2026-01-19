package sh.joey.mc.merit;

import sh.joey.mc.merit.challenge.Challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for converting tracking keys to human-readable display names.
 */
public final class TrackingKeyDisplay {

    private TrackingKeyDisplay() {}

    // Special display names for distance movement types
    private static final Map<String, String> DISTANCE_DISPLAY_NAMES = Map.ofEntries(
            Map.entry("WALK", "Walking"),
            Map.entry("SPRINT", "Sprinting"),
            Map.entry("SWIM", "Swimming"),
            Map.entry("CLIMB", "Climbing"),
            Map.entry("ELYTRA", "Elytra"),
            Map.entry("BOAT", "Boat"),
            Map.entry("HORSE", "Horse"),
            Map.entry("MINECART", "Minecart"),
            Map.entry("PIG", "Pig")
    );

    // Special display names for weapon types
    private static final Map<String, String> WEAPON_DISPLAY_NAMES = Map.of(
            "SWORD", "Sword",
            "BOW", "Bow",
            "CROSSBOW", "Crossbow",
            "AXE", "Axe",
            "TRIDENT", "Trident"
    );

    // Tracking key prefixes and their category labels
    private static final Map<String, String> PREFIX_CATEGORIES = Map.ofEntries(
            Map.entry("blocks_mined:", "block"),
            Map.entry("blocks_placed:", "block"),
            Map.entry("harvested:", "crop"),
            Map.entry("kills:", "mob"),
            Map.entry("pvp_kills:", "weapon"),
            Map.entry("distance:", "travel"),
            Map.entry("smelted:", "item"),
            Map.entry("crafted:", "item"),
            Map.entry("damage_dealt:", "target")
    );

    /**
     * Convert a tracking key to a human-readable display name.
     * <p>
     * Examples:
     * - "blocks_mined:STONE" → "Stone"
     * - "pvp_kills:SWORD" → "Sword"
     * - "distance:WALK" → "Walking"
     * - "blocks_placed:ANY" → "Any block"
     * - "pvp_kills" → "Any weapon"
     */
    public static String toDisplayName(String trackingKey) {
        // Handle keys without a colon (simple counters like "pvp_kills", "xp_gained")
        if (!trackingKey.contains(":")) {
            return getSimpleKeyName(trackingKey);
        }

        int colonIndex = trackingKey.indexOf(':');
        String prefix = trackingKey.substring(0, colonIndex + 1);
        String suffix = trackingKey.substring(colonIndex + 1);

        // Handle :ANY suffix
        if ("ANY".equals(suffix)) {
            String category = PREFIX_CATEGORIES.getOrDefault(prefix, "type");
            return "Any " + category;
        }

        // Check for special display names based on prefix type
        if (prefix.equals("distance:")) {
            return DISTANCE_DISPLAY_NAMES.getOrDefault(suffix, toTitleCase(suffix));
        }

        if (prefix.equals("pvp_kills:")) {
            return WEAPON_DISPLAY_NAMES.getOrDefault(suffix, toTitleCase(suffix));
        }

        // Default: convert material/entity name to title case
        return toTitleCase(suffix);
    }

    /**
     * Get a simplified name for tracking keys that don't have a colon.
     */
    private static String getSimpleKeyName(String trackingKey) {
        return switch (trackingKey) {
            case "pvp_kills" -> "Any weapon";
            case "pvp_wins" -> "PvP wins";
            case "xp_gained" -> "XP gained";
            case "levels_gained" -> "Levels gained";
            case "items_enchanted" -> "Items enchanted";
            case "enchants_level_30" -> "Level 30 enchants";
            case "potions_brewed" -> "Potions brewed";
            case "villager_trades" -> "Villager trades";
            case "advancements_earned" -> "Advancements";
            case "enchanted_books_created" -> "Enchanted books";
            case "anvil_uses" -> "Anvil uses";
            case "items_repaired" -> "Repairs";
            case "grindstone_uses" -> "Grindstone uses";
            case "smithing_uses" -> "Smithing uses";
            case "items_crafted" -> "Items crafted";
            case "biomes_visited" -> "Biomes visited";
            case "structures_found" -> "Structures found";
            case "loot_chests_opened" -> "Loot chests";
            case "sunrises_witnessed" -> "Sunrises";
            case "days_survived" -> "Days survived";
            case "nights_survived" -> "Nights survived";
            default -> toTitleCase(trackingKey.replace('_', ' '));
        };
    }

    /**
     * Convert SCREAMING_SNAKE_CASE or snake_case to Title Case.
     * Handles common Minecraft naming patterns.
     */
    private static String toTitleCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Replace underscores with spaces
        String[] words = input.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) continue;

            if (i > 0) {
                result.append(" ");
            }

            // Capitalize first letter
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.toString();
    }

    /**
     * Get all display names for a challenge's tracking keys.
     *
     * @param challenge The challenge to get display names for
     * @return List of human-readable display names
     */
    public static List<String> getDisplayNames(Challenge challenge) {
        List<String> names = new ArrayList<>();
        for (String key : challenge.trackingKeys()) {
            names.add(toDisplayName(key));
        }
        return names;
    }

    /**
     * Get the prefix (category) from a tracking key.
     * <p>
     * Examples:
     * - "blocks_mined:STONE" → "blocks_mined"
     * - "pvp_kills" → "pvp_kills"
     */
    public static String getPrefix(String trackingKey) {
        int colonIndex = trackingKey.indexOf(':');
        return colonIndex >= 0 ? trackingKey.substring(0, colonIndex) : trackingKey;
    }

    /**
     * Get the suffix (specific type) from a tracking key.
     * <p>
     * Examples:
     * - "blocks_mined:STONE" → "STONE"
     * - "pvp_kills" → null
     */
    public static String getSuffix(String trackingKey) {
        int colonIndex = trackingKey.indexOf(':');
        return colonIndex >= 0 ? trackingKey.substring(colonIndex + 1) : null;
    }
}
