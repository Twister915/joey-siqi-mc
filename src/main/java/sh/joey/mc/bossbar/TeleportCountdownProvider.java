package sh.joey.mc.bossbar;

import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.Optional;

/**
 * Boss bar provider that shows teleport countdown when a player is teleporting.
 * High priority to ensure it shows over other providers during teleport.
 */
public final class TeleportCountdownProvider implements BossBarProvider {

    private static final int PRIORITY = 200; // High priority during teleport

    private final SafeTeleporter teleporter;

    public TeleportCountdownProvider(SafeTeleporter teleporter) {
        this.teleporter = teleporter;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public Optional<BossBarState> getState(Player player) {
        var playerId = player.getUniqueId();

        // Check for active teleport first
        SafeTeleporter.TeleportState teleportState = teleporter.getTeleportState(playerId);
        if (teleportState != null) {
            float progress = teleportState.getProgress();
            int remaining = teleportState.getRemainingSeconds();

            // Don't show if already at 0 (about to teleport)
            if (remaining <= 0) {
                return Optional.empty();
            }

            String title = ChatColor.translateAlternateColorCodes('&',
                    "&b&lTeleporting &7in &f&l" + remaining + "&7 second" + (remaining != 1 ? "s" : "") + "&7... &8(don't move!)");

            return Optional.of(new BossBarState(title, BarColor.BLUE, progress, BarStyle.SOLID));
        }

        // Check for recently cancelled teleport
        SafeTeleporter.CancelledState cancelledState = teleporter.getCancelledState(playerId);
        if (cancelledState != null) {
            float progress = cancelledState.getProgress();

            String title = ChatColor.translateAlternateColorCodes('&',
                    "&c&lTeleport Cancelled &8- &7You moved!");

            return Optional.of(new BossBarState(title, BarColor.RED, progress, BarStyle.SOLID));
        }

        return Optional.empty();
    }
}
