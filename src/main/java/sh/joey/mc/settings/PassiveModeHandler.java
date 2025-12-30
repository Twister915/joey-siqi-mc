package sh.joey.mc.settings;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Handles passive mode - disables PvP for players who have it enabled.
 * When passive mode is on, the player cannot deal or receive damage from other players.
 */
public final class PassiveModeHandler implements Disposable {

    private final SettingsManager settings;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public PassiveModeHandler(SiqiJoeyPlugin plugin, SettingsManager settings) {
        this.settings = settings;

        // Cancel PvP damage if either player has passive mode
        disposables.add(plugin.watchEvent(EntityDamageByEntityEvent.class)
                .filter(e -> e.getEntity() instanceof Player)
                .filter(this::isPlayerSourcedDamage)
                .subscribe(this::handlePvpDamage));
    }

    private boolean isPlayerSourcedDamage(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();

        if (damager instanceof Player) {
            return true;
        }

        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Player;
        }

        return false;
    }

    private void handlePvpDamage(EntityDamageByEntityEvent event) {
        Player victim = (Player) event.getEntity();
        Player attacker = getAttackingPlayer(event);

        if (attacker == null) {
            return;
        }

        PlayerSettings victimSettings = settings.getSettings(victim.getUniqueId());
        PlayerSettings attackerSettings = settings.getSettings(attacker.getUniqueId());

        if (victimSettings.passiveMode() || attackerSettings.passiveMode()) {
            event.setCancelled(true);
        }
    }

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

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
