package sh.joey.mc.settings;

/**
 * Immutable record containing a player's settings.
 */
public record PlayerSettings(
        boolean keepInventory,
        DisplayTimeSetting displayTime,
        boolean easyMode,
        boolean passiveMode
) {
    /**
     * Default settings for new players.
     * - keepInventory: false (vanilla behavior)
     * - displayTime: ALWAYS (current behavior)
     * - easyMode: false (vanilla difficulty)
     * - passiveMode: false (PvP enabled)
     */
    public static final PlayerSettings DEFAULTS = new PlayerSettings(
            false,
            DisplayTimeSetting.ALWAYS,
            false,
            false
    );

    /**
     * Create a copy with keepInventory changed.
     */
    public PlayerSettings withKeepInventory(boolean keepInventory) {
        return new PlayerSettings(keepInventory, displayTime, easyMode, passiveMode);
    }

    /**
     * Create a copy with displayTime changed.
     */
    public PlayerSettings withDisplayTime(DisplayTimeSetting displayTime) {
        return new PlayerSettings(keepInventory, displayTime, easyMode, passiveMode);
    }

    /**
     * Create a copy with easyMode changed.
     */
    public PlayerSettings withEasyMode(boolean easyMode) {
        return new PlayerSettings(keepInventory, displayTime, easyMode, passiveMode);
    }

    /**
     * Create a copy with passiveMode changed.
     */
    public PlayerSettings withPassiveMode(boolean passiveMode) {
        return new PlayerSettings(keepInventory, displayTime, easyMode, passiveMode);
    }
}
