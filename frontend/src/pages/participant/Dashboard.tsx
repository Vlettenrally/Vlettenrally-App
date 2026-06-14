/**
 * Dashboard Page - Main participant view
 */
import { useEffect, useState } from 'react';
import {
  Box,
  Paper,
  Typography,
  Card,
  CardContent,
  Alert,
  Grid,
  Dialog,
  DialogTitle,
  DialogContent,
} from '@mui/material';
import {
  EmojiEvents as TrophyIcon,
  Flag as FlagIcon,
  WifiOff as WifiOffIcon,
} from '@mui/icons-material';
import { useTeamStore, type Achievement } from '../../store/useTeamStore';
import { useGameStore } from '../../store/useGameStore';
import { useMessageStore, type Message } from '../../store/useMessageStore';
import { formatCountdown, getSecondsUntil } from '../../utils/dateTime';
import { storage } from '../../services/storage';
import api from '../../services/api';
import { mockApi } from '../../services/mockApi';
import { config } from '../../config';
import { MessageList } from '../../components/MessageList';
import { useConnectionStatus } from '../../hooks/useConnectionStatus';

/** Show an "ending soon" warning when the rally ends within this many seconds. */
const ENDING_SOON_SECONDS = 60 * 60;
/** How many messages to preview on the dashboard. */
const RECENT_MESSAGES_COUNT = 5;

/** Game-status card driven by a 1s tick so countdowns stay live. */
function GameStatusCard() {
  const { game } = useGameStore();
  const { team } = useTeamStore();
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
      <Card sx={{ bgcolor: 'rgba(88, 242, 5, 0.67)', color: 'white', boxShadow: 'none' }}>
        <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, textAlign: 'center' }}>
          <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '0.95rem' }}>De Vlettenrally is afgelopen</Typography>
          <Typography variant="caption" sx={{ fontSize: '0.8rem', opacity: 0.9 }}>Bedankt voor het meedoen!</Typography>
        </CardContent>
      </Card>
    );
  }

  // This team has finished: their race is over even while the game runs on.
  if (team?.finished) {
    return (
      <Card sx={{ mb: 3, bgcolor: 'success.main', color: 'success.contrastText' }}>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
            <FlagIcon fontSize="large" />
            <Box sx={{ flexGrow: 1 }}>
              <Typography variant="h6">Jullie zijn gefinisht!</Typography>
              <Typography variant="body2">Goed gevaren — jullie eindscore staat vast.</Typography>
            </Box>
          </Box>
        </CardContent>
      </Card>
    );
  }

  // Ongoing (with an "ending soon" countdown near the end).
  if (game.status === 'active') {
    const secondsToEnd = game.endTime ? getSecondsUntil(game.endTime) : null;
    if (secondsToEnd !== null && secondsToEnd > 0 && secondsToEnd <= ENDING_SOON_SECONDS) {
      return (
        <Card sx={{ bgcolor: 'rgb(182, 66, 24)', color: 'white', boxShadow: 'none' }}>
          <CardContent sx={{ p: 1, '&:last-child': { pb: 1 } }}>
            <Typography variant="caption" sx={{ fontSize: '0.7rem', opacity: 0.9 }}>Zorg dat je optijd finisht, de Vlettenrally eindigt over:</Typography>
            <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1.2rem', my: 0.3 }}>{formatCountdown(secondsToEnd)}</Typography>
          </CardContent>
        </Card>
      );
    }
    return (
        <Card sx={{ bgcolor: 'rgb(3, 177, 0)', color: 'white', boxShadow: 'none' }}>
        <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, textAlign: 'center' }}>
          <Typography variant="caption" sx={{ fontWeight: 700, fontSize: '0.9rem', opacity: 0.9 }}>De Vlettenrally is gestart!</Typography>
        </CardContent>
      </Card>
    );
  }

  // Planned: count down to the start.
  const startTarget = game.startSignalTime ?? game.startTime;
  const secondsToStart = startTarget ? getSecondsUntil(startTarget) : null;
  if (secondsToStart !== null && secondsToStart > 0) {
    return (
      <Card sx={{ bgcolor: 'rgba(252, 172, 0, 0.79)', color: 'white', boxShadow: 'none' }}>
        <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, textAlign: 'center' }}>
          <Typography variant="caption" sx={{ fontSize: '1.2rem', opacity: 0.9 }}>De Vlettenrally start over:</Typography>
          <Typography variant="h6" sx={{ fontWeight: 700, fontSize: '1.5rem', my: 0.3 }}>{formatCountdown(secondsToStart)}</Typography>
        </CardContent>
      </Card>
    );
  }

  // Start time reached but not yet flipped to active.
  return (
    <Card sx={{ bgcolor: 'rgb(31, 170, 0)', color: 'white', boxShadow: 'none' }}>
      <CardContent sx={{ p: 1, '&:last-child': { pb: 1 }, textAlign: 'center' }}>
        <Typography variant="body2" sx={{ fontWeight: 600, fontSize: '1.3rem' }}>Bijna van start!</Typography>
        <Typography variant="caption" sx={{ fontSize: '1rem', opacity: 0.9 }}>Maak je klaar om te starten!</Typography>
      </CardContent>
    </Card>
  );
}

export function DashboardPage() {
  const { team, achievements, setAchievements } = useTeamStore();
  const { messages, setMessages } = useMessageStore();
  const { showDisconnected } = useConnectionStatus();
  // Checkpoints live in the game store so useTeamSync can refresh them on resume.
  const totalCheckpoints = useGameStore((state) => state.checkpoints.length);

  const [showAllMessages, setShowAllMessages] = useState(false);

  // Hydrate data needed on this page (messages, verified achievements,
  // checkpoints). Real-time updates and useTeamSync keep these fresh afterwards.
  // getCheckpoints already excludes hidden checkpoints for teams, so its length
  // is the right denominator.
  useEffect(() => {
    const teamId = storage.getTeamId();
    const gameId = storage.getGameId();

    // Use mock API in mock mode
    const apiToUse = config.useMocks ? mockApi : api;

    if (teamId) {
      apiToUse.getMessages(teamId).then((response) => {
        if (response.data) setMessages(response.data as Message[]);
      });
      apiToUse.getTeamVerifiedAchievements(teamId).then((response) => {
        if (response.data) setAchievements(response.data as Achievement[]);
      });
    }

    if (gameId) {
      apiToUse.getCheckpoints(gameId).then((response) => {
        if (response.data) useGameStore.getState().setCheckpoints(response.data);
      });
      
      // Load game data for countdown
      if (config.useMocks && mockApi.getGameById) {
        mockApi.getGameById().then((response) => {
          if (response.data) {
            const { setGame } = useGameStore.getState();
            setGame(response.data);
          }
        });
      }
    }
    // Run once on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const reachedCount = achievements.length;

  return (
    <Box sx={{ 
      height: 'calc(100vh - 64px)', // Subtract AppBar height
      display: 'flex', 
      flexDirection: 'column',
      overflow: 'hidden',
    }}>
      {/* Stats & Game Status Header */}
      {team && (
        <Paper 
          elevation={2} 
          sx={{ 
            p: 1, 
            background: 'linear-gradient(180deg, #1b6363 0%, #2f9898 100%)',
            color: 'white',
            borderRadius: 0,
          }}
        >
          {/* Stats Row */}
          <Grid container spacing={2} alignItems="center" justifyContent="center">
            {/* Punten */}
            <Grid size={{ xs: 6, sm: 6 }}>
              <Box sx={{ 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center',
                gap: 0.5,
              }}>
                <TrophyIcon sx={{ color: '#ffcb0f', fontSize: '1.2rem' }} />
                <Box>
                  <Typography variant="body1" sx={{ fontWeight: 600, color: 'white', fontSize: '1.5rem', lineHeight: 1.2 }}>
                    {team.totalPoints}
                  </Typography>
                  <Typography variant="caption" sx={{ opacity: 0.85, fontSize: '0.9rem' }}>
                    Punten
                  </Typography>
                </Box>
              </Box>
            </Grid>
            
            {/* Checkpoints */}
            <Grid size={{ xs: 6, sm: 6 }}>
              <Box sx={{ 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center',
                gap: 0.5,
              }}>
                <FlagIcon sx={{ color: '#ffcb0f', fontSize: '1.2rem' }} />
                <Box>
                  <Typography variant="body1" sx={{ fontWeight: 600, color: 'white', fontSize: '1.5rem', lineHeight: 1.2 }}>
                    {totalCheckpoints > 0
                      ? `${reachedCount}/${totalCheckpoints}`
                      : reachedCount}
                  </Typography>
                  <Typography variant="caption" sx={{ opacity: 0.85, fontSize: '0.9rem' }}>
                    Checkpoints
                  </Typography>
                </Box>
              </Box>
            </Grid>
          </Grid>
          
          {/* Game Status */}
          <Box sx={{ mt: 1 }}>
            <GameStatusCard />
          </Box>
        </Paper>
      )}

      {/* Connection Warning */}
      {showDisconnected && (
        <Alert 
          severity="warning" 
          icon={<WifiOffIcon />}
          sx={{ 
            borderRadius: 0,
          }}
        >
          Geen verbinding met server. Sommige gegevens kunnen verouderd zijn.
        </Alert>
      )}

      {/* Chat-style Messages Feed */}
      <Box sx={{ 
        flex: 1,
        overflowY: 'auto',
        backgroundColor: '#f8f9fa',
        px: 2,
        pb: 2,
      }}>
        {messages.length > 0 ? (
          <>
            <MessageList
              messages={messages}
              emptyText="Je ontvangt hier updates tijdens de rally"
            />
            {messages.length > RECENT_MESSAGES_COUNT && (
              <Box sx={{ textAlign: 'center', mt: 2 }}>
                <Typography variant="caption" color="text.secondary">
                  {messages.length} berichten geladen
                </Typography>
              </Box>
            )}
          </>
        ) : (
          <Box sx={{ 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center',
            height: '100%',
          }}>
            <Typography variant="body2" color="text.secondary">
              Je ontvangt hier updates tijdens de rally
            </Typography>
          </Box>
        )}
      </Box>

      {/* Dialog for all messages (not used in chat view, but kept for compatibility) */}
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
    </Box>
  );
}

export default DashboardPage;
