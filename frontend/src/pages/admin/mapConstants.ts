/**
 * Shared constants for the admin map and its legend.
 */

/** Single colour for all team markers, tracks and legend dots (MUI success.main green).
 *  Kept distinct from checkpoint orange and the hidden/stale grey. */
export const TEAM_COLOR = '#2e7d32';

/** A team's most recent location is considered "stale" once it is older than this. */
export const STALE_AFTER_MS = 5 * 60_000; // 5 minutes
