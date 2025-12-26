package sh.joey.mc.bluemap;

import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.BlueMapWorld;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.utility.SpawnStorage;
import sh.joey.mc.utility.WarpStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Integrates with BlueMap to display POI markers for warps and spawn points.
 */
public final class BlueMapIntegration implements Disposable {

    private static final String WARPS_MARKER_SET = "siqi-warps";
    private static final String SPAWNS_MARKER_SET = "siqi-spawns";

    private final SiqiJoeyPlugin plugin;
    private final WarpStorage warpStorage;
    private final SpawnStorage spawnStorage;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private BlueMapAPI api;
    private Consumer<BlueMapAPI> enableListener;
    private Consumer<BlueMapAPI> disableListener;

    public BlueMapIntegration(SiqiJoeyPlugin plugin, WarpStorage warpStorage, SpawnStorage spawnStorage) {
        this.plugin = plugin;
        this.warpStorage = warpStorage;
        this.spawnStorage = spawnStorage;

        // Register BlueMap lifecycle listeners
        enableListener = this::onBlueMapEnable;
        disableListener = this::onBlueMapDisable;
        BlueMapAPI.onEnable(enableListener);
        BlueMapAPI.onDisable(disableListener);

        // Subscribe to storage change events (debounced on main thread)
        disposables.add(warpStorage.onChanged()
                .debounce(100, TimeUnit.MILLISECONDS, plugin.mainScheduler())
                .subscribe(v -> refreshWarps()));

        disposables.add(spawnStorage.onChanged()
                .debounce(100, TimeUnit.MILLISECONDS, plugin.mainScheduler())
                .subscribe(v -> refreshSpawns()));
    }

    private void onBlueMapEnable(BlueMapAPI api) {
        this.api = api;
        plugin.getLogger().info("BlueMap API enabled, creating markers...");
        refreshAll();
    }

    private void onBlueMapDisable(BlueMapAPI api) {
        this.api = null;
        plugin.getLogger().info("BlueMap API disabled");
    }

    /**
     * Refreshes both warp and spawn markers.
     */
    public void refreshAll() {
        refreshWarps();
        refreshSpawns();
    }

    /**
     * Reloads all warp markers from the database.
     */
    public void refreshWarps() {
        if (api == null) return;

        warpStorage.getAllWarps()
                .toList()
                .subscribe(
                        warps -> {
                            // Group warps by world
                            Map<UUID, MarkerSet> markerSetsByWorld = new HashMap<>();

                            for (var warp : warps) {
                                api.getWorld(warp.worldId()).ifPresent(world -> {
                                    MarkerSet markerSet = markerSetsByWorld.computeIfAbsent(
                                            warp.worldId(),
                                            id -> MarkerSet.builder()
                                                    .label("Warps")
                                                    .toggleable(true)
                                                    .defaultHidden(false)
                                                    .build()
                                    );

                                    POIMarker marker = POIMarker.builder()
                                            .label(warp.name())
                                            .position(warp.x(), warp.y(), warp.z())
                                            .build();

                                    markerSet.put("warp-" + warp.name(), marker);
                                });
                            }

                            // Add marker sets to all maps for each world
                            for (var entry : markerSetsByWorld.entrySet()) {
                                api.getWorld(entry.getKey()).ifPresent(world -> {
                                    for (BlueMapMap map : world.getMaps()) {
                                        map.getMarkerSets().put(WARPS_MARKER_SET, entry.getValue());
                                    }
                                });
                            }

                            // Remove marker sets from worlds that have no warps
                            for (BlueMapWorld world : api.getWorlds()) {
                                UUID worldId = getWorldUUID(world);
                                if (worldId != null && !markerSetsByWorld.containsKey(worldId)) {
                                    for (BlueMapMap map : world.getMaps()) {
                                        map.getMarkerSets().remove(WARPS_MARKER_SET);
                                    }
                                }
                            }

                            plugin.getLogger().info("Refreshed " + warps.size() + " warp markers");
                        },
                        error -> plugin.getLogger().warning("Failed to refresh warp markers: " + error.getMessage())
                );
    }

    /**
     * Reloads all spawn markers from the database.
     */
    public void refreshSpawns() {
        if (api == null) return;

        spawnStorage.getAllSpawns()
                .toList()
                .subscribe(
                        spawns -> {
                            // Group spawns by world
                            Map<UUID, MarkerSet> markerSetsByWorld = new HashMap<>();

                            for (var spawn : spawns) {
                                api.getWorld(spawn.worldId()).ifPresent(world -> {
                                    MarkerSet markerSet = markerSetsByWorld.computeIfAbsent(
                                            spawn.worldId(),
                                            id -> MarkerSet.builder()
                                                    .label("Spawn Points")
                                                    .toggleable(true)
                                                    .defaultHidden(false)
                                                    .build()
                                    );

                                    // Use world name as label
                                    String worldName = getWorldName(spawn.worldId());
                                    POIMarker marker = POIMarker.builder()
                                            .label(worldName + " Spawn")
                                            .position(spawn.x(), spawn.y(), spawn.z())
                                            .build();

                                    markerSet.put("spawn-" + spawn.worldId(), marker);
                                });
                            }

                            // Add marker sets to all maps for each world
                            for (var entry : markerSetsByWorld.entrySet()) {
                                api.getWorld(entry.getKey()).ifPresent(world -> {
                                    for (BlueMapMap map : world.getMaps()) {
                                        map.getMarkerSets().put(SPAWNS_MARKER_SET, entry.getValue());
                                    }
                                });
                            }

                            // Remove marker sets from worlds that have no spawns
                            for (BlueMapWorld world : api.getWorlds()) {
                                UUID worldId = getWorldUUID(world);
                                if (worldId != null && !markerSetsByWorld.containsKey(worldId)) {
                                    for (BlueMapMap map : world.getMaps()) {
                                        map.getMarkerSets().remove(SPAWNS_MARKER_SET);
                                    }
                                }
                            }

                            plugin.getLogger().info("Refreshed " + spawns.size() + " spawn markers");
                        },
                        error -> plugin.getLogger().warning("Failed to refresh spawn markers: " + error.getMessage())
                );
    }

    /**
     * Gets the world name from a world UUID.
     */
    private String getWorldName(UUID worldId) {
        var world = Bukkit.getWorld(worldId);
        return world != null ? world.getName() : worldId.toString();
    }

    /**
     * Tries to get the UUID of a BlueMapWorld by checking loaded Bukkit worlds.
     */
    private UUID getWorldUUID(BlueMapWorld blueMapWorld) {
        for (var world : Bukkit.getWorlds()) {
            if (api.getWorld(world).map(w -> w.equals(blueMapWorld)).orElse(false)) {
                return world.getUID();
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        disposables.dispose();

        // Unregister BlueMap listeners
        if (enableListener != null) {
            BlueMapAPI.unregisterListener(enableListener);
        }
        if (disableListener != null) {
            BlueMapAPI.unregisterListener(disableListener);
        }
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
