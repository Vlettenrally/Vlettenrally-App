/**
 * Point ledger store — admin Points Management page.
 *
 * Holds the current page of point entries plus pagination metadata.
 * Real-time POINT_ENTRY_CREATED / POINT_ENTRY_UPDATED events are forwarded
 * into addEntry / updateEntry from the admin realtime hook.
 */
import { create } from 'zustand';
import type { PointEntry } from '../types/entities';

/** Newest first, matching the API's default ordering. */
const byCreatedAtDesc = (a: PointEntry, b: PointEntry) => b.createdAt.localeCompare(a.createdAt);

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
    // Only count it as a new entry when the id wasn't already present — the same
    // id can arrive twice (event replay) or be routed here as an update, and
    // those must not inflate totalCount / pagination.
    const isNew = !state.entries.some((e) => e.id === entry.id);
    const without = state.entries.filter((e) => e.id !== entry.id);
    const next = [entry, ...without].sort(byCreatedAtDesc);
    return { entries: next, totalCount: isNew ? state.totalCount + 1 : state.totalCount };
  }),

  updateEntry: (entry) => set((state) => ({
    // Re-sort: an edit can change createdAt, which would otherwise leave the
    // list out of order (addEntry keeps it sorted, so updateEntry must too).
    entries: state.entries.map((e) => (e.id === entry.id ? entry : e)).sort(byCreatedAtDesc),
  })),

  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  reset: () => set({
    entries: [], totalCount: 0, page: 0, pageSize: 25, totalPages: 0,
    isLoading: false, error: null,
  }),
}));
