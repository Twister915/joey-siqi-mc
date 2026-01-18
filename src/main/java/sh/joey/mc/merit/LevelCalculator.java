package sh.joey.mc.merit;

/**
 * Calculates levels from merit using the formula: merit_for_level(n) = base * n^exponent
 */
public final class LevelCalculator {

    private final int baseXp;
    private final double exponent;

    public LevelCalculator(MeritConfig config) {
        this.baseXp = config.levelBaseXp();
        this.exponent = config.levelExponent();
    }

    /**
     * Calculate the total merit required to reach a given level.
     */
    public long meritForLevel(int level) {
        if (level <= 1) return 0;
        return (long) (baseXp * Math.pow(level, exponent));
    }

    /**
     * Calculate the merit required for just the specified level (not cumulative).
     */
    public long meritForLevelOnly(int level) {
        if (level <= 1) return baseXp;
        return meritForLevel(level) - meritForLevel(level - 1);
    }

    /**
     * Calculate the level for a given total merit amount.
     */
    public int levelForMerit(long totalMerit) {
        if (totalMerit <= 0) return 1;

        // Binary search for the level
        int low = 1;
        int high = 10000; // Reasonable upper bound

        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (meritForLevel(mid) <= totalMerit) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }

        return low;
    }

    /**
     * Calculate progress towards the next level (0.0 to 1.0).
     */
    public double progressToNextLevel(long totalMerit) {
        int currentLevel = levelForMerit(totalMerit);
        long currentLevelMerit = meritForLevel(currentLevel);
        long nextLevelMerit = meritForLevel(currentLevel + 1);

        long meritIntoLevel = totalMerit - currentLevelMerit;
        long meritNeeded = nextLevelMerit - currentLevelMerit;

        if (meritNeeded <= 0) return 1.0;
        return (double) meritIntoLevel / meritNeeded;
    }

    /**
     * Calculate merit needed to reach the next level.
     */
    public long meritToNextLevel(long totalMerit) {
        int currentLevel = levelForMerit(totalMerit);
        long nextLevelMerit = meritForLevel(currentLevel + 1);
        return nextLevelMerit - totalMerit;
    }
}
