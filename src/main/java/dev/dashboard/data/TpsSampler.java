package dev.dashboard.data;

import dev.dashboard.WebDashboardPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Measures actual server TPS by tracking real elapsed time between ticks.
 *
 * <p>
 * Runs as a repeating sync task (every tick = period 1). On each invocation
 * it records the wall-clock nanosecond delta and feeds it into three
 * independent
 * exponential moving averages (EMA) for 1-minute, 5-minute, and 15-minute
 * windows — exactly the same approach Paper uses internally.
 *
 * <p>
 * <strong>EMA formula:</strong> {@code alpha = 1 - e^(-dt/window)}
 * where {@code dt} is elapsed seconds and {@code window} is the target
 * smoothing window in seconds.
 */
public final class TpsSampler extends BukkitRunnable {

    // Smoothing windows in seconds
    private static final double WINDOW_1M = 60.0;
    private static final double WINDOW_5M = 300.0;
    private static final double WINDOW_15M = 900.0;

    // Cap TPS readings so a single fast tick doesn't spike the average above 20
    private static final double MAX_TPS = 20.0;

    private final ThreadSafeDataStore store;

    // Rolling EMA values — only accessed from the main thread by this task
    private double avg1m = 20.0;
    private double avg5m = 20.0;
    private double avg15m = 20.0;

    private long lastNano = System.nanoTime();

    public TpsSampler(ThreadSafeDataStore store) {
        this.store = store;
    }

    /** Starts the sampler as a repeating sync task (period = 1 tick). */
    public void start(WebDashboardPlugin plugin) {
        runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        long now = System.nanoTime();
        long deltaNs = now - lastNano;
        lastNano = now;

        if (deltaNs <= 0)
            return; // guard against clock anomalies

        // Convert nanoseconds → ticks per second (TPS) for this interval.
        // One ideal tick is 50 ms = 50_000_000 ns → TPS = 1e9 / deltaNs.
        // We clamp to MAX_TPS to avoid spikes from garbage-collection pauses
        // that created one very-short tick immediately after a very-long one.
        double instantTps = Math.min(MAX_TPS, 1_000_000_000.0 / deltaNs);

        // EMA: elapsed time in seconds
        double dt = deltaNs / 1_000_000_000.0;
        avg1m = ema(avg1m, instantTps, dt, WINDOW_1M);
        avg5m = ema(avg5m, instantTps, dt, WINDOW_5M);
        avg15m = ema(avg15m, instantTps, dt, WINDOW_15M);

        // Publish to the data store — the immutable double[] is written
        // atomically via the volatile field in ThreadSafeDataStore
        store.setTps(round(avg1m), round(avg5m), round(avg15m));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Exponential Moving Average step.
     * alpha = 1 - e^(-dt / window) ensures the smoothing adapts correctly
     * even when tick intervals are irregular.
     */
    private static double ema(double prev, double instant, double dt, double window) {
        double alpha = 1.0 - Math.exp(-dt / window);
        return prev + alpha * (instant - prev);
    }

    /** Round to two decimal places for cleaner JSON output. */
    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
