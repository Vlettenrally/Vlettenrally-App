/**
 * Picture Store - state for team picture uploads and admin moderation.
 */
import { create } from 'zustand';
import type { Picture } from '../types/entities';

interface PictureState {
  // Team side
  pictures: Picture[];
  // Per-file upload progress keyed by a temporary client id (0-100).
  uploadProgress: Record<string, number>;

  // Admin side
  moderationPictures: Picture[];

  isLoading: boolean;
  error: string | null;

  setPictures: (pictures: Picture[]) => void;
  upsertPicture: (picture: Picture) => void;
  removePicture: (pictureId: string) => void;
  setUploadProgress: (key: string, percent: number) => void;
  clearUploadProgress: (key: string) => void;

  setModerationPictures: (pictures: Picture[]) => void;
  updateModerationPicture: (picture: Picture) => void;
  upsertModerationPicture: (picture: Picture) => void;

  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  reset: () => void;
}

export const usePictureStore = create<PictureState>((set) => ({
  pictures: [],
  uploadProgress: {},
  moderationPictures: [],
  isLoading: false,
  error: null,

  setPictures: (pictures) => set({ pictures }),
  upsertPicture: (picture) =>
    set((state) => {
      const exists = state.pictures.some((p) => p.id === picture.id);
      return {
        pictures: exists
          ? state.pictures.map((p) => (p.id === picture.id ? picture : p))
          : [picture, ...state.pictures],
      };
    }),
  removePicture: (pictureId) =>
    set((state) => ({ pictures: state.pictures.filter((p) => p.id !== pictureId) })),
  setUploadProgress: (key, percent) =>
    set((state) => ({ uploadProgress: { ...state.uploadProgress, [key]: percent } })),
  clearUploadProgress: (key) =>
    set((state) => {
      const next = { ...state.uploadProgress };
      delete next[key];
      return { uploadProgress: next };
    }),

  setModerationPictures: (moderationPictures) => set({ moderationPictures }),
  updateModerationPicture: (picture) =>
    set((state) => ({
      moderationPictures: state.moderationPictures.map((p) => (p.id === picture.id ? picture : p)),
    })),
  upsertModerationPicture: (picture) =>
    set((state) => {
      const exists = state.moderationPictures.some((p) => p.id === picture.id);
      return {
        moderationPictures: exists
          ? state.moderationPictures.map((p) => (p.id === picture.id ? picture : p))
          : [picture, ...state.moderationPictures],
      };
    }),

  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  reset: () =>
    set({ pictures: [], uploadProgress: {}, moderationPictures: [], isLoading: false, error: null }),
}));
