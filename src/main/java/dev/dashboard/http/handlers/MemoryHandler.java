package dev.dashboard.http.handlers;

import dev.dashboard.data.ThreadSafeDataStore;
import dev.dashboard.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * {@code GET /api/memory}
 *
 * <p>
 * Returns the current JVM heap memory usage.
 *
 * <p>
 * Example response:
 * 
 * <pre>
 * { "used": 314572800, "max": 2147483648, "pct": 14 }
 * </pre>
 *
 * <p>
 * Values are in bytes; {@code pct} is the integer percentage of max memory
 * used.
 */
public final class MemoryHandler implements HttpHandler {

    private final ThreadSafeDataStore store;

    public MemoryHandler(ThreadSafeDataStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            HandlerUtil.methodNotAllowed(exchange);
            return;
        }
        long used = store.getUsedMemoryBytes();
        long max = store.getMaxMemoryBytes();
        long pct = max > 0 ? Math.round(100.0 * used / max) : 0;

        String json = JsonUtil.object(
                JsonUtil.field("used", used),
                JsonUtil.field("max", max),
                JsonUtil.field("pct", pct));
        HandlerUtil.sendJson(exchange, 200, json);
    }
}
