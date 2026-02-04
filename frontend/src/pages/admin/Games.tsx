import { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Button,
  Card,
  CardContent,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  CircularProgress,
  Alert,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import { gameService } from '../../services/gameService';
import { useGameStore } from '../../store/useGameStore';
import { useAdminWebSocket } from '../../hooks/useWebSocket';
import type { Game } from '../../types/entities';
import GameFormDialog from '../../components/GameFormDialog';

const getStatusColor = (status?: string): 'default' | 'warning' | 'success' | 'error' => {
  switch (status?.toLowerCase()) {
    case 'planned':
      return 'warning';
    case 'active':
      return 'success';
    case 'completed':
      return 'default';
    default:
      return 'default';
  }
};

const formatDateTime = (date?: Date): string => {
  if (!date) return '-';
  try {
    return date.toLocaleString('nl-NL', {
      dateStyle: 'short',
      timeStyle: 'short',
    });
  } catch {
    return '-';
  }
};

const formatStatus = (status?: string): string => {
  if (!status) return 'Unknown';
  return status.charAt(0).toUpperCase() + status.slice(1);
};

const Games = () => {
  const [localError, setLocalError] = useState<string | null>(null);
  
  // Dialog states
  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [selectedGame, setSelectedGame] = useState<Game | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [gameToDelete, setGameToDelete] = useState<Game | null>(null);
  const [deleting, setDeleting] = useState(false);

  // Get games, loading state, and actions from store
  const { games, isLoading, error, selectedGameId, fetchGames, setSelectedGameId } = useGameStore();
  const { subscribe } = useAdminWebSocket(selectedGameId);

  useEffect(() => {
    fetchGames();
  }, [fetchGames]);

  // Listen for WebSocket game status updates
  useEffect(() => {
    const unsubscribe = subscribe('GAME_STATUS', () => {
      // Refresh games when status changes
      fetchGames();
    });
    return () => unsubscribe();
  }, [subscribe, fetchGames]);

  const handleCreateGame = () => {
    setSelectedGame(null);
    setFormDialogOpen(true);
  };

  const handleEditGame = (game: Game) => {
    setSelectedGame(game);
    setFormDialogOpen(true);
  };

  const handleDeleteClick = (game: Game) => {
    setGameToDelete(game);
    setDeleteDialogOpen(true);
  };

  const handleDeleteConfirm = async () => {
    if (!gameToDelete) return;

    setDeleting(true);
    try {
      await gameService.deleteGame(gameToDelete.id);
      setDeleteDialogOpen(false);
      setGameToDelete(null);
      await fetchGames();
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : 'Failed to delete game');
    } finally {
      setDeleting(false);
    }
  };

  const handleFormClose = () => {
    setFormDialogOpen(false);
    setSelectedGame(null);
  };

  const handleFormSave = async (newGame: Game) => {
    await fetchGames();
    // Always select the newly created/updated game
    setSelectedGameId(newGame.id);
  };

  if (isLoading && games.length === 0) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Games
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreateGame}>
          Create Game
        </Button>
      </Box>

      {(error || localError) && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setLocalError(null)}>
          {error || localError}
        </Alert>
      )}

      {games.length === 0 ? (
        <Card>
          <CardContent>
            <Typography color="text.secondary">
              No games created yet. Click "Create Game" to get started.
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Name</strong></TableCell>
                <TableCell><strong>External Event</strong></TableCell>
                <TableCell><strong>Status</strong></TableCell>
                <TableCell><strong>Start Time</strong></TableCell>
                <TableCell><strong>End Time</strong></TableCell>
                <TableCell><strong>Location Tracking</strong></TableCell>
                <TableCell align="right"><strong>Actions</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {games.map((game) => (
                <TableRow key={game.id} hover>
                  <TableCell>
                    <Typography variant="body1" fontWeight="medium">
                      {game.name}
                    </Typography>
                    {game.description && (
                      <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: 200 }}>
                        {game.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    {game.externalEventName ? (
                      <Typography variant="body2">{game.externalEventName}</Typography>
                    ) : (
                      <Typography variant="body2" color="text.secondary">-</Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={formatStatus(game.status)}
                      color={getStatusColor(game.status)}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>{formatDateTime(game.startTime)}</TableCell>
                  <TableCell>{formatDateTime(game.endTime)}</TableCell>
                  <TableCell>
                    {game.locationEndpointUrl ? (
                      <Chip
                        label={game.locationEndpointActive ? 'Active' : 'Inactive'}
                        color={game.locationEndpointActive ? 'success' : 'default'}
                        size="small"
                        variant="outlined"
                      />
                    ) : (
                      <Typography variant="body2" color="text.secondary">Not configured</Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <IconButton
                      size="small"
                      onClick={() => handleEditGame(game)}
                      title="Edit game"
                    >
                      <EditIcon />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => handleDeleteClick(game)}
                      color="error"
                      title="Delete game"
                    >
                      <DeleteIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {/* Game Form Dialog */}
      <GameFormDialog
        open={formDialogOpen}
        onClose={handleFormClose}
        game={selectedGame}
        onSave={handleFormSave}
      />

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Delete Game</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete "{gameToDelete?.name}"? This action cannot be undone
            and will remove all associated data including teams and messages.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleting}
          >
            {deleting ? <CircularProgress size={20} /> : 'Delete'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default Games;
