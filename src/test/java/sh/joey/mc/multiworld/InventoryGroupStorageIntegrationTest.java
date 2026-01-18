package sh.joey.mc.multiworld;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.inventory.InventorySnapshot;
import sh.joey.mc.inventory.InventorySnapshotStorage;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for InventoryGroupStorage.
 * Tests the pivot table mapping (player_id, inventory_group) to snapshot_id.
 */
class InventoryGroupStorageIntegrationTest extends PostgresIntegrationTest {

    private InventoryGroupStorage inventoryGroupStorage;
    private InventorySnapshotStorage snapshotStorage;

    @BeforeEach
    void setUpStorage() {
        inventoryGroupStorage = new InventoryGroupStorage(storage);
        snapshotStorage = new InventorySnapshotStorage(storage);
    }

    /**
     * Creates a test inventory snapshot and returns its ID.
     * Required because inventory_group_snapshots has a FK to inventory_snapshots.
     */
    private UUID createTestSnapshot(UUID playerId) {
        UUID snapshotId = UUID.randomUUID();
        InventorySnapshot snapshot = new InventorySnapshot(
                snapshotId,
                playerId,
                new byte[0],  // inventoryData
                new byte[0],  // armorData
                new byte[0],  // offhandData
                new byte[0],  // enderChestData
                0,            // xpLevel
                0.0f,         // xpProgress
                20.0,         // health
                20.0,         // maxHealth
                20,           // hunger
                5.0f,         // saturation
                Collections.emptyList(),  // effects
                Collections.emptyMap(),   // labels
                Instant.now() // snapshotAt
        );
        return blockingGet(snapshotStorage.save(snapshot));
    }

    @Test
    @DisplayName("Get snapshot for group returns empty when no snapshot exists")
    void getSnapshotForGroup_noSnapshot_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<UUID> snapshot = blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "survival"));

        assertThat(snapshot).isEmpty();
    }

    @Test
    @DisplayName("Set and get snapshot for group")
    void setAndGetSnapshotForGroup() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID snapshotId = createTestSnapshot(playerId);

        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "survival", snapshotId));

        Optional<UUID> retrieved = blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "survival"));
        assertThat(retrieved).hasValue(snapshotId);
    }

    @Test
    @DisplayName("Set snapshot twice for same group results in single row (UPSERT)")
    void setSnapshotTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID snapshot1 = createTestSnapshot(playerId);
        UUID snapshot2 = createTestSnapshot(playerId);

        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "survival", snapshot1));
        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "survival", snapshot2));

        int rowCount = countRows(
                "SELECT COUNT(*) FROM inventory_group_snapshots WHERE player_id = '" + playerId + "' AND inventory_group = 'survival'"
        );
        assertThat(rowCount).isEqualTo(1);

        Optional<UUID> retrieved = blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "survival"));
        assertThat(retrieved).hasValue(snapshot2);
    }

    @Test
    @DisplayName("Different inventory groups have separate snapshots")
    void differentGroups_separateSnapshots() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID survivalSnapshot = createTestSnapshot(playerId);
        UUID creativeSnapshot = createTestSnapshot(playerId);

        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "survival", survivalSnapshot));
        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "creative", creativeSnapshot));

        assertThat(blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "survival")))
                .hasValue(survivalSnapshot);
        assertThat(blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "creative")))
                .hasValue(creativeSnapshot);
    }

    @Test
    @DisplayName("Different players have separate snapshots")
    void differentPlayers_separateSnapshots() throws SQLException {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID snapshot1 = createTestSnapshot(player1);
        UUID snapshot2 = createTestSnapshot(player2);

        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(player1, "survival", snapshot1));
        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(player2, "survival", snapshot2));

        assertThat(blockingGet(inventoryGroupStorage.getSnapshotForGroup(player1, "survival")))
                .hasValue(snapshot1);
        assertThat(blockingGet(inventoryGroupStorage.getSnapshotForGroup(player2, "survival")))
                .hasValue(snapshot2);
    }

    @Test
    @DisplayName("Clear group removes entry")
    void clearGroup_removesEntry() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID snapshotId = createTestSnapshot(playerId);

        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "survival", snapshotId));
        blockingAwait(inventoryGroupStorage.clearGroup(playerId, "survival"));

        Optional<UUID> retrieved = blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "survival"));
        assertThat(retrieved).isEmpty();

        int rowCount = countRows(
                "SELECT COUNT(*) FROM inventory_group_snapshots WHERE player_id = '" + playerId + "' AND inventory_group = 'survival'"
        );
        assertThat(rowCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Clear non-existent group is no-op")
    void clearNonExistentGroup_noOp() {
        UUID playerId = UUID.randomUUID();

        // Should not throw
        blockingAwait(inventoryGroupStorage.clearGroup(playerId, "non-existent"));

        Optional<UUID> retrieved = blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "non-existent"));
        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("Clear one group does not affect other groups")
    void clearGroup_doesNotAffectOthers() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID survivalSnapshot = createTestSnapshot(playerId);
        UUID creativeSnapshot = createTestSnapshot(playerId);

        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "survival", survivalSnapshot));
        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "creative", creativeSnapshot));

        blockingAwait(inventoryGroupStorage.clearGroup(playerId, "survival"));

        assertThat(blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "survival"))).isEmpty();
        assertThat(blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "creative")))
                .hasValue(creativeSnapshot);
    }

    @Test
    @DisplayName("Inventory group name is case-sensitive")
    void inventoryGroup_caseSensitive() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID snapshot1 = createTestSnapshot(playerId);
        UUID snapshot2 = createTestSnapshot(playerId);

        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "survival", snapshot1));
        blockingAwait(inventoryGroupStorage.setSnapshotForGroup(playerId, "Survival", snapshot2));

        // These should be treated as separate groups
        assertThat(blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "survival")))
                .hasValue(snapshot1);
        assertThat(blockingGet(inventoryGroupStorage.getSnapshotForGroup(playerId, "Survival")))
                .hasValue(snapshot2);
    }
}
