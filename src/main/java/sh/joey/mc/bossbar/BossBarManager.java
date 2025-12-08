package sh.joey.mc.bossbar;

import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.*;

/**
 * Manages per-player boss bars with a priority-based provider system.
 * Each tick, providers are polled and the highest priority provider
 * with content determines what's shown.
 */
public final class BossBarManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final List<BossBarProvider> providers = new ArrayList<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    public BossBarManager(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Create boss bars for already-online players
        plugin.getServer().getOnlinePlayers().forEach(this::createBossBar);

        // Player join/quit events
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> createBossBar(event.getPlayer())));

        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    BossBar bar = playerBossBars.remove(event.getPlayer().getUniqueId());
                    if (bar != null) {
                        bar.removeAll();
                    }
                }));

        // Tick-based updates
        disposables.add(plugin.watchEvent(ServerTickStartEvent.class)
                .subscribe(event -> updateAllBossBars()));
    }

    /**
     * Registers a provider. Providers are automatically sorted by priority (highest first).
     */
    public void registerProvider(BossBarProvider provider) {
        providers.add(provider);
        providers.sort(Comparator.comparingInt(BossBarProvider::getPriority).reversed());
    }

    @Override
    public void dispose() {
        disposables.dispose();
        playerBossBars.values().forEach(BossBar::removeAll);
        playerBossBars.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private void updateAllBossBars() {
        for (var entry : playerBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }

            BossBar bossBar = entry.getValue();
            Optional<BossBarState> state = getHighestPriorityState(player);

            if (state.isPresent()) {
                updateBossBar(bossBar, state.get());
                bossBar.setVisible(true);
            } else {
                bossBar.setVisible(false);
            }
        }
    }

    private Optional<BossBarState> getHighestPriorityState(Player player) {
        for (BossBarProvider provider : providers) {
            try {
                Optional<BossBarState> state = provider.getState(player);
                if (state.isPresent()) {
                    return state;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("BossBarProvider " + provider.getClass().getSimpleName()
                        + " threw exception: " + e.getMessage());
            }
        }
        return Optional.empty();
    }

    private void createBossBar(Player player) {
        if (playerBossBars.containsKey(player.getUniqueId())) {
            return;
        }

        BossBar bossBar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
        bossBar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bossBar);
    }

    private static void updateBossBar(BossBar bossBar, BossBarState state) {
        bossBar.setTitle(state.title());
        bossBar.setColor(state.color());
        bossBar.setProgress(state.progress());
        bossBar.setStyle(state.style());
    }
}
