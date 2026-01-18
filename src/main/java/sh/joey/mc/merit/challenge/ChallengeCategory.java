package sh.joey.mc.merit.challenge;

/**
 * Categories of challenges.
 */
public enum ChallengeCategory {
    MINING("Mining"),
    FARMING("Farming"),
    BUILDING("Building"),
    PVP("PvP"),
    PVE("PvE"),
    PROGRESSION("Progression"),
    CRAFTING("Crafting"),
    SMELTING("Smelting"),
    EXPLORATION("Exploration"),
    TIME("Time");

    private final String displayName;

    ChallengeCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
