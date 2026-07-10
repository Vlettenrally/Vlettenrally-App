/**
 * Admin Ranking - Live leaderboard for staff (position / team / points).
 *
 * Unlike the participant Rankings page, this shows the full standings at all
 * times, regardless of whether rankings have been published: the backend
 * streams RANKING_UPDATE to the "ranking" admin topic on every points change
 * (see PointsLedgerService.publishStandings). Gated behind the `ranking` role.
 */
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Box,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Alert,
  CircularProgress,
} from '@mui/material';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { useAdminTopicHeartbeat, ADMIN_TOPICS } from '../../hooks/useAdminHeartbeat';
import websocket, { WebSocketEventType } from '../../services/websocket';
import api from '../../services/api';
import type { RankingEntry } from '../../types/entities';

export default function Ranking() {
  const { selectedGameId } = useGameStore();
  const { t } = useTranslation();
  const [rankings, setRankings] = useState<RankingEntry[]>([]);
  const [loading, setLoading] = useState(false);

  const loadRankings = useCallback(async () => {
    if (!selectedGameId) return;
    setLoading(true);
    try {
      const response = await api.getRankings(selectedGameId);
      if (response.data) setRankings(response.data as RankingEntry[]);
    } finally {
      setLoading(false);
    }
  }, [selectedGameId]);

  // Only receive ranking events from the backend while this page is mounted.
  useAdminTopicHeartbeat(ADMIN_TOPICS.RANKING);

  // Initial load + reload after a reconnect/resync.
  useEffect(() => {
    void loadRankings();
  }, [loadRankings]);
  useAdminResync(() => void loadRankings());

  // Live standings: replace with the freshly-computed ranking on each update.
  useEffect(() => {
    const unsubscribe = websocket.on(WebSocketEventType.RANKING_UPDATE, (m) => {
      setRankings(m.payload as RankingEntry[]);
    });
    return () => unsubscribe();
  }, []);

  if (!selectedGameId) {
    return (
      <Box sx={{ p: { xs: 2, md: 3 } }}>
        <Typography variant="h4">{t('admin.ranking.title')}</Typography>
        <Alert severity="info" sx={{ mt: 2 }}>{t('admin.ranking.selectGameFirst')}</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Typography variant="h4" component="h1">{t('admin.ranking.title')}</Typography>
          {loading && <CircularProgress size={20} />}
        </Stack>
        <Typography variant="body2" color="text.secondary">
          {t('admin.ranking.subtitle')}
        </Typography>

        <Paper>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell width={60}>{t('admin.ranking.rank')}</TableCell>
                  <TableCell>{t('admin.ranking.team')}</TableCell>
                  <TableCell align="right">{t('admin.ranking.points')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {!loading && rankings.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} sx={{ color: 'text.secondary' }}>
                      {t('admin.ranking.noData')}
                    </TableCell>
                  </TableRow>
                )}
                {rankings.map((row) => (
                  <TableRow key={row.teamId}>
                    <TableCell>{row.position}</TableCell>
                    <TableCell>{row.teamName}</TableCell>
                    <TableCell align="right">{row.points}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      </Stack>
    </Box>
  );
}
