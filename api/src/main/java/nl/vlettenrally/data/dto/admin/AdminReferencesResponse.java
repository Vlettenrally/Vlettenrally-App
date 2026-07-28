package nl.vlettenrally.data.dto.admin;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * How much attributed content an admin user is responsible for.
 * <p>
 * An admin can only be deleted outright when every count here is zero. The FKs pointing at
 * {@code admins} are mostly {@code ON DELETE SET NULL}, so deleting an admin with history would
 * silently blank out who moderated, sent or authored each record — the API refuses instead, and
 * this breakdown tells the caller why.
 * <p>
 * Named counts rather than a map keep the JSON self-documenting and give the frontend stable
 * keys to localize. The service returns this type directly: it is a pure value carrier with no
 * domain behaviour, so a parallel domain record plus mapper would add a layer for no gain.
 */
@RegisterForReflection
public record AdminReferencesResponse(
    long moderatedPictures,
    long sentMessages,
    long verifiedAchievements,
    long pointEntries,
    long automationRules
) {

    /** True when the admin still has attributed content and therefore cannot be deleted. */
    public boolean hasAny() {
        return moderatedPictures > 0
            || sentMessages > 0
            || verifiedAchievements > 0
            || pointEntries > 0
            || automationRules > 0;
    }

    /**
     * The non-zero counts as human-readable fragments, for the English API error message.
     * The frontend localizes its own version from the raw counts instead.
     */
    public String describeNonZero() {
        List<String> parts = new ArrayList<>();
        append(parts, moderatedPictures, "moderated picture");
        append(parts, sentMessages, "sent message");
        append(parts, verifiedAchievements, "verified achievement");
        append(parts, pointEntries, "point entry", "point entries");
        append(parts, automationRules, "automation rule");
        return String.join(", ", parts);
    }

    private static void append(List<String> parts, long count, String singular) {
        append(parts, count, singular, singular + "s");
    }

    private static void append(List<String> parts, long count, String singular, String plural) {
        if (count > 0) {
            parts.add(count + " " + (count == 1 ? singular : plural));
        }
    }
}
