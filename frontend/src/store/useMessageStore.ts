/**
 * Message Store - Global state for messages
 */
import { create } from 'zustand';

// A live arrival only triggers the in-app banner when it is this recent. Keeps
// banners meaningful for genuinely-new messages while skipping older messages
// replayed through addMessage by background-sync when the app reopens.
const BANNER_RECENT_WINDOW_MS = 2 * 60 * 1000;

export interface Message {
  id: string;
  teamId: string;
  messageType: 'general' | 'countdown' | 'checkpoint' | 'rallydesk' | 'achievement' | 'rival';
  content: string;
  icon?: string;
  sentAt: string;
  delivered: boolean;
  read: boolean;
}

interface MessageState {
  messages: Message[];
  unreadCount: number;
  isLoading: boolean;
  error: string | null;
  // Undismissed recent live arrivals for the in-app new-message banner. Only
  // fed by addMessage (WebSocket / push), never by bulk load, so refreshing
  // doesn't cause banner-spam. The banner shows the head and clears the queue
  // on view/dismiss.
  bannerQueue: Message[];

  setMessages: (messages: Message[]) => void;
  addMessage: (message: Message) => void;
  updateMessageStatus: (messageId: string, delivered: boolean, read: boolean) => void;
  markAsRead: (messageId: string) => void;
  markAllAsRead: () => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  clearBannerQueue: () => void;
  reset: () => void;
}

export const useMessageStore = create<MessageState>((set) => ({
  messages: [],
  unreadCount: 0,
  isLoading: false,
  error: null,
  bannerQueue: [],

  setMessages: (messages) =>
    set({
      messages,
      unreadCount: messages.filter((m) => !m.read).length,
    }),

  addMessage: (message) =>
    set((state) => {
      // Check if message already exists to prevent duplicates
      if (state.messages.some((m) => m.id === message.id)) {
        return state;
      }
      const messages = [message, ...state.messages];
      const age = Date.now() - new Date(message.sentAt).getTime();
      const bannerWorthy =
        !message.read &&
        !Number.isNaN(age) &&
        age <= BANNER_RECENT_WINDOW_MS &&
        !state.bannerQueue.some((m) => m.id === message.id);
      return {
        messages,
        unreadCount: messages.filter((m) => !m.read).length,
        bannerQueue: bannerWorthy ? [...state.bannerQueue, message] : state.bannerQueue,
      };
    }),

  updateMessageStatus: (messageId, delivered, read) =>
    set((state) => {
      const messages = state.messages.map((m) =>
        m.id === messageId ? { ...m, delivered, read } : m
      );
      return {
        messages,
        unreadCount: messages.filter((m) => !m.read).length,
      };
    }),

  markAsRead: (messageId) =>
    set((state) => {
      const messages = state.messages.map((m) =>
        m.id === messageId ? { ...m, read: true } : m
      );
      return {
        messages,
        unreadCount: messages.filter((m) => !m.read).length,
      };
    }),

  markAllAsRead: () =>
    set((state) => {
      const messages = state.messages.map((m) => ({ ...m, read: true }));
      return {
        messages,
        unreadCount: messages.filter((m) => !m.read).length,
      };
    }),

  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  clearBannerQueue: () => set({ bannerQueue: [] }),
  reset: () => set({ messages: [], unreadCount: 0, isLoading: false, error: null, bannerQueue: [] }),
}));
