/**
 * useInViewMarkAsRead Hook
 * 
 * Automatically marks messages as read when they become visible in the viewport.
 * Uses IntersectionObserver to detect visibility and batches API calls for efficiency.
 */
import { useEffect, useRef, useCallback } from 'react';
import api from '../services/api';
import { useMessageStore } from '../store/useMessageStore';

interface UseInViewMarkAsReadOptions {
  /** Minimum time (ms) a message must be visible before marking as read */
  visibilityThreshold?: number;
  /** Debounce time (ms) for batching API calls */
  debounceTime?: number;
  /** Root margin for IntersectionObserver */
  rootMargin?: string;
}

interface TrackedMessage {
  id: string;
  visibleSince: number | null;
}

/**
 * Hook that returns a ref callback to attach to message elements.
 * When unread messages become visible for the specified threshold,
 * they are batched and marked as read via API.
 */
export function useInViewMarkAsRead(options: UseInViewMarkAsReadOptions = {}) {
  const {
    visibilityThreshold = 500,
    debounceTime = 1000,
    rootMargin = '0px',
  } = options;

  const { markAsRead } = useMessageStore();
  const observerRef = useRef<IntersectionObserver | null>(null);
  const trackedMessagesRef = useRef<Map<Element, TrackedMessage>>(new Map());
  const pendingMarkReadRef = useRef<Set<string>>(new Set());
  const debounceTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Send batched mark-read API call
  const flushPendingMarkRead = useCallback(async () => {
    const ids = Array.from(pendingMarkReadRef.current);
    if (ids.length === 0) return;

    pendingMarkReadRef.current.clear();

    try {
      // Update local store immediately for responsive UI
      ids.forEach((id) => markAsRead(id));

      // Send batch API request
      await api.markMessagesAsRead(ids);
    } catch (error) {
      console.error('[useInViewMarkAsRead] Failed to mark messages as read:', error);
    }
  }, [markAsRead]);

  // Schedule a debounced flush
  const scheduleFlush = useCallback(() => {
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
    }
    debounceTimerRef.current = setTimeout(() => {
      flushPendingMarkRead();
    }, debounceTime);
  }, [debounceTime, flushPendingMarkRead]);

  // Queue a message to be marked as read
  const queueMarkAsRead = useCallback(
    (messageId: string) => {
      pendingMarkReadRef.current.add(messageId);
      scheduleFlush();
    },
    [scheduleFlush]
  );

  // IntersectionObserver callback
  const handleIntersection = useCallback(
    (entries: IntersectionObserverEntry[]) => {
      const now = Date.now();

      entries.forEach((entry) => {
        const tracked = trackedMessagesRef.current.get(entry.target);
        if (!tracked) return;

        if (entry.isIntersecting) {
          // Message became visible - start tracking time
          if (tracked.visibleSince === null) {
            tracked.visibleSince = now;
          }
        } else {
          // Message left viewport - reset visibility tracking
          tracked.visibleSince = null;
        }
      });
    },
    []
  );

  // Check visibility thresholds periodically
  useEffect(() => {
    const checkVisibilityThresholds = () => {
      const now = Date.now();

      trackedMessagesRef.current.forEach((tracked, element) => {
        if (tracked.visibleSince !== null) {
          const visibleDuration = now - tracked.visibleSince;
          if (visibleDuration >= visibilityThreshold) {
            // Message has been visible long enough - queue for marking
            queueMarkAsRead(tracked.id);
            // Stop tracking this message
            trackedMessagesRef.current.delete(element);
            observerRef.current?.unobserve(element);
          }
        }
      });
    };

    const intervalId = setInterval(checkVisibilityThresholds, 100);
    return () => clearInterval(intervalId);
  }, [visibilityThreshold, queueMarkAsRead]);

  // Initialize IntersectionObserver
  useEffect(() => {
    observerRef.current = new IntersectionObserver(handleIntersection, {
      rootMargin,
      threshold: 0.5, // At least 50% visible
    });

    return () => {
      observerRef.current?.disconnect();
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
      // Flush any pending on unmount
      flushPendingMarkRead();
    };
  }, [handleIntersection, rootMargin, flushPendingMarkRead]);

  /**
   * Ref callback to attach to message elements.
   * Only tracks unread messages.
   * 
   * @param messageId The message ID
   * @param isRead Whether the message is already read
   * @returns A ref callback function
   */
  const getMessageRef = useCallback(
    (messageId: string, isRead: boolean) => {
      return (element: HTMLElement | null) => {
        // Don't track already-read messages
        if (isRead) {
          return;
        }

        if (element) {
          // Start observing
          trackedMessagesRef.current.set(element, {
            id: messageId,
            visibleSince: null,
          });
          observerRef.current?.observe(element);
        }
      };
    },
    []
  );

  return { getMessageRef };
}
