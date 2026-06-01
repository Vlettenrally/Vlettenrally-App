/**
 * Rankings Page - Final game leaderboard. Only reachable once the game is COMPLETED;
 * App.tsx redirects elsewhere while the game is still PLANNED/ACTIVE.
 */
import { useEffect, useState } from 'react';
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
          Eindstand
        </Typography>
        <Typography variant="body2" color="text.secondary">
          De definitieve ranglijst van {game?.name ?? 'het spel'}.
        </Typography>

        <Paper>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell width={60}>#</TableCell>
                  <TableCell>Team</TableCell>
                  <TableCell align="right">Punten</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {!loading && rankings.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} sx={{ color: 'text.secondary' }}>
                      Geen ranglijstgegevens beschikbaar.
                    </TableCell>
                  </TableRow>
                )}
                {rankings.map((row) => (
                  <TableRow key={row.teamId} selected={row.teamId === team?.id}>
                    <TableCell>{row.position}</TableCell>
                    <TableCell>
                      {row.teamName}
                      {row.teamId === team?.id && (
                        <Chip size="small" label="Jij" sx={{ ml: 1 }} color="primary" />
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
