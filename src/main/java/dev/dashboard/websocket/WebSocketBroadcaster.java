package dev.dashboard.websocket;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the set of active {@link WebSocketClient} connections and provides
 * a thread-safe {@link #broadcast} method used from multiple call sites:
 * <ul>
 * <li>{@link dev.dashboard.data.DataSyncTask} — pushes TPS/player updates (main
 * thread)</li>
 * <li>{@link dev.dashboard.listeners.ChatListener} — pushes chat events (async
 * thread)</li>
 * </ul>
 *
 * <p>
 * Uses a {@link ConcurrentHashMap} as a set (all values are a sentinel
 * {@link Boolean#TRUE}) so that concurrent adds, removes, and iteration are all
 * safe without additional locking.
 */
public final class WebSocketBroadcaster {

    private static final Logger LOG = Logger.getLogger("WebDashboardAPI");

    /** Live client sessions keyed by identity. */
    private final ConcurrentHashMap<WebSocketClient, Boolean> clients = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Client lifecycle
    // -----------------------------------------------------------------------

    public void add(WebSocketClient client) {
        clients.put(client, Boolean.TRUE);
        LOG.fine("WebSocket client connected: " + client.remoteAddress()
                + "  total=" + clients.size());
    }

    public void remove(WebSocketClient client) {
        clients.remove(client);
        LOG.fine("WebSocket client removed.  total=" + clients.size());
    }

    // -----------------------------------------------------------------------
    // Broadcast
    // -----------------------------------------------------------------------

    /**
     * Sends {@code json} to every connected client.
     *
     * <p>
     * Dead clients (detected by {@link WebSocketClient#isAlive()} or
     * a write failure inside {@link WebSocketClient#send}) are pruned
     * lazily during iteration.
     *
     * @param json a fully-formed JSON string to broadcast
     */
    public void broadcast(String json) {
        if (clients.isEmpty())
            return;

        Collection<WebSocketClient> snapshot = clients.keySet();
        for (WebSocketClient client : snapshot) {
            if (client.isAlive()) {
                client.send(json);
            } else {
                clients.remove(client);
            }
        }
    }

    /** Number of currently connected WebSocket clients. */
    public int clientCount() {
        return clients.size();
    }

    /** Closes all connections (called on plugin disable). */
    public void closeAll() {
        for (WebSocketClient client : clients.keySet()) {
            client.close();
        }
        clients.clear();
    }
}
