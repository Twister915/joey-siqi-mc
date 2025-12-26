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
import sh.joey.mc.day.DayMessageDebugCommand;
import sh.joey.mc.death.DeathMessageProvider;
import sh.joey.mc.welcome.ChatMessageProvider;
import sh.joey.mc.welcome.ConnectionMessageProvider;
import sh.joey.mc.welcome.JoinMessageProvider;
import sh.joey.mc.welcome.ServerPingProvider;
import sh.joey.mc.cmd.CmdExecutor;
import sh.joey.mc.home.BedHomeListener;
import sh.joey.mc.home.HomeCommand;
import sh.joey.mc.home.HomeStorage;
import sh.joey.mc.session.OnTimeCommand;
import sh.joey.mc.session.PlayerSessionStorage;
import sh.joey.mc.session.PlayerSessionTracker;
import sh.joey.mc.session.WhoisCommand;
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
import sh.joey.mc.teleport.commands.TpHereCommand;
import sh.joey.mc.rx.BukkitSchedulers;
import sh.joey.mc.world.TimePassingMonitor;
import sh.joey.mc.inventory.InventorySnapshotStorage;
import sh.joey.mc.multiworld.AdvancementBlocker;
import sh.joey.mc.multiworld.GamemodeManager;
import sh.joey.mc.multiworld.InventoryGroupManager;
import sh.joey.mc.multiworld.InventoryGroupStorage;
import sh.joey.mc.multiworld.PlayerLastWorldStorage;
import sh.joey.mc.multiworld.PlayerWorldPositionStorage;
import sh.joey.mc.multiworld.WorldCommand;
import sh.joey.mc.multiworld.WorldManager;
import sh.joey.mc.multiworld.WorldPositionTracker;
import sh.joey.mc.multiworld.WorldsConfig;
import sh.joey.mc.permissions.DisplayManager;
import sh.joey.mc.permissions.PermissionAttacher;
import sh.joey.mc.permissions.PermissionCache;
import sh.joey.mc.permissions.PermissionResolver;
import sh.joey.mc.permissions.PermissionStorage;
import sh.joey.mc.permissions.cmd.PermCommand;
import sh.joey.mc.utility.ClearCommand;
import sh.joey.mc.utility.GiveCommand;
import sh.joey.mc.utility.ItemCommand;
import sh.joey.mc.utility.ListCommand;
import sh.joey.mc.utility.MapCommand;
import sh.joey.mc.utility.MapConfig;
import sh.joey.mc.utility.RemoveCommand;
import sh.joey.mc.utility.SeedCommand;
import sh.joey.mc.utility.SetSpawnCommand;
import sh.joey.mc.utility.SpawnCommand;
import sh.joey.mc.utility.SpawnStorage;
import sh.joey.mc.utility.SuicideCommand;
import sh.joey.mc.utility.TimeCommand;
import sh.joey.mc.utility.WarpCommand;
import sh.joey.mc.utility.WarpStorage;
import sh.joey.mc.utility.WeatherCommand;
import sh.joey.mc.tips.TipsConfig;
import sh.joey.mc.tips.TipsProvider;
import sh.joey.mc.resourcepack.ResourcePackConfig;
import sh.joey.mc.resourcepack.ResourcePackStorage;
import sh.joey.mc.resourcepack.ResourcePackManager;
import sh.joey.mc.resourcepack.ResourcePackCommand;
import sh.joey.mc.multiworld.WorldAliasCommand;
import sh.joey.mc.tablist.TablistProvider;
import sh.joey.mc.bluemap.BlueMapIntegration;
import sh.joey.mc.nickname.NicknameManager;
import sh.joey.mc.nickname.NicknameStorage;
import sh.joey.mc.nickname.NicknameValidator;
import sh.joey.mc.nickname.NickCommand;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.msg.MessageConfig;
import sh.joey.mc.msg.MsgCommand;
import sh.joey.mc.msg.PrivateMessageManager;
import sh.joey.mc.msg.PrivateMessageStorage;
import sh.joey.mc.msg.ReplyCommand;
import sh.joey.mc.adminmode.AdminModeCommand;
import sh.joey.mc.adminmode.AdminModeManager;
import sh.joey.mc.adminmode.AdminModeStorage;

@SuppressWarnings("unused")
public final class SiqiJoeyPlugin extends JavaPlugin {

    private BukkitSchedulers schedulers;
    private DatabaseService database;
    private final CompositeDisposable components = new CompositeDisposable();

    @Override
    public void onEnable() {
        // Initialize RxJava schedulers first
        schedulers = new BukkitSchedulers(this);

        // Load database config and initialize (disposed separately in onDisable, after components)
        var dbConfig = DatabaseConfig.load(this);
        database = new DatabaseService(getLogger());
        database.initialize(dbConfig);

        // Run migrations (blocks until complete)
        var migrationRunner = new MigrationRunner(this, database);
        migrationRunner.run();

        // Create storage service
        var storageService = new StorageService(database);

        // Player session tracking (early - for player ID lookups)
        var playerSessionStorage = new PlayerSessionStorage(storageService);
        var playerSessionTracker = new PlayerSessionTracker(this, playerSessionStorage);
        components.add(playerSessionTracker);
        components.add(CmdExecutor.register(this,
                new OnTimeCommand(this, playerSessionStorage, playerSessionTracker)));

        // Nickname system (after session storage, before display systems)
        var nicknameStorage = new NicknameStorage(storageService);
        var nicknameValidator = new NicknameValidator(playerSessionStorage, nicknameStorage);
        var nicknameManager = new NicknameManager(this, nicknameStorage);
        components.add(nicknameManager);
        components.add(CmdExecutor.register(this,
                new NickCommand(this, playerSessionStorage, nicknameValidator, nicknameManager)));

        // Player resolver (central player lookup service)
        var playerResolver = new PlayerResolver(this, playerSessionStorage, nicknameManager, nicknameStorage);

        // Whois command (needs playerResolver, so after resolver init)
        components.add(CmdExecutor.register(this,
                new WhoisCommand(this, playerSessionStorage, nicknameManager, playerResolver)));

        // Private messaging system
        var messageConfig = MessageConfig.load(this);
        var privateMessageStorage = new PrivateMessageStorage(storageService);
        var privateMessageManager = new PrivateMessageManager(this, privateMessageStorage, messageConfig, playerResolver);
        components.add(privateMessageManager);
        components.add(CmdExecutor.register(this, new MsgCommand(this, playerResolver, privateMessageManager)));
        components.add(CmdExecutor.register(this, new ReplyCommand(this, privateMessageManager)));

        // Permission system
        var permissionStorage = new PermissionStorage(storageService);
        var permissionResolver = new PermissionResolver(permissionStorage);
        var permissionCache = new PermissionCache(this, permissionResolver);
        components.add(permissionCache);
        var permissionAttacher = new PermissionAttacher(this, permissionCache);
        components.add(permissionAttacher);
        var displayManager = new DisplayManager(this, permissionCache);
        components.add(displayManager);
        components.add(CmdExecutor.register(this,
                new PermCommand(this, permissionStorage, playerSessionStorage,
                        permissionCache, permissionAttacher, displayManager)));

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

        // World position storage (used by SafeTeleporter for cross-world position tracking)
        var playerWorldPositionStorage = new PlayerWorldPositionStorage(storageService);

        // Load worlds config early (SafeTeleporter needs it for instant teleport check)
        var worldsConfig = WorldsConfig.load(this);

        // Multi-world system (moved earlier - needed for admin mode)
        var inventorySnapshotStorage = new InventorySnapshotStorage(storageService);

        var worldManager = new WorldManager(this, worldsConfig);
        worldManager.loadWorlds();

        // Admin mode system
        var adminModeStorage = new AdminModeStorage(storageService);
        var adminModeManager = new AdminModeManager(this, adminModeStorage, inventorySnapshotStorage, worldManager);
        components.add(adminModeManager);

        var safeTeleporter = new SafeTeleporter(this, config, locationTracker, confirmationManager,
                playerWorldPositionStorage, worldsConfig, adminModeManager::isInAdminMode);
        components.add(safeTeleporter);

        // Register admin mode command
        components.add(CmdExecutor.register(this, new AdminModeCommand(adminModeManager)));

        // Register teleport countdown provider (needs SafeTeleporter)
        bossBarManager.registerProvider(new TeleportCountdownProvider(safeTeleporter));

        // Register commands using CmdExecutor
        components.add(CmdExecutor.register(this, new BackCommand(this, locationTracker, safeTeleporter)));
        components.add(CmdExecutor.register(this, new TpCommand(this, config, safeTeleporter, confirmationManager, playerResolver)));
        components.add(CmdExecutor.register(this, new TpHereCommand(this, config, safeTeleporter, confirmationManager, playerResolver)));
        components.add(CmdExecutor.register(this, ConfirmCommands.accept(confirmationManager)));
        components.add(CmdExecutor.register(this, ConfirmCommands.decline(confirmationManager)));

        // Home system (uses PostgreSQL)
        var homeStorage = new HomeStorage(storageService);
        components.add(CmdExecutor.register(this,
                new HomeCommand(this, homeStorage, playerSessionStorage, playerResolver,
                        safeTeleporter, confirmationManager)));

        var bedHomeListener = new BedHomeListener(this, homeStorage);
        components.add(bedHomeListener);

        // Day message system
        var dayMessageProvider = new DayMessageProvider(this, nicknameManager);
        components.add(dayMessageProvider);
        components.add(CmdExecutor.register(this, new DayMessageDebugCommand()));

        // Welcome message systems
        var connectionMessageProvider = new ConnectionMessageProvider(this, nicknameManager);
        components.add(connectionMessageProvider);

        var joinMessageProvider = new JoinMessageProvider(this, nicknameManager);
        components.add(joinMessageProvider);

        var serverPingProvider = new ServerPingProvider(this);
        components.add(serverPingProvider);

        var chatMessageProvider = new ChatMessageProvider(this, displayManager, nicknameManager);
        components.add(chatMessageProvider);

        // Death message system
        var deathMessageProvider = new DeathMessageProvider(this, nicknameManager);
        components.add(deathMessageProvider);

        // Monitor to verify time pauses when server is empty
        var timePassingMonitor = new TimePassingMonitor(this);
        components.add(timePassingMonitor);

        // Multi-world inventory and gamemode management
        var inventoryGroupStorage = new InventoryGroupStorage(storageService);
        var playerLastWorldStorage = new PlayerLastWorldStorage(storageService);

        var gamemodeManager = new GamemodeManager(this, worldManager, playerLastWorldStorage);
        components.add(gamemodeManager);

        var advancementBlocker = new AdvancementBlocker(this, worldManager);
        components.add(advancementBlocker);

        var inventoryGroupManager = new InventoryGroupManager(
                this, worldManager, inventorySnapshotStorage, inventoryGroupStorage,
                playerLastWorldStorage, playerWorldPositionStorage);
        components.add(inventoryGroupManager);

        var worldPositionTracker = new WorldPositionTracker(this, playerWorldPositionStorage);
        components.add(worldPositionTracker);

        var worldCommand = new WorldCommand(this, worldManager, safeTeleporter, playerWorldPositionStorage);
        components.add(CmdExecutor.register(this, worldCommand));

        // World alias commands
        components.add(CmdExecutor.register(this, new WorldAliasCommand("survival", "world", worldCommand)));
        components.add(CmdExecutor.register(this, new WorldAliasCommand("creative", "creative", worldCommand)));
        components.add(CmdExecutor.register(this, new WorldAliasCommand("superflat", "superflat", worldCommand)));

        // Map config (used by tips and map command)
        var mapConfig = MapConfig.load(this);

        // Tips system
        var tipsConfig = TipsConfig.load(this);
        var tipsProvider = new TipsProvider(this, tipsConfig, mapConfig);
        components.add(tipsProvider);

        // Tablist header/footer
        var tablistProvider = new TablistProvider(this);
        components.add(tablistProvider);

        // Utility commands
        components.add(CmdExecutor.register(this, new ClearCommand("clear", confirmationManager, playerResolver)));
        components.add(CmdExecutor.register(this, new ClearCommand("ci", confirmationManager, playerResolver)));
        components.add(CmdExecutor.register(this, new ItemCommand("item")));
        components.add(CmdExecutor.register(this, new ItemCommand("i")));
        components.add(CmdExecutor.register(this, new GiveCommand(playerResolver)));
        components.add(CmdExecutor.register(this, new TimeCommand()));
        components.add(CmdExecutor.register(this, new WeatherCommand()));
        components.add(CmdExecutor.register(this, new ListCommand(nicknameManager)));
        components.add(CmdExecutor.register(this, new MapCommand(mapConfig)));
        components.add(CmdExecutor.register(this, new SuicideCommand(confirmationManager)));
        components.add(CmdExecutor.register(this, new RemoveCommand()));
        components.add(CmdExecutor.register(this, new SeedCommand()));

        // Warp system
        var warpStorage = new WarpStorage(storageService);
        components.add(CmdExecutor.register(this, new WarpCommand(this, warpStorage, safeTeleporter)));

        // Spawn system
        var spawnStorage = new SpawnStorage(storageService);
        components.add(CmdExecutor.register(this, new SpawnCommand(this, spawnStorage, safeTeleporter)));
        components.add(CmdExecutor.register(this, new SetSpawnCommand(this, spawnStorage)));

        // BlueMap integration (optional - only loads if BlueMap plugin is present)
        if (getServer().getPluginManager().getPlugin("BlueMap") != null) {
            var blueMapIntegration = new BlueMapIntegration(this, warpStorage, spawnStorage);
            components.add(blueMapIntegration);
            getLogger().info("BlueMap detected, marker integration enabled");
        }

        // Resource pack system
        var resourcePackConfig = ResourcePackConfig.load(this);
        var resourcePackStorage = new ResourcePackStorage(storageService);
        var resourcePackManager = new ResourcePackManager(this, resourcePackConfig, resourcePackStorage);
        components.add(resourcePackManager);
        components.add(CmdExecutor.register(this,
                new ResourcePackCommand(this, resourcePackConfig, resourcePackStorage, resourcePackManager)));

        getLogger().info("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        components.dispose();
        database.dispose();
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
