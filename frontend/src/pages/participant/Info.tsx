import { Container, Paper, Stack, Typography } from '@mui/material';
import MarkdownContent from '../../components/MarkdownContent';
import { useGameStore } from '../../store/useGameStore';

export default function ParticipantInfoPage() {
  const { game } = useGameStore();

  return (
    <Container maxWidth="md" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          Spelinformatie en regels
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Alles wat je team moet weten voor {game?.name ?? 'dit spel'}.
        </Typography>

        <Paper sx={{ p: 3 }}>
          {game?.participantInfo ? (
            <MarkdownContent>{game.participantInfo}</MarkdownContent>
          ) : (
            <Typography color="text.secondary">
              Er is nog geen spelinformatie beschikbaar.
            </Typography>
          )}
        </Paper>
      </Stack>
    </Container>
  );
}
