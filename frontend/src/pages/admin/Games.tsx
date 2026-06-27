import { useEffect, useMemo, useState } from 'react';
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
  Divider,
  TextField,
  Stack,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
    QrCode2 as QrCodeIcon,
  ContentCopy as ContentCopyIcon,
  FileDownload as FileDownloadIcon,
  Leaderboard as LeaderboardIcon,
} from '@mui/icons-material';
import QRCode from 'qrcode';
import api from '../../services/api';
import { gameService } from '../../services/gameService';
import { useGameStore } from '../../store/useGameStore';
import { websocket, WebSocketEventType } from '../../services/websocket';
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
    return date.toLocaleString(undefined, {
      dateStyle: 'short',
      timeStyle: 'short',
    });
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

  const [checkInDialogOpen, setCheckInDialogOpen] = useState(false);
  const [checkInGame, setCheckInGame] = useState<Game | null>(null);
  const [checkInLoading, setCheckInLoading] = useState(false);
  const [checkInError, setCheckInError] = useState<string | null>(null);
  const [checkInLink, setCheckInLink] = useState<{ url: string; token: string; expiresAt: number; expiryReason: string } | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [copySuccess, setCopySuccess] = useState<string | null>(null);

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

  const handleGenerateCheckInLink = async (game: Game) => {
    setCheckInDialogOpen(true);
    setCheckInGame(game);
    setCheckInLink(null);
    setQrDataUrl(null);
    setCheckInError(null);
    setCopySuccess(null);
    setCheckInLoading(true);

    try {
      const response = await gameService.generateCheckInLink(game.id);
      setCheckInLink(response);

      const label = `${game.name} · Vlettenrally check-in`;
      const canvas = document.createElement('canvas');
      const qrSize = 512;
      const labelHeight = 48;
      canvas.width = qrSize;
      canvas.height = qrSize + labelHeight;

      await QRCode.toCanvas(canvas, response.url, {
        width: qrSize,
        margin: 2,
        errorCorrectionLevel: 'Q',
      });

      const ctx = canvas.getContext('2d');
      if (ctx) {
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, qrSize, qrSize, labelHeight);
        ctx.fillStyle = '#111111';
        ctx.textAlign = 'center';
        ctx.font = 'bold 18px sans-serif';
        ctx.fillText(label, qrSize / 2, qrSize + 30);
      }

      setQrDataUrl(canvas.toDataURL('image/png'));
    } catch (err) {
      setCheckInError(err instanceof Error ? err.message : t('admin.games.generateLinkFailed'));
    } finally {
      setCheckInLoading(false);
    }
  };

  const handleCopyLink = async () => {
    if (!checkInLink?.url) return;
    try {
      await navigator.clipboard.writeText(checkInLink.url);
      setCopySuccess(t('admin.games.linkCopied'));
    } catch {
      setCheckInError(t('admin.games.copyLinkFailed'));
    }
  };

  const handleDownloadQr = () => {
    if (!qrDataUrl || !checkInGame) return;
    const slug = checkInGame.name
      .toLowerCase()
      .replace(/\s+/g, '-')
      .replace(/[^a-z0-9-]/g, '');
    const filename = `vlettenrally-${slug || 'game'}-checkin.png`;

    const link = document.createElement('a');
    link.href = qrDataUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const formattedExpiry = useMemo(() => {
    if (!checkInLink?.expiresAt) return '-';
    return new Date(checkInLink.expiresAt * 1000).toLocaleString(undefined, {
      dateStyle: 'short',
      timeStyle: 'short',
    });
  }, [checkInLink?.expiresAt]);

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
            <Typography color="text.secondary">
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
                      <Typography variant="body2" color="text.secondary">{t('admin.games.locationNotConfigured')}</Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
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
                      onClick={() => handleGenerateCheckInLink(game)}
                      title={t('admin.games.generateCheckinLink')}
                    >
                      <QrCodeIcon />
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

      {/* Check-in Link Dialog */}
      <Dialog open={checkInDialogOpen} onClose={() => setCheckInDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{t('admin.games.checkinLink')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {checkInLoading && (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
                <CircularProgress />
              </Box>
            )}

            {checkInError && <Alert severity="error">{checkInError}</Alert>}
            {copySuccess && <Alert severity="success">{copySuccess}</Alert>}

            {checkInLink && (
              <>
                <Typography variant="body2" color="text.secondary">
                  {checkInLink.expiryReason}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('admin.games.expiresOn', { expiry: formattedExpiry })}
                </Typography>

                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <TextField
                    label={t('admin.games.checkinLink')}
                    value={checkInLink.url}
                    fullWidth
                    slotProps={{input:{ readOnly: true }}}
                  />
                  <Button
                    variant="outlined"
                    startIcon={<ContentCopyIcon />}
                    onClick={handleCopyLink}
                  >
                    {t('admin.games.copyLink')}
                  </Button>
                </Box>

                <Divider />

                {qrDataUrl && (
                  <Box sx={{ textAlign: 'center' }}>
                    <Box
                      component="img"
                      src={qrDataUrl}
                      alt="Check-in QR"
                      sx={{ width: '100%', maxWidth: 320, borderRadius: 2 }}
                    />
                    <Box sx={{ mt: 2 }}>
                      <Button
                        variant="contained"
                        startIcon={<FileDownloadIcon />}
                        onClick={handleDownloadQr}
                      >
                        {t('admin.games.downloadQR')}
                      </Button>
                    </Box>
                  </Box>
                )}
              </>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCheckInDialogOpen(false)}>{t('common.close')}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default Games;
