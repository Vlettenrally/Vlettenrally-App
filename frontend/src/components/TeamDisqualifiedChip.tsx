import { Chip } from '@mui/material';
import type { ChipProps } from '@mui/material';
import { Block as DisqualifiedIcon } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';

/**
 * A disqualified team as one chip. Renders nothing when the team is not disqualified,
 * keeping the column quiet until it's actually relevant.
 */
type Props = {
  team: { disqualified?: boolean };
  size?: ChipProps['size'];
};

export default function TeamDisqualifiedChip({ team, size = 'small' }: Props) {
  const { t } = useTranslation();
  if (!team.disqualified) return null;

  return (
    <Chip
      label={t('components.teamDisqualifiedChip.label')}
      color="error"
      icon={<DisqualifiedIcon />}
      size={size}
    />
  );
}
