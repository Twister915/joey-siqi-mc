package sh.joey.mc.trails;

/**
 * Types of trails that can be enabled.
 * Each type has its own permission and settings.
 */
public enum TrailType {
    ELYTRA("elytra", "smp.trails.elytra");
    // Future: WALK("walk", "smp.trails.walk"),
    //         SLEEP("sleep", "smp.trails.sleep");

    private final String id;
    private final String permission;

    TrailType(String id, String permission) {
        this.id = id;
        this.permission = permission;
    }

    public String id() {
        return id;
    }

    public String permission() {
        return permission;
    }

    public static TrailType fromId(String id) {
        for (TrailType type : values()) {
            if (type.id.equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
