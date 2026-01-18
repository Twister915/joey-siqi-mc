package sh.joey.mc.anticheat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

class CheatViolationStorageIntegrationTest extends PostgresIntegrationTest {

    private CheatViolationStorage violationStorage;

    @BeforeEach
    void setUpStorage() {
        violationStorage = new CheatViolationStorage(storage);
    }

    @Test
    @DisplayName("Record and retrieve a violation")
    void recordViolation_success() {
        UUID playerId = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();
        ViolationLocation location = new ViolationLocation("world", 100.5, 64.0, -200.3, 0, 0);

        blockingAwait(violationStorage.recordViolation(
                playerId,
                serverSessionId,
                "Speed",
                2.5,
                5.0,
                location,
                Map.of("speed", 15.5, "maxSpeed", 10.0),
                Detection.SOURCE_CUSTOM
        ));

        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getPlayerViolations(playerId, 10)
        );

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).checkName()).isEqualTo("Speed");
        assertThat(violations.get(0).violationLevel()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Get recent violations returns in descending time order")
    void getRecentViolations_descendingOrder() throws InterruptedException {
        UUID playerId = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();
        ViolationLocation location = new ViolationLocation("world", 0, 64, 0, 0, 0);

        blockingAwait(violationStorage.recordViolation(
                playerId, serverSessionId, "Speed", 1.0, 1.0, location, null, Detection.SOURCE_CUSTOM
        ));
        Thread.sleep(10); // Ensure different timestamps
        blockingAwait(violationStorage.recordViolation(
                playerId, serverSessionId, "Fly", 2.0, 3.0, location, null, Detection.SOURCE_CUSTOM
        ));
        Thread.sleep(10);
        blockingAwait(violationStorage.recordViolation(
                playerId, serverSessionId, "Reach", 1.5, 4.5, location, null, Detection.SOURCE_CUSTOM
        ));

        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getRecentViolations(10)
        );

        assertThat(violations).hasSize(3);
        assertThat(violations.get(0).checkName()).isEqualTo("Reach");
        assertThat(violations.get(1).checkName()).isEqualTo("Fly");
        assertThat(violations.get(2).checkName()).isEqualTo("Speed");
    }

    @Test
    @DisplayName("Get player violations filters by player")
    void getPlayerViolations_filtersCorrectly() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();
        ViolationLocation location = new ViolationLocation("world", 0, 64, 0, 0, 0);

        blockingAwait(violationStorage.recordViolation(
                player1, serverSessionId, "Speed", 1.0, 1.0, location, null, Detection.SOURCE_CUSTOM
        ));
        blockingAwait(violationStorage.recordViolation(
                player2, serverSessionId, "Fly", 2.0, 2.0, location, null, Detection.SOURCE_CUSTOM
        ));
        blockingAwait(violationStorage.recordViolation(
                player1, serverSessionId, "Reach", 1.5, 2.5, location, null, Detection.SOURCE_CUSTOM
        ));

        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getPlayerViolations(player1, 10)
        );

        assertThat(violations).hasSize(2);
        assertThat(violations).allMatch(v -> v.playerId().equals(player1));
    }

    @Test
    @DisplayName("Get player violations respects limit")
    void getPlayerViolations_respectsLimit() {
        UUID playerId = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();
        ViolationLocation location = new ViolationLocation("world", 0, 64, 0, 0, 0);

        for (int i = 0; i < 15; i++) {
            blockingAwait(violationStorage.recordViolation(
                    playerId, serverSessionId, "Speed", 1.0, (double) i, location, null, Detection.SOURCE_CUSTOM
            ));
        }

        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getPlayerViolations(playerId, 5)
        );

        assertThat(violations).hasSize(5);
    }

    @Test
    @DisplayName("Mark violation as reviewed")
    void markReviewed_updatesFields() {
        UUID playerId = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ViolationLocation location = new ViolationLocation("world", 0, 64, 0, 0, 0);

        blockingAwait(violationStorage.recordViolation(
                playerId, serverSessionId, "Speed", 1.0, 1.0, location, null, Detection.SOURCE_CUSTOM
        ));

        List<CheatViolationStorage.ViolationEntry> beforeReview = blockingList(
                violationStorage.getPlayerViolations(playerId, 10)
        );
        UUID violationId = beforeReview.get(0).id();

        blockingAwait(violationStorage.markReviewed(
                violationId, reviewerId, "cheating", "Confirmed speed hacking"
        ));

        List<CheatViolationStorage.ViolationEntry> afterReview = blockingList(
                violationStorage.getPlayerViolations(playerId, 10)
        );

        assertThat(afterReview.get(0).reviewed()).isTrue();
    }

    @Test
    @DisplayName("Delete old violations removes entries older than retention period")
    void deleteOldViolations_removesOldEntries() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();

        // Insert an old violation (35 days ago)
        try (var conn = database.getConnection();
             var stmt = conn.prepareStatement("""
                     INSERT INTO cheat_violations
                     (player_id, server_session_id, check_name, violation_weight, violation_level,
                      player_location, detected_at)
                     VALUES (?, ?, 'Speed', 1.0, 1.0, '{"world":"world","x":0,"y":64,"z":0}'::jsonb,
                             NOW() - INTERVAL '35 days')
                     """)) {
            stmt.setObject(1, playerId);
            stmt.setObject(2, serverSessionId);
            stmt.executeUpdate();
        }

        // Insert a recent violation
        ViolationLocation location = new ViolationLocation("world", 0, 64, 0, 0, 0);
        blockingAwait(violationStorage.recordViolation(
                playerId, serverSessionId, "Fly", 1.0, 1.0, location, null, Detection.SOURCE_CUSTOM
        ));

        blockingAwait(violationStorage.deleteOldViolations(30));

        List<CheatViolationStorage.ViolationEntry> remaining = blockingList(
                violationStorage.getPlayerViolations(playerId, 10)
        );

        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).checkName()).isEqualTo("Fly");
    }

    @Test
    @DisplayName("Violation data JSON is stored and retrieved correctly")
    void violationData_storedCorrectly() {
        UUID playerId = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();
        ViolationLocation location = new ViolationLocation("world", 100, 64, -100, 0, 0);
        Map<String, Object> data = Map.of(
                "speed", 15.5,
                "maxSpeed", 10.0,
                "ratio", 1.55
        );

        blockingAwait(violationStorage.recordViolation(
                playerId, serverSessionId, "Speed", 2.0, 5.0, location, data, Detection.SOURCE_CUSTOM
        ));

        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getPlayerViolations(playerId, 10)
        );

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).violationDataJson()).isNotNull();
        assertThat(violations.get(0).violationDataJson()).contains("speed");
        assertThat(violations.get(0).violationDataJson()).contains("15.5");
    }

    @Test
    @DisplayName("Null violation data is handled correctly")
    void nullViolationData_handledCorrectly() {
        UUID playerId = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();
        ViolationLocation location = new ViolationLocation("world", 0, 64, 0, 0, 0);

        blockingAwait(violationStorage.recordViolation(
                playerId, serverSessionId, "Speed", 1.0, 1.0, location, null, Detection.SOURCE_CUSTOM
        ));

        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getPlayerViolations(playerId, 10)
        );

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).violationDataJson()).isNull();
    }

    @Test
    @DisplayName("Location JSON contains all fields")
    void locationJson_containsAllFields() {
        UUID playerId = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();
        ViolationLocation location = new ViolationLocation("survival", 123.456, 64.0, -789.012, 45.5f, -30.0f);

        blockingAwait(violationStorage.recordViolation(
                playerId, serverSessionId, "Speed", 1.0, 1.0, location, null, Detection.SOURCE_CUSTOM
        ));

        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getPlayerViolations(playerId, 10)
        );

        String locationJson = violations.get(0).locationJson();
        assertThat(locationJson).contains("survival");
        assertThat(locationJson).contains("123.456");
        assertThat(locationJson).contains("64.0");
        assertThat(locationJson).contains("-789.012");
    }

    @Test
    @DisplayName("Empty result when no violations for player")
    void getPlayerViolations_noViolations_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getPlayerViolations(playerId, 10)
        );

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Empty result when no recent violations")
    void getRecentViolations_noViolations_returnsEmpty() {
        List<CheatViolationStorage.ViolationEntry> violations = blockingList(
                violationStorage.getRecentViolations(10)
        );

        assertThat(violations).isEmpty();
    }
}
