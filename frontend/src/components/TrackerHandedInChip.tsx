import { Chip } from '@mui/material';
import type { ChipProps } from '@mui/material';
import { AssignmentTurnedIn as HandedInIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { getTrackerHandInStatus, type TrackerHandInStatus } from '../utils/trackerHandInStatus';
import type { AdminTeam } from '../types/entities';

/**
 * A team's tracker return state as one chip. Renders nothing when no tracker is assigned,
 * keeping the column quiet until it's actually relevant.
 */
const styles: Record<Exclude<TrackerHandInStatus, 'noTracker'>, { color: ChipProps['color']; icon?: React.ReactElement }> = {
  notHandedIn: { color: 'default' },
  handedIn: { color: 'success', icon: <HandedInIcon /> },
};

type Props = {
  team: Pick<AdminTeam, 'trackerId' | 'trackerHandedIn'>;
  size?: ChipProps['size'];
};

export default function TrackerHandedInChip({ team, size = 'small' }: Props) {
  const { t } = useTranslation();
  const status = getTrackerHandInStatus(team);
  if (status === 'noTracker') return null;

  const { color, icon } = styles[status];
  return (
    <Chip
      label={t(`components.trackerHandedInChip.${status}`)}
      color={color}
      icon={icon}
      size={size}
    />
  );
}
