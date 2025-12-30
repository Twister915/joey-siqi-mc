package sh.joey.mc.settings;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central manager for player settings.
 * Handles in-memory caching and event-based features (keep inventory, easy mode).
 */
public final class SettingsManager implements Disposable {

    private static final String[] INSTA_KILL_MESSAGES = {
            "Critical hit!",
            "One-shot!",
            "Super effective!",
            "Lucky strike!",
            "Devastating blow!",
            "K.O.!",
            "Perfect hit!",
            "Boom!",
            "Gotcha!"
    };

    private final SiqiJoeyPlugin plugin;
    private final SettingsStorage storage;
    private final Logger logger;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public SettingsManager(SiqiJoeyPlugin plugin, SettingsStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.logger = plugin.getLogger();

        // Load all settings into cache on startup (blocking)
        loadCacheBlocking();

        // Player join - load settings from database into cache
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> loadPlayerSettings(event.getPlayer().getUniqueId())));

        // Player quit - remove from cache
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> cache.remove(event.getPlayer().getUniqueId())));

        // Keep inventory handler - HIGHEST priority to run after other death handlers
        disposables.add(plugin.watchEvent(EventPriority.HIGHEST, PlayerDeathEvent.class)
                .subscribe(this::handleDeath));

        // Easy mode - damage reduction (mobs dealing damage to players)
        disposables.add(plugin.watchEvent(EntityDamageByEntityEvent.class)
                .filter(e -> e.getEntity() instanceof Player)
                .filter(e -> !isPlayerSourcedDamage(e))  // Mobs only, not PvP
                .subscribe(this::handleIncomingDamage));

        // Easy mode - insta-kill chance (player dealing damage to mobs)
        disposables.add(plugin.watchEvent(EntityDamageByEntityEvent.class)
                .filter(e -> e.getDamager() instanceof Player)
                .filter(e -> e.getEntity() instanceof LivingEntity)
                .filter(e -> !(e.getEntity() instanceof Player))  // Don't insta-kill players
                .subscribe(this::handleOutgoingDamage));

        // Passive mode - cancel PvP damage if either player has passive mode
        disposables.add(plugin.watchEvent(EntityDamageByEntityEvent.class)
                .filter(e -> e.getEntity() instanceof Player)
                .filter(this::isPlayerSourcedDamage)
                .subscribe(this::handlePvpDamage));

        logger.info("[Settings] Initialized with " + cache.size() + " cached settings");
    }

    private void loadCacheBlocking() {
        try {
            storage.getAllSettings()
                    .blockingForEach(entry -> cache.put(entry.getKey(), entry.getValue()));
        } catch (Exception e) {
            logger.warning("[Settings] Failed to load settings cache: " + e.getMessage());
        }
    }

    private void loadPlayerSettings(UUID playerId) {
        // Load from database, falling back to defaults if not found
        storage.getSettings(playerId)
                .defaultIfEmpty(PlayerSettings.DEFAULTS)
                .subscribe(
                        settings -> cache.put(playerId, settings),
                        err -> {
                            logger.warning("[Settings] Failed to load settings for " + playerId + ": " + err.getMessage());
                            cache.put(playerId, PlayerSettings.DEFAULTS);
                        }
                );
    }

    private void handleDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PlayerSettings settings = getSettings(player.getUniqueId());

        if (settings.keepInventory()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    private boolean isPlayerSourcedDamage(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();

        // Direct player damage
        if (damager instanceof Player) {
            return true;
        }

        // Projectile shot by a player (arrows, tridents, etc.)
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Player;
        }

        return false;
    }

    private void handleIncomingDamage(EntityDamageByEntityEvent event) {
        Player victim = (Player) event.getEntity();
        PlayerSettings settings = getSettings(victim.getUniqueId());

        if (settings.easyMode()) {
            // Reduce damage to 25%
            event.setDamage(event.getDamage() * 0.25);
        }
    }

    private void handleOutgoingDamage(EntityDamageByEntityEvent event) {
        Player attacker = (Player) event.getDamager();
        PlayerSettings settings = getSettings(attacker.getUniqueId());

        if (settings.easyMode() && random.nextDouble() < 0.05) {
            LivingEntity mob = (LivingEntity) event.getEntity();

            // Schedule insta-kill for next tick to let damage apply first
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!mob.isDead()) {
                    mob.setHealth(0);

                    // Particle effects
                    Location loc = mob.getLocation().add(0, 0.5, 0);
                    loc.getWorld().spawnParticle(Particle.HEART, loc, 8, 0.5, 0.5, 0.5, 0.1);

                    // Sound effect
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

                    // Cute message via action bar
                    String message = INSTA_KILL_MESSAGES[random.nextInt(INSTA_KILL_MESSAGES.length)];
                    attacker.sendActionBar(Component.text(message, NamedTextColor.LIGHT_PURPLE));
                }
            });
        }
    }

    private void handlePvpDamage(EntityDamageByEntityEvent event) {
        Player victim = (Player) event.getEntity();
        Player attacker = getAttackingPlayer(event);

        if (attacker == null) {
            return;
        }

        PlayerSettings victimSettings = getSettings(victim.getUniqueId());
        PlayerSettings attackerSettings = getSettings(attacker.getUniqueId());

        // Cancel damage if either player has passive mode enabled
        if (victimSettings.passiveMode() || attackerSettings.passiveMode()) {
            event.setCancelled(true);
        }
    }

    /**
     * Gets the attacking player from a damage event.
     * Handles both direct attacks and projectile attacks (arrows, tridents, etc).
     */
    private Player getAttackingPlayer(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();

        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }

    // === Public API ===

    /**
     * Gets settings for a player. Returns defaults if not in cache.
     * This is a fast, synchronous operation.
     */
    public PlayerSettings getSettings(UUID playerId) {
        return cache.getOrDefault(playerId, PlayerSettings.DEFAULTS);
    }

    /**
     * Sets the keep inventory setting for a player.
     */
    public void setKeepInventory(UUID playerId, boolean enabled) {
        PlayerSettings current = getSettings(playerId);
        PlayerSettings updated = current.withKeepInventory(enabled);
        cache.put(playerId, updated);

        // Persist async
        storage.saveSettings(playerId, updated)
                .subscribe(
                        () -> {},
                        err -> logger.warning("[Settings] Failed to save settings for " + playerId + ": " + err.getMessage())
                );
    }

    /**
     * Sets the display time setting for a player.
     */
    public void setDisplayTime(UUID playerId, DisplayTimeSetting setting) {
        PlayerSettings current = getSettings(playerId);
        PlayerSettings updated = current.withDisplayTime(setting);
        cache.put(playerId, updated);

        // Persist async
        storage.saveSettings(playerId, updated)
                .subscribe(
                        () -> {},
                        err -> logger.warning("[Settings] Failed to save settings for " + playerId + ": " + err.getMessage())
                );
    }

    /**
     * Sets the easy mode setting for a player.
     */
    public void setEasyMode(UUID playerId, boolean enabled) {
        PlayerSettings current = getSettings(playerId);
        PlayerSettings updated = current.withEasyMode(enabled);
        cache.put(playerId, updated);

        // Persist async
        storage.saveSettings(playerId, updated)
                .subscribe(
                        () -> {},
                        err -> logger.warning("[Settings] Failed to save settings for " + playerId + ": " + err.getMessage())
                );
    }

    /**
     * Sets the passive mode setting for a player.
     */
    public void setPassiveMode(UUID playerId, boolean enabled) {
        PlayerSettings current = getSettings(playerId);
        PlayerSettings updated = current.withPassiveMode(enabled);
        cache.put(playerId, updated);

        // Persist async
        storage.saveSettings(playerId, updated)
                .subscribe(
                        () -> {},
                        err -> logger.warning("[Settings] Failed to save settings for " + playerId + ": " + err.getMessage())
                );
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
