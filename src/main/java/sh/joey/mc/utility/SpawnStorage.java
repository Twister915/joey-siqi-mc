package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.storage.StorageService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage for world spawn points.
 */
public final class SpawnStorage {

    private final StorageService storage;
    private final PublishSubject<UUID> changeSubject = PublishSubject.create();

    public SpawnStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Observable that emits the world UUID whenever a spawn point is added or updated.
     */
    public Observable<UUID> onChanged() {
        return changeSubject.hide();
    }

    public record SpawnPoint(UUID worldId, double x, double y, double z, float yaw, float pitch) {
        public @Nullable Location toLocation() {
            World world = Bukkit.getWorld(worldId);
            if (world == null) return null;
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public Maybe<SpawnPoint> getSpawn(UUID worldId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT world_id, x, y, z, yaw, pitch FROM world_spawns WHERE world_id = ?")) {
                stmt.setObject(1, worldId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return new SpawnPoint(
                            UUID.fromString(rs.getString("world_id")),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    );
                }
                return null;
            }
        });
    }

    public Flowable<SpawnPoint> getAllSpawns() {
        return storage.queryFlowable(conn -> {
            List<SpawnPoint> spawns = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT world_id, x, y, z, yaw, pitch FROM world_spawns")) {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    spawns.add(new SpawnPoint(
                            UUID.fromString(rs.getString("world_id")),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch")
                    ));
                }
            }
            return spawns;
        });
    }

    public Completable setSpawn(UUID worldId, Location location, @Nullable UUID setBy) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO world_spawns (world_id, x, y, z, yaw, pitch, set_by, set_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                    ON CONFLICT (world_id) DO UPDATE SET
                        x = EXCLUDED.x,
                        y = EXCLUDED.y,
                        z = EXCLUDED.z,
                        yaw = EXCLUDED.yaw,
                        pitch = EXCLUDED.pitch,
                        set_by = EXCLUDED.set_by,
                        set_at = NOW()
                    """)) {
                stmt.setObject(1, worldId);
                stmt.setDouble(2, location.getX());
                stmt.setDouble(3, location.getY());
                stmt.setDouble(4, location.getZ());
                stmt.setFloat(5, location.getYaw());
                stmt.setFloat(6, location.getPitch());
                stmt.setObject(7, setBy);
                stmt.executeUpdate();
            }
        }).doOnComplete(() -> changeSubject.onNext(worldId));
    }
}
