package sh.joey.mc.rx;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RxJava Schedulers that integrate with Bukkit's scheduler system.
 * Provides main thread and async thread scheduling.
 */
public final class BukkitSchedulers {

    private static final long TICKS_PER_SECOND = 20L;

    private final BukkitScheduler mainThread;
    private final BukkitScheduler async;

    public BukkitSchedulers(Plugin plugin) {
        this.mainThread = new BukkitScheduler(plugin, true);
        this.async = new BukkitScheduler(plugin, false);
    }

    /**
     * Shuts down both schedulers. No new tasks will be accepted after this call.
     * Bukkit automatically cancels pending tasks when the plugin is disabled.
     */
    public void shutdown() {
        mainThread.shutdown();
        async.shutdown();
    }

    /**
     * Scheduler that executes work on Bukkit's main server thread.
     * If already on the main thread for immediate tasks, runs synchronously.
     */
    public Scheduler mainThread() {
        return mainThread;
    }

    /**
     * Scheduler that executes work on Bukkit's async thread pool.
     */
    public Scheduler async() {
        return async;
    }

    private static long toTicks(long delay, TimeUnit unit) {
        long millis = unit.toMillis(delay);
        if (millis <= 0) {
            return 0;
        }
        return Math.max(1, (millis * TICKS_PER_SECOND) / 1000);
    }

    private static final class BukkitScheduler extends Scheduler {
        private final Plugin plugin;
        private final boolean mainThread;
        private volatile boolean shutdown = false;

        BukkitScheduler(Plugin plugin, boolean mainThread) {
            this.plugin = plugin;
            this.mainThread = mainThread;
        }

        @Override
        public Worker createWorker() {
            if (shutdown) {
                // Return a worker that immediately rejects all work
                return new ShutdownWorker();
            }
            return new BukkitWorker(plugin, mainThread);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }
    }

    /**
     * Worker that rejects all work (used after shutdown).
     */
    private static final class ShutdownWorker extends Scheduler.Worker {
        @Override
        public Disposable schedule(Runnable run, long delay, TimeUnit unit) {
            return Disposable.disposed();
        }

        @Override
        public void dispose() {}

        @Override
        public boolean isDisposed() {
            return true;
        }
    }

    /**
     * Worker that schedules tasks via Bukkit's scheduler.
     * Tracks all scheduled tasks for best-effort cancellation on dispose.
     */
    private static final class BukkitWorker extends Scheduler.Worker {
        private final Plugin plugin;
        private final boolean mainThread;
        private final CompositeDisposable tasks = new CompositeDisposable();

        BukkitWorker(Plugin plugin, boolean mainThread) {
            this.plugin = plugin;
            this.mainThread = mainThread;
        }

        @Override
        public Disposable schedule(Runnable run, long delay, TimeUnit unit) {
            if (tasks.isDisposed()) {
                return Disposable.disposed();
            }

            long ticks = toTicks(delay, unit);

            // If no delay and already on main thread, run immediately
            if (mainThread && ticks == 0 && Bukkit.isPrimaryThread()) {
                run.run();
                return Disposable.disposed();
            }

            var taskDisposable = new TrackedBukkitTask(tasks);

            Runnable wrapped = () -> {
                try {
                    run.run();
                } finally {
                    tasks.delete(taskDisposable);
                }
            };

            BukkitTask task = (ticks == 0)
                    ? scheduleImmediate(wrapped)
                    : scheduleDelayed(wrapped, ticks);

            taskDisposable.setTask(task);
            tasks.add(taskDisposable);

            return taskDisposable;
        }

        @Override
        public Disposable schedulePeriodically(Runnable run, long initialDelay, long period, TimeUnit unit) {
            if (tasks.isDisposed()) {
                return Disposable.disposed();
            }

            long initialTicks = toTicks(initialDelay, unit);
            long periodTicks = Math.max(1, toTicks(period, unit));

            var taskDisposable = new TrackedBukkitTask(tasks);
            BukkitTask task = schedulePeriodic(run, initialTicks, periodTicks);

            taskDisposable.setTask(task);
            tasks.add(taskDisposable);

            return taskDisposable;
        }

        private BukkitTask scheduleImmediate(Runnable run) {
            return mainThread
                    ? Bukkit.getScheduler().runTask(plugin, run)
                    : Bukkit.getScheduler().runTaskAsynchronously(plugin, run);
        }

        private BukkitTask scheduleDelayed(Runnable run, long ticks) {
            return mainThread
                    ? Bukkit.getScheduler().runTaskLater(plugin, run, ticks)
                    : Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, run, ticks);
        }

        private BukkitTask schedulePeriodic(Runnable run, long initialTicks, long periodTicks) {
            return mainThread
                    ? Bukkit.getScheduler().runTaskTimer(plugin, run, initialTicks, periodTicks)
                    : Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, run, initialTicks, periodTicks);
        }

        @Override
        public void dispose() {
            tasks.dispose();
        }

        @Override
        public boolean isDisposed() {
            return tasks.isDisposed();
        }
    }

    /**
     * Disposable that wraps a BukkitTask and removes itself from parent on dispose.
     */
    private static final class TrackedBukkitTask implements Disposable {
        private final CompositeDisposable parent;
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private volatile BukkitTask task;

        TrackedBukkitTask(CompositeDisposable parent) {
            this.parent = parent;
        }

        void setTask(BukkitTask task) {
            this.task = task;
            if (disposed.get() && task != null) {
                task.cancel();
            }
        }

        @Override
        public void dispose() {
            if (disposed.compareAndSet(false, true)) {
                BukkitTask t = task;
                if (t != null) {
                    t.cancel();
                }
                parent.delete(this);
            }
        }

        @Override
        public boolean isDisposed() {
            return disposed.get();
        }
    }
}
