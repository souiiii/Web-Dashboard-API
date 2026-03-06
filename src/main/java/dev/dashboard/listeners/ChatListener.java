package dev.dashboard.listeners;

import dev.dashboard.data.ChatEntry;
import dev.dashboard.data.ThreadSafeDataStore;
import dev.dashboard.util.JsonUtil;
import dev.dashboard.websocket.WebSocketBroadcaster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Listens for player chat events and:
 * <ol>
 * <li>Appends the message to the in-memory bounded chat log.</li>
 * <li>Immediately broadcasts the message to all connected WebSocket clients
 * so the dashboard chat feed updates in real-time.</li>
 * </ol>
 *
 * <p>
 * <strong>Thread note:</strong> {@link AsyncPlayerChatEvent} fires on a
 * separate async thread (not the main thread). Both operations performed here
 * are designed to be called safely from any thread:
 * <ul>
 * <li>{@link ThreadSafeDataStore#addChat} uses a synchronized block.</li>
 * <li>{@link WebSocketBroadcaster#broadcast} iterates a
 * {@link java.util.concurrent.ConcurrentHashMap} and writes to raw
 * sockets — no Bukkit API calls.</li>
 * </ul>
 */
public final class ChatListener implements Listener {

    private final ThreadSafeDataStore store;
    private final WebSocketBroadcaster broadcaster;

    public ChatListener(ThreadSafeDataStore store, WebSocketBroadcaster broadcaster) {
        this.store = store;
        this.broadcaster = broadcaster;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String player = event.getPlayer().getName();
        String message = event.getMessage();

        // 1. Log to bounded in-memory store
        ChatEntry entry = new ChatEntry(player, message);
        store.addChat(entry);

        // 2. Push to WebSocket clients as a "chat" event JSON
        String json = JsonUtil.object(
                JsonUtil.field("type", "\"chat\""),
                JsonUtil.field("player", JsonUtil.string(player)),
                JsonUtil.field("message", JsonUtil.string(message)),
                JsonUtil.field("timestamp", entry.getTimestamp()));
        broadcaster.broadcast(json);
    }
}
