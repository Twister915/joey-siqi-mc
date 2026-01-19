package sh.joey.mc.merit;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.merit.challenge.ChallengeAssigner;
import sh.joey.mc.merit.challenge.ChallengeRegistry;
import sh.joey.mc.merit.tracking.*;
import sh.joey.mc.storage.StorageService;

/**
 * Central manager for the merit system.
 * Coordinates all components and provides the public API.
 */
public final class MeritManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final MeritConfig config;
    private final MeritStorage storage;
    private final LevelCalculator levelCalculator;
    private final ChallengeRegistry registry;
    private final ChallengeAssigner assigner;
    private final MeritBossBarProvider bossBarProvider;
    private final ProgressTracker progressTracker;
    private final OnlineTimeTracker onlineTimeTracker;

    public MeritManager(SiqiJoeyPlugin plugin, StorageService storageService, MeritConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.storage = new MeritStorage(storageService);
        this.levelCalculator = new LevelCalculator(config);
        this.registry = new ChallengeRegistry();
        this.assigner = new ChallengeAssigner(registry, config.weeklyChallengeCount());
        this.bossBarProvider = new MeritBossBarProvider();

        // Create progress tracker
        this.progressTracker = new ProgressTracker(plugin, storage, bossBarProvider, assigner, levelCalculator, config);
        disposables.add(progressTracker);

        // Create all trackers
        disposables.add(new MiningTracker(plugin, progressTracker));
        disposables.add(new FarmingTracker(plugin, progressTracker));
        disposables.add(new BuildingTracker(plugin, progressTracker));
        disposables.add(new CombatTracker(plugin, progressTracker));
        disposables.add(new CraftingTracker(plugin, progressTracker));
        disposables.add(new SmeltingTracker(plugin, progressTracker));
        disposables.add(new ExplorationTracker(plugin, progressTracker));
        disposables.add(new ProgressionTracker(plugin, progressTracker));
        this.onlineTimeTracker = new OnlineTimeTracker(plugin, storage, progressTracker, bossBarProvider, assigner, config);
        disposables.add(onlineTimeTracker);
        disposables.add(new TimeTracker(plugin, progressTracker));

        // Load data for players on join
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> progressTracker.loadPlayer(event.getPlayer().getUniqueId())));

        // Unload data on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> progressTracker.unloadPlayer(event.getPlayer().getUniqueId())));

        // Load data for already online players
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            progressTracker.loadPlayer(player.getUniqueId());
        }
    }

    /**
     * Get the boss bar provider for registration with BossBarManager.
     */
    public MeritBossBarProvider getBossBarProvider() {
        return bossBarProvider;
    }

    /**
     * Get the level prefix component for a player.
     */
    public Component getLevelPrefix(Player player) {
        int level = progressTracker.getCachedLevel(player.getUniqueId());
        return Messages.getLevelPrefix(level);
    }

    /**
     * Get the cached level for a player.
     */
    public int getLevel(Player player) {
        return progressTracker.getCachedLevel(player.getUniqueId());
    }

    /**
     * Get the storage layer.
     */
    public MeritStorage getStorage() {
        return storage;
    }

    /**
     * Get the level calculator.
     */
    public LevelCalculator getLevelCalculator() {
        return levelCalculator;
    }

    /**
     * Get the challenge registry.
     */
    public ChallengeRegistry getRegistry() {
        return registry;
    }

    /**
     * Get the challenge assigner.
     */
    public ChallengeAssigner getAssigner() {
        return assigner;
    }

    /**
     * Get the progress tracker.
     */
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    /**
     * Get the configuration.
     */
    public MeritConfig getConfig() {
        return config;
    }

    /**
     * Get the current session seconds for a player (not yet flushed to DB).
     */
    public long getCurrentSessionSeconds(java.util.UUID playerId) {
        return onlineTimeTracker.getCurrentSessionSeconds(playerId);
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
