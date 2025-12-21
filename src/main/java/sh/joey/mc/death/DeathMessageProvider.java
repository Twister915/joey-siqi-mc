package sh.joey.mc.death;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Replaces vanilla death messages with styled custom variants.
 * Uses the plugin's color scheme with humorous message variants.
 */
public final class DeathMessageProvider implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public DeathMessageProvider(SiqiJoeyPlugin plugin) {
        disposables.add(plugin.watchEvent(PlayerDeathEvent.class)
                .subscribe(this::handleDeath));
    }

    private void handleDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        EntityDamageEvent lastDamage = player.getLastDamageCause();

        if (lastDamage == null) {
            event.deathMessage(formatSimple(DeathMessages.getMessage(DamageCause.CUSTOM), player.getName()));
            return;
        }

        DamageCause cause = lastDamage.getCause();
        Component message = buildDeathMessage(player, lastDamage, cause);
        event.deathMessage(message);
    }

    private Component buildDeathMessage(Player player, EntityDamageEvent lastDamage, DamageCause cause) {
        String playerName = player.getName();

        // Check for entity involvement (PvP, mob kills, projectiles, explosions)
        if (lastDamage instanceof EntityDamageByEntityEvent entityEvent) {
            Entity damager = entityEvent.getDamager();
            return handleEntityDamage(playerName, damager, cause);
        }

        // Environmental death
        String template = DeathMessages.getMessage(cause);
        return formatSimple(template, playerName);
    }

    private Component handleEntityDamage(String playerName, Entity damager, DamageCause cause) {
        // Handle projectiles - get the shooter
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player killerPlayer) {
                return formatPvP(DeathMessages.getPvPMessage(), playerName, killerPlayer.getName());
            } else if (shooter instanceof LivingEntity killerEntity) {
                return formatKiller(DeathMessages.getProjectileMessage(), playerName, getEntityName(killerEntity));
            }
            // Projectile with no identifiable shooter
            return formatSimple(DeathMessages.getMessage(cause), playerName);
        }

        // Handle direct entity damage
        if (damager instanceof Player killerPlayer) {
            return formatPvP(DeathMessages.getPvPMessage(), playerName, killerPlayer.getName());
        }

        // Handle creepers specially
        if (damager instanceof Creeper) {
            return formatSimple(DeathMessages.getEntityMessage(null, true), playerName);
        }

        // Handle explosions
        if (cause == DamageCause.ENTITY_EXPLOSION || cause == DamageCause.BLOCK_EXPLOSION) {
            if (damager instanceof LivingEntity entity) {
                return formatKiller(DeathMessages.getExplosionMessage(), playerName, getEntityName(entity));
            }
            return formatSimple(DeathMessages.getMessage(cause), playerName);
        }

        // Handle sonic boom (Warden)
        if (cause == DamageCause.SONIC_BOOM) {
            return formatKiller(DeathMessages.getSonicBoomMessage(), playerName, getEntityName(damager));
        }

        // Handle thorns
        if (cause == DamageCause.THORNS) {
            if (damager instanceof LivingEntity entity) {
                return formatKiller(DeathMessages.getThornsMessage(), playerName, getEntityName(entity));
            }
        }

        // Generic entity attack
        if (damager instanceof LivingEntity entity) {
            String killerName = getEntityName(entity);
            return formatKiller(DeathMessages.getEntityMessage(killerName, false), playerName, killerName);
        }

        // Fallback
        return formatSimple(DeathMessages.getMessage(cause), playerName);
    }

    private String getEntityName(Entity entity) {
        // Use custom name if available, otherwise use the entity type name
        if (entity.customName() != null) {
            // Extract plain text from the component
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(entity.customName());
        }
        // Convert entity type to readable name (e.g., ZOMBIE -> Zombie)
        String typeName = entity.getType().name();
        return capitalize(typeName.toLowerCase().replace('_', ' '));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : str.toCharArray()) {
            if (c == ' ') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Format a simple death message with just the player name.
     */
    private Component formatSimple(String template, String playerName) {
        // Split on {player} and format
        String[] parts = template.split("\\{player}", 2);
        if (parts.length == 1) {
            // No placeholder, just return as-is
            return Component.text(template).color(NamedTextColor.DARK_GRAY);
        }

        Component result = Component.empty();
        if (!parts[0].isEmpty()) {
            result = result.append(Component.text(parts[0]).color(NamedTextColor.DARK_GRAY));
        }
        result = result.append(Component.text(playerName).color(NamedTextColor.GRAY));
        if (parts.length > 1 && !parts[1].isEmpty()) {
            result = result.append(Component.text(parts[1]).color(NamedTextColor.DARK_GRAY));
        }
        return result;
    }

    /**
     * Format a death message with player and killer names (mob/entity kills).
     */
    private Component formatKiller(String template, String playerName, String killerName) {
        // Handle both {player} and {killer} placeholders
        Component result = Component.empty();
        String remaining = template;

        while (!remaining.isEmpty()) {
            int playerIdx = remaining.indexOf("{player}");
            int killerIdx = remaining.indexOf("{killer}");

            if (playerIdx == -1 && killerIdx == -1) {
                // No more placeholders
                result = result.append(Component.text(remaining).color(NamedTextColor.DARK_GRAY));
                break;
            }

            // Find the first placeholder
            int nextIdx;
            boolean isPlayer;
            if (playerIdx == -1) {
                nextIdx = killerIdx;
                isPlayer = false;
            } else if (killerIdx == -1) {
                nextIdx = playerIdx;
                isPlayer = true;
            } else {
                isPlayer = playerIdx < killerIdx;
                nextIdx = isPlayer ? playerIdx : killerIdx;
            }

            // Add text before placeholder
            if (nextIdx > 0) {
                result = result.append(Component.text(remaining.substring(0, nextIdx)).color(NamedTextColor.DARK_GRAY));
            }

            // Add the name
            if (isPlayer) {
                result = result.append(Component.text(playerName).color(NamedTextColor.GRAY));
                remaining = remaining.substring(nextIdx + "{player}".length());
            } else {
                result = result.append(Component.text(killerName).color(NamedTextColor.RED));
                remaining = remaining.substring(nextIdx + "{killer}".length());
            }
        }

        return result;
    }

    /**
     * Format a PvP death message (same as killer but different color for player killers).
     */
    private Component formatPvP(String template, String playerName, String killerPlayerName) {
        // Same logic as formatKiller but killer is also a player
        return formatKiller(template, playerName, killerPlayerName);
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
