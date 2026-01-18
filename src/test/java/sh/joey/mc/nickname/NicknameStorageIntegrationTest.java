package sh.joey.mc.nickname;

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
 * Integration tests for NicknameStorage.
 */
class NicknameStorageIntegrationTest extends PostgresIntegrationTest {

    private NicknameStorage nicknameStorage;

    @BeforeEach
    void setUpStorage() {
        nicknameStorage = new NicknameStorage(storage);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Get nickname returns empty when no nickname set")
        void getNickname_noNickname_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            Optional<Nickname> nickname = blockingGet(nicknameStorage.getNickname(playerId));

            assertThat(nickname).isEmpty();
        }

        @Test
        @DisplayName("Set and get nickname")
        void setAndGetNickname() {
            UUID playerId = UUID.randomUUID();

            blockingAwait(nicknameStorage.setNickname(playerId, "CoolPlayer"));

            Optional<Nickname> nickname = blockingGet(nicknameStorage.getNickname(playerId));
            assertThat(nickname).isPresent();
            assertThat(nickname.get().playerId()).isEqualTo(playerId);
            assertThat(nickname.get().nickname()).isEqualTo("CoolPlayer");
            assertThat(nickname.get().createdAt()).isNotNull();
            assertThat(nickname.get().updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Set nickname twice results in single row (UPSERT)")
        void setNicknameTwice_singleRow() throws SQLException {
            UUID playerId = UUID.randomUUID();

            blockingAwait(nicknameStorage.setNickname(playerId, "FirstNick"));
            blockingAwait(nicknameStorage.setNickname(playerId, "SecondNick"));

            int rowCount = countRows("SELECT COUNT(*) FROM player_nicknames WHERE player_id = '" + playerId + "'");
            assertThat(rowCount).isEqualTo(1);

            Optional<Nickname> nickname = blockingGet(nicknameStorage.getNickname(playerId));
            assertThat(nickname).isPresent();
            assertThat(nickname.get().nickname()).isEqualTo("SecondNick");
        }

        @Test
        @DisplayName("Remove nickname deletes entry")
        void removeNickname_deletesEntry() throws SQLException {
            UUID playerId = UUID.randomUUID();

            blockingAwait(nicknameStorage.setNickname(playerId, "ToRemove"));

            boolean removed = blockingGet(nicknameStorage.removeNickname(playerId));

            assertThat(removed).isTrue();
            assertThat(blockingGet(nicknameStorage.getNickname(playerId))).isEmpty();

            int rowCount = countRows("SELECT COUNT(*) FROM player_nicknames WHERE player_id = '" + playerId + "'");
            assertThat(rowCount).isEqualTo(0);
        }

        @Test
        @DisplayName("Remove non-existent nickname returns false")
        void removeNonExistentNickname_returnsFalse() {
            UUID playerId = UUID.randomUUID();

            boolean removed = blockingGet(nicknameStorage.removeNickname(playerId));

            assertThat(removed).isFalse();
        }
    }

    @Nested
    @DisplayName("Case-Insensitive Lookup")
    class CaseInsensitiveLookupTests {

        @Test
        @DisplayName("Find player ID by nickname is case-insensitive")
        void findPlayerIdByNickname_caseInsensitive() {
            UUID playerId = UUID.randomUUID();

            blockingAwait(nicknameStorage.setNickname(playerId, "CoolPlayer"));

            assertThat(blockingGet(nicknameStorage.findPlayerIdByNickname("CoolPlayer"))).hasValue(playerId);
            assertThat(blockingGet(nicknameStorage.findPlayerIdByNickname("coolplayer"))).hasValue(playerId);
            assertThat(blockingGet(nicknameStorage.findPlayerIdByNickname("COOLPLAYER"))).hasValue(playerId);
            assertThat(blockingGet(nicknameStorage.findPlayerIdByNickname("CoOlPlAyEr"))).hasValue(playerId);
        }

        @Test
        @DisplayName("Find player ID returns empty when nickname not found")
        void findPlayerIdByNickname_notFound_returnsEmpty() {
            Optional<UUID> playerId = blockingGet(nicknameStorage.findPlayerIdByNickname("NonExistent"));

            assertThat(playerId).isEmpty();
        }
    }

    @Nested
    @DisplayName("Nickname Availability")
    class AvailabilityTests {

        @Test
        @DisplayName("Nickname is available when not used")
        void isNicknameAvailable_notUsed_returnsTrue() {
            boolean available = blockingGet(nicknameStorage.isNicknameAvailable("UnusedNick"));

            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("Nickname is unavailable when used")
        void isNicknameAvailable_used_returnsFalse() {
            UUID playerId = UUID.randomUUID();
            blockingAwait(nicknameStorage.setNickname(playerId, "TakenNick"));

            boolean available = blockingGet(nicknameStorage.isNicknameAvailable("TakenNick"));

            assertThat(available).isFalse();
        }

        @Test
        @DisplayName("Nickname availability check is case-insensitive")
        void isNicknameAvailable_caseInsensitive() {
            UUID playerId = UUID.randomUUID();
            blockingAwait(nicknameStorage.setNickname(playerId, "TakenNick"));

            assertThat(blockingGet(nicknameStorage.isNicknameAvailable("takennick"))).isFalse();
            assertThat(blockingGet(nicknameStorage.isNicknameAvailable("TAKENNICK"))).isFalse();
        }

        @Test
        @DisplayName("Nickname is available for same player (exclude self)")
        void isNicknameAvailableFor_samePlayer_returnsTrue() {
            UUID playerId = UUID.randomUUID();
            blockingAwait(nicknameStorage.setNickname(playerId, "MyNick"));

            boolean available = blockingGet(nicknameStorage.isNicknameAvailableFor(playerId, "MyNick"));

            assertThat(available).isTrue();
        }

        @Test
        @DisplayName("Nickname is unavailable for different player")
        void isNicknameAvailableFor_differentPlayer_returnsFalse() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            blockingAwait(nicknameStorage.setNickname(player1, "TakenNick"));

            boolean available = blockingGet(nicknameStorage.isNicknameAvailableFor(player2, "TakenNick"));

            assertThat(available).isFalse();
        }
    }

    @Nested
    @DisplayName("Prefix Search")
    class PrefixSearchTests {

        @Test
        @DisplayName("Find nicknames by prefix")
        void findNicknamesByPrefix() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            UUID player3 = UUID.randomUUID();

            blockingAwait(nicknameStorage.setNickname(player1, "JohnDoe"));
            blockingAwait(nicknameStorage.setNickname(player2, "JohnSmith"));
            blockingAwait(nicknameStorage.setNickname(player3, "JaneDoe"));

            List<String> results = blockingList(nicknameStorage.findNicknamesByPrefix("John", 10));

            assertThat(results).containsExactlyInAnyOrder("JohnDoe", "JohnSmith");
        }

        @Test
        @DisplayName("Prefix search is case-insensitive")
        void findNicknamesByPrefix_caseInsensitive() {
            UUID playerId = UUID.randomUUID();
            blockingAwait(nicknameStorage.setNickname(playerId, "CoolPlayer"));

            List<String> lower = blockingList(nicknameStorage.findNicknamesByPrefix("cool", 10));
            List<String> upper = blockingList(nicknameStorage.findNicknamesByPrefix("COOL", 10));
            List<String> mixed = blockingList(nicknameStorage.findNicknamesByPrefix("CoOl", 10));

            assertThat(lower).containsExactly("CoolPlayer");
            assertThat(upper).containsExactly("CoolPlayer");
            assertThat(mixed).containsExactly("CoolPlayer");
        }

        @Test
        @DisplayName("Prefix search respects limit")
        void findNicknamesByPrefix_respectsLimit() {
            for (int i = 0; i < 10; i++) {
                blockingAwait(nicknameStorage.setNickname(UUID.randomUUID(), "Test" + i));
            }

            List<String> results = blockingList(nicknameStorage.findNicknamesByPrefix("Test", 5));

            assertThat(results).hasSize(5);
        }

        @Test
        @DisplayName("Prefix search returns empty when no matches")
        void findNicknamesByPrefix_noMatches_returnsEmpty() {
            UUID playerId = UUID.randomUUID();
            blockingAwait(nicknameStorage.setNickname(playerId, "SomeNick"));

            List<String> results = blockingList(nicknameStorage.findNicknamesByPrefix("xyz", 10));

            assertThat(results).isEmpty();
        }
    }

    @Test
    @DisplayName("Different players have separate nicknames")
    void differentPlayers_separateNicknames() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        blockingAwait(nicknameStorage.setNickname(player1, "Nick1"));
        blockingAwait(nicknameStorage.setNickname(player2, "Nick2"));

        var nick1 = blockingGet(nicknameStorage.getNickname(player1));
        var nick2 = blockingGet(nicknameStorage.getNickname(player2));

        assertThat(nick1).isPresent();
        assertThat(nick1.get().nickname()).isEqualTo("Nick1");

        assertThat(nick2).isPresent();
        assertThat(nick2.get().nickname()).isEqualTo("Nick2");
    }
}
