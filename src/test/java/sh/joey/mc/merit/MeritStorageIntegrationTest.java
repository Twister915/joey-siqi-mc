package sh.joey.mc.merit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for MeritStorage.
 */
class MeritStorageIntegrationTest extends PostgresIntegrationTest {

    private MeritStorage meritStorage;

    @BeforeEach
    void setUpStorage() {
        meritStorage = new MeritStorage(storage);
    }

    // ===== PLAYER MERIT TESTS =====

    @Test
    @DisplayName("Get player merit returns empty when no record exists")
    void getPlayerMerit_noRecord_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<MeritStorage.PlayerMerit> merit = blockingGet(meritStorage.getPlayerMerit(playerId));

        assertThat(merit).isEmpty();
    }

    @Test
    @DisplayName("Get or create player merit creates new record")
    void getOrCreatePlayerMerit_createsNewRecord() {
        UUID playerId = UUID.randomUUID();

        MeritStorage.PlayerMerit merit = blockingGet(meritStorage.getOrCreatePlayerMerit(playerId));

        assertThat(merit.playerId()).isEqualTo(playerId);
        assertThat(merit.totalMerit()).isEqualTo(0);
        assertThat(merit.level()).isEqualTo(1);
    }

    @Test
    @DisplayName("Get or create player merit returns existing record")
    void getOrCreatePlayerMerit_returnsExisting() {
        UUID playerId = UUID.randomUUID();

        // Create and then add merit
        blockingGet(meritStorage.getOrCreatePlayerMerit(playerId));
        blockingAwait(meritStorage.addMerit(playerId, 1000, 5));

        MeritStorage.PlayerMerit merit = blockingGet(meritStorage.getOrCreatePlayerMerit(playerId));

        assertThat(merit.totalMerit()).isEqualTo(1000);
        assertThat(merit.level()).isEqualTo(5);
    }

    @Test
    @DisplayName("Add merit accumulates")
    void addMerit_accumulates() {
        UUID playerId = UUID.randomUUID();
        blockingGet(meritStorage.getOrCreatePlayerMerit(playerId));

        blockingAwait(meritStorage.addMerit(playerId, 100, 2));
        blockingAwait(meritStorage.addMerit(playerId, 200, 3));

        Optional<MeritStorage.PlayerMerit> merit = blockingGet(meritStorage.getPlayerMerit(playerId));
        assertThat(merit).isPresent();
        assertThat(merit.get().totalMerit()).isEqualTo(300);
        assertThat(merit.get().level()).isEqualTo(3);
    }

    @Test
    @DisplayName("Set merit overwrites total")
    void setMerit_overwritesTotal() {
        UUID playerId = UUID.randomUUID();
        blockingGet(meritStorage.getOrCreatePlayerMerit(playerId));
        blockingAwait(meritStorage.addMerit(playerId, 1000, 10));

        blockingAwait(meritStorage.setMerit(playerId, 500, 5));

        Optional<MeritStorage.PlayerMerit> merit = blockingGet(meritStorage.getPlayerMerit(playerId));
        assertThat(merit).isPresent();
        assertThat(merit.get().totalMerit()).isEqualTo(500);
        assertThat(merit.get().level()).isEqualTo(5);
    }

    @Test
    @DisplayName("Leaderboard returns players ordered by merit")
    void leaderboard_orderedByMerit() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        blockingAwait(meritStorage.setMerit(player1, 100, 2));
        blockingAwait(meritStorage.setMerit(player2, 500, 5));
        blockingAwait(meritStorage.setMerit(player3, 250, 3));

        List<MeritStorage.PlayerMerit> leaderboard = blockingList(meritStorage.getLeaderboard(10));

        assertThat(leaderboard).hasSize(3);
        assertThat(leaderboard.get(0).playerId()).isEqualTo(player2);
        assertThat(leaderboard.get(1).playerId()).isEqualTo(player3);
        assertThat(leaderboard.get(2).playerId()).isEqualTo(player1);
    }

    @Test
    @DisplayName("Leaderboard respects limit")
    void leaderboard_respectsLimit() {
        for (int i = 0; i < 15; i++) {
            blockingAwait(meritStorage.setMerit(UUID.randomUUID(), i * 100, i));
        }

        List<MeritStorage.PlayerMerit> leaderboard = blockingList(meritStorage.getLeaderboard(5));

        assertThat(leaderboard).hasSize(5);
    }

    // ===== PLAYER PROGRESS TESTS =====

    private static final int TEST_WEEK = 42;

    @Test
    @DisplayName("Update progress creates new entries")
    void updateProgress_createsNewEntries() {
        UUID playerId = UUID.randomUUID();
        Map<String, Long> deltas = Map.of(
                "blocks_mined:STONE", 100L,
                "blocks_mined:IRON_ORE", 25L
        );

        blockingAwait(meritStorage.updateProgress(playerId, TEST_WEEK, deltas));

        Map<String, Long> progress = blockingGet(meritStorage.getProgress(playerId, TEST_WEEK));
        assertThat(progress).containsEntry("blocks_mined:STONE", 100L);
        assertThat(progress).containsEntry("blocks_mined:IRON_ORE", 25L);
    }

    @Test
    @DisplayName("Update progress accumulates values")
    void updateProgress_accumulatesValues() {
        UUID playerId = UUID.randomUUID();

        blockingAwait(meritStorage.updateProgress(playerId, TEST_WEEK, Map.of("blocks_mined:STONE", 100L)));
        blockingAwait(meritStorage.updateProgress(playerId, TEST_WEEK, Map.of("blocks_mined:STONE", 50L)));

        Map<String, Long> progress = blockingGet(meritStorage.getProgress(playerId, TEST_WEEK));
        assertThat(progress).containsEntry("blocks_mined:STONE", 150L);
    }

    @Test
    @DisplayName("Get progress returns specific stat from map")
    void getProgress_returnsSpecificStat() {
        UUID playerId = UUID.randomUUID();
        blockingAwait(meritStorage.updateProgress(playerId, TEST_WEEK, Map.of(
                "blocks_mined:STONE", 100L,
                "blocks_mined:IRON_ORE", 25L
        )));

        Map<String, Long> progress = blockingGet(meritStorage.getProgress(playerId, TEST_WEEK));
        assertThat(progress.get("blocks_mined:STONE")).isEqualTo(100L);
    }

    @Test
    @DisplayName("Get progress returns empty map for unknown player")
    void getProgress_returnsEmptyForUnknown() {
        UUID playerId = UUID.randomUUID();

        Map<String, Long> progress = blockingGet(meritStorage.getProgress(playerId, TEST_WEEK));
        assertThat(progress).isEmpty();
    }

    // ===== WEEKLY CHALLENGE PROGRESS TESTS =====

    @Test
    @DisplayName("Get weekly challenge progress returns empty for new player")
    void getWeeklyChallengeProgress_newPlayer_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Map<String, MeritStorage.WeeklyChallengeProgress> progress =
                blockingGet(meritStorage.getWeeklyChallengeProgress(playerId, 42));

        assertThat(progress).isEmpty();
    }

    @Test
    @DisplayName("Update weekly challenge progress creates entry")
    void updateWeeklyChallengeProgress_createsEntry() {
        UUID playerId = UUID.randomUUID();
        int weekNumber = 42;

        blockingAwait(meritStorage.updateWeeklyChallengeProgress(playerId, weekNumber, "mining_stone", 150));

        Map<String, MeritStorage.WeeklyChallengeProgress> progress =
                blockingGet(meritStorage.getWeeklyChallengeProgress(playerId, weekNumber));

        assertThat(progress).containsKey("mining_stone");
        assertThat(progress.get("mining_stone").progress()).isEqualTo(150);
        assertThat(progress.get("mining_stone").completed()).isFalse();
    }

    @Test
    @DisplayName("Complete weekly challenge marks as completed")
    void completeWeeklyChallenge_marksAsCompleted() {
        UUID playerId = UUID.randomUUID();
        int weekNumber = 42;

        blockingAwait(meritStorage.completeWeeklyChallenge(playerId, weekNumber, "mining_stone", 500));

        Map<String, MeritStorage.WeeklyChallengeProgress> progress =
                blockingGet(meritStorage.getWeeklyChallengeProgress(playerId, weekNumber));

        assertThat(progress.get("mining_stone").completed()).isTrue();
        assertThat(progress.get("mining_stone").completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Record completion adds to history")
    void recordCompletion_addsToHistory() {
        UUID playerId = UUID.randomUUID();

        blockingAwait(meritStorage.recordCompletion(playerId, "mining_stone", 42, 50));
        blockingAwait(meritStorage.recordCompletion(playerId, "farming_wheat", 42, 75));

        List<MeritStorage.ChallengeCompletion> history =
                blockingList(meritStorage.getCompletionHistory(playerId, 10));

        assertThat(history).hasSize(2);
    }

    @Test
    @DisplayName("Weekly leaderboard shows merit earned this week")
    void weeklyLeaderboard_showsWeeklyMerit() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        int weekNumber = 42;

        blockingAwait(meritStorage.recordCompletion(player1, "challenge1", weekNumber, 100));
        blockingAwait(meritStorage.recordCompletion(player1, "challenge2", weekNumber, 50));
        blockingAwait(meritStorage.recordCompletion(player2, "challenge1", weekNumber, 200));

        List<MeritStorage.WeeklyLeaderboardEntry> leaderboard =
                blockingList(meritStorage.getWeeklyLeaderboard(weekNumber, 10));

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.get(0).playerId()).isEqualTo(player2);
        assertThat(leaderboard.get(0).weeklyMerit()).isEqualTo(200);
        assertThat(leaderboard.get(1).playerId()).isEqualTo(player1);
        assertThat(leaderboard.get(1).weeklyMerit()).isEqualTo(150);
    }

    // ===== WEEKLY ONLINE TIME TESTS =====

    @Test
    @DisplayName("Get weekly online time returns defaults for new player")
    void getWeeklyOnlineTime_newPlayer_returnsDefaults() {
        UUID playerId = UUID.randomUUID();

        MeritStorage.WeeklyOnlineTime time = blockingGet(meritStorage.getWeeklyOnlineTime(playerId, 42));

        assertThat(time.secondsOnline()).isEqualTo(0);
        assertThat(time.meritClaimed()).isEqualTo(0);
    }

    @Test
    @DisplayName("Update weekly online time accumulates")
    void updateWeeklyOnlineTime_accumulates() {
        UUID playerId = UUID.randomUUID();
        int weekNumber = 42;

        blockingAwait(meritStorage.updateWeeklyOnlineTime(playerId, weekNumber, 600));
        blockingAwait(meritStorage.updateWeeklyOnlineTime(playerId, weekNumber, 400));

        MeritStorage.WeeklyOnlineTime time = blockingGet(meritStorage.getWeeklyOnlineTime(playerId, weekNumber));

        assertThat(time.secondsOnline()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Claim online time merit updates claimed amount")
    void claimOnlineTimeMerit_updatesClaimed() {
        UUID playerId = UUID.randomUUID();
        int weekNumber = 42;

        blockingAwait(meritStorage.updateWeeklyOnlineTime(playerId, weekNumber, 3600));
        blockingAwait(meritStorage.claimOnlineTimeMerit(playerId, weekNumber, 20));

        MeritStorage.WeeklyOnlineTime time = blockingGet(meritStorage.getWeeklyOnlineTime(playerId, weekNumber));

        assertThat(time.secondsOnline()).isEqualTo(3600);
        assertThat(time.meritClaimed()).isEqualTo(20);
    }

    @Test
    @DisplayName("Different weeks have separate online time")
    void differentWeeks_separateOnlineTime() {
        UUID playerId = UUID.randomUUID();

        blockingAwait(meritStorage.updateWeeklyOnlineTime(playerId, 41, 1000));
        blockingAwait(meritStorage.updateWeeklyOnlineTime(playerId, 42, 2000));

        MeritStorage.WeeklyOnlineTime week41 = blockingGet(meritStorage.getWeeklyOnlineTime(playerId, 41));
        MeritStorage.WeeklyOnlineTime week42 = blockingGet(meritStorage.getWeeklyOnlineTime(playerId, 42));

        assertThat(week41.secondsOnline()).isEqualTo(1000);
        assertThat(week42.secondsOnline()).isEqualTo(2000);
    }
}
