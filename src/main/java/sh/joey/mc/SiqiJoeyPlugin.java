package sh.joey.mc;

import org.bukkit.plugin.java.JavaPlugin;
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
import sh.joey.mc.teleport.LocationTracker;
import sh.joey.mc.teleport.PluginConfig;
import sh.joey.mc.teleport.RequestManager;
import sh.joey.mc.teleport.SafeTeleporter;
import sh.joey.mc.teleport.commands.BackCommand;
import sh.joey.mc.teleport.commands.TpCommand;
import sh.joey.mc.teleport.commands.YesNoCommands;

@SuppressWarnings("unused")
public final class SiqiJoeyPlugin extends JavaPlugin {

    private BossBarManager bossBarManager;

    @Override
    public void onEnable() {
        // Boss bar system with priority-based providers
        bossBarManager = new BossBarManager(this);
        bossBarManager.registerProvider(new TimeOfDayProvider());
        bossBarManager.registerProvider(new LodestoneCompassProvider());
        bossBarManager.registerProvider(new BiomeChangeProvider(this));
        bossBarManager.registerProvider(new WeatherChangeProvider(this));

        // Load config
        var config = PluginConfig.load(this);

        // Initialize teleport system components (they register their own listeners)
        var locationTracker = new LocationTracker(this);
        var safeTeleporter = new SafeTeleporter(this, config, locationTracker);
        var requestManager = new RequestManager(this, config, safeTeleporter);

        // Register teleport countdown provider (needs SafeTeleporter)
        bossBarManager.registerProvider(new TeleportCountdownProvider(safeTeleporter));

        // Register teleport commands
        var tpCommand = new TpCommand(this, requestManager);
        getCommand("back").setExecutor(new BackCommand(locationTracker, safeTeleporter));
        getCommand("tp").setExecutor(tpCommand);
        getCommand("tp").setTabCompleter(tpCommand);
        getCommand("accept").setExecutor(YesNoCommands.accept(requestManager));
        getCommand("decline").setExecutor(YesNoCommands.decline(requestManager));

        // Home system
        var homeStorage = new HomeStorage(this);
        var homeCommand = new HomeCommand(this, homeStorage, safeTeleporter);
        getCommand("home").setExecutor(homeCommand);
        getCommand("home").setTabCompleter(homeCommand);
        new BedHomeListener(this, homeStorage);

        // Day message system
        new DayMessageProvider(this);

        // Welcome message systems
        new JoinMessageProvider(this);
        new ServerPingProvider(this);

        getLogger().info("Plugin enabled!");
    }

    @Override
    public void onDisable() {
        bossBarManager.onDisable();
        bossBarManager = null;
    }
}
