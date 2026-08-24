/**
 * Tests for useAsyncData: fetcher identity is the cache key; state is only set
 * from the promise continuation. We verify the derived loading flag, stale
 * response dropping when the fetcher changes mid-flight, reload semantics
 * (refetch without clearing current data), and the enabled gate.
 */
import { expect, it, vi, afterEach } from 'vitest';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { useCallback } from 'react';
import { useAsyncData } from './useAsyncData';

afterEach(cleanup);

/** A fetcher whose promise we resolve manually. */
function deferred<T>() {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

it('loads data and derives loading', async () => {
  const { result } = renderHook(() => useAsyncData(useCallback(() => Promise.resolve('hello'), [])));
  expect(result.current.loading).toBe(true);
  expect(result.current.data).toBeUndefined();

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.data).toBe('hello');
  expect(result.current.error).toBeUndefined();
});

it('exposes a rejection as error', async () => {
  const boom = new Error('boom');
  const { result } = renderHook(() => useAsyncData(useCallback(() => Promise.reject(boom), [])));
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error).toBe(boom);
  expect(result.current.data).toBeUndefined();
});

it('drops a stale response when the fetcher identity changes mid-flight', async () => {
  const first = deferred<string>();
  const { result, rerender } = renderHook(
    ({ id }: { id: string }) =>
      useAsyncData(
        useCallback(() => (id === 'a' ? first.promise : Promise.resolve('fresh-' + id)), [id]),
      ),
    { initialProps: { id: 'a' } },
  );
  expect(result.current.loading).toBe(true);

  // Switch to a new fetcher while request 'a' is still in flight.
  rerender({ id: 'b' });
  await waitFor(() => expect(result.current.data).toBe('fresh-b'));

  // Late arrival of the old response must not overwrite the new data.
  await act(async () => {
    first.resolve('stale-a');
    await Promise.resolve();
  });
  expect(result.current.data).toBe('fresh-b');
});

it('reload refetches without clearing current data', async () => {
  let calls = 0;
  const { result } = renderHook(() =>
    useAsyncData(useCallback(() => Promise.resolve('v' + ++calls), [])),
  );
  await waitFor(() => expect(result.current.data).toBe('v1'));

  act(() => result.current.reload());
  // Old data stays visible while the refetch is in flight.
  expect(result.current.loading).toBe(false);
  expect(result.current.data).toBe('v1');
  await waitFor(() => expect(result.current.data).toBe('v2'));
});

it('does not fetch while disabled, then fetches once enabled', async () => {
  const fetcher = vi.fn(() => Promise.resolve(42));
  const { result, rerender } = renderHook(
    ({ enabled }: { enabled: boolean }) =>
      useAsyncData(useCallback(fetcher, []), { enabled }),
    { initialProps: { enabled: false } },
  );
  expect(result.current.loading).toBe(false);
  expect(fetcher).not.toHaveBeenCalled();

  rerender({ enabled: true });
  await waitFor(() => expect(result.current.data).toBe(42));
});
