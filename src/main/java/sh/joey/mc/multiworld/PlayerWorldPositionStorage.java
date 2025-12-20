package sh.joey.mc.multiworld;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.bukkit.Location;
import org.bukkit.World;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Storage for player positions per world.
 * Used by /world command to teleport players back to their last location in a world.
 */
public final class PlayerWorldPositionStorage {

    private final StorageService storage;

    public PlayerWorldPositionStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Saves a player's position in a world.
     *
     * @param playerId  the player's UUID
     * @param location  the location to save
     * @return a completable that completes when saved
     */
    public Completable savePosition(UUID playerId, Location location) {
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO player_world_positions (player_id, world_id, x, y, z, yaw, pitch, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (player_id, world_id)
                DO UPDATE SET x = EXCLUDED.x, y = EXCLUDED.y, z = EXCLUDED.z,
                              yaw = EXCLUDED.yaw, pitch = EXCLUDED.pitch, updated_at = NOW()
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setObject(2, location.getWorld().getUID());
                stmt.setDouble(3, location.getX());
                stmt.setDouble(4, location.getY());
                stmt.setDouble(5, location.getZ());
                stmt.setFloat(6, location.getYaw());
                stmt.setFloat(7, location.getPitch());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Gets a player's last position in a world.
     *
     * @param playerId the player's UUID
     * @param world    the world
     * @return the location, or empty if no position saved
     */
    public Maybe<Location> getPosition(UUID playerId, World world) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT x, y, z, yaw, pitch
                FROM player_world_positions
                WHERE player_id = ? AND world_id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setObject(2, world.getUID());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new Location(
                                world,
                                rs.getDouble("x"),
                                rs.getDouble("y"),
                                rs.getDouble("z"),
                                rs.getFloat("yaw"),
                                rs.getFloat("pitch")
                        );
                    }
                    return null;
                }
            }
        });
    }
}
