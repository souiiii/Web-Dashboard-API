package dev.dashboard.http.handlers;

import dev.dashboard.data.ThreadSafeDataStore;
import dev.dashboard.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@code GET /api/tps}
 *
 * <p>
 * Returns the current 1-minute, 5-minute, and 15-minute TPS averages
 * measured by {@link dev.dashboard.data.TpsSampler}.
 *
 * <p>
 * Example response:
 * 
 * <pre>
 * {
 *   "tps1m": 19.98,
 *   "tps5m": 19.97,
 *   "tps15m": 19.96
 * }
 * </pre>
 */
public final class TpsHandler implements HttpHandler {

    private final ThreadSafeDataStore store;

    public TpsHandler(ThreadSafeDataStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            HandlerUtil.methodNotAllowed(exchange);
            return;
        }
        double[] tps = store.getTps();
        String json = JsonUtil.object(
                JsonUtil.field("tps1m", tps[0]),
                JsonUtil.field("tps5m", tps[1]),
                JsonUtil.field("tps15m", tps[2]));
        HandlerUtil.sendJson(exchange, 200, json);
    }
}
