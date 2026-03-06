package dev.dashboard.http.handlers;

import dev.dashboard.config.PluginConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Auth middleware that wraps another {@link HttpHandler}.
 *
 * <p>
 * If an auth token is configured, every request to the inner handler must
 * include an {@code Authorization: Bearer <token>} header. If the token is
 * empty in the config, all requests pass through immediately.
 *
 * <p>
 * This is a decorator / wrapper pattern:
 * {@code new AuthMiddleware(handler, config)}.
 */
public final class AuthMiddleware implements HttpHandler {

    private final HttpHandler delegate;
    private final PluginConfig config;

    public AuthMiddleware(HttpHandler delegate, PluginConfig config) {
        this.delegate = delegate;
        this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String token = config.getAuthToken();
        if (!token.isEmpty()) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.equals("Bearer " + token)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }
        }
        delegate.handle(exchange);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    static void sendError(HttpExchange ex, int code, String msg) throws IOException {
        byte[] body = ("{\"error\":\"" + msg + "\"}").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, body.length);
        ex.getResponseBody().write(body);
        ex.close();
    }
}
