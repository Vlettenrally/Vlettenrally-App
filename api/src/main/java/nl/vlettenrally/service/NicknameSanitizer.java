package nl.vlettenrally.service;

import java.text.Normalizer;

/**
 * Cleans a user-supplied device nickname ("phone name") before it is stored or
 * echoed back. The primary write path is the public, unauthenticated
 * {@code POST /api/v1/check-in/register-device} endpoint, so nothing about the
 * value can be trusted.
 *
 * <p>Rather than reject a bad name (which would fail a public check-in on a weird
 * paste), we sanitize and truncate silently: strip control and format characters
 * (log injection, bidi/zero-width spoofing of names in the admin view), normalize
 * to NFC, and cap the length well under the {@code VARCHAR(120)} column so an
 * oversized value can never reach the database as a raw error.
 */
public final class NicknameSanitizer {

    /** Maximum length in Unicode code points; comfortably under the DB column's 120. */
    public static final int MAX_LENGTH = 80;

    private NicknameSanitizer() {
    }

    /**
     * @param raw the untrusted nickname, may be {@code null}
     * @return {@code null} if the input was {@code null}; otherwise a cleaned value
     *         that is at most {@link #MAX_LENGTH} code points and free of control,
     *         format, and separator characters. Returns {@code ""} when nothing
     *         printable survives — callers already treat empty as "no nickname".
     */
    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC);

        StringBuilder sb = new StringBuilder(normalized.length());
        int codePointCount = 0;
        int i = 0;
        while (i < normalized.length() && codePointCount < MAX_LENGTH) {
            int cp = normalized.codePointAt(i);
            i += Character.charCount(cp);

            int type = Character.getType(cp);
            // Drop control (Cc), format (Cf, includes bidi overrides / zero-width),
            // line separator (Zl), and paragraph separator (Zp) characters.
            if (type == Character.CONTROL
                    || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                continue;
            }

            sb.appendCodePoint(cp);
            codePointCount++;
        }

        return sb.toString().trim();
    }
}
