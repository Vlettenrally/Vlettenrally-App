import { Box, Typography, Button, Card, CardContent, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip, CircularProgress, Tooltip, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';
import { Upload as ImportIcon, Link as LinkIcon, Add as AddIcon, CheckCircle as SuccessIcon, Error as ErrorIcon } from '@mui/icons-material';
import { useCallback, useEffect, useState } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { teamService } from '../../services/teamService';
import { gameService } from '../../services/gameService';
import { websocket } from '../../services/websocket';
import TeamDetailDialog from '../../components/TeamDetailDialog';
import type { AdminTeam, Game, TeamImportResult, ParticipantLinkResult } from '../../types/entities';

type LinkResultStatus = 'linked' | 'unmatched';

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
  const [linkDetailsOpen, setLinkDetailsOpen] = useState(false);
  const [linkDetailsStatus, setLinkDetailsStatus] = useState<LinkResultStatus>('linked');

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
    setLinkDetailsOpen(false);

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

  const handleOpenLinkDetails = (status: LinkResultStatus) => {
    if (!linkResult) return;
    const items = status === 'linked' ? linkResult.linkedTeams : linkResult.unmatchedParticipants;
    if (items.length === 0) return;
    setLinkDetailsStatus(status);
    setLinkDetailsOpen(true);
  };

  const handleCloseLinkDetails = () => {
    setLinkDetailsOpen(false);
  };

  const isCompleted = game?.status === 'completed';
  const canImport = game?.externalEventId && !isCompleted;
  const linkedTeams = linkResult?.linkedTeams ?? [];
  const unmatchedParticipants = linkResult?.unmatchedParticipants ?? [];
  const detailItems = linkDetailsStatus === 'linked' ? linkedTeams : unmatchedParticipants;
  const detailTitle = linkDetailsStatus === 'linked' ? 'Gekoppelde teams' : 'Deelnemers zonder team';

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
              />
              <Chip 
                label={`${importResult.updated} bijgewerkt`} 
                color="info" 
                size="small" 
              />
              {importResult.errors > 0 && (
                <Chip 
                  label={`${importResult.errors} fouten`} 
                  color="error" 
                  size="small" 
                  icon={<ErrorIcon />}
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
                onClick={() => handleOpenLinkDetails('linked')}
              />
              {linkResult.unmatched > 0 && (
                <Chip
                  label={`${linkResult.unmatched} zonder team`}
                  color="warning"
                  size="small"
                  clickable={unmatchedParticipants.length > 0}
                  onClick={() => handleOpenLinkDetails('unmatched')}
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

      <Dialog open={linkDetailsOpen} onClose={handleCloseLinkDetails} fullWidth maxWidth="sm">
        <DialogTitle>{detailTitle}</DialogTitle>
        <DialogContent dividers>
          {detailItems.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              Geen resultaten.
            </Typography>
          ) : (
            <Box component="ul" sx={{ m: 0, pl: 3 }}>
              {detailItems.map((item, index) => (
                <Box component="li" key={`${item}-${index}`} sx={{ mb: 0.5 }}>
                  <Typography variant="body2">{item}</Typography>
                </Box>
              ))}
            </Box>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseLinkDetails}>Sluiten</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
