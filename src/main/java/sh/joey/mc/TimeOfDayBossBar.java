package sh.joey.mc;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TimeOfDayBossBar implements Listener {

    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    TimeOfDayBossBar(SiqiJoeyPlugin plugin) {
        plugin.getServer().getWorlds().forEach(this::createBossBar);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void onDisable() {
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        createBossBar(event.getWorld());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        BossBar worldBar = bossBars.get(world.getUID());
        if (worldBar != null) {
            worldBar.addPlayer(player);
        }
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        BossBar worldBar = bossBars.get(world.getUID());
        if (worldBar != null) {
            worldBar.removePlayer(player);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        World fromWorld = event.getFrom();
        World toWorld = event.getPlayer().getWorld();

        BossBar oldWorldBossBar = bossBars.get(fromWorld.getUID());
        if (oldWorldBossBar != null) {
            oldWorldBossBar.removePlayer(event.getPlayer());
        }

        BossBar newWorldBossBar = bossBars.get(toWorld.getUID());
        if (newWorldBossBar != null) {
            newWorldBossBar.removePlayer(event.getPlayer());
        }
    }

    private void createBossBar(World world) {
        if (bossBars.containsKey(world.getUID())) {
            return;
        }

        Optional<BossBarState> bossBarStateOptional = computeBossBarState(world);
        if (bossBarStateOptional.isEmpty()) {
            return;
        }

        BossBarState bossBarState = bossBarStateOptional.get();

        bossBars.put(
                world.getUID(),
                Bukkit.createBossBar(
                        bossBarState.title,
                        bossBarState.color,
                        bossBarState.style));
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        BossBar oldBar = bossBars.remove(event.getWorld().getUID());
        if (oldBar != null) {
            oldBar.removeAll();
        }
    }

    @EventHandler
    public void onTick(ServerTickStartEvent event) {
        bossBars.forEach((worldId, bossBar) ->
                computeBossBarState(Bukkit.getWorld(worldId))
                        .ifPresent(bossBarState ->
                                updateBossBar(bossBar, bossBarState)));
    }

    private static void updateBossBar(BossBar bossBar, BossBarState desiredState) {
        bossBar.setProgress(desiredState.progress);
        bossBar.setColor(desiredState.color);
        bossBar.setTitle(desiredState.title);
        bossBar.setStyle(desiredState.style);
    }

    @SuppressWarnings("deprecation")
    static Optional<BossBarState> computeBossBarState(World world) {
        WorldTime worldTime = getWorldTime(world);

        BarColor color = worldTime.localTime.isNight() ? BarColor.BLUE : BarColor.RED;

        String colorCode = worldTime.localTime.isNight() ? "&9" : "&6";
        String timeOfDay = worldTime.localTime.isNight() ? "Night" : "Day";
        String title = String.format(
                "%s&l%s %d &7:: %s&l%s",
                colorCode,
                timeOfDay,
                worldTime.days,
                colorCode,
                worldTime.localTime);
        title = ChatColor.translateAlternateColorCodes('&', title);
        BarStyle style;
        if (worldTime.localTime.isNight()) {
            style = BarStyle.SEGMENTED_10;
        } else {
            style = BarStyle.SEGMENTED_12;
        }
        return Optional.of(new BossBarState(title, color, worldTime.localTime.progress, style));
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

    record BossBarState(String title, BarColor color, float progress, BarStyle style) {
    }
}
