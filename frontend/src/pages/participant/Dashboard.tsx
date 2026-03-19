/**
 * Dashboard Page - Main participant view
 */
import { useEffect, useState } from 'react';
import {
  Box,
  Container,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  LinearProgress,
  Chip,
} from '@mui/material';
import {
  EmojiEvents as TrophyIcon,
  Timer as TimerIcon,
  Flag as FlagIcon,
} from '@mui/icons-material';
import { useTeamStore } from '../../store/useTeamStore';
import { useGameStore } from '../../store/useGameStore';
import { useMessageStore } from '../../store/useMessageStore';
import { formatCountdown, getSecondsUntil } from '../../utils/dateTime';

export function DashboardPage() {
  const { team, achievements } = useTeamStore();
  const { game } = useGameStore();
  const { unreadCount } = useMessageStore();

  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    if (!game?.startSignalTime) return;

    const updateCountdown = () => {
      const seconds = getSecondsUntil(game.startSignalTime!);
      setCountdown(Math.max(0, seconds));
    };

    updateCountdown();
    const interval = setInterval(updateCountdown, 1000);

    return () => clearInterval(interval);
  }, [game?.startSignalTime]);

  return (
    <Container maxWidth="lg">
      <Box sx={{ mt: 3, mb: 3 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Dashboard
        </Typography>

        {team && (
          <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
            <Grid container spacing={2} alignItems="center">
              <Grid size={{ xs: 12, sm: 4 }}>
                <Typography variant="h6">{team.name}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {team.description}
                </Typography>
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <TrophyIcon color="primary" />
                  <Box>
                    <Typography variant="h5">{team.totalPoints}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Punten
                    </Typography>
                  </Box>
                </Box>
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <FlagIcon color="primary" />
                  <Box>
                    <Typography variant="h5">{achievements.length}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      Checkpoints
                    </Typography>
                  </Box>
                </Box>
              </Grid>
            </Grid>
          </Paper>
        )}

        {game?.status === 'planned' && countdown > 0 && (
          <Card sx={{ mb: 3, bgcolor: 'primary.main', color: 'primary.contrastText' }}>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                <TimerIcon fontSize="large" />
                <Box sx={{ flexGrow: 1 }}>
                  <Typography variant="h6">Start over</Typography>
                  <Typography variant="h4">{formatCountdown(countdown)}</Typography>
                </Box>
              </Box>
            </CardContent>
          </Card>
        )}

        {game?.status === 'active' && (
          <Card sx={{ mb: 3, bgcolor: 'success.main', color: 'success.contrastText' }}>
            <CardContent>
              <Typography variant="h6">Rally is bezig!</Typography>
              <Typography variant="body2">Veel succes en vaar voorzichtig!</Typography>
            </CardContent>
          </Card>
        )}

        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Berichten
                </Typography>
                {unreadCount > 0 ? (
                  <Box>
                    <Chip
                      label={`${unreadCount} ongelezen`}
                      color="primary"
                      size="small"
                    />
                    <Typography variant="body2" sx={{ mt: 1 }}>
                      Je hebt nieuwe berichten!
                    </Typography>
                  </Box>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    Geen nieuwe berichten
                  </Typography>
                )}
              </CardContent>
            </Card>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Prestaties
                </Typography>
                {achievements.length > 0 ? (
                  <Box>
                    <Typography variant="body2">
                      {achievements.length} checkpoint{achievements.length !== 1 ? 's' : ''}{' '}
                      behaald
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={(achievements.length / 10) * 100}
                      sx={{ mt: 1 }}
                    />
                  </Box>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    Nog geen checkpoints behaald
                  </Typography>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      </Box>
    </Container>
  );
}

export default DashboardPage;

