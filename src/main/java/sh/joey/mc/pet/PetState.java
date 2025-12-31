package sh.joey.mc.pet;

/**
 * The behavioral state of a pet.
 */
public enum PetState {
    FOLLOWING("following"),
    SITTING("sitting");

    private final String id;

    PetState(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static PetState fromId(String id) {
        for (PetState state : values()) {
            if (state.id.equalsIgnoreCase(id)) {
                return state;
            }
        }
        return null;
    }
}
