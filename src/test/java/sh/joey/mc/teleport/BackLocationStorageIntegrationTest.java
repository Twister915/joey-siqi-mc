package sh.joey.mc.teleport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for BackLocationStorage.
 */
class BackLocationStorageIntegrationTest extends PostgresIntegrationTest {

    private BackLocationStorage backLocationStorage;

    @BeforeEach
    void setUpStorage() {
        backLocationStorage = new BackLocationStorage(storage);
    }

    @Test
    @DisplayName("Get back location returns empty when no location saved")
    void getBackLocation_noLocation_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<BackLocation> location = blockingGet(backLocationStorage.getBackLocation(playerId));

        assertThat(location).isEmpty();
    }

    @Test
    @DisplayName("Save and get back location")
    void saveAndGetBackLocation() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        BackLocation location = new BackLocation(
                playerId,
                BackLocation.LocationType.DEATH,
                worldId,
                100.5, 64.0, -200.25,
                45.0f, -30.0f
        );

        blockingAwait(backLocationStorage.saveLocation(location));

        Optional<BackLocation> retrieved = blockingGet(backLocationStorage.getBackLocation(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().playerId()).isEqualTo(playerId);
        assertThat(retrieved.get().type()).isEqualTo(BackLocation.LocationType.DEATH);
        assertThat(retrieved.get().worldId()).isEqualTo(worldId);
        assertThat(retrieved.get().x()).isCloseTo(100.5, within(0.001));
        assertThat(retrieved.get().y()).isCloseTo(64.0, within(0.001));
        assertThat(retrieved.get().z()).isCloseTo(-200.25, within(0.001));
        assertThat(retrieved.get().pitch()).isCloseTo(45.0f, within(0.001f));
        assertThat(retrieved.get().yaw()).isCloseTo(-30.0f, within(0.001f));
    }

    @Test
    @DisplayName("Save location twice results in single row (UPSERT)")
    void saveLocationTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId1 = UUID.randomUUID();
        UUID worldId2 = UUID.randomUUID();

        BackLocation location1 = new BackLocation(playerId, BackLocation.LocationType.DEATH, worldId1, 0, 64, 0, 0, 0);
        BackLocation location2 = new BackLocation(playerId, BackLocation.LocationType.TELEPORT, worldId2, 100, 128, 200, 90, 45);

        blockingAwait(backLocationStorage.saveLocation(location1));
        blockingAwait(backLocationStorage.saveLocation(location2));

        int rowCount = countRows("SELECT COUNT(*) FROM back_locations WHERE player_id = '" + playerId + "'");
        assertThat(rowCount).isEqualTo(1);

        Optional<BackLocation> retrieved = blockingGet(backLocationStorage.getBackLocation(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().type()).isEqualTo(BackLocation.LocationType.TELEPORT);
        assertThat(retrieved.get().worldId()).isEqualTo(worldId2);
    }

    @Test
    @DisplayName("Different players have separate back locations")
    void differentPlayers_separateLocations() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        BackLocation loc1 = new BackLocation(player1, BackLocation.LocationType.DEATH, worldId, 10, 64, 10, 0, 0);
        BackLocation loc2 = new BackLocation(player2, BackLocation.LocationType.TELEPORT, worldId, 100, 128, 100, 0, 0);

        blockingAwait(backLocationStorage.saveLocation(loc1));
        blockingAwait(backLocationStorage.saveLocation(loc2));

        var retrieved1 = blockingGet(backLocationStorage.getBackLocation(player1));
        var retrieved2 = blockingGet(backLocationStorage.getBackLocation(player2));

        assertThat(retrieved1).isPresent();
        assertThat(retrieved1.get().type()).isEqualTo(BackLocation.LocationType.DEATH);

        assertThat(retrieved2).isPresent();
        assertThat(retrieved2.get().type()).isEqualTo(BackLocation.LocationType.TELEPORT);
    }

    @Test
    @DisplayName("Death location type is stored correctly")
    void deathLocationType_storedCorrectly() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        BackLocation location = new BackLocation(playerId, BackLocation.LocationType.DEATH, worldId, 0, 64, 0, 0, 0);
        blockingAwait(backLocationStorage.saveLocation(location));

        int deathCount = countRows(
                "SELECT COUNT(*) FROM back_locations WHERE player_id = '" + playerId + "' AND location_type = 'death'"
        );
        assertThat(deathCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Teleport location type is stored correctly")
    void teleportLocationType_storedCorrectly() throws SQLException {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        BackLocation location = new BackLocation(playerId, BackLocation.LocationType.TELEPORT, worldId, 0, 64, 0, 0, 0);
        blockingAwait(backLocationStorage.saveLocation(location));

        int teleportCount = countRows(
                "SELECT COUNT(*) FROM back_locations WHERE player_id = '" + playerId + "' AND location_type = 'teleport'"
        );
        assertThat(teleportCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Negative coordinates are stored correctly")
    void negativeCoordinates_storedCorrectly() {
        UUID playerId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();

        BackLocation location = new BackLocation(playerId, BackLocation.LocationType.DEATH, worldId, -1000.75, -64.0, -500.5, -180.0f, -90.0f);
        blockingAwait(backLocationStorage.saveLocation(location));

        Optional<BackLocation> retrieved = blockingGet(backLocationStorage.getBackLocation(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().x()).isCloseTo(-1000.75, within(0.001));
        assertThat(retrieved.get().y()).isCloseTo(-64.0, within(0.001));
        assertThat(retrieved.get().z()).isCloseTo(-500.5, within(0.001));
        assertThat(retrieved.get().pitch()).isCloseTo(-180.0f, within(0.001f));
        assertThat(retrieved.get().yaw()).isCloseTo(-90.0f, within(0.001f));
    }
}
