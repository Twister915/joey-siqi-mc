package sh.joey.mc.rtp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for RtpStorage.
 */
class RtpStorageIntegrationTest extends PostgresIntegrationTest {

    private RtpStorage rtpStorage;

    @BeforeEach
    void setUpStorage() {
        rtpStorage = new RtpStorage(storage);
    }

    @Test
    @DisplayName("Get last RTP time returns empty when no usage recorded")
    void getLastRtpTime_noUsage_returnsEmpty() {
        UUID playerId = UUID.randomUUID();

        Optional<Instant> lastTime = blockingGet(rtpStorage.getLastRtpTime(playerId));

        assertThat(lastTime).isEmpty();
    }

    @Test
    @DisplayName("Record and get RTP usage")
    void recordAndGetRtpUsage() {
        UUID playerId = UUID.randomUUID();
        Instant before = Instant.now().minusSeconds(1);

        blockingAwait(rtpStorage.recordRtpUsage(playerId));

        Optional<Instant> lastTime = blockingGet(rtpStorage.getLastRtpTime(playerId));
        assertThat(lastTime).isPresent();
        assertThat(lastTime.get()).isAfter(before);
    }

    @Test
    @DisplayName("Record RTP usage twice results in single row (UPSERT)")
    void recordRtpUsageTwice_singleRow() throws SQLException {
        UUID playerId = UUID.randomUUID();

        blockingAwait(rtpStorage.recordRtpUsage(playerId));
        blockingAwait(rtpStorage.recordRtpUsage(playerId));

        int rowCount = countRows("SELECT COUNT(*) FROM rtp_cooldowns WHERE player_id = '" + playerId + "'");
        assertThat(rowCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Different players have separate RTP records")
    void differentPlayers_separateRecords() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        blockingAwait(rtpStorage.recordRtpUsage(player1));
        // Small delay to ensure different timestamps
        blockingAwait(rtpStorage.recordRtpUsage(player2));

        var time1 = blockingGet(rtpStorage.getLastRtpTime(player1));
        var time2 = blockingGet(rtpStorage.getLastRtpTime(player2));

        assertThat(time1).isPresent();
        assertThat(time2).isPresent();
    }

    @Test
    @DisplayName("Get active cooldowns returns only recent entries")
    void getActiveCooldowns_returnsOnlyRecent() throws SQLException {
        UUID recentPlayer = UUID.randomUUID();
        UUID oldPlayer = UUID.randomUUID();

        // Record a recent usage
        blockingAwait(rtpStorage.recordRtpUsage(recentPlayer));

        // Manually insert an old usage (2 hours ago)
        try (var conn = database.getConnection();
             var stmt = conn.prepareStatement(
                     "INSERT INTO rtp_cooldowns (player_id, last_used_at) VALUES (?, NOW() - INTERVAL '2 hours')")) {
            stmt.setObject(1, oldPlayer);
            stmt.executeUpdate();
        }

        // Get cooldowns with 5 minute duration
        List<RtpStorage.CooldownEntry> cooldowns = blockingList(rtpStorage.getActiveCooldowns(Duration.ofMinutes(5)));

        // Only recent player should be returned
        assertThat(cooldowns).hasSize(1);
        assertThat(cooldowns.get(0).playerId()).isEqualTo(recentPlayer);
    }

    @Test
    @DisplayName("Get active cooldowns returns empty when no recent entries")
    void getActiveCooldowns_noRecent_returnsEmpty() throws SQLException {
        UUID oldPlayer = UUID.randomUUID();

        // Manually insert an old usage (2 hours ago)
        try (var conn = database.getConnection();
             var stmt = conn.prepareStatement(
                     "INSERT INTO rtp_cooldowns (player_id, last_used_at) VALUES (?, NOW() - INTERVAL '2 hours')")) {
            stmt.setObject(1, oldPlayer);
            stmt.executeUpdate();
        }

        List<RtpStorage.CooldownEntry> cooldowns = blockingList(rtpStorage.getActiveCooldowns(Duration.ofMinutes(5)));

        assertThat(cooldowns).isEmpty();
    }

    @Test
    @DisplayName("Get active cooldowns returns all recent entries")
    void getActiveCooldowns_multipleRecent_returnsAll() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        blockingAwait(rtpStorage.recordRtpUsage(player1));
        blockingAwait(rtpStorage.recordRtpUsage(player2));
        blockingAwait(rtpStorage.recordRtpUsage(player3));

        List<RtpStorage.CooldownEntry> cooldowns = blockingList(rtpStorage.getActiveCooldowns(Duration.ofMinutes(5)));

        assertThat(cooldowns).hasSize(3);
        assertThat(cooldowns).extracting(RtpStorage.CooldownEntry::playerId)
                .containsExactlyInAnyOrder(player1, player2, player3);
    }

    @Test
    @DisplayName("Cooldown entry contains correct timestamp")
    void cooldownEntry_containsCorrectTimestamp() {
        UUID playerId = UUID.randomUUID();
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        blockingAwait(rtpStorage.recordRtpUsage(playerId));

        List<RtpStorage.CooldownEntry> cooldowns = blockingList(rtpStorage.getActiveCooldowns(Duration.ofMinutes(5)));

        assertThat(cooldowns).hasSize(1);
        assertThat(cooldowns.get(0).lastUsedAt()).isAfterOrEqualTo(before);
    }
}
