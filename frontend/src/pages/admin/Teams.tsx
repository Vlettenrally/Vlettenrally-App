import { Box, Typography, Button, Card, CardContent, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Chip } from '@mui/material';
import { Upload as ImportIcon, Add as AddIcon } from '@mui/icons-material';
import { useEffect, useState } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { teamService } from '../../services/teamService';
import { useAdminWebSocket } from '../../hooks/useWebSocket';
import { websocket } from '../../services/websocket';
import TeamDetailDialog from '../../components/TeamDetailDialog';
import type { AdminTeam } from '../../types/entities';

export default function Teams() {
  const selectedGameId = useGameStore((s) => s.selectedGameId);
  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [selectedTeam, setSelectedTeam] = useState<AdminTeam | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);

  // Connect to WebSocket for real-time updates
  useAdminWebSocket(selectedGameId);

  // Load teams when game changes
  useEffect(() => {
    if (!selectedGameId) {
      setTeams([]);
      return;
    }
    
    let cancelled = false;
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
  };

  const handleNewTeam = () => {
    setSelectedTeam(null);
    setDialogOpen(true);
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

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Teams
        </Typography>
        <Box sx={{ display: 'flex', gap: 2 }}>
          <Button variant="outlined" startIcon={<ImportIcon />}>
            Import Teams
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
                  <TableCell><strong>Boat Number</strong></TableCell>
                  <TableCell><strong>Description</strong></TableCell>
                  <TableCell align="center"><strong>Checked In</strong></TableCell>
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
                    <TableCell>{team.boatNumber}</TableCell>
                    <TableCell>{team.description}</TableCell>
                    <TableCell align="center">
                      <Chip 
                        label={team.checkedIn ? 'Yes' : 'No'} 
                        color={team.checkedIn ? 'success' : 'default'}
                        size="small"
                      />
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
