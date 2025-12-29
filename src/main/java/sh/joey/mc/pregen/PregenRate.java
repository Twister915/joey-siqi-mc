package sh.joey.mc.pregen;

/**
 * Pre-generation speed presets.
 * Controls how many chunks are generated per tick when the server is empty.
 */
public enum PregenRate {
    SLOW(1, 2),       // 1-2 chunks per tick - safe for weak hardware
    FAST(5, 10),      // 5-10 chunks per tick - balanced
    FASTEST(20, 30);  // 20-30 chunks per tick - aggressive

    private final int minChunksPerTick;
    private final int maxChunksPerTick;

    PregenRate(int min, int max) {
        this.minChunksPerTick = min;
        this.maxChunksPerTick = max;
    }

    public int getMinChunksPerTick() {
        return minChunksPerTick;
    }

    public int getMaxChunksPerTick() {
        return maxChunksPerTick;
    }
}
