package sh.joey.mc.rtp;

import org.bukkit.Location;
import org.bukkit.block.Biome;

/**
 * Represents a candidate location for random teleportation.
 */
public record RtpCandidate(
        int index,
        Location location,
        Biome biome,
        String biomeName,
        int distanceFromSpawn,
        String direction,
        String hint
) {}
