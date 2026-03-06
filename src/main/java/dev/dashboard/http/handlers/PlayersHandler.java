package dev.dashboard.http.handlers;

import dev.dashboard.data.ThreadSafeDataStore;
import dev.dashboard.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;

/**
 * {@code GET /api/players}
 *
 * <p>
 * Returns the list of currently online player names.
 *
 * <p>
 * Example response:
 * 
 * <pre>
 * { "count": 2, "players": ["Steve", "Alex"] }
 * </pre>
 */
public final class PlayersHandler implements HttpHandler {

    private final ThreadSafeDataStore store;

    public PlayersHandler(ThreadSafeDataStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            HandlerUtil.methodNotAllowed(exchange);
            return;
        }
        List<String> players = store.getPlayers();
        String json = JsonUtil.object(
                JsonUtil.field("count", players.size()),
                JsonUtil.field("players", JsonUtil.stringArray(players)));
        HandlerUtil.sendJson(exchange, 200, json);
    }
}
