package dev.dashboard.data;

/**
 * Immutable record representing a single chat message captured from the
 * Minecraft server. Stored in the bounded chat log inside ThreadSafeDataStore.
 */
public final class ChatEntry {

    private final long timestamp;   // System.currentTimeMillis() at capture time
    private final String player;    // Player display name
    private final String message;   // Raw chat message text

    public ChatEntry(String player, String message) {
        this.timestamp = System.currentTimeMillis();
        this.player    = player;
        this.message   = message;
    }

    public long   getTimestamp() { return timestamp; }
    public String getPlayer()    { return player;    }
    public String getMessage()   { return message;   }
}
