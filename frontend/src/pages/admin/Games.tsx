import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
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
  TextField,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Leaderboard as LeaderboardIcon,
  Cast as CastIcon,
  ContentCopy as ContentCopyIcon,
  PauseCircle as PauseCircleIcon,
  PlayCircle as PlayCircleIcon,
} from '@mui/icons-material';
import api from '../../services/api';
import { gameService } from '../../services/gameService';
import { useGameStore } from '../../store/useGameStore';
import { websocket, WebSocketEventType } from '../../services/websocket';
import type { Game } from '../../types/entities';
import GameFormDialog from '../../components/GameFormDialog';
import { formatDateTime as formatDateTimeLocal } from '../../utils/dateTime';

const getStatusColor = (status?: string): 'default' | 'warning' | 'success' | 'error' => {
  switch (status?.toLowerCase()) {
    case 'planned':
      return 'warning';
    case 'active':
      return 'success';
    case 'paused':
      return 'error';
    case 'completed':
      return 'default';
    default:
      return 'default';
  }
};

const formatDateTime = (date?: string | Date): string => {
  if (!date) return '-';
  try {
    return formatDateTimeLocal(date);
  } catch {
    return '-';
  }
};

const Games = () => {
  const { t } = useTranslation();
  const [localError, setLocalError] = useState<string | null>(null);

  // Dialog states
  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [selectedGame, setSelectedGame] = useState<Game | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [gameToDelete, setGameToDelete] = useState<Game | null>(null);
  const [deleting, setDeleting] = useState(false);

  const [publishDialogOpen, setPublishDialogOpen] = useState(false);
  const [gameToPublish, setGameToPublish] = useState<Game | null>(null);
  const [publishing, setPublishing] = useState(false);

  // Emergency stop / resume
  const [pauseDialogOpen, setPauseDialogOpen] = useState(false);
  const [gameToPause, setGameToPause] = useState<Game | null>(null);
  const [pauseReason, setPauseReason] = useState('');
  const [pausing, setPausing] = useState(false);

  // Public narrowcasting map link
  const [mapLinkDialogOpen, setMapLinkDialogOpen] = useState(false);
  const [gameToShare, setGameToShare] = useState<Game | null>(null);
  const [mapLink, setMapLink] = useState<string | null>(null);
  const [mapLinkError, setMapLinkError] = useState<string | null>(null);
  const [mapLinkLoading, setMapLinkLoading] = useState(false);
  const [mapLinkCopied, setMapLinkCopied] = useState(false);

  // Get games, loading state, and actions from store
  const { games, isLoading, error, fetchGames, setSelectedGameId } = useGameStore();

  useEffect(() => {
    fetchGames();
  }, [fetchGames]);

  // Listen for WebSocket game status updates
  useEffect(() => {
    const unsubscribe = websocket.on(WebSocketEventType.GAME_STATUS, () => {
      // Refresh games when status changes
      fetchGames();
    });
    return () => unsubscribe();
  }, [fetchGames]);

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
      setLocalError(err instanceof Error ? err.message : t('admin.games.deleteGameFailed'));
    } finally {
      setDeleting(false);
    }
  };

  const handlePublishClick = (game: Game) => {
    setGameToPublish(game);
    setPublishDialogOpen(true);
  };

  const handlePublishConfirm = async () => {
    if (!gameToPublish) return;

    setPublishing(true);
    try {
      const response = await api.publishRankings(gameToPublish.id);
      if (response.error) throw new Error(response.error);
      setPublishDialogOpen(false);
      setGameToPublish(null);
      await fetchGames();
    } catch (err) {
      setLocalError(err instanceof Error ? err.message : t('admin.games.releaseRankingsFailed'));
    } finally {
      setPublishing(false);
    }
  };

  const handlePauseClick = (game: Game) => {
    setGameToPause(game);
    setPauseReason('');
    setPauseDialogOpen(true);
  };

  /**
   * Stop or restart the rally. Both go through their own endpoint rather than a status edit,
   * because pausing also opens the period that keeps the frozen stretch out of the scores.
   */
  const handlePauseConfirm = async () => {
    if (!gameToPause) return;

    const resuming = gameToPause.status === 'paused';
    setPausing(true);
    try {
      if (resuming) {
        await gameService.resumeGame(gameToPause.id);
      } else {
        await gameService.pauseGame(gameToPause.id, pauseReason.trim() || undefined);
      }
      setPauseDialogOpen(false);
      setGameToPause(null);
      await fetchGames();
    } catch (err) {
      setLocalError(err instanceof Error ? err.message
        : t(resuming ? 'admin.games.resumeGameFailed' : 'admin.games.pauseGameFailed'));
    } finally {
      setPausing(false);
    }
  };

  /**
   * Fetch the game's public map token and compose the display URL from this app's own origin — the
   * API deliberately returns only the token, so it never has to know where the frontend is hosted.
   */
  const handleMapLinkClick = async (game: Game) => {
    setGameToShare(game);
    setMapLink(null);
    setMapLinkError(null);
    setMapLinkCopied(false);
    setMapLinkDialogOpen(true);
    setMapLinkLoading(true);
    try {
      const response = await api.getPublicMapToken(game.id);
      if (response.status === 503) {
        setMapLinkError(t('admin.games.publicMapLinkDisabled'));
      } else if (response.error || !response.data?.token) {
        setMapLinkError(response.error || t('admin.games.publicMapLinkFailed'));
      } else {
        const params = new URLSearchParams({ g: game.id, t: response.data.token });
        setMapLink(`${window.location.origin}/public/map?${params.toString()}`);
      }
    } catch (err) {
      setMapLinkError(err instanceof Error ? err.message : t('admin.games.publicMapLinkFailed'));
    } finally {
      setMapLinkLoading(false);
    }
  };

  const handleCopyMapLink = async () => {
    if (!mapLink) return;
    try {
      await navigator.clipboard.writeText(mapLink);
      setMapLinkCopied(true);
    } catch {
      // Clipboard access can be blocked (non-secure origin, permissions). The link is on screen
      // and selectable, so this is a non-event — just don't claim it was copied.
      setMapLinkCopied(false);
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
          {t('admin.games.title')}
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={handleCreateGame}>
          {t('admin.games.createGame')}
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
            <Typography color="textSecondary">
              {t('admin.games.noGames')}
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>{t('admin.games.columns.name')}</strong></TableCell>
                <TableCell><strong>{t('admin.games.columns.externalEvent')}</strong></TableCell>
                <TableCell><strong>{t('admin.games.columns.status')}</strong></TableCell>
                <TableCell><strong>{t('admin.games.columns.startTime')}</strong></TableCell>
                <TableCell><strong>{t('admin.games.columns.endTime')}</strong></TableCell>
                <TableCell><strong>{t('admin.games.columns.locationTracking')}</strong></TableCell>
                <TableCell align="right"><strong>{t('admin.games.columns.actions')}</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {games.map((game) => (
                <TableRow key={game.id} hover>
                  <TableCell>
                    <Typography variant="body1" sx={{
                      fontWeight: "medium"
                    }}>
                      {game.name}
                    </Typography>
                    {game.description && (
                      <Typography variant="body2" color="textSecondary" noWrap sx={{ maxWidth: 200 }}>
                        {game.description}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    {game.externalEventName ? (
                      <Typography variant="body2">{game.externalEventName}</Typography>
                    ) : (
                      <Typography variant="body2" color="textSecondary">-</Typography>
                    )}
                  </TableCell>
                  <TableCell>
                    <Chip
                      label={game.status ? t(`admin.games.gameStatus.${game.status}`, { defaultValue: game.status }) : t('common.unknown')}
                      color={getStatusColor(game.status)}
                      size="small"
                    />
                  </TableCell>
                  <TableCell>{formatDateTime(game.startTime)}</TableCell>
                  <TableCell>{formatDateTime(game.endTime)}</TableCell>
                  <TableCell>
                    {game.locationEndpointUrl ? (
                      <Chip
                        label={game.locationEndpointActive ? t('admin.games.statusActive') : t('admin.games.statusInactive')}
                        color={game.locationEndpointActive ? 'success' : 'default'}
                        size="small"
                        variant="outlined"
                      />
                    ) : (
                      <Typography variant="body2" color="textSecondary">{t('admin.games.locationNotConfigured')}</Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {(game.status === 'active' || game.status === 'paused') && (
                      <IconButton
                        size="small"
                        onClick={() => handlePauseClick(game)}
                        color={game.status === 'paused' ? 'error' : 'default'}
                        title={game.status === 'paused' ? t('admin.games.resumeGame') : t('admin.games.pauseGame')}
                      >
                        {game.status === 'paused' ? <PlayCircleIcon /> : <PauseCircleIcon />}
                      </IconButton>
                    )}
                    {game.status === 'completed' && (
                      <IconButton
                        size="small"
                        onClick={() => handlePublishClick(game)}
                        disabled={game.rankingsPublished}
                        color={game.rankingsPublished ? 'success' : 'default'}
                        title={game.rankingsPublished ? t('admin.games.releaseRankingsPublished') : t('admin.games.releaseRankings')}
                      >
                        <LeaderboardIcon />
                      </IconButton>
                    )}
                    <IconButton
                      size="small"
                      onClick={() => handleMapLinkClick(game)}
                      title={t('admin.games.publicMapLink')}
                    >
                      <CastIcon />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => handleEditGame(game)}
                      title={t('admin.games.editGame')}
                    >
                      <EditIcon />
                    </IconButton>
                    <IconButton
                      size="small"
                      onClick={() => handleDeleteClick(game)}
                      color="error"
                      title={t('admin.games.deleteGame')}
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

      {/* Publish Rankings Confirmation Dialog */}
      <Dialog open={publishDialogOpen} onClose={() => setPublishDialogOpen(false)}>
        <DialogTitle>{t('admin.games.releaseRankings')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('admin.games.releaseRankingsConfirm', { name: gameToPublish?.name })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPublishDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button
            onClick={handlePublishConfirm}
            color="primary"
            variant="contained"
            disabled={publishing}
            startIcon={publishing ? <CircularProgress size={16} /> : <LeaderboardIcon />}
          >
            {t('admin.games.release')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Emergency Stop / Resume Dialog */}
      <Dialog open={pauseDialogOpen} onClose={() => setPauseDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>
          {gameToPause?.status === 'paused' ? t('admin.games.resumeGame') : t('admin.games.pauseGame')}
        </DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>
            {gameToPause?.status === 'paused'
              ? t('admin.games.resumeGameConfirm', { name: gameToPause?.name })
              : t('admin.games.pauseGameConfirm', { name: gameToPause?.name })}
          </DialogContentText>
          {gameToPause?.status === 'paused' ? (
            <Alert severity="info">{t('admin.games.resumeGameEffects')}</Alert>
          ) : (
            <>
              <Alert severity="warning" sx={{ mb: 2 }}>{t('admin.games.pauseGameEffects')}</Alert>
              <TextField
                fullWidth
                autoFocus
                multiline
                minRows={2}
                label={t('admin.games.pauseReason')}
                helperText={t('admin.games.pauseReasonHelp')}
                value={pauseReason}
                onChange={(e) => setPauseReason(e.target.value)}
              />
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPauseDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button
            onClick={handlePauseConfirm}
            color={gameToPause?.status === 'paused' ? 'primary' : 'error'}
            variant="contained"
            disabled={pausing}
            startIcon={pausing
              ? <CircularProgress size={16} />
              : gameToPause?.status === 'paused' ? <PlayCircleIcon /> : <PauseCircleIcon />}
          >
            {gameToPause?.status === 'paused' ? t('admin.games.resume') : t('admin.games.pause')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Public Narrowcasting Map Link Dialog */}
      <Dialog open={mapLinkDialogOpen} onClose={() => setMapLinkDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t('admin.games.publicMapLink')}</DialogTitle>
        <DialogContent>
          {mapLinkLoading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
              <CircularProgress />
            </Box>
          ) : mapLinkError ? (
            <Alert severity="warning">{mapLinkError}</Alert>
          ) : (
            <>
              <DialogContentText sx={{ mb: 2 }}>
                {t('admin.games.publicMapLinkDescription', { name: gameToShare?.name })}
              </DialogContentText>
              <TextField
                fullWidth
                value={mapLink ?? ''}
                slotProps={{ input: { readOnly: true } }}
                onFocus={(e) => e.target.select()}
                size="small"
              />
              <Alert severity="info" sx={{ mt: 2 }}>
                {t('admin.games.publicMapLinkWarning')}
              </Alert>
            </>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setMapLinkDialogOpen(false)}>{t('common.close')}</Button>
          {mapLink && (
            <Button
              onClick={handleCopyMapLink}
              variant="contained"
              startIcon={<ContentCopyIcon />}
            >
              {mapLinkCopied ? t('admin.games.publicMapLinkCopied') : t('admin.games.publicMapLinkCopy')}
            </Button>
          )}
        </DialogActions>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>{t('admin.games.deleteGameTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('admin.games.deleteGameConfirm', { name: gameToDelete?.name })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button
            onClick={handleDeleteConfirm}
            color="error"
            variant="contained"
            disabled={deleting}
          >
            {deleting ? <CircularProgress size={20} /> : t('admin.games.deleteGameAction')}
          </Button>
        </DialogActions>
      </Dialog>

    </Box>
  );
};

export default Games;
