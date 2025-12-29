package sh.joey.mc.pregen;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Generates chunk coordinates in a square spiral pattern outward from center.
 * Used for pre-generating chunks starting from world spawn.
 *
 * <p>Pattern for a 5x5 grid centered at (0,0):
 * <pre>
 * 20 21 22 23 24
 * 19  6  7  8  9
 * 18  5  0  1 10
 * 17  4  3  2 11
 * 16 15 14 13 12
 * </pre>
 *
 * <p>The spiral starts at the center (index 0), moves right, then down,
 * then left, then up, expanding outward with each ring.
 */
public final class SpiralIterator implements Iterator<int[]> {

    private final int centerX;
    private final int centerZ;
    private final int sideChunks;
    private final long totalChunks;

    // Current position relative to center
    private int x = 0;
    private int z = 0;

    // Current direction: 0=right, 1=down, 2=left, 3=up
    private int direction = 0;

    // Segment tracking
    private int segmentLength = 1;
    private int segmentProgress = 0;
    private int segmentsInCurrentRing = 0;

    // Overall progress
    private long index = 0;
    private boolean started = false;

    /**
     * Create a spiral iterator centered on a specific chunk.
     *
     * @param centerChunkX center X coordinate (chunk coordinates)
     * @param centerChunkZ center Z coordinate (chunk coordinates)
     * @param sideChunks   side length in chunks (e.g., 1562 for 25km)
     */
    public SpiralIterator(int centerChunkX, int centerChunkZ, int sideChunks) {
        this.centerX = centerChunkX;
        this.centerZ = centerChunkZ;
        this.sideChunks = sideChunks;
        this.totalChunks = (long) sideChunks * sideChunks;
    }

    @Override
    public boolean hasNext() {
        return index < totalChunks;
    }

    @Override
    public int[] next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        // First call returns center
        if (!started) {
            started = true;
            index++;
            return new int[]{centerX + x, centerZ + z};
        }

        // Move to next position in spiral
        advance();
        index++;
        return new int[]{centerX + x, centerZ + z};
    }

    private void advance() {
        // Direction vectors: right, down, left, up
        int[] dx = {1, 0, -1, 0};
        int[] dz = {0, 1, 0, -1};

        // Move one step in current direction
        x += dx[direction];
        z += dz[direction];
        segmentProgress++;

        // Check if we've completed this segment
        if (segmentProgress >= segmentLength) {
            segmentProgress = 0;
            direction = (direction + 1) % 4;  // Turn clockwise
            segmentsInCurrentRing++;

            // After every 2 segments (one horizontal + one vertical), increase length
            if (segmentsInCurrentRing >= 2) {
                segmentsInCurrentRing = 0;
                segmentLength++;
            }
        }
    }

    /**
     * Current index (number of chunks processed).
     */
    public long getIndex() {
        return index;
    }

    /**
     * Total chunks in the area.
     */
    public long getTotalChunks() {
        return totalChunks;
    }

    /**
     * Progress as a fraction (0.0 to 1.0).
     */
    public double getProgress() {
        return totalChunks > 0 ? (double) index / totalChunks : 1.0;
    }

    /**
     * Get the half-side (radius from center to edge).
     */
    public int getHalfSide() {
        return sideChunks / 2;
    }
}
