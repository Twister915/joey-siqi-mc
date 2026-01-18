package sh.joey.mc.utility;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for WarpStorage.
 */
class WarpStorageIntegrationTest extends PostgresIntegrationTest {

    private WarpStorage warpStorage;

    @BeforeEach
    void setUpStorage() {
        warpStorage = new WarpStorage(storage);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Get warp returns empty when warp does not exist")
        void getWarp_notExists_returnsEmpty() {
            Optional<WarpStorage.Warp> warp = blockingGet(warpStorage.getWarp("nonexistent"));
            assertThat(warp).isEmpty();
        }

        @Test
        @DisplayName("Set and get warp via raw SQL (Bukkit-free)")
        void setAndGetWarp_rawSql() throws SQLException {
            UUID worldId = UUID.randomUUID();
            UUID createdBy = UUID.randomUUID();

            insertWarp("spawn", worldId, 100.5, 64.0, -200.25, 45.0f, -30.0f, createdBy);

            Optional<WarpStorage.Warp> retrieved = blockingGet(warpStorage.getWarp("spawn"));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().name()).isEqualTo("spawn");
            assertThat(retrieved.get().worldId()).isEqualTo(worldId);
            assertThat(retrieved.get().x()).isCloseTo(100.5, within(0.001));
            assertThat(retrieved.get().y()).isCloseTo(64.0, within(0.001));
            assertThat(retrieved.get().z()).isCloseTo(-200.25, within(0.001));
            assertThat(retrieved.get().yaw()).isCloseTo(45.0f, within(0.001f));
            assertThat(retrieved.get().pitch()).isCloseTo(-30.0f, within(0.001f));
            assertThat(retrieved.get().createdBy()).isEqualTo(createdBy);
        }

        @Test
        @DisplayName("Warp name is normalized to lowercase")
        void warpName_normalizedToLowercase() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertWarp("MyWarp", worldId, 0, 64, 0, 0, 0, null);

            // Should be stored as lowercase
            int rowCount = countRows("SELECT COUNT(*) FROM warps WHERE name = 'mywarp'");
            assertThat(rowCount).isEqualTo(1);

            // Should be retrievable with any case
            assertThat(blockingGet(warpStorage.getWarp("mywarp"))).isPresent();
            assertThat(blockingGet(warpStorage.getWarp("MYWARP"))).isPresent();
            assertThat(blockingGet(warpStorage.getWarp("MyWarp"))).isPresent();
        }

        @Test
        @DisplayName("Set warp twice results in single row (UPSERT)")
        void setWarpTwice_singleRow() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertWarp("shop", worldId, 0, 64, 0, 0, 0, null);
            insertWarp("shop", worldId, 100, 128, 200, 90, 45, null);

            int rowCount = countRows("SELECT COUNT(*) FROM warps WHERE name = 'shop'");
            assertThat(rowCount).isEqualTo(1);

            Optional<WarpStorage.Warp> retrieved = blockingGet(warpStorage.getWarp("shop"));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().x()).isCloseTo(100.0, within(0.001));
        }

        @Test
        @DisplayName("Created_by can be null")
        void createdBy_canBeNull() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertWarp("anonymous", worldId, 0, 64, 0, 0, 0, null);

            Optional<WarpStorage.Warp> retrieved = blockingGet(warpStorage.getWarp("anonymous"));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().createdBy()).isNull();
        }

        @Test
        @DisplayName("Delete warp removes entry")
        void deleteWarp_removesEntry() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertWarp("todelete", worldId, 0, 64, 0, 0, 0, null);

            boolean deleted = blockingGet(warpStorage.deleteWarp("todelete"));
            assertThat(deleted).isTrue();

            int rowCount = countRows("SELECT COUNT(*) FROM warps WHERE name = 'todelete'");
            assertThat(rowCount).isEqualTo(0);
        }

        @Test
        @DisplayName("Delete non-existent warp returns false")
        void deleteNonExistentWarp_returnsFalse() {
            boolean deleted = blockingGet(warpStorage.deleteWarp("nonexistent"));
            assertThat(deleted).isFalse();
        }
    }

    @Nested
    @DisplayName("List Operations")
    class ListTests {

        @Test
        @DisplayName("Get all warps returns empty list when no warps")
        void getAllWarps_noWarps_returnsEmptyList() {
            List<WarpStorage.Warp> warps = blockingList(warpStorage.getAllWarps());
            assertThat(warps).isEmpty();
        }

        @Test
        @DisplayName("Get all warps returns all warps sorted by name")
        void getAllWarps_returnsSortedList() throws SQLException {
            UUID worldId = UUID.randomUUID();

            insertWarp("zoo", worldId, 0, 64, 0, 0, 0, null);
            insertWarp("apple", worldId, 10, 64, 10, 0, 0, null);
            insertWarp("market", worldId, 20, 64, 20, 0, 0, null);

            List<WarpStorage.Warp> warps = blockingList(warpStorage.getAllWarps());
            assertThat(warps).hasSize(3);
            assertThat(warps.get(0).name()).isEqualTo("apple");
            assertThat(warps.get(1).name()).isEqualTo("market");
            assertThat(warps.get(2).name()).isEqualTo("zoo");
        }
    }

    @Nested
    @DisplayName("Observable Notifications")
    class NotificationTests {

        @Test
        @DisplayName("Delete warp emits notification")
        void deleteWarp_emitsNotification() throws SQLException {
            UUID worldId = UUID.randomUUID();
            insertWarp("notify", worldId, 0, 64, 0, 0, 0, null);

            AtomicReference<String> notified = new AtomicReference<>();
            warpStorage.onChanged().take(1).subscribe(notified::set);

            blockingGet(warpStorage.deleteWarp("notify"));

            assertThat(notified.get()).isEqualTo("notify");
        }
    }

    private void insertWarp(String name, UUID worldId, double x, double y, double z,
                            float yaw, float pitch, UUID createdBy) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     INSERT INTO warps (name, world_id, x, y, z, yaw, pitch, created_by)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT (name) DO UPDATE SET
                         world_id = EXCLUDED.world_id,
                         x = EXCLUDED.x,
                         y = EXCLUDED.y,
                         z = EXCLUDED.z,
                         yaw = EXCLUDED.yaw,
                         pitch = EXCLUDED.pitch,
                         created_by = EXCLUDED.created_by
                     """)) {
            stmt.setString(1, name.toLowerCase());
            stmt.setObject(2, worldId);
            stmt.setDouble(3, x);
            stmt.setDouble(4, y);
            stmt.setDouble(5, z);
            stmt.setFloat(6, yaw);
            stmt.setFloat(7, pitch);
            stmt.setObject(8, createdBy);
            stmt.executeUpdate();
        }
    }
}
