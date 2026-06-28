/**
 * Rankings Page - Final game leaderboard. Only reachable once an admin has
 * revealed the standings at the award ceremony (game.rankingsPublished);
 * App.tsx's RankingsRoute redirects elsewhere before the reveal.
 */
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Container,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  Chip,
} from '@mui/material';
import { useTeamStore } from '../../store/useTeamStore';
import { useGameStore } from '../../store/useGameStore';
import api from '../../services/api';
import type { RankingEntry } from '../../types/entities';

export default function Rankings() {
  const { team, rankings, setRankings } = useTeamStore();
  const { game } = useGameStore();
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const gameId = game?.id;
    if (!gameId) return;
    let active = true;
    (async () => {
      setLoading(true);
      try {
        const response = await api.getRankings(gameId);
        if (active && response.data) setRankings(response.data as RankingEntry[]);
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [game?.id, setRankings]);

  return (
    <Container maxWidth="md" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          {t('participant.rankings.title')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {t('participant.rankings.subtitle', { game: game?.name ?? 'het spel' })}
        </Typography>

        <Paper>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell width={60}>{t('participant.rankings.rank')}</TableCell>
                  <TableCell>{t('participant.rankings.team')}</TableCell>
                  <TableCell align="right">{t('participant.rankings.points')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {!loading && rankings.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} sx={{ color: 'text.secondary' }}>
                      {t('participant.rankings.noData')}
                    </TableCell>
                  </TableRow>
                )}
                {rankings.map((row) => (
                  <TableRow key={row.teamId} selected={row.teamId === team?.id}>
                    <TableCell>{row.position}</TableCell>
                    <TableCell>
                      {row.teamName}
                      {row.teamId === team?.id && (
                        <Chip size="small" label={t('participant.rankings.you')} sx={{ ml: 1 }} color="primary" />
                      )}
                    </TableCell>
                    <TableCell align="right">{row.points}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      </Stack>
    </Container>
  );
}
