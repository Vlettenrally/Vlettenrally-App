package nl.vlettenrally.service;

import java.util.Map;

/**
 * Resolves {@code {key}} placeholders in message text. The single source of
 * truth for placeholder substitution, used both by the message delivery path
 * (per-recipient {@code {team}}) and by the automation executor
 * ({@code {checkpoint}}, {@code {rival}}).
 *
 * <p>Only the keys present in the supplied map are replaced; any other text —
 * including unknown or unprovided placeholders — is left untouched, so a
 * mistyped or context-less placeholder is visible rather than silently dropped.
 */
public final class MessagePlaceholders {

    private MessagePlaceholders() {}

    /**
     * Replace each {@code {key}} occurrence with its value. Null content returns
     * null; null values are skipped (their placeholder is left as-is).
     */
    public static String resolve(String content, Map<String, String> values) {
        if (content == null || values == null || values.isEmpty()) {
            return content;
        }
        String result = content;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }
}
