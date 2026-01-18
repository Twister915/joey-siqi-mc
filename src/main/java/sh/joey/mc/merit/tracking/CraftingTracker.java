package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Tracks crafting for crafting challenges.
 */
public final class CraftingTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public CraftingTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        disposables.add(plugin.watchEvent(true, CraftItemEvent.class)
                .subscribe(event -> {
                    if (!(event.getWhoClicked() instanceof Player player)) {
                        return;
                    }

                    ItemStack result = event.getRecipe().getResult();
                    String material = result.getType().name();
                    int amount = calculateCraftedAmount(event);

                    if (amount > 0) {
                        progressTracker.increment(player.getUniqueId(), "crafted:" + material, amount);
                        progressTracker.increment(player.getUniqueId(), "items_crafted", amount);
                    }
                }));
    }

    /**
     * Calculate actual amount crafted accounting for shift-click.
     */
    private int calculateCraftedAmount(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        int resultAmount = result.getAmount();

        if (event.isShiftClick()) {
            // Calculate how many times the recipe can be crafted
            int minStackSize = Integer.MAX_VALUE;
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (item != null && item.getAmount() > 0) {
                    minStackSize = Math.min(minStackSize, item.getAmount());
                }
            }
            if (minStackSize == Integer.MAX_VALUE) {
                minStackSize = 1;
            }
            return resultAmount * minStackSize;
        }

        return resultAmount;
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
