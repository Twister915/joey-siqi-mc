package sh.joey.mc;

import org.bukkit.plugin.java.JavaPlugin;

public final class SiqiJoeyPlugin extends JavaPlugin {

    private TimeOfDayBossBar timeBossBarFeature;

    @Override
    public void onEnable() {
        timeBossBarFeature = new TimeOfDayBossBar(this);
    }

    @Override
    public void onDisable() {
        timeBossBarFeature.onDisable();
        timeBossBarFeature = null;
    }
}
