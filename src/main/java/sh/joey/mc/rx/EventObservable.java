package sh.joey.mc.rx;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An Observable that emits Bukkit events.
 * <p>
 * Safety guarantees:
 * <ul>
 *   <li>Event registration always happens on the main thread</li>
 *   <li>Observers receive onComplete when the plugin disables</li>
 *   <li>Exceptions in observers are caught and routed to RxJavaPlugins.onError</li>
 *   <li>No events are emitted after dispose or completion</li>
 * </ul>
 */
public final class EventObservable<T extends Event> extends Observable<T> {

    private final Set<Class<? extends T>> eventType;
    private final Plugin plugin;
    private final EventPriority priority;
    private final boolean ignoreCancelled;

    public EventObservable(Set<Class<? extends T>> eventType, Plugin plugin, EventPriority priority, boolean ignoreCancelled) {
        this.eventType = eventType;
        this.plugin = plugin;
        this.priority = priority != null ? priority : EventPriority.NORMAL;
        this.ignoreCancelled = ignoreCancelled;
    }

    @Override
    protected void subscribeActual(@NonNull Observer<? super T> observer) {
        EventSubscription<T> subscription = new EventSubscription<>(
                observer, eventType, plugin, priority, ignoreCancelled
        );

        // Must call onSubscribe first per RxJava contract
        observer.onSubscribe(subscription);

        // Register event listener on main thread
        if (Bukkit.isPrimaryThread()) {
            subscription.register();
        } else {
            Bukkit.getScheduler().runTask(plugin, subscription::register);
        }
    }

    /**
     * Manages the lifecycle of a single event subscription.
     */
    private static final class EventSubscription<T extends Event> implements Disposable, Listener {
        private final Observer<? super T> downstream;
        private final Set<Class<? extends T>> types;
        private final Plugin plugin;
        private final EventPriority priority;
        private final boolean ignoreCancelled;

        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private final AtomicBoolean registered = new AtomicBoolean(false);

        EventSubscription(Observer<? super T> downstream, Set<Class<? extends T>> types,
                          Plugin plugin, EventPriority priority, boolean ignoreCancelled) {
            this.downstream = downstream;
            this.types = types;
            this.plugin = plugin;
            this.priority = priority;
            this.ignoreCancelled = ignoreCancelled;
        }

        void register() {
            if (disposed.get() || !registered.compareAndSet(false, true)) {
                return;
            }

            // Register shutdown listener for this subscription
            plugin.getServer().getPluginManager().registerEvent(
                    PluginDisableEvent.class,
                    this,
                    EventPriority.MONITOR,
                    (listener, event) -> {
                        if (((PluginDisableEvent) event).getPlugin() == plugin) {
                            complete();
                        }
                    },
                    plugin,
                    false
            );

            for (Class<? extends T> type : types) {
                // Register the actual event listener
                plugin.getServer().getPluginManager().registerEvent(
                        type,
                        this,
                        priority,
                        this::handleEvent,
                        plugin,
                        ignoreCancelled
                );
            }
        }

        @SuppressWarnings("unchecked")
        private void handleEvent(Listener listener, Event event) {
            if (disposed.get()) {
                return;
            }

            Class<? extends Event> eventType = event.getClass();
            for (Class<? extends T> type : types) {
                if (type == eventType || type.isAssignableFrom(eventType)) {
                    try {
                        downstream.onNext((T) event);
                    } catch (Throwable t) {
                        dispose();
                        try {
                            downstream.onError(t);
                        } catch (Throwable inner) {
                            RxJavaPlugins.onError(inner);
                        }
                    }
                    return;
                }
            }
        }

        /**
         * Completes this subscription (called on plugin disable).
         */
        void complete() {
            if (disposed.compareAndSet(false, true)) {
                unregister();
                try {
                    downstream.onComplete();
                } catch (Throwable t) {
                    RxJavaPlugins.onError(t);
                }
            }
        }

        @Override
        public void dispose() {
            if (disposed.compareAndSet(false, true)) {
                unregister();
            }
        }

        private void unregister() {
            if (registered.get()) {
                HandlerList.unregisterAll(this);
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
