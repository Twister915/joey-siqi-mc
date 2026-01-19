package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.loot.LootTable;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks movement and exploration for exploration challenges.
 * Samples movement every second to reduce performance impact.
 */
public final class ExplorationTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Biome>> visitedBiomes = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> discoveredStructures = new ConcurrentHashMap<>();

    private static final long SAMPLE_INTERVAL_MS = 1000; // 1 second
    private static final double MIN_DISTANCE = 0.1; // Minimum distance to track
    // Max reasonable distance per sample (sprint-jumping with speed effects is ~10 blocks/sec)
    private static final double MAX_WALK_DISTANCE = 20.0;

    /**
     * Structure loot table prefixes that indicate structure discovery.
     */
    private static final Set<String> STRUCTURE_LOOT_PREFIXES = Set.of(
            "chests/simple_dungeon",
            "chests/stronghold",
            "chests/abandoned_mineshaft",
            "chests/buried_treasure",
            "chests/desert_pyramid",
            "chests/end_city_treasure",
            "chests/igloo_chest",
            "chests/jungle_temple",
            "chests/nether_bridge",
            "chests/pillager_outpost",
            "chests/bastion",
            "chests/ruined_portal",
            "chests/shipwreck",
            "chests/underwater_ruin",
            "chests/village",
            "chests/woodland_mansion",
            "chests/ancient_city",
            "chests/trial_chambers"
    );

    public ExplorationTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        disposables.add(plugin.watchEvent(PlayerMoveEvent.class)
                .subscribe(event -> {
                    Player player = event.getPlayer();
                    UUID playerId = player.getUniqueId();
                    long now = System.currentTimeMillis();

                    // Sample rate limit
                    Long lastUpdate = lastUpdateTime.get(playerId);
                    if (lastUpdate != null && now - lastUpdate < SAMPLE_INTERVAL_MS) {
                        return;
                    }

                    Location from = lastLocations.get(playerId);
                    Location to = player.getLocation();

                    if (from == null || !from.getWorld().equals(to.getWorld())) {
                        lastLocations.put(playerId, to);
                        lastUpdateTime.put(playerId, now);
                        return;
                    }

                    double distance = from.distance(to);
                    if (distance < MIN_DISTANCE) {
                        return;
                    }

                    lastLocations.put(playerId, to);
                    lastUpdateTime.put(playerId, now);

                    // Skip unreasonably large movements (likely teleport or other non-walking cause)
                    if (distance > MAX_WALK_DISTANCE) {
                        return;
                    }

                    // Determine movement type
                    String movementType = getMovementType(player, from, to);
                    long blocks = (long) distance;

                    if (blocks > 0) {
                        progressTracker.increment(playerId, "distance:" + movementType, blocks);
                    }

                    // Track unique biomes visited
                    Biome currentBiome = to.getBlock().getBiome();
                    Set<Biome> playerBiomes = visitedBiomes.computeIfAbsent(playerId, k -> new HashSet<>());
                    if (playerBiomes.add(currentBiome)) {
                        progressTracker.increment(playerId, "biomes_visited");
                    }
                }));

        // Reset last location on teleport to prevent counting teleport distance
        disposables.add(plugin.watchEvent(PlayerTeleportEvent.class)
                .subscribe(event -> {
                    UUID playerId = event.getPlayer().getUniqueId();
                    Location destination = event.getTo();
                    if (destination != null) {
                        lastLocations.put(playerId, destination);
                        lastUpdateTime.put(playerId, System.currentTimeMillis());
                    }
                }));

        // Track loot chest opens and structure discovery
        disposables.add(plugin.watchEvent(LootGenerateEvent.class)
                .filter(event -> event.getEntity() instanceof Player)
                .subscribe(event -> {
                    Player player = (Player) event.getEntity();
                    UUID playerId = player.getUniqueId();
                    progressTracker.increment(playerId, "loot_chests_opened");

                    // Check if this is a structure loot table
                    LootTable lootTable = event.getLootTable();
                    if (lootTable != null) {
                        String key = lootTable.getKey().getKey();
                        String structureType = getStructureType(key);
                        if (structureType != null) {
                            Set<String> playerStructures = discoveredStructures.computeIfAbsent(playerId, k -> new HashSet<>());
                            if (playerStructures.add(structureType)) {
                                progressTracker.increment(playerId, "structures_found");
                            }
                        }
                    }
                }));

        // Cleanup on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    UUID playerId = event.getPlayer().getUniqueId();
                    visitedBiomes.remove(playerId);
                    discoveredStructures.remove(playerId);
                }));
    }

    /**
     * Extracts the structure type from a loot table key.
     * Returns null if not a structure loot table.
     */
    private String getStructureType(String lootTableKey) {
        for (String prefix : STRUCTURE_LOOT_PREFIXES) {
            if (lootTableKey.startsWith(prefix)) {
                // Normalize to structure category (e.g., "chests/stronghold_corridor" -> "stronghold")
                String[] parts = prefix.split("/");
                if (parts.length >= 2) {
                    return parts[1].split("_")[0]; // Get first part of structure name
                }
            }
        }
        return null;
    }

    private String getMovementType(Player player, Location from, Location to) {
        // Check if player is in a vehicle
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            if (vehicle instanceof Boat) {
                return "BOAT";
            }
            if (vehicle instanceof Horse) {
                return "HORSE";
            }
            if (vehicle instanceof Minecart) {
                return "MINECART";
            }
            if (vehicle instanceof Pig) {
                return "PIG";
            }
        }

        // Check if gliding with elytra
        if (player.isGliding()) {
            return "ELYTRA";
        }

        // Check if swimming
        if (player.isSwimming()) {
            return "SWIM";
        }

        // Check if climbing (significant Y increase)
        double yDiff = to.getY() - from.getY();
        if (yDiff > 0.5) {
            return "CLIMB";
        }

        // Check if sprinting
        if (player.isSprinting()) {
            return "SPRINT";
        }

        // Default to walking
        return "WALK";
    }

    @Override
    public void dispose() {
        disposables.dispose();
        lastLocations.clear();
        lastUpdateTime.clear();
        visitedBiomes.clear();
        discoveredStructures.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
