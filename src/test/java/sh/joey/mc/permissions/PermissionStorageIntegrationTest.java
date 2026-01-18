package sh.joey.mc.permissions;

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
 * Integration tests for PermissionStorage.
 * These tests verify database operations work correctly,
 * particularly around NULL handling in unique constraints.
 */
class PermissionStorageIntegrationTest extends PostgresIntegrationTest {

    private PermissionStorage permissionStorage;

    @BeforeEach
    void setUpStorage() {
        permissionStorage = new PermissionStorage(storage);
    }

    @Nested
    @DisplayName("Group CRUD Operations")
    class GroupCrudTests {

        @Test
        @DisplayName("Create and retrieve a group")
        void createAndRetrieveGroup() {
            blockingAwait(permissionStorage.createGroup("TestGroup", 100));

            Optional<Group> group = blockingGet(permissionStorage.getGroup("TestGroup"));

            assertThat(group).isPresent();
            assertThat(group.get().displayName()).isEqualTo("TestGroup");
            assertThat(group.get().canonicalName()).isEqualTo("testgroup");
            assertThat(group.get().priority()).isEqualTo(100);
            assertThat(group.get().isDefault()).isFalse();
        }

        @Test
        @DisplayName("Group lookup is case-insensitive")
        void groupLookupCaseInsensitive() {
            blockingAwait(permissionStorage.createGroup("TestGroup", 100));

            Optional<Group> lower = blockingGet(permissionStorage.getGroup("testgroup"));
            Optional<Group> upper = blockingGet(permissionStorage.getGroup("TESTGROUP"));
            Optional<Group> mixed = blockingGet(permissionStorage.getGroup("TeStGrOuP"));

            assertThat(lower).isPresent();
            assertThat(upper).isPresent();
            assertThat(mixed).isPresent();
            assertThat(lower.get().displayName()).isEqualTo("TestGroup");
        }

        @Test
        @DisplayName("Delete a group")
        void deleteGroup() {
            blockingAwait(permissionStorage.createGroup("ToDelete", 50));
            assertThat(blockingGet(permissionStorage.groupExists("ToDelete"))).isTrue();

            boolean deleted = blockingGet(permissionStorage.deleteGroup("ToDelete"));

            assertThat(deleted).isTrue();
            assertThat(blockingGet(permissionStorage.groupExists("ToDelete"))).isFalse();
        }

        @Test
        @DisplayName("Set group as default")
        void setGroupDefault() {
            blockingAwait(permissionStorage.createGroup("DefaultGroup", 100));
            blockingAwait(permissionStorage.setGroupDefault("DefaultGroup", true));

            Optional<Group> group = blockingGet(permissionStorage.getGroup("DefaultGroup"));

            assertThat(group).isPresent();
            assertThat(group.get().isDefault()).isTrue();

            List<Group> defaults = blockingList(permissionStorage.getDefaultGroups());
            assertThat(defaults).hasSize(1);
            assertThat(defaults.get(0).canonicalName()).isEqualTo("defaultgroup");
        }

        @Test
        @DisplayName("Set group priority")
        void setGroupPriority() {
            blockingAwait(permissionStorage.createGroup("PriorityGroup", 50));
            blockingAwait(permissionStorage.setGroupPriority("PriorityGroup", 200));

            Optional<Group> group = blockingGet(permissionStorage.getGroup("PriorityGroup"));

            assertThat(group).isPresent();
            assertThat(group.get().priority()).isEqualTo(200);
        }

        @Test
        @DisplayName("Get all groups returns them sorted by priority")
        void getAllGroupsSortedByPriority() {
            blockingAwait(permissionStorage.createGroup("LowPriority", 10));
            blockingAwait(permissionStorage.createGroup("HighPriority", 100));
            blockingAwait(permissionStorage.createGroup("MediumPriority", 50));

            List<Group> groups = blockingList(permissionStorage.getAllGroups());

            assertThat(groups).hasSize(3);
            assertThat(groups.get(0).canonicalName()).isEqualTo("highpriority");
            assertThat(groups.get(1).canonicalName()).isEqualTo("mediumpriority");
            assertThat(groups.get(2).canonicalName()).isEqualTo("lowpriority");
        }
    }

    @Nested
    @DisplayName("Group Permission Upserts - NULL Uniqueness Bug")
    class GroupPermissionUpsertTests {

        @Test
        @DisplayName("Setting same global permission twice results in ONE row")
        void globalPermissionUpsert_singleRow() throws SQLException {
            blockingAwait(permissionStorage.createGroup("Test", 100));

            // Set permission to allow, then deny
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", null, true));
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", null, false));

            // Query database directly to count rows
            int rowCount = countRows(
                    "SELECT COUNT(*) FROM group_permissions WHERE group_name = 'test' AND permission = 'test.perm' AND world_id IS NULL"
            );
            assertThat(rowCount).isEqualTo(1); // Would have been 2 with the bug!

            // Verify state is updated to deny
            Optional<Group> group = blockingGet(permissionStorage.getGroup("Test"));
            assertThat(group).isPresent();
            assertThat(group.get().grants()).hasSize(1);
            assertThat(group.get().grants().get(0).state()).isFalse();
        }

        @Test
        @DisplayName("Setting same world-specific permission twice results in ONE row")
        void worldPermissionUpsert_singleRow() throws SQLException {
            UUID worldId = UUID.randomUUID();
            blockingAwait(permissionStorage.createGroup("Test", 100));

            // Set permission to allow, then deny
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", worldId, true));
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", worldId, false));

            // Query database directly to count rows
            int rowCount = countRows(
                    "SELECT COUNT(*) FROM group_permissions WHERE group_name = 'test' AND permission = 'test.perm' AND world_id = '" + worldId + "'"
            );
            assertThat(rowCount).isEqualTo(1);

            // Verify state is updated to deny
            Optional<Group> group = blockingGet(permissionStorage.getGroup("Test"));
            assertThat(group).isPresent();
            assertThat(group.get().grants()).hasSize(1);
            assertThat(group.get().grants().get(0).state()).isFalse();
        }

        @Test
        @DisplayName("Global and world-specific permissions for same permission string are separate rows")
        void globalAndWorldPermissions_separateRows() throws SQLException {
            UUID worldId = UUID.randomUUID();
            blockingAwait(permissionStorage.createGroup("Test", 100));

            // Add global and world-specific versions of same permission
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", null, true));
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", worldId, false));

            // Should be 2 rows (one global, one world-specific)
            int totalRows = countRows(
                    "SELECT COUNT(*) FROM group_permissions WHERE group_name = 'test' AND permission = 'test.perm'"
            );
            assertThat(totalRows).isEqualTo(2);

            Optional<Group> group = blockingGet(permissionStorage.getGroup("Test"));
            assertThat(group).isPresent();
            assertThat(group.get().grants()).hasSize(2);
        }

        @Test
        @DisplayName("Overwrite from allow to deny updates existing row (preserves ID)")
        void overwriteAllowToDeny_updatesRow() throws SQLException {
            blockingAwait(permissionStorage.createGroup("Test", 100));

            // Set to allow
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", null, true));

            // Get the ID
            Optional<Group> groupBefore = blockingGet(permissionStorage.getGroup("Test"));
            UUID idBefore = groupBefore.get().grants().get(0).id();

            // Update to deny
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", null, false));

            // ID should be the same (row was updated, not replaced)
            Optional<Group> groupAfter = blockingGet(permissionStorage.getGroup("Test"));
            UUID idAfter = groupAfter.get().grants().get(0).id();

            assertThat(idAfter).isEqualTo(idBefore);
            assertThat(groupAfter.get().grants().get(0).state()).isFalse();
        }

        @Test
        @DisplayName("Multiple different permissions with NULL world_id are all unique")
        void differentPermissions_allUnique() throws SQLException {
            blockingAwait(permissionStorage.createGroup("Test", 100));

            blockingAwait(permissionStorage.addGroupPermission("Test", "perm.one", null, true));
            blockingAwait(permissionStorage.addGroupPermission("Test", "perm.two", null, true));
            blockingAwait(permissionStorage.addGroupPermission("Test", "perm.three", null, false));

            int rowCount = countRows(
                    "SELECT COUNT(*) FROM group_permissions WHERE group_name = 'test' AND world_id IS NULL"
            );
            assertThat(rowCount).isEqualTo(3);
        }

        @Test
        @DisplayName("Remove permission deletes it")
        void removePermission() throws SQLException {
            blockingAwait(permissionStorage.createGroup("Test", 100));
            blockingAwait(permissionStorage.addGroupPermission("Test", "test.perm", null, true));

            boolean removed = blockingGet(permissionStorage.removeGroupPermission("Test", "test.perm"));

            assertThat(removed).isTrue();

            int rowCount = countRows(
                    "SELECT COUNT(*) FROM group_permissions WHERE group_name = 'test' AND permission = 'test.perm'"
            );
            assertThat(rowCount).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Player Permission Upserts - NULL Uniqueness Bug")
    class PlayerPermissionUpsertTests {

        private final UUID playerId = UUID.randomUUID();

        @Test
        @DisplayName("Setting same global permission twice results in ONE row")
        void globalPermissionUpsert_singleRow() throws SQLException {
            // Set permission to allow, then deny
            blockingAwait(permissionStorage.addPlayerPermission(playerId, "test.perm", null, true));
            blockingAwait(permissionStorage.addPlayerPermission(playerId, "test.perm", null, false));

            // Query database directly to count rows
            int rowCount = countRows(
                    "SELECT COUNT(*) FROM player_permissions WHERE player_id = '" + playerId + "' AND permission = 'test.perm' AND world_id IS NULL"
            );
            assertThat(rowCount).isEqualTo(1);

            // Verify state is updated to deny
            List<PermissionGrant> grants = blockingList(permissionStorage.getPlayerPermissions(playerId));
            assertThat(grants).hasSize(1);
            assertThat(grants.get(0).state()).isFalse();
        }

        @Test
        @DisplayName("Setting same world-specific permission twice results in ONE row")
        void worldPermissionUpsert_singleRow() throws SQLException {
            UUID worldId = UUID.randomUUID();

            // Set permission to allow, then deny
            blockingAwait(permissionStorage.addPlayerPermission(playerId, "test.perm", worldId, true));
            blockingAwait(permissionStorage.addPlayerPermission(playerId, "test.perm", worldId, false));

            // Query database directly to count rows
            int rowCount = countRows(
                    "SELECT COUNT(*) FROM player_permissions WHERE player_id = '" + playerId + "' AND permission = 'test.perm' AND world_id = '" + worldId + "'"
            );
            assertThat(rowCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Global and world-specific permissions are separate rows")
        void globalAndWorldPermissions_separateRows() throws SQLException {
            UUID worldId = UUID.randomUUID();

            blockingAwait(permissionStorage.addPlayerPermission(playerId, "test.perm", null, true));
            blockingAwait(permissionStorage.addPlayerPermission(playerId, "test.perm", worldId, false));

            int totalRows = countRows(
                    "SELECT COUNT(*) FROM player_permissions WHERE player_id = '" + playerId + "' AND permission = 'test.perm'"
            );
            assertThat(totalRows).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Player-Group Membership")
    class PlayerGroupMembershipTests {

        private final UUID playerId = UUID.randomUUID();

        @Test
        @DisplayName("Add player to group")
        void addPlayerToGroup() {
            blockingAwait(permissionStorage.createGroup("Members", 100));
            blockingAwait(permissionStorage.addPlayerToGroup(playerId, "Members"));

            List<String> groups = blockingList(permissionStorage.getPlayerExplicitGroups(playerId));

            assertThat(groups).containsExactly("members");
        }

        @Test
        @DisplayName("Adding same player to same group twice is idempotent")
        void addPlayerToGroupIdempotent() throws SQLException {
            blockingAwait(permissionStorage.createGroup("Members", 100));
            blockingAwait(permissionStorage.addPlayerToGroup(playerId, "Members"));
            blockingAwait(permissionStorage.addPlayerToGroup(playerId, "Members"));

            int rowCount = countRows(
                    "SELECT COUNT(*) FROM player_groups WHERE player_id = '" + playerId + "' AND group_name = 'members'"
            );
            assertThat(rowCount).isEqualTo(1);
        }

        @Test
        @DisplayName("Remove player from group")
        void removePlayerFromGroup() {
            blockingAwait(permissionStorage.createGroup("Members", 100));
            blockingAwait(permissionStorage.addPlayerToGroup(playerId, "Members"));

            boolean removed = blockingGet(permissionStorage.removePlayerFromGroup(playerId, "Members"));

            assertThat(removed).isTrue();
            List<String> groups = blockingList(permissionStorage.getPlayerExplicitGroups(playerId));
            assertThat(groups).isEmpty();
        }

        @Test
        @DisplayName("Get group members")
        void getGroupMembers() {
            UUID player1 = UUID.randomUUID();
            UUID player2 = UUID.randomUUID();
            blockingAwait(permissionStorage.createGroup("Staff", 100));
            blockingAwait(permissionStorage.addPlayerToGroup(player1, "Staff"));
            blockingAwait(permissionStorage.addPlayerToGroup(player2, "Staff"));

            List<UUID> members = blockingList(permissionStorage.getGroupMembers("Staff"));

            assertThat(members).containsExactlyInAnyOrder(player1, player2);
        }

        @Test
        @DisplayName("Get player groups includes default groups")
        void getPlayerGroupsIncludesDefaults() {
            blockingAwait(permissionStorage.createGroup("DefaultGroup", 50));
            blockingAwait(permissionStorage.setGroupDefault("DefaultGroup", true));
            blockingAwait(permissionStorage.createGroup("ExplicitGroup", 100));
            blockingAwait(permissionStorage.addPlayerToGroup(playerId, "ExplicitGroup"));

            List<Group> groups = blockingList(permissionStorage.getPlayerGroups(playerId));

            assertThat(groups).hasSize(2);
            assertThat(groups).extracting(Group::canonicalName)
                    .containsExactlyInAnyOrder("defaultgroup", "explicitgroup");
        }
    }

    @Nested
    @DisplayName("Group Attributes")
    class GroupAttributesTests {

        @Test
        @DisplayName("Set chat prefix and suffix")
        void setChatPrefixAndSuffix() {
            blockingAwait(permissionStorage.createGroup("VIP", 100));
            blockingAwait(permissionStorage.setGroupAttribute("VIP", "chat", "prefix", "&6[VIP] "));
            blockingAwait(permissionStorage.setGroupAttribute("VIP", "chat", "suffix", " &6★"));

            Optional<Group> group = blockingGet(permissionStorage.getGroup("VIP"));

            assertThat(group).isPresent();
            assertThat(group.get().attributes().chatPrefix()).isEqualTo("&6[VIP] ");
            assertThat(group.get().attributes().chatSuffix()).isEqualTo(" &6★");
        }

        @Test
        @DisplayName("Set nameplate prefix and suffix")
        void setNameplatePrefixAndSuffix() {
            blockingAwait(permissionStorage.createGroup("Admin", 100));
            blockingAwait(permissionStorage.setGroupAttribute("Admin", "nameplate", "prefix", "&c"));
            blockingAwait(permissionStorage.setGroupAttribute("Admin", "nameplate", "suffix", " &7[A]"));

            Optional<Group> group = blockingGet(permissionStorage.getGroup("Admin"));

            assertThat(group).isPresent();
            assertThat(group.get().attributes().nameplatePrefix()).isEqualTo("&c");
            assertThat(group.get().attributes().nameplateSuffix()).isEqualTo(" &7[A]");
        }

        @Test
        @DisplayName("Clear attribute by setting to null")
        void clearAttribute() {
            blockingAwait(permissionStorage.createGroup("Test", 100));
            blockingAwait(permissionStorage.setGroupAttribute("Test", "chat", "prefix", "&a[Test] "));
            blockingAwait(permissionStorage.setGroupAttribute("Test", "chat", "prefix", null));

            Optional<Group> group = blockingGet(permissionStorage.getGroup("Test"));

            assertThat(group).isPresent();
            assertThat(group.get().attributes().chatPrefix()).isNull();
        }
    }

    @Nested
    @DisplayName("Player Attributes")
    class PlayerAttributesTests {

        private final UUID playerId = UUID.randomUUID();

        @Test
        @DisplayName("Set and get player attributes")
        void setAndGetPlayerAttributes() {
            blockingAwait(permissionStorage.setPlayerAttribute(playerId, "chat", "prefix", "&b[Cool] "));
            blockingAwait(permissionStorage.setPlayerAttribute(playerId, "nameplate", "suffix", " &3✦"));

            Optional<PermissibleAttributes> attrs = blockingGet(permissionStorage.getPlayerAttributes(playerId));

            assertThat(attrs).isPresent();
            assertThat(attrs.get().chatPrefix()).isEqualTo("&b[Cool] ");
            assertThat(attrs.get().nameplateSuffix()).isEqualTo(" &3✦");
        }

        @Test
        @DisplayName("Update existing player attribute (upsert)")
        void updatePlayerAttribute() throws SQLException {
            blockingAwait(permissionStorage.setPlayerAttribute(playerId, "chat", "prefix", "&a[First] "));
            blockingAwait(permissionStorage.setPlayerAttribute(playerId, "chat", "prefix", "&b[Second] "));

            int rowCount = countRows(
                    "SELECT COUNT(*) FROM perm_players WHERE player_id = '" + playerId + "'"
            );
            assertThat(rowCount).isEqualTo(1);

            Optional<PermissibleAttributes> attrs = blockingGet(permissionStorage.getPlayerAttributes(playerId));
            assertThat(attrs).isPresent();
            assertThat(attrs.get().chatPrefix()).isEqualTo("&b[Second] ");
        }
    }
}
