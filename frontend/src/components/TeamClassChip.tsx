import { Chip } from '@mui/material';
import type { ChipProps } from '@mui/material';
import { useTranslation } from 'react-i18next';

/**
 * A team's boat class as one chip. Green = Lelievlet (the standard fleet), orange = vrije
 * klasse (faster boats racing outside it).
 */
type Props = {
  team: { freeClass?: boolean };
  size?: ChipProps['size'];
};

export default function TeamClassChip({ team, size = 'small' }: Props) {
  const { t } = useTranslation();
  const freeClass = team.freeClass ?? false;
  return (
    <Chip
      label={t(freeClass ? 'components.teamClassChip.freeClass' : 'components.teamClassChip.lelieClass')}
      color={freeClass ? 'warning' : 'success'}
      size={size}
    />
  );
}
