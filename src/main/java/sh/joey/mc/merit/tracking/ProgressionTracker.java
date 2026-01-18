package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks XP, enchanting, brewing, and other progression activities.
 */
public final class ProgressionTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, Integer> lastLevels = new ConcurrentHashMap<>();

    public ProgressionTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        // Track XP gained
        disposables.add(plugin.watchEvent(PlayerExpChangeEvent.class)
                .subscribe(event -> {
                    int xp = event.getAmount();
                    if (xp > 0) {
                        progressTracker.increment(event.getPlayer().getUniqueId(), "xp_gained", xp);

                        // Track levels gained
                        Player player = event.getPlayer();
                        UUID playerId = player.getUniqueId();
                        int currentLevel = player.getLevel();
                        int lastLevel = lastLevels.getOrDefault(playerId, currentLevel);

                        if (currentLevel > lastLevel) {
                            progressTracker.increment(playerId, "levels_gained", currentLevel - lastLevel);
                        }
                        lastLevels.put(playerId, currentLevel);
                    }
                }));

        // Track enchanting
        disposables.add(plugin.watchEvent(EnchantItemEvent.class)
                .subscribe(event -> {
                    UUID playerId = event.getEnchanter().getUniqueId();
                    progressTracker.increment(playerId, "items_enchanted");

                    // Track level 30 enchants
                    if (event.getExpLevelCost() >= 30) {
                        progressTracker.increment(playerId, "enchants_level_30");
                    }

                    // Track enchanted books
                    if (event.getItem().getType() == Material.BOOK) {
                        progressTracker.increment(playerId, "enchanted_books_created");
                    }
                }));

        // Track brewing (potions brewed)
        disposables.add(plugin.watchEvent(BrewEvent.class)
                .subscribe(event -> {
                    // BrewEvent doesn't have a player - we track via FuelHolder
                    // For simplicity, we skip player tracking here
                    // A more complex approach would track who fuels the brewing stand
                }));

        // Track advancements
        disposables.add(plugin.watchEvent(PlayerAdvancementDoneEvent.class)
                .subscribe(event -> {
                    // Skip recipe unlocks (they start with "minecraft:recipes/")
                    String key = event.getAdvancement().getKey().toString();
                    if (!key.contains("recipes/")) {
                        progressTracker.increment(event.getPlayer().getUniqueId(), "advancements_earned");
                    }
                }));

        // Track anvil and smithing table usage
        disposables.add(plugin.watchEvent(InventoryClickEvent.class)
                .subscribe(event -> {
                    if (!(event.getWhoClicked() instanceof Player player)) {
                        return;
                    }

                    InventoryType type = event.getInventory().getType();

                    // Anvil usage - check if result slot clicked with item
                    if (type == InventoryType.ANVIL && event.getSlot() == 2) {
                        ItemStack result = event.getCurrentItem();
                        if (result != null && result.getType() != Material.AIR) {
                            progressTracker.increment(player.getUniqueId(), "anvil_uses");
                            // Check if it's a repair
                            ItemStack input = event.getInventory().getItem(0);
                            if (input != null && input.getType() == result.getType()) {
                                progressTracker.increment(player.getUniqueId(), "items_repaired");
                            }
                        }
                    }

                    // Smithing table usage
                    if (type == InventoryType.SMITHING && event.getSlot() == 3) {
                        ItemStack result = event.getCurrentItem();
                        if (result != null && result.getType() != Material.AIR) {
                            progressTracker.increment(player.getUniqueId(), "smithing_uses");
                        }
                    }

                    // Grindstone usage
                    if (type == InventoryType.GRINDSTONE && event.getSlot() == 2) {
                        ItemStack result = event.getCurrentItem();
                        if (result != null && result.getType() != Material.AIR) {
                            progressTracker.increment(player.getUniqueId(), "grindstone_uses");
                        }
                    }
                }));
    }

    @Override
    public void dispose() {
        disposables.dispose();
        lastLevels.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
