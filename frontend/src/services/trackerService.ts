import api from './api';
import { type Tracker, type AddTrackersResult } from '../types/entities';

/**
 * Service for the global tracker pool: listing trackers with health status,
 * bulk-adding pasted IDs, and enabling/disabling a tracker.
 */
export const trackerService = {
  getTrackers: async (): Promise<Tracker[]> => {
    const response = await api.getTrackers();
    if (response.error) {
      throw new Error(response.error);
    }
    return response.data ?? [];
  },

  addTrackers: async (trackerIds: string[]): Promise<AddTrackersResult> => {
    const response = await api.addTrackers(trackerIds);
    if (response.error) {
      throw new Error(response.error);
    }
    return response.data!;
  },

  setEnabled: async (trackerId: string, enabled: boolean): Promise<Tracker> => {
    const response = await api.setTrackerEnabled(trackerId, enabled);
    if (response.error) {
      throw new Error(response.error);
    }
    return response.data!;
  },
};
