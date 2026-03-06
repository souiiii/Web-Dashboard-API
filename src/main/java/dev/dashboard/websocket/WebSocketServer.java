package dev.dashboard.websocket;

import dev.dashboard.config.PluginConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the RFC 6455 WebSocket upgrade handshake for a single socket.
 *
 * <p>
 * Called from {@link dev.dashboard.http.HttpServerManager} when it detects
 * an {@code Upgrade: websocket} header. After the handshake this class
 * registers the new {@link WebSocketClient} with the
 * {@link WebSocketBroadcaster}
 * and starts a per-client read-loop thread to detect disconnects.
 * </p>
 *
 * <h2>Handshake Summary (RFC 6455 §4.2)</h2>
 * <ol>
 * <li>Client sends an HTTP GET with {@code Upgrade: websocket} and
 * {@code Sec-WebSocket-Key: <base64>}.</li>
 * <li>Server computes {@code SHA-1(key + MAGIC_GUID)}, base64-encodes the
 * result, and returns it as {@code Sec-WebSocket-Accept}.</li>
 * <li>Client verifies the accept value; the connection is now a WebSocket.</li>
 * </ol>
 */
public final class WebSocketServer {

    private static final Logger LOG = Logger.getLogger("WebDashboardAPI");

    /** RFC 6455 §4.1 magic GUID concatenated with the client key before SHA-1. */
    private static final String MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private static final Pattern KEY_PATTERN = Pattern.compile("Sec-WebSocket-Key:\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TOKEN_QUERY_PATTERN = Pattern.compile("[?&]token=([^&\\s]+)");

    private final WebSocketBroadcaster broadcaster;
    private final PluginConfig config;

    public WebSocketServer(WebSocketBroadcaster broadcaster, PluginConfig config) {
        this.broadcaster = broadcaster;
        this.config = config;
    }

    /**
     * Performs the WebSocket handshake on a raw socket that has already
     * received an HTTP upgrade request. The raw HTTP request headers are
     * passed in as {@code requestHeaders}.
     *
     * <p>
     * If the handshake succeeds a {@link WebSocketClient} is registered and
     * a daemon read-loop thread is started. Otherwise the socket is closed.
     *
     * @param socket         the raw TCP connection
     * @param requestHeaders the full HTTP request headers string (already read)
     */
    public void handleUpgrade(Socket socket, String requestHeaders) {
        try {
            // --- 0. Auth check ---
            if (!config.getAuthToken().isEmpty()) {
                Matcher m = TOKEN_QUERY_PATTERN.matcher(requestHeaders.split("\r\n")[0]);
                if (!m.find() || !m.group(1).equals(config.getAuthToken())) {
                    sendRaw(socket, "HTTP/1.1 401 Unauthorized\r\n\r\n");
                    socket.close();
                    return;
                }
            }

            // --- 1. Extract client key ---
            Matcher m = KEY_PATTERN.matcher(requestHeaders);
            if (!m.find()) {
                sendRaw(socket, "HTTP/1.1 400 Bad Request\r\n\r\n");
                socket.close();
                return;
            }
            String clientKey = m.group(1).trim();

            // --- 2. Compute accept value ---
            String acceptValue = computeAccept(clientKey);

            // --- 3. Send 101 Switching Protocols response ---
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + acceptValue + "\r\n"
                    + "\r\n";
            sendRaw(socket, response);

            // --- 4. Register client and start read loop ---
            WebSocketClient client = new WebSocketClient(socket);
            broadcaster.add(client);
            LOG.info("WebSocket client connected: " + client.remoteAddress());

            // Daemon thread per client — reads incoming frames (mainly to detect close)
            Thread readThread = new Thread(() -> client.readLoop(broadcaster),
                    "WS-Read-" + client.remoteAddress());
            readThread.setDaemon(true);
            readThread.start();

        } catch (IOException | NoSuchAlgorithmException e) {
            LOG.warning("WebSocket upgrade failed: " + e.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Computes {@code Base64(SHA-1(clientKey + MAGIC_GUID))} per RFC 6455 §4.2.2.
     */
    private static String computeAccept(String clientKey)
            throws NoSuchAlgorithmException {
        String combined = clientKey + MAGIC_GUID;
        byte[] sha1 = MessageDigest.getInstance("SHA-1")
                .digest(combined.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sha1);
    }

    private static void sendRaw(Socket socket, String text) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(text.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }
}
