package dev.dashboard.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Represents a single connected WebSocket client.
 *
 * <p>
 * Wraps a raw TCP {@link Socket} that has already completed the RFC 6455
 * HTTP upgrade handshake (performed by {@link WebSocketServer}). Handles
 * encoding outgoing text frames and provides a {@link #isAlive()} check so
 * the broadcaster can prune dead connections.
 *
 * <h2>RFC 6455 text frame layout (simplified)</h2>
 * 
 * <pre>
 *  Byte 0:  FIN=1, RSV=0, Opcode=0x1 (text)  → 0x81
 *  Byte 1:  MASK=0, Payload length (7-bit, or 0x7E/0x7F for extended)
 *  Bytes n: Payload data (UTF-8)
 * </pre>
 * 
 * Server-to-client frames are <em>never</em> masked (RFC 6455 §5.1).
 */
public final class WebSocketClient {

    private final Socket socket;
    private final OutputStream out;
    private volatile boolean alive = true;

    private static final Logger LOG = Logger.getLogger("WebDashboardAPI");

    public WebSocketClient(Socket socket) throws IOException {
        this.socket = socket;
        this.out = socket.getOutputStream();
    }

    /**
     * Encodes {@code message} as a single RFC 6455 text frame and writes it
     * to the underlying socket. Thread-safe: synchronized on {@code this}.
     *
     * @param message UTF-8 text to send (must not be {@code null})
     */
    public synchronized void send(String message) {
        if (!alive)
            return;
        try {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);
            int len = payload.length;

            // Build frame header
            byte[] header;
            if (len <= 125) {
                header = new byte[2];
                header[0] = (byte) 0x81; // FIN + text opcode
                header[1] = (byte) len;
            } else if (len <= 65535) {
                header = new byte[4];
                header[0] = (byte) 0x81;
                header[1] = (byte) 0x7E; // 16-bit extended length
                header[2] = (byte) (len >> 8);
                header[3] = (byte) (len & 0xFF);
            } else {
                header = new byte[10];
                header[0] = (byte) 0x81;
                header[1] = (byte) 0x7F; // 64-bit extended length
                // Java int max is < 2^31, so upper 4 bytes are always 0
                header[6] = (byte) ((len >> 24) & 0xFF);
                header[7] = (byte) ((len >> 16) & 0xFF);
                header[8] = (byte) ((len >> 8) & 0xFF);
                header[9] = (byte) (len & 0xFF);
            }

            out.write(header);
            out.write(payload);
            out.flush();

        } catch (IOException e) {
            LOG.fine("WebSocket client disconnected: " + e.getMessage());
            close();
        }
    }

    /**
     * Reads and discards an incoming client frame just enough to detect
     * a close frame (opcode 0x8). Runs on a dedicated per-client thread
     * started by {@link WebSocketServer}.
     */
    public void readLoop(WebSocketBroadcaster broadcaster) {
        try (InputStream in = socket.getInputStream()) {
            byte[] header = new byte[2];
            while (alive) {
                int read = in.read(header);
                if (read < 2)
                    break;

                int opcode = header[0] & 0x0F;
                boolean masked = (header[1] & 0x80) != 0;
                int payloadLen = header[1] & 0x7F;

                // Handle extended length fields
                if (payloadLen == 126) {
                    byte[] ext = new byte[2];
                    if (in.read(ext) < 2)
                        break;
                    payloadLen = ((ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
                } else if (payloadLen == 127) {
                    byte[] ext = new byte[8];
                    if (in.read(ext) < 8)
                        break;
                    payloadLen = (int) (((long) (ext[4] & 0xFF) << 24)
                            | ((ext[5] & 0xFF) << 16)
                            | ((ext[6] & 0xFF) << 8)
                            | (ext[7] & 0xFF));
                }

                // Read masking key (client → server frames are always masked)
                byte[] mask = new byte[4];
                if (masked && in.read(mask) < 4)
                    break;

                // Discard payload (we don't process client→server messages for now)
                long skipped = 0;
                while (skipped < payloadLen) {
                    long s = in.skip(payloadLen - skipped);
                    if (s <= 0)
                        break;
                    skipped += s;
                }

                // Connection close frame
                if (opcode == 0x8)
                    break;
            }
        } catch (IOException ignored) {
            // Client disconnected
        } finally {
            close();
            broadcaster.remove(this);
        }
    }

    public void close() {
        alive = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    public boolean isAlive() {
        return alive && !socket.isClosed();
    }

    /** Remote address string for logging. */
    public String remoteAddress() {
        return socket.getRemoteSocketAddress() != null
                ? socket.getRemoteSocketAddress().toString()
                : "unknown";
    }
}
