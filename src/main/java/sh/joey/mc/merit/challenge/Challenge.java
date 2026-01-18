package sh.joey.mc.merit.challenge;

import java.util.Set;

/**
 * A challenge definition.
 *
 * @param id Unique identifier (e.g., "mining_stone_breaker")
 * @param name Display name (e.g., "Stone Breaker")
 * @param description Description of what to do
 * @param category The challenge category
 * @param target Target value to complete the challenge
 * @param meritReward Merit earned on completion
 * @param trackingKeys The stat keys that contribute to this challenge
 */
public record Challenge(
        String id,
        String name,
        String description,
        ChallengeCategory category,
        long target,
        int meritReward,
        Set<String> trackingKeys
) {
    /**
     * Create a challenge with a single tracking key.
     */
    public static Challenge of(String id, String name, String description, ChallengeCategory category,
                               long target, int meritReward, String trackingKey) {
        return new Challenge(id, name, description, category, target, meritReward, Set.of(trackingKey));
    }

    /**
     * Create a challenge with multiple tracking keys.
     */
    public static Challenge of(String id, String name, String description, ChallengeCategory category,
                               long target, int meritReward, String... trackingKeys) {
        return new Challenge(id, name, description, category, target, meritReward, Set.of(trackingKeys));
    }
}
