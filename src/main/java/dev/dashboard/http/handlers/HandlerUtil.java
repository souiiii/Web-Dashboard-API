package dev.dashboard.http.handlers;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Static helpers shared across all HTTP handlers.
 */
final class HandlerUtil {

    private HandlerUtil() {
    }

    /** Sends a JSON response with the given status code. */
    static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    /** Sends a plain-text bytes response. */
    static void sendBytes(HttpExchange exchange, int status,
            String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "max-age=300");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    /** 405 Method Not Allowed. */
    static void methodNotAllowed(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Allow", "GET");
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
    }
}
