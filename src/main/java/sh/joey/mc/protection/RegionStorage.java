package sh.joey.mc.protection;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            // First, load all regions with their members
            String regionSql = """
                SELECT r.id, r.owner_id, r.name, r.world_id, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm.member_id) FILTER (WHERE rm.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                LEFT JOIN region_members rm ON r.id = rm.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                ORDER BY r.created_at
                """;

            // Load all anchors for active regions
            String anchorSql = """
                SELECT a.id, a.region_id, a.x, a.y, a.z, a.created_at
                FROM region_anchors a
                INNER JOIN protection_regions r ON a.region_id = r.id
                WHERE r.deleted_at IS NULL
                ORDER BY a.created_at
                """;

            // Load anchors into a map by region ID
            Map<UUID, List<Anchor>> anchorsByRegion = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(anchorSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Anchor anchor = readAnchor(rs);
                    anchorsByRegion.computeIfAbsent(anchor.regionId(), k -> new ArrayList<>())
                            .add(anchor);
                }
            }

            // Load regions and attach anchors
            List<Region> regions = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(regionSql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID regionId = rs.getObject("id", UUID.class);
                    List<Anchor> anchors = anchorsByRegion.getOrDefault(regionId, List.of());
                    regions.add(readRegion(rs, anchors));
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
                SELECT r.id, r.owner_id, r.name, r.world_id, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm.member_id) FILTER (WHERE rm.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                LEFT JOIN region_members rm ON r.id = rm.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.id = ? AND r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, regionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        List<Anchor> anchors = loadAnchorsForRegion(conn, regionId);
                        return readRegion(rs, anchors);
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
                SELECT r.id, r.owner_id, r.name, r.world_id, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm.member_id) FILTER (WHERE rm.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                LEFT JOIN region_members rm ON r.id = rm.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.owner_id = ? AND LOWER(r.name) = ? AND r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, ownerId);
                stmt.setString(2, normalizedName);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        UUID regionId = rs.getObject("id", UUID.class);
                        List<Anchor> anchors = loadAnchorsForRegion(conn, regionId);
                        return readRegion(rs, anchors);
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
                SELECT r.id, r.owner_id, r.name, r.world_id, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm.member_id) FILTER (WHERE rm.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                LEFT JOIN region_members rm ON r.id = rm.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.owner_id = ? AND r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                ORDER BY r.created_at
                """;

            // Load anchors for this owner's regions
            String anchorSql = """
                SELECT a.id, a.region_id, a.x, a.y, a.z, a.created_at
                FROM region_anchors a
                INNER JOIN protection_regions r ON a.region_id = r.id
                WHERE r.owner_id = ? AND r.deleted_at IS NULL
                ORDER BY a.created_at
                """;

            Map<UUID, List<Anchor>> anchorsByRegion = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(anchorSql)) {
                stmt.setObject(1, ownerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Anchor anchor = readAnchor(rs);
                        anchorsByRegion.computeIfAbsent(anchor.regionId(), k -> new ArrayList<>())
                                .add(anchor);
                    }
                }
            }

            List<Region> regions = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, ownerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID regionId = rs.getObject("id", UUID.class);
                        List<Anchor> anchors = anchorsByRegion.getOrDefault(regionId, List.of());
                        regions.add(readRegion(rs, anchors));
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
                SELECT r.id, r.owner_id, r.name, r.world_id, r.radius,
                       r.building_access, r.container_access, r.door_access,
                       pn.username as owner_name,
                       COALESCE(array_agg(rm2.member_id) FILTER (WHERE rm2.member_id IS NOT NULL), '{}') as members
                FROM protection_regions r
                INNER JOIN region_members rm ON r.id = rm.region_id AND rm.member_id = ?
                LEFT JOIN region_members rm2 ON r.id = rm2.region_id
                LEFT JOIN player_names pn ON r.owner_id = pn.player_id
                WHERE r.deleted_at IS NULL
                GROUP BY r.id, r.owner_id, r.name, r.world_id, r.radius,
                         r.building_access, r.container_access, r.door_access, pn.username
                ORDER BY r.created_at
                """;

            // Load anchors for member regions
            String anchorSql = """
                SELECT a.id, a.region_id, a.x, a.y, a.z, a.created_at
                FROM region_anchors a
                INNER JOIN protection_regions r ON a.region_id = r.id
                INNER JOIN region_members rm ON r.id = rm.region_id AND rm.member_id = ?
                WHERE r.deleted_at IS NULL
                ORDER BY a.created_at
                """;

            Map<UUID, List<Anchor>> anchorsByRegion = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(anchorSql)) {
                stmt.setObject(1, memberId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Anchor anchor = readAnchor(rs);
                        anchorsByRegion.computeIfAbsent(anchor.regionId(), k -> new ArrayList<>())
                                .add(anchor);
                    }
                }
            }

            List<Region> regions = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, memberId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID regionId = rs.getObject("id", UUID.class);
                        List<Anchor> anchors = anchorsByRegion.getOrDefault(regionId, List.of());
                        regions.add(readRegion(rs, anchors));
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
     * Create a new region with its first anchor.
     *
     * @param region the region to create
     * @param firstAnchor the initial anchor (lodestone location)
     */
    public Completable createRegion(Region region, Anchor firstAnchor) {
        String normalizedName = normalizeName(region.name());
        return storage.execute(conn -> {
            // Insert region
            String regionSql = """
                INSERT INTO protection_regions
                    (id, owner_id, name, world_id, radius,
                     building_access, container_access, door_access)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(regionSql)) {
                stmt.setObject(1, region.id());
                stmt.setObject(2, region.ownerId());
                stmt.setString(3, normalizedName);
                stmt.setObject(4, region.worldId());
                stmt.setInt(5, region.radius());
                stmt.setString(6, region.buildingAccess().name());
                stmt.setString(7, region.containerAccess().name());
                stmt.setString(8, region.doorAccess().name());
                stmt.executeUpdate();
            }

            // Insert first anchor
            String anchorSql = """
                INSERT INTO region_anchors (id, region_id, x, y, z)
                VALUES (?, ?, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(anchorSql)) {
                stmt.setObject(1, firstAnchor.id());
                stmt.setObject(2, region.id());
                stmt.setInt(3, firstAnchor.x());
                stmt.setInt(4, firstAnchor.y());
                stmt.setInt(5, firstAnchor.z());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Soft-delete a region by setting deleted_at.
     * Anchors are cascade-deleted by foreign key.
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
     * Update the name of a region.
     */
    public Completable updateName(UUID regionId, String name) {
        return storage.execute(conn -> {
            String sql = "UPDATE protection_regions SET name = ? WHERE id = ? AND deleted_at IS NULL";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, normalizeName(name));
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

    // ========== Anchor Operations ==========

    /**
     * Add an anchor to a region.
     */
    public Completable addAnchor(Anchor anchor) {
        return storage.execute(conn -> {
            String sql = "INSERT INTO region_anchors (id, region_id, x, y, z) VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, anchor.id());
                stmt.setObject(2, anchor.regionId());
                stmt.setInt(3, anchor.x());
                stmt.setInt(4, anchor.y());
                stmt.setInt(5, anchor.z());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Remove an anchor from a region.
     *
     * @return true if the anchor was removed
     */
    public Single<Boolean> removeAnchor(UUID anchorId) {
        return storage.query(conn -> {
            String sql = "DELETE FROM region_anchors WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, anchorId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Get all anchors for a region.
     */
    public Flowable<Anchor> getAnchors(UUID regionId) {
        return storage.queryFlowable(conn -> loadAnchorsForRegion(conn, regionId));
    }

    /**
     * Count anchors for a region.
     */
    public Single<Integer> countAnchors(UUID regionId) {
        return storage.query(conn -> {
            String sql = "SELECT COUNT(*) FROM region_anchors WHERE region_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, regionId);

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    // ========== Helper Methods ==========

    private List<Anchor> loadAnchorsForRegion(java.sql.Connection conn, UUID regionId) throws SQLException {
        String sql = "SELECT id, region_id, x, y, z, created_at FROM region_anchors WHERE region_id = ? ORDER BY created_at";

        List<Anchor> anchors = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, regionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    anchors.add(readAnchor(rs));
                }
            }
        }
        return anchors;
    }

    private Anchor readAnchor(ResultSet rs) throws SQLException {
        return new Anchor(
                rs.getObject("id", UUID.class),
                rs.getObject("region_id", UUID.class),
                rs.getInt("x"),
                rs.getInt("y"),
                rs.getInt("z"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private Region readRegion(ResultSet rs, List<Anchor> anchors) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID ownerId = rs.getObject("owner_id", UUID.class);
        String ownerName = rs.getString("owner_name");
        String name = rs.getString("name");
        UUID worldId = rs.getObject("world_id", UUID.class);
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

        return new Region(id, ownerId, ownerName, name, worldId,
                radius, buildingAccess, containerAccess, doorAccess, members, List.copyOf(anchors));
    }
}
