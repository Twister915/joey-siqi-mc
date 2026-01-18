package sh.joey.mc.anticheat;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

public record Detection(
        UUID playerId,
        String checkName,
        double weight,
        Location location,
        Map<String, Object> data,
        String source
) {
    public static final String SOURCE_CUSTOM = "custom";
    public static final String SOURCE_GRIM = "grim";

    public Detection(UUID playerId, String checkName, double weight, Location location, Map<String, Object> data) {
        this(playerId, checkName, weight, location, data, SOURCE_CUSTOM);
    }

    public Detection(UUID playerId, String checkName, double weight, Location location) {
        this(playerId, checkName, weight, location, Map.of(), SOURCE_CUSTOM);
    }
}
