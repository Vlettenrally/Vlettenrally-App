import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  CircularProgress,
  Alert,
  Typography,
  Box,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import { useGameStore } from '../store/useGameStore';
import type { CopyAutomationsResult } from '../types/entities';

interface CopyAutomationsDialogProps {
  open: boolean;
  onClose: () => void;
  gameId: string;
  onCopied?: () => void;
}

export default function CopyAutomationsDialog({
  open,
  onClose,
  gameId,
  onCopied,
}: CopyAutomationsDialogProps) {
  const { t } = useTranslation();
  const games = useGameStore((s) => s.games);
  const fetchGames = useGameStore((s) => s.fetchGames);

  const [sourceGameId, setSourceGameId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CopyAutomationsResult | null>(null);

  const sourceOptions = useMemo(
    () => games.filter((g) => g.id !== gameId),
    [games, gameId],
  );

  // Reset the form the moment the dialog opens (adjust-state-during-render).
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setSourceGameId('');
      setError(null);
      setResult(null);
    }
  }

  useEffect(() => {
    if (open && games.length === 0) {
      fetchGames();
    }
  }, [open, games.length, fetchGames]);

  const handleCopy = async () => {
    if (!sourceGameId) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const response = await api.copyAutomations(gameId, sourceGameId);
      if (response.data) {
        setResult(response.data);
        onCopied?.();
      } else {
        setError(response.error || t('components.copyAutomations.copyFailed'));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('components.copyAutomations.copyFailed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('components.copyAutomations.title')}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {t('components.copyAutomations.description')}
          </Typography>

          {error && <Alert severity="error">{error}</Alert>}

          {result ? (
            <Alert severity="success">
              {t('components.copyAutomations.copied', { count: result.copied })}
              {result.distanceRateCopied && t('components.copyAutomations.distanceRateCopied')}.
              {result.skippedDuplicate.length > 0 && (
                <div>{t('components.copyAutomations.skippedDuplicate', { names: result.skippedDuplicate.join(', ') })}</div>
              )}
              {result.skippedUnmatchedCheckpoint.length > 0 && (
                <div>
                  {t('components.copyAutomations.skippedUnmatchedCheckpoint', { names: result.skippedUnmatchedCheckpoint.join(', ') })}
                </div>
              )}
            </Alert>
          ) : (
            <FormControl fullWidth>
              <InputLabel>{t('components.copyAutomations.sourceGame')}</InputLabel>
              <Select
                label={t('components.copyAutomations.sourceGame')}
                value={sourceGameId}
                onChange={(e) => setSourceGameId(e.target.value)}
              >
                {sourceOptions.map((game) => (
                  <MenuItem key={game.id} value={game.id}>
                    {game.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{result ? t('components.copyAutomations.close') : t('common.cancel')}</Button>
        {!result && (
          <Button onClick={handleCopy} variant="contained" disabled={loading || !sourceGameId}>
            {loading ? <CircularProgress size={20} /> : t('components.copyAutomations.copy')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
