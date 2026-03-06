package dev.dashboard.util;

import java.util.List;

/**
 * Minimal JSON builder with zero external dependencies.
 *
 * <p>
 * All methods return properly escaped JSON strings. This avoids pulling in
 * Gson/Jackson and keeps the shaded jar small. The escaping logic handles the
 * characters that are most commonly encountered in Minecraft player names and
 * chat messages; for a production plugin you would replace this with Gson.
 */
public final class JsonUtil {

    private JsonUtil() {
    } // utility class — no instances

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Builds a JSON object from a set of pre-rendered key-value fields.
     * 
     * <pre>{@code
     * JsonUtil.object(
     *         JsonUtil.field("name", "\"Steve\""),
     *         JsonUtil.field("score", 42))
     * // → {"name":"Steve","score":42}
     * }</pre>
     */
    public static String object(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    /** Wraps a raw (already-rendered) value in a named field. */
    public static String field(String key, String rawValue) {
        return "\"" + escapeString(key) + "\":" + rawValue;
    }

    /** Convenience overload for numeric (long) field values. */
    public static String field(String key, long value) {
        return "\"" + escapeString(key) + "\":" + value;
    }

    /** Convenience overload for numeric (double) field values. */
    public static String field(String key, double value) {
        return "\"" + escapeString(key) + "\":" + value;
    }

    /** Wraps a Java String as a JSON string value (with quotes + escaping). */
    public static String string(String s) {
        return "\"" + escapeString(s) + "\"";
    }

    /** Builds a JSON array of String values. */
    public static String stringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append('"').append(escapeString(values.get(i))).append('"');
        }
        return sb.append("]").toString();
    }

    /** Builds a TPS JSON array: [1m, 5m, 15m]. */
    public static String tpsArray(double tps1m, double tps5m, double tps15m) {
        return "[" + tps1m + "," + tps5m + "," + tps15m + "]";
    }

    // -----------------------------------------------------------------------
    // Internal escaping
    // -----------------------------------------------------------------------

    /**
     * Escapes a Java string for safe inclusion in a JSON string literal.
     * Handles: {@code " \ / \b \f \n \r \t} and control characters U+0000–U+001F.
     */
    public static String escapeString(String s) {
        if (s == null)
            return "";
        StringBuilder sb = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '/' -> sb.append("\\/");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
