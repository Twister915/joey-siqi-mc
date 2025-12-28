package sh.joey.mc.trails;

/**
 * Intensity levels for trail particles.
 * Controls both particle count and spawn rate.
 */
public enum TrailIntensity {
    LOW("low", 2, 5),
    MEDIUM("medium", 4, 3),
    HIGH("high", 6, 2);

    private final String id;
    private final int particleCount;
    private final int tickInterval;

    TrailIntensity(String id, int particleCount, int tickInterval) {
        this.id = id;
        this.particleCount = particleCount;
        this.tickInterval = tickInterval;
    }

    public String id() {
        return id;
    }

    public int particleCount() {
        return particleCount;
    }

    public int tickInterval() {
        return tickInterval;
    }

    public static TrailIntensity fromId(String id) {
        for (TrailIntensity intensity : values()) {
            if (intensity.id.equalsIgnoreCase(id)) {
                return intensity;
            }
        }
        return null;
    }

    public static TrailIntensity defaultIntensity() {
        return MEDIUM;
    }
}
