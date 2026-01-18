package sh.joey.mc.steve;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for SteveStorage.
 * Tests AI chat history and cooldown tracking.
 */
class SteveStorageIntegrationTest extends PostgresIntegrationTest {

    private SteveStorage steveStorage;

    @BeforeEach
    void setUpStorage() {
        steveStorage = new SteveStorage(storage);
    }

    @Nested
    @DisplayName("History Operations")
    class HistoryTests {

        @Test
        @DisplayName("Save and get history")
        void saveAndGetHistory() {
            UUID playerId = UUID.randomUUID();
            SteveAnswer answer = new SteveAnswer("This is the answer.", List.of(), 0.05);

            blockingAwait(steveStorage.saveHistory(playerId, "What is Minecraft?", answer, "claude-3", 0));

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));
            assertThat(history).hasSize(1);
            assertThat(history.get(0).playerId()).isEqualTo(playerId);
            assertThat(history.get(0).question()).isEqualTo("What is Minecraft?");
            assertThat(history.get(0).answer()).isEqualTo("This is the answer.");
            assertThat(history.get(0).askedAt()).isNotNull();
        }

        @Test
        @DisplayName("Get history returns empty when no history")
        void getHistory_noHistory_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));

            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("History is ordered by asked_at descending")
        void history_orderedDescending() throws InterruptedException {
            UUID playerId = UUID.randomUUID();

            blockingAwait(steveStorage.saveHistory(playerId, "Question 1", new SteveAnswer("Answer 1", List.of(), 0), "claude-3", 0));
            Thread.sleep(10);
            blockingAwait(steveStorage.saveHistory(playerId, "Question 2", new SteveAnswer("Answer 2", List.of(), 0), "claude-3", 1));
            Thread.sleep(10);
            blockingAwait(steveStorage.saveHistory(playerId, "Question 3", new SteveAnswer("Answer 3", List.of(), 0), "claude-3", 2));

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));

            assertThat(history).hasSize(3);
            assertThat(history.get(0).question()).isEqualTo("Question 3"); // Most recent first
            assertThat(history.get(1).question()).isEqualTo("Question 2");
            assertThat(history.get(2).question()).isEqualTo("Question 1");
        }

        @Test
        @DisplayName("Get history count returns correct count")
        void getHistoryCount_returnsCorrectCount() {
            UUID playerId = UUID.randomUUID();

            for (int i = 0; i < 5; i++) {
                blockingAwait(steveStorage.saveHistory(playerId, "Question " + i, new SteveAnswer("Answer " + i, List.of(), 0), "claude-3", i));
            }

            int count = blockingGet(steveStorage.getHistoryCount(playerId));

            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("Get history count returns zero when no history")
        void getHistoryCount_noHistory_returnsZero() {
            UUID playerId = UUID.randomUUID();

            int count = blockingGet(steveStorage.getHistoryCount(playerId));

            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Citations JSON")
    class CitationsTests {

        @Test
        @DisplayName("Citations are saved and retrieved correctly")
        void citations_savedAndRetrieved() {
            UUID playerId = UUID.randomUUID();
            List<SteveAnswer.Citation> citations = List.of(
                    new SteveAnswer.Citation("Minecraft Wiki", "https://minecraft.wiki/page1"),
                    new SteveAnswer.Citation("Official Docs", "https://minecraft.net/docs")
            );
            SteveAnswer answer = new SteveAnswer("Answer with citations.", citations, 0.1);

            blockingAwait(steveStorage.saveHistory(playerId, "Question?", answer, "claude-3", 0));

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));
            assertThat(history).hasSize(1);
            assertThat(history.get(0).citations()).hasSize(2);
            assertThat(history.get(0).citations()).extracting(SteveAnswer.Citation::title)
                    .containsExactlyInAnyOrder("Minecraft Wiki", "Official Docs");
            assertThat(history.get(0).citations()).extracting(SteveAnswer.Citation::url)
                    .containsExactlyInAnyOrder("https://minecraft.wiki/page1", "https://minecraft.net/docs");
        }

        @Test
        @DisplayName("Empty citations list is handled correctly")
        void emptyCitations_handledCorrectly() {
            UUID playerId = UUID.randomUUID();
            SteveAnswer answer = new SteveAnswer("Answer without citations.", List.of(), 0);

            blockingAwait(steveStorage.saveHistory(playerId, "Question?", answer, "claude-3", 0));

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));
            assertThat(history).hasSize(1);
            assertThat(history.get(0).citations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cost Tracking")
    class CostTests {

        @Test
        @DisplayName("Cost is saved and retrieved correctly")
        void cost_savedAndRetrieved() {
            UUID playerId = UUID.randomUUID();
            SteveAnswer answer = new SteveAnswer("Answer.", List.of(), 0.15);

            blockingAwait(steveStorage.saveHistory(playerId, "Question?", answer, "claude-3", 0));

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));
            assertThat(history).hasSize(1);
            assertThat(history.get(0).costCents()).hasValue(0.15);
        }

        @Test
        @DisplayName("Zero cost is stored as null and retrieved as empty")
        void zeroCost_storedAsNull() {
            UUID playerId = UUID.randomUUID();
            SteveAnswer answer = new SteveAnswer("Answer.", List.of(), 0);

            blockingAwait(steveStorage.saveHistory(playerId, "Question?", answer, "claude-3", 0));

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));
            assertThat(history).hasSize(1);
            assertThat(history.get(0).costCents()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Context Count")
    class ContextCountTests {

        @Test
        @DisplayName("Context count is saved and retrieved correctly")
        void contextCount_savedAndRetrieved() {
            UUID playerId = UUID.randomUUID();
            SteveAnswer answer = new SteveAnswer("Answer.", List.of(), 0);

            blockingAwait(steveStorage.saveHistory(playerId, "Question?", answer, "claude-3", 5));

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));
            assertThat(history).hasSize(1);
            assertThat(history.get(0).contextCount()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Model Name")
    class ModelNameTests {

        @Test
        @DisplayName("Model name is saved and retrieved correctly")
        void modelName_savedAndRetrieved() {
            UUID playerId = UUID.randomUUID();
            SteveAnswer answer = new SteveAnswer("Answer.", List.of(), 0);

            blockingAwait(steveStorage.saveHistory(playerId, "Question?", answer, "claude-3-opus", 0));

            List<SteveHistoryEntry> history = blockingList(steveStorage.getHistory(playerId));
            assertThat(history).hasSize(1);
            assertThat(history.get(0).modelName()).hasValue("claude-3-opus");
        }
    }

    @Nested
    @DisplayName("Cooldown Operations")
    class CooldownTests {

        @Test
        @DisplayName("Record usage and get last Steve time")
        void recordUsageAndGetLastTime() {
            UUID playerId = UUID.randomUUID();
            Instant before = Instant.now().minusSeconds(1);

            blockingAwait(steveStorage.recordSteveUsage(playerId));

            Optional<Instant> lastTime = blockingGet(steveStorage.getLastSteveTime(playerId));
            assertThat(lastTime).isPresent();
            assertThat(lastTime.get()).isAfter(before);
        }

        @Test
        @DisplayName("Get last Steve time returns empty when no usage")
        void getLastSteveTime_noUsage_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            Optional<Instant> lastTime = blockingGet(steveStorage.getLastSteveTime(playerId));

            assertThat(lastTime).isEmpty();
        }

        @Test
        @DisplayName("Record usage upserts on conflict")
        void recordUsage_upsertsOnConflict() throws SQLException, InterruptedException {
            UUID playerId = UUID.randomUUID();

            blockingAwait(steveStorage.recordSteveUsage(playerId));
            Thread.sleep(50);
            blockingAwait(steveStorage.recordSteveUsage(playerId));

            // Should only have one row
            int count = countRows("SELECT COUNT(*) FROM steve_cooldowns WHERE player_id = '" + playerId + "'");
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Get active cooldowns returns players within duration")
        void getActiveCooldowns_returnsPlayersWithinDuration() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            blockingAwait(steveStorage.recordSteveUsage(player1));
            blockingAwait(steveStorage.recordSteveUsage(player2));

            List<SteveStorage.CooldownEntry> active = blockingList(
                    steveStorage.getActiveCooldowns(Duration.ofMinutes(5))
            );

            assertThat(active).hasSize(2);
            assertThat(active).extracting(SteveStorage.CooldownEntry::playerId)
                    .containsExactlyInAnyOrder(player1, player2);
        }

        @Test
        @DisplayName("Get active cooldowns excludes expired cooldowns")
        void getActiveCooldowns_excludesExpired() throws SQLException {
            UUID playerId = UUID.randomUUID();

            // Insert a cooldown from 10 minutes ago (expired for 5 minute duration)
            try (var conn = database.getConnection();
                 var stmt = conn.prepareStatement(
                         "INSERT INTO steve_cooldowns (player_id, last_used_at) VALUES (?, NOW() - INTERVAL '10 minutes')")) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }

            List<SteveStorage.CooldownEntry> active = blockingList(
                    steveStorage.getActiveCooldowns(Duration.ofMinutes(5))
            );

            assertThat(active).isEmpty();
        }
    }

    @Test
    @DisplayName("Different players have separate history")
    void differentPlayers_separateHistory() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        blockingAwait(steveStorage.saveHistory(player1, "Q1", new SteveAnswer("A1", List.of(), 0), "claude-3", 0));
        blockingAwait(steveStorage.saveHistory(player1, "Q2", new SteveAnswer("A2", List.of(), 0), "claude-3", 0));
        blockingAwait(steveStorage.saveHistory(player2, "Q3", new SteveAnswer("A3", List.of(), 0), "claude-3", 0));

        List<SteveHistoryEntry> player1History = blockingList(steveStorage.getHistory(player1));
        List<SteveHistoryEntry> player2History = blockingList(steveStorage.getHistory(player2));

        assertThat(player1History).hasSize(2);
        assertThat(player2History).hasSize(1);
    }
}
