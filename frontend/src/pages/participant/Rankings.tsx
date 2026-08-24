/**
 * Rankings Page - Final game leaderboard. Only reachable once an admin has
 * revealed the standings at the award ceremony (game.rankingsPublished);
 * App.tsx's RankingsRoute redirects elsewhere before the reveal.
 */
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Container,
  Fab,
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
  Zoom,
} from '@mui/material';
import { ArrowUpward as ArrowUpwardIcon, MyLocation as MyLocationIcon } from '@mui/icons-material';
import { useTeamStore } from '../../store/useTeamStore';
import { useGameStore } from '../../store/useGameStore';
import api from '../../services/api';
import TeamFinishChip from '../../components/TeamFinishChip';
import TeamClassChip from '../../components/TeamClassChip';
import type { RankingEntry } from '../../types/entities';

export default function Rankings() {
  const { team, rankings, setRankings } = useTeamStore();
  const { game } = useGameStore();
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const ownRowRef = useRef<HTMLTableRowElement | null>(null);
  const [ownRowVisible, setOwnRowVisible] = useState(true);
  const [scrolled, setScrolled] = useState(false);

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

  // The sticky AppBar covers the top of the viewport, so a row scrolled behind
  // it does not count as visible.
  useEffect(() => {
    const node = ownRowRef.current;
    if (!node) {
      setOwnRowVisible(true);
      return;
    }
    const observer = new IntersectionObserver(
      ([entry]) => setOwnRowVisible(entry.isIntersecting),
      { rootMargin: '-64px 0px 0px 0px' },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [rankings, team?.id]);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 100);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const fabMode = !ownRowVisible ? 'me' : scrolled ? 'top' : 'hidden';
  const fabLabel = t(
    fabMode === 'me' ? 'participant.rankings.goToMyTeam' : 'participant.rankings.backToTop',
  );

  const handleFabClick = () => {
    if (fabMode === 'me') {
      ownRowRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    } else {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  return (
    <Container maxWidth="md" sx={{ mt: 3, mb: 12 }}>
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          {t('participant.rankings.title')}
        </Typography>
        <Typography variant="body2" color="textSecondary">
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
                  <TableCell align="center">{t('participant.rankings.class')}</TableCell>
                  <TableCell align="center">{t('participant.rankings.finish')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {!loading && rankings.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} sx={{ color: 'text.secondary' }}>
                      {t('participant.rankings.noData')}
                    </TableCell>
                  </TableRow>
                )}
                {rankings.map((row) => (
                  <TableRow
                    key={row.teamId}
                    ref={row.teamId === team?.id ? ownRowRef : undefined}
                    selected={row.teamId === team?.id}
                  >
                    <TableCell>{row.disqualified ? t('participant.rankings.disqualifiedPosition') : row.position}</TableCell>
                    <TableCell>
                      {row.teamName}
                      {row.teamId === team?.id && (
                        <Chip size="small" label={t('participant.rankings.you')} sx={{ ml: 1 }} color="primary" />
                      )}
                    </TableCell>
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

      <Zoom in={fabMode !== 'hidden'} unmountOnExit>
        <Fab
          variant="extended"
          color="primary"
          onClick={handleFabClick}
          aria-label={fabLabel}
          sx={{
            position: 'fixed',
            bottom: 24,
            right: 24,
            zIndex: (theme) => theme.zIndex.fab,
          }}
        >
          {fabMode === 'me' ? (
            <MyLocationIcon sx={{ mr: 1 }} />
          ) : (
            <ArrowUpwardIcon sx={{ mr: 1 }} />
          )}
          {fabLabel}
        </Fab>
      </Zoom>
    </Container>
  );
}
