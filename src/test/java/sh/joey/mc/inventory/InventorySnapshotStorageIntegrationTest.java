package sh.joey.mc.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for InventorySnapshotStorage.
 * Tests binary data round-trip and JSON serialization.
 */
class InventorySnapshotStorageIntegrationTest extends PostgresIntegrationTest {

    private InventorySnapshotStorage snapshotStorage;

    @BeforeEach
    void setUpStorage() {
        snapshotStorage = new InventorySnapshotStorage(storage);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Save snapshot and get by ID")
        void saveAndGetById() {
            UUID playerId = UUID.randomUUID();
            InventorySnapshot snapshot = createSnapshot(playerId);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));

            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().id()).isEqualTo(savedId);
            assertThat(retrieved.get().playerId()).isEqualTo(playerId);
        }

        @Test
        @DisplayName("Get by ID returns empty when not found")
        void getById_notFound_returnsEmpty() {
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(UUID.randomUUID()));

            assertThat(retrieved).isEmpty();
        }

        @Test
        @DisplayName("Delete snapshot by ID")
        void deleteById() throws SQLException {
            UUID playerId = UUID.randomUUID();
            InventorySnapshot snapshot = createSnapshot(playerId);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            blockingAwait(snapshotStorage.deleteById(savedId));

            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));
            assertThat(retrieved).isEmpty();

            int count = countRows("SELECT COUNT(*) FROM inventory_snapshots WHERE id = '" + savedId + "'");
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("List snapshots by player ordered by time descending")
        void listByPlayer_orderedByTimeDescending() throws InterruptedException {
            UUID playerId = UUID.randomUUID();

            InventorySnapshot snapshot1 = createSnapshot(playerId);
            blockingGet(snapshotStorage.save(snapshot1));
            Thread.sleep(10);

            InventorySnapshot snapshot2 = createSnapshot(playerId);
            blockingGet(snapshotStorage.save(snapshot2));
            Thread.sleep(10);

            InventorySnapshot snapshot3 = createSnapshot(playerId);
            blockingGet(snapshotStorage.save(snapshot3));

            List<InventorySnapshot> snapshots = blockingList(snapshotStorage.listByPlayer(playerId, 10, 0));

            assertThat(snapshots).hasSize(3);
            // Most recent first
            assertThat(snapshots.get(0).id()).isEqualTo(snapshot3.id());
            assertThat(snapshots.get(1).id()).isEqualTo(snapshot2.id());
            assertThat(snapshots.get(2).id()).isEqualTo(snapshot1.id());
        }

        @Test
        @DisplayName("List snapshots by player respects limit and offset")
        void listByPlayer_respectsLimitAndOffset() {
            UUID playerId = UUID.randomUUID();

            for (int i = 0; i < 10; i++) {
                blockingGet(snapshotStorage.save(createSnapshot(playerId)));
            }

            List<InventorySnapshot> firstPage = blockingList(snapshotStorage.listByPlayer(playerId, 5, 0));
            List<InventorySnapshot> secondPage = blockingList(snapshotStorage.listByPlayer(playerId, 5, 5));

            assertThat(firstPage).hasSize(5);
            assertThat(secondPage).hasSize(5);

            // Pages should not overlap
            assertThat(firstPage).extracting(InventorySnapshot::id)
                    .doesNotContainAnyElementsOf(secondPage.stream().map(InventorySnapshot::id).toList());
        }

        @Test
        @DisplayName("List snapshots returns empty when no snapshots")
        void listByPlayer_noSnapshots_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            List<InventorySnapshot> snapshots = blockingList(snapshotStorage.listByPlayer(playerId, 10, 0));

            assertThat(snapshots).isEmpty();
        }
    }

    @Nested
    @DisplayName("Binary Data Round-Trip")
    class BinaryDataTests {

        @Test
        @DisplayName("Inventory data round-trips correctly")
        void inventoryData_roundTrips() {
            UUID playerId = UUID.randomUUID();
            byte[] inventoryData = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
            InventorySnapshot snapshot = createSnapshotWithData(playerId, inventoryData, new byte[0], new byte[0], new byte[0]);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().inventoryData()).isEqualTo(inventoryData);
        }

        @Test
        @DisplayName("Armor data round-trips correctly")
        void armorData_roundTrips() {
            UUID playerId = UUID.randomUUID();
            byte[] armorData = new byte[]{11, 12, 13, 14, 15};
            InventorySnapshot snapshot = createSnapshotWithData(playerId, new byte[0], armorData, new byte[0], new byte[0]);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().armorData()).isEqualTo(armorData);
        }

        @Test
        @DisplayName("Offhand data round-trips correctly")
        void offhandData_roundTrips() {
            UUID playerId = UUID.randomUUID();
            byte[] offhandData = new byte[]{21, 22, 23};
            InventorySnapshot snapshot = createSnapshotWithData(playerId, new byte[0], new byte[0], offhandData, new byte[0]);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().offhandData()).isEqualTo(offhandData);
        }

        @Test
        @DisplayName("Ender chest data round-trips correctly")
        void enderChestData_roundTrips() {
            UUID playerId = UUID.randomUUID();
            byte[] enderChestData = new byte[]{31, 32, 33, 34, 35, 36};
            InventorySnapshot snapshot = createSnapshotWithData(playerId, new byte[0], new byte[0], new byte[0], enderChestData);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().enderChestData()).isEqualTo(enderChestData);
        }

        @Test
        @DisplayName("Empty byte arrays round-trip correctly")
        void emptyByteArrays_roundTrip() {
            UUID playerId = UUID.randomUUID();
            InventorySnapshot snapshot = createSnapshotWithData(playerId, new byte[0], new byte[0], new byte[0], new byte[0]);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().inventoryData()).isEmpty();
            assertThat(retrieved.get().armorData()).isEmpty();
            assertThat(retrieved.get().offhandData()).isEmpty();
            assertThat(retrieved.get().enderChestData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Player Stats Round-Trip")
    class PlayerStatsTests {

        @Test
        @DisplayName("XP level and progress round-trip correctly")
        void xpData_roundTrips() {
            UUID playerId = UUID.randomUUID();
            InventorySnapshot snapshot = createSnapshotWithStats(playerId, 30, 0.75f, 20.0, 20.0, 15, 4.5f);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().xpLevel()).isEqualTo(30);
            assertThat(retrieved.get().xpProgress()).isEqualTo(0.75f);
        }

        @Test
        @DisplayName("Health and max health round-trip correctly")
        void healthData_roundTrips() {
            UUID playerId = UUID.randomUUID();
            InventorySnapshot snapshot = createSnapshotWithStats(playerId, 0, 0f, 15.5, 40.0, 20, 5.0f);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().health()).isEqualTo(15.5);
            assertThat(retrieved.get().maxHealth()).isEqualTo(40.0);
        }

        @Test
        @DisplayName("Hunger and saturation round-trip correctly")
        void hungerData_roundTrips() {
            UUID playerId = UUID.randomUUID();
            InventorySnapshot snapshot = createSnapshotWithStats(playerId, 0, 0f, 20.0, 20.0, 12, 2.5f);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().hunger()).isEqualTo(12);
            assertThat(retrieved.get().saturation()).isEqualTo(2.5f);
        }
    }

    @Nested
    @DisplayName("JSON Data Round-Trip")
    class JsonDataTests {

        @Test
        @DisplayName("Effects JSON round-trips correctly")
        void effectsJson_roundTrips() {
            UUID playerId = UUID.randomUUID();
            List<InventorySnapshot.EffectData> effects = List.of(
                    new InventorySnapshot.EffectData("minecraft:speed", 600, 1, false, true, true),
                    new InventorySnapshot.EffectData("minecraft:strength", 1200, 2, true, false, false)
            );
            InventorySnapshot snapshot = createSnapshotWithEffects(playerId, effects);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().effects()).hasSize(2);
            assertThat(retrieved.get().effects()).extracting(InventorySnapshot.EffectData::id)
                    .containsExactlyInAnyOrder("minecraft:speed", "minecraft:strength");
        }

        @Test
        @DisplayName("Empty effects list round-trips correctly")
        void emptyEffects_roundTrips() {
            UUID playerId = UUID.randomUUID();
            InventorySnapshot snapshot = createSnapshotWithEffects(playerId, Collections.emptyList());

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().effects()).isEmpty();
        }

        @Test
        @DisplayName("Labels JSON round-trips correctly")
        void labelsJson_roundTrips() {
            UUID playerId = UUID.randomUUID();
            Map<String, Object> labels = Map.of(
                    "reason", "world_change",
                    "world_from", "overworld",
                    "world_to", "nether"
            );
            InventorySnapshot snapshot = createSnapshotWithLabels(playerId, labels);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().labels()).containsKeys("reason", "world_from", "world_to");
            assertThat(retrieved.get().labels().get("reason")).isEqualTo("world_change");
        }

        @Test
        @DisplayName("Empty labels map round-trips correctly")
        void emptyLabels_roundTrips() {
            UUID playerId = UUID.randomUUID();
            InventorySnapshot snapshot = createSnapshotWithLabels(playerId, Collections.emptyMap());

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().labels()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Timestamp Handling")
    class TimestampTests {

        @Test
        @DisplayName("Snapshot timestamp round-trips correctly")
        void snapshotAt_roundTrips() {
            UUID playerId = UUID.randomUUID();
            Instant snapshotTime = Instant.now().minusSeconds(3600);
            InventorySnapshot snapshot = createSnapshotWithTime(playerId, snapshotTime);

            UUID savedId = blockingGet(snapshotStorage.save(snapshot));
            Optional<InventorySnapshot> retrieved = blockingGet(snapshotStorage.getById(savedId));

            assertThat(retrieved).isPresent();
            // Postgres truncates to microseconds, so we compare to second precision
            assertThat(retrieved.get().snapshotAt().getEpochSecond()).isEqualTo(snapshotTime.getEpochSecond());
        }
    }

    @Test
    @DisplayName("Different players have separate snapshots")
    void differentPlayers_separateSnapshots() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        blockingGet(snapshotStorage.save(createSnapshot(player1)));
        blockingGet(snapshotStorage.save(createSnapshot(player1)));
        blockingGet(snapshotStorage.save(createSnapshot(player2)));

        List<InventorySnapshot> player1Snapshots = blockingList(snapshotStorage.listByPlayer(player1, 10, 0));
        List<InventorySnapshot> player2Snapshots = blockingList(snapshotStorage.listByPlayer(player2, 10, 0));

        assertThat(player1Snapshots).hasSize(2);
        assertThat(player2Snapshots).hasSize(1);
    }

    // Helper methods

    private InventorySnapshot createSnapshot(UUID playerId) {
        return new InventorySnapshot(
                UUID.randomUUID(),
                playerId,
                new byte[0],
                new byte[0],
                new byte[0],
                new byte[0],
                0, 0.0f,
                20.0, 20.0,
                20, 5.0f,
                Collections.emptyList(),
                Collections.emptyMap(),
                Instant.now()
        );
    }

    private InventorySnapshot createSnapshotWithData(UUID playerId, byte[] inventory, byte[] armor,
                                                       byte[] offhand, byte[] enderChest) {
        return new InventorySnapshot(
                UUID.randomUUID(),
                playerId,
                inventory,
                armor,
                offhand,
                enderChest,
                0, 0.0f,
                20.0, 20.0,
                20, 5.0f,
                Collections.emptyList(),
                Collections.emptyMap(),
                Instant.now()
        );
    }

    private InventorySnapshot createSnapshotWithStats(UUID playerId, int xpLevel, float xpProgress,
                                                        double health, double maxHealth,
                                                        int hunger, float saturation) {
        return new InventorySnapshot(
                UUID.randomUUID(),
                playerId,
                new byte[0],
                new byte[0],
                new byte[0],
                new byte[0],
                xpLevel, xpProgress,
                health, maxHealth,
                hunger, saturation,
                Collections.emptyList(),
                Collections.emptyMap(),
                Instant.now()
        );
    }

    private InventorySnapshot createSnapshotWithEffects(UUID playerId, List<InventorySnapshot.EffectData> effects) {
        return new InventorySnapshot(
                UUID.randomUUID(),
                playerId,
                new byte[0],
                new byte[0],
                new byte[0],
                new byte[0],
                0, 0.0f,
                20.0, 20.0,
                20, 5.0f,
                effects,
                Collections.emptyMap(),
                Instant.now()
        );
    }

    private InventorySnapshot createSnapshotWithLabels(UUID playerId, Map<String, Object> labels) {
        return new InventorySnapshot(
                UUID.randomUUID(),
                playerId,
                new byte[0],
                new byte[0],
                new byte[0],
                new byte[0],
                0, 0.0f,
                20.0, 20.0,
                20, 5.0f,
                Collections.emptyList(),
                labels,
                Instant.now()
        );
    }

    private InventorySnapshot createSnapshotWithTime(UUID playerId, Instant snapshotAt) {
        return new InventorySnapshot(
                UUID.randomUUID(),
                playerId,
                new byte[0],
                new byte[0],
                new byte[0],
                new byte[0],
                0, 0.0f,
                20.0, 20.0,
                20, 5.0f,
                Collections.emptyList(),
                Collections.emptyMap(),
                snapshotAt
        );
    }
}
