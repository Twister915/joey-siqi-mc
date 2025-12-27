package sh.joey.mc.bossbar;

import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import sh.joey.mc.sleep.MajoritySleepManager;

import java.util.Optional;

/**
 * Boss bar provider that shows the sleep countdown when enough players are sleeping.
 */
public final class SleepCountdownProvider implements BossBarProvider {

    private static final int PRIORITY = 180; // Below teleport (200), above biome/weather (150)

    private final MajoritySleepManager sleepManager;

    public SleepCountdownProvider(MajoritySleepManager sleepManager) {
        this.sleepManager = sleepManager;
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public Optional<BossBarState> getState(Player player) {
        var state = sleepManager.getCountdownState(player);
        if (state == null) {
            return Optional.empty();
        }

        String title = ChatColor.translateAlternateColorCodes('&',
                "&e&l☾ Skipping Night &7in &f&l" + state.remainingSeconds() +
                "&7... &8(" + state.sleepingCount() + "/" + state.totalCount() + " sleeping)");

        return Optional.of(new BossBarState(title, BarColor.YELLOW, state.progress(), BarStyle.SOLID));
    }
}
