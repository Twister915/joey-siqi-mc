package sh.joey.mc.pet;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;

import java.util.UUID;

/**
 * Storage for player pet data.
 */
public final class PetStorage {

    private final StorageService storage;

    public PetStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Gets a player's active pet if they have one.
     */
    public Maybe<ActivePet> getActivePet(UUID playerId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT pet_type, state FROM player_pets WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    PetType type = PetType.fromId(rs.getString("pet_type"));
                    PetState state = PetState.fromId(rs.getString("state"));
                    if (type != null && state != null) {
                        return new ActivePet(playerId, type, state);
                    }
                }
                return null;
            }
        });
    }

    /**
     * Saves a player's active pet.
     */
    public Completable savePet(UUID playerId, PetType type, PetState state) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO player_pets (player_id, pet_type, state, updated_at)
                    VALUES (?, ?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET
                        pet_type = EXCLUDED.pet_type,
                        state = EXCLUDED.state,
                        updated_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, type.id());
                stmt.setString(3, state.id());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Updates just the state of a player's pet.
     */
    public Completable updateState(UUID playerId, PetState state) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    UPDATE player_pets SET state = ?, updated_at = NOW() WHERE player_id = ?
                    """)) {
                stmt.setString(1, state.id());
                stmt.setObject(2, playerId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Removes a player's pet.
     */
    public Completable removePet(UUID playerId) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement(
                    "DELETE FROM player_pets WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }
        });
    }
}
