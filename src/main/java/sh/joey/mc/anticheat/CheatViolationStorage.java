package sh.joey.mc.anticheat;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import sh.joey.mc.Json;
import sh.joey.mc.storage.StorageService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CheatViolationStorage {

    private final StorageService storage;

    public CheatViolationStorage(StorageService storage) {
        this.storage = storage;
    }

    public Completable recordViolation(
            UUID playerId,
            UUID serverSessionId,
            String checkName,
            double weight,
            double violationLevel,
            ViolationLocation location,
            Map<String, Object> violationData,
            String source
    ) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO cheat_violations
                    (player_id, server_session_id, check_name, violation_weight, violation_level,
                     player_location, violation_data, source)
                    VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setObject(2, serverSessionId);
                stmt.setString(3, checkName);
                stmt.setDouble(4, weight);
                stmt.setDouble(5, violationLevel);
                stmt.setString(6, locationToJson(location));
                stmt.setString(7, violationData != null ? Json.GSON.toJson(violationData) : null);
                stmt.setString(8, source);
                stmt.executeUpdate();
            }
        });
    }

    public Flowable<ViolationEntry> getRecentViolations(int limit) {
        return storage.queryFlowable(conn -> {
            List<ViolationEntry> entries = new ArrayList<>();
            try (var stmt = conn.prepareStatement("""
                    SELECT id, player_id, check_name, violation_level, detected_at,
                           player_location, violation_data, reviewed_at, source
                    FROM cheat_violations
                    ORDER BY detected_at DESC
                    LIMIT ?
                    """)) {
                stmt.setInt(1, limit);
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    entries.add(new ViolationEntry(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("player_id")),
                            rs.getString("check_name"),
                            rs.getDouble("violation_level"),
                            rs.getTimestamp("detected_at").toInstant(),
                            rs.getString("player_location"),
                            rs.getString("violation_data"),
                            rs.getTimestamp("reviewed_at") != null,
                            rs.getString("source")
                    ));
                }
            }
            return entries;
        });
    }

    public Flowable<ViolationEntry> getPlayerViolations(UUID playerId, int limit) {
        return storage.queryFlowable(conn -> {
            List<ViolationEntry> entries = new ArrayList<>();
            try (var stmt = conn.prepareStatement("""
                    SELECT id, player_id, check_name, violation_level, detected_at,
                           player_location, violation_data, reviewed_at, source
                    FROM cheat_violations
                    WHERE player_id = ?
                    ORDER BY detected_at DESC
                    LIMIT ?
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, limit);
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    entries.add(new ViolationEntry(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("player_id")),
                            rs.getString("check_name"),
                            rs.getDouble("violation_level"),
                            rs.getTimestamp("detected_at").toInstant(),
                            rs.getString("player_location"),
                            rs.getString("violation_data"),
                            rs.getTimestamp("reviewed_at") != null,
                            rs.getString("source")
                    ));
                }
            }
            return entries;
        });
    }

    public Completable markReviewed(UUID violationId, UUID reviewerId, String verdict, String notes) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    UPDATE cheat_violations
                    SET reviewed_at = NOW(), reviewed_by_player_id = ?, verdict = ?, notes = ?
                    WHERE id = ?
                    """)) {
                stmt.setObject(1, reviewerId);
                stmt.setString(2, verdict);
                stmt.setString(3, notes);
                stmt.setObject(4, violationId);
                stmt.executeUpdate();
            }
        });
    }

    public Completable deleteOldViolations(int retentionDays) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement(
                    "DELETE FROM cheat_violations WHERE detected_at < NOW() - INTERVAL '" + retentionDays + " days'")) {
                stmt.executeUpdate();
            }
        });
    }

    private String locationToJson(ViolationLocation loc) {
        return Json.GSON.toJson(Map.of(
                "world", loc.world(),
                "x", loc.x(),
                "y", loc.y(),
                "z", loc.z(),
                "yaw", loc.yaw(),
                "pitch", loc.pitch()
        ));
    }

    public record ViolationEntry(
            UUID id,
            UUID playerId,
            String checkName,
            double violationLevel,
            Instant detectedAt,
            String locationJson,
            String violationDataJson,
            boolean reviewed,
            String source
    ) {}
}
