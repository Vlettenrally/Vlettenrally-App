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
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
} from '@mui/material';
import {
  EmojiEvents as TrophyIcon,
  Timer as TimerIcon,
  Flag as FlagIcon,
} from '@mui/icons-material';
import { useTeamStore, type Achievement } from '../../store/useTeamStore';
import { useGameStore } from '../../store/useGameStore';
import { useMessageStore, type Message } from '../../store/useMessageStore';
import { formatCountdown, getSecondsUntil } from '../../utils/dateTime';
import { storage } from '../../services/storage';
import api from '../../services/api';
import { MessageList } from '../../components/MessageList';

/** Show an "ending soon" warning when the rally ends within this many seconds. */
const ENDING_SOON_SECONDS = 30 * 60;
/** How many messages to preview on the dashboard. */
const RECENT_MESSAGES_COUNT = 5;

/** Game-status card driven by a 1s tick so countdowns stay live. */
function GameStatusCard() {
  const { game } = useGameStore();
  // Tick every second so the countdowns below re-evaluate against the clock.
  const [, setTick] = useState(0);

  useEffect(() => {
    const interval = setInterval(() => setTick((t) => t + 1), 1000);
    return () => clearInterval(interval);
  }, []);

  if (!game) return null;

  const endPassed = game.endTime ? getSecondsUntil(game.endTime) <= 0 : false;

  // Ended / completed.
  if (game.status === 'completed' || endPassed) {
    return (
      <Card sx={{ mb: 3, bgcolor: 'grey.700', color: 'common.white' }}>
        <CardContent>
          <Typography variant="h6">De rally is afgelopen</Typography>
          <Typography variant="body2">Bedankt voor het meedoen!</Typography>
        </CardContent>
      </Card>
    );
  }

  // Ongoing (with an "ending soon" countdown near the end).
  if (game.status === 'active') {
    const secondsToEnd = game.endTime ? getSecondsUntil(game.endTime) : null;
    if (secondsToEnd !== null && secondsToEnd > 0 && secondsToEnd <= ENDING_SOON_SECONDS) {
      return (
        <Card sx={{ mb: 3, bgcolor: 'warning.main', color: 'warning.contrastText' }}>
          <CardContent>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <TimerIcon fontSize="large" />
              <Box sx={{ flexGrow: 1 }}>
                <Typography variant="h6">Rally eindigt over</Typography>
                <Typography variant="h4">{formatCountdown(secondsToEnd)}</Typography>
                <Typography variant="h4">Zorg dat je op tijd over de finish bent</Typography>
              </Box>
            </Box>
          </CardContent>
        </Card>
      );
    }
    return (
      <Card sx={{ mb: 3, bgcolor: 'success.main', color: 'success.contrastText' }}>
        <CardContent>
          <Typography variant="h6">Rally is bezig!</Typography>
          <Typography variant="body2">Veel succes en vaar voorzichtig!</Typography>
        </CardContent>
      </Card>
    );
  }

  // Planned: count down to the start.
  const startTarget = game.startSignalTime ?? game.startTime;
  const secondsToStart = startTarget ? getSecondsUntil(startTarget) : null;
  if (secondsToStart !== null && secondsToStart > 0) {
    return (
      <Card sx={{ mb: 3, bgcolor: 'primary.main', color: 'primary.contrastText' }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <TimerIcon fontSize="large" />
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="h6">Rally begint over</Typography>
              <Typography variant="h4">{formatCountdown(secondsToStart)}</Typography>
            </Box>
          </Box>
        </CardContent>
      </Card>
    );
  }

  // Start time reached but not yet flipped to active.
  return (
    <Card sx={{ mb: 3, bgcolor: 'primary.main', color: 'primary.contrastText' }}>
      <CardContent>
        <Typography variant="h6">Bijna van start!</Typography>
        <Typography variant="body2">Maak je klaar om te vertrekken.</Typography>
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  const { team, achievements, setAchievements } = useTeamStore();
  const { messages, setMessages } = useMessageStore();

  const [showAllMessages, setShowAllMessages] = useState(false);
  const [totalCheckpoints, setTotalCheckpoints] = useState(0);

  // Hydrate data needed on this page (messages, verified achievements,
  // checkpoint total). Real-time updates keep messages/achievements fresh
  // afterwards. getCheckpoints already excludes hidden checkpoints for teams,
  // so its length is the right denominator.
  useEffect(() => {
    const teamId = storage.getTeamId();
    const gameId = storage.getGameId();

    if (teamId) {
      api.getMessages(teamId).then((response) => {
        if (response.data) setMessages(response.data as Message[]);
      });
      api.getTeamVerifiedAchievements(teamId).then((response) => {
        if (response.data) setAchievements(response.data as Achievement[]);
      });
    }

    if (gameId) {
      api.getCheckpoints(gameId).then((response) => {
        if (response.data) setTotalCheckpoints(response.data.length);
      });
    }
    // Run once on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const reachedCount = achievements.length;
  const progressValue =
    totalCheckpoints > 0
      ? Math.min(100, (reachedCount / totalCheckpoints) * 100)
      : reachedCount > 0
        ? 100
        : 0;

  const recentMessages = messages.slice(0, RECENT_MESSAGES_COUNT);

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
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography variant="h5">
                      {totalCheckpoints > 0
                        ? `${reachedCount} / ${totalCheckpoints}`
                        : reachedCount}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Checkpoints
                    </Typography>
                    <LinearProgress
                      variant="determinate"
                      value={progressValue}
                      sx={{ mt: 0.5 }}
                    />
                  </Box>
                </Box>
              </Grid>
            </Grid>
          </Paper>
        )}

        <GameStatusCard />

        <Card>
          <CardContent>
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                mb: 1,
              }}
            >
              <Typography variant="h6">Recente berichten</Typography>
              {messages.length > RECENT_MESSAGES_COUNT && (
                <Button size="small" onClick={() => setShowAllMessages(true)}>
                  Toon alle berichten
                </Button>
              )}
            </Box>
            <MessageList
              messages={recentMessages}
              emptyText="Je ontvangt hier updates tijdens de rally"
            />
          </CardContent>
        </Card>
      </Box>

      <Dialog
        open={showAllMessages}
        onClose={() => setShowAllMessages(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Alle berichten</DialogTitle>
        <DialogContent dividers>
          <MessageList messages={messages} />
        </DialogContent>
      </Dialog>
    </Container>
  );
}

export default DashboardPage;
