package sh.joey.mc.whitelist;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for WhitelistStorage.
 */
class WhitelistStorageIntegrationTest extends PostgresIntegrationTest {

    private WhitelistStorage whitelistStorage;

    @BeforeEach
    void setUpStorage() {
        whitelistStorage = new WhitelistStorage(storage);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Is whitelisted returns false when player not in whitelist")
        void isWhitelisted_notInWhitelist_returnsFalse() {
            UUID playerId = UUID.randomUUID();

            boolean whitelisted = blockingGet(whitelistStorage.isWhitelisted(playerId));

            assertThat(whitelisted).isFalse();
        }

        @Test
        @DisplayName("Add player and check is whitelisted")
        void addPlayerAndCheckIsWhitelisted() {
            UUID playerId = UUID.randomUUID();

            blockingAwait(whitelistStorage.addPlayer(playerId, "TestPlayer", null));

            boolean whitelisted = blockingGet(whitelistStorage.isWhitelisted(playerId));
            assertThat(whitelisted).isTrue();
        }

        @Test
        @DisplayName("Get entry returns correct data")
        void getEntry_returnsCorrectData() {
            UUID playerId = UUID.randomUUID();
            UUID inviterId = UUID.randomUUID();

            blockingAwait(whitelistStorage.addPlayer(playerId, "TestPlayer", inviterId));

            Optional<WhitelistEntry> entry = blockingGet(whitelistStorage.getEntry(playerId));
            assertThat(entry).isPresent();
            assertThat(entry.get().playerId()).isEqualTo(playerId);
            assertThat(entry.get().playerName()).isEqualTo("TestPlayer");
            assertThat(entry.get().invitedBy()).isEqualTo(inviterId);
            assertThat(entry.get().createdAt()).isNotNull();
        }

        @Test
        @DisplayName("Get entry returns empty when not found")
        void getEntry_notFound_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            Optional<WhitelistEntry> entry = blockingGet(whitelistStorage.getEntry(playerId));

            assertThat(entry).isEmpty();
        }

        @Test
        @DisplayName("Add player without inviter")
        void addPlayer_withoutInviter() {
            UUID playerId = UUID.randomUUID();

            blockingAwait(whitelistStorage.addPlayer(playerId, "SoloPlayer", null));

            Optional<WhitelistEntry> entry = blockingGet(whitelistStorage.getEntry(playerId));
            assertThat(entry).isPresent();
            assertThat(entry.get().invitedBy()).isNull();
        }

        @Test
        @DisplayName("Remove player removes entry")
        void removePlayer_removesEntry() throws SQLException {
            UUID playerId = UUID.randomUUID();

            blockingAwait(whitelistStorage.addPlayer(playerId, "ToRemove", null));

            boolean removed = blockingGet(whitelistStorage.removePlayer(playerId));

            assertThat(removed).isTrue();
            assertThat(blockingGet(whitelistStorage.isWhitelisted(playerId))).isFalse();

            int rowCount = countRows("SELECT COUNT(*) FROM whitelist WHERE player_id = '" + playerId + "'");
            assertThat(rowCount).isEqualTo(0);
        }

        @Test
        @DisplayName("Remove non-existent player returns false")
        void removeNonExistentPlayer_returnsFalse() {
            UUID playerId = UUID.randomUUID();

            boolean removed = blockingGet(whitelistStorage.removePlayer(playerId));

            assertThat(removed).isFalse();
        }
    }

    @Nested
    @DisplayName("UPSERT and COALESCE Logic")
    class UpsertCoalesceTests {

        @Test
        @DisplayName("Add player twice results in single row (UPSERT)")
        void addPlayerTwice_singleRow() throws SQLException {
            UUID playerId = UUID.randomUUID();

            blockingAwait(whitelistStorage.addPlayer(playerId, "OriginalName", null));
            blockingAwait(whitelistStorage.addPlayer(playerId, "UpdatedName", null));

            int rowCount = countRows("SELECT COUNT(*) FROM whitelist WHERE player_id = '" + playerId + "'");
            assertThat(rowCount).isEqualTo(1);

            Optional<WhitelistEntry> entry = blockingGet(whitelistStorage.getEntry(playerId));
            assertThat(entry).isPresent();
            assertThat(entry.get().playerName()).isEqualTo("UpdatedName");
        }

        @Test
        @DisplayName("COALESCE preserves existing inviter when adding with null inviter")
        void coalesce_preservesExistingInviter() {
            UUID playerId = UUID.randomUUID();
            UUID originalInviter = UUID.randomUUID();

            // First add with inviter
            blockingAwait(whitelistStorage.addPlayer(playerId, "TestPlayer", originalInviter));

            // Second add without inviter (e.g., name update)
            blockingAwait(whitelistStorage.addPlayer(playerId, "UpdatedName", null));

            Optional<WhitelistEntry> entry = blockingGet(whitelistStorage.getEntry(playerId));
            assertThat(entry).isPresent();
            assertThat(entry.get().invitedBy()).isEqualTo(originalInviter); // Preserved!
            assertThat(entry.get().playerName()).isEqualTo("UpdatedName");
        }

        @Test
        @DisplayName("COALESCE uses new inviter when existing is null")
        void coalesce_usesNewInviterWhenExistingNull() {
            UUID playerId = UUID.randomUUID();
            UUID newInviter = UUID.randomUUID();

            // First add without inviter
            blockingAwait(whitelistStorage.addPlayer(playerId, "TestPlayer", null));

            // Second add with inviter
            blockingAwait(whitelistStorage.addPlayer(playerId, "TestPlayer", newInviter));

            Optional<WhitelistEntry> entry = blockingGet(whitelistStorage.getEntry(playerId));
            assertThat(entry).isPresent();
            assertThat(entry.get().invitedBy()).isEqualTo(newInviter);
        }
    }

    @Nested
    @DisplayName("List Operations")
    class ListOperationsTests {

        @Test
        @DisplayName("Get all entries returns empty list when no entries")
        void getAllEntries_noEntries_returnsEmpty() {
            List<WhitelistEntry> entries = blockingList(whitelistStorage.getAllEntries());
            assertThat(entries).isEmpty();
        }

        @Test
        @DisplayName("Get all entries returns all entries")
        void getAllEntries_returnsAllEntries() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            blockingAwait(whitelistStorage.addPlayer(player1, "Player1", null));
            blockingAwait(whitelistStorage.addPlayer(player2, "Player2", null));
            blockingAwait(whitelistStorage.addPlayer(player3, "Player3", null));

            List<WhitelistEntry> entries = blockingList(whitelistStorage.getAllEntries());

            assertThat(entries).hasSize(3);
            assertThat(entries).extracting(WhitelistEntry::playerId)
                    .containsExactlyInAnyOrder(player1, player2, player3);
        }

        @Test
        @DisplayName("Get invited by returns only invited players")
        void getInvitedBy_returnsOnlyInvitedPlayers() {
            UUID inviter = UUID.randomUUID();
            UUID invited1 = UUID.randomUUID();
            UUID invited2 = UUID.randomUUID();
            UUID otherPlayer = UUID.randomUUID();

            blockingAwait(whitelistStorage.addPlayer(inviter, "Inviter", null));
            blockingAwait(whitelistStorage.addPlayer(invited1, "Invited1", inviter));
            blockingAwait(whitelistStorage.addPlayer(invited2, "Invited2", inviter));
            blockingAwait(whitelistStorage.addPlayer(otherPlayer, "Other", null));

            List<WhitelistEntry> invitedBy = blockingList(whitelistStorage.getInvitedBy(inviter));

            assertThat(invitedBy).hasSize(2);
            assertThat(invitedBy).extracting(WhitelistEntry::playerId)
                    .containsExactlyInAnyOrder(invited1, invited2);
        }

        @Test
        @DisplayName("Get invited by returns empty when no invites")
        void getInvitedBy_noInvites_returnsEmpty() {
            UUID inviter = UUID.randomUUID();
            blockingAwait(whitelistStorage.addPlayer(inviter, "Inviter", null));

            List<WhitelistEntry> invitedBy = blockingList(whitelistStorage.getInvitedBy(inviter));

            assertThat(invitedBy).isEmpty();
        }
    }

    @Nested
    @DisplayName("Count Operations")
    class CountOperationsTests {

        @Test
        @DisplayName("Count returns zero when empty")
        void count_empty_returnsZero() {
            int count = blockingGet(whitelistStorage.count());
            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("Count returns correct count")
        void count_returnsCorrectCount() {
            for (int i = 0; i < 5; i++) {
                blockingAwait(whitelistStorage.addPlayer(UUID.randomUUID(), "Player" + i, null));
            }

            int count = blockingGet(whitelistStorage.count());
            assertThat(count).isEqualTo(5);
        }

        @Test
        @DisplayName("Count updates after add and remove")
        void count_updatesAfterAddAndRemove() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            blockingAwait(whitelistStorage.addPlayer(player1, "Player1", null));
            blockingAwait(whitelistStorage.addPlayer(player2, "Player2", null));
            assertThat(blockingGet(whitelistStorage.count())).isEqualTo(2);

            blockingGet(whitelistStorage.removePlayer(player1));
            assertThat(blockingGet(whitelistStorage.count())).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Different players are independent")
    void differentPlayers_independent() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID inviter = UUID.randomUUID();

        blockingAwait(whitelistStorage.addPlayer(player1, "Player1", inviter));
        blockingAwait(whitelistStorage.addPlayer(player2, "Player2", null));

        var entry1 = blockingGet(whitelistStorage.getEntry(player1));
        var entry2 = blockingGet(whitelistStorage.getEntry(player2));

        assertThat(entry1).isPresent();
        assertThat(entry1.get().invitedBy()).isEqualTo(inviter);

        assertThat(entry2).isPresent();
        assertThat(entry2.get().invitedBy()).isNull();
    }
}
