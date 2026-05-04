import { Box, Typography, Button, Card, CardContent, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip, CircularProgress, Tooltip } from '@mui/material';
import { Upload as ImportIcon, Add as AddIcon, CheckCircle as SuccessIcon, Error as ErrorIcon } from '@mui/icons-material';
import { useEffect, useState } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { teamService } from '../../services/teamService';
import { gameService } from '../../services/gameService';
import { websocket } from '../../services/websocket';
import TeamDetailDialog from '../../components/TeamDetailDialog';
import type { AdminTeam, Game, TeamImportResult } from '../../types/entities';

/**
 * Check if check-in has started based on checkInStartTime
 */
const hasCheckInStarted = (game: Game | null): boolean => {
  if (!game?.checkInStartTime) return false;
  return new Date() >= new Date(game.checkInStartTime);
};

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

  // Load game and teams when game changes
  useEffect(() => {
    if (!selectedGameId) {
      setGame(null);
      setTeams([]);
      return;
    }
    
    let cancelled = false;
    
    // Load game details
    gameService.getGame(selectedGameId).then((data) => {
      if (!cancelled) setGame(data);
    });
    
    // Load teams
    teamService.getTeamsByGame(selectedGameId).then((data) => {
      if (!cancelled) setTeams(data);
    });
    
    return () => {
      cancelled = true;
    };
  }, [selectedGameId]);

  // Subscribe to WebSocket team events
  useEffect(() => {
    if (!selectedGameId) return;
    
    const refreshTeams = () => {
      teamService.getTeamsByGame(selectedGameId).then(setTeams);
    };
    
    const unsubscribeCreated = websocket.on('team:created', refreshTeams);
    const unsubscribeUpdated = websocket.on('team:updated', refreshTeams);

    return () => {
      unsubscribeCreated();
      unsubscribeUpdated();
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

  const checkInStarted = hasCheckInStarted(game);
  const canImport = game?.externalEventId && !checkInStarted;

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
          {game?.externalEventId && (
            <Tooltip 
              title={checkInStarted ? "Teams importeren is niet mogelijk nadat de check-in is gestart" : ""}
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
    </Box>
  );
}
