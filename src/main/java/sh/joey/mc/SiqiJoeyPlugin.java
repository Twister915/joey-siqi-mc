package sh.joey.mc;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import sh.joey.mc.rx.EventObservable;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import sh.joey.mc.bossbar.BiomeChangeProvider;
import sh.joey.mc.bossbar.BossBarManager;
import sh.joey.mc.bossbar.LodestoneCompassProvider;
import sh.joey.mc.bossbar.TimeOfDayProvider;
import sh.joey.mc.bossbar.TeleportCountdownProvider;
import sh.joey.mc.bossbar.WeatherChangeProvider;
import sh.joey.mc.day.DayMessageProvider;
import sh.joey.mc.welcome.JoinMessageProvider;
import sh.joey.mc.welcome.ServerPingProvider;
import sh.joey.mc.home.BedHomeListener;
import sh.joey.mc.home.HomeCommand;
import sh.joey.mc.home.HomeStorage;
import sh.joey.mc.home.HomeTabCompleter;
import sh.joey.mc.session.PlayerSessionStorage;
import sh.joey.mc.session.PlayerSessionTracker;
import sh.joey.mc.storage.DatabaseConfig;
import sh.joey.mc.storage.DatabaseService;
import sh.joey.mc.storage.MigrationRunner;
import sh.joey.mc.storage.StorageService;
import sh.joey.mc.confirm.ConfirmationManager;
import sh.joey.mc.confirm.ConfirmCommands;
import sh.joey.mc.teleport.BackLocationStorage;
import sh.joey.mc.teleport.LocationTracker;
import sh.joey.mc.teleport.PluginConfig;
import sh.joey.mc.teleport.SafeTeleporter;
import sh.joey.mc.teleport.commands.BackCommand;
import sh.joey.mc.teleport.commands.TpCommand;
import sh.joey.mc.rx.BukkitSchedulers;
import sh.joey.mc.world.TimePassingMonitor;

@SuppressWarnings("unused")
public final class SiqiJoeyPlugin extends JavaPlugin {

    private BukkitSchedulers schedulers;
    private final CompositeDisposable components = new CompositeDisposable();

    @Override
    public void onEnable() {
        // Initialize RxJava schedulers first
        schedulers = new BukkitSchedulers(this);

        // Load database config and initialize
        var dbConfig = DatabaseConfig.load(this);
        var database = new DatabaseService(getLogger());
        database.initialize(dbConfig);
        components.add(database);

        // Run migrations (blocks until complete)
        var migrationRunner = new MigrationRunner(this, database);
        migrationRunner.run();

        // Create storage service
        var storageService = new StorageService(database);

        // Player session tracking (early - for player ID lookups)
        var playerSessionStorage = new PlayerSessionStorage(storageService);
        var playerSessionTracker = new PlayerSessionTracker(this, playerSessionStorage);
        components.add(playerSessionTracker);

        // Boss bar system with priority-based providers
        var bossBarManager = new BossBarManager(this);
        components.add(bossBarManager);
        bossBarManager.registerProvider(new TimeOfDayProvider());
        bossBarManager.registerProvider(new LodestoneCompassProvider());

        var biomeChangeProvider = new BiomeChangeProvider(this);
        components.add(biomeChangeProvider);
        bossBarManager.registerProvider(biomeChangeProvider);

        var weatherChangeProvider = new WeatherChangeProvider(this);
        components.add(weatherChangeProvider);
        bossBarManager.registerProvider(weatherChangeProvider);

        // Load teleport config
        var config = PluginConfig.load(this);

        // Confirmation system (early - other components depend on it)
        var confirmationManager = new ConfirmationManager(this);
        components.add(confirmationManager);

        // Initialize teleport system components
        var backLocationStorage = new BackLocationStorage(storageService);
        var locationTracker = new LocationTracker(this, backLocationStorage);
        components.add(locationTracker);

        var safeTeleporter = new SafeTeleporter(this, config, locationTracker, confirmationManager);
        components.add(safeTeleporter);

        // Register teleport countdown provider (needs SafeTeleporter)
        bossBarManager.registerProvider(new TeleportCountdownProvider(safeTeleporter));

        // Register teleport commands
        var tpCommand = new TpCommand(this, config, safeTeleporter, confirmationManager);
        getCommand("back").setExecutor(new BackCommand(this, locationTracker, safeTeleporter));
        getCommand("tp").setExecutor(tpCommand);
        getCommand("tp").setTabCompleter(tpCommand);
        getCommand("accept").setExecutor(ConfirmCommands.accept(confirmationManager));
        getCommand("decline").setExecutor(ConfirmCommands.decline(confirmationManager));

        // Home system (uses PostgreSQL)
        var homeStorage = new HomeStorage(storageService);
        var homeCommand = new HomeCommand(this, homeStorage, playerSessionStorage, safeTeleporter, confirmationManager);
        getCommand("home").setExecutor(homeCommand);

        var homeTabCompleter = new HomeTabCompleter(this, homeStorage, playerSessionStorage);
        components.add(homeTabCompleter);

        var bedHomeListener = new BedHomeListener(this, homeStorage);
        components.add(bedHomeListener);

        // Day message system
        var dayMessageProvider = new DayMessageProvider(this);
        components.add(dayMessageProvider);

        // Welcome message systems
        var joinMessageProvider = new JoinMessageProvider(this);
        components.add(joinMessageProvider);

        var serverPingProvider = new ServerPingProvider(this);
        components.add(serverPingProvider);

        // Monitor to verify time pauses when server is empty
        var timePassingMonitor = new TimePassingMonitor(this);
        components.add(timePassingMonitor);

        getLogger().info("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        components.dispose();
        schedulers.shutdown();
    }

    /**
     * Returns the RxJava Scheduler for Bukkit's main server thread.
     */
    public Scheduler mainScheduler() {
        return schedulers.mainThread();
    }

    /**
     * Returns the RxJava Scheduler for Bukkit's async thread pool.
     */
    public Scheduler asyncScheduler() {
        return schedulers.async();
    }

    /**
     * Creates an Observable that emits events of the specified types.
     * Uses default priority (NORMAL) and does not ignore cancelled events.
     */
    @SafeVarargs
    public final <T extends Event> Observable<T> watchEvent(Class<? extends T>... eventTypes) {
        return watchEvent(false, EventPriority.NORMAL, eventTypes);
    }

    /**
     * Creates an Observable that emits events of the specified types with the given priority.
     * Does not ignore cancelled events.
     */
    @SafeVarargs
    public final <T extends Event> Observable<T> watchEvent(EventPriority priority, Class<? extends T>... eventTypes) {
        return watchEvent(false, priority, eventTypes);
    }

    /**
     * Creates an Observable that emits events of the specified types.
     * Uses default priority (NORMAL).
     */
    @SafeVarargs
    public final <T extends Event> Observable<T> watchEvent(boolean ignoreCancelled, Class<? extends T>... eventTypes) {
        return watchEvent(ignoreCancelled, EventPriority.NORMAL, eventTypes);
    }

    /**
     * Creates an Observable that emits events of the specified types.
     *
     * @param ignoreCancelled if true, cancelled events will not be emitted
     * @param priority the event priority for the listener
     * @param eventTypes one or more event classes to listen for
     * @return an Observable that emits the specified event types
     */
    @SafeVarargs
    public final <T extends Event> Observable<T> watchEvent(boolean ignoreCancelled, EventPriority priority,
                                                            Class<? extends T>... eventTypes) {
        if (eventTypes.length == 0) {
            return Observable.empty();
        }

        return new EventObservable<>(Set.of(eventTypes), this, priority, ignoreCancelled);
    }

    /**
     * Creates an Observable that emits on the main thread at a fixed interval.
     * More efficient than Observable.interval().observeOn(mainScheduler()) as it
     * directly uses Bukkit's scheduler without thread switching overhead.
     *
     * @param period the period between emissions
     * @param unit the time unit
     * @return an Observable that emits Long values starting from 0
     */
    public Observable<Long> interval(long period, TimeUnit unit) {
        return interval(period, period, unit);
    }

    /**
     * Creates an Observable that emits on the main thread at a fixed interval.
     * More efficient than Observable.interval().observeOn(mainScheduler()) as it
     * directly uses Bukkit's scheduler without thread switching overhead.
     *
     * @param initialDelay the initial delay before first emission
     * @param period the period between subsequent emissions
     * @param unit the time unit
     * @return an Observable that emits Long values starting from 0
     */
    public Observable<Long> interval(long initialDelay, long period, TimeUnit unit) {
        return Observable.create(emitter -> {
            long[] count = {0};
            long initialTicks = toTicks(initialDelay, unit);
            long periodTicks = Math.max(1, toTicks(period, unit));

            BukkitTask task = getServer().getScheduler().runTaskTimer(this, () -> {
                if (!emitter.isDisposed()) {
                    emitter.onNext(count[0]++);
                }
            }, initialTicks, periodTicks);

            emitter.setCancellable(task::cancel);
        });
    }

    /**
     * Creates an Observable that emits once on the main thread after a delay.
     * More efficient than Observable.timer().observeOn(mainScheduler()) as it
     * directly uses Bukkit's scheduler without thread switching overhead.
     *
     * @param delay the delay before emission
     * @param unit the time unit
     * @return an Observable that emits 0L then completes
     */
    public Observable<Long> timer(long delay, TimeUnit unit) {
        return Observable.create(emitter -> {
            long ticks = toTicks(delay, unit);

            BukkitTask task = getServer().getScheduler().runTaskLater(this, () -> {
                if (!emitter.isDisposed()) {
                    emitter.onNext(0L);
                    emitter.onComplete();
                }
            }, ticks);

            emitter.setCancellable(task::cancel);
        });
    }

    private static long toTicks(long delay, TimeUnit unit) {
        long millis = unit.toMillis(delay);
        if (millis <= 0) return 0;
        return Math.max(1, (millis * 20L) / 1000);
    }
}
