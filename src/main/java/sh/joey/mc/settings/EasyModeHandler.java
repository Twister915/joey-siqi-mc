package sh.joey.mc.settings;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Boss;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Random;

/**
 * Handles easy mode gameplay effects:
 * - 75% damage reduction from mobs
 * - 5% chance to insta-kill hostile mobs (excludes bosses and passive mobs)
 */
public final class EasyModeHandler implements Disposable {

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
    private final SettingsManager settings;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Random random = new Random();

    public EasyModeHandler(SiqiJoeyPlugin plugin, SettingsManager settings) {
        this.plugin = plugin;
        this.settings = settings;

        // Damage reduction (mobs dealing damage to players)
        disposables.add(plugin.watchEvent(EntityDamageByEntityEvent.class)
                .filter(e -> e.getEntity() instanceof Player)
                .filter(e -> !isPlayerSourcedDamage(e))
                .subscribe(this::handleIncomingDamage));

        // Insta-kill chance (player dealing damage to mobs)
        disposables.add(plugin.watchEvent(EntityDamageByEntityEvent.class)
                .filter(e -> e.getDamager() instanceof Player)
                .filter(e -> e.getEntity() instanceof LivingEntity)
                .filter(e -> !(e.getEntity() instanceof Player))
                .subscribe(this::handleOutgoingDamage));
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

    private void handleIncomingDamage(EntityDamageByEntityEvent event) {
        Player victim = (Player) event.getEntity();
        PlayerSettings playerSettings = settings.getSettings(victim.getUniqueId());

        if (playerSettings.easyMode()) {
            event.setDamage(event.getDamage() * 0.25);
        }
    }

    private void handleOutgoingDamage(EntityDamageByEntityEvent event) {
        Player attacker = (Player) event.getDamager();
        LivingEntity mob = (LivingEntity) event.getEntity();

        // Only apply to hostile mobs, exclude bosses and passive mobs
        if (!(mob instanceof Monster) || mob instanceof Boss || mob instanceof Warden) {
            return;
        }

        PlayerSettings playerSettings = settings.getSettings(attacker.getUniqueId());

        if (playerSettings.easyMode() && random.nextDouble() < 0.05) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!mob.isDead()) {
                    mob.setHealth(0);

                    Location loc = mob.getLocation().add(0, 0.5, 0);
                    loc.getWorld().spawnParticle(Particle.HEART, loc, 8, 0.5, 0.5, 0.5, 0.1);
                    loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

                    String message = INSTA_KILL_MESSAGES[random.nextInt(INSTA_KILL_MESSAGES.length)];
                    attacker.sendActionBar(Component.text(message, NamedTextColor.LIGHT_PURPLE));
                }
            });
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
