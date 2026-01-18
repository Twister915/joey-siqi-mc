package sh.joey.mc.home;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for HomeStorage.
 * Tests transactional soft-delete, array aggregation, and share/unshare workflow.
 */
class HomeStorageIntegrationTest extends PostgresIntegrationTest {

    private HomeStorage homeStorage;

    @BeforeEach
    void setUpStorage() {
        homeStorage = new HomeStorage(storage);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Get home returns empty when no home exists")
        void getHome_noHome_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            Optional<Home> home = blockingGet(homeStorage.getHome(playerId, "home"));

            assertThat(home).isEmpty();
        }

        @Test
        @DisplayName("Set and get home")
        void setAndGetHome() {
            UUID playerId = UUID.randomUUID();
            UUID worldId = UUID.randomUUID();
            Home home = createHome(playerId, "base", worldId, 100, 64, 200);

            blockingAwait(homeStorage.setHome(playerId, home));

            Optional<Home> retrieved = blockingGet(homeStorage.getHome(playerId, "base"));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().id()).isEqualTo(home.id());
            assertThat(retrieved.get().name()).isEqualTo("base");
            assertThat(retrieved.get().ownerId()).isEqualTo(playerId);
            assertThat(retrieved.get().worldId()).isEqualTo(worldId);
            assertThat(retrieved.get().x()).isEqualTo(100);
            assertThat(retrieved.get().y()).isEqualTo(64);
            assertThat(retrieved.get().z()).isEqualTo(200);
        }

        @Test
        @DisplayName("Has any homes returns false when no homes")
        void hasAnyHomes_noHomes_returnsFalse() {
            UUID playerId = UUID.randomUUID();

            boolean hasHomes = blockingGet(homeStorage.hasAnyHomes(playerId));

            assertThat(hasHomes).isFalse();
        }

        @Test
        @DisplayName("Has any homes returns true when home exists")
        void hasAnyHomes_homeExists_returnsTrue() {
            UUID playerId = UUID.randomUUID();
            Home home = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(playerId, home));

            boolean hasHomes = blockingGet(homeStorage.hasAnyHomes(playerId));

            assertThat(hasHomes).isTrue();
        }

        @Test
        @DisplayName("Delete home soft-deletes the entry")
        void deleteHome_softDeletes() throws SQLException {
            UUID playerId = UUID.randomUUID();
            Home home = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(playerId, home));

            boolean deleted = blockingGet(homeStorage.deleteHome(playerId, "home"));

            assertThat(deleted).isTrue();
            assertThat(blockingGet(homeStorage.getHome(playerId, "home"))).isEmpty();

            // Verify soft delete (row still exists with deleted_at set)
            int activeCount = countRows("SELECT COUNT(*) FROM homes WHERE player_id = '" + playerId + "' AND deleted_at IS NULL");
            int totalCount = countRows("SELECT COUNT(*) FROM homes WHERE player_id = '" + playerId + "'");
            assertThat(activeCount).isEqualTo(0);
            assertThat(totalCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Delete non-existent home returns false")
        void deleteNonExistentHome_returnsFalse() {
            UUID playerId = UUID.randomUUID();

            boolean deleted = blockingGet(homeStorage.deleteHome(playerId, "nonexistent"));

            assertThat(deleted).isFalse();
        }
    }

    @Nested
    @DisplayName("Name Normalization")
    class NameNormalizationTests {

        @Test
        @DisplayName("Home names are normalized to lowercase")
        void homeNamesNormalized() {
            UUID playerId = UUID.randomUUID();
            Home home = createHome(playerId, "MyBase", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(playerId, home));

            // Can retrieve with different case
            assertThat(blockingGet(homeStorage.getHome(playerId, "mybase"))).isPresent();
            assertThat(blockingGet(homeStorage.getHome(playerId, "MYBASE"))).isPresent();
            assertThat(blockingGet(homeStorage.getHome(playerId, "MyBase"))).isPresent();
        }

        @Test
        @DisplayName("Normalize name helper works correctly")
        void normalizeName_works() {
            assertThat(HomeStorage.normalizeName("  MyBase  ")).isEqualTo("mybase");
            assertThat(HomeStorage.normalizeName("HOME")).isEqualTo("home");
            assertThat(HomeStorage.normalizeName("test")).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("Transactional Set Home (Soft Delete + Insert)")
    class TransactionalSetHomeTests {

        @Test
        @DisplayName("Setting home with same name replaces old home")
        void setHomeSameName_replacesOldHome() throws SQLException {
            UUID playerId = UUID.randomUUID();
            UUID worldId = UUID.randomUUID();

            Home home1 = createHome(playerId, "home", worldId, 100, 64, 100);
            blockingAwait(homeStorage.setHome(playerId, home1));

            Home home2 = createHome(playerId, "home", worldId, 200, 64, 200);
            blockingAwait(homeStorage.setHome(playerId, home2));

            // Should have one active home
            Optional<Home> retrieved = blockingGet(homeStorage.getHome(playerId, "home"));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().id()).isEqualTo(home2.id());
            assertThat(retrieved.get().x()).isEqualTo(200);
            assertThat(retrieved.get().z()).isEqualTo(200);

            // Old home should be soft-deleted (total 2 rows, 1 active)
            int activeCount = countRows("SELECT COUNT(*) FROM homes WHERE player_id = '" + playerId + "' AND deleted_at IS NULL");
            int totalCount = countRows("SELECT COUNT(*) FROM homes WHERE player_id = '" + playerId + "'");
            assertThat(activeCount).isEqualTo(1);
            assertThat(totalCount).isEqualTo(2);
        }

        @Test
        @DisplayName("Setting home does not affect other homes")
        void setHome_doesNotAffectOtherHomes() {
            UUID playerId = UUID.randomUUID();

            Home home1 = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            Home home2 = createHome(playerId, "base", UUID.randomUUID(), 100, 100, 100);
            blockingAwait(homeStorage.setHome(playerId, home1));
            blockingAwait(homeStorage.setHome(playerId, home2));

            // Both should exist
            assertThat(blockingGet(homeStorage.getHome(playerId, "home"))).isPresent();
            assertThat(blockingGet(homeStorage.getHome(playerId, "base"))).isPresent();
        }
    }

    @Nested
    @DisplayName("Get Homes (Owned + Shared)")
    class GetHomesTests {

        @Test
        @DisplayName("Get homes returns empty when no homes")
        void getHomes_noHomes_returnsEmpty() {
            UUID playerId = UUID.randomUUID();

            List<Home> homes = blockingList(homeStorage.getHomes(playerId));

            assertThat(homes).isEmpty();
        }

        @Test
        @DisplayName("Get homes returns owned homes")
        void getHomes_returnsOwnedHomes() {
            UUID playerId = UUID.randomUUID();

            Home home1 = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            Home home2 = createHome(playerId, "base", UUID.randomUUID(), 100, 100, 100);
            blockingAwait(homeStorage.setHome(playerId, home1));
            blockingAwait(homeStorage.setHome(playerId, home2));

            List<Home> homes = blockingList(homeStorage.getHomes(playerId));

            assertThat(homes).hasSize(2);
            assertThat(homes).extracting(Home::name).containsExactlyInAnyOrder("home", "base");
        }

        @Test
        @DisplayName("Get homes returns shared homes")
        void getHomes_returnsSharedHomes() {
            UUID owner = UUID.randomUUID();
            UUID target = UUID.randomUUID();

            Home home = createHome(owner, "shared-base", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(owner, home));
            blockingGet(homeStorage.shareHome(owner, "shared-base", target));

            List<Home> targetHomes = blockingList(homeStorage.getHomes(target));

            assertThat(targetHomes).hasSize(1);
            assertThat(targetHomes.get(0).name()).isEqualTo("shared-base");
            assertThat(targetHomes.get(0).ownerId()).isEqualTo(owner);
        }

        @Test
        @DisplayName("Get homes returns both owned and shared homes")
        void getHomes_returnsBothOwnedAndShared() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();

            // player1's own home
            Home ownHome = createHome(player1, "my-home", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(player1, ownHome));

            // player2 shares a home with player1
            Home sharedHome = createHome(player2, "shared", UUID.randomUUID(), 100, 100, 100);
            blockingAwait(homeStorage.setHome(player2, sharedHome));
            blockingGet(homeStorage.shareHome(player2, "shared", player1));

            List<Home> player1Homes = blockingList(homeStorage.getHomes(player1));

            assertThat(player1Homes).hasSize(2);
            assertThat(player1Homes).extracting(Home::name).containsExactlyInAnyOrder("my-home", "shared");
        }
    }

    @Nested
    @DisplayName("Share/Unshare Workflow")
    class ShareUnshareTests {

        @Test
        @DisplayName("Share home returns SUCCESS")
        void shareHome_returnsSuccess() {
            UUID owner = UUID.randomUUID();
            UUID target = UUID.randomUUID();

            Home home = createHome(owner, "base", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(owner, home));

            HomeStorage.ShareResult result = blockingGet(homeStorage.shareHome(owner, "base", target));

            assertThat(result).isEqualTo(HomeStorage.ShareResult.SUCCESS);
        }

        @Test
        @DisplayName("Share home returns HOME_NOT_FOUND when home doesn't exist")
        void shareHome_homeNotFound() {
            UUID owner = UUID.randomUUID();
            UUID target = UUID.randomUUID();

            HomeStorage.ShareResult result = blockingGet(homeStorage.shareHome(owner, "nonexistent", target));

            assertThat(result).isEqualTo(HomeStorage.ShareResult.HOME_NOT_FOUND);
        }

        @Test
        @DisplayName("Share home returns ALREADY_SHARED when already shared")
        void shareHome_alreadyShared() {
            UUID owner = UUID.randomUUID();
            UUID target = UUID.randomUUID();

            Home home = createHome(owner, "base", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(owner, home));
            blockingGet(homeStorage.shareHome(owner, "base", target));

            HomeStorage.ShareResult result = blockingGet(homeStorage.shareHome(owner, "base", target));

            assertThat(result).isEqualTo(HomeStorage.ShareResult.ALREADY_SHARED);
        }

        @Test
        @DisplayName("Unshare home returns true when share exists")
        void unshareHome_returnsTrue() {
            UUID owner = UUID.randomUUID();
            UUID target = UUID.randomUUID();

            Home home = createHome(owner, "base", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(owner, home));
            blockingGet(homeStorage.shareHome(owner, "base", target));

            boolean unshared = blockingGet(homeStorage.unshareHome(owner, "base", target));

            assertThat(unshared).isTrue();

            // Target should no longer see the home
            List<Home> targetHomes = blockingList(homeStorage.getHomes(target));
            assertThat(targetHomes).isEmpty();
        }

        @Test
        @DisplayName("Unshare home returns false when not shared")
        void unshareHome_notShared_returnsFalse() {
            UUID owner = UUID.randomUUID();
            UUID target = UUID.randomUUID();

            Home home = createHome(owner, "base", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(owner, home));

            boolean unshared = blockingGet(homeStorage.unshareHome(owner, "base", target));

            assertThat(unshared).isFalse();
        }

        @Test
        @DisplayName("Unshare home returns false when home doesn't exist")
        void unshareHome_homeNotFound_returnsFalse() {
            UUID owner = UUID.randomUUID();
            UUID target = UUID.randomUUID();

            boolean unshared = blockingGet(homeStorage.unshareHome(owner, "nonexistent", target));

            assertThat(unshared).isFalse();
        }

        @Test
        @DisplayName("Shared players are included in sharedWith set")
        void sharedWith_includesSharedPlayers() {
            UUID owner = UUID.randomUUID();
            UUID target1 = UUID.randomUUID();
            UUID target2 = UUID.randomUUID();

            Home home = createHome(owner, "base", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(owner, home));
            blockingGet(homeStorage.shareHome(owner, "base", target1));
            blockingGet(homeStorage.shareHome(owner, "base", target2));

            Optional<Home> retrieved = blockingGet(homeStorage.getHome(owner, "base"));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().sharedWith()).containsExactlyInAnyOrder(target1, target2);
        }
    }

    @Nested
    @DisplayName("Soft Delete Behavior")
    class SoftDeleteTests {

        @Test
        @DisplayName("Deleted homes are not returned by getHome")
        void deletedHomes_notReturnedByGetHome() {
            UUID playerId = UUID.randomUUID();
            Home home = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(playerId, home));
            blockingGet(homeStorage.deleteHome(playerId, "home"));

            Optional<Home> retrieved = blockingGet(homeStorage.getHome(playerId, "home"));

            assertThat(retrieved).isEmpty();
        }

        @Test
        @DisplayName("Deleted homes are not returned by getHomes")
        void deletedHomes_notReturnedByGetHomes() {
            UUID playerId = UUID.randomUUID();
            Home home1 = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            Home home2 = createHome(playerId, "base", UUID.randomUUID(), 100, 100, 100);
            blockingAwait(homeStorage.setHome(playerId, home1));
            blockingAwait(homeStorage.setHome(playerId, home2));
            blockingGet(homeStorage.deleteHome(playerId, "home"));

            List<Home> homes = blockingList(homeStorage.getHomes(playerId));

            assertThat(homes).hasSize(1);
            assertThat(homes.get(0).name()).isEqualTo("base");
        }

        @Test
        @DisplayName("Deleted homes are not counted by hasAnyHomes")
        void deletedHomes_notCountedByHasAnyHomes() {
            UUID playerId = UUID.randomUUID();
            Home home = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            blockingAwait(homeStorage.setHome(playerId, home));
            blockingGet(homeStorage.deleteHome(playerId, "home"));

            boolean hasHomes = blockingGet(homeStorage.hasAnyHomes(playerId));

            assertThat(hasHomes).isFalse();
        }
    }

    @Nested
    @DisplayName("Count Owned Homes")
    class CountOwnedHomesTests {

        @Test
        @DisplayName("returns 0 when no homes")
        void countOwnedHomes_noHomes_returnsZero() {
            UUID playerId = UUID.randomUUID();

            int count = blockingGet(homeStorage.countOwnedHomes(playerId));

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("returns correct count after adding homes")
        void countOwnedHomes_afterAddingHomes_returnsCorrectCount() {
            UUID playerId = UUID.randomUUID();

            Home home1 = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            Home home2 = createHome(playerId, "base", UUID.randomUUID(), 100, 100, 100);
            Home home3 = createHome(playerId, "farm", UUID.randomUUID(), 200, 200, 200);
            blockingAwait(homeStorage.setHome(playerId, home1));
            blockingAwait(homeStorage.setHome(playerId, home2));
            blockingAwait(homeStorage.setHome(playerId, home3));

            int count = blockingGet(homeStorage.countOwnedHomes(playerId));

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("excludes soft-deleted homes")
        void countOwnedHomes_excludesSoftDeleted() {
            UUID playerId = UUID.randomUUID();

            Home home1 = createHome(playerId, "home", UUID.randomUUID(), 0, 0, 0);
            Home home2 = createHome(playerId, "base", UUID.randomUUID(), 100, 100, 100);
            blockingAwait(homeStorage.setHome(playerId, home1));
            blockingAwait(homeStorage.setHome(playerId, home2));
            blockingGet(homeStorage.deleteHome(playerId, "home"));

            int count = blockingGet(homeStorage.countOwnedHomes(playerId));

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("only counts owned homes, not shared")
        void countOwnedHomes_onlyCountsOwned_notShared() {
            UUID owner = UUID.randomUUID();
            UUID receiver = UUID.randomUUID();

            // Owner has 2 homes
            Home ownerHome1 = createHome(owner, "home", UUID.randomUUID(), 0, 0, 0);
            Home ownerHome2 = createHome(owner, "base", UUID.randomUUID(), 100, 100, 100);
            blockingAwait(homeStorage.setHome(owner, ownerHome1));
            blockingAwait(homeStorage.setHome(owner, ownerHome2));

            // Owner shares one home with receiver
            blockingGet(homeStorage.shareHome(owner, "base", receiver));

            // Receiver has 1 own home
            Home receiverHome = createHome(receiver, "my-home", UUID.randomUUID(), 200, 200, 200);
            blockingAwait(homeStorage.setHome(receiver, receiverHome));

            // Receiver should only count their owned home, not the shared one
            int receiverCount = blockingGet(homeStorage.countOwnedHomes(receiver));
            assertThat(receiverCount).isEqualTo(1);

            // Owner count should be 2
            int ownerCount = blockingGet(homeStorage.countOwnedHomes(owner));
            assertThat(ownerCount).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("Different players have separate homes")
    void differentPlayers_separateHomes() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        Home home1 = createHome(player1, "home", UUID.randomUUID(), 0, 0, 0);
        Home home2 = createHome(player2, "home", UUID.randomUUID(), 100, 100, 100);
        blockingAwait(homeStorage.setHome(player1, home1));
        blockingAwait(homeStorage.setHome(player2, home2));

        var retrieved1 = blockingGet(homeStorage.getHome(player1, "home"));
        var retrieved2 = blockingGet(homeStorage.getHome(player2, "home"));

        assertThat(retrieved1).isPresent();
        assertThat(retrieved1.get().x()).isEqualTo(0);

        assertThat(retrieved2).isPresent();
        assertThat(retrieved2.get().x()).isEqualTo(100);
    }

    /**
     * Helper to create a Home record without Bukkit dependencies.
     */
    private Home createHome(UUID ownerId, String name, UUID worldId,
                            double x, double y, double z) {
        return new Home(
                UUID.randomUUID(),
                name.toLowerCase().trim(),
                ownerId,
                null,
                worldId,
                x, y, z,
                0.0f, 0.0f,
                new HashSet<>()
        );
    }
}
