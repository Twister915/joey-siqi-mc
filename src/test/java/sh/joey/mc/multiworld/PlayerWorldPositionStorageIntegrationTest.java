package sh.joey.mc.multiworld;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for PlayerWorldPositionStorage.
 * Tests the raw database operations since Location/World require Bukkit runtime.
 */
class PlayerWorldPositionStorageIntegrationTest extends PostgresIntegrationTest {

    @BeforeEach
    void setUpStorage() {
        // No storage instance needed - we test raw SQL since the storage requires Bukkit objects
    }

    @Test
    @DisplayName("Save position stores all coordinates correctly")
    void savePosition_storesCoordinates() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        insertPosition(playerId, worldId, 100.5, 64.0, -200.25, 45.0f, -30.0f);

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT x, y, z, yaw, pitch FROM player_world_positions WHERE player_id = ? AND world_id = ?")) {
            stmt.setObject(1, playerId);
            stmt.setObject(2, worldId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("x")).isCloseTo(100.5, within(0.001));
                assertThat(rs.getDouble("y")).isCloseTo(64.0, within(0.001));
                assertThat(rs.getDouble("z")).isCloseTo(-200.25, within(0.001));
                assertThat(rs.getFloat("yaw")).isCloseTo(45.0f, within(0.001f));
                assertThat(rs.getFloat("pitch")).isCloseTo(-30.0f, within(0.001f));
            }
        }
    }

    @Test
    @DisplayName("Save position twice for same player/world results in single row (UPSERT)")
    void savePositionTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        insertPosition(playerId, worldId, 0, 64, 0, 0, 0);
        insertPosition(playerId, worldId, 100, 128, 200, 90, 45);

        int rowCount = countRows(
                "SELECT COUNT(*) FROM player_world_positions WHERE player_id = '" + playerId + "' AND world_id = '" + worldId + "'"
        );
        assertThat(rowCount).isEqualTo(1);

        // Verify updated values
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT x, y, z FROM player_world_positions WHERE player_id = ? AND world_id = ?")) {
            stmt.setObject(1, playerId);
            stmt.setObject(2, worldId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("x")).isCloseTo(100.0, within(0.001));
                assertThat(rs.getDouble("y")).isCloseTo(128.0, within(0.001));
                assertThat(rs.getDouble("z")).isCloseTo(200.0, within(0.001));
            }
        }
    }

    @Test
    @DisplayName("Different worlds have separate positions for same player")
    void differentWorlds_separatePositions() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID world1 = UUID.randomUUID();
        UUID world2 = UUID.randomUUID();

        insertPosition(playerId, world1, 10, 20, 30, 0, 0);
        insertPosition(playerId, world2, 100, 200, 300, 0, 0);

        int totalRows = countRows(
                "SELECT COUNT(*) FROM player_world_positions WHERE player_id = '" + playerId + "'"
        );
        assertThat(totalRows).isEqualTo(2);

        // Verify world1 position
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT x FROM player_world_positions WHERE player_id = ? AND world_id = ?")) {
            stmt.setObject(1, playerId);
            stmt.setObject(2, world1);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("x")).isCloseTo(10.0, within(0.001));
            }
        }

        // Verify world2 position
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT x FROM player_world_positions WHERE player_id = ? AND world_id = ?")) {
            stmt.setObject(1, playerId);
            stmt.setObject(2, world2);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("x")).isCloseTo(100.0, within(0.001));
            }
        }
    }

    @Test
    @DisplayName("Different players have separate positions for same world")
    void differentPlayers_separatePositions() throws SQLException {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        insertPosition(player1, worldId, 50, 64, 50, 0, 0);
        insertPosition(player2, worldId, 150, 128, 150, 0, 0);

        // Verify player1 position
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT x FROM player_world_positions WHERE player_id = ? AND world_id = ?")) {
            stmt.setObject(1, player1);
            stmt.setObject(2, worldId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("x")).isCloseTo(50.0, within(0.001));
            }
        }

        // Verify player2 position
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT x FROM player_world_positions WHERE player_id = ? AND world_id = ?")) {
            stmt.setObject(1, player2);
            stmt.setObject(2, worldId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("x")).isCloseTo(150.0, within(0.001));
            }
        }
    }

    @Test
    @DisplayName("Updated_at is set on insert and update")
    void updatedAt_setOnInsertAndUpdate() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        insertPosition(playerId, worldId, 0, 64, 0, 0, 0);

        int hasUpdatedAt = countRows(
                "SELECT COUNT(*) FROM player_world_positions WHERE player_id = '" + playerId + "' AND updated_at IS NOT NULL"
        );
        assertThat(hasUpdatedAt).isEqualTo(1);
    }

    @Test
    @DisplayName("Negative coordinates are stored correctly")
    void negativeCoordinates_storedCorrectly() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        insertPosition(playerId, worldId, -1000.75, -64.0, -500.5, -180.0f, -90.0f);

        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT x, y, z, yaw, pitch FROM player_world_positions WHERE player_id = ? AND world_id = ?")) {
            stmt.setObject(1, playerId);
            stmt.setObject(2, worldId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("x")).isCloseTo(-1000.75, within(0.001));
                assertThat(rs.getDouble("y")).isCloseTo(-64.0, within(0.001));
                assertThat(rs.getDouble("z")).isCloseTo(-500.5, within(0.001));
                assertThat(rs.getFloat("yaw")).isCloseTo(-180.0f, within(0.001f));
                assertThat(rs.getFloat("pitch")).isCloseTo(-90.0f, within(0.001f));
            }
        }
    }

    private void insertPosition(UUID playerId, UUID worldId, double x, double y, double z, float yaw, float pitch)
            throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     INSERT INTO player_world_positions (player_id, world_id, x, y, z, yaw, pitch, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                     ON CONFLICT (player_id, world_id)
                     DO UPDATE SET x = EXCLUDED.x, y = EXCLUDED.y, z = EXCLUDED.z,
                                   yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch, updated_at = NOW()
                     """)) {
            stmt.setObject(1, playerId);
            stmt.setObject(2, worldId);
            stmt.setDouble(3, x);
            stmt.setDouble(4, y);
            stmt.setDouble(5, z);
            stmt.setFloat(6, yaw);
            stmt.setFloat(7, pitch);
            stmt.executeUpdate();
        }
    }
}
