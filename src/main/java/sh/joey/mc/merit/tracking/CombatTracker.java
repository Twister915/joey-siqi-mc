package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Tracks combat for PvP and PvE challenges.
 */
public final class CombatTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public CombatTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        // Track kills
        disposables.add(plugin.watchEvent(EntityDeathEvent.class)
                .subscribe(event -> {
                    LivingEntity victim = event.getEntity();
                    Player killer = victim.getKiller();

                    if (killer == null) {
                        return;
                    }

                    String entityType = victim.getType().name();

                    // PvP kill
                    if (victim instanceof Player) {
                        progressTracker.increment(killer.getUniqueId(), "pvp_kills");

                        // Track by weapon type
                        String weaponType = getWeaponType(killer.getInventory().getItemInMainHand());
                        if (weaponType != null) {
                            progressTracker.increment(killer.getUniqueId(), "pvp_kills:" + weaponType);
                        }
                    } else {
                        // PvE kill
                        progressTracker.increment(killer.getUniqueId(), "kills:" + entityType);
                    }
                }));

        // Track damage dealt
        disposables.add(plugin.watchEvent(EntityDamageByEntityEvent.class)
                .subscribe(event -> {
                    Player damager = getDamager(event.getDamager());
                    if (damager == null) {
                        return;
                    }

                    Entity victim = event.getEntity();
                    double damage = event.getFinalDamage();
                    int damageInt = (int) Math.ceil(damage);

                    if (damageInt > 0) {
                        if (victim instanceof Player) {
                            progressTracker.increment(damager.getUniqueId(), "damage_dealt:PLAYER", damageInt);
                        } else {
                            progressTracker.increment(damager.getUniqueId(), "damage_dealt:MOB", damageInt);
                        }
                    }
                }));
    }

    private Player getDamager(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private String getWeaponType(ItemStack item) {
        if (item == null) {
            return null;
        }

        Material material = item.getType();
        String name = material.name();

        if (name.endsWith("_SWORD")) {
            return "SWORD";
        }
        if (name.endsWith("_AXE")) {
            return "AXE";
        }
        if (material == Material.BOW) {
            return "BOW";
        }
        if (material == Material.CROSSBOW) {
            return "CROSSBOW";
        }
        if (material == Material.TRIDENT) {
            return "TRIDENT";
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
