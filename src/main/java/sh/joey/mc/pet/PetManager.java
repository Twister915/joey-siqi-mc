package sh.joey.mc.pet;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Central manager for the pet system.
 * Handles pet lifecycle, event listening, and animation tick loop.
 */
public final class PetManager implements Disposable {

    private static final long TICK_INTERVAL_MS = 50; // 20 TPS

    private final SiqiJoeyPlugin plugin;
    private final PetStorage storage;
    private final Logger logger;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, Pet> activePets = new ConcurrentHashMap<>();

    public PetManager(SiqiJoeyPlugin plugin, PetStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.logger = plugin.getLogger();

        // Tick-based animation loop
        disposables.add(plugin.interval(TICK_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .subscribe(tick -> tickAllPets()));

        // Player join - restore pet from database
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(this::handlePlayerJoin));

        // Player quit - despawn and save pet state
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(this::handlePlayerQuit));

        // World change - teleport pet
        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(this::handleWorldChange));

        // Death/respawn - teleport pet after respawn
        disposables.add(plugin.watchEvent(PlayerRespawnEvent.class)
                .subscribe(this::handleRespawn));
    }

    private void tickAllPets() {
        for (Pet pet : activePets.values()) {
            try {
                pet.tick();
            } catch (Exception e) {
                logger.warning("Pet tick error for " + pet.getOwner().getName() + ": " + e.getMessage());
            }
        }
    }

    private void handlePlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        disposables.add(storage.getActivePet(playerId)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        activePet -> spawnPetForPlayer(event.getPlayer(), activePet),
                        err -> logger.warning("Failed to load pet for " + event.getPlayer().getName() + ": " + err.getMessage())
                ));
    }

    private void spawnPetForPlayer(Player player, ActivePet activePet) {
        if (!player.isOnline()) return;

        Pet pet = activePet.type().createPet(plugin, player, this);
        pet.setState(activePet.state());

        Location spawnLoc = findLocationNearPlayer(player);
        pet.spawn(spawnLoc);

        activePets.put(player.getUniqueId(), pet);
        logger.info("Restored " + activePet.type().displayName() + " pet for " + player.getName());
    }

    private void handlePlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Pet pet = activePets.remove(playerId);

        if (pet != null) {
            // Save state before despawning (fire and forget)
            storage.savePet(playerId, pet.getType(), pet.getState())
                    .subscribe(
                            () -> {},
                            err -> logger.warning("Failed to save pet for " + event.getPlayer().getName() + ": " + err.getMessage())
                    );
            pet.dispose();
        }
    }

    private void handleWorldChange(PlayerChangedWorldEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Pet pet = activePets.get(playerId);

        if (pet != null) {
            // Despawn in old world, respawn in new
            pet.despawn();

            // Small delay to let the player fully load into new world
            disposables.add(plugin.timer(100, TimeUnit.MILLISECONDS)
                    .observeOn(plugin.mainScheduler())
                    .subscribe(tick -> {
                        if (event.getPlayer().isOnline()) {
                            Location newLoc = findLocationNearPlayer(event.getPlayer());
                            pet.spawn(newLoc);
                        }
                    }));
        }
    }

    private void handleRespawn(PlayerRespawnEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Pet pet = activePets.get(playerId);

        if (pet != null) {
            // Teleport pet to respawn location after a short delay
            disposables.add(plugin.timer(500, TimeUnit.MILLISECONDS)
                    .observeOn(plugin.mainScheduler())
                    .subscribe(tick -> {
                        if (event.getPlayer().isOnline()) {
                            Location newLoc = findLocationNearPlayer(event.getPlayer());
                            pet.teleportTo(newLoc);
                            pet.playSpawnEffect();
                        }
                    }));
        }
    }

    // ===== Public API =====

    /**
     * Spawns a pet for a player.
     */
    public void spawnPet(Player player, PetType type) {
        UUID playerId = player.getUniqueId();

        // Despawn existing pet if any
        Pet existing = activePets.remove(playerId);
        if (existing != null) {
            existing.dispose();
        }

        // Create and spawn new pet
        Pet pet = type.createPet(plugin, player, this);
        Location spawnLoc = findLocationNearPlayer(player);
        pet.spawn(spawnLoc);
        pet.playSpawnEffect();

        activePets.put(playerId, pet);

        // Save to database
        storage.savePet(playerId, type, PetState.FOLLOWING)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to save pet: " + err.getMessage())
                );
    }

    /**
     * Despawns a player's pet.
     */
    public void despawnPet(Player player) {
        UUID playerId = player.getUniqueId();
        Pet pet = activePets.remove(playerId);

        if (pet != null) {
            pet.playDespawnEffect();
            pet.dispose();

            storage.removePet(playerId)
                    .subscribe(
                            () -> {},
                            err -> logger.warning("Failed to remove pet: " + err.getMessage())
                    );
        }
    }

    /**
     * Toggles a pet's sit/follow state.
     */
    public void toggleSit(Player player) {
        UUID playerId = player.getUniqueId();
        Pet pet = activePets.get(playerId);

        if (pet != null) {
            PetState newState = pet.getState() == PetState.SITTING
                    ? PetState.FOLLOWING
                    : PetState.SITTING;
            pet.setState(newState);

            storage.updateState(playerId, newState)
                    .subscribe(
                            () -> {},
                            err -> logger.warning("Failed to update pet state: " + err.getMessage())
                    );
        }
    }

    /**
     * Gets a player's active pet if they have one.
     */
    public Optional<Pet> getPet(UUID playerId) {
        return Optional.ofNullable(activePets.get(playerId));
    }

    /**
     * Checks if a player has an active pet.
     */
    public boolean hasPet(UUID playerId) {
        return activePets.containsKey(playerId);
    }

    private Location findLocationNearPlayer(Player player) {
        Location playerLoc = player.getLocation();
        // Spawn behind and to the side of the player
        double angle = Math.toRadians(playerLoc.getYaw() + 135);
        double x = playerLoc.getX() + Math.cos(angle) * 1.5;
        double z = playerLoc.getZ() + Math.sin(angle) * 1.5;
        Location loc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
        loc.setYaw(playerLoc.getYaw());
        return loc;
    }

    @Override
    public void dispose() {
        disposables.dispose();
        // Despawn all pets
        for (Pet pet : activePets.values()) {
            pet.dispose();
        }
        activePets.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
