/**
 * Game Clock Widget - Shows elapsed/remaining time for active game
 */
import { useState, useEffect } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  Chip,
} from '@mui/material';
import { Schedule as ClockIcon } from '@mui/icons-material';
import { storage } from '../../services/storage';

interface Game {
  id: string;
  name: string;
  startTime: string;
  endTime: string;
  status: 'planned' | 'active' | 'completed';
}

interface GameClockWidgetProps {
  collapsed?: boolean;
}

function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(Math.abs(ms) / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

export function GameClockWidget({ collapsed = false }: GameClockWidgetProps) {
  const [game, setGame] = useState<Game | null>(null);
  const [timeDisplay, setTimeDisplay] = useState('--:--');
  const [status, setStatus] = useState<'before' | 'during' | 'after'>('before');

  useEffect(() => {
    const fetchGame = async () => {
      try {
        const token = storage.getAuthToken();
        const gameId = storage.getAdminSelectedGame();
        if (!gameId) return;

        const response = await fetch(`/api/v1/games/${gameId}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (response.ok) {
          const data = await response.json();
          setGame(data);
        }
      } catch (error) {
        console.error('Failed to fetch game:', error);
      }
    };
    fetchGame();
  }, []);

  useEffect(() => {
    if (!game) return;

    const updateClock = () => {
      const now = new Date();
      const start = new Date(game.startTime);
      const end = new Date(game.endTime);

      if (now < start) {
        setStatus('before');
        setTimeDisplay(formatDuration(start.getTime() - now.getTime()));
      } else if (now >= start && now <= end) {
        setStatus('during');
        setTimeDisplay(formatDuration(now.getTime() - start.getTime()));
      } else {
        setStatus('after');
        setTimeDisplay('Finished');
      }
    };

    updateClock();
    const interval = setInterval(updateClock, 1000);
    return () => clearInterval(interval);
  }, [game]);

  const statusConfig = {
    before: { label: 'Starts in', color: 'warning' as const },
    during: { label: 'Elapsed', color: 'success' as const },
    after: { label: 'Completed', color: 'disabled' as const },
  };

  // Chip colors need separate config since they support 'default'
  const chipColors = {
    before: 'warning' as const,
    during: 'success' as const,
    after: 'default' as const,
  };

  if (collapsed) {
    return (
      <Box sx={{ textAlign: 'center', py: 1 }}>
        <ClockIcon color={statusConfig[status].color} />
        <Typography variant="caption" display="block" sx={{ fontFamily: 'monospace' }}>
          {timeDisplay}
        </Typography>
      </Box>
    );
  }

  return (
    <Card sx={{ mb: 2 }}>
      <CardContent sx={{ pb: '12px !important' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
          <ClockIcon sx={{ mr: 1, color: 'primary.main' }} fontSize="small" />
          <Typography variant="subtitle2" color="text.secondary">
            Game Clock
          </Typography>
        </Box>
        <Box>
          <Typography
            variant="h4"
            component="div"
            sx={{ fontFamily: 'monospace', mb: 1 }}
          >
            {timeDisplay}
          </Typography>
          <Chip
            label={statusConfig[status].label}
            size="small"
            color={chipColors[status]}
          />
        </Box>
      </CardContent>
    </Card>
  );
}
