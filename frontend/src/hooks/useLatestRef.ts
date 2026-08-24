/**
 * useLatestRef Hook - Keep a ref in sync with the latest value.
 *
 * Writing `ref.current = value` directly in the render body is impure and is
 * flagged by react-hooks/refs. Updating it in a useLayoutEffect instead runs
 * synchronously as part of the same commit, so nothing (a WebSocket handler,
 * a timer, a promise microtask) can observe a stale value between commit and
 * the ref update.
 */
import { useLayoutEffect, useRef } from 'react';

export function useLatestRef<T>(value: T): React.RefObject<T> {
  const ref = useRef(value);
  useLayoutEffect(() => {
    ref.current = value;
  });
  return ref;
}
