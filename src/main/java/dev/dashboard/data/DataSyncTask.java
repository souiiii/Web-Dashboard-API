package dev.dashboard.data;

import dev.dashboard.WebDashboardPlugin;
import dev.dashboard.websocket.WebSocketBroadcaster;
import dev.dashboard.util.JsonUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Periodic sync task that runs on the Minecraft main thread every N ticks.
 *
 * <p>
 * Responsibilities:
 * <ol>
 * <li>Snapshots online player names &amp; JVM memory into
 * {@link ThreadSafeDataStore}.</li>
 * <li>Broadcasts a combined status JSON to all connected WebSocket clients so
 * the dashboard updates automatically without client-side polling.</li>
 * </ol>
 *
 * <p>
 * Bukkit's {@link org.bukkit.scheduler.BukkitScheduler} runs this on the
 * main thread, so calling {@code Bukkit.getOnlinePlayers()} here is safe.
 * The WebSocket broadcast happens on the same call but writes to client sockets
 * (I/O) — each {@link dev.dashboard.websocket.WebSocketClient#send} is
 * non-blocking for small payloads; large-scale deployments could move the I/O
 * to a separate executor if needed.
 */
public final class DataSyncTask extends BukkitRunnable {

    private final ThreadSafeDataStore store;
    private final WebSocketBroadcaster broadcaster;

    public DataSyncTask(ThreadSafeDataStore store, WebSocketBroadcaster broadcaster) {
        this.store = store;
        this.broadcaster = broadcaster;
    }

    /** Schedules the task to run every {@code intervalTicks} ticks (sync). */
    public void start(WebDashboardPlugin plugin, long intervalTicks) {
        runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    @Override
    public void run() {
        // --- 1. Snapshot players (safe: main thread) ---
        List<String> names = Bukkit.getOnlinePlayers()
                .stream()
                .map(Player::getName)
                .collect(Collectors.toList());
        store.setPlayers(names);

        // --- 2. Snapshot JVM memory ---
        Runtime rt = Runtime.getRuntime();
        long usedBytes = rt.totalMemory() - rt.freeMemory();
        long maxBytes = rt.maxMemory();
        store.setMemory(usedBytes, maxBytes);

        // --- 3. Broadcast live status over WebSocket ---
        double[] tps = store.getTps();
        String json = JsonUtil.object(
                JsonUtil.field("type", "\"status\""),
                JsonUtil.field("tps", JsonUtil.tpsArray(tps[0], tps[1], tps[2])),
                JsonUtil.field("players", JsonUtil.stringArray(names)),
                JsonUtil.field("memory", JsonUtil.object(
                        JsonUtil.field("used", usedBytes),
                        JsonUtil.field("max", maxBytes),
                        JsonUtil.field("pct", maxBytes > 0
                                ? Math.round(100.0 * usedBytes / maxBytes)
                                : 0))));
        broadcaster.broadcast(json);
    }
}
