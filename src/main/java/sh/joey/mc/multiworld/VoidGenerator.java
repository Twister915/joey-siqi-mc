package sh.joey.mc.multiworld;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * A chunk generator that creates empty void worlds.
 * Places a small bedrock platform at spawn for safety.
 */
public final class VoidGenerator extends ChunkGenerator {

    private static final int SPAWN_Y = 64;
    private static final int PLATFORM_SIZE = 3; // 3x3 bedrock platform

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // Only place bedrock platform in the spawn chunk (0, 0)
        if (chunkX == 0 && chunkZ == 0) {
            int halfSize = PLATFORM_SIZE / 2;
            for (int x = -halfSize; x <= halfSize; x++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    // Chunk-local coordinates (0-15)
                    int localX = x + halfSize;
                    int localZ = z + halfSize;
                    chunkData.setBlock(localX, SPAWN_Y, localZ, Material.BEDROCK);
                }
            }
        }
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // No terrain generation - leave as air
    }

    @Override
    public void generateBedrock(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // No bedrock layer - we handle spawn platform in generateSurface
    }

    @Override
    public void generateCaves(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        // No caves
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return true; // We use this for the spawn platform
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        // Spawn on top of the bedrock platform
        return new Location(world, 1.5, SPAWN_Y + 1, 1.5);
    }
}
