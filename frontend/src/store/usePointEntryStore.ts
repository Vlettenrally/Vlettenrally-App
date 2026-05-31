/**
 * Point ledger store — admin Points Management page.
 *
 * Holds the current page of point entries plus pagination metadata.
 * Real-time POINT_ENTRY_CREATED / POINT_ENTRY_UPDATED events are forwarded
 * into addEntry / updateEntry from the admin realtime hook.
 */
import { create } from 'zustand';
import type { PointEntry } from '../types/entities';

interface PointEntryState {
  entries: PointEntry[];
  totalCount: number;
  page: number;
  pageSize: number;
  totalPages: number;
  isLoading: boolean;
  error: string | null;

  setEntries: (entries: PointEntry[], meta: { page: number; pageSize: number; totalCount: number; totalPages: number }) => void;
  /** Insert (or replace) an entry, keeping the list sorted by createdAt descending. */
  addEntry: (entry: PointEntry) => void;
  updateEntry: (entry: PointEntry) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

export const usePointEntryStore = create<PointEntryState>((set) => ({
  entries: [],
  totalCount: 0,
  page: 0,
  pageSize: 25,
  totalPages: 0,
  isLoading: false,
  error: null,

  setEntries: (entries, meta) => set({
    entries,
    page: meta.page,
    pageSize: meta.pageSize,
    totalCount: meta.totalCount,
    totalPages: meta.totalPages,
  }),

  addEntry: (entry) => set((state) => {
    const without = state.entries.filter((e) => e.id !== entry.id);
    const next = [entry, ...without].sort((a, b) =>
      b.createdAt.localeCompare(a.createdAt)
    );
    return { entries: next, totalCount: state.totalCount + 1 };
  }),

  updateEntry: (entry) => set((state) => ({
    entries: state.entries.map((e) => (e.id === entry.id ? entry : e)),
  })),

  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  reset: () => set({
    entries: [], totalCount: 0, page: 0, pageSize: 25, totalPages: 0,
    isLoading: false, error: null,
  }),
}));
