package sh.joey.mc.protection;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Visualizes region borders using particles.
 * Players can toggle visualization on/off.
 */
public final class RegionVisualizer implements Disposable {

    private static final int POINTS_PER_CIRCLE = 48;
    private static final double PILLAR_HEIGHT = 3.0;
    private static final int PILLAR_POINTS = 6;

    private final RegionManager manager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // Players with visualization enabled
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    public RegionVisualizer(SiqiJoeyPlugin plugin, RegionManager manager) {
        this.manager = manager;

        // Render particles every 500ms
        disposables.add(plugin.interval(500, TimeUnit.MILLISECONDS)
                .subscribe(tick -> renderAll()));

        // Clean up on player quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> enabledPlayers.remove(event.getPlayer().getUniqueId())));
    }

    /**
     * Toggle visualization for a player.
     *
     * @return true if visualization is now enabled
     */
    public boolean toggle(UUID playerId) {
        if (enabledPlayers.contains(playerId)) {
            enabledPlayers.remove(playerId);
            return false;
        } else {
            enabledPlayers.add(playerId);
            return true;
        }
    }

    /**
     * Check if visualization is enabled for a player.
     */
    public boolean isEnabled(UUID playerId) {
        return enabledPlayers.contains(playerId);
    }

    private void renderAll() {
        for (UUID playerId : enabledPlayers) {
            Player player = org.bukkit.Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                enabledPlayers.remove(playerId);
                continue;
            }

            renderForPlayer(player);
        }
    }

    private void renderForPlayer(Player player) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;

        // Find regions near the player (within render distance)
        int renderDistance = 64;
        for (Region region : manager.getCache().getRegionsInWorld(world.getUID())) {
            double dx = region.centerX() - playerLoc.getX();
            double dz = region.centerZ() - playerLoc.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);

            // Only render if player is within render distance of the region border
            if (distance > region.radius() + renderDistance) {
                continue;
            }

            renderRegion(player, region);
        }
    }

    private void renderRegion(Player player, Region region) {
        World world = player.getWorld();
        if (world == null) return;

        // Determine particle color based on player's access
        boolean canBuild = region.canBuild(player.getUniqueId());
        Color color = canBuild ? Color.fromRGB(0, 255, 0) : Color.fromRGB(255, 0, 0);
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);

        double playerY = player.getLocation().getY();
        double centerX = region.centerX() + 0.5;
        double centerZ = region.centerZ() + 0.5;
        int radius = region.radius();

        // Render circle at player's Y level
        for (int i = 0; i < POINTS_PER_CIRCLE; i++) {
            double angle = (2 * Math.PI * i) / POINTS_PER_CIRCLE;
            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);

            // Only render points within 32 blocks of player
            double dx = x - player.getLocation().getX();
            double dz = z - player.getLocation().getZ();
            if (dx * dx + dz * dz > 32 * 32) continue;

            Location loc = new Location(world, x, playerY, z);
            player.spawnParticle(Particle.DUST, loc, 1, dustOptions);
        }

        // Render cardinal pillars
        renderPillar(player, centerX + radius, centerZ, playerY, dustOptions);
        renderPillar(player, centerX - radius, centerZ, playerY, dustOptions);
        renderPillar(player, centerX, centerZ + radius, playerY, dustOptions);
        renderPillar(player, centerX, centerZ - radius, playerY, dustOptions);
    }

    private void renderPillar(Player player, double x, double z, double baseY, Particle.DustOptions dustOptions) {
        World world = player.getWorld();
        if (world == null) return;

        // Only render pillars within 24 blocks
        double dx = x - player.getLocation().getX();
        double dz = z - player.getLocation().getZ();
        if (dx * dx + dz * dz > 24 * 24) return;

        for (int i = 0; i < PILLAR_POINTS; i++) {
            double y = baseY - PILLAR_HEIGHT / 2 + (PILLAR_HEIGHT * i) / (PILLAR_POINTS - 1);
            Location loc = new Location(world, x, y, z);
            player.spawnParticle(Particle.DUST, loc, 1, dustOptions);
        }
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
