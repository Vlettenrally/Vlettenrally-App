import { Chip } from '@mui/material';
import type { ChipProps } from '@mui/material';
import { Flag as FinishedIcon, Schedule as LateIcon, DoNotDisturb as DnfIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { getFinishStatus, type FinishStatus } from '../utils/finishStatus';
import type { Game } from '../types/entities';

/**
 * The team's finish state as one chip. Green = in on time, orange = finished after the game's
 * end time (and penalised for it), red = the game is over and they never came in. A team still
 * en route during a running game renders nothing, keeping the column quiet until it matters.
 */
const styles: Record<Exclude<FinishStatus, 'enRoute'>, { color: ChipProps['color']; icon: React.ReactElement }> = {
  finished: { color: 'success', icon: <FinishedIcon /> },
  late: { color: 'warning', icon: <LateIcon /> },
  dnf: { color: 'error', icon: <DnfIcon /> },
};

type Props = {
  team: { finished?: boolean; finishedAt?: string | null };
  game: Pick<Game, 'endTime' | 'status'> | null | undefined;
  size?: ChipProps['size'];
};

export default function TeamFinishChip({ team, game, size = 'small' }: Props) {
  const { t } = useTranslation();
  const status = getFinishStatus(team, game);
  if (status === 'enRoute') return null;

  const { color, icon } = styles[status];
  return (
    <Chip
      label={t(`components.teamFinishChip.${status}`)}
      color={color}
      icon={icon}
      size={size}
    />
  );
}
