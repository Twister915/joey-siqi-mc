package sh.joey.mc.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
 * Integration tests for PlayerSessionStorage.
 * Tests session lifecycle, crash recovery, and player lookups.
 */
class PlayerSessionStorageIntegrationTest extends PostgresIntegrationTest {

    private PlayerSessionStorage sessionStorage;

    @BeforeEach
    void setUpStorage() {
        sessionStorage = new PlayerSessionStorage(storage);
    }

    @Nested
    @DisplayName("Session Lifecycle")
    class SessionLifecycleTests {

        @Test
        @DisplayName("Record join creates session")
        void recordJoin_createsSession() throws SQLException {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            int count = countRows("SELECT COUNT(*) FROM player_sessions WHERE player_id = '" + playerId + "'");
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Record disconnect sets disconnected_at")
        void recordDisconnect_setsDisconnectedAt() throws SQLException {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));
            blockingAwait(sessionStorage.recordDisconnect(playerId, serverSessionId));

            int disconnectedCount = countRows(
                    "SELECT COUNT(*) FROM player_sessions WHERE player_id = '" + playerId + "' AND disconnected_at IS NOT NULL"
            );
            assertThat(disconnectedCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Update last seen updates active sessions")
        void updateLastSeen_updatesActiveSessions() throws SQLException, InterruptedException {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));
            Thread.sleep(50); // Ensure time passes
            blockingAwait(sessionStorage.updateLastSeen(serverSessionId));

            // Verify last_seen_at was updated (after connected_at)
            int updatedCount = countRows(
                    "SELECT COUNT(*) FROM player_sessions WHERE player_id = '" + playerId +
                    "' AND last_seen_at > connected_at"
            );
            assertThat(updatedCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Close all sessions closes active sessions for server")
        void closeAllSessions_closesActiveSessionsForServer() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(player1, "Player1", "192.168.1.1", true, serverSessionId));
            blockingAwait(sessionStorage.recordJoin(player2, "Player2", "192.168.1.2", true, serverSessionId));

            int closed = blockingGet(sessionStorage.closeAllSessions(serverSessionId));

            assertThat(closed).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Crash Recovery (Orphaned Sessions)")
    class CrashRecoveryTests {

        @Test
        @DisplayName("Fix orphaned sessions closes sessions from other server runs")
        void fixOrphanedSessions_closesOtherServerSessions() {
            UUID playerId = UUID.randomUUID();
            UUID oldServerSessionId = UUID.randomUUID();
            UUID newServerSessionId = UUID.randomUUID();

            // Simulate crash: session from old server run was never closed
            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, oldServerSessionId));

            int fixed = blockingGet(sessionStorage.fixOrphanedSessions(newServerSessionId));

            assertThat(fixed).isEqualTo(1);
        }

        @Test
        @DisplayName("Fix orphaned sessions does not affect current server sessions")
        void fixOrphanedSessions_doesNotAffectCurrentServer() throws SQLException {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            int fixed = blockingGet(sessionStorage.fixOrphanedSessions(serverSessionId));

            assertThat(fixed).isEqualTo(0);

            // Session should still be active
            int activeCount = countRows(
                    "SELECT COUNT(*) FROM player_sessions WHERE player_id = '" + playerId + "' AND disconnected_at IS NULL"
            );
            assertThat(activeCount).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Player Lookup by Name")
    class PlayerLookupByNameTests {

        @Test
        @DisplayName("Find player ID by name returns player ID")
        void findPlayerIdByName_returnsPlayerId() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            Optional<UUID> found = blockingGet(sessionStorage.findPlayerIdByName("TestPlayer"));

            assertThat(found).hasValue(playerId);
        }

        @Test
        @DisplayName("Find player ID by name is case-insensitive")
        void findPlayerIdByName_caseInsensitive() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            assertThat(blockingGet(sessionStorage.findPlayerIdByName("testplayer"))).hasValue(playerId);
            assertThat(blockingGet(sessionStorage.findPlayerIdByName("TESTPLAYER"))).hasValue(playerId);
            assertThat(blockingGet(sessionStorage.findPlayerIdByName("TeStPlAyEr"))).hasValue(playerId);
        }

        @Test
        @DisplayName("Find player ID by name returns empty when not found")
        void findPlayerIdByName_notFound_returnsEmpty() {
            Optional<UUID> found = blockingGet(sessionStorage.findPlayerIdByName("NonExistent"));

            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("Find player ID returns most recent session's player")
        void findPlayerIdByName_returnsMostRecentSession() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            UUID serverSession1 = UUID.randomUUID();
            UUID serverSession2 = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "OldName", "192.168.1.1", true, serverSession1));
            Thread.sleep(10);
            blockingAwait(sessionStorage.recordJoin(playerId, "NewName", "192.168.1.1", true, serverSession2));

            // Both names should find the same player
            assertThat(blockingGet(sessionStorage.findPlayerIdByName("OldName"))).hasValue(playerId);
            assertThat(blockingGet(sessionStorage.findPlayerIdByName("NewName"))).hasValue(playerId);
        }
    }

    @Nested
    @DisplayName("Username Lookup by ID")
    class UsernameLookupByIdTests {

        @Test
        @DisplayName("Find username by ID returns username")
        void findUsernameById_returnsUsername() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            Optional<String> found = blockingGet(sessionStorage.findUsernameById(playerId));

            assertThat(found).hasValue("TestPlayer");
        }

        @Test
        @DisplayName("Find username by ID returns most recent username")
        void findUsernameById_returnsMostRecent() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            UUID serverSession1 = UUID.randomUUID();
            UUID serverSession2 = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "OldName", "192.168.1.1", true, serverSession1));
            Thread.sleep(10);
            blockingAwait(sessionStorage.recordJoin(playerId, "NewName", "192.168.1.1", true, serverSession2));

            Optional<String> found = blockingGet(sessionStorage.findUsernameById(playerId));

            assertThat(found).hasValue("NewName");
        }

        @Test
        @DisplayName("Find username by ID returns empty when not found")
        void findUsernameById_notFound_returnsEmpty() {
            Optional<String> found = blockingGet(sessionStorage.findUsernameById(UUID.randomUUID()));

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("Username Exists Check")
    class UsernameExistsTests {

        @Test
        @DisplayName("Username exists returns true when exists")
        void usernameExists_returnsTrue() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            boolean exists = blockingGet(sessionStorage.usernameExists("TestPlayer"));

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Username exists is case-insensitive")
        void usernameExists_caseInsensitive() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            assertThat(blockingGet(sessionStorage.usernameExists("testplayer"))).isTrue();
            assertThat(blockingGet(sessionStorage.usernameExists("TESTPLAYER"))).isTrue();
        }

        @Test
        @DisplayName("Username exists returns false when not exists")
        void usernameExists_returnsFalse() {
            boolean exists = blockingGet(sessionStorage.usernameExists("NonExistent"));

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("Username Prefix Search")
    class UsernamePrefixSearchTests {

        @Test
        @DisplayName("Find usernames by prefix returns matches")
        void findUsernamesByPrefix_returnsMatches() {
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(UUID.randomUUID(), "JohnDoe", "192.168.1.1", true, serverSessionId));
            blockingAwait(sessionStorage.recordJoin(UUID.randomUUID(), "JohnSmith", "192.168.1.2", true, serverSessionId));
            blockingAwait(sessionStorage.recordJoin(UUID.randomUUID(), "JaneDoe", "192.168.1.3", true, serverSessionId));

            List<String> results = blockingList(sessionStorage.findUsernamesByPrefix("John", 10));

            assertThat(results).containsExactlyInAnyOrder("JohnDoe", "JohnSmith");
        }

        @Test
        @DisplayName("Find usernames by prefix is case-insensitive")
        void findUsernamesByPrefix_caseInsensitive() {
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(UUID.randomUUID(), "TestPlayer", "192.168.1.1", true, serverSessionId));

            assertThat(blockingList(sessionStorage.findUsernamesByPrefix("test", 10))).containsExactly("TestPlayer");
            assertThat(blockingList(sessionStorage.findUsernamesByPrefix("TEST", 10))).containsExactly("TestPlayer");
        }

        @Test
        @DisplayName("Find usernames by prefix respects limit")
        void findUsernamesByPrefix_respectsLimit() {
            UUID serverSessionId = UUID.randomUUID();

            for (int i = 0; i < 10; i++) {
                blockingAwait(sessionStorage.recordJoin(UUID.randomUUID(), "Test" + i, "192.168.1." + i, true, serverSessionId));
            }

            List<String> results = blockingList(sessionStorage.findUsernamesByPrefix("Test", 5));

            assertThat(results).hasSize(5);
        }
    }

    @Nested
    @DisplayName("Session Time Queries")
    class SessionTimeQueryTests {

        @Test
        @DisplayName("Get current session start returns session start time")
        void getCurrentSessionStart_returnsStartTime() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();
            Instant before = Instant.now().minusSeconds(1);

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            Optional<Instant> sessionStart = blockingGet(sessionStorage.getCurrentSessionStart(playerId, serverSessionId));

            assertThat(sessionStart).isPresent();
            assertThat(sessionStart.get()).isAfter(before);
        }

        @Test
        @DisplayName("Get current session start returns empty for disconnected session")
        void getCurrentSessionStart_disconnected_returnsEmpty() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));
            blockingAwait(sessionStorage.recordDisconnect(playerId, serverSessionId));

            Optional<Instant> sessionStart = blockingGet(sessionStorage.getCurrentSessionStart(playerId, serverSessionId));

            assertThat(sessionStart).isEmpty();
        }

        @Test
        @DisplayName("Get first join date returns earliest session")
        void getFirstJoinDate_returnsEarliest() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            UUID serverSession1 = UUID.randomUUID();
            UUID serverSession2 = UUID.randomUUID();
            Instant before = Instant.now().minusSeconds(1);

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSession1));
            Thread.sleep(50);
            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSession2));

            Optional<Instant> firstJoin = blockingGet(sessionStorage.getFirstJoinDate(playerId));

            assertThat(firstJoin).isPresent();
            assertThat(firstJoin.get()).isAfter(before);
        }

        @Test
        @DisplayName("Get last seen date returns latest activity")
        void getLastSeenDate_returnsLatest() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSessionId));

            Optional<Instant> lastSeen = blockingGet(sessionStorage.getLastSeenDate(playerId));

            assertThat(lastSeen).isPresent();
        }
    }

    @Nested
    @DisplayName("IP Address Tracking")
    class IpAddressTrackingTests {

        @Test
        @DisplayName("Get last IP address returns most recent IP")
        void getLastIpAddress_returnsMostRecent() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            UUID serverSession1 = UUID.randomUUID();
            UUID serverSession2 = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.1", true, serverSession1));
            Thread.sleep(10);
            blockingAwait(sessionStorage.recordJoin(playerId, "TestPlayer", "192.168.1.2", true, serverSession2));

            Optional<String> ip = blockingGet(sessionStorage.getLastIpAddress(playerId));

            assertThat(ip).hasValue("192.168.1.2");
        }

        @Test
        @DisplayName("Get last IP address returns empty when not found")
        void getLastIpAddress_notFound_returnsEmpty() {
            Optional<String> ip = blockingGet(sessionStorage.getLastIpAddress(UUID.randomUUID()));

            assertThat(ip).isEmpty();
        }
    }

    @Nested
    @DisplayName("First Join Detection")
    class FirstJoinTests {

        @Test
        @DisplayName("Is first join returns true for first session")
        void isFirstJoin_firstSession_returnsTrue() {
            UUID playerId = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "NewPlayer", "192.168.1.1", true, serverSessionId));

            boolean isFirst = blockingGet(sessionStorage.isFirstJoin(playerId));

            assertThat(isFirst).isTrue();
        }

        @Test
        @DisplayName("Is first join returns false for returning player")
        void isFirstJoin_returningPlayer_returnsFalse() {
            UUID playerId = UUID.randomUUID();
            UUID serverSession1 = UUID.randomUUID();
            UUID serverSession2 = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "OldPlayer", "192.168.1.1", true, serverSession1));
            blockingAwait(sessionStorage.recordJoin(playerId, "OldPlayer", "192.168.1.1", true, serverSession2));

            boolean isFirst = blockingGet(sessionStorage.isFirstJoin(playerId));

            assertThat(isFirst).isFalse();
        }
    }

    @Nested
    @DisplayName("Username History")
    class UsernameHistoryTests {

        @Test
        @DisplayName("Get username history returns history entries")
        void getUsernameHistory_returnsEntries() throws InterruptedException {
            UUID playerId = UUID.randomUUID();
            UUID serverSession1 = UUID.randomUUID();
            UUID serverSession2 = UUID.randomUUID();

            blockingAwait(sessionStorage.recordJoin(playerId, "OldName", "192.168.1.1", true, serverSession1));
            Thread.sleep(10);
            blockingAwait(sessionStorage.recordJoin(playerId, "NewName", "192.168.1.1", true, serverSession2));

            List<PlayerSessionStorage.UsernameHistoryEntry> history =
                    blockingList(sessionStorage.getUsernameHistory(playerId));

            assertThat(history).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("Top Online Time")
    class TopOnlineTimeTests {

        @Test
        @DisplayName("Get top online time returns players with sessions in range")
        void getTopOnlineTime_returnsPlayersInRange() throws InterruptedException {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID serverSessionId = UUID.randomUUID();
            Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

            blockingAwait(sessionStorage.recordJoin(player1, "Player1", "192.168.1.1", true, serverSessionId));
            blockingAwait(sessionStorage.recordJoin(player2, "Player2", "192.168.1.2", true, serverSessionId));

            // Need to update last_seen to create non-zero session duration
            Thread.sleep(50);
            blockingAwait(sessionStorage.updateLastSeen(serverSessionId));

            List<PlayerSessionStorage.TopOnlineTimeEntry> top =
                    blockingList(sessionStorage.getTopOnlineTime(oneHourAgo, 10));

            assertThat(top).isNotEmpty();
            assertThat(top).extracting(PlayerSessionStorage.TopOnlineTimeEntry::playerId)
                    .containsExactlyInAnyOrder(player1, player2);
        }

        @Test
        @DisplayName("Get top online time respects limit")
        void getTopOnlineTime_respectsLimit() throws InterruptedException {
            UUID serverSessionId = UUID.randomUUID();
            Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);

            for (int i = 0; i < 10; i++) {
                blockingAwait(sessionStorage.recordJoin(UUID.randomUUID(), "Player" + i, "192.168.1." + i, true, serverSessionId));
            }

            // Need to update last_seen to create non-zero session duration
            Thread.sleep(50);
            blockingAwait(sessionStorage.updateLastSeen(serverSessionId));

            List<PlayerSessionStorage.TopOnlineTimeEntry> top =
                    blockingList(sessionStorage.getTopOnlineTime(oneHourAgo, 5));

            assertThat(top).hasSize(5);
        }
    }

    @Test
    @DisplayName("Different players have separate sessions")
    void differentPlayers_separateSessions() throws SQLException {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID serverSessionId = UUID.randomUUID();

        blockingAwait(sessionStorage.recordJoin(player1, "Player1", "192.168.1.1", true, serverSessionId));
        blockingAwait(sessionStorage.recordJoin(player2, "Player2", "192.168.1.2", true, serverSessionId));

        int count1 = countRows("SELECT COUNT(*) FROM player_sessions WHERE player_id = '" + player1 + "'");
        int count2 = countRows("SELECT COUNT(*) FROM player_sessions WHERE player_id = '" + player2 + "'");

        assertThat(count1).isEqualTo(1);
        assertThat(count2).isEqualTo(1);
    }
}
