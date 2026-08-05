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
  Button,
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
import { Download as DownloadIcon } from '@mui/icons-material';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { useAdminTopicHeartbeat, ADMIN_TOPICS } from '../../hooks/useAdminHeartbeat';
import websocket, { WebSocketEventType } from '../../services/websocket';
import api from '../../services/api';
import { gameService } from '../../services/gameService';
import { downloadFile } from '../../utils/download';
import TeamFinishChip from '../../components/TeamFinishChip';
import TeamClassChip from '../../components/TeamClassChip';
import type { RankingEntry } from '../../types/entities';

export default function Ranking() {
  const { selectedGameId } = useGameStore();
  // The finish chip needs the game's end time and status to tell finished from late from DNF.
  // AdminLayout keeps `games` populated on mount and on every resync.
  const game = useGameStore((s) => s.games.find((g) => g.id === s.selectedGameId) ?? null);
  const { t } = useTranslation();
  const [rankings, setRankings] = useState<RankingEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

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

  // The file holds more than this table shows — scouting group and the admin-only team
  // notes — so it is built server-side rather than from the rows in state.
  const handleExport = async () => {
    if (!selectedGameId) return;
    setExporting(true);
    setExportError(null);
    try {
      const file = await gameService.exportRankings(selectedGameId);
      downloadFile(file, `ranglijst-${selectedGameId}.csv`);
    } catch (err) {
      setExportError(err instanceof Error ? err.message : t('admin.ranking.exportFailed'));
    } finally {
      setExporting(false);
    }
  };

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
          <Box sx={{ flex: 1 }} />
          <Button
            variant="outlined"
            startIcon={exporting ? <CircularProgress size={20} /> : <DownloadIcon />}
            onClick={handleExport}
            disabled={exporting}
          >
            {t('admin.ranking.export')}
          </Button>
        </Stack>
        <Typography variant="body2" color="text.secondary">
          {t('admin.ranking.subtitle')}
        </Typography>

        {exportError && <Alert severity="error" onClose={() => setExportError(null)}>{exportError}</Alert>}

        <Paper>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell width={60}>{t('admin.ranking.rank')}</TableCell>
                  <TableCell>{t('admin.ranking.team')}</TableCell>
                  <TableCell align="right">{t('admin.ranking.points')}</TableCell>
                  <TableCell align="center">{t('admin.ranking.class')}</TableCell>
                  <TableCell align="center">{t('admin.ranking.finish')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {!loading && rankings.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} sx={{ color: 'text.secondary' }}>
                      {t('admin.ranking.noData')}
                    </TableCell>
                  </TableRow>
                )}
                {rankings.map((row) => (
                  <TableRow key={row.teamId}>
                    <TableCell>{row.position}</TableCell>
                    <TableCell>{row.teamName}</TableCell>
                    <TableCell align="right">{row.points}</TableCell>
                    <TableCell align="center">
                      <TeamClassChip team={row} />
                    </TableCell>
                    <TableCell align="center">
                      <TeamFinishChip team={row} game={game} />
                    </TableCell>
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
