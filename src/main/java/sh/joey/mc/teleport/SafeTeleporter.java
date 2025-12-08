package sh.joey.mc.teleport;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Handles safe teleportation with warmup countdown and movement detection.
 * Players must stand still during the warmup period to teleport.
 */
public final class SafeTeleporter implements Listener {
    private static final long CANCELLED_DISPLAY_MS = 3000; // Show cancelled for 3 seconds

    private final JavaPlugin plugin;
    private final PluginConfig config;
    private final LocationTracker locationTracker;
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, Long> cancelledTeleports = new HashMap<>();

    private record PendingTeleport(
            Location startLocation,
            Location destination,
            BukkitTask countdownTask,
            Consumer<Boolean> onComplete,
            long startTimeMs,
            int totalSeconds
    ) {}

    /**
     * State of a pending teleport for external display (e.g., boss bar).
     */
    public record TeleportState(long startTimeMs, int totalSeconds) {
        public float getProgress() {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            long totalMs = totalSeconds * 1000L;
            return Math.max(0, Math.min(1, 1.0f - (float) elapsed / totalMs));
        }

        public int getRemainingSeconds() {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            long totalMs = totalSeconds * 1000L;
            long remaining = totalMs - elapsed;
            return (int) Math.max(0, Math.ceil(remaining / 1000.0));
        }
    }

    /**
     * State of a cancelled teleport for external display.
     */
    public record CancelledState(long cancelledAtMs) {
        public float getProgress() {
            long elapsed = System.currentTimeMillis() - cancelledAtMs;
            return Math.max(0, 1.0f - (float) elapsed / CANCELLED_DISPLAY_MS);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - cancelledAtMs > CANCELLED_DISPLAY_MS;
        }
    }

    public SafeTeleporter(JavaPlugin plugin, PluginConfig config, LocationTracker locationTracker) {
        this.plugin = plugin;
        this.config = config;
        this.locationTracker = locationTracker;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Initiates a safe teleport with warmup. Player must not move during countdown.
     *
     * @param player      The player to teleport
     * @param destination Where to teleport
     * @param onComplete  Callback with true if teleport succeeded, false if cancelled
     */
    public void teleport(Player player, Location destination, Consumer<Boolean> onComplete) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing pending teleport
        cancelTeleport(playerId, false);

        // Clear any cancelled display state
        cancelledTeleports.remove(playerId);

        Location startLocation = player.getLocation().clone();
        int[] secondsRemaining = {config.teleportWarmupSeconds()};

        Messages.countdown(player, secondsRemaining[0]);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            secondsRemaining[0]--;

            if (secondsRemaining[0] > 0) {
                Messages.countdown(player, secondsRemaining[0]);
            } else {
                // Time's up - execute teleport
                executeTeleport(player, destination, onComplete);
            }
        }, 20L, 20L); // 20 ticks = 1 second

        pendingTeleports.put(playerId, new PendingTeleport(
                startLocation, destination, task, onComplete,
                System.currentTimeMillis(), config.teleportWarmupSeconds()
        ));
    }

    private void executeTeleport(Player player, Location destination, Consumer<Boolean> onComplete) {
        UUID playerId = player.getUniqueId();
        PendingTeleport pending = pendingTeleports.remove(playerId);

        if (pending != null) {
            pending.countdownTask().cancel();
        }

        // Record current location before teleporting (for /back)
        Location departureLocation = player.getLocation().clone();
        locationTracker.recordTeleportFrom(playerId, departureLocation);

        // Play departure effects
        playTeleportEffects(departureLocation, true);

        player.teleport(destination);

        // Play arrival effects
        playTeleportEffects(destination, false);

        Messages.success(player, "Teleported!");

        if (onComplete != null) {
            onComplete.accept(true);
        }
    }

    /**
     * Plays teleport particle and sound effects at a location.
     *
     * @param location   The location to play effects at
     * @param isDeparture True if this is the departure location, false for arrival
     */
    private void playTeleportEffects(Location location, boolean isDeparture) {
        World world = location.getWorld();
        if (world == null) return;

        // Center the effects at player eye level
        Location effectLocation = location.clone().add(0, 1, 0);

        // Smoke particles that linger and rise
        world.spawnParticle(
                Particle.CAMPFIRE_SIGNAL_SMOKE,
                effectLocation,
                15,           // count
                0.3,          // offsetX
                0.5,          // offsetY
                0.3,          // offsetZ
                0.01          // speed (slow rise)
        );

        // Portal particles for a magical effect
        world.spawnParticle(
                Particle.PORTAL,
                effectLocation,
                30,           // count
                0.4,          // offsetX
                0.8,          // offsetY
                0.4,          // offsetZ
                0.5           // speed
        );

        // Play teleport sound for nearby players
        world.playSound(
                location,
                isDeparture ? Sound.ENTITY_ENDERMAN_TELEPORT : Sound.ITEM_CHORUS_FRUIT_TELEPORT,
                1.0f,         // volume
                isDeparture ? 0.8f : 1.2f  // pitch (lower for departure, higher for arrival)
        );
    }

    private void cancelTeleport(UUID playerId, boolean notify) {
        PendingTeleport pending = pendingTeleports.remove(playerId);
        if (pending != null) {
            pending.countdownTask().cancel();
            if (notify) {
                // Record cancellation for boss bar display
                cancelledTeleports.put(playerId, System.currentTimeMillis());
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    Messages.teleportCancelled(player);
                }
            }
            if (pending.onComplete() != null) {
                pending.onComplete().accept(false);
            }
        }
    }

    public boolean hasPendingTeleport(UUID playerId) {
        return pendingTeleports.containsKey(playerId);
    }

    /**
     * Gets the teleport state for a player, if they have a pending teleport.
     */
    public TeleportState getTeleportState(UUID playerId) {
        PendingTeleport pending = pendingTeleports.get(playerId);
        if (pending == null) {
            return null;
        }
        return new TeleportState(pending.startTimeMs(), pending.totalSeconds());
    }

    /**
     * Gets the cancelled state for a player, if their teleport was recently cancelled.
     */
    public CancelledState getCancelledState(UUID playerId) {
        Long cancelledAt = cancelledTeleports.get(playerId);
        if (cancelledAt == null) {
            return null;
        }
        CancelledState state = new CancelledState(cancelledAt);
        if (state.isExpired()) {
            cancelledTeleports.remove(playerId);
            return null;
        }
        return state;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingTeleport pending = pendingTeleports.get(playerId);

        if (pending == null) {
            return;
        }

        // Calculate horizontal distance moved (ignore Y for small jumps/falls)
        Location from = pending.startLocation();
        Location to = event.getTo();

        double horizontalDistance = Math.sqrt(
                Math.pow(to.getX() - from.getX(), 2) +
                Math.pow(to.getZ() - from.getZ(), 2)
        );

        // Also check vertical but with more tolerance (for jumping)
        double verticalDistance = Math.abs(to.getY() - from.getY());

        // Cancel if moved beyond tolerance
        // Horizontal tolerance is strict, vertical is more forgiving
        if (horizontalDistance > config.movementToleranceBlocks() ||
            verticalDistance > config.movementToleranceBlocks() * 2) {
            cancelTeleport(playerId, true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        cancelTeleport(playerId, false);
        cancelledTeleports.remove(playerId);
    }
}
