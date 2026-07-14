/**
 * Normalisation for team invite codes.
 *
 * The same code reaches us in three shapes, and all of them have to resolve to the one
 * canonical form the API expects:
 *   - `HTTPS://HOST/I/K7QM4X9BTD`  — uppercase, decoded straight off a scanned QR
 *   - `https://host/i/k7qm4x9btd`  — mixed case, from the invite page's "copy link" button
 *   - `K7QM4X9BTD`                 — bare, typed in by hand when the camera isn't usable
 */

/**
 * The backend mints codes from Crockford base32 (see InviteCodeStore) — it deliberately
 * omits I, L, O and U so the code survives being read off a screen and typed in.
 */
const CODE_PATTERN = /^[0-9A-HJKMNP-TV-Z]{10}$/;

/**
 * Crockford's canonical decoding of the characters people substitute anyway. Applied only
 * to the input, never when generating: a hand-typed `LOUISE` still lands on the right code.
 */
function foldConfusables(value: string): string {
  return value.replace(/[ILOU]/g, (c) => (c === 'O' ? '0' : c === 'U' ? 'V' : '1'));
}

/**
 * Extract a valid invite code from anything a scanner or a human can hand us, or null if
 * there isn't one. Callers treat null as "not an invite" — never as an expired code.
 */
export function parseInviteCode(input: string | null | undefined): string | null {
  if (!input) return null;

  let candidate = input.trim();
  if (candidate.length === 0) return null;

  // A URL payload: keep only the last non-empty path segment. Parsing rather than regexing
  // the path keeps any query or fragment a QR generator tacked on out of the code.
  if (/^[a-z]+:\/\//i.test(candidate)) {
    try {
      const segments = new URL(candidate).pathname.split('/').filter(Boolean);
      candidate = segments[segments.length - 1] ?? '';
    } catch {
      return null;
    }
  }

  // Tolerate the grouping a person might add when copying the code off a screen.
  candidate = foldConfusables(candidate.toUpperCase().replace(/[\s-]/g, ''));

  return CODE_PATTERN.test(candidate) ? candidate : null;
}
