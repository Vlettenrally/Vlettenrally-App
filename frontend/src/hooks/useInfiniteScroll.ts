/**
 * Load the next page when a sentinel element scrolls into view.
 *
 * Returns a ref callback to put on an element rendered *after* the list. The next page is
 * requested while the sentinel is still below the fold (see ROOT_MARGIN), so scrolling stays
 * continuous instead of stalling at the bottom on every batch.
 */
import { useEffect, useRef, useState } from 'react';

// Start fetching this far before the sentinel actually reaches the viewport.
const ROOT_MARGIN = '400px';

interface InfiniteScrollOptions {
  /** Whether another page exists. The observer is torn down once this is false. */
  hasMore: boolean;
  /** Whether a request is already in flight — suppresses duplicate loads. */
  loading: boolean;
  onLoadMore: () => void;
}

export function useInfiniteScroll({
  hasMore,
  loading,
  onLoadMore,
}: InfiniteScrollOptions): (node: HTMLElement | null) => void {
  // The sentinel is conditionally rendered, so it has to be state rather than a ref: the effect
  // must re-run when the element appears or disappears, not only on mount.
  const [sentinel, setSentinel] = useState<HTMLElement | null>(null);

  // Read the callback through a ref. It closes over the cursor and so changes every page;
  // depending on it directly would rebuild the observer on each load.
  const onLoadMoreRef = useRef(onLoadMore);
  useEffect(() => {
    onLoadMoreRef.current = onLoadMore;
  }, [onLoadMore]);

  useEffect(() => {
    if (!sentinel || !hasMore || loading) return;

    const observer = new IntersectionObserver(([entry]) => {
      if (entry.isIntersecting) onLoadMoreRef.current();
    }, { rootMargin: ROOT_MARGIN });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [sentinel, hasMore, loading]);

  return setSentinel;
}

export default useInfiniteScroll;
