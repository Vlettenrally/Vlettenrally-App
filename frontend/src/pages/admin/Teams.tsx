import { Box, Typography, Button, Card, CardContent, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TableSortLabel, Chip, CircularProgress, Tooltip, Dialog, DialogTitle, DialogContent, DialogActions, TextField } from '@mui/material';
import { Upload as ImportIcon, Add as AddIcon, CheckCircle as SuccessIcon, Error as ErrorIcon, GpsFixed as TrackerIcon, Download as DownloadIcon } from '@mui/icons-material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { teamService } from '../../services/teamService';
import { gameService } from '../../services/gameService';
import { websocket } from '../../services/websocket';
import TeamDetailDialog from '../../components/TeamDetailDialog';
import TrackerExportDialog from '../../components/TrackerExportDialog';
import TeamCheckInChip from '../../components/TeamCheckInChip';
import TeamFinishChip from '../../components/TeamFinishChip';
import TrackerHandedInChip from '../../components/TrackerHandedInChip';
import { teamMatchesQuery } from '../../utils/teamSearch';
import { checkInStatusRank } from '../../utils/checkInStatus';
import { finishStatusRank } from '../../utils/finishStatus';
import { trackerHandInStatusRank } from '../../utils/trackerHandInStatus';
import type { AdminTeam, Game, TeamImportResult, TrackerAssignResult } from '../../types/entities';

type DetailDialog = { title: string; items: string[] };

type SortColumn =
  | 'name'
  | 'description'
  | 'trackerId'
  | 'trackerHandedIn'
  | 'totalPoints'
  | 'checkInStatus'
  | 'finished';
type SortOrder = 'asc' | 'desc';

const columns: { column: SortColumn; labelKey: string; align?: 'right' | 'center' }[] = [
  { column: 'name', labelKey: 'admin.teams.columnTeamName' },
  { column: 'description', labelKey: 'admin.teams.columnDescription' },
  { column: 'trackerId', labelKey: 'admin.teams.columnTracker' },
  { column: 'trackerHandedIn', labelKey: 'admin.teams.columnTrackerHandedIn', align: 'center' },
  { column: 'totalPoints', labelKey: 'admin.teams.columnPoints', align: 'right' },
  { column: 'checkInStatus', labelKey: 'admin.teams.columnCheckInStatus', align: 'center' },
  { column: 'finished', labelKey: 'admin.teams.columnFinish', align: 'center' },
];

const compareByName = (a: AdminTeam, b: AdminTeam) =>
  a.name.localeCompare(b.name, 'nl', { sensitivity: 'base' });

function compareTeams(a: AdminTeam, b: AdminTeam, column: SortColumn, game: Game | null): number {
  switch (column) {
    case 'name':
      return compareByName(a, b);
    case 'description':
      return a.description.localeCompare(b.description, 'nl', { sensitivity: 'base' });
    case 'trackerId':
      return (a.trackerId ?? '').localeCompare(b.trackerId ?? '', 'nl', { sensitivity: 'base' });
    case 'trackerHandedIn':
      return trackerHandInStatusRank(a) - trackerHandInStatusRank(b);
    case 'totalPoints':
      return a.totalPoints - b.totalPoints;
    case 'checkInStatus':
      return checkInStatusRank(a) - checkInStatusRank(b);
    case 'finished':
      return finishStatusRank(a, game) - finishStatusRank(b, game);
  }
}

export default function Teams() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);
  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [orderBy, setOrderBy] = useState<SortColumn>('name');
  const [order, setOrder] = useState<SortOrder>('asc');
  const [selectedTeam, setSelectedTeam] = useState<AdminTeam | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [game, setGame] = useState<Game | null>(null);

  // Import state
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<TeamImportResult | null>(null);
  const [importError, setImportError] = useState<string | null>(null);

  // Tracker assignment state
  const [trackerDialogOpen, setTrackerDialogOpen] = useState(false);
  const [assigning, setAssigning] = useState(false);
  const [assignResult, setAssignResult] = useState<TrackerAssignResult | null>(null);
  const [assignError, setAssignError] = useState<string | null>(null);
  const [exportDialogOpen, setExportDialogOpen] = useState(false);

  // Shared detail dialog for clickable result chips (import + linking)
  const [detail, setDetail] = useState<DetailDialog | null>(null);

  // Reload game details + teams. Used on game change and on resync (app resume /
  // reconnect) to top up state that missed team:* WebSocket events.
  const reloadGameAndTeams = useCallback(async () => {
    if (!selectedGameId) {
      setGame(null);
      setTeams([]);
      return;
    }
    const [gameData, teamData] = await Promise.all([
      gameService.getGame(selectedGameId),
      teamService.getTeamsByGame(selectedGameId),
    ]);
    setGame(gameData);
    setTeams(teamData);
  }, [selectedGameId]);

  // Load game and teams when game changes
  useEffect(() => {
    void reloadGameAndTeams();
  }, [reloadGameAndTeams]);

  useAdminResync(reloadGameAndTeams);

  // Subscribe to WebSocket team events
  useEffect(() => {
    if (!selectedGameId) return;

    const refreshTeams = () => {
      teamService.getTeamsByGame(selectedGameId).then(setTeams);
    };

    const unsubscribeCreated = websocket.on('team:created', refreshTeams);
    const unsubscribeUpdated = websocket.on('team:updated', refreshTeams);
    const unsubscribeDeleted = websocket.on('team:deleted', refreshTeams);

    return () => {
      unsubscribeCreated();
      unsubscribeUpdated();
      unsubscribeDeleted();
    };
  }, [selectedGameId]);

  const handleRowClick = (team: AdminTeam) => {
    setSelectedTeam(team);
    setDialogOpen(true);
    // Clear import result when starting a new action
    setImportResult(null);
    setImportError(null);
  };

  const handleNewTeam = () => {
    setSelectedTeam(null);
    setDialogOpen(true);
    // Clear import result when starting a new action
    setImportResult(null);
    setImportError(null);
  };

  const handleDialogClose = () => {
    setDialogOpen(false);
    setSelectedTeam(null);
  };

  const handleSave = () => {
    if (selectedGameId) {
      teamService.getTeamsByGame(selectedGameId).then(setTeams);
    }
  };

  const handleImportTeams = async () => {
    if (!selectedGameId) return;

    setImporting(true);
    setImportResult(null);
    setImportError(null);

    try {
      const result = await gameService.importTeams(selectedGameId);
      setImportResult(result);
      // Refresh teams list after import
      teamService.getTeamsByGame(selectedGameId).then(setTeams);
    } catch (err) {
      setImportError(err instanceof Error ? err.message : t('admin.teams.importFailed'));
    } finally {
      setImporting(false);
    }
  };

  const handleAssignTrackers = async () => {
    if (!selectedGameId) return;

    setAssigning(true);
    setAssignResult(null);
    setAssignError(null);

    try {
      const result = await gameService.assignTrackers(selectedGameId);
      setAssignResult(result);
      setTrackerDialogOpen(false);
      // Refresh teams so the assigned tracker IDs show up.
      teamService.getTeamsByGame(selectedGameId).then(setTeams);
    } catch (err) {
      setAssignError(err instanceof Error ? err.message : t('admin.teams.assignFailed'));
    } finally {
      setAssigning(false);
    }
  };

  // Open the shared detail dialog for a result chip. No-op when there's nothing to show.
  const openDetail = (title: string, items: string[]) => {
    if (items.length === 0) return;
    setDetail({ title, items });
  };

  const handleCloseDetail = () => {
    setDetail(null);
  };

  const isCompleted = game?.status === 'completed';
  const canImport = game?.externalEventId && !isCompleted;

  const handleSort = (column: SortColumn) => {
    if (orderBy === column) {
      setOrder(order === 'asc' ? 'desc' : 'asc');
    } else {
      setOrderBy(column);
      setOrder('asc');
    }
  };

  const filteredTeams = useMemo(
    () => teams.filter((team) => teamMatchesQuery(team, searchQuery)),
    [teams, searchQuery],
  );

  const sortedTeams = useMemo(() => {
    const sign = order === 'asc' ? 1 : -1;
    return [...filteredTeams].sort(
      (a, b) => sign * compareTeams(a, b, orderBy, game) || compareByName(a, b),
    );
  }, [filteredTeams, orderBy, order, game]);

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          {t('admin.teams.title')}
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          {importResult && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Chip
                label={t('admin.teams.importCreated', { count: importResult.created })}
                color="success"
                size="small"
                icon={<SuccessIcon />}
                clickable={importResult.createdTeams.length > 0}
                onClick={() => openDetail(t('admin.teams.createdTeamsTitle'), importResult.createdTeams)}
              />
              <Chip
                label={t('admin.teams.importUpdated', { count: importResult.updated })}
                color="info"
                size="small"
                clickable={importResult.updatedTeams.length > 0}
                onClick={() => openDetail(t('admin.teams.updatedTeamsTitle'), importResult.updatedTeams)}
              />
              {importResult.skipped > 0 && (
                <Chip
                  label={t('admin.teams.importSkipped', { count: importResult.skipped })}
                  color="warning"
                  size="small"
                  clickable={importResult.skippedDuplicates.length > 0}
                  onClick={() => openDetail(t('admin.teams.skippedTeamsTitle'), importResult.skippedDuplicates)}
                />
              )}
              {importResult.errors > 0 && (
                <Chip
                  label={t('admin.teams.importErrors', { count: importResult.errors })}
                  color="error"
                  size="small"
                  icon={<ErrorIcon />}
                  clickable={importResult.errorDetails.length > 0}
                  onClick={() => openDetail(t('admin.teams.errorsTitle'), importResult.errorDetails)}
                />
              )}
            </Box>
          )}
          {importError && (
            <Chip
              label={importError}
              color="error"
              size="small"
              icon={<ErrorIcon />}
            />
          )}
          {assignResult && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Chip
                label={t('admin.teams.assignedCount', { count: assignResult.assigned })}
                color="success"
                size="small"
                icon={<SuccessIcon />}
                clickable={assignResult.assignedPairs.length > 0}
                onClick={() => openDetail(t('admin.teams.assignedTrackersTitle'), assignResult.assignedPairs)}
              />
              {assignResult.teamsWithoutTracker > 0 && (
                <Chip
                  label={t('admin.teams.withoutTracker', { count: assignResult.teamsWithoutTracker })}
                  color="warning"
                  size="small"
                  clickable={assignResult.teamsWithoutTrackerNames.length > 0}
                  onClick={() => openDetail(t('admin.teams.noTrackerTeamsTitle'), assignResult.teamsWithoutTrackerNames)}
                />
              )}
              {assignResult.unused > 0 && (
                <Chip
                  label={t('admin.teams.unusedTrackers', { count: assignResult.unused })}
                  color="info"
                  size="small"
                  clickable={assignResult.unusedTrackers.length > 0}
                  onClick={() => openDetail(t('admin.teams.unusedTrackersTitle'), assignResult.unusedTrackers)}
                />
              )}
            </Box>
          )}
          {assignError && (
            <Chip
              label={assignError}
              color="error"
              size="small"
              icon={<ErrorIcon />}
            />
          )}
          {game?.externalEventId && (
            <Tooltip
              title={isCompleted ? t('admin.teams.importDisabledTooltip') : ''}
              arrow
            >
              <span>
                <Button
                  variant="outlined"
                  startIcon={importing ? <CircularProgress size={20} /> : <ImportIcon />}
                  onClick={handleImportTeams}
                  disabled={!canImport || importing}
                >
                  {importing ? t('admin.teams.importing') : t('admin.teams.importTeams')}
                </Button>
              </span>
            </Tooltip>
          )}
          <Button
            variant="outlined"
            startIcon={<TrackerIcon />}
            onClick={() => {
              setAssignError(null);
              setTrackerDialogOpen(true);
            }}
          >
            {t('admin.teams.assignTrackers')}
          </Button>
          <Button
            variant="outlined"
            startIcon={<DownloadIcon />}
            onClick={() => setExportDialogOpen(true)}
            disabled={!selectedGameId}
          >
            {t('admin.teams.exportTrackers')}
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleNewTeam}>
            {t('admin.teams.newTeam')}
          </Button>
        </Box>
      </Box>
      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            {t('admin.teams.teamsLoaded', { count: teams.length })}
          </Typography>
          <TextField
            size="small"
            fullWidth
            placeholder={t('admin.teams.searchTeam')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            sx={{ mb: 2 }}
          />
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  {columns.map(({ column, labelKey, align }) => (
                    <TableCell
                      key={column}
                      align={align}
                      sortDirection={orderBy === column ? order : false}
                    >
                      <TableSortLabel
                        active={orderBy === column}
                        direction={orderBy === column ? order : 'asc'}
                        onClick={() => handleSort(column)}
                      >
                        <strong>{t(labelKey)}</strong>
                      </TableSortLabel>
                    </TableCell>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {sortedTeams.map((team) => (
                  <TableRow
                    key={team.id}
                    hover
                    onClick={() => handleRowClick(team)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>{team.name}</TableCell>
                    <TableCell>{team.description}</TableCell>
                    <TableCell>{team.trackerId || '—'}</TableCell>
                    <TableCell align="center">
                      <TrackerHandedInChip team={team} />
                    </TableCell>
                    <TableCell align="right">{team.totalPoints}</TableCell>
                    <TableCell align="center">
                      <TeamCheckInChip team={team} />
                    </TableCell>
                    <TableCell align="center">
                      <TeamFinishChip team={team} game={game} />
                    </TableCell>
                  </TableRow>
                ))}
                {filteredTeams.length === 0 && teams.length > 0 && (
                  <TableRow>
                    <TableCell colSpan={columns.length}>
                      <Typography variant="body2" color="text.secondary">
                        {t('admin.teams.noResults')}
                      </Typography>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>
        </CardContent>
      </Card>

      <TeamDetailDialog
        open={dialogOpen}
        onClose={handleDialogClose}
        team={selectedTeam}
        gameId={selectedGameId || ''}
        onSave={handleSave}
      />

      <TrackerExportDialog
        open={exportDialogOpen}
        onClose={() => setExportDialogOpen(false)}
        gameId={selectedGameId || ''}
      />

      <Dialog open={trackerDialogOpen} onClose={() => setTrackerDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t('admin.teams.trackerDialogTitle')}</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body2" sx={{ mb: 2 }}>
            {t('admin.teams.trackerDialogConfirm', {
              count: teams.filter((team) => !team.trackerId).length,
            })}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('admin.teams.trackerDialogHint')}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTrackerDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button
            variant="contained"
            onClick={handleAssignTrackers}
            disabled={assigning}
            startIcon={assigning ? <CircularProgress size={20} /> : <TrackerIcon />}
          >
            {assigning ? t('admin.teams.assigning') : t('admin.teams.assign')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={detail !== null} onClose={handleCloseDetail} fullWidth maxWidth="sm">
        <DialogTitle>{detail?.title}</DialogTitle>
        <DialogContent dividers>
          {!detail || detail.items.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              {t('admin.teams.noResults')}
            </Typography>
          ) : (
            <Box component="ul" sx={{ m: 0, pl: 3 }}>
              {detail.items.map((item, index) => (
                <Box component="li" key={`${item}-${index}`} sx={{ mb: 0.5 }}>
                  <Typography variant="body2">{item}</Typography>
                </Box>
              ))}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDetail}>{t('common.close')}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
