package sh.joey.mc.pet;

import java.util.UUID;

/**
 * Record representing a player's active pet for database persistence.
 */
public record ActivePet(
        UUID playerId,
        PetType type,
        PetState state
) {
}
