package sh.joey.mc.trails;

/**
 * Bundles a trail effect with its intensity setting.
 */
public record TrailSetting(TrailEffect effect, TrailIntensity intensity) {

    public TrailSetting withIntensity(TrailIntensity newIntensity) {
        return new TrailSetting(effect, newIntensity);
    }

    public TrailSetting withEffect(TrailEffect newEffect) {
        return new TrailSetting(newEffect, intensity);
    }
}
