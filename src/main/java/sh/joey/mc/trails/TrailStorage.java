package sh.joey.mc.trails;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;
import sh.joey.mc.trails.elytra.CustomColorEffect;
import sh.joey.mc.trails.elytra.ElytraTrailEffect;
import sh.joey.mc.trails.elytra.RainbowEffect;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage for player trail preferences.
 */
public final class TrailStorage {

    private final StorageService storage;

    public TrailStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Gets a player's trail setting for a specific trail type.
     */
    public Maybe<TrailSetting> getTrailSetting(UUID playerId, TrailType trailType) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT effect, intensity FROM player_trails WHERE player_id = ? AND trail_type = ?")) {
                stmt.setObject(1, playerId);
                stmt.setString(2, trailType.id());
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return readSetting(rs, trailType);
                }
                return null;
            }
        });
    }

    /**
     * Gets all trail settings for a player.
     */
    public Flowable<TrailSettingRow> getAllTrailSettings(UUID playerId) {
        return storage.queryFlowable(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT trail_type, effect, intensity FROM player_trails WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                List<TrailSettingRow> settings = new ArrayList<>();
                while (rs.next()) {
                    String typeId = rs.getString("trail_type");
                    TrailType type = TrailType.fromId(typeId);
                    if (type != null) {
                        TrailSetting setting = readSetting(rs, type);
                        if (setting != null) {
                            settings.add(new TrailSettingRow(type, setting));
                        }
                    }
                }
                return settings;
            }
        });
    }

    /**
     * Sets a player's trail effect for a specific trail type.
     */
    public Completable setTrailEffect(UUID playerId, TrailType trailType, TrailEffect effect) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO player_trails (player_id, trail_type, effect, intensity, created_at, updated_at)
                    VALUES (?, ?, ?, 'medium', NOW(), NOW())
                    ON CONFLICT (player_id, trail_type) DO UPDATE SET
                        effect = EXCLUDED.effect,
                        updated_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, trailType.id());
                stmt.setString(3, effect.id());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Sets a player's trail effect and intensity for a specific trail type.
     */
    public Completable setTrailSetting(UUID playerId, TrailType trailType, TrailEffect effect, TrailIntensity intensity) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO player_trails (player_id, trail_type, effect, intensity, created_at, updated_at)
                    VALUES (?, ?, ?, ?, NOW(), NOW())
                    ON CONFLICT (player_id, trail_type) DO UPDATE SET
                        effect = EXCLUDED.effect,
                        intensity = EXCLUDED.intensity,
                        updated_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, trailType.id());
                stmt.setString(3, effect.id());
                stmt.setString(4, intensity.id());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Updates only the intensity for a trail type.
     */
    public Completable setTrailIntensity(UUID playerId, TrailType trailType, TrailIntensity intensity) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    UPDATE player_trails
                    SET intensity = ?, updated_at = NOW()
                    WHERE player_id = ? AND trail_type = ?
                    """)) {
                stmt.setString(1, intensity.id());
                stmt.setObject(2, playerId);
                stmt.setString(3, trailType.id());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Clears a player's trail setting for a specific trail type.
     */
    public Completable clearTrailSetting(UUID playerId, TrailType trailType) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement(
                    "DELETE FROM player_trails WHERE player_id = ? AND trail_type = ?")) {
                stmt.setObject(1, playerId);
                stmt.setString(2, trailType.id());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Clears all trail settings for a player.
     */
    public Completable clearAllTrailSettings(UUID playerId) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement(
                    "DELETE FROM player_trails WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }
        });
    }

    private TrailSetting readSetting(ResultSet rs, TrailType type) throws SQLException {
        String effectId = rs.getString("effect");
        String intensityId = rs.getString("intensity");

        TrailEffect effect = parseEffect(effectId, type);
        if (effect == null) {
            return null;
        }

        TrailIntensity intensity = TrailIntensity.fromId(intensityId);
        if (intensity == null) {
            intensity = TrailIntensity.defaultIntensity();
        }

        return new TrailSetting(effect, intensity);
    }

    private TrailEffect parseEffect(String effectId, TrailType type) {
        if (effectId == null) {
            return null;
        }

        // Check for rainbow
        if (effectId.equalsIgnoreCase("rainbow")) {
            return RainbowEffect.INSTANCE;
        }

        // Check for custom color
        if (CustomColorEffect.isCustomColor(effectId)) {
            return CustomColorEffect.fromId(effectId);
        }

        // Check for built-in effects based on trail type
        if (type == TrailType.ELYTRA) {
            return ElytraTrailEffect.fromId(effectId);
        }

        return null;
    }

    /**
     * Record combining trail type with its setting.
     */
    public record TrailSettingRow(TrailType type, TrailSetting setting) {}
}
