package sh.joey.mc.merit.challenge;

import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Deterministically assigns weekly challenges to players.
 * Uses a hash of playerId + weekNumber to ensure the same player
 * gets the same challenges for a given week, without needing storage.
 */
public final class ChallengeAssigner {

    private static final LocalDate EPOCH = LocalDate.of(2024, 1, 1);

    private final ChallengeRegistry registry;
    private final int challengeCount;

    public ChallengeAssigner(ChallengeRegistry registry, int challengeCount) {
        this.registry = registry;
        this.challengeCount = challengeCount;
    }

    /**
     * Get the current week number since epoch.
     */
    public int getCurrentWeekNumber() {
        long epochDay = EPOCH.toEpochDay();
        long today = LocalDate.now().toEpochDay();
        return (int) ((today - epochDay) / 7);
    }

    /**
     * Get the weekly challenges assigned to a player for the current week.
     */
    public List<Challenge> getWeeklyChallenges(UUID playerId) {
        return getWeeklyChallenges(playerId, getCurrentWeekNumber());
    }

    /**
     * Get the weekly challenges assigned to a player for a specific week.
     */
    public List<Challenge> getWeeklyChallenges(UUID playerId, int weekNumber) {
        long seed = playerId.getLeastSignificantBits() ^
                    playerId.getMostSignificantBits() ^
                    (weekNumber * 31L);
        Random random = new Random(seed);

        // Get all categories and shuffle them
        List<ChallengeCategory> categories = new ArrayList<>(List.of(ChallengeCategory.values()));
        Collections.shuffle(categories, random);

        // Select one challenge from each of the first N categories
        List<Challenge> assigned = new ArrayList<>();
        for (int i = 0; i < Math.min(challengeCount, categories.size()); i++) {
            ChallengeCategory category = categories.get(i);
            List<Challenge> categoryChallenge = registry.getByCategory(category);
            if (!categoryChallenge.isEmpty()) {
                Challenge selected = categoryChallenge.get(random.nextInt(categoryChallenge.size()));
                assigned.add(selected);
            }
        }

        return assigned;
    }

    /**
     * Check if a challenge is assigned to a player this week.
     */
    public boolean isChallengeAssigned(UUID playerId, Challenge challenge) {
        return getWeeklyChallenges(playerId).contains(challenge);
    }

    /**
     * Get challenges for a specific category assigned to a player this week.
     */
    public List<Challenge> getAssignedByCategory(UUID playerId, ChallengeCategory category) {
        return getWeeklyChallenges(playerId).stream()
                .filter(c -> c.category() == category)
                .toList();
    }
}
