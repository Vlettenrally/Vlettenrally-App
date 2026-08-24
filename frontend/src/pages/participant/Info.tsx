import { Container, Paper, Stack, Typography } from '@mui/material';
import { useTranslation } from 'react-i18next';
import MarkdownContent from '../../components/MarkdownContent';
import { useGameStore } from '../../store/useGameStore';

export default function ParticipantInfoPage() {
  const { game } = useGameStore();
  const { t } = useTranslation();

  return (
    <Container maxWidth="md" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          {t('participant.info.title')}
        </Typography>
        <Typography variant="body2" color="textSecondary">
          {t('participant.info.subtitle', { game: game?.name ?? 'dit spel' })}
        </Typography>

        <Paper sx={{ p: 3 }}>
          {game?.participantInfo ? (
            <MarkdownContent>{game.participantInfo}</MarkdownContent>
          ) : (
            <Typography color="textSecondary">
              {t('participant.info.noInfo')}
            </Typography>
          )}
        </Paper>
      </Stack>
    </Container>
  );
}
