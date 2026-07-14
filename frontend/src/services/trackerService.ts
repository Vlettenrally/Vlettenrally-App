import api from './api';
import { type Tracker, type AddTrackersResult, type DeleteTrackersResult } from '../types/entities';

/**
 * Service for the global tracker pool: listing trackers with health status,
 * bulk-adding pasted IDs, enabling/disabling a tracker, and removing trackers
 * from the pool.
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

  deleteTrackers: async (trackerIds: string[]): Promise<DeleteTrackersResult> => {
    const response = await api.deleteTrackers(trackerIds);
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
