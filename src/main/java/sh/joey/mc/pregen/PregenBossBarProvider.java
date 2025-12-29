package sh.joey.mc.pregen;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.bossbar.BossBarProvider;
import sh.joey.mc.bossbar.BossBarState;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Boss bar provider for admins monitoring chunk pre-generation progress.
 * Activated per-player via /pregen monitor toggle.
 */
public final class PregenBossBarProvider implements BossBarProvider, Disposable {

    private static final int PRIORITY = 175;  // Above biome (150), below teleport (200)

    private final PregenManager manager;
    private final Set<UUID> monitoringPlayers = new HashSet<>();
    private final CompositeDisposable disposables = new CompositeDisposable();

    public PregenBossBarProvider(SiqiJoeyPlugin plugin, PregenManager manager) {
        this.manager = manager;

        // Clean up when players quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> monitoringPlayers.remove(event.getPlayer().getUniqueId())));
    }

    /**
     * Toggles monitoring for a player.
     * @return true if monitoring is now enabled, false if disabled
     */
    public boolean toggleMonitoring(UUID playerId) {
        if (monitoringPlayers.contains(playerId)) {
            monitoringPlayers.remove(playerId);
            return false;
        } else {
            monitoringPlayers.add(playerId);
            return true;
        }
    }

    public boolean isMonitoring(UUID playerId) {
        return monitoringPlayers.contains(playerId);
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public Optional<BossBarState> getState(Player player) {
        if (!monitoringPlayers.contains(player.getUniqueId())) {
            return Optional.empty();
        }

        PregenManager.State state = manager.getState();
        String currentWorld = manager.getCurrentWorld();
        Map<String, PregenManager.WorldProgress> progress = manager.getWorldProgress();

        // Build title based on state
        String title;
        BarColor color;
        float progressValue;

        switch (state) {
            case RUNNING -> {
                if (currentWorld != null && progress.containsKey(currentWorld)) {
                    PregenManager.WorldProgress wp = progress.get(currentWorld);
                    String modeIndicator = manager.isForced() ? " [FORCED]" : "";
                    title = String.format("§6[Pregen]§r %s%s - %.1f%% (%,d/%,d)",
                            currentWorld, modeIndicator,
                            wp.getProgressPercent(),
                            wp.getProcessedChunks(),
                            wp.totalChunks());
                    progressValue = (float) (wp.getProgressPercent() / 100.0);
                    color = manager.isForced() ? BarColor.PURPLE : BarColor.GREEN;
                } else {
                    title = "§6[Pregen]§r Running...";
                    progressValue = 0f;
                    color = BarColor.GREEN;
                }
            }
            case PAUSED -> {
                title = "§6[Pregen]§r §ePaused";
                progressValue = getCurrentOverallProgress(progress);
                color = BarColor.YELLOW;
            }
            case WAITING -> {
                title = "§6[Pregen]§r §eWaiting (players online)";
                progressValue = getCurrentOverallProgress(progress);
                color = BarColor.YELLOW;
            }
            case IDLE -> {
                if (progress.isEmpty()) {
                    title = "§6[Pregen]§r §7Idle (not configured)";
                    progressValue = 0f;
                } else {
                    // Check if all complete
                    boolean allComplete = progress.values().stream().allMatch(PregenManager.WorldProgress::complete);
                    if (allComplete) {
                        title = "§6[Pregen]§r §aComplete!";
                        progressValue = 1f;
                    } else {
                        title = "§6[Pregen]§r §7Idle";
                        progressValue = getCurrentOverallProgress(progress);
                    }
                }
                color = BarColor.WHITE;
            }
            default -> {
                return Optional.empty();
            }
        }

        return Optional.of(new BossBarState(title, color, Math.max(0f, Math.min(1f, progressValue)), BarStyle.SOLID));
    }

    private float getCurrentOverallProgress(Map<String, PregenManager.WorldProgress> progress) {
        if (progress.isEmpty()) return 0f;

        long totalChunks = 0;
        long processedChunks = 0;
        for (PregenManager.WorldProgress wp : progress.values()) {
            totalChunks += wp.totalChunks();
            processedChunks += wp.getProcessedChunks();
        }

        if (totalChunks == 0) return 0f;
        return (float) processedChunks / totalChunks;
    }
}
