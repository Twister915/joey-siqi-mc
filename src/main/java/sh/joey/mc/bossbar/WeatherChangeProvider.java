package sh.joey.mc.bossbar;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Boss bar provider that shows weather change notifications.
 * Display lasts for 5 seconds after weather changes.
 */
public final class WeatherChangeProvider implements BossBarProvider, Disposable {

    private static final int PRIORITY = 140;
    private static final long DISPLAY_DURATION_MS = 5000;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, WeatherNotification> worldNotifications = new HashMap<>();
    private final Map<UUID, Long> playerWorldEntryTime = new HashMap<>();

    private record WeatherNotification(WeatherType type, long changedAt) {}

    private enum WeatherType {
        CLEAR("Clear Skies", BarColor.YELLOW, "&e"),
        RAIN("Rain", BarColor.BLUE, "&9"),
        THUNDER("Thunderstorm", BarColor.PURPLE, "&5");

        final String displayName;
        final BarColor barColor;
        final String colorCode;

        WeatherType(String displayName, BarColor barColor, String colorCode) {
            this.displayName = displayName;
            this.barColor = barColor;
            this.colorCode = colorCode;
        }
    }

    public WeatherChangeProvider(SiqiJoeyPlugin plugin) {
        // Weather change events
        disposables.add(plugin.watchEvent(WeatherChangeEvent.class)
                .subscribe(this::handleWeatherChange));

        disposables.add(plugin.watchEvent(ThunderChangeEvent.class)
                .subscribe(this::handleThunderChange));

        // Player world entry tracking
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> playerWorldEntryTime.put(
                        event.getPlayer().getUniqueId(), System.currentTimeMillis())));

        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(event -> playerWorldEntryTime.put(
                        event.getPlayer().getUniqueId(), System.currentTimeMillis())));

        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> playerWorldEntryTime.remove(event.getPlayer().getUniqueId())));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private void handleWeatherChange(WeatherChangeEvent event) {
        World world = event.getWorld();
        boolean willRain = event.toWeatherState();

        WeatherType newType;
        if (willRain) {
            newType = world.isThundering() ? WeatherType.THUNDER : WeatherType.RAIN;
        } else {
            newType = WeatherType.CLEAR;
        }

        worldNotifications.put(world.getUID(), new WeatherNotification(newType, System.currentTimeMillis()));
    }

    private void handleThunderChange(ThunderChangeEvent event) {
        World world = event.getWorld();
        boolean willThunder = event.toThunderState();

        if (!world.hasStorm() && !willThunder) {
            return;
        }

        WeatherType newType = willThunder ? WeatherType.THUNDER : WeatherType.RAIN;
        worldNotifications.put(world.getUID(), new WeatherNotification(newType, System.currentTimeMillis()));
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Optional<BossBarState> getState(Player player) {
        World world = player.getWorld();

        // Only show for overworld
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return Optional.empty();
        }

        WeatherNotification notification = worldNotifications.get(world.getUID());
        if (notification == null) {
            return Optional.empty();
        }

        // Don't show weather changes that happened before the player entered this world
        Long entryTime = playerWorldEntryTime.get(player.getUniqueId());
        if (entryTime != null && notification.changedAt() < entryTime) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        long elapsed = now - notification.changedAt();

        if (elapsed > DISPLAY_DURATION_MS) {
            return Optional.empty();
        }

        float progress = 1.0f - (float) elapsed / DISPLAY_DURATION_MS;
        WeatherType type = notification.type();

        String title = ChatColor.translateAlternateColorCodes('&',
                "&7Weather: " + type.colorCode + "&l" + type.displayName);

        return Optional.of(new BossBarState(title, type.barColor, progress, BarStyle.SOLID));
    }
}
