package dev.dashboard.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed wrapper around the plugin's {@code config.yml}.
 *
 * <p>
 * Stores a snapshot of config values at load/reload time so that web
 * threads can read them without touching the Bukkit API.
 */
public final class PluginConfig {

    private int port;
    private String authToken;
    private int chatLogSize;
    private long wsBroadcastInterval;

    /** Loads (or reloads) values from the given Bukkit config. */
    public void load(FileConfiguration cfg) {
        port = cfg.getInt("port", 8080);
        authToken = cfg.getString("auth-token", "");
        chatLogSize = cfg.getInt("chat-log-size", 100);
        wsBroadcastInterval = cfg.getLong("ws-broadcast-interval", 40);
    }

    public int getPort() {
        return port;
    }

    public String getAuthToken() {
        return authToken;
    }

    public int getChatLogSize() {
        return chatLogSize;
    }

    public long getWsBroadcastInterval() {
        return wsBroadcastInterval;
    }
}
