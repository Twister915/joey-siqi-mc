package sh.joey.mc.merit;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import sh.joey.mc.bossbar.BossBarProvider;
import sh.joey.mc.bossbar.BossBarState;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boss bar provider for merit notifications.
 * Shows challenge progress milestones and level-up notifications.
 */
public final class MeritBossBarProvider implements BossBarProvider {

    private static final int PRIORITY = 180; // Below teleport (200), above biome (150)
    private static final long PROGRESS_DURATION_MS = 3000;
    private static final long COMPLETE_DURATION_MS = 4000;
    private static final long LEVEL_UP_DURATION_MS = 5000;

    private final Map<UUID, MeritNotification> notifications = new ConcurrentHashMap<>();

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public Optional<BossBarState> getState(Player player) {
        MeritNotification notif = notifications.get(player.getUniqueId());
        if (notif == null || notif.isExpired()) {
            notifications.remove(player.getUniqueId());
            return Optional.empty();
        }
        return Optional.of(new BossBarState(notif.title(), notif.color(), notif.progress(), BarStyle.SOLID));
    }

    /**
     * Show challenge progress notification.
     */
    public void showProgress(UUID playerId, String challengeName, int percent) {
        String title;
        BarColor color;
        long duration;

        if (percent == 100) {
            title = "\u2605 " + challengeName + " Complete!"; // star
            color = BarColor.GREEN;
            duration = COMPLETE_DURATION_MS;
        } else if (percent == 50) {
            title = "\u2726 " + challengeName + ": Halfway there!"; // four-pointed star
            color = BarColor.YELLOW;
            duration = PROGRESS_DURATION_MS;
        } else {
            title = "\u2726 " + challengeName + ": " + percent + "% complete!"; // four-pointed star
            color = BarColor.YELLOW;
            duration = PROGRESS_DURATION_MS;
        }

        long expiresAt = System.currentTimeMillis() + duration;
        notifications.put(playerId, new MeritNotification(title, color, 1.0f, expiresAt));
    }

    /**
     * Show level-up notification.
     */
    public void showLevelUp(UUID playerId, int newLevel) {
        String title = "\u2b06 Level Up! You are now Level " + newLevel + "!"; // upward arrow
        long expiresAt = System.currentTimeMillis() + LEVEL_UP_DURATION_MS;
        notifications.put(playerId, new MeritNotification(title, BarColor.PINK, 1.0f, expiresAt));
    }

    /**
     * Show merit earned notification.
     */
    public void showMeritEarned(UUID playerId, int amount, String reason) {
        String title = "+" + amount + " Merit - " + reason;
        long expiresAt = System.currentTimeMillis() + PROGRESS_DURATION_MS;
        notifications.put(playerId, new MeritNotification(title, BarColor.PURPLE, 1.0f, expiresAt));
    }

    /**
     * Clear notification for a player.
     */
    public void clear(UUID playerId) {
        notifications.remove(playerId);
    }
}
