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
import api from '../services/api';
import { useGameStore } from '../store/useGameStore';
import type { CopyCheckpointsResult } from '../types/entities';

interface CopyZonesDialogProps {
  open: boolean;
  onClose: () => void;
  gameId: string;
  onCopied?: () => void;
}

export default function CopyZonesDialog({
  open,
  onClose,
  gameId,
  onCopied,
}: CopyZonesDialogProps) {
  const games = useGameStore((s) => s.games);
  const fetchGames = useGameStore((s) => s.fetchGames);

  const [sourceGameId, setSourceGameId] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CopyCheckpointsResult | null>(null);

  const sourceOptions = useMemo(
    () => games.filter((g) => g.id !== gameId),
    [games, gameId],
  );

  useEffect(() => {
    if (open) {
      setSourceGameId('');
      setError(null);
      setResult(null);
      if (games.length === 0) {
        fetchGames();
      }
    }
  }, [open, games.length, fetchGames]);

  const handleCopy = async () => {
    if (!sourceGameId) return;
    setLoading(true);
    setError(null);
    setResult(null);
    try {
      const response = await api.copyCheckpoints(gameId, sourceGameId);
      if (response.data) {
        setResult(response.data);
        onCopied?.();
      } else {
        setError(response.error || 'Kopiëren mislukt');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Kopiëren mislukt');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Zones kopiëren</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Kopieert alle zones van een andere game naar deze game. Openings- en
            sluitingstijden worden verschoven naar de tijdlijn van deze game en de
            "verborgen voor teams"-instelling blijft behouden. Zones met een naam die
            hier al bestaat, worden overgeslagen.
          </Typography>

          {error && <Alert severity="error">{error}</Alert>}

          {result ? (
            <Alert severity="success">
              {result.copied} gekopieerd.
              {result.skippedDuplicate.length > 0 && (
                <div>Overgeslagen (bestaat al): {result.skippedDuplicate.join(', ')}</div>
              )}
            </Alert>
          ) : (
            <FormControl fullWidth>
              <InputLabel>Bron-game</InputLabel>
              <Select
                label="Bron-game"
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
        <Button onClick={onClose}>{result ? 'Sluiten' : 'Annuleren'}</Button>
        {!result && (
          <Button onClick={handleCopy} variant="contained" disabled={loading || !sourceGameId}>
            {loading ? <CircularProgress size={20} /> : 'Kopiëren'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}
