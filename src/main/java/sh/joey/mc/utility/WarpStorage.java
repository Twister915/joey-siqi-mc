package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.storage.StorageService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage for warps.
 */
public final class WarpStorage {

    private final StorageService storage;
    private final PublishSubject<Void> changeSubject = PublishSubject.create();

    public WarpStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Observable that emits whenever warps are added, updated, or deleted.
     */
    public Observable<Void> onChanged() {
        return changeSubject.hide();
    }

    public record Warp(String name, UUID worldId, double x, double y, double z, float yaw, float pitch, @Nullable UUID createdBy) {
        public @Nullable Location toLocation() {
            World world = Bukkit.getWorld(worldId);
            if (world == null) return null;
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public Maybe<Warp> getWarp(String name) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT name, world_id, x, y, z, yaw, pitch, created_by FROM warps WHERE name = ?")) {
                stmt.setString(1, name.toLowerCase());
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return parseWarp(rs);
                }
                return null;
            }
        });
    }

    public Flowable<Warp> getAllWarps() {
        return storage.queryFlowable(conn -> {
            List<Warp> warps = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT name, world_id, x, y, z, yaw, pitch, created_by FROM warps ORDER BY name")) {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    warps.add(parseWarp(rs));
                }
            }
            return warps;
        });
    }

    public Completable setWarp(String name, Location location, @Nullable UUID createdBy) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO warps (name, world_id, x, y, z, yaw, pitch, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (name) DO UPDATE SET
                        world_id = EXCLUDED.world_id,
                        x = EXCLUDED.x,
                        y = EXCLUDED.y,
                        z = EXCLUDED.z,
                        yaw = EXCLUDED.yaw,
                        pitch = EXCLUDED.pitch,
                        created_by = EXCLUDED.created_by
                    """)) {
                stmt.setString(1, name.toLowerCase());
                stmt.setObject(2, location.getWorld().getUID());
                stmt.setDouble(3, location.getX());
                stmt.setDouble(4, location.getY());
                stmt.setDouble(5, location.getZ());
                stmt.setFloat(6, location.getYaw());
                stmt.setFloat(7, location.getPitch());
                stmt.setObject(8, createdBy);
                stmt.executeUpdate();
            }
        }).doOnComplete(() -> changeSubject.onNext(null));
    }

    public Single<Boolean> deleteWarp(String name) {
        return storage.<Boolean>query(conn -> {
            try (var stmt = conn.prepareStatement("DELETE FROM warps WHERE name = ?")) {
                stmt.setString(1, name.toLowerCase());
                return stmt.executeUpdate() > 0;
            }
        }).doOnSuccess(deleted -> {
            if (deleted) changeSubject.onNext(null);
        });
    }

    private Warp parseWarp(ResultSet rs) throws SQLException {
        return new Warp(
                rs.getString("name"),
                UUID.fromString(rs.getString("world_id")),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getString("created_by") != null ? UUID.fromString(rs.getString("created_by")) : null
        );
    }
}
