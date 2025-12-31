package sh.joey.mc.pet;

import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.pet.hamster.HamsterPet;

/**
 * Enum of available pet types.
 */
public enum PetType {
    HAMSTER("hamster", "Hamster", "smp.pet.hamster");

    private final String id;
    private final String displayName;
    private final String permission;

    PetType(String id, String displayName, String permission) {
        this.id = id;
        this.displayName = displayName;
        this.permission = permission;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String permission() {
        return permission;
    }

    /**
     * Creates a new pet instance of this type for the given owner.
     */
    public Pet createPet(SiqiJoeyPlugin plugin, Player owner, PetManager manager) {
        return switch (this) {
            case HAMSTER -> new HamsterPet(plugin, owner, manager);
        };
    }

    /**
     * Finds a pet type by its ID.
     */
    public static PetType fromId(String id) {
        for (PetType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
