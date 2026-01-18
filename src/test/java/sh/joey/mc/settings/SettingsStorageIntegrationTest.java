package sh.joey.mc.settings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for SettingsStorage.
 */
class SettingsStorageIntegrationTest extends PostgresIntegrationTest {

    private SettingsStorage settingsStorage;

    @BeforeEach
    void setUpStorage() {
        settingsStorage = new SettingsStorage(storage);
    }

    @Test
    @DisplayName("Get settings returns empty when no settings saved")
    void getSettings_noSettings_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<PlayerSettings> settings = blockingGet(settingsStorage.getSettings(playerId));

        assertThat(settings).isEmpty();
    }

    @Test
    @DisplayName("Save and get settings")
    void saveAndGetSettings() {
        UUID playerId = UUID.randomUUID();
        PlayerSettings settings = new PlayerSettings(true, DisplayTimeSetting.HOLDING_CLOCK, true, false);

        blockingAwait(settingsStorage.saveSettings(playerId, settings));

        Optional<PlayerSettings> retrieved = blockingGet(settingsStorage.getSettings(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().keepInventory()).isTrue();
        assertThat(retrieved.get().displayTime()).isEqualTo(DisplayTimeSetting.HOLDING_CLOCK);
        assertThat(retrieved.get().easyMode()).isTrue();
        assertThat(retrieved.get().passiveMode()).isFalse();
    }

    @Test
    @DisplayName("Save settings twice results in single row (UPSERT)")
    void saveSettingsTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();

        PlayerSettings settings1 = new PlayerSettings(false, DisplayTimeSetting.ALWAYS, false, false);
        PlayerSettings settings2 = new PlayerSettings(true, DisplayTimeSetting.NEVER, true, true);

        blockingAwait(settingsStorage.saveSettings(playerId, settings1));
        blockingAwait(settingsStorage.saveSettings(playerId, settings2));

        int rowCount = countRows("SELECT COUNT(*) FROM player_settings WHERE player_id = '" + playerId + "'");
        assertThat(rowCount).isEqualTo(1);

        Optional<PlayerSettings> retrieved = blockingGet(settingsStorage.getSettings(playerId));
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().keepInventory()).isTrue();
        assertThat(retrieved.get().displayTime()).isEqualTo(DisplayTimeSetting.NEVER);
        assertThat(retrieved.get().easyMode()).isTrue();
        assertThat(retrieved.get().passiveMode()).isTrue();
    }

    @Test
    @DisplayName("Different players have separate settings")
    void differentPlayers_separateSettings() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        PlayerSettings settings1 = new PlayerSettings(true, DisplayTimeSetting.ALWAYS, false, false);
        PlayerSettings settings2 = new PlayerSettings(false, DisplayTimeSetting.NEVER, true, true);

        blockingAwait(settingsStorage.saveSettings(player1, settings1));
        blockingAwait(settingsStorage.saveSettings(player2, settings2));

        var retrieved1 = blockingGet(settingsStorage.getSettings(player1));
        var retrieved2 = blockingGet(settingsStorage.getSettings(player2));

        assertThat(retrieved1).isPresent();
        assertThat(retrieved1.get().keepInventory()).isTrue();
        assertThat(retrieved1.get().easyMode()).isFalse();

        assertThat(retrieved2).isPresent();
        assertThat(retrieved2.get().keepInventory()).isFalse();
        assertThat(retrieved2.get().easyMode()).isTrue();
    }

    @Test
    @DisplayName("All display time settings are stored correctly")
    void allDisplayTimeSettings_storedCorrectly() {
        for (DisplayTimeSetting setting : DisplayTimeSetting.values()) {
            UUID playerId = UUID.randomUUID();
            PlayerSettings settings = new PlayerSettings(false, setting, false, false);

            blockingAwait(settingsStorage.saveSettings(playerId, settings));

            Optional<PlayerSettings> retrieved = blockingGet(settingsStorage.getSettings(playerId));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().displayTime()).isEqualTo(setting);
        }
    }

    @Test
    @DisplayName("Get all settings returns all entries")
    void getAllSettings_returnsAllEntries() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        blockingAwait(settingsStorage.saveSettings(player1, PlayerSettings.DEFAULTS));
        blockingAwait(settingsStorage.saveSettings(player2, PlayerSettings.DEFAULTS.withKeepInventory(true)));
        blockingAwait(settingsStorage.saveSettings(player3, PlayerSettings.DEFAULTS.withEasyMode(true)));

        List<Map.Entry<UUID, PlayerSettings>> allSettings = blockingList(settingsStorage.getAllSettings());

        assertThat(allSettings).hasSize(3);
        assertThat(allSettings).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder(player1, player2, player3);
    }

    @Test
    @DisplayName("Get all settings returns empty list when no settings")
    void getAllSettings_noSettings_returnsEmptyList() {
        List<Map.Entry<UUID, PlayerSettings>> allSettings = blockingList(settingsStorage.getAllSettings());
        assertThat(allSettings).isEmpty();
    }

    @Test
    @DisplayName("Boolean fields are stored correctly")
    void booleanFields_storedCorrectly() throws SQLException {
        UUID playerId = UUID.randomUUID();
        PlayerSettings settings = new PlayerSettings(true, DisplayTimeSetting.ALWAYS, true, true);

        blockingAwait(settingsStorage.saveSettings(playerId, settings));

        int allTrue = countRows(
                "SELECT COUNT(*) FROM player_settings WHERE player_id = '" + playerId +
                        "' AND keep_inventory = true AND easy_mode = true AND passive_mode = true"
        );
        assertThat(allTrue).isEqualTo(1);
    }
}
