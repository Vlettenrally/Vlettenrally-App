/**
 * Check-In Progress Widget - Shows team check-in status
 */
import { useState, useEffect } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  LinearProgress,
  CircularProgress,
} from '@mui/material';
import { HowToReg as CheckInIcon } from '@mui/icons-material';
import { storage } from '../../services/storage';

interface CheckInProgressWidgetProps {
  collapsed?: boolean;
}

export function CheckInProgressWidget({ collapsed = false }: CheckInProgressWidgetProps) {
  const [checkedIn, setCheckedIn] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchTeams = async () => {
      try {
        const token = storage.getAuthToken();
        const gameId = storage.getAdminSelectedGame();
        if (!gameId) {
          setLoading(false);
          return;
        }

        const response = await fetch(`/api/v1/games/${gameId}/teams`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (response.ok) {
          const teams = await response.json();
          setTotal(teams.length);
          setCheckedIn(teams.filter((t: { checkedIn: boolean }) => t.checkedIn).length);
        }
      } catch (error) {
        console.error('Failed to fetch teams:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchTeams();
  }, []);

  const progress = total > 0 ? (checkedIn / total) * 100 : 0;

  if (collapsed) {
    return (
      <Box sx={{ textAlign: 'center', py: 1 }}>
        <CheckInIcon color={progress === 100 ? 'success' : 'primary'} />
        <Typography variant="caption" display="block">
          {checkedIn}/{total}
        </Typography>
      </Box>
    );
  }

  return (
    <Card sx={{ mb: 2 }}>
      <CardContent sx={{ pb: '12px !important' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
          <CheckInIcon sx={{ mr: 1, color: 'primary.main' }} fontSize="small" />
          <Typography variant="subtitle2" color="text.secondary">
            Check-In Progress
          </Typography>
        </Box>
        {loading ? (
          <CircularProgress size={20} />
        ) : (
          <Box>
            <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 1, mb: 1 }}>
              <Typography variant="h4" component="span">
                {checkedIn}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                / {total} teams
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={progress}
              sx={{ height: 6, borderRadius: 1 }}
              color={progress === 100 ? 'success' : 'primary'}
            />
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
