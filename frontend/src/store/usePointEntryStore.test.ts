import { beforeEach, describe, expect, it } from 'vitest';
import { usePointEntryStore } from './usePointEntryStore';
import type { PointEntry } from '../types/entities';

function entry(id: string, createdAt: string): PointEntry {
  return { id, createdAt } as PointEntry;
}

beforeEach(() => {
  usePointEntryStore.getState().reset();
});

describe('addEntry', () => {
  it('prepends and keeps the list sorted by createdAt descending', () => {
    const { addEntry } = usePointEntryStore.getState();
    addEntry(entry('a', '2026-06-24T10:00:00.000Z'));
    addEntry(entry('b', '2026-06-24T12:00:00.000Z'));
    addEntry(entry('c', '2026-06-24T11:00:00.000Z'));
    expect(usePointEntryStore.getState().entries.map((e) => e.id)).toEqual(['b', 'c', 'a']);
  });

  it('replaces an existing entry with the same id (dedup)', () => {
    const { addEntry } = usePointEntryStore.getState();
    addEntry(entry('a', '2026-06-24T10:00:00.000Z'));
    addEntry({ ...entry('a', '2026-06-24T10:00:00.000Z'), createdAt: '2026-06-24T10:00:00.000Z', id: 'a' });
    expect(usePointEntryStore.getState().entries).toHaveLength(1);
  });

  it('does not increment totalCount when the same id is added twice', () => {
    const { addEntry } = usePointEntryStore.getState();
    addEntry(entry('a', '2026-06-24T10:00:00.000Z'));
    addEntry(entry('a', '2026-06-24T10:00:00.000Z')); // same id -> replace, no count bump
    const state = usePointEntryStore.getState();
    expect(state.entries).toHaveLength(1);
    expect(state.totalCount).toBe(1);
  });
});

describe('updateEntry', () => {
  it('replaces the matching entry in place', () => {
    usePointEntryStore.getState().setEntries(
      [entry('a', '2026-06-24T10:00:00.000Z'), entry('b', '2026-06-24T11:00:00.000Z')],
      { page: 0, pageSize: 25, totalCount: 2, totalPages: 1 }
    );
    usePointEntryStore.getState().updateEntry({ ...entry('a', '2026-06-24T10:00:00.000Z'), id: 'a' } as PointEntry);
    expect(usePointEntryStore.getState().entries).toHaveLength(2);
  });

  it('is a no-op for an unknown id', () => {
    usePointEntryStore.getState().setEntries(
      [entry('a', '2026-06-24T10:00:00.000Z')],
      { page: 0, pageSize: 25, totalCount: 1, totalPages: 1 }
    );
    const before = usePointEntryStore.getState().entries;
    usePointEntryStore.getState().updateEntry(entry('missing', '2026-06-24T09:00:00.000Z'));
    expect(usePointEntryStore.getState().entries).toEqual(before);
  });

  it('re-sorts when an updated createdAt changes the order', () => {
    usePointEntryStore.getState().setEntries(
      [entry('a', '2026-06-24T12:00:00.000Z'), entry('b', '2026-06-24T11:00:00.000Z')],
      { page: 0, pageSize: 25, totalCount: 2, totalPages: 1 }
    );
    // Move 'a' to the oldest timestamp; it should drop below 'b'.
    usePointEntryStore.getState().updateEntry(entry('a', '2026-06-24T09:00:00.000Z'));
    expect(usePointEntryStore.getState().entries.map((e) => e.id)).toEqual(['b', 'a']);
  });
});
