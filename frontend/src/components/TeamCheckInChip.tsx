import { Chip } from '@mui/material';
import type { ChipProps } from '@mui/material';
import { Smartphone as DigitalIcon, EditNote as ManualIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { getCheckInStatus, type CheckInStatus } from '../utils/checkInStatus';
import type { AdminTeam } from '../types/entities';

/**
 * The team's check-in state as one chip. Grey = nothing yet, orange = the team still
 * has to finish check-in on their phones, green = done (manual or digital).
 */
const styles: Record<CheckInStatus, { color: ChipProps['color']; icon?: React.ReactElement }> = {
  notCheckedIn: { color: 'default' },
  manual: { color: 'success', icon: <ManualIcon /> },
  digitalPending: { color: 'warning', icon: <DigitalIcon /> },
  digitalCompleted: { color: 'success', icon: <DigitalIcon /> },
};

type Props = {
  team: Pick<AdminTeam, 'checkedIn' | 'checkedInDigitally' | 'checkInMethod'>;
  size?: ChipProps['size'];
};

export default function TeamCheckInChip({ team, size = 'small' }: Props) {
  const { t } = useTranslation();
  const status = getCheckInStatus(team);
  const { color, icon } = styles[status];

  return (
    <Chip
      label={t(`components.teamCheckInChip.${status}`)}
      color={color}
      icon={icon}
      size={size}
    />
  );
}
