package sh.joey.mc.protection;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles persistence of protection regions to PostgreSQL.
 * All operations are async and return RxJava types.
 */
public final class RegionStorage {

    private final StorageService storage;

    public RegionStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Normalizes a region name: lowercase and trimmed.
     */
    public static String normalizeName(String name) {
        return name.toLowerCase().trim();
    }

    /**
     * Get all active regions (for cache loading on startup).
     */
    public Flowable<Region> getAllRegions() {
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT r.id, r.owner_id, r.name, r.world_id,
                       r.center_x, r.center_y, r.center_z, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm.member_id) FILTER (WHERE rm.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                LEFT JOIN region_members rm ON r.id = rm.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id,
                         r.center_x, r.center_y, r.center_z, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                ORDER BY r.created_at
                """;

            List<Region> regions = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    regions.add(readRegion(rs));
                }
            }
            return regions;
        });
    }

    /**
     * Get a specific region by ID.
     */
    public Maybe<Region> getRegion(UUID regionId) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT r.id, r.owner_id, r.name, r.world_id,
                       r.center_x, r.center_y, r.center_z, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm.member_id) FILTER (WHERE rm.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                LEFT JOIN region_members rm ON r.id = rm.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.id = ? AND r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id,
                         r.center_x, r.center_y, r.center_z, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, regionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readRegion(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Get a specific region by owner ID and name.
     */
    public Maybe<Region> getRegion(UUID ownerId, String name) {
        String normalizedName = normalizeName(name);
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT r.id, r.owner_id, r.name, r.world_id,
                       r.center_x, r.center_y, r.center_z, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm.member_id) FILTER (WHERE rm.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                LEFT JOIN region_members rm ON r.id = rm.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.owner_id = ? AND LOWER(r.name) = ? AND r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id,
                         r.center_x, r.center_y, r.center_z, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, ownerId);
                stmt.setString(2, normalizedName);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readRegion(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Get all regions owned by a player.
     */
    public Flowable<Region> getOwnedRegions(UUID ownerId) {
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT r.id, r.owner_id, r.name, r.world_id,
                       r.center_x, r.center_y, r.center_z, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm.member_id) FILTER (WHERE rm.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                LEFT JOIN region_members rm ON r.id = rm.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.owner_id = ? AND r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id,
                         r.center_x, r.center_y, r.center_z, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                ORDER BY r.created_at
                """;

            List<Region> regions = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, ownerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        regions.add(readRegion(rs));
                    }
                }
            }
            return regions;
        });
    }

    /**
     * Get all regions where the player is a member (not owner).
     */
    public Flowable<Region> getMemberRegions(UUID memberId) {
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT r.id, r.owner_id, r.name, r.world_id,
                       r.center_x, r.center_y, r.center_z, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm2.member_id) FILTER (WHERE rm2.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                INNER JOIN region_members rm ON r.id = rm.region_id AND rm.member_id = ?
                LEFT JOIN region_members rm2 ON r.id = rm2.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id,
                         r.center_x, r.center_y, r.center_z, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                ORDER BY r.created_at
                """;

            List<Region> regions = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, memberId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        regions.add(readRegion(rs));
                    }
                }
            }
            return regions;
        });
    }

    /**
     * Count the number of regions owned by a player.
     */
    public Single<Integer> countOwnedRegions(UUID ownerId) {
        return storage.query(conn -> {
            String sql = "SELECT COUNT(*) FROM protection_regions WHERE owner_id = ? AND deleted_at IS NULL";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, ownerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    /**
     * Create a new region.
     */
    public Completable createRegion(Region region) {
        String normalizedName = normalizeName(region.name());
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO protection_regions
                    (id, owner_id, name, world_id, center_x, center_y, center_z, radius,
                     building_access, container_access, door_access)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, region.id());
                stmt.setObject(2, region.ownerId());
                stmt.setString(3, normalizedName);
                stmt.setObject(4, region.worldId());
                stmt.setInt(5, region.centerX());
                stmt.setInt(6, region.centerY());
                stmt.setInt(7, region.centerZ());
                stmt.setInt(8, region.radius());
                stmt.setString(9, region.buildingAccess().name());
                stmt.setString(10, region.containerAccess().name());
                stmt.setString(11, region.doorAccess().name());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Soft-delete a region by setting deleted_at.
     *
     * @return true if a region was deleted
     */
    public Single<Boolean> deleteRegion(UUID regionId) {
        return storage.query(conn -> {
            String sql = "UPDATE protection_regions SET deleted_at = NOW() WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, regionId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Update the radius of a region.
     */
    public Completable updateRadius(UUID regionId, int radius) {
        return storage.execute(conn -> {
            String sql = "UPDATE protection_regions SET radius = ? WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, radius);
                stmt.setObject(2, regionId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Update the access settings of a region.
     */
    public Completable updateAccess(UUID regionId, AccessLevel building, AccessLevel containers, AccessLevel doors) {
        return storage.execute(conn -> {
            String sql = """
                UPDATE protection_regions
                SET building_access = ?, container_access = ?, door_access = ?
                WHERE id = ? AND deleted_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, building.name());
                stmt.setString(2, containers.name());
                stmt.setString(3, doors.name());
                stmt.setObject(4, regionId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Add a member to a region.
     *
     * @return true if the member was added (false if already a member)
     */
    public Single<Boolean> addMember(UUID regionId, UUID memberId) {
        return storage.query(conn -> {
            String sql = """
                INSERT INTO region_members (region_id, member_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, regionId);
                stmt.setObject(2, memberId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Remove a member from a region.
     *
     * @return true if the member was removed
     */
    public Single<Boolean> removeMember(UUID regionId, UUID memberId) {
        return storage.query(conn -> {
            String sql = "DELETE FROM region_members WHERE region_id = ? AND member_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, regionId);
                stmt.setObject(2, memberId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Get all members of a region.
     */
    public Flowable<UUID> getMembers(UUID regionId) {
        return storage.queryFlowable(conn -> {
            String sql = "SELECT member_id FROM region_members WHERE region_id = ?";

            List<UUID> members = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, regionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        members.add(rs.getObject("member_id", UUID.class));
                    }
                }
            }
            return members;
        });
    }

    private Region readRegion(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID ownerId = rs.getObject("owner_id", UUID.class);
        String ownerName = rs.getString("owner_name");
        String name = rs.getString("name");
        UUID worldId = rs.getObject("world_id", UUID.class);
        int centerX = rs.getInt("center_x");
        int centerY = rs.getInt("center_y");
        int centerZ = rs.getInt("center_z");
        int radius = rs.getInt("radius");
        AccessLevel buildingAccess = AccessLevel.fromString(rs.getString("building_access"));
        AccessLevel containerAccess = AccessLevel.fromString(rs.getString("container_access"));
        AccessLevel doorAccess = AccessLevel.fromString(rs.getString("door_access"));

        Set<UUID> members = new HashSet<>();
        java.sql.Array membersArray = rs.getArray("members");
        if (membersArray != null) {
            UUID[] uuids = (UUID[]) membersArray.getArray();
            for (UUID uuid : uuids) {
                if (uuid != null) {
                    members.add(uuid);
                }
            }
        }

        return new Region(id, ownerId, ownerName, name, worldId, centerX, centerY, centerZ,
                radius, buildingAccess, containerAccess, doorAccess, members);
    }
}
