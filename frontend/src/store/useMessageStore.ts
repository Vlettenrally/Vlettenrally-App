/**
 * Message Store - Global state for messages
 */
import { create } from 'zustand';

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
  // Signal for the most recent genuinely-new message added via addMessage.
  // Bumped only on real live arrivals (WebSocket / push), never on bulk load,
  // so UI (e.g. the in-app new-message banner) can react without banner-spam on
  // refresh. The nonce re-fires consumers even for otherwise-equal arrivals.
  latestArrival: { message: Message; nonce: number } | null;

  setMessages: (messages: Message[]) => void;
  addMessage: (message: Message) => void;
  updateMessageStatus: (messageId: string, delivered: boolean, read: boolean) => void;
  markAsRead: (messageId: string) => void;
  markAllAsRead: () => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

export const useMessageStore = create<MessageState>((set) => ({
  messages: [],
  unreadCount: 0,
  isLoading: false,
  error: null,
  latestArrival: null,

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
      return {
        messages,
        unreadCount: messages.filter((m) => !m.read).length,
        latestArrival: { message, nonce: state.latestArrival ? state.latestArrival.nonce + 1 : 1 },
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
  reset: () => set({ messages: [], unreadCount: 0, isLoading: false, error: null, latestArrival: null }),
}));
