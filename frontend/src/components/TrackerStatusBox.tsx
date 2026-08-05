/**
 * Bordered tracker box shown on the Check-in and Checkout desk pages: tracker ID plus,
 * once its health record has been fetched, last-seen (orange when stale) and battery.
 */
import { Box, Chip, Typography } from '@mui/material';
import { GpsFixed as TrackerIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { isTrackerStale, batteryColor } from '../utils/trackerStatus';
import type { Tracker } from '../types/entities';

interface TrackerStatusBoxProps {
  trackerId: string | null | undefined;
  /** undefined = health not fetched yet, null = fetched but no matching pool record. */
  tracker: Tracker | null | undefined;
  label: string;
  noTrackerLabel: string;
}

export default function TrackerStatusBox({
  trackerId,
  tracker,
  label,
  noTrackerLabel,
}: TrackerStatusBoxProps) {
  const { t, i18n } = useTranslation();

  const stale = tracker ? isTrackerStale(tracker.lastSeenAt) : false;
  const lastSeenText = tracker?.lastSeenAt
    ? new Date(tracker.lastSeenAt).toLocaleString(i18n.language)
    : t('admin.trackers.neverSeen');

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        p: 2,
        borderRadius: 2,
        border: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.default',
      }}
    >
      <TrackerIcon color={trackerId ? 'primary' : 'disabled'} />
      <Box sx={{ flexGrow: 1 }}>
        <Typography variant="overline" color="text.secondary" sx={{ display: 'block', lineHeight: 1.2 }}>
          {label}
        </Typography>
        <Typography variant="h6" sx={{ wordBreak: 'break-all' }}>
          {trackerId || noTrackerLabel}
        </Typography>
        {trackerId && tracker !== undefined && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 0.5 }}>
            <Typography variant="caption" color={stale ? 'warning.main' : 'text.secondary'}>
              {t('admin.trackers.columnLastSeen')}: {lastSeenText}
            </Typography>
            {tracker?.batteryPercentage != null && (
              <Chip
                size="small"
                color={batteryColor(tracker.batteryPercentage)}
                label={`${tracker.batteryPercentage}%`}
              />
            )}
          </Box>
        )}
      </Box>
    </Box>
  );
}
