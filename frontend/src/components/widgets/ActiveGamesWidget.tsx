/**
 * Active Games Widget - Shows count and status of running games
 */
import { useState, useEffect } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  CircularProgress,
  Chip,
} from '@mui/material';
import { SportsScore as GamesIcon } from '@mui/icons-material';
import { storage } from '../../services/storage';

interface Game {
  id: string;
  name: string;
  status: 'planned' | 'active' | 'completed';
}

interface ActiveGamesWidgetProps {
  collapsed?: boolean;
}

export function ActiveGamesWidget({ collapsed = false }: ActiveGamesWidgetProps) {
  const [games, setGames] = useState<Game[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchGames = async () => {
      try {
        const token = storage.getAuthToken();
        const response = await fetch('/api/v1/games', {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (response.ok) {
          const data = await response.json();
          setGames(data);
        }
      } catch (error) {
        console.error('Failed to fetch games:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchGames();
  }, []);

  const activeGames = games.filter((g) => g.status === 'active');
  const plannedGames = games.filter((g) => g.status === 'planned');

  if (collapsed) {
    return (
      <Box sx={{ textAlign: 'center', py: 1 }}>
        <GamesIcon color="primary" />
        <Typography variant="caption" display="block">
          {activeGames.length}
        </Typography>
      </Box>
    );
  }

  return (
    <Card sx={{ mb: 2 }}>
      <CardContent sx={{ pb: '12px !important' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
          <GamesIcon sx={{ mr: 1, color: 'primary.main' }} fontSize="small" />
          <Typography variant="subtitle2" color="text.secondary">
            Games
          </Typography>
        </Box>
        {loading ? (
          <CircularProgress size={20} />
        ) : (
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mb: 1 }}>
              <Typography variant="h4" component="span">
                {activeGames.length}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                active
              </Typography>
            </Box>
            {plannedGames.length > 0 && (
              <Chip
                label={`${plannedGames.length} planned`}
                size="small"
                color="default"
                variant="outlined"
              />
            )}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
