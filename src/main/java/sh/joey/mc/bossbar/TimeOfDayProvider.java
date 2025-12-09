package sh.joey.mc.bossbar;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Boss bar provider that shows time of day information.
 * Low priority - shows when nothing else is active.
 *
 * Performance: Caches the boss bar state per world since all players
 * in the same world see the same time. Cache is invalidated when the
 * minute changes.
 */
public final class TimeOfDayProvider implements BossBarProvider {

    private static final int PRIORITY = 0;

    // US-style number formatting with commas (e.g., "1,234")
    private static final NumberFormat DAY_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    // Cache per world - all players in same world see same time
    private final Map<UUID, CachedState> worldCache = new HashMap<>();

    private record CachedState(long days, byte hour, byte minute, BossBarState state) {}

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public Optional<BossBarState> getState(Player player) {
        World world = player.getWorld();

        // Only show for overworld-type environments
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return Optional.empty();
        }

        WorldTime worldTime = getWorldTime(world);
        ClockTime clock = worldTime.localTime();

        // Check cache - only rebuild if minute/day changed
        UUID worldId = world.getUID();
        CachedState cached = worldCache.get(worldId);
        if (cached != null
                && cached.days == worldTime.days()
                && cached.hour == clock.hour()
                && cached.minute == clock.minute()) {
            // Update progress on cached state (progress changes every tick)
            return Optional.of(cached.state.withProgress(clock.progress()));
        }

        // Build new state
        BossBarState state = buildState(worldTime);
        worldCache.put(worldId, new CachedState(worldTime.days(), clock.hour(), clock.minute(), state));
        return Optional.of(state);
    }

    @SuppressWarnings("deprecation")
    private BossBarState buildState(WorldTime worldTime) {
        ClockTime clock = worldTime.localTime();
        boolean isNight = clock.isNight();

        BarColor color = isNight ? BarColor.BLUE : BarColor.RED;
        BarStyle style = isNight ? BarStyle.SEGMENTED_10 : BarStyle.SEGMENTED_12;

        // Build title using StringBuilder + ChatColor constants (avoids String.format and translateAlternateColorCodes)
        ChatColor primaryColor = isNight ? ChatColor.BLUE : ChatColor.GOLD;
        String timeOfDay = isNight ? "Night" : "Day";

        StringBuilder sb = new StringBuilder(32);
        sb.append(primaryColor).append(ChatColor.BOLD)
          .append(timeOfDay).append(' ').append(DAY_FORMAT.format(worldTime.days()))
          .append(' ').append(ChatColor.GRAY).append(":: ")
          .append(primaryColor).append(ChatColor.BOLD)
          .append(clock.toStringFast());

        return new BossBarState(sb.toString(), color, clock.progress(), style);
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

        /**
         * Fast string formatting that avoids String.format overhead.
         * Returns time like "7:05 AM" or "11:30 PM".
         */
        String toStringFast() {
            int hr = hour % 12;
            if (hr == 0) {
                hr = 12;
            }

            // Build string without String.format
            StringBuilder sb = new StringBuilder(8);
            sb.append(hr).append(':');
            if (minute < 10) {
                sb.append('0');
            }
            sb.append(minute).append(' ');
            sb.append(hour < 12 ? "AM" : "PM");
            return sb.toString();
        }

        boolean isNight() {
            return hour < 5 || hour > 18;
        }
    }
}
