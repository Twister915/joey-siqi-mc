package sh.joey.mc.adminmode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.inventory.InventorySnapshot;
import sh.joey.mc.inventory.InventorySnapshotStorage;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for AdminModeStorage.
 */
class AdminModeStorageIntegrationTest extends PostgresIntegrationTest {

    private AdminModeStorage adminModeStorage;
    private InventorySnapshotStorage snapshotStorage;

    @BeforeEach
    void setUpStorage() {
        adminModeStorage = new AdminModeStorage(storage);
        snapshotStorage = new InventorySnapshotStorage(storage);
    }

    /**
     * Creates a test inventory snapshot and returns its ID.
     * Required because admin_mode_state has a FK to inventory_snapshots.
     */
    private UUID createTestSnapshot(UUID playerId) {
        UUID snapshotId = UUID.randomUUID();
        InventorySnapshot snapshot = new InventorySnapshot(
                snapshotId,
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
        return blockingGet(snapshotStorage.save(snapshot));
    }

    @Test
    @DisplayName("Get state returns empty when player not in admin mode")
    void getState_notInAdminMode_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<AdminModeState> state = blockingGet(adminModeStorage.getState(playerId));

        assertThat(state).isEmpty();
    }

    @Test
    @DisplayName("Enter admin mode and get state")
    void enterAdminModeAndGetState() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID snapshotId = createTestSnapshot(playerId);
        Instant before = Instant.now().minusSeconds(1);

        blockingAwait(adminModeStorage.enterAdminMode(playerId, worldId, snapshotId));

        Optional<AdminModeState> state = blockingGet(adminModeStorage.getState(playerId));
        assertThat(state).isPresent();
        assertThat(state.get().playerId()).isEqualTo(playerId);
        assertThat(state.get().worldId()).isEqualTo(worldId);
        assertThat(state.get().snapshotId()).isEqualTo(snapshotId);
        assertThat(state.get().enteredAt()).isAfter(before);
    }

    @Test
    @DisplayName("Enter admin mode twice results in single row (UPSERT)")
    void enterAdminModeTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId1 = UUID.randomUUID();
        UUID worldId2 = UUID.randomUUID();
        UUID snapshotId1 = createTestSnapshot(playerId);
        UUID snapshotId2 = createTestSnapshot(playerId);

        blockingAwait(adminModeStorage.enterAdminMode(playerId, worldId1, snapshotId1));
        blockingAwait(adminModeStorage.enterAdminMode(playerId, worldId2, snapshotId2));

        int rowCount = countRows("SELECT COUNT(*) FROM admin_mode_state WHERE player_id = '" + playerId + "'");
        assertThat(rowCount).isEqualTo(1);

        Optional<AdminModeState> state = blockingGet(adminModeStorage.getState(playerId));
        assertThat(state).isPresent();
        assertThat(state.get().worldId()).isEqualTo(worldId2);
        assertThat(state.get().snapshotId()).isEqualTo(snapshotId2);
    }

    @Test
    @DisplayName("Exit admin mode removes entry")
    void exitAdminMode_removesEntry() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID snapshotId = createTestSnapshot(playerId);

        blockingAwait(adminModeStorage.enterAdminMode(playerId, worldId, snapshotId));
        blockingAwait(adminModeStorage.exitAdminMode(playerId));

        Optional<AdminModeState> state = blockingGet(adminModeStorage.getState(playerId));
        assertThat(state).isEmpty();

        int rowCount = countRows("SELECT COUNT(*) FROM admin_mode_state WHERE player_id = '" + playerId + "'");
        assertThat(rowCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Exit admin mode for non-existent player is no-op")
    void exitAdminMode_nonExistentPlayer_noOp() {
        UUID playerId = UUID.randomUUID();

        // Should not throw
        blockingAwait(adminModeStorage.exitAdminMode(playerId));

        Optional<AdminModeState> state = blockingGet(adminModeStorage.getState(playerId));
        assertThat(state).isEmpty();
    }

    @Test
    @DisplayName("Different players have separate admin mode states")
    void differentPlayers_separateStates() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID worldId1 = UUID.randomUUID();
        UUID worldId2 = UUID.randomUUID();
        UUID snapshotId1 = createTestSnapshot(player1);
        UUID snapshotId2 = createTestSnapshot(player2);

        blockingAwait(adminModeStorage.enterAdminMode(player1, worldId1, snapshotId1));
        blockingAwait(adminModeStorage.enterAdminMode(player2, worldId2, snapshotId2));

        var state1 = blockingGet(adminModeStorage.getState(player1));
        var state2 = blockingGet(adminModeStorage.getState(player2));

        assertThat(state1).isPresent();
        assertThat(state1.get().worldId()).isEqualTo(worldId1);

        assertThat(state2).isPresent();
        assertThat(state2.get().worldId()).isEqualTo(worldId2);
    }

    @Test
    @DisplayName("Get all in admin mode returns empty when no players")
    void getAllInAdminMode_noPlayers_returnsEmpty() {
        List<AdminModeState> states = blockingList(adminModeStorage.getAllInAdminMode());
        assertThat(states).isEmpty();
    }

    @Test
    @DisplayName("Get all in admin mode returns all entries")
    void getAllInAdminMode_returnsAllEntries() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID snapshotId1 = createTestSnapshot(player1);
        UUID snapshotId2 = createTestSnapshot(player2);
        UUID snapshotId3 = createTestSnapshot(player3);

        blockingAwait(adminModeStorage.enterAdminMode(player1, worldId, snapshotId1));
        blockingAwait(adminModeStorage.enterAdminMode(player2, worldId, snapshotId2));
        blockingAwait(adminModeStorage.enterAdminMode(player3, worldId, snapshotId3));

        List<AdminModeState> states = blockingList(adminModeStorage.getAllInAdminMode());

        assertThat(states).hasSize(3);
        assertThat(states).extracting(AdminModeState::playerId)
                .containsExactlyInAnyOrder(player1, player2, player3);
    }

    @Test
    @DisplayName("Get all in admin mode excludes exited players")
    void getAllInAdminMode_excludesExitedPlayers() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        UUID snapshotId1 = createTestSnapshot(player1);
        UUID snapshotId2 = createTestSnapshot(player2);

        blockingAwait(adminModeStorage.enterAdminMode(player1, worldId, snapshotId1));
        blockingAwait(adminModeStorage.enterAdminMode(player2, worldId, snapshotId2));
        blockingAwait(adminModeStorage.exitAdminMode(player1));

        List<AdminModeState> states = blockingList(adminModeStorage.getAllInAdminMode());

        assertThat(states).hasSize(1);
        assertThat(states.get(0).playerId()).isEqualTo(player2);
    }
}
