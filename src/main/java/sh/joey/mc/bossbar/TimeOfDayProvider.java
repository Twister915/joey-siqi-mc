package sh.joey.mc.bossbar;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Boss bar provider that shows time of day information.
 * Low priority - shows when nothing else is active.
 */
public final class TimeOfDayProvider implements BossBarProvider {

    private static final int PRIORITY = 0;

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Optional<BossBarState> getState(Player player) {
        World world = player.getWorld();

        // Only show for overworld-type environments
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return Optional.empty();
        }

        WorldTime worldTime = getWorldTime(world);

        BarColor color = worldTime.localTime().isNight() ? BarColor.BLUE : BarColor.RED;

        String colorCode = worldTime.localTime().isNight() ? "&9" : "&6";
        String timeOfDay = worldTime.localTime().isNight() ? "Night" : "Day";
        String title = String.format(
                "%s&l%s %d &7:: %s&l%s",
                colorCode,
                timeOfDay,
                worldTime.days(),
                colorCode,
                worldTime.localTime());
        title = ChatColor.translateAlternateColorCodes('&', title);

        BarStyle style = worldTime.localTime().isNight()
                ? BarStyle.SEGMENTED_10
                : BarStyle.SEGMENTED_12;

        return Optional.of(new BossBarState(title, color, worldTime.localTime().progress(), style));
    }

    static WorldTime getWorldTime(World world) {
        return new WorldTime(getDays(world), getWorldClockTime(world));
    }

    record WorldTime(long days, ClockTime localTime) {
    }

    static long getDays(World world) {
        return ((world.getFullTime() + 6_000) / 24_000) + 1;
    }

    static ClockTime getWorldClockTime(World world) {
        long rawTicks = world.getTime();
        long ticks = (rawTicks + 6_000) % 24_000L;
        byte hour = (byte) (ticks / 1_000L);
        byte minute = (byte) ((3L * (ticks % 1_000L)) / 50L);
        // 0_000 = it's daytime, 13_000 = it's night-time
        long joeyTicks = (rawTicks + 1_000L) % 24_000L;
        float progress;
        if (joeyTicks < 14_000) {
            progress = (float) joeyTicks / 14_000.0f;
        } else {
            progress = (float) (joeyTicks - 14_000) / 10_000.0f;
        }
        return new ClockTime(hour, minute, progress);
    }

    record ClockTime(byte hour, byte minute, float progress) {

        @Override
        public @NotNull String toString() {
            byte hr = (byte) (hour % (byte) 12);
            if (hr == 0) {
                hr = 12;
            }

            String amPm = hour < 12 ? "AM" : "PM";

            return String.format("%d:%02d %s", hr, minute, amPm);
        }

        boolean isNight() {
            return hour < 5 || hour > 18;
        }
    }
}
