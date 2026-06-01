/**
 * Rivals Page - Mini leaderboard of [me + up to 5 rivals].
 * Editable as long as the game has not yet ended.
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  Container,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import GroupAddIcon from '@mui/icons-material/GroupAdd';
import { useTeamStore, type RankingEntry } from '../../store/useTeamStore';
import { useGameStore } from '../../store/useGameStore';
import teamService from '../../services/teamService';
import type { PublicTeam, TeamOption } from '../../types/entities';

const MAX_RIVALS = 5;

export default function Rivals() {
  const { team, rivalTeams, setRivalTeams } = useTeamStore();
  const { game } = useGameStore();

  const [eligible, setEligible] = useState<TeamOption[]>([]);
  const [loadingEligible, setLoadingEligible] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [selection, setSelection] = useState<TeamOption[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isLocked = game?.status === 'completed';

  // Initial rival fetch — useRealtime keeps it fresh after that.
  useEffect(() => {
    if (!team) return;
    teamService.getRivalTeams(team.id).then(setRivalTeams).catch(() => undefined);
  }, [team, setRivalTeams]);

  // The backend delivers an already-filtered [me + rivals] set: RIVAL_UPDATE keeps
  // rivalTeams fresh during the game and refreshes our own team, so we just compose
  // the two here with group-relative positions — no client-side filtering of a full
  // ranking (which would leak every team's score during the game).
  const leaderboard = useMemo(() => {
    if (!team) return [] as Array<RankingEntry & { isMe: boolean }>;
    const entries: RankingEntry[] = [
      { position: 0, teamId: team.id, teamName: team.name, points: team.totalPoints },
      ...rivalTeams.map((r) => ({ position: 0, teamId: r.id, teamName: r.name, points: r.totalPoints })),
    ];
    return entries
      .sort((a, b) => b.points - a.points)
      .map((r, i) => ({ ...r, position: i + 1, isMe: r.teamId === team.id }));
  }, [team, rivalTeams]);

  const openDialog = async () => {
    if (!team) return;
    setError(null);
    setLoadingEligible(true);
    try {
      const teams = await teamService.getEligibleRivalTeams(team.gameId, team.id);
      teams.sort((a, b) => a.name.localeCompare(b.name, 'nl'));
      setEligible(teams);
      // Preselect currently chosen rivals so removal works through the same picker.
      setSelection(
        teams.filter((t) => rivalTeams.some((r) => r.id === t.id))
      );
      setDialogOpen(true);
    } catch (e) {
      setError('Kon de teamlijst niet laden.');
      console.warn('Failed to load eligible rivals', e);
    } finally {
      setLoadingEligible(false);
    }
  };

  const handleSave = async () => {
    if (!team) return;
    setSubmitting(true);
    setError(null);
    const result = await teamService.setRivalTeams(team.id, selection.map((s) => s.id));
    setSubmitting(false);
    if (result.error) {
      setError(result.error);
      return;
    }
    // The server WS message will also refresh; set immediately to feel snappy.
    const publicShape: PublicTeam[] = result.rivals;
    setRivalTeams(publicShape);
    setDialogOpen(false);
  };

  if (!team) {
    return (
      <Container maxWidth="md" sx={{ mt: 3 }}>
        <Typography>Team laden…</Typography>
      </Container>
    );
  }

  return (
    <Container maxWidth="md" sx={{ mt: 3, mb: 6 }}>
      <Stack spacing={2}>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <Typography variant="h4" component="h1">
            Rivalen
          </Typography>
          {!isLocked ? (
            <Button
              variant="contained"
              startIcon={<GroupAddIcon />}
              onClick={openDialog}
              disabled={loadingEligible}
            >
              Rivalen beheren
            </Button>
          ) : (
            <Chip label="Rivaalkeuze is gesloten" color="default" />
          )}
        </Box>

        <Typography variant="body2" color="text.secondary">
          Kies maximaal {MAX_RIVALS} andere teams om hun stand bij te houden.
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
                {leaderboard.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={3} sx={{ color: 'text.secondary' }}>
                      Nog geen rivalen gekozen.
                    </TableCell>
                  </TableRow>
                )}
                {leaderboard.map((row) => (
                  <TableRow key={row.teamId} selected={row.isMe}>
                    <TableCell>{row.position}</TableCell>
                    <TableCell>
                      {row.teamName}
                      {row.isMe && (
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

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Rivalen kiezen</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Selecteer maximaal {MAX_RIVALS} teams. Begin te typen om te filteren.
            </Typography>
            <Autocomplete
              multiple
              options={eligible}
              value={selection}
              onChange={(_, value) => {
                if (value.length > MAX_RIVALS) {
                  setError(`Je kunt maximaal ${MAX_RIVALS} rivalen kiezen.`);
                  return;
                }
                setError(null);
                setSelection(value);
              }}
              isOptionEqualToValue={(option, val) => option.id === val.id}
              getOptionLabel={(option) => option.name}
              openOnFocus
              filterSelectedOptions
              renderValue={(value, getTagProps) =>
                value.map((option, index) => (
                  <Chip
                    label={option.name}
                    {...getTagProps({ index })}
                    key={option.id}
                  />
                ))
              }
              renderInput={(params) => (
                <TextField {...params} label="Zoek een team" placeholder="Begin te typen…" />
              )}
            />
            {error && <Alert severity="error">{error}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={submitting}>
            Annuleren
          </Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={submitting || selection.length > MAX_RIVALS}
          >
            Opslaan
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
