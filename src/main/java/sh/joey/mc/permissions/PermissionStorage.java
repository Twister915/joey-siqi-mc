package sh.joey.mc.permissions;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles persistence of permission data to PostgreSQL.
 * All operations are async and return RxJava types.
 */
public final class PermissionStorage {

    private final StorageService storage;

    public PermissionStorage(StorageService storage) {
        this.storage = storage;
    }

    // ========== Group Operations ==========

    /**
     * Get a group by name (case-insensitive).
     * Returns the group with all its permission grants.
     */
    public Maybe<Group> getGroup(String name) {
        String canonical = Group.normalize(name);
        return storage.queryMaybe(conn -> {
            // First get group record
            String groupSql = """
                    SELECT canonical_name, display_name, priority, is_default,
                           chat_prefix, chat_suffix, nameplate_prefix, nameplate_suffix, name_color,
                           created_at, updated_at
                    FROM perm_groups WHERE canonical_name = ?
                    """;

            Group group = null;
            try (PreparedStatement stmt = conn.prepareStatement(groupSql)) {
                stmt.setString(1, canonical);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        group = readGroup(rs);
                    }
                }
            }

            if (group == null) {
                return null;
            }

            // Then get grants
            List<PermissionGrant> grants = getGroupGrantsSync(conn, canonical, group.priority());

            return group.withGrants(grants);
        });
    }

    /**
     * Get all groups with their permission grants.
     */
    public Flowable<Group> getAllGroups() {
        return storage.queryFlowable(conn -> {
            String sql = """
                    SELECT canonical_name, display_name, priority, is_default,
                           chat_prefix, chat_suffix, nameplate_prefix, nameplate_suffix, name_color,
                           created_at, updated_at
                    FROM perm_groups
                    ORDER BY priority DESC, canonical_name
                    """;

            List<Group> groups = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Group group = readGroup(rs);
                    // Load grants for each group
                    List<PermissionGrant> grants = getGroupGrantsSync(conn, group.canonicalName(), group.priority());
                    groups.add(group.withGrants(grants));
                }
            }
            return groups;
        });
    }

    /**
     * Get all default groups (groups where is_default = true).
     */
    public Flowable<Group> getDefaultGroups() {
        return storage.queryFlowable(conn -> {
            String sql = """
                    SELECT canonical_name, display_name, priority, is_default,
                           chat_prefix, chat_suffix, nameplate_prefix, nameplate_suffix, name_color,
                           created_at, updated_at
                    FROM perm_groups
                    WHERE is_default = TRUE
                    ORDER BY priority DESC
                    """;

            List<Group> groups = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Group group = readGroup(rs);
                    List<PermissionGrant> grants = getGroupGrantsSync(conn, group.canonicalName(), group.priority());
                    groups.add(group.withGrants(grants));
                }
            }
            return groups;
        });
    }

    /**
     * Check if a group exists.
     */
    public Single<Boolean> groupExists(String name) {
        String canonical = Group.normalize(name);
        return storage.query(conn -> {
            String sql = "SELECT 1 FROM perm_groups WHERE canonical_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, canonical);
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /**
     * Create a new group.
     */
    public Completable createGroup(String displayName, int priority) {
        String canonical = Group.normalize(displayName);
        return storage.execute(conn -> {
            String sql = """
                    INSERT INTO perm_groups (canonical_name, display_name, priority)
                    VALUES (?, ?, ?)
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, canonical);
                stmt.setString(2, displayName);
                stmt.setInt(3, priority);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Delete a group.
     */
    public Single<Boolean> deleteGroup(String name) {
        String canonical = Group.normalize(name);
        return storage.query(conn -> {
            String sql = "DELETE FROM perm_groups WHERE canonical_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, canonical);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Set the default flag for a group.
     */
    public Completable setGroupDefault(String name, boolean isDefault) {
        String canonical = Group.normalize(name);
        return storage.execute(conn -> {
            String sql = """
                    UPDATE perm_groups
                    SET is_default = ?, updated_at = NOW()
                    WHERE canonical_name = ?
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setBoolean(1, isDefault);
                stmt.setString(2, canonical);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Set the priority for a group.
     */
    public Completable setGroupPriority(String name, int priority) {
        String canonical = Group.normalize(name);
        return storage.execute(conn -> {
            String sql = """
                    UPDATE perm_groups
                    SET priority = ?, updated_at = NOW()
                    WHERE canonical_name = ?
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, priority);
                stmt.setString(2, canonical);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Set an attribute (prefix/suffix) for a group.
     *
     * @param name           Group name
     * @param attributeType  "chat" or "nameplate"
     * @param prefixOrSuffix "prefix" or "suffix"
     * @param value          The value to set, or null to clear
     */
    public Completable setGroupAttribute(String name, String attributeType, String prefixOrSuffix, @Nullable String value) {
        String canonical = Group.normalize(name);
        String column = attributeType + "_" + prefixOrSuffix;

        // Validate column name to prevent SQL injection
        if (!isValidAttributeColumn(column)) {
            return Completable.error(new IllegalArgumentException("Invalid attribute: " + column));
        }

        return storage.execute(conn -> {
            String sql = "UPDATE perm_groups SET " + column + " = ?, updated_at = NOW() WHERE canonical_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (value == null) {
                    stmt.setNull(1, Types.VARCHAR);
                } else {
                    stmt.setString(1, value);
                }
                stmt.setString(2, canonical);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Add a permission grant to a group.
     */
    public Completable addGroupPermission(String groupName, String permission, @Nullable UUID worldId, boolean state) {
        String canonical = Group.normalize(groupName);
        return storage.execute(conn -> {
            String sql = """
                    INSERT INTO group_permissions (group_name, permission, world_id, state)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (group_name, permission, world_id)
                    DO UPDATE SET state = EXCLUDED.state
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, canonical);
                stmt.setString(2, permission.toLowerCase());
                if (worldId == null) {
                    stmt.setNull(3, Types.OTHER);
                } else {
                    stmt.setObject(3, worldId);
                }
                stmt.setBoolean(4, state);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Remove a permission grant from a group.
     */
    public Single<Boolean> removeGroupPermission(String groupName, String permission) {
        String canonical = Group.normalize(groupName);
        return storage.query(conn -> {
            String sql = "DELETE FROM group_permissions WHERE group_name = ? AND permission = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, canonical);
                stmt.setString(2, permission.toLowerCase());
                return stmt.executeUpdate() > 0;
            }
        });
    }

    // ========== Player Attributes Operations ==========

    /**
     * Get display attributes for a player.
     */
    public Maybe<PermissibleAttributes> getPlayerAttributes(UUID playerId) {
        return storage.queryMaybe(conn -> {
            String sql = """
                    SELECT chat_prefix, chat_suffix, nameplate_prefix, nameplate_suffix, name_color
                    FROM perm_players
                    WHERE player_id = ?
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readAttributes(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Set an attribute (prefix/suffix) for a player.
     *
     * @param playerId       Player UUID
     * @param attributeType  "chat" or "nameplate"
     * @param prefixOrSuffix "prefix" or "suffix"
     * @param value          The value to set, or null to clear
     */
    public Completable setPlayerAttribute(UUID playerId, String attributeType, String prefixOrSuffix, @Nullable String value) {
        String column = attributeType + "_" + prefixOrSuffix;

        // Validate column name to prevent SQL injection
        if (!isValidAttributeColumn(column)) {
            return Completable.error(new IllegalArgumentException("Invalid attribute: " + column));
        }

        return storage.execute(conn -> {
            // First try to insert, then update on conflict
            String sql = """
                    INSERT INTO perm_players (player_id, %s)
                    VALUES (?, ?)
                    ON CONFLICT (player_id)
                    DO UPDATE SET %s = EXCLUDED.%s, updated_at = NOW()
                    """.formatted(column, column, column);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                if (value == null) {
                    stmt.setNull(2, Types.VARCHAR);
                } else {
                    stmt.setString(2, value);
                }
                stmt.executeUpdate();
            }
        });
    }

    // ========== Player Permission Operations ==========

    /**
     * Get all permission grants for a player.
     */
    public Flowable<PermissionGrant> getPlayerPermissions(UUID playerId) {
        return storage.queryFlowable(conn -> {
            String sql = """
                    SELECT id, permission, world_id, state
                    FROM player_permissions
                    WHERE player_id = ?
                    """;
            List<PermissionGrant> grants = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        // Player permissions get highest priority (above all groups)
                        grants.add(readGrant(rs, Integer.MAX_VALUE));
                    }
                }
            }
            return grants;
        });
    }

    /**
     * Add a permission grant to a player.
     */
    public Completable addPlayerPermission(UUID playerId, String permission, @Nullable UUID worldId, boolean state) {
        return storage.execute(conn -> {
            String sql = """
                    INSERT INTO player_permissions (player_id, permission, world_id, state)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (player_id, permission, world_id)
                    DO UPDATE SET state = EXCLUDED.state
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, permission.toLowerCase());
                if (worldId == null) {
                    stmt.setNull(3, Types.OTHER);
                } else {
                    stmt.setObject(3, worldId);
                }
                stmt.setBoolean(4, state);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Remove a permission grant from a player.
     */
    public Single<Boolean> removePlayerPermission(UUID playerId, String permission) {
        return storage.query(conn -> {
            String sql = "DELETE FROM player_permissions WHERE player_id = ? AND permission = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, permission.toLowerCase());
                return stmt.executeUpdate() > 0;
            }
        });
    }

    // ========== Player-Group Membership Operations ==========

    /**
     * Get all groups a player is a member of (explicit + default).
     * Returns groups ordered by priority (highest first).
     */
    public Flowable<Group> getPlayerGroups(UUID playerId) {
        return storage.queryFlowable(conn -> {
            // Get explicit memberships plus default groups
            String sql = """
                    SELECT g.canonical_name, g.display_name, g.priority, g.is_default,
                           g.chat_prefix, g.chat_suffix, g.nameplate_prefix, g.nameplate_suffix, g.name_color,
                           g.created_at, g.updated_at
                    FROM perm_groups g
                    WHERE g.is_default = TRUE
                       OR g.canonical_name IN (
                           SELECT group_name FROM player_groups WHERE player_id = ?
                       )
                    ORDER BY g.priority DESC
                    """;

            List<Group> groups = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Group group = readGroup(rs);
                        List<PermissionGrant> grants = getGroupGrantsSync(conn, group.canonicalName(), group.priority());
                        groups.add(group.withGrants(grants));
                    }
                }
            }
            return groups;
        });
    }

    /**
     * Get explicit group memberships for a player (not including default groups).
     */
    public Flowable<String> getPlayerExplicitGroups(UUID playerId) {
        return storage.queryFlowable(conn -> {
            String sql = "SELECT group_name FROM player_groups WHERE player_id = ?";
            List<String> groups = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        groups.add(rs.getString("group_name"));
                    }
                }
            }
            return groups;
        });
    }

    /**
     * Add a player to a group.
     */
    public Completable addPlayerToGroup(UUID playerId, String groupName) {
        String canonical = Group.normalize(groupName);
        return storage.execute(conn -> {
            String sql = """
                    INSERT INTO player_groups (player_id, group_name)
                    VALUES (?, ?)
                    ON CONFLICT DO NOTHING
                    """;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, canonical);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Remove a player from a group.
     */
    public Single<Boolean> removePlayerFromGroup(UUID playerId, String groupName) {
        String canonical = Group.normalize(groupName);
        return storage.query(conn -> {
            String sql = "DELETE FROM player_groups WHERE player_id = ? AND group_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, canonical);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Get all members of a group (UUIDs).
     */
    public Flowable<UUID> getGroupMembers(String groupName) {
        String canonical = Group.normalize(groupName);
        return storage.queryFlowable(conn -> {
            String sql = "SELECT player_id FROM player_groups WHERE group_name = ?";
            List<UUID> members = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, canonical);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        members.add(rs.getObject("player_id", UUID.class));
                    }
                }
            }
            return members;
        });
    }

    // ========== Helper Methods ==========

    private List<PermissionGrant> getGroupGrantsSync(java.sql.Connection conn, String groupName, int priority) throws SQLException {
        String sql = """
                SELECT id, permission, world_id, state
                FROM group_permissions
                WHERE group_name = ?
                """;
        List<PermissionGrant> grants = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    grants.add(readGrant(rs, priority));
                }
            }
        }
        return grants;
    }

    private Group readGroup(ResultSet rs) throws SQLException {
        PermissibleAttributes attrs = readAttributes(rs);
        return Group.withoutGrants(
                rs.getString("canonical_name"),
                rs.getString("display_name"),
                rs.getInt("priority"),
                rs.getBoolean("is_default"),
                attrs,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private PermissibleAttributes readAttributes(ResultSet rs) throws SQLException {
        return new PermissibleAttributes(
                rs.getString("chat_prefix"),
                rs.getString("chat_suffix"),
                rs.getString("nameplate_prefix"),
                rs.getString("nameplate_suffix"),
                rs.getString("name_color")
        );
    }

    private PermissionGrant readGrant(ResultSet rs, int priority) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String permission = rs.getString("permission");
        UUID worldId = rs.getObject("world_id", UUID.class);
        boolean state = rs.getBoolean("state");

        // Handle potentially invalid permissions from DB
        try {
            return PermissionGrant.of(id, permission, worldId, state, priority);
        } catch (IllegalArgumentException e) {
            // If permission is malformed, create a placeholder that won't match anything
            return new PermissionGrant(id,
                    ParsedPermission.parse("invalid").orElseThrow(),
                    worldId, state, priority);
        }
    }

    private boolean isValidAttributeColumn(String column) {
        return switch (column) {
            case "chat_prefix", "chat_suffix", "nameplate_prefix", "nameplate_suffix", "name_color" -> true;
            default -> false;
        };
    }
}
