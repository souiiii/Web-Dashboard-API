package dev.dashboard.http.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Serves static files bundled inside the plugin jar's {@code /web/} resource
 * directory. Handles the root path ({@code /}) and maps it to
 * {@code /web/index.html}.
 *
 * <p>
 * Files are loaded once from the classloader and cached in-memory to avoid
 * repeated resource lookups every request.
 */
public final class RootHandler implements HttpHandler {

    private static final Logger LOG = Logger.getLogger("WebDashboardAPI");

    private static final Map<String, String> MIME_TYPES = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "application/javascript; charset=utf-8",
            ".json", "application/json",
            ".png", "image/png",
            ".ico", "image/x-icon");

    /** Cache: resource path → raw bytes */
    private final Map<String, byte[]> cache = new HashMap<>();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            HandlerUtil.methodNotAllowed(exchange);
            return;
        }

        String path = exchange.getRequestURI().getPath();

        // Map "/" and "/index.html" to the bundled HTML page
        if (path.equals("/") || path.equals("/index.html")) {
            path = "/web/index.html";
        } else {
            path = "/web" + path;
        }

        byte[] content = loadResource(path);
        if (content == null) {
            byte[] notFound = "404 Not Found".getBytes();
            exchange.sendResponseHeaders(404, notFound.length);
            exchange.getResponseBody().write(notFound);
            exchange.close();
            return;
        }

        String mime = resolveMime(path);
        HandlerUtil.sendBytes(exchange, 200, mime, content);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private byte[] loadResource(String resourcePath) {
        return cache.computeIfAbsent(resourcePath, p -> {
            try (InputStream is = RootHandler.class.getResourceAsStream(p)) {
                if (is == null)
                    return null;
                return is.readAllBytes();
            } catch (IOException e) {
                LOG.warning("Could not read resource: " + p + " — " + e.getMessage());
                return null;
            }
        });
    }

    private static String resolveMime(String path) {
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            String ext = path.substring(dot).toLowerCase();
            return MIME_TYPES.getOrDefault(ext, "application/octet-stream");
        }
        return "text/plain";
    }
}
