package sh.joey.mc.inventory;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of a player's inventory and state at a point in time.
 * This record is decoupled from any specific use case (multi-world, backups, etc.)
 * and simply captures/restores player state.
 */
public record InventorySnapshot(
        UUID id,
        UUID playerId,
        byte[] inventoryData,
        byte[] armorData,
        byte[] offhandData,
        byte[] enderChestData,
        int xpLevel,
        float xpProgress,
        double health,
        double maxHealth,
        int hunger,
        float saturation,
        List<EffectData> effects,
        Map<String, Object> labels,
        Instant snapshotAt
) {
    /**
     * Potion effect data for serialization.
     */
    public record EffectData(
            String id,
            int duration,
            int amplifier,
            boolean ambient,
            boolean particles,
            boolean icon
    ) {
        public static EffectData from(PotionEffect effect) {
            return new EffectData(
                    effect.getType().getKey().toString(),
                    effect.getDuration(),
                    effect.getAmplifier(),
                    effect.isAmbient(),
                    effect.hasParticles(),
                    effect.hasIcon()
            );
        }

        public PotionEffect toPotionEffect(int remainingDuration) {
            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.fromString(id));
            if (type == null) {
                return null;
            }
            return new PotionEffect(
                    type,
                    remainingDuration,
                    amplifier,
                    ambient,
                    particles,
                    icon
            );
        }
    }

    /**
     * Captures the current state of a player's inventory and stats.
     *
     * @param player the player to capture
     * @param labels arbitrary metadata to attach to the snapshot
     * @return a new InventorySnapshot
     */
    public static InventorySnapshot capture(Player player, Map<String, Object> labels) {
        PlayerInventory inv = player.getInventory();

        byte[] inventoryData = ItemStack.serializeItemsAsBytes(inv.getStorageContents());
        byte[] armorData = ItemStack.serializeItemsAsBytes(inv.getArmorContents());
        byte[] offhandData = ItemStack.serializeItemsAsBytes(new ItemStack[]{inv.getItemInOffHand()});
        byte[] enderChestData = ItemStack.serializeItemsAsBytes(player.getEnderChest().getContents());

        Collection<PotionEffect> activeEffects = player.getActivePotionEffects();
        List<EffectData> effects = activeEffects.stream()
                .map(EffectData::from)
                .toList();

        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();

        return new InventorySnapshot(
                UUID.randomUUID(),
                player.getUniqueId(),
                inventoryData,
                armorData,
                offhandData,
                enderChestData,
                player.getLevel(),
                player.getExp(),
                player.getHealth(),
                maxHealth,
                player.getFoodLevel(),
                player.getSaturation(),
                effects,
                labels,
                Instant.now()
        );
    }

    /**
     * Applies this snapshot to a player.
     *
     * @param player the player to apply to
     * @param decayEffects if true, subtract elapsed time from potion effect durations
     */
    public void applyTo(Player player, boolean decayEffects) {
        PlayerInventory inv = player.getInventory();

        // Apply inventory
        ItemStack[] inventory = ItemStack.deserializeItemsFromBytes(inventoryData);
        ItemStack[] armor = ItemStack.deserializeItemsFromBytes(armorData);
        ItemStack[] offhand = ItemStack.deserializeItemsFromBytes(offhandData);
        ItemStack[] enderChest = ItemStack.deserializeItemsFromBytes(enderChestData);

        inv.setStorageContents(inventory);
        inv.setArmorContents(armor);
        if (offhand.length > 0 && offhand[0] != null) {
            inv.setItemInOffHand(offhand[0]);
        } else {
            inv.setItemInOffHand(null);
        }
        player.getEnderChest().setContents(enderChest);

        // Apply XP
        player.setLevel(xpLevel);
        player.setExp(xpProgress);

        // Apply health (clamped to max)
        double playerMaxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(health, playerMaxHealth));

        // Apply hunger/saturation
        player.setFoodLevel(hunger);
        player.setSaturation(saturation);

        // Clear existing effects before applying snapshot effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        // Apply potion effects
        if (effects != null && !effects.isEmpty()) {
            int elapsedTicks = 0;
            if (decayEffects) {
                long elapsedMs = Duration.between(snapshotAt, Instant.now()).toMillis();
                elapsedTicks = (int) (elapsedMs / 50); // 50ms per tick
            }

            for (EffectData effect : effects) {
                int remainingDuration = effect.duration() - elapsedTicks;
                if (remainingDuration > 0) {
                    PotionEffect potionEffect = effect.toPotionEffect(remainingDuration);
                    if (potionEffect != null) {
                        player.addPotionEffect(potionEffect);
                    }
                }
            }
        }
    }

    /**
     * Clears a player's inventory and resets their state to fresh defaults.
     * Used when entering an inventory group for the first time.
     *
     * @param player the player to clear
     */
    public static void clearPlayer(Player player) {
        PlayerInventory inv = player.getInventory();

        // Clear inventory
        inv.clear();
        player.getEnderChest().clear();

        // Reset XP
        player.setLevel(0);
        player.setExp(0);

        // Reset health to max
        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(maxHealth);

        // Reset hunger/saturation
        player.setFoodLevel(20);
        player.setSaturation(5.0f);

        // Clear all potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }
}
