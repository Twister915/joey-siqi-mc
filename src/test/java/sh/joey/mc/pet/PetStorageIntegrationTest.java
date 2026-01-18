package sh.joey.mc.pet;

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
 * Integration tests for PetStorage.
 */
class PetStorageIntegrationTest extends PostgresIntegrationTest {

    private PetStorage petStorage;

    @BeforeEach
    void setUpStorage() {
        petStorage = new PetStorage(storage);
    }

    @Test
    @DisplayName("Get active pet returns empty when no pet")
    void getActivePet_noPet_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<ActivePet> pet = blockingGet(petStorage.getActivePet(playerId));

        assertThat(pet).isEmpty();
    }

    @Test
    @DisplayName("Save and get active pet")
    void saveAndGetActivePet() {
        UUID playerId = UUID.randomUUID();

        blockingAwait(petStorage.savePet(playerId, PetType.HAMSTER, PetState.FOLLOWING));

        Optional<ActivePet> retrieved = blockingGet(petStorage.getActivePet(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().playerId()).isEqualTo(playerId);
        assertThat(retrieved.get().type()).isEqualTo(PetType.HAMSTER);
        assertThat(retrieved.get().state()).isEqualTo(PetState.FOLLOWING);
    }

    @Test
    @DisplayName("Save pet twice results in single row (UPSERT)")
    void savePetTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();

        blockingAwait(petStorage.savePet(playerId, PetType.HAMSTER, PetState.FOLLOWING));
        blockingAwait(petStorage.savePet(playerId, PetType.HAMSTER, PetState.SITTING));

        int rowCount = countRows(
                "SELECT COUNT(*) FROM player_pets WHERE player_id = '" + playerId + "'"
        );
        assertThat(rowCount).isEqualTo(1);

        Optional<ActivePet> retrieved = blockingGet(petStorage.getActivePet(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().state()).isEqualTo(PetState.SITTING);
    }

    @Test
    @DisplayName("Update state changes only state field")
    void updateState_changesOnlyState() {
        UUID playerId = UUID.randomUUID();

        blockingAwait(petStorage.savePet(playerId, PetType.HAMSTER, PetState.FOLLOWING));
        blockingAwait(petStorage.updateState(playerId, PetState.SITTING));

        Optional<ActivePet> retrieved = blockingGet(petStorage.getActivePet(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().type()).isEqualTo(PetType.HAMSTER);
        assertThat(retrieved.get().state()).isEqualTo(PetState.SITTING);
    }

    @Test
    @DisplayName("Remove pet deletes entry")
    void removePet_deletesEntry() throws SQLException {
        UUID playerId = UUID.randomUUID();

        blockingAwait(petStorage.savePet(playerId, PetType.HAMSTER, PetState.FOLLOWING));
        blockingAwait(petStorage.removePet(playerId));

        Optional<ActivePet> retrieved = blockingGet(petStorage.getActivePet(playerId));
        assertThat(retrieved).isEmpty();

        int rowCount = countRows(
                "SELECT COUNT(*) FROM player_pets WHERE player_id = '" + playerId + "'"
        );
        assertThat(rowCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Remove non-existent pet is no-op")
    void removeNonExistentPet_noOp() {
        UUID playerId = UUID.randomUUID();

        // Should not throw
        blockingAwait(petStorage.removePet(playerId));

        Optional<ActivePet> retrieved = blockingGet(petStorage.getActivePet(playerId));
        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("Different players have separate pets")
    void differentPlayers_separatePets() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        blockingAwait(petStorage.savePet(player1, PetType.HAMSTER, PetState.FOLLOWING));
        blockingAwait(petStorage.savePet(player2, PetType.HAMSTER, PetState.SITTING));

        var pet1 = blockingGet(petStorage.getActivePet(player1));
        var pet2 = blockingGet(petStorage.getActivePet(player2));

        assertThat(pet1).isPresent();
        assertThat(pet1.get().state()).isEqualTo(PetState.FOLLOWING);

        assertThat(pet2).isPresent();
        assertThat(pet2.get().state()).isEqualTo(PetState.SITTING);
    }

    @Test
    @DisplayName("Pet type is stored and retrieved correctly")
    void petType_storedCorrectly() throws SQLException {
        UUID playerId = UUID.randomUUID();

        blockingAwait(petStorage.savePet(playerId, PetType.HAMSTER, PetState.FOLLOWING));

        // Verify raw value in database
        int hamsterCount = countRows(
                "SELECT COUNT(*) FROM player_pets WHERE player_id = '" + playerId + "' AND pet_type = 'hamster'"
        );
        assertThat(hamsterCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Pet state is stored and retrieved correctly")
    void petState_storedCorrectly() throws SQLException {
        UUID playerId = UUID.randomUUID();

        blockingAwait(petStorage.savePet(playerId, PetType.HAMSTER, PetState.SITTING));

        // Verify raw value in database
        int sittingCount = countRows(
                "SELECT COUNT(*) FROM player_pets WHERE player_id = '" + playerId + "' AND state = 'sitting'"
        );
        assertThat(sittingCount).isEqualTo(1);
    }
}
