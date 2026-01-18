package sh.joey.mc.anticheat;

import org.bukkit.Location;

public record ViolationLocation(
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public static ViolationLocation from(Location location) {
        return new ViolationLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }
}
