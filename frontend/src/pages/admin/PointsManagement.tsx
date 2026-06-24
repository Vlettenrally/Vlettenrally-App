import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Autocomplete,
  Box,
  Typography,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Chip,
  Button,
  TextField,
  MenuItem,
  IconButton,
  Tooltip,
  Stack,
  Alert,
  CircularProgress,
  Popover,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  LocationOn as LocationOnIcon,
  Refresh as RefreshIcon,
} from '@mui/icons-material';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { usePointEntryStore } from '../../store/usePointEntryStore';
import { teamService } from '../../services/teamService';
import api from '../../services/api';
import EditEntryDialog from '../../components/admin/EditEntryDialog';
import CreateAdjustmentDialog from '../../components/admin/CreateAdjustmentDialog';
import MapPopupDialog from '../../components/admin/MapPopupDialog';
import type { AdminTeam, Checkpoint, PointEntry, PointEntryType } from '../../types/entities';

const ENTRY_TYPE_LABELS: Record<PointEntryType, string> = {
  CHECKPOINT_VERIFICATION: 'Checkpoint',
  MANUAL_ADJUSTMENT: 'Handmatig',
  AUTOMATION_BONUS: 'Automatisering',
  DISTANCE_BONUS: 'Afstand',
};

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString('nl-NL', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function PointsManagement() {
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  const entries = usePointEntryStore((s) => s.entries);
  const totalCount = usePointEntryStore((s) => s.totalCount);
  const page = usePointEntryStore((s) => s.page);
  const pageSize = usePointEntryStore((s) => s.pageSize);
  const isLoading = usePointEntryStore((s) => s.isLoading);
  const error = usePointEntryStore((s) => s.error);
  const setEntries = usePointEntryStore((s) => s.setEntries);
  const updateEntry = usePointEntryStore((s) => s.updateEntry);
  const addEntry = usePointEntryStore((s) => s.addEntry);
  const setLoading = usePointEntryStore((s) => s.setLoading);
  const setError = usePointEntryStore((s) => s.setError);

  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
  const [filterTeam, setFilterTeam] = useState<AdminTeam | null>(null);
  const [filterType, setFilterType] = useState<PointEntryType | ''>('');

  const [editTarget, setEditTarget] = useState<PointEntry | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [mapTarget, setMapTarget] = useState<PointEntry | null>(null);
  const [reasonPopover, setReasonPopover] = useState<{ anchor: HTMLElement; entry: PointEntry } | null>(null);

  // Load teams + checkpoints when the game changes (used for filters and map overlays)
  useEffect(() => {
    if (!selectedGameId) {
      setTeams([]);
      setCheckpoints([]);
      return;
    }
    let cancelled = false;
    teamService.getTeamsByGame(selectedGameId).then((data) => {
      if (!cancelled) setTeams(data);
    });
    api.getCheckpoints(selectedGameId).then((response) => {
      if (cancelled) return;
      setCheckpoints(response.data ?? []);
    });
    return () => { cancelled = true; };
  }, [selectedGameId]);

  const loadEntries = useCallback(async (overrides?: { page?: number; pageSize?: number }) => {
    if (!selectedGameId) return;
    setLoading(true);
    setError(null);
    const response = await api.getPointEntries(selectedGameId, {
      teamId: filterTeam?.id || undefined,
      entryType: filterType || undefined,
      page: overrides?.page ?? page,
      pageSize: overrides?.pageSize ?? pageSize,
    });
    setLoading(false);
    if (response.error || !response.data) {
      setError(response.error || 'Kon ledger niet laden');
      return;
    }
    setEntries(response.data.entries, {
      page: response.data.page,
      pageSize: response.data.pageSize,
      totalCount: response.data.totalCount,
      totalPages: response.data.totalPages,
    });
  }, [selectedGameId, filterTeam, filterType, page, pageSize, setEntries, setLoading, setError]);

  // Reload when game or filters change
  useEffect(() => {
    if (!selectedGameId) return;
    void loadEntries({ page: 0 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedGameId, filterTeam, filterType]);

  // Reload the ledger on resync (app resume / reconnect) to catch point entries
  // whose POINT_ENTRY_* events were missed while suspended.
  useAdminResync(() => void loadEntries());

  const teamNameById = useMemo(() => {
    const map = new Map<string, string>();
    teams.forEach((t) => map.set(t.id, t.name));
    return map;
  }, [teams]);

  const handleChangePage = (_: unknown, newPage: number) => {
    void loadEntries({ page: newPage });
  };

  const handleChangePageSize = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newSize = Number(e.target.value);
    void loadEntries({ page: 0, pageSize: newSize });
  };

  if (!selectedGameId) {
    return (
      <Box sx={{ p: { xs: 2, md: 3 } }}>
        <Typography variant="h4">Puntenbeheer</Typography>
        <Alert severity="info" sx={{ mt: 2 }}>Kies eerst een spel in de zijbalk.</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: { xs: 1.5, md: 3 } }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        alignItems={{ xs: 'stretch', sm: 'center' }}
        justifyContent="space-between"
        sx={{ mb: 2 }}
      >
        <Typography variant="h4">Puntenbeheer</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => void loadEntries()}
            disabled={isLoading}
          >
            Vernieuwen
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateOpen(true)}
          >
            Handmatige correctie
          </Button>
        </Stack>
      </Stack>

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <Autocomplete
              value={filterTeam}
              onChange={(_, newValue) => setFilterTeam(newValue)}
              options={teams}
              getOptionLabel={(t) => t.name}
              isOptionEqualToValue={(a, b) => a.id === b.id}
              renderInput={(params) => <TextField {...params} label="Team" placeholder="Alle teams" />}
              sx={{ minWidth: { md: 260 }, flex: { xs: 1, md: 'none' } }}
            />
            <TextField
              select
              label="Type"
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as PointEntryType | '')}
              sx={{ minWidth: { md: 200 } }}
            >
              <MenuItem value="">Alle</MenuItem>
              <MenuItem value="CHECKPOINT_VERIFICATION">Checkpoint</MenuItem>
              <MenuItem value="MANUAL_ADJUSTMENT">Handmatig</MenuItem>
            </TextField>
          </Stack>
        </CardContent>
      </Card>

      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <Card>
        <CardContent sx={{ p: { xs: 1, md: 2 } }}>
          {isLoading && entries.length === 0 ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
              <CircularProgress />
            </Box>
          ) : (
            <>
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table size="small" sx={{ minWidth: 900 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell><strong>Tijd</strong></TableCell>
                      <TableCell><strong>Team</strong></TableCell>
                      <TableCell><strong>Type</strong></TableCell>
                      <TableCell><strong>Beschrijving</strong></TableCell>
                      <TableCell align="right"><strong>Origineel</strong></TableCell>
                      <TableCell align="right"><strong>Aanpassing</strong></TableCell>
                      <TableCell align="right"><strong>Huidig</strong></TableCell>
                      <TableCell align="center"><strong>Locatie</strong></TableCell>
                      <TableCell align="center"><strong>Acties</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {entries.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={9} align="center">
                          <Typography variant="body2" color="text.secondary">
                            Geen punten geregistreerd voor deze selectie.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                    {entries.map((entry) => {
                      const delta = entry.originalAmount - entry.currentAmount;
                      const teamName = teamNameById.get(entry.teamId) ?? '—';
                      const wasEdited = entry.editedAt != null;
                      return (
                        <TableRow key={entry.id} hover>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>
                            {formatTimestamp(entry.createdAt)}
                          </TableCell>
                          <TableCell>{teamName}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={ENTRY_TYPE_LABELS[entry.entryType]}
                              color={entry.entryType === 'CHECKPOINT_VERIFICATION' ? 'primary' : 'default'}
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell>{entry.description ?? ''}</TableCell>
                          <TableCell align="right">{entry.originalAmount}</TableCell>
                          <TableCell align="right">
                            {delta !== 0 ? (
                              <Chip
                                size="small"
                                color={delta > 0 ? 'error' : 'success'}
                                label={delta > 0 ? `-${delta}` : `+${-delta}`}
                                onClick={wasEdited ? (e) => setReasonPopover({ anchor: e.currentTarget, entry }) : undefined}
                                sx={wasEdited ? { cursor: 'pointer' } : undefined}
                              />
                            ) : (
                              '—'
                            )}
                          </TableCell>
                          <TableCell align="right">
                            <strong>{entry.currentAmount}</strong>
                          </TableCell>
                          <TableCell align="center">
                            {entry.lat != null && entry.lng != null ? (
                              <Tooltip title="Toon op kaart">
                                <IconButton size="small" onClick={() => setMapTarget(entry)}>
                                  <LocationOnIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            ) : (
                              '—'
                            )}
                          </TableCell>
                          <TableCell align="center">
                            <Tooltip title="Bewerken">
                              <IconButton size="small" onClick={() => setEditTarget(entry)}>
                                <EditIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                component="div"
                count={totalCount}
                page={page}
                onPageChange={handleChangePage}
                rowsPerPage={pageSize}
                onRowsPerPageChange={handleChangePageSize}
                rowsPerPageOptions={[10, 25, 50, 100]}
                labelRowsPerPage="Per pagina"
              />
            </>
          )}
        </CardContent>
      </Card>

      <Popover
        open={reasonPopover !== null}
        anchorEl={reasonPopover?.anchor ?? null}
        onClose={() => setReasonPopover(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        transformOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        {reasonPopover && (
          <Box sx={{ p: 2, maxWidth: 320 }}>
            <Typography variant="subtitle2" gutterBottom>
              Reden voor wijziging
            </Typography>
            <Typography variant="body2" sx={{ mb: 1, whiteSpace: 'pre-wrap' }}>
              {reasonPopover.entry.editReason || '(geen reden opgegeven)'}
            </Typography>
            {reasonPopover.entry.editedAt && (
              <Typography variant="caption" color="text.secondary">
                Bewerkt op {formatTimestamp(reasonPopover.entry.editedAt)}
              </Typography>
            )}
          </Box>
        )}
      </Popover>

      <EditEntryDialog
        open={editTarget !== null}
        onClose={() => setEditTarget(null)}
        entry={editTarget}
        onSaved={(entry) => updateEntry(entry)}
      />

      <CreateAdjustmentDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        gameId={selectedGameId}
        teams={teams}
        onSaved={(entry) => addEntry(entry)}
      />

      {mapTarget?.lat != null && mapTarget?.lng != null && (
        <MapPopupDialog
          open={mapTarget !== null}
          onClose={() => setMapTarget(null)}
          lat={mapTarget.lat}
          lng={mapTarget.lng}
          label={teamNameById.get(mapTarget.teamId) ?? 'Punt'}
          checkpoints={checkpoints}
        />
      )}
    </Box>
  );
}
