import { Box, Typography, Button, Card, CardContent, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip, CircularProgress, Tooltip, Dialog, DialogTitle, DialogContent, DialogActions, TextField } from '@mui/material';
import { Upload as ImportIcon, Link as LinkIcon, Add as AddIcon, CheckCircle as SuccessIcon, Error as ErrorIcon, GpsFixed as TrackerIcon, Download as DownloadIcon } from '@mui/icons-material';
import { useCallback, useEffect, useState } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { teamService } from '../../services/teamService';
import { gameService } from '../../services/gameService';
import { websocket } from '../../services/websocket';
import TeamDetailDialog from '../../components/TeamDetailDialog';
import type { AdminTeam, Game, TeamImportResult, TrackerAssignResult, ParticipantLinkResult } from '../../types/entities';

type DetailDialog = { title: string; items: string[] };

export default function Teams() {
  const selectedGameId = useGameStore((s) => s.selectedGameId);
  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [selectedTeam, setSelectedTeam] = useState<AdminTeam | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [game, setGame] = useState<Game | null>(null);
  
  // Import state
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState<TeamImportResult | null>(null);
  const [importError, setImportError] = useState<string | null>(null);

  // Participant linking state
  const [linking, setLinking] = useState(false);
  const [linkResult, setLinkResult] = useState<ParticipantLinkResult | null>(null);
  const [linkError, setLinkError] = useState<string | null>(null);

  // Tracker assignment state
  const [trackerDialogOpen, setTrackerDialogOpen] = useState(false);
  const [trackerInput, setTrackerInput] = useState('');
  const [assigning, setAssigning] = useState(false);
  const [assignResult, setAssignResult] = useState<TrackerAssignResult | null>(null);
  const [assignError, setAssignError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);

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
      setImportError(err instanceof Error ? err.message : 'Importeren mislukt');
    } finally {
      setImporting(false);
    }
  };

  const handleLinkParticipants = async () => {
    if (!selectedGameId) return;

    setLinking(true);
    setLinkResult(null);
    setLinkError(null);
    setDetail(null);

    try {
      const result = await gameService.linkParticipants(selectedGameId);
      setLinkResult(result);
      // Refresh teams list to reflect newly linked participants
      teamService.getTeamsByGame(selectedGameId).then(setTeams);
    } catch (err) {
      setLinkError(err instanceof Error ? err.message : 'Koppelen mislukt');
    } finally {
      setLinking(false);
    }
  };

  const handleAssignTrackers = async () => {
    if (!selectedGameId) return;

    // Split the pasted blob into individual tracker IDs (one per line, also
    // tolerating commas/whitespace between IDs); the backend de-dupes and trims.
    const trackerIds = trackerInput
      .split(/[\s,;]+/)
      .map((id) => id.trim())
      .filter((id) => id.length > 0);

    setAssigning(true);
    setAssignResult(null);
    setAssignError(null);

    try {
      const result = await gameService.assignTrackers(selectedGameId, trackerIds);
      setAssignResult(result);
      setTrackerDialogOpen(false);
      setTrackerInput('');
      // Refresh teams so the assigned tracker IDs show up.
      teamService.getTeamsByGame(selectedGameId).then(setTeams);
    } catch (err) {
      setAssignError(err instanceof Error ? err.message : 'Toewijzen mislukt');
    } finally {
      setAssigning(false);
    }
  };

  const handleExportTrackers = async () => {
    if (!selectedGameId) return;

    setExporting(true);
    try {
      const csv = await gameService.exportTrackers(selectedGameId);
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `trackers-${selectedGameId}.csv`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      setAssignError(err instanceof Error ? err.message : 'Export mislukt');
    } finally {
      setExporting(false);
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
  const linkedTeams = linkResult?.linkedTeams ?? [];
  const unmatchedParticipants = linkResult?.unmatchedParticipants ?? [];

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Teams
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          {importResult && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Chip
                label={`${importResult.created} aangemaakt`}
                color="success"
                size="small"
                icon={<SuccessIcon />}
                clickable={importResult.createdTeams.length > 0}
                onClick={() => openDetail('Aangemaakte teams', importResult.createdTeams)}
              />
              <Chip
                label={`${importResult.updated} bijgewerkt`}
                color="info"
                size="small"
                clickable={importResult.updatedTeams.length > 0}
                onClick={() => openDetail('Bijgewerkte teams', importResult.updatedTeams)}
              />
              {importResult.skipped > 0 && (
                <Chip
                  label={`${importResult.skipped} overgeslagen`}
                  color="warning"
                  size="small"
                  clickable={importResult.skippedDuplicates.length > 0}
                  onClick={() => openDetail('Overgeslagen (dubbele bootnummers)', importResult.skippedDuplicates)}
                />
              )}
              {importResult.errors > 0 && (
                <Chip
                  label={`${importResult.errors} fouten`}
                  color="error"
                  size="small"
                  icon={<ErrorIcon />}
                  clickable={importResult.errorDetails.length > 0}
                  onClick={() => openDetail('Fouten', importResult.errorDetails)}
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
          {linkResult && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Chip
                label={`${linkResult.linked} gekoppeld`}
                color="success"
                size="small"
                icon={<SuccessIcon />}
                clickable={linkedTeams.length > 0}
                onClick={() => openDetail('Gekoppelde teams', linkedTeams)}
              />
              {linkResult.unmatched > 0 && (
                <Chip
                  label={`${linkResult.unmatched} zonder team`}
                  color="warning"
                  size="small"
                  clickable={unmatchedParticipants.length > 0}
                  onClick={() => openDetail('Deelnemers zonder team', unmatchedParticipants)}
                />
              )}
            </Box>
          )}
          {linkError && (
            <Chip
              label={linkError}
              color="error"
              size="small"
              icon={<ErrorIcon />}
            />
          )}
          {assignResult && (
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Chip
                label={`${assignResult.assigned} toegewezen`}
                color="success"
                size="small"
                icon={<SuccessIcon />}
                clickable={assignResult.assignedPairs.length > 0}
                onClick={() => openDetail('Toegewezen trackers', assignResult.assignedPairs)}
              />
              {assignResult.teamsWithoutTracker > 0 && (
                <Chip
                  label={`${assignResult.teamsWithoutTracker} zonder tracker`}
                  color="warning"
                  size="small"
                  clickable={assignResult.teamsWithoutTrackerNames.length > 0}
                  onClick={() => openDetail('Teams zonder tracker', assignResult.teamsWithoutTrackerNames)}
                />
              )}
              {assignResult.unused > 0 && (
                <Chip
                  label={`${assignResult.unused} ongebruikt`}
                  color="info"
                  size="small"
                  clickable={assignResult.unusedTrackers.length > 0}
                  onClick={() => openDetail('Ongebruikte trackers', assignResult.unusedTrackers)}
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
              title={isCompleted ? "Teams importeren is niet mogelijk nadat de wedstrijd is afgelopen" : ""}
              arrow
            >
              <span>
                <Button
                  variant="outlined"
                  startIcon={importing ? <CircularProgress size={20} /> : <ImportIcon />}
                  onClick={handleImportTeams}
                  disabled={!canImport || importing}
                >
                  {importing ? 'Importeren...' : 'Teams Importeren'}
                </Button>
              </span>
            </Tooltip>
          )}
          {game?.locationEndpointUrl && (
            <Button
              variant="outlined"
              startIcon={linking ? <CircularProgress size={20} /> : <LinkIcon />}
              onClick={handleLinkParticipants}
              disabled={linking}
            >
              {linking ? 'Koppelen...' : 'Koppel deelnemers'}
            </Button>
          )}
          <Button
            variant="outlined"
            startIcon={<TrackerIcon />}
            onClick={() => {
              setAssignError(null);
              setTrackerDialogOpen(true);
            }}
          >
            Trackers toewijzen
          </Button>
          <Button
            variant="outlined"
            startIcon={exporting ? <CircularProgress size={20} /> : <DownloadIcon />}
            onClick={handleExportTrackers}
            disabled={exporting}
          >
            Export trackers
          </Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={handleNewTeam}>
            New Team
          </Button>
        </Box>
      </Box>
      <Card>
        <CardContent>
          <Typography variant="h6" sx={{ mb: 2 }}>
            {teams.length} team{teams.length !== 1 ? 's' : ''} loaded
          </Typography>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell><strong>Team Name</strong></TableCell>
                  <TableCell><strong>Description</strong></TableCell>
                  <TableCell><strong>Tracker</strong></TableCell>
                  <TableCell align="right"><strong>Points</strong></TableCell>
                  <TableCell align="center"><strong>Checked In</strong></TableCell>
                  <TableCell align="center"><strong>Digital</strong></TableCell>
                  <TableCell align="center"><strong>Finish</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {teams.map((team) => (
                  <TableRow 
                    key={team.id} 
                    hover 
                    onClick={() => handleRowClick(team)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>{team.name}</TableCell>
                    <TableCell>{team.description}</TableCell>
                    <TableCell>{team.trackerId || '—'}</TableCell>
                    <TableCell align="right">{team.totalPoints}</TableCell>
                    <TableCell align="center">
                      <Chip 
                        label={team.checkedIn ? 'Yes' : 'No'} 
                        color={team.checkedIn ? 'success' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell align="center">
                      {team.checkedIn && (
                        <Chip
                          label={team.checkedInDigitally ? 'Yes' : 'No'}
                          color={team.checkedInDigitally ? 'info' : 'warning'}
                          size="small"
                          variant="outlined"
                        />
                      )}
                    </TableCell>
                    <TableCell align="center">
                      {team.finished && (
                        <Chip label="Gefinisht" color="success" size="small" />
                      )}
                    </TableCell>
                  </TableRow>
                ))}
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

      <Dialog open={trackerDialogOpen} onClose={() => setTrackerDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Trackers toewijzen</DialogTitle>
        <DialogContent dividers>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Plak de tracker-ID's (één per regel). Ze worden automatisch verdeeld over teams die nog
            geen tracker hebben. Al toegewezen ID's worden overgeslagen.
          </Typography>
          <TextField
            label="Tracker-ID's"
            value={trackerInput}
            onChange={(e) => setTrackerInput(e.target.value)}
            multiline
            rows={10}
            fullWidth
            autoFocus
            placeholder={'860123456789012\n860123456789013\n860123456789014'}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTrackerDialogOpen(false)}>Annuleren</Button>
          <Button
            variant="contained"
            onClick={handleAssignTrackers}
            disabled={assigning || trackerInput.trim().length === 0}
            startIcon={assigning ? <CircularProgress size={20} /> : <TrackerIcon />}
          >
            {assigning ? 'Toewijzen...' : 'Toewijzen'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={detail !== null} onClose={handleCloseDetail} fullWidth maxWidth="sm">
        <DialogTitle>{detail?.title}</DialogTitle>
        <DialogContent dividers>
          {!detail || detail.items.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              Geen resultaten.
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
          <Button onClick={handleCloseDetail}>Sluiten</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
