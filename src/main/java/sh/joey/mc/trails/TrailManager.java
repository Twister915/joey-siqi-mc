package sh.joey.mc.trails;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages trail particle effects for players.
 * Handles event listening, caching, and particle spawning.
 */
public final class TrailManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final TrailStorage storage;
    private final Logger logger;

    // In-memory cache: playerId -> Map<TrailType, TrailSetting>
    private final Map<UUID, Map<TrailType, TrailSetting>> playerTrails = new ConcurrentHashMap<>();

    // Rate limiting: playerId -> Map<TrailType, lastSpawnTick>
    private final Map<UUID, Map<TrailType, Long>> lastSpawnTick = new ConcurrentHashMap<>();

    public TrailManager(SiqiJoeyPlugin plugin, TrailStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.logger = plugin.getLogger();

        // Watch PlayerMoveEvent for elytra gliding
        disposables.add(plugin.watchEvent(PlayerMoveEvent.class)
                .filter(e -> e.getPlayer().isGliding())
                .filter(e -> e.getPlayer().hasPermission(TrailType.ELYTRA.permission()))
                .subscribe(this::handleElytraGlide));

        // Watch PlayerMoveEvent for ghast riding
        disposables.add(plugin.watchEvent(PlayerMoveEvent.class)
                .filter(e -> {
                    Entity vehicle = e.getPlayer().getVehicle();
                    return vehicle != null && vehicle.getType() == EntityType.GHAST;
                })
                .filter(e -> e.getPlayer().hasPermission(TrailType.GHAST.permission()))
                .subscribe(this::handleGhastRide));

        // Watch PlayerMoveEvent for walking
        disposables.add(plugin.watchEvent(PlayerMoveEvent.class)
                .filter(e -> !e.getPlayer().isFlying())
                .filter(e -> !e.getPlayer().isGliding())
                .filter(e -> e.getPlayer().getVehicle() == null)
                .filter(e -> hasActuallyMoved(e))
                .filter(e -> e.getPlayer().hasPermission(TrailType.WALK.permission()))
                .subscribe(this::handleWalk));

        // Load on join
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(this::handlePlayerJoin));

        // Clear on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(this::handlePlayerQuit));
    }

    private void handlePlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        loadPlayerTrails(playerId);
    }

    private void handlePlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        playerTrails.remove(playerId);
        lastSpawnTick.remove(playerId);
    }

    private void loadPlayerTrails(UUID playerId) {
        disposables.add(storage.getAllTrailSettings(playerId)
                .toList()
                .subscribe(
                        settings -> {
                            Map<TrailType, TrailSetting> map = new EnumMap<>(TrailType.class);
                            for (var row : settings) {
                                map.put(row.type(), row.setting());
                            }
                            if (!map.isEmpty()) {
                                playerTrails.put(playerId, map);
                            }
                        },
                        err -> logger.warning("Failed to load trails for " + playerId + ": " + err.getMessage())
                ));
    }

    private void handleElytraGlide(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        TrailSetting setting = getSetting(playerId, TrailType.ELYTRA);
        if (setting == null) return;

        // Rate limit based on intensity
        long currentTick = player.getWorld().getFullTime();
        if (!shouldSpawn(playerId, TrailType.ELYTRA, currentTick, setting.intensity().tickInterval())) {
            return;
        }

        // Spawn at previous location for trail effect
        Location from = event.getFrom().clone().add(0, 0.5, 0);
        setting.effect().spawn(from, currentTick, setting.intensity());
    }

    private void handleGhastRide(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        TrailSetting setting = getSetting(playerId, TrailType.GHAST);
        if (setting == null) return;

        // Rate limit based on intensity
        long currentTick = player.getWorld().getFullTime();
        if (!shouldSpawn(playerId, TrailType.GHAST, currentTick, setting.intensity().tickInterval())) {
            return;
        }

        // Spawn at the back of the ghast
        Entity ghast = player.getVehicle();
        if (ghast == null) return;

        Location trailLocation = calculateGhastBackPosition(ghast.getLocation());
        setting.effect().spawn(trailLocation, currentTick, setting.intensity());
    }

    private Location calculateGhastBackPosition(Location ghastLoc) {
        // Calculate position behind the ghast based on its facing direction
        float yawRad = (float) Math.toRadians(ghastLoc.getYaw());
        double backX = -Math.sin(yawRad);
        double backZ = Math.cos(yawRad);
        // 2 blocks behind, slightly above center
        return ghastLoc.clone().add(backX * 2.0, 0.5, backZ * 2.0);
    }

    private boolean hasActuallyMoved(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // Check if position changed (not just head rotation)
        return from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
    }

    private void handleWalk(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        TrailSetting setting = getSetting(playerId, TrailType.WALK);
        if (setting == null) return;

        // Rate limit based on intensity
        long currentTick = player.getWorld().getFullTime();
        if (!shouldSpawn(playerId, TrailType.WALK, currentTick, setting.intensity().tickInterval())) {
            return;
        }

        // Spawn at previous location (at feet level)
        Location from = event.getFrom().clone().add(0, 0.1, 0);
        setting.effect().spawn(from, currentTick, setting.intensity());
    }

    private boolean shouldSpawn(UUID playerId, TrailType type, long currentTick, int interval) {
        Map<TrailType, Long> ticks = lastSpawnTick.computeIfAbsent(playerId, k -> new EnumMap<>(TrailType.class));
        Long lastTick = ticks.get(type);

        if (lastTick == null || currentTick - lastTick >= interval) {
            ticks.put(type, currentTick);
            return true;
        }
        return false;
    }

    /**
     * Gets a player's trail setting for a type.
     * Returns null if no trail is set.
     */
    public TrailSetting getSetting(UUID playerId, TrailType type) {
        Map<TrailType, TrailSetting> map = playerTrails.get(playerId);
        return map != null ? map.get(type) : null;
    }

    /**
     * Sets a player's trail effect in the cache.
     * Also saves to database.
     */
    public void setEffect(Player player, TrailType type, TrailEffect effect) {
        UUID playerId = player.getUniqueId();

        // Get current intensity or default
        TrailSetting current = getSetting(playerId, type);
        TrailIntensity intensity = current != null ? current.intensity() : TrailIntensity.defaultIntensity();

        TrailSetting newSetting = new TrailSetting(effect, intensity);
        updateCache(playerId, type, newSetting);

        disposables.add(storage.setTrailSetting(playerId, type, effect, intensity)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to save trail for " + player.getName() + ": " + err.getMessage())
                ));
    }

    /**
     * Sets a player's trail effect with a specific intensity.
     */
    public void setSetting(Player player, TrailType type, TrailEffect effect, TrailIntensity intensity) {
        UUID playerId = player.getUniqueId();
        TrailSetting newSetting = new TrailSetting(effect, intensity);
        updateCache(playerId, type, newSetting);

        disposables.add(storage.setTrailSetting(playerId, type, effect, intensity)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to save trail for " + player.getName() + ": " + err.getMessage())
                ));
    }

    /**
     * Updates only the intensity for a trail type.
     */
    public void setIntensity(Player player, TrailType type, TrailIntensity intensity) {
        UUID playerId = player.getUniqueId();
        TrailSetting current = getSetting(playerId, type);

        if (current == null) {
            return; // No effect set, nothing to update
        }

        TrailSetting newSetting = current.withIntensity(intensity);
        updateCache(playerId, type, newSetting);

        disposables.add(storage.setTrailIntensity(playerId, type, intensity)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to save intensity for " + player.getName() + ": " + err.getMessage())
                ));
    }

    /**
     * Clears a player's trail for a type.
     */
    public void clearTrail(Player player, TrailType type) {
        UUID playerId = player.getUniqueId();
        Map<TrailType, TrailSetting> map = playerTrails.get(playerId);
        if (map != null) {
            map.remove(type);
            if (map.isEmpty()) {
                playerTrails.remove(playerId);
            }
        }

        disposables.add(storage.clearTrailSetting(playerId, type)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to clear trail for " + player.getName() + ": " + err.getMessage())
                ));
    }

    /**
     * Clears all trails for a player.
     */
    public void clearAllTrails(Player player) {
        UUID playerId = player.getUniqueId();
        playerTrails.remove(playerId);

        disposables.add(storage.clearAllTrailSettings(playerId)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to clear all trails for " + player.getName() + ": " + err.getMessage())
                ));
    }

    private void updateCache(UUID playerId, TrailType type, TrailSetting setting) {
        playerTrails.computeIfAbsent(playerId, k -> new EnumMap<>(TrailType.class))
                .put(type, setting);
    }

    @Override
    public void dispose() {
        disposables.dispose();
        playerTrails.clear();
        lastSpawnTick.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
