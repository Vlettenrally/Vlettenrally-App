/**
 * Notification Sync Service
 * 
 * Handles synchronization of push notifications with the message store using:
 * - IndexedDB for persistent queue of pending message IDs (accessible from service worker)
 * - BroadcastChannel for immediate sync when app is open
 */

const DB_NAME = 'vlettenrally-notifications';
const DB_VERSION = 1;
const STORE_NAME = 'pending-messages';
const CHANNEL_NAME = 'notification-sync';

// Message types for BroadcastChannel
export interface NotificationSyncMessage {
  type: 'NEW_MESSAGE' | 'MESSAGE_CLICKED';
  messageId: string;
}

// ==================== IndexedDB Functions ====================

/**
 * Open the IndexedDB database
 */
export function openNotificationDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onerror = () => {
      console.error('[NotificationSync] Failed to open IndexedDB:', request.error);
      reject(request.error);
    };

    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME, { keyPath: 'messageId' });
      }
    };
  });
}

/**
 * Add a message ID to the pending queue
 */
export async function addPendingMessageId(messageId: string): Promise<void> {
  try {
    const db = await openNotificationDb();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.put({ messageId, timestamp: Date.now() });

      request.onerror = () => {
        console.error('[NotificationSync] Failed to add pending message:', request.error);
        reject(request.error);
      };

      request.onsuccess = () => {
        resolve();
      };

      transaction.oncomplete = () => {
        db.close();
      };
    });
  } catch (error) {
    console.error('[NotificationSync] Failed to add pending message ID:', error);
  }
}

/**
 * Get all pending message IDs
 */
export async function getPendingMessageIds(): Promise<string[]> {
  try {
    const db = await openNotificationDb();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, 'readonly');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.getAll();

      request.onerror = () => {
        console.error('[NotificationSync] Failed to get pending messages:', request.error);
        reject(request.error);
      };

      request.onsuccess = () => {
        const items = request.result as Array<{ messageId: string; timestamp: number }>;
        resolve(items.map((item) => item.messageId));
      };

      transaction.oncomplete = () => {
        db.close();
      };
    });
  } catch (error) {
    console.error('[NotificationSync] Failed to get pending message IDs:', error);
    return [];
  }
}

/**
 * Clear all pending message IDs
 */
export async function clearPendingMessageIds(): Promise<void> {
  try {
    const db = await openNotificationDb();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.clear();

      request.onerror = () => {
        console.error('[NotificationSync] Failed to clear pending messages:', request.error);
        reject(request.error);
      };

      request.onsuccess = () => {
        resolve();
      };

      transaction.oncomplete = () => {
        db.close();
      };
    });
  } catch (error) {
    console.error('[NotificationSync] Failed to clear pending message IDs:', error);
  }
}

/**
 * Remove a specific message ID from the pending queue
 */
export async function removePendingMessageId(messageId: string): Promise<void> {
  try {
    const db = await openNotificationDb();
    return new Promise((resolve, reject) => {
      const transaction = db.transaction(STORE_NAME, 'readwrite');
      const store = transaction.objectStore(STORE_NAME);
      const request = store.delete(messageId);

      request.onerror = () => {
        console.error('[NotificationSync] Failed to remove pending message:', request.error);
        reject(request.error);
      };

      request.onsuccess = () => {
        resolve();
      };

      transaction.oncomplete = () => {
        db.close();
      };
    });
  } catch (error) {
    console.error('[NotificationSync] Failed to remove pending message ID:', error);
  }
}

// ==================== BroadcastChannel Functions ====================

let broadcastChannel: BroadcastChannel | null = null;

/**
 * Get or create the BroadcastChannel instance
 */
export function getNotificationChannel(): BroadcastChannel | null {
  if (typeof BroadcastChannel === 'undefined') {
    console.warn('[NotificationSync] BroadcastChannel not supported');
    return null;
  }

  if (!broadcastChannel) {
    broadcastChannel = new BroadcastChannel(CHANNEL_NAME);
  }
  return broadcastChannel;
}

/**
 * Send a message via BroadcastChannel
 */
export function broadcastNotification(message: NotificationSyncMessage): void {
  const channel = getNotificationChannel();
  if (channel) {
    channel.postMessage(message);
  }
}

/**
 * Subscribe to notification sync messages
 * @param callback Function to handle incoming messages
 * @returns Unsubscribe function
 */
export function subscribeToNotifications(
  callback: (message: NotificationSyncMessage) => void
): () => void {
  const channel = getNotificationChannel();
  if (!channel) {
    return () => {};
  }

  const handler = (event: MessageEvent<NotificationSyncMessage>) => {
    callback(event.data);
  };

  channel.addEventListener('message', handler);

  return () => {
    channel.removeEventListener('message', handler);
  };
}

/**
 * Close the BroadcastChannel (call on app cleanup)
 */
export function closeNotificationChannel(): void {
  if (broadcastChannel) {
    broadcastChannel.close();
    broadcastChannel = null;
  }
}
