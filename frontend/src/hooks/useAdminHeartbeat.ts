/**
 * useAdminTopicHeartbeat - Signals that an admin sub-page is actively mounted.
 *
 * The admin WebSocket connection is app-wide (opened once in AdminLayout), so
 * without gating the backend would push every admin broadcast to every admin
 * session regardless of which page they're on. Each page that consumes admin
 * events (including the live map, topic "map") sends an ADMIN_HEARTBEAT for its
 * topic every 10s while mounted; the backend only streams that topic's events to
 * sessions with a fresh heartbeat (see AdminTopicSubscriptionManager).
 *
 * Topic strings must stay in sync with the backend AdminTopics constants.
 */
import { useEffect } from 'react';
import { useWebSocketSubscription } from './useWebSocket';

export const ADMIN_TOPICS = {
  MAP: 'map',
  PICTURES: 'pictures',
  POINTS: 'points',
  MESSAGES: 'messages',
  AUTOMATIONS: 'automations',
  INBOX: 'inbox',
} as const;

export type AdminTopic = typeof ADMIN_TOPICS[keyof typeof ADMIN_TOPICS];

const ADMIN_HEARTBEAT_INTERVAL_MS = 10_000; // 10 seconds (well within the 30s server timeout)

/**
 * Send ADMIN_HEARTBEAT for the given topic every 10s while connected. Mount in
 * an admin page component; the heartbeat stops (and the subscription expires
 * server-side within 30s) when the page unmounts.
 */
export function useAdminTopicHeartbeat(topic: AdminTopic) {
  const { send, connected } = useWebSocketSubscription();

  useEffect(() => {
    if (!connected) return;

    send({ type: 'ADMIN_HEARTBEAT', topic });
    const id = setInterval(() => {
      send({ type: 'ADMIN_HEARTBEAT', topic });
    }, ADMIN_HEARTBEAT_INTERVAL_MS);

    return () => clearInterval(id);
  }, [connected, send, topic]);
}

export default useAdminTopicHeartbeat;
