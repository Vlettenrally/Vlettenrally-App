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
  
  setMessages: (messages: Message[]) => void;
  addMessage: (message: Message) => void;
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

  setMessages: (messages) =>
    set({
      messages,
      unreadCount: messages.filter((m) => !m.read).length,
    }),

  addMessage: (message) =>
    set((state) => {
      const messages = [message, ...state.messages];
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
    set((state) => ({
      messages: state.messages.map((m) => ({ ...m, read: true })),
      unreadCount: 0,
    })),

  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  reset: () => set({ messages: [], unreadCount: 0, isLoading: false, error: null }),
}));
