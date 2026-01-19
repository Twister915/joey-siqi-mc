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
 * Renders the outer perimeter of regions (union of all anchor circles).
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
            // Check if any anchor is within render distance
            boolean anyNearby = false;
            for (Anchor anchor : region.anchors()) {
                double dx = anchor.x() - playerLoc.getX();
                double dz = anchor.z() - playerLoc.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= region.radius() + renderDistance) {
                    anyNearby = true;
                    break;
                }
            }

            if (anyNearby) {
                renderRegion(player, region);
            }
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
        int radius = region.radius();
        var anchors = region.anchors();

        // Render outer perimeter only - for each anchor's circle, only render points
        // that are NOT inside any other anchor's circle
        for (Anchor anchor : anchors) {
            double centerX = anchor.x() + 0.5;
            double centerZ = anchor.z() + 0.5;

            // Check if this anchor is close enough to render
            double dx = centerX - player.getLocation().getX();
            double dz = centerZ - player.getLocation().getZ();
            if (dx * dx + dz * dz > (radius + 64) * (radius + 64)) {
                continue;
            }

            renderOuterPerimeter(player, anchor, anchors, radius, playerY, dustOptions);
        }
    }

    /**
     * Renders only the outer perimeter points for an anchor's circle.
     * Points that fall inside another anchor's circle are skipped.
     */
    private void renderOuterPerimeter(Player player, Anchor anchor, java.util.List<Anchor> allAnchors,
                                       int radius, double playerY, Particle.DustOptions dustOptions) {
        World world = player.getWorld();
        if (world == null) return;

        double centerX = anchor.x() + 0.5;
        double centerZ = anchor.z() + 0.5;

        // Render circle points at player's Y level
        for (int i = 0; i < POINTS_PER_CIRCLE; i++) {
            double angle = (2 * Math.PI * i) / POINTS_PER_CIRCLE;
            double x = centerX + radius * Math.cos(angle);
            double z = centerZ + radius * Math.sin(angle);

            // Only render points within 32 blocks of player
            double dx = x - player.getLocation().getX();
            double dz = z - player.getLocation().getZ();
            if (dx * dx + dz * dz > 32 * 32) continue;

            // Skip if this point is inside another anchor's circle (not on outer perimeter)
            if (isInsideOtherAnchor(x, z, anchor, allAnchors, radius)) continue;

            Location loc = new Location(world, x, playerY, z);
            player.spawnParticle(Particle.DUST, loc, 1, dustOptions);
        }

        // Render cardinal pillars only if they're on the outer perimeter
        double[] cardinalX = {centerX + radius, centerX - radius, centerX, centerX};
        double[] cardinalZ = {centerZ, centerZ, centerZ + radius, centerZ - radius};

        for (int i = 0; i < 4; i++) {
            if (!isInsideOtherAnchor(cardinalX[i], cardinalZ[i], anchor, allAnchors, radius)) {
                renderPillar(player, cardinalX[i], cardinalZ[i], playerY, dustOptions);
            }
        }
    }

    /**
     * Checks if a point is inside any other anchor's circle (excluding the source anchor).
     */
    private boolean isInsideOtherAnchor(double x, double z, Anchor sourceAnchor,
                                         java.util.List<Anchor> allAnchors, int radius) {
        for (Anchor other : allAnchors) {
            if (other.id().equals(sourceAnchor.id())) continue;

            double otherCenterX = other.x() + 0.5;
            double otherCenterZ = other.z() + 0.5;
            double dx = x - otherCenterX;
            double dz = z - otherCenterZ;
            double distSq = dx * dx + dz * dz;

            // Use slightly smaller radius to avoid gaps at intersection points
            if (distSq < (radius - 0.5) * (radius - 0.5)) {
                return true;
            }
        }
        return false;
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
