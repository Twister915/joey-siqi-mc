package sh.joey.mc.multiworld;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for PlayerLastWorldStorage.
 */
class PlayerLastWorldStorageIntegrationTest extends PostgresIntegrationTest {

    private PlayerLastWorldStorage lastWorldStorage;

    @BeforeEach
    void setUpStorage() {
        lastWorldStorage = new PlayerLastWorldStorage(storage);
    }

    @Test
    @DisplayName("Get last world returns empty when not tracked")
    void getLastWorld_notTracked_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<PlayerLastWorldStorage.LastWorld> lastWorld = blockingGet(lastWorldStorage.getLastWorld(playerId));

        assertThat(lastWorld).isEmpty();
    }

    @Test
    @DisplayName("Set and get last world")
    void setAndGetLastWorld() {
        UUID playerId = UUID.randomUUID();
        UUID worldUuid = UUID.randomUUID();
        String inventoryGroup = "survival";

        blockingAwait(lastWorldStorage.setLastWorld(playerId, worldUuid, inventoryGroup));

        Optional<PlayerLastWorldStorage.LastWorld> retrieved = blockingGet(lastWorldStorage.getLastWorld(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().worldUuid()).isEqualTo(worldUuid);
        assertThat(retrieved.get().inventoryGroup()).isEqualTo(inventoryGroup);
    }

    @Test
    @DisplayName("Set last world twice results in single row (UPSERT)")
    void setLastWorldTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID world1 = UUID.randomUUID();
        UUID world2 = UUID.randomUUID();

        blockingAwait(lastWorldStorage.setLastWorld(playerId, world1, "group-a"));
        blockingAwait(lastWorldStorage.setLastWorld(playerId, world2, "group-b"));

        int rowCount = countRows(
                "SELECT COUNT(*) FROM player_last_worlds WHERE player_id = '" + playerId + "'"
        );
        assertThat(rowCount).isEqualTo(1);

        Optional<PlayerLastWorldStorage.LastWorld> retrieved = blockingGet(lastWorldStorage.getLastWorld(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().worldUuid()).isEqualTo(world2);
        assertThat(retrieved.get().inventoryGroup()).isEqualTo("group-b");
    }

    @Test
    @DisplayName("Different players have separate last world entries")
    void differentPlayers_separateEntries() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID world1 = UUID.randomUUID();
        UUID world2 = UUID.randomUUID();

        blockingAwait(lastWorldStorage.setLastWorld(player1, world1, "survival"));
        blockingAwait(lastWorldStorage.setLastWorld(player2, world2, "creative"));

        var lastWorld1 = blockingGet(lastWorldStorage.getLastWorld(player1));
        var lastWorld2 = blockingGet(lastWorldStorage.getLastWorld(player2));

        assertThat(lastWorld1).isPresent();
        assertThat(lastWorld1.get().worldUuid()).isEqualTo(world1);
        assertThat(lastWorld1.get().inventoryGroup()).isEqualTo("survival");

        assertThat(lastWorld2).isPresent();
        assertThat(lastWorld2.get().worldUuid()).isEqualTo(world2);
        assertThat(lastWorld2.get().inventoryGroup()).isEqualTo("creative");
    }

    @Test
    @DisplayName("Updated_at is set on insert and update")
    void updatedAt_setOnInsertAndUpdate() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID world1 = UUID.randomUUID();
        UUID world2 = UUID.randomUUID();

        blockingAwait(lastWorldStorage.setLastWorld(playerId, world1, "group1"));

        // Verify updated_at is set
        int hasUpdatedAt = countRows(
                "SELECT COUNT(*) FROM player_last_worlds WHERE player_id = '" + playerId + "' AND updated_at IS NOT NULL"
        );
        assertThat(hasUpdatedAt).isEqualTo(1);

        // Update and verify it's still set (in practice would change, but we just verify NOT NULL)
        blockingAwait(lastWorldStorage.setLastWorld(playerId, world2, "group2"));

        hasUpdatedAt = countRows(
                "SELECT COUNT(*) FROM player_last_worlds WHERE player_id = '" + playerId + "' AND updated_at IS NOT NULL"
        );
        assertThat(hasUpdatedAt).isEqualTo(1);
    }
}
