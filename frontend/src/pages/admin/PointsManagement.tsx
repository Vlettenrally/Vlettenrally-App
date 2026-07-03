import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
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
import { useAdminTopicHeartbeat, ADMIN_TOPICS } from '../../hooks/useAdminHeartbeat';
import websocket, { WebSocketEventType } from '../../services/websocket';
import { usePointEntryStore } from '../../store/usePointEntryStore';
import { teamService } from '../../services/teamService';
import api from '../../services/api';
import EditEntryDialog from '../../components/admin/EditEntryDialog';
import CreateAdjustmentDialog from '../../components/admin/CreateAdjustmentDialog';
import MapPopupDialog from '../../components/admin/MapPopupDialog';
import { teamMatchesQuery } from '../../utils/teamSearch';
import type { AdminTeam, Checkpoint, PointEntry, PointEntryType } from '../../types/entities';

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Credited distance for a distance-bonus entry, as km (>= 1 km) or m. */
function formatDistance(meters: number): string {
  if (meters >= 1000) {
    return `${(meters / 1000).toLocaleString(undefined, {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    })} km`;
  }
  return `${Math.round(meters)} m`;
}

export default function PointsManagement() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  const ENTRY_TYPE_LABELS: Record<PointEntryType, string> = {
    CHECKPOINT_VERIFICATION: t('admin.pointsManagement.typeCheckpoint'),
    MANUAL_ADJUSTMENT: t('admin.pointsManagement.typeManual'),
    AUTOMATION_BONUS: t('admin.pointsManagement.typeAutomation'),
    DISTANCE_BONUS: t('admin.pointsManagement.typeDistance'),
  };

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
      setError(response.error || t('admin.pointsManagement.failedToLoad'));
      return;
    }
    setEntries(response.data.entries, {
      page: response.data.page,
      pageSize: response.data.pageSize,
      totalCount: response.data.totalCount,
      totalPages: response.data.totalPages,
    });
  }, [selectedGameId, filterTeam, filterType, page, pageSize, setEntries, setLoading, setError, t]);

  useEffect(() => {
    if (!selectedGameId) return;
    void loadEntries({ page: 0 });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedGameId, filterTeam, filterType]);

  useAdminResync(() => void loadEntries());

  // Only receive point-ledger events from the backend while this page is mounted.
  useAdminTopicHeartbeat(ADMIN_TOPICS.POINTS);

  // Live point-ledger updates: fold new/edited entries into the store.
  useEffect(() => {
    const unsubCreated = websocket.on(WebSocketEventType.POINT_ENTRY_CREATED, (m) =>
      addEntry(m.payload as PointEntry),
    );
    const unsubUpdated = websocket.on(WebSocketEventType.POINT_ENTRY_UPDATED, (m) =>
      updateEntry(m.payload as PointEntry),
    );
    const unsubReset = websocket.on(WebSocketEventType.TEAM_LEDGER_RESET, () =>
      void loadEntries({ page: 0 }),
    );
    return () => {
      unsubCreated();
      unsubUpdated();
      unsubReset();
    };
  }, [addEntry, updateEntry, loadEntries]);

  const teamNameById = useMemo(() => {
    const map = new Map<string, string>();
    teams.forEach((team) => map.set(team.id, team.name));
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
        <Typography variant="h4">{t('admin.pointsManagement.title')}</Typography>
        <Alert severity="info" sx={{ mt: 2 }}>{t('admin.pointsManagement.selectGameFirst')}</Alert>
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
        <Typography variant="h4">{t('admin.pointsManagement.title')}</Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => void loadEntries()}
            disabled={isLoading}
          >
            {t('admin.pointsManagement.refresh')}
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setCreateOpen(true)}
          >
            {t('admin.pointsManagement.manualAdjustment')}
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
              getOptionLabel={(team) => team.name}
              filterOptions={(options, { inputValue }) =>
                options.filter((option) => teamMatchesQuery(option, inputValue))
              }
              renderOption={({ key, ...props }, option) => (
                <Box key={key} component="li" {...props}>
                  <Box>
                    <Typography variant="body2">{option.name}</Typography>
                    {option.description && (
                      <Typography variant="caption" color="text.secondary" display="block">
                        {option.description}
                      </Typography>
                    )}
                  </Box>
                </Box>
              )}
              isOptionEqualToValue={(a, b) => a.id === b.id}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label={t('common.team')}
                  placeholder={t('admin.pointsManagement.allTeamsPlaceholder')}
                />
              )}
              sx={{ minWidth: { md: 260 }, flex: { xs: 1, md: 'none' } }}
            />
            <TextField
              select
              label="Type"
              value={filterType}
              onChange={(e) => setFilterType(e.target.value as PointEntryType | '')}
              sx={{ minWidth: { md: 200 } }}
            >
              <MenuItem value="">{t('admin.pointsManagement.allTypes')}</MenuItem>
              <MenuItem value="CHECKPOINT_VERIFICATION">{t('admin.pointsManagement.typeCheckpoint')}</MenuItem>
              <MenuItem value="MANUAL_ADJUSTMENT">{t('admin.pointsManagement.typeManual')}</MenuItem>
              <MenuItem value="AUTOMATION_BONUS">{t('admin.pointsManagement.typeAutomation')}</MenuItem>
              <MenuItem value="DISTANCE_BONUS">{t('admin.pointsManagement.typeDistance')}</MenuItem>
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
                      <TableCell><strong>{t('admin.pointsManagement.columnTime')}</strong></TableCell>
                      <TableCell><strong>{t('admin.pointsManagement.columnTeam')}</strong></TableCell>
                      <TableCell><strong>{t('admin.pointsManagement.columnType')}</strong></TableCell>
                      <TableCell><strong>{t('admin.pointsManagement.columnDescription')}</strong></TableCell>
                      <TableCell align="right"><strong>{t('admin.pointsManagement.columnOriginal')}</strong></TableCell>
                      <TableCell align="right"><strong>{t('admin.pointsManagement.columnAdjustment')}</strong></TableCell>
                      <TableCell align="right"><strong>{t('admin.pointsManagement.columnCurrent')}</strong></TableCell>
                      <TableCell align="center"><strong>{t('admin.pointsManagement.columnLocation')}</strong></TableCell>
                      <TableCell align="center"><strong>{t('admin.pointsManagement.columnActions')}</strong></TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {entries.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={9} align="center">
                          <Typography variant="body2" color="text.secondary">
                            {t('admin.pointsManagement.noEntries')}
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                    {entries.map((entry) => {
                      const delta = entry.originalAmount - entry.currentAmount;
                      const teamName = teamNameById.get(entry.teamId) ?? '—';
                      const wasEdited = entry.editedAt != null;
                      // Distance bonuses accrue into one entry over time, so the
                      // original/adjustment framing doesn't apply — show the full
                      // value plus the distance travelled instead.
                      const isDistance = entry.entryType === 'DISTANCE_BONUS';
                      return (
                        <TableRow key={entry.id} hover>
                          <TableCell sx={{ whiteSpace: 'nowrap' }}>
                            {/* Distance bonuses accrue into one row over time, so show when
                                they last grew rather than the frozen creation time. */}
                            {formatTimestamp(isDistance ? entry.updatedAt : entry.createdAt)}
                          </TableCell>
                          <TableCell>{teamName}</TableCell>
                          <TableCell>
                            <Chip
                              size="small"
                              label={ENTRY_TYPE_LABELS[entry.entryType]}
                              color={
                                entry.entryType === 'CHECKPOINT_VERIFICATION' ? 'primary'
                                : entry.entryType === 'DISTANCE_BONUS' ? 'success'
                                : entry.entryType === 'AUTOMATION_BONUS' ? 'secondary'
                                : 'default'
                              }
                              variant="outlined"
                            />
                          </TableCell>
                          <TableCell>{entry.description ?? ''}</TableCell>
                          <TableCell align="right">{isDistance ? '—' : entry.originalAmount}</TableCell>
                          <TableCell align="right">
                            {isDistance ? (
                              '—'
                            ) : delta !== 0 ? (
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
                            {isDistance && entry.distanceMeters != null && (
                              <Typography variant="caption" color="text.secondary" display="block">
                                {formatDistance(entry.distanceMeters)}
                              </Typography>
                            )}
                          </TableCell>
                          <TableCell align="center">
                            {entry.lat != null && entry.lng != null ? (
                              <Tooltip title={t('admin.pointsManagement.showOnMap')}>
                                <IconButton size="small" onClick={() => setMapTarget(entry)}>
                                  <LocationOnIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            ) : (
                              '—'
                            )}
                          </TableCell>
                          <TableCell align="center">
                            {entry.entryType !== 'DISTANCE_BONUS' && (
                              <Tooltip title={t('admin.pointsManagement.edit')}>
                                <IconButton size="small" onClick={() => setEditTarget(entry)}>
                                  <EditIcon fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            )}
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
                labelRowsPerPage={t('admin.pointsManagement.rowsPerPage')}
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
              {t('admin.pointsManagement.editReason')}
            </Typography>
            <Typography variant="body2" sx={{ mb: 1, whiteSpace: 'pre-wrap' }}>
              {reasonPopover.entry.editReason || t('admin.pointsManagement.noReason')}
            </Typography>
            {reasonPopover.entry.editedAt && (
              <Typography variant="caption" color="text.secondary">
                {t('admin.pointsManagement.editedAt', { date: formatTimestamp(reasonPopover.entry.editedAt) })}
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
          label={teamNameById.get(mapTarget.teamId) ?? t('common.unknown')}
          checkpoints={checkpoints}
        />
      )}
    </Box>
  );
}
