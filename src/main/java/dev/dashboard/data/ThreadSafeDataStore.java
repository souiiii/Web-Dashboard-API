package dev.dashboard.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Central data store that bridges the Minecraft main thread and the
 * asynchronous HTTP/WebSocket thread pool.
 *
 * <p>
 * <strong>Thread-safety contract:</strong>
 * <ul>
 * <li>All <em>writes</em> are performed exclusively from a repeating Bukkit
 * <em>sync</em> scheduler task running on the main server thread.</li>
 * <li>All <em>reads</em> are performed by HTTP handler / WebSocket
 * threads.</li>
 * <li>Every field that crosses the thread boundary is declared
 * {@code volatile}, and collection snapshots are replaced atomically
 * with immutable/unmodifiable copies — guaranteeing safe publication
 * without explicit locking on the hot read path.</li>
 * </ul>
 *
 * <p>
 * This pattern is intentional: it avoids synchronising on the main thread
 * (which would stall gameplay) while giving web threads a consistent, recent
 * view of the server state.
 */
public final class ThreadSafeDataStore {

    // -----------------------------------------------------------------------
    // TPS (1-minute, 5-minute, 15-minute rolling averages)
    // -----------------------------------------------------------------------
    /** Volatile array — replaced atomically by the TPS sampler each tick. */
    private volatile double[] tps = { 20.0, 20.0, 20.0 };

    /** Written from TpsSampler (main thread), read from web threads. */
    public void setTps(double tps1m, double tps5m, double tps15m) {
        this.tps = new double[] { tps1m, tps5m, tps15m };
    }

    /** Returns an immutable snapshot: [1m, 5m, 15m]. */
    public double[] getTps() {
        return tps.clone();
    }

    // -----------------------------------------------------------------------
    // Online players
    // -----------------------------------------------------------------------
    private volatile List<String> players = Collections.emptyList();

    /** Replace the player snapshot. Call from the sync DataSyncTask only. */
    public void setPlayers(List<String> names) {
        this.players = Collections.unmodifiableList(new ArrayList<>(names));
    }

    public List<String> getPlayers() {
        return players; // unmodifiable, safe to return directly
    }

    // -----------------------------------------------------------------------
    // JVM Memory
    // -----------------------------------------------------------------------
    private volatile long usedMemoryBytes = 0;
    private volatile long maxMemoryBytes = 0;

    public void setMemory(long used, long max) {
        this.usedMemoryBytes = used;
        this.maxMemoryBytes = max;
    }

    public long getUsedMemoryBytes() {
        return usedMemoryBytes;
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    // -----------------------------------------------------------------------
    // Chat log (bounded FIFO)
    // -----------------------------------------------------------------------
    /**
     * The chat log is the only field that is written from an
     * <em>async</em> event handler (AsyncPlayerChatEvent). We protect it
     * with an intrinsic lock on the deque itself to keep the critical section
     * as short as possible.
     */
    private final Deque<ChatEntry> chatLog = new ArrayDeque<>();
    private volatile int chatLogMaxSize = 100;

    public void setChatLogMaxSize(int size) {
        chatLogMaxSize = Math.max(1, size);
    }

    /**
     * Appends a new chat entry. Safe to call from any thread.
     * Trims the oldest entry when the log is full.
     */
    public void addChat(ChatEntry entry) {
        synchronized (chatLog) {
            chatLog.addLast(entry);
            while (chatLog.size() > chatLogMaxSize) {
                chatLog.pollFirst();
            }
        }
    }

    /**
     * Returns an immutable snapshot of the chat log, oldest first.
     * Safe to call from any thread.
     */
    public List<ChatEntry> getChatLog() {
        synchronized (chatLog) {
            return Collections.unmodifiableList(new ArrayList<>(chatLog));
        }
    }
}
