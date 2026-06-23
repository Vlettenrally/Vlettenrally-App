import { beforeEach, describe, expect, it } from 'vitest';
import { useMessageStore, type Message } from './useMessageStore';

function msg(id: string, overrides: Partial<Message> = {}): Message {
  return {
    id,
    teamId: 'team-1',
    messageType: 'general',
    content: `message ${id}`,
    sentAt: '2026-06-24T12:00:00.000Z',
    delivered: true,
    read: false,
    ...overrides,
  };
}

beforeEach(() => {
  useMessageStore.getState().reset();
});

describe('setMessages', () => {
  it('replaces the list and recomputes unreadCount', () => {
    useMessageStore.getState().setMessages([
      msg('a', { read: true }),
      msg('b', { read: false }),
      msg('c', { read: false }),
    ]);
    const state = useMessageStore.getState();
    expect(state.messages).toHaveLength(3);
    expect(state.unreadCount).toBe(2);
  });
});

describe('addMessage', () => {
  it('prepends a new message and updates unreadCount', () => {
    const { addMessage } = useMessageStore.getState();
    addMessage(msg('a', { read: false }));
    addMessage(msg('b', { read: false }));
    const state = useMessageStore.getState();
    expect(state.messages.map((m) => m.id)).toEqual(['b', 'a']); // newest first
    expect(state.unreadCount).toBe(2);
  });

  it('ignores a duplicate id (no change)', () => {
    const { addMessage } = useMessageStore.getState();
    addMessage(msg('a', { read: false }));
    const before = useMessageStore.getState().messages;
    addMessage(msg('a', { content: 'changed', read: false }));
    const after = useMessageStore.getState();
    expect(after.messages).toBe(before); // same reference, state unchanged
    expect(after.messages).toHaveLength(1);
    expect(after.unreadCount).toBe(1);
  });

  it('does not increment unreadCount for an already-read message', () => {
    useMessageStore.getState().addMessage(msg('a', { read: true }));
    expect(useMessageStore.getState().unreadCount).toBe(0);
  });
});

describe('updateMessageStatus', () => {
  it('updates delivered/read flags and recomputes unreadCount', () => {
    useMessageStore.getState().setMessages([msg('a', { read: false }), msg('b', { read: false })]);
    useMessageStore.getState().updateMessageStatus('a', true, true);
    const state = useMessageStore.getState();
    expect(state.messages.find((m) => m.id === 'a')).toMatchObject({ delivered: true, read: true });
    expect(state.unreadCount).toBe(1);
  });

  it('is a no-op for an unknown id', () => {
    useMessageStore.getState().setMessages([msg('a', { read: false })]);
    useMessageStore.getState().updateMessageStatus('missing', true, true);
    expect(useMessageStore.getState().unreadCount).toBe(1);
  });
});

describe('markAsRead / markAllAsRead', () => {
  it('markAsRead lowers unreadCount by one', () => {
    useMessageStore.getState().setMessages([msg('a', { read: false }), msg('b', { read: false })]);
    useMessageStore.getState().markAsRead('a');
    expect(useMessageStore.getState().unreadCount).toBe(1);
  });

  it('markAsRead on an already-read message leaves the count unchanged', () => {
    useMessageStore.getState().setMessages([msg('a', { read: true }), msg('b', { read: false })]);
    useMessageStore.getState().markAsRead('a');
    expect(useMessageStore.getState().unreadCount).toBe(1);
  });

  it('markAllAsRead clears the count and marks every message read', () => {
    useMessageStore.getState().setMessages([msg('a', { read: false }), msg('b', { read: false })]);
    useMessageStore.getState().markAllAsRead();
    const state = useMessageStore.getState();
    expect(state.unreadCount).toBe(0);
    expect(state.messages.every((m) => m.read)).toBe(true);
  });
});
