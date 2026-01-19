package sh.joey.mc.protection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import sh.joey.mc.storage.PostgresIntegrationTest;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Integration tests for RegionStorage.
 * Tests CRUD operations, member management, and soft-delete behavior.
 */
class RegionStorageIntegrationTest extends PostgresIntegrationTest {

    private RegionStorage regionStorage;

    @BeforeEach
    void setUpStorage() {
        regionStorage = new RegionStorage(storage);
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudTests {

        @Test
        @DisplayName("Get region returns empty when no region exists")
        void getRegion_noRegion_returnsEmpty() {
            UUID regionId = UUID.randomUUID();

            Optional<Region> region = blockingGet(regionStorage.getRegion(regionId));

            assertThat(region).isEmpty();
        }

        @Test
        @DisplayName("Create and get region by ID")
        void createAndGetRegionById() {
            UUID ownerId = UUID.randomUUID();
            UUID worldId = UUID.randomUUID();
            Region region = createRegion(ownerId, "base", worldId, 100, 64, 200, 16);

            blockingAwait(regionStorage.createRegion(region));

            Optional<Region> retrieved = blockingGet(regionStorage.getRegion(region.id()));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().id()).isEqualTo(region.id());
            assertThat(retrieved.get().name()).isEqualTo("base");
            assertThat(retrieved.get().ownerId()).isEqualTo(ownerId);
            assertThat(retrieved.get().worldId()).isEqualTo(worldId);
            assertThat(retrieved.get().centerX()).isEqualTo(100);
            assertThat(retrieved.get().centerY()).isEqualTo(64);
            assertThat(retrieved.get().centerZ()).isEqualTo(200);
            assertThat(retrieved.get().radius()).isEqualTo(16);
        }

        @Test
        @DisplayName("Create and get region by owner and name")
        void createAndGetRegionByOwnerAndName() {
            UUID ownerId = UUID.randomUUID();
            UUID worldId = UUID.randomUUID();
            Region region = createRegion(ownerId, "MyBase", worldId, 0, 0, 0, 16);

            blockingAwait(regionStorage.createRegion(region));

            // Can retrieve with different case
            Optional<Region> retrieved = blockingGet(regionStorage.getRegion(ownerId, "mybase"));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().id()).isEqualTo(region.id());
        }

        @Test
        @DisplayName("Delete region soft-deletes the entry")
        void deleteRegion_softDeletes() throws Exception {
            UUID ownerId = UUID.randomUUID();
            Region region = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));

            boolean deleted = blockingGet(regionStorage.deleteRegion(region.id()));

            assertThat(deleted).isTrue();
            assertThat(blockingGet(regionStorage.getRegion(region.id()))).isEmpty();

            // Verify soft delete (row still exists with deleted_at set)
            int activeCount = countRows("SELECT COUNT(*) FROM protection_regions WHERE id = '" + region.id() + "' AND deleted_at IS NULL");
            int totalCount = countRows("SELECT COUNT(*) FROM protection_regions WHERE id = '" + region.id() + "'");
            assertThat(activeCount).isEqualTo(0);
            assertThat(totalCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Delete non-existent region returns false")
        void deleteNonExistentRegion_returnsFalse() {
            UUID regionId = UUID.randomUUID();

            boolean deleted = blockingGet(regionStorage.deleteRegion(regionId));

            assertThat(deleted).isFalse();
        }
    }

    @Nested
    @DisplayName("Name Normalization")
    class NameNormalizationTests {

        @Test
        @DisplayName("Region names are normalized to lowercase")
        void regionNamesNormalized() {
            UUID ownerId = UUID.randomUUID();
            Region region = createRegion(ownerId, "MyBase", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));

            // Can retrieve with different case
            assertThat(blockingGet(regionStorage.getRegion(ownerId, "mybase"))).isPresent();
            assertThat(blockingGet(regionStorage.getRegion(ownerId, "MYBASE"))).isPresent();
            assertThat(blockingGet(regionStorage.getRegion(ownerId, "MyBase"))).isPresent();
        }

        @Test
        @DisplayName("Normalize name helper works correctly")
        void normalizeName_works() {
            assertThat(RegionStorage.normalizeName("  MyBase  ")).isEqualTo("mybase");
            assertThat(RegionStorage.normalizeName("HOME")).isEqualTo("home");
            assertThat(RegionStorage.normalizeName("test")).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("Get Owned Regions")
    class GetOwnedRegionsTests {

        @Test
        @DisplayName("Get owned regions returns empty when no regions")
        void getOwnedRegions_noRegions_returnsEmpty() {
            UUID ownerId = UUID.randomUUID();

            List<Region> regions = blockingList(regionStorage.getOwnedRegions(ownerId));

            assertThat(regions).isEmpty();
        }

        @Test
        @DisplayName("Get owned regions returns all owned regions")
        void getOwnedRegions_returnsAllOwned() {
            UUID ownerId = UUID.randomUUID();

            Region region1 = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            Region region2 = createRegion(ownerId, "base", UUID.randomUUID(), 100, 100, 100, 24);
            blockingAwait(regionStorage.createRegion(region1));
            blockingAwait(regionStorage.createRegion(region2));

            List<Region> regions = blockingList(regionStorage.getOwnedRegions(ownerId));

            assertThat(regions).hasSize(2);
            assertThat(regions).extracting(Region::name).containsExactlyInAnyOrder("home", "base");
        }

        @Test
        @DisplayName("Get owned regions excludes deleted regions")
        void getOwnedRegions_excludesDeleted() {
            UUID ownerId = UUID.randomUUID();

            Region region1 = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            Region region2 = createRegion(ownerId, "base", UUID.randomUUID(), 100, 100, 100, 24);
            blockingAwait(regionStorage.createRegion(region1));
            blockingAwait(regionStorage.createRegion(region2));
            blockingGet(regionStorage.deleteRegion(region1.id()));

            List<Region> regions = blockingList(regionStorage.getOwnedRegions(ownerId));

            assertThat(regions).hasSize(1);
            assertThat(regions.get(0).name()).isEqualTo("base");
        }
    }

    @Nested
    @DisplayName("Count Owned Regions")
    class CountOwnedRegionsTests {

        @Test
        @DisplayName("Returns 0 when no regions")
        void countOwnedRegions_noRegions_returnsZero() {
            UUID ownerId = UUID.randomUUID();

            int count = blockingGet(regionStorage.countOwnedRegions(ownerId));

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("Returns correct count after adding regions")
        void countOwnedRegions_afterAddingRegions_returnsCorrectCount() {
            UUID ownerId = UUID.randomUUID();

            Region region1 = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            Region region2 = createRegion(ownerId, "base", UUID.randomUUID(), 100, 100, 100, 24);
            Region region3 = createRegion(ownerId, "farm", UUID.randomUUID(), 200, 200, 200, 32);
            blockingAwait(regionStorage.createRegion(region1));
            blockingAwait(regionStorage.createRegion(region2));
            blockingAwait(regionStorage.createRegion(region3));

            int count = blockingGet(regionStorage.countOwnedRegions(ownerId));

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("Excludes soft-deleted regions")
        void countOwnedRegions_excludesSoftDeleted() {
            UUID ownerId = UUID.randomUUID();

            Region region1 = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            Region region2 = createRegion(ownerId, "base", UUID.randomUUID(), 100, 100, 100, 24);
            blockingAwait(regionStorage.createRegion(region1));
            blockingAwait(regionStorage.createRegion(region2));
            blockingGet(regionStorage.deleteRegion(region1.id()));

            int count = blockingGet(regionStorage.countOwnedRegions(ownerId));

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Member Management")
    class MemberManagementTests {

        @Test
        @DisplayName("Add member returns true when added")
        void addMember_returnsTrue() {
            UUID ownerId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            Region region = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));

            boolean added = blockingGet(regionStorage.addMember(region.id(), memberId));

            assertThat(added).isTrue();
        }

        @Test
        @DisplayName("Add member returns false when already member")
        void addMember_alreadyMember_returnsFalse() {
            UUID ownerId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            Region region = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));
            blockingGet(regionStorage.addMember(region.id(), memberId));

            boolean added = blockingGet(regionStorage.addMember(region.id(), memberId));

            assertThat(added).isFalse();
        }

        @Test
        @DisplayName("Remove member returns true when removed")
        void removeMember_returnsTrue() {
            UUID ownerId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            Region region = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));
            blockingGet(regionStorage.addMember(region.id(), memberId));

            boolean removed = blockingGet(regionStorage.removeMember(region.id(), memberId));

            assertThat(removed).isTrue();
        }

        @Test
        @DisplayName("Remove member returns false when not member")
        void removeMember_notMember_returnsFalse() {
            UUID ownerId = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();
            Region region = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));

            boolean removed = blockingGet(regionStorage.removeMember(region.id(), memberId));

            assertThat(removed).isFalse();
        }

        @Test
        @DisplayName("Members are included when retrieving region")
        void membersIncludedInRegion() {
            UUID ownerId = UUID.randomUUID();
            UUID member1 = UUID.randomUUID();
            UUID member2 = UUID.randomUUID();
            Region region = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));
            blockingGet(regionStorage.addMember(region.id(), member1));
            blockingGet(regionStorage.addMember(region.id(), member2));

            Optional<Region> retrieved = blockingGet(regionStorage.getRegion(region.id()));

            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().members()).containsExactlyInAnyOrder(member1, member2);
        }

        @Test
        @DisplayName("Get member regions returns regions where player is member")
        void getMemberRegions_returnsCorrectRegions() {
            UUID owner1 = UUID.randomUUID();
            UUID owner2 = UUID.randomUUID();
            UUID memberId = UUID.randomUUID();

            Region region1 = createRegion(owner1, "base1", UUID.randomUUID(), 0, 0, 0, 16);
            Region region2 = createRegion(owner2, "base2", UUID.randomUUID(), 1000, 0, 1000, 16);
            blockingAwait(regionStorage.createRegion(region1));
            blockingAwait(regionStorage.createRegion(region2));
            blockingGet(regionStorage.addMember(region1.id(), memberId));
            blockingGet(regionStorage.addMember(region2.id(), memberId));

            List<Region> memberRegions = blockingList(regionStorage.getMemberRegions(memberId));

            assertThat(memberRegions).hasSize(2);
            assertThat(memberRegions).extracting(Region::name).containsExactlyInAnyOrder("base1", "base2");
        }
    }

    @Nested
    @DisplayName("Access Settings")
    class AccessSettingsTests {

        @Test
        @DisplayName("Update access settings persists correctly")
        void updateAccess_persistsCorrectly() {
            UUID ownerId = UUID.randomUUID();
            Region region = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));

            blockingAwait(regionStorage.updateAccess(region.id(),
                    AccessLevel.OWNER, AccessLevel.EVERYBODY, AccessLevel.MEMBERS));

            Optional<Region> retrieved = blockingGet(regionStorage.getRegion(region.id()));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().buildingAccess()).isEqualTo(AccessLevel.OWNER);
            assertThat(retrieved.get().containerAccess()).isEqualTo(AccessLevel.EVERYBODY);
            assertThat(retrieved.get().doorAccess()).isEqualTo(AccessLevel.MEMBERS);
        }
    }

    @Nested
    @DisplayName("Radius Updates")
    class RadiusUpdateTests {

        @Test
        @DisplayName("Update radius persists correctly")
        void updateRadius_persistsCorrectly() {
            UUID ownerId = UUID.randomUUID();
            Region region = createRegion(ownerId, "home", UUID.randomUUID(), 0, 0, 0, 16);
            blockingAwait(regionStorage.createRegion(region));

            blockingAwait(regionStorage.updateRadius(region.id(), 32));

            Optional<Region> retrieved = blockingGet(regionStorage.getRegion(region.id()));
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().radius()).isEqualTo(32);
        }
    }

    @Nested
    @DisplayName("Get All Regions")
    class GetAllRegionsTests {

        @Test
        @DisplayName("Get all regions returns all active regions")
        void getAllRegions_returnsAllActive() {
            UUID owner1 = UUID.randomUUID();
            UUID owner2 = UUID.randomUUID();

            Region region1 = createRegion(owner1, "base1", UUID.randomUUID(), 0, 0, 0, 16);
            Region region2 = createRegion(owner2, "base2", UUID.randomUUID(), 1000, 0, 1000, 24);
            Region region3 = createRegion(owner1, "deleted", UUID.randomUUID(), 2000, 0, 2000, 16);
            blockingAwait(regionStorage.createRegion(region1));
            blockingAwait(regionStorage.createRegion(region2));
            blockingAwait(regionStorage.createRegion(region3));
            blockingGet(regionStorage.deleteRegion(region3.id()));

            List<Region> allRegions = blockingList(regionStorage.getAllRegions());

            assertThat(allRegions).hasSize(2);
            assertThat(allRegions).extracting(Region::name).containsExactlyInAnyOrder("base1", "base2");
        }
    }

    @Nested
    @DisplayName("Name Uniqueness")
    class NameUniquenessTests {

        @Test
        @DisplayName("Same name allowed for different owners")
        void sameName_differentOwners_allowed() {
            UUID owner1 = UUID.randomUUID();
            UUID owner2 = UUID.randomUUID();

            Region region1 = createRegion(owner1, "home", UUID.randomUUID(), 0, 0, 0, 16);
            Region region2 = createRegion(owner2, "home", UUID.randomUUID(), 1000, 0, 1000, 16);
            blockingAwait(regionStorage.createRegion(region1));
            blockingAwait(regionStorage.createRegion(region2));

            Optional<Region> retrieved1 = blockingGet(regionStorage.getRegion(owner1, "home"));
            Optional<Region> retrieved2 = blockingGet(regionStorage.getRegion(owner2, "home"));

            assertThat(retrieved1).isPresent();
            assertThat(retrieved2).isPresent();
            assertThat(retrieved1.get().centerX()).isEqualTo(0);
            assertThat(retrieved2.get().centerX()).isEqualTo(1000);
        }
    }

    /**
     * Helper to create a Region record without Bukkit dependencies.
     */
    private Region createRegion(UUID ownerId, String name, UUID worldId,
                                int centerX, int centerY, int centerZ, int radius) {
        return new Region(
                UUID.randomUUID(),
                ownerId,
                null,
                name.toLowerCase().trim(),
                worldId,
                centerX, centerY, centerZ,
                radius,
                AccessLevel.MEMBERS,
                AccessLevel.MEMBERS,
                AccessLevel.EVERYBODY,
                new HashSet<>()
        );
    }
}
