package dev.dashboard.http.handlers;

import dev.dashboard.data.ChatEntry;
import dev.dashboard.data.ThreadSafeDataStore;
import dev.dashboard.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

/**
 * {@code GET /api/chat}
 *
 * <p>
 * Returns the last N chat messages captured in the in-memory log.
 *
 * <p>
 * Example response:
 * 
 * <pre>
 * {
 *   "count": 2,
 *   "messages": [
 *     { "timestamp": 1700000000000, "player": "Steve", "message": "Hello!" },
 *     { "timestamp": 1700000001000, "player": "Alex",  "message": "Hi!"    }
 *   ]
 * }
 * </pre>
 */
public final class ChatLogHandler implements HttpHandler {

    private final ThreadSafeDataStore store;

    public ChatLogHandler(ThreadSafeDataStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            HandlerUtil.methodNotAllowed(exchange);
            return;
        }
        List<ChatEntry> log = store.getChatLog();

        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < log.size(); i++) {
            if (i > 0)
                arr.append(',');
            ChatEntry e = log.get(i);
            arr.append(JsonUtil.object(
                    JsonUtil.field("timestamp", e.getTimestamp()),
                    JsonUtil.field("player", JsonUtil.string(e.getPlayer())),
                    JsonUtil.field("message", JsonUtil.string(e.getMessage()))));
        }
        arr.append("]");

        String json = JsonUtil.object(
                JsonUtil.field("count", log.size()),
                JsonUtil.field("messages", arr.toString()));
        HandlerUtil.sendJson(exchange, 200, json);
    }
}
