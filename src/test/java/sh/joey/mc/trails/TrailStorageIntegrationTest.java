package sh.joey.mc.trails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for TrailStorage.
 * Tests the raw database operations since TrailEffect implementations depend on Bukkit classes.
 */
class TrailStorageIntegrationTest extends PostgresIntegrationTest {

    private TrailStorage trailStorage;

    @BeforeEach
    void setUpStorage() {
        trailStorage = new TrailStorage(storage);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Get trail setting returns empty when no setting")
        void getTrailSetting_noSetting_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            Optional<TrailSetting> setting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA));

            assertThat(setting).isEmpty();
        }

        @Test
        @DisplayName("Insert and retrieve trail effect via raw SQL")
        void insertAndRetrieve_rawSql() throws SQLException {
            UUID playerId = UUID.randomUUID();

            insertTrail(playerId, "elytra", "rainbow", "medium");

            // The storage can read "rainbow" and create a RainbowEffect
            Optional<TrailSetting> setting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA));
            assertThat(setting).isPresent();
            assertThat(setting.get().effect().id()).isEqualTo("rainbow");
            assertThat(setting.get().intensity()).isEqualTo(TrailIntensity.MEDIUM);
        }

        @Test
        @DisplayName("Insert twice results in single row (UPSERT)")
        void insertTwice_singleRow() throws SQLException {
            UUID playerId = UUID.randomUUID();

            insertTrail(playerId, "elytra", "rainbow", "low");
            insertTrail(playerId, "elytra", "rainbow", "high");

            int rowCount = countRows(
                    "SELECT COUNT(*) FROM player_trails WHERE player_id = '" + playerId + "' AND trail_type = 'elytra'"
            );
            assertThat(rowCount).isEqualTo(1);

            // Verify updated intensity
            String intensity = getIntensity(playerId, "elytra");
            assertThat(intensity).isEqualTo("high");
        }

        @Test
        @DisplayName("Clear trail setting removes entry")
        void clearTrailSetting_removesEntry() throws SQLException {
            UUID playerId = UUID.randomUUID();

            insertTrail(playerId, "elytra", "rainbow", "medium");
            blockingAwait(trailStorage.clearTrailSetting(playerId, TrailType.ELYTRA));

            Optional<TrailSetting> setting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA));
            assertThat(setting).isEmpty();

            int rowCount = countRows(
                    "SELECT COUNT(*) FROM player_trails WHERE player_id = '" + playerId + "' AND trail_type = 'elytra'"
            );
            assertThat(rowCount).isEqualTo(0);
        }

        @Test
        @DisplayName("Clear non-existent setting is no-op")
        void clearNonExistentSetting_noOp() {
            UUID playerId = UUID.randomUUID();

            // Should not throw
            blockingAwait(trailStorage.clearTrailSetting(playerId, TrailType.ELYTRA));

            Optional<TrailSetting> setting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA));
            assertThat(setting).isEmpty();
        }
    }

    @Nested
    @DisplayName("Intensity Updates")
    class IntensityUpdateTests {

        @Test
        @DisplayName("Update intensity only")
        void updateIntensity() throws SQLException {
            UUID playerId = UUID.randomUUID();

            insertTrail(playerId, "elytra", "rainbow", "low");
            blockingAwait(trailStorage.setTrailIntensity(playerId, TrailType.ELYTRA, TrailIntensity.HIGH));

            String intensity = getIntensity(playerId, "elytra");
            assertThat(intensity).isEqualTo("high");

            // Effect should remain unchanged
            Optional<TrailSetting> setting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA));
            assertThat(setting).isPresent();
            assertThat(setting.get().effect().id()).isEqualTo("rainbow");
        }

        @Test
        @DisplayName("All intensity levels are stored correctly")
        void allIntensityLevels_storedCorrectly() throws SQLException {
            for (TrailIntensity intensity : TrailIntensity.values()) {
                UUID playerId = UUID.randomUUID();

                insertTrail(playerId, "elytra", "rainbow", intensity.id());

                Optional<TrailSetting> setting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA));
                assertThat(setting).isPresent();
                assertThat(setting.get().intensity()).isEqualTo(intensity);
            }
        }
    }

    @Nested
    @DisplayName("Multiple Trail Types")
    class MultipleTrailTypesTests {

        @Test
        @DisplayName("Different trail types have separate settings")
        void differentTrailTypes_separateSettings() throws SQLException {
            UUID playerId = UUID.randomUUID();

            insertTrail(playerId, "elytra", "rainbow", "low");
            insertTrail(playerId, "walk", "rainbow", "high");

            var elytraSetting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA));
            var walkSetting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.WALK));

            assertThat(elytraSetting).isPresent();
            assertThat(elytraSetting.get().intensity()).isEqualTo(TrailIntensity.LOW);

            assertThat(walkSetting).isPresent();
            assertThat(walkSetting.get().intensity()).isEqualTo(TrailIntensity.HIGH);
        }

        @Test
        @DisplayName("Clear one trail type does not affect others")
        void clearOneTrailType_doesNotAffectOthers() throws SQLException {
            UUID playerId = UUID.randomUUID();

            insertTrail(playerId, "elytra", "rainbow", "medium");
            insertTrail(playerId, "walk", "rainbow", "medium");

            blockingAwait(trailStorage.clearTrailSetting(playerId, TrailType.ELYTRA));

            assertThat(blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA))).isEmpty();
            assertThat(blockingGet(trailStorage.getTrailSetting(playerId, TrailType.WALK))).isPresent();
        }

        @Test
        @DisplayName("Get all trail settings returns all types")
        void getAllTrailSettings_returnsAllTypes() throws SQLException {
            UUID playerId = UUID.randomUUID();

            insertTrail(playerId, "elytra", "rainbow", "medium");
            insertTrail(playerId, "walk", "rainbow", "high");

            List<TrailStorage.TrailSettingRow> settings = blockingList(trailStorage.getAllTrailSettings(playerId));

            assertThat(settings).hasSize(2);
            assertThat(settings).extracting(TrailStorage.TrailSettingRow::type)
                    .containsExactlyInAnyOrder(TrailType.ELYTRA, TrailType.WALK);
        }

        @Test
        @DisplayName("Get all trail settings returns empty when no settings")
        void getAllTrailSettings_noSettings_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            List<TrailStorage.TrailSettingRow> settings = blockingList(trailStorage.getAllTrailSettings(playerId));

            assertThat(settings).isEmpty();
        }
    }

    @Nested
    @DisplayName("Clear All Settings")
    class ClearAllSettingsTests {

        @Test
        @DisplayName("Clear all trail settings removes all entries")
        void clearAllTrailSettings_removesAllEntries() throws SQLException {
            UUID playerId = UUID.randomUUID();

            insertTrail(playerId, "elytra", "rainbow", "medium");
            insertTrail(playerId, "walk", "rainbow", "medium");
            insertTrail(playerId, "ghast", "rainbow", "medium");

            blockingAwait(trailStorage.clearAllTrailSettings(playerId));

            List<TrailStorage.TrailSettingRow> settings = blockingList(trailStorage.getAllTrailSettings(playerId));
            assertThat(settings).isEmpty();

            int rowCount = countRows("SELECT COUNT(*) FROM player_trails WHERE player_id = '" + playerId + "'");
            assertThat(rowCount).isEqualTo(0);
        }

        @Test
        @DisplayName("Clear all settings does not affect other players")
        void clearAllSettings_doesNotAffectOtherPlayers() throws SQLException {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            insertTrail(player1, "elytra", "rainbow", "medium");
            insertTrail(player2, "elytra", "rainbow", "medium");

            blockingAwait(trailStorage.clearAllTrailSettings(player1));

            assertThat(blockingList(trailStorage.getAllTrailSettings(player1))).isEmpty();
            assertThat(blockingList(trailStorage.getAllTrailSettings(player2))).hasSize(1);
        }
    }

    @Test
    @DisplayName("Rainbow effect is stored and retrieved correctly")
    void rainbowEffect_storedCorrectly() throws SQLException {
        UUID playerId = UUID.randomUUID();

        insertTrail(playerId, "elytra", "rainbow", "medium");

        Optional<TrailSetting> setting = blockingGet(trailStorage.getTrailSetting(playerId, TrailType.ELYTRA));
        assertThat(setting).isPresent();
        assertThat(setting.get().effect().id()).isEqualTo("rainbow");
    }

    @Test
    @DisplayName("Different players have separate trail settings")
    void differentPlayers_separateSettings() throws SQLException {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        insertTrail(player1, "elytra", "rainbow", "low");
        insertTrail(player2, "elytra", "rainbow", "high");

        var setting1 = blockingGet(trailStorage.getTrailSetting(player1, TrailType.ELYTRA));
        var setting2 = blockingGet(trailStorage.getTrailSetting(player2, TrailType.ELYTRA));

        assertThat(setting1).isPresent();
        assertThat(setting1.get().intensity()).isEqualTo(TrailIntensity.LOW);

        assertThat(setting2).isPresent();
        assertThat(setting2.get().intensity()).isEqualTo(TrailIntensity.HIGH);
    }

    private void insertTrail(UUID playerId, String trailType, String effect, String intensity)
            throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     INSERT INTO player_trails (player_id, trail_type, effect, intensity, created_at, updated_at)
                     VALUES (?, ?, ?, ?, NOW(), NOW())
                     ON CONFLICT (player_id, trail_type) DO UPDATE SET
                         effect = EXCLUDED.effect,
                         intensity = EXCLUDED.intensity,
                         updated_at = NOW()
                     """)) {
            stmt.setObject(1, playerId);
            stmt.setString(2, trailType);
            stmt.setString(3, effect);
            stmt.setString(4, intensity);
            stmt.executeUpdate();
        }
    }

    private String getIntensity(UUID playerId, String trailType) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT intensity FROM player_trails WHERE player_id = ? AND trail_type = ?")) {
            stmt.setObject(1, playerId);
            stmt.setString(2, trailType);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("intensity");
                }
                return null;
            }
        }
    }
}
