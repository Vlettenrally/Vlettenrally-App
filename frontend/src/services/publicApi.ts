/**
 * Client for the login-less public API.
 *
 * Deliberately separate from `services/api.ts`. That client is built around the team/admin session:
 * it attaches the stored Bearer token, proactively refreshes it, and on a 401 retries once and then
 * logs the user out. None of that applies here — the credential is a token from the URL, there is no
 * session to refresh, and a "log out" side effect on an unattended narrowcasting display would be
 * actively wrong. This is a different authentication scheme, not a thin wrapper over the same one.
 */
import config from '../config';
import type { PublicMapResponse } from '../types/entities';

/** Header carrying the per-game map token. Must match PublicMapTokenFilter.TOKEN_HEADER. */
const MAP_TOKEN_HEADER = 'X-Public-Map-Token';

/** Thrown when the server rejects the token, so the caller can tell "bad link" from "try again". */
export class PublicAccessDeniedError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'PublicAccessDeniedError';
  }
}

/**
 * Live team positions and visible zones for a game.
 *
 * Rejects with {@link PublicAccessDeniedError} on 401/404 (a link that will never work), and with a
 * plain Error on anything else (a transient failure worth retrying on the next poll).
 */
export async function fetchPublicMap(
  gameId: string,
  token: string,
  signal?: AbortSignal,
): Promise<PublicMapResponse> {
  const response = await fetch(
    `${config.apiBaseUrl}/public/games/${encodeURIComponent(gameId)}/map`,
    {
      headers: { [MAP_TOKEN_HEADER]: token },
      signal,
    },
  );

  if (response.status === 401 || response.status === 403 || response.status === 404) {
    throw new PublicAccessDeniedError(`Public map unavailable (${response.status})`);
  }
  if (!response.ok) {
    throw new Error(`Public map request failed (${response.status})`);
  }

  return response.json() as Promise<PublicMapResponse>;
}
