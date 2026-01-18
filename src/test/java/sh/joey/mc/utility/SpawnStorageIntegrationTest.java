package sh.joey.mc.utility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for SpawnStorage.
 */
class SpawnStorageIntegrationTest extends PostgresIntegrationTest {

    private SpawnStorage spawnStorage;

    @BeforeEach
    void setUpStorage() {
        spawnStorage = new SpawnStorage(storage);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Get spawn returns empty when spawn does not exist")
        void getSpawn_notExists_returnsEmpty() {
            UUID worldId = UUID.randomUUID();
            Optional<SpawnStorage.SpawnPoint> spawn = blockingGet(spawnStorage.getSpawn(worldId));
            assertThat(spawn).isEmpty();
        }

        @Test
        @DisplayName("Set and get spawn via raw SQL (Bukkit-free)")
        void setAndGetSpawn_rawSql() throws SQLException {
            UUID worldId = UUID.randomUUID();
            UUID setBy = UUID.randomUUID();

            insertSpawn(worldId, 100.5, 64.0, -200.25, 45.0f, -30.0f, setBy);

            Optional<SpawnStorage.SpawnPoint> retrieved = blockingGet(spawnStorage.getSpawn(worldId));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().worldId()).isEqualTo(worldId);
            assertThat(retrieved.get().x()).isCloseTo(100.5, within(0.001));
            assertThat(retrieved.get().y()).isCloseTo(64.0, within(0.001));
            assertThat(retrieved.get().z()).isCloseTo(-200.25, within(0.001));
            assertThat(retrieved.get().yaw()).isCloseTo(45.0f, within(0.001f));
            assertThat(retrieved.get().pitch()).isCloseTo(-30.0f, within(0.001f));
        }

        @Test
        @DisplayName("Set spawn twice results in single row (UPSERT)")
        void setSpawnTwice_singleRow() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertSpawn(worldId, 0, 64, 0, 0, 0, null);
            insertSpawn(worldId, 100, 128, 200, 90, 45, null);

            int rowCount = countRows("SELECT COUNT(*) FROM world_spawns WHERE world_id = '" + worldId + "'");
            assertThat(rowCount).isEqualTo(1);

            Optional<SpawnStorage.SpawnPoint> retrieved = blockingGet(spawnStorage.getSpawn(worldId));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().x()).isCloseTo(100.0, within(0.001));
        }

        @Test
        @DisplayName("Different worlds have separate spawns")
        void differentWorlds_separateSpawns() throws SQLException {
            UUID world1 = UUID.randomUUID();
            UUID world2 = UUID.randomUUID();

            insertSpawn(world1, 10, 64, 10, 0, 0, null);
            insertSpawn(world2, 100, 128, 100, 0, 0, null);

            Optional<SpawnStorage.SpawnPoint> spawn1 = blockingGet(spawnStorage.getSpawn(world1));
            Optional<SpawnStorage.SpawnPoint> spawn2 = blockingGet(spawnStorage.getSpawn(world2));

            assertThat(spawn1).isPresent();
            assertThat(spawn1.get().x()).isCloseTo(10.0, within(0.001));

            assertThat(spawn2).isPresent();
            assertThat(spawn2.get().x()).isCloseTo(100.0, within(0.001));
        }

        @Test
        @DisplayName("Set_by can be null")
        void setBy_canBeNull() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertSpawn(worldId, 0, 64, 0, 0, 0, null);

            // Verify via raw SQL that set_by is null
            int hasNullSetBy = countRows(
                    "SELECT COUNT(*) FROM world_spawns WHERE world_id = '" + worldId + "' AND set_by IS NULL"
            );
            assertThat(hasNullSetBy).isEqualTo(1);
        }

        @Test
        @DisplayName("Set_at timestamp is recorded")
        void setAt_timestampRecorded() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertSpawn(worldId, 0, 64, 0, 0, 0, null);

            int hasSetAt = countRows(
                    "SELECT COUNT(*) FROM world_spawns WHERE world_id = '" + worldId + "' AND set_at IS NOT NULL"
            );
            assertThat(hasSetAt).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("List Operations")
    class ListTests {

        @Test
        @DisplayName("Get all spawns returns empty list when no spawns")
        void getAllSpawns_noSpawns_returnsEmptyList() {
            List<SpawnStorage.SpawnPoint> spawns = blockingList(spawnStorage.getAllSpawns());
            assertThat(spawns).isEmpty();
        }

        @Test
        @DisplayName("Get all spawns returns all spawns")
        void getAllSpawns_returnsAllSpawns() throws SQLException {
            UUID world1 = UUID.randomUUID();
            UUID world2 = UUID.randomUUID();
            UUID world3 = UUID.randomUUID();

            insertSpawn(world1, 0, 64, 0, 0, 0, null);
            insertSpawn(world2, 10, 64, 10, 0, 0, null);
            insertSpawn(world3, 20, 64, 20, 0, 0, null);

            List<SpawnStorage.SpawnPoint> spawns = blockingList(spawnStorage.getAllSpawns());
            assertThat(spawns).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Observable Notifications")
    class NotificationTests {

        @Test
        @DisplayName("Set spawn via raw SQL does not emit (only setSpawn method does)")
        void rawSqlInsert_doesNotEmitNotification() throws SQLException {
            UUID worldId = UUID.randomUUID();

            AtomicReference<UUID> notified = new AtomicReference<>();
            spawnStorage.onChanged().take(1).subscribe(notified::set);

            // Raw SQL insert bypasses the observable
            insertSpawn(worldId, 0, 64, 0, 0, 0, null);

            // Give a moment for any potential emission
            assertThat(notified.get()).isNull();
        }
    }

    @Nested
    @DisplayName("Coordinate Handling")
    class CoordinateTests {

        @Test
        @DisplayName("Negative coordinates are stored correctly")
        void negativeCoordinates_storedCorrectly() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertSpawn(worldId, -1000.75, -64.0, -500.5, -180.0f, -90.0f, null);

            Optional<SpawnStorage.SpawnPoint> retrieved = blockingGet(spawnStorage.getSpawn(worldId));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().x()).isCloseTo(-1000.75, within(0.001));
            assertThat(retrieved.get().y()).isCloseTo(-64.0, within(0.001));
            assertThat(retrieved.get().z()).isCloseTo(-500.5, within(0.001));
            assertThat(retrieved.get().yaw()).isCloseTo(-180.0f, within(0.001f));
            assertThat(retrieved.get().pitch()).isCloseTo(-90.0f, within(0.001f));
        }

        @Test
        @DisplayName("Large coordinates are stored correctly")
        void largeCoordinates_storedCorrectly() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertSpawn(worldId, 29999984.0, 320.0, -29999984.0, 0, 0, null);

            Optional<SpawnStorage.SpawnPoint> retrieved = blockingGet(spawnStorage.getSpawn(worldId));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().x()).isCloseTo(29999984.0, within(0.001));
            assertThat(retrieved.get().y()).isCloseTo(320.0, within(0.001));
            assertThat(retrieved.get().z()).isCloseTo(-29999984.0, within(0.001));
        }
    }

    private void insertSpawn(UUID worldId, double x, double y, double z,
                             float yaw, float pitch, UUID setBy) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     INSERT INTO world_spawns (world_id, x, y, z, yaw, pitch, set_by, set_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                     ON CONFLICT (world_id) DO UPDATE SET
                         x = EXCLUDED.x,
                         y = EXCLUDED.y,
                         z = EXCLUDED.z,
                         yaw = EXCLUDED.yaw,
                         pitch = EXCLUDED.pitch,
                         set_by = EXCLUDED.set_by,
                         set_at = NOW()
                     """)) {
            stmt.setObject(1, worldId);
            stmt.setDouble(2, x);
            stmt.setDouble(3, y);
            stmt.setDouble(4, z);
            stmt.setFloat(5, yaw);
            stmt.setFloat(6, pitch);
            stmt.setObject(7, setBy);
            stmt.executeUpdate();
        }
    }
}
