package sh.joey.mc.multiworld;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Blocks advancements in worlds with disable_advancements enabled.
 * Immediately revokes any advancement earned in those worlds.
 */
public final class AdvancementBlocker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final WorldManager worldManager;

    public AdvancementBlocker(SiqiJoeyPlugin plugin, WorldManager worldManager) {
        this.worldManager = worldManager;

        disposables.add(plugin.watchEvent(PlayerAdvancementDoneEvent.class)
                .subscribe(this::handleAdvancement));
    }

    private void handleAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();

        worldManager.getConfig(player.getWorld()).ifPresent(config -> {
            if (config.disableAdvancements()) {
                revokeAdvancement(player, event.getAdvancement());
            }
        });
    }

    private void revokeAdvancement(Player player, Advancement advancement) {
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        for (String criteria : progress.getAwardedCriteria()) {
            progress.revokeCriteria(criteria);
        }
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
