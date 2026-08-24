/**
 * useAsyncData - Load async data into state without setting state in effects.
 *
 * The dominant page pattern used to be a useCallback'd fetch function that set
 * loading/data/error state itself, invoked from a mount effect (and often again
 * by useAdminResync). react-hooks/set-state-in-effect flags that shape: the
 * effect synchronously calls a state-setting function on commit. This hook is
 * the restructured form the rule asks for:
 *
 *   - the fetcher RETURNS data (throw or reject to report an error),
 *   - state is only set inside the promise's .then continuation (an
 *     external-system response, which the rule exempts),
 *   - `loading` is derived during render instead of being set imperatively,
 *   - a response is dropped if the fetcher identity changed while it was in
 *     flight, so a stale request can never overwrite a newer one.
 *
 * The fetcher's useCallback identity is the cache key: give the fetcher the
 * same deps you would have given the old load function.
 *
 *   const fetcher = useCallback(async () => {
 *     const resp = await api.getStats(selectedGameId);
 *     if (resp.error || !resp.data) throw new Error(resp.error);
 *     return resp.data;
 *   }, [selectedGameId]);
 *   const { data, loading, error, reload } = useAsyncData(fetcher);
 *   useAdminResync(reload);
 *
 * `reload` refetches without clearing the current data (no spinner flash on
 * resync); changing the fetcher identity (deps changed) resets to loading.
 * Pass `enabled: false` to suspend fetching; data reads as undefined and
 * loading as false until re-enabled.
 */
import { useCallback, useEffect, useState } from 'react';

interface AsyncDataResult<T> {
  /** Fetcher identity this result belongs to; stale results are ignored. */
  fetcher: () => Promise<T>;
  data?: T;
  error?: unknown;
}

export interface AsyncDataOptions {
  /** When false, nothing is fetched and the hook reads as not loading. */
  enabled?: boolean;
}

export function useAsyncData<T>(
  fetcher: () => Promise<T>,
  { enabled = true }: AsyncDataOptions = {},
) {
  const [result, setResult] = useState<AsyncDataResult<T> | null>(null);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    fetcher().then(
      (data) => {
        if (!cancelled) setResult({ fetcher, data });
      },
      (error: unknown) => {
        if (!cancelled) setResult({ fetcher, error });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [fetcher, enabled, nonce]);

  // Derived, not synced: a result held for a previous fetcher identity means
  // the current request is still in flight (or was never started).
  const current = enabled && result?.fetcher === fetcher ? result : null;
  const reload = useCallback(() => setNonce((n) => n + 1), []);

  return {
    data: current?.data,
    error: current?.error,
    loading: enabled && !current,
    reload,
  };
}

/** Render-side helper: the message of a useAsyncData error, or null when there is none. */
export function asErrorMessage(error: unknown): string | null {
  if (error === undefined || error === null) return null;
  return error instanceof Error ? error.message : String(error);
}

export default useAsyncData;
