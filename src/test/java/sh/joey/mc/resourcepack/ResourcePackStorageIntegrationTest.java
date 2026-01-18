package sh.joey.mc.resourcepack;

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
 * Integration tests for ResourcePackStorage.
 */
class ResourcePackStorageIntegrationTest extends PostgresIntegrationTest {

    private ResourcePackStorage resourcePackStorage;

    @BeforeEach
    void setUpStorage() {
        resourcePackStorage = new ResourcePackStorage(storage);
    }

    @Test
    @DisplayName("Get player pack returns empty when no pack set")
    void getPlayerPack_noPackSet_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<String> pack = blockingGet(resourcePackStorage.getPlayerPack(playerId));

        assertThat(pack).isEmpty();
    }

    @Test
    @DisplayName("Set and get player pack")
    void setAndGetPlayerPack() {
        UUID playerId = UUID.randomUUID();
        String packId = "my-custom-pack";

        blockingAwait(resourcePackStorage.setPlayerPack(playerId, packId));

        Optional<String> retrieved = blockingGet(resourcePackStorage.getPlayerPack(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo("my-custom-pack"); // normalized to lowercase
    }

    @Test
    @DisplayName("Set player pack normalizes to lowercase")
    void setPlayerPack_normalizesToLowercase() {
        UUID playerId = UUID.randomUUID();

        blockingAwait(resourcePackStorage.setPlayerPack(playerId, "My-CUSTOM-Pack"));

        Optional<String> retrieved = blockingGet(resourcePackStorage.getPlayerPack(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get()).isEqualTo("my-custom-pack");
    }

    @Test
    @DisplayName("Set player pack twice results in single row (UPSERT)")
    void setPlayerPackTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();

        blockingAwait(resourcePackStorage.setPlayerPack(playerId, "pack-one"));
        blockingAwait(resourcePackStorage.setPlayerPack(playerId, "pack-two"));

        int rowCount = countRows(
                "SELECT COUNT(*) FROM player_resource_packs WHERE player_id = '" + playerId + "'"
        );
        assertThat(rowCount).isEqualTo(1);

        Optional<String> retrieved = blockingGet(resourcePackStorage.getPlayerPack(playerId));
        assertThat(retrieved).hasValue("pack-two");
    }

    @Test
    @DisplayName("Clear player pack removes entry")
    void clearPlayerPack() throws SQLException {
        UUID playerId = UUID.randomUUID();

        blockingAwait(resourcePackStorage.setPlayerPack(playerId, "some-pack"));
        blockingAwait(resourcePackStorage.clearPlayerPack(playerId));

        Optional<String> retrieved = blockingGet(resourcePackStorage.getPlayerPack(playerId));
        assertThat(retrieved).isEmpty();

        int rowCount = countRows(
                "SELECT COUNT(*) FROM player_resource_packs WHERE player_id = '" + playerId + "'"
        );
        assertThat(rowCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Clear non-existent pack is no-op")
    void clearNonExistentPack_noOp() {
        UUID playerId = UUID.randomUUID();

        // Should not throw
        blockingAwait(resourcePackStorage.clearPlayerPack(playerId));

        Optional<String> retrieved = blockingGet(resourcePackStorage.getPlayerPack(playerId));
        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("Different players have separate packs")
    void differentPlayers_separatePacks() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        blockingAwait(resourcePackStorage.setPlayerPack(player1, "pack-a"));
        blockingAwait(resourcePackStorage.setPlayerPack(player2, "pack-b"));

        assertThat(blockingGet(resourcePackStorage.getPlayerPack(player1))).hasValue("pack-a");
        assertThat(blockingGet(resourcePackStorage.getPlayerPack(player2))).hasValue("pack-b");
    }
}
