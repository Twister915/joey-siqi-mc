package sh.joey.mc.antitroll;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.MushroomCow;
import org.bukkit.event.player.PlayerShearEntityEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Applies restrictions to players with the smp.antitroll permission.
 */
public final class AntiTrollManager implements Disposable {

    private static final String PERMISSION = "smp.antitroll";

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("!").color(NamedTextColor.RED))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final CompositeDisposable disposables = new CompositeDisposable();

    public AntiTrollManager(SiqiJoeyPlugin plugin) {
        // Prevent shearing named mooshrooms
        disposables.add(plugin.watchEvent(PlayerShearEntityEvent.class)
                .filter(event -> event.getPlayer().hasPermission(PERMISSION))
                .filter(event -> event.getEntity() instanceof MushroomCow)
                .filter(event -> event.getEntity().customName() != null)
                .subscribe(event -> {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(PREFIX.append(
                            Component.text("You cannot shear named mooshrooms.").color(NamedTextColor.RED)));
                }));
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
