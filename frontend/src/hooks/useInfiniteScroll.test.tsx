import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { act, cleanup, render } from '@testing-library/react';
import { useInfiniteScroll } from './useInfiniteScroll';

/**
 * The sentinel is the only thing that pulls the next batch of photos. If it fires while a
 * request is already running, the admin pictures page loads (and pays to presign) the same
 * batch twice; if it never fires, the wall silently ends at the first batch.
 */

/** Set by the IntersectionObserver stub; drives whether the sentinel is on screen. */
let emitIntersection: (isIntersecting: boolean) => void;
let observedNodes: Element[];

// No global setupFiles in vitest.config, so RTL's auto-cleanup is not registered.
afterEach(cleanup);

beforeEach(() => {
  observedNodes = [];
  emitIntersection = () => {};
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      constructor(cb: IntersectionObserverCallback) {
        emitIntersection = (isIntersecting) =>
          act(() => {
            cb([{ isIntersecting } as IntersectionObserverEntry], this as never);
          });
      }
      observe(node: Element) {
        observedNodes.push(node);
      }
      unobserve() {}
      disconnect() {}
    },
  );
});

function Harness({
  hasMore,
  loading,
  onLoadMore,
}: {
  hasMore: boolean;
  loading: boolean;
  onLoadMore: () => void;
}) {
  const sentinelRef = useInfiniteScroll({ hasMore, loading, onLoadMore });
  return hasMore ? <div ref={sentinelRef} data-testid="sentinel" /> : null;
}

describe('useInfiniteScroll', () => {
  it('loads more when the sentinel scrolls into view', () => {
    const onLoadMore = vi.fn();
    render(<Harness hasMore loading={false} onLoadMore={onLoadMore} />);

    emitIntersection(true);

    expect(onLoadMore).toHaveBeenCalledTimes(1);
  });

  it('does nothing while the sentinel is still below the fold', () => {
    const onLoadMore = vi.fn();
    render(<Harness hasMore loading={false} onLoadMore={onLoadMore} />);

    emitIntersection(false);

    expect(onLoadMore).not.toHaveBeenCalled();
  });

  it('does not observe while a batch is already in flight', () => {
    const onLoadMore = vi.fn();
    render(<Harness hasMore loading onLoadMore={onLoadMore} />);

    expect(observedNodes).toHaveLength(0);
  });

  it('stops observing once the last batch has been loaded', () => {
    const onLoadMore = vi.fn();
    render(<Harness hasMore={false} loading={false} onLoadMore={onLoadMore} />);

    expect(observedNodes).toHaveLength(0);
  });

  it('resumes after a batch finishes, using the latest callback', () => {
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(<Harness hasMore loading onLoadMore={first} />);

    rerender(<Harness hasMore loading={false} onLoadMore={second} />);
    emitIntersection(true);

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);
  });
});
