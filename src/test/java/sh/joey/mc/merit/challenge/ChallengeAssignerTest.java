package sh.joey.mc.merit.challenge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ChallengeAssigner.
 */
class ChallengeAssignerTest {

    private ChallengeRegistry registry;
    private ChallengeAssigner assigner;

    @BeforeEach
    void setUp() {
        registry = new ChallengeRegistry();
        assigner = new ChallengeAssigner(registry, 8);
    }

    @Test
    @DisplayName("Returns correct number of challenges")
    void returnsCorrectNumberOfChallenges() {
        UUID playerId = UUID.randomUUID();
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);
        assertThat(challenges).hasSize(8);
    }

    @Test
    @DisplayName("Same player same week gets same challenges")
    void samePlayerSameWeek_sameChallenge() {
        UUID playerId = UUID.randomUUID();
        int weekNumber = assigner.getCurrentWeekNumber();

        List<Challenge> first = assigner.getWeeklyChallenges(playerId, weekNumber);
        List<Challenge> second = assigner.getWeeklyChallenges(playerId, weekNumber);

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("Same player different week gets different challenges")
    void samePlayerDifferentWeek_differentChallenges() {
        UUID playerId = UUID.randomUUID();

        List<Challenge> week1 = assigner.getWeeklyChallenges(playerId, 1);
        List<Challenge> week2 = assigner.getWeeklyChallenges(playerId, 2);

        // Should not be exactly equal (highly unlikely due to random shuffle)
        assertThat(week1).isNotEqualTo(week2);
    }

    @Test
    @DisplayName("Different players same week get different challenges")
    void differentPlayersSameWeek_differentChallenges() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        int weekNumber = assigner.getCurrentWeekNumber();

        List<Challenge> challenges1 = assigner.getWeeklyChallenges(player1, weekNumber);
        List<Challenge> challenges2 = assigner.getWeeklyChallenges(player2, weekNumber);

        // Should not be exactly equal (highly unlikely due to random seed)
        assertThat(challenges1).isNotEqualTo(challenges2);
    }

    @Test
    @DisplayName("Challenges come from different categories")
    void challengesFromDifferentCategories() {
        UUID playerId = UUID.randomUUID();
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);

        Set<ChallengeCategory> categories = new HashSet<>();
        for (Challenge challenge : challenges) {
            categories.add(challenge.category());
        }

        // With 8 challenges and 10 categories, we should have 8 different categories
        assertThat(categories).hasSize(8);
    }

    @Test
    @DisplayName("isChallengeAssigned returns true for assigned challenges")
    void isChallengeAssigned_returnsTrueForAssigned() {
        UUID playerId = UUID.randomUUID();
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);

        for (Challenge challenge : challenges) {
            assertThat(assigner.isChallengeAssigned(playerId, challenge)).isTrue();
        }
    }

    @Test
    @DisplayName("Deterministic across multiple calls")
    void deterministicAcrossMultipleCalls() {
        UUID playerId = UUID.randomUUID();
        int weekNumber = 42;

        // Call many times and verify always same result
        List<Challenge> baseline = assigner.getWeeklyChallenges(playerId, weekNumber);
        for (int i = 0; i < 10; i++) {
            List<Challenge> result = assigner.getWeeklyChallenges(playerId, weekNumber);
            assertThat(result).isEqualTo(baseline);
        }
    }

    @Test
    @DisplayName("Week number changes with time")
    void weekNumberChangesWithTime() {
        int currentWeek = assigner.getCurrentWeekNumber();
        // Week number should be positive (we're past 2024)
        assertThat(currentWeek).isPositive();
    }

    @Test
    @DisplayName("Get assigned by category returns matching challenges")
    void getAssignedByCategory_returnsMatchingChallenges() {
        UUID playerId = UUID.randomUUID();
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);

        // Find a category that has an assigned challenge
        ChallengeCategory testCategory = challenges.get(0).category();

        List<Challenge> byCategory = assigner.getAssignedByCategory(playerId, testCategory);
        assertThat(byCategory).hasSize(1);
        assertThat(byCategory.get(0).category()).isEqualTo(testCategory);
    }

    @Test
    @DisplayName("All challenges have valid IDs")
    void allChallengesHaveValidIds() {
        UUID playerId = UUID.randomUUID();
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);

        for (Challenge challenge : challenges) {
            assertThat(challenge.id()).isNotNull();
            assertThat(challenge.id()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("All challenges have valid rewards")
    void allChallengesHaveValidRewards() {
        UUID playerId = UUID.randomUUID();
        List<Challenge> challenges = assigner.getWeeklyChallenges(playerId);

        for (Challenge challenge : challenges) {
            assertThat(challenge.meritReward()).isPositive();
            assertThat(challenge.target()).isPositive();
        }
    }
}
