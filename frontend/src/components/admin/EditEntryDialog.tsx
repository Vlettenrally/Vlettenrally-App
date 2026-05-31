import { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Alert,
  Stack,
  Typography,
} from '@mui/material';
import api from '../../services/api';
import type { PointEntry } from '../../types/entities';

interface EditEntryDialogProps {
  open: boolean;
  onClose: () => void;
  entry: PointEntry | null;
  onSaved: (entry: PointEntry) => void;
}

/**
 * Lets an admin change the {@code currentAmount} on an existing ledger entry.
 * A reason is mandatory so the audit trail stays meaningful.
 */
export default function EditEntryDialog({ open, onClose, entry, onSaved }: EditEntryDialogProps) {
  const [trackedId, setTrackedId] = useState<string | null>(null);
  // String-backed so the field can be cleared while typing a new value
  const [amount, setAmount] = useState<string>('0');
  const [reason, setReason] = useState<string>('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Re-initialize when the entry being edited changes (derived-state pattern)
  if (entry && entry.id !== trackedId) {
    setTrackedId(entry.id);
    setAmount(String(entry.currentAmount));
    setReason('');
    setError(null);
  }

  if (!entry) return null;

  const parsedAmount = Number(amount);
  const amountIsValid = amount.trim() !== '' && Number.isFinite(parsedAmount);

  const handleSave = async () => {
    if (!amountIsValid) {
      setError('Vul een geldig aantal punten in');
      return;
    }
    if (!reason.trim()) {
      setError('Reden is verplicht');
      return;
    }
    setSaving(true);
    setError(null);
    const response = await api.editPointEntry(entry.id, { amount: parsedAmount, reason: reason.trim() });
    setSaving(false);
    if (response.error || !response.data) {
      setError(response.error || 'Onbekende fout');
      return;
    }
    onSaved(response.data);
    onClose();
  };

  const delta = amountIsValid ? parsedAmount - entry.currentAmount : 0;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Punten aanpassen</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Origineel: <strong>{entry.originalAmount}</strong> · Huidig: <strong>{entry.currentAmount}</strong>
            {entry.description ? ` · ${entry.description}` : ''}
          </Typography>
          <TextField
            label="Nieuw aantal punten"
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            inputProps={{ step: 1, inputMode: 'numeric' }}
            fullWidth
            autoFocus
            error={!amountIsValid && amount.trim() !== ''}
          />
          {delta !== 0 && (
            <Typography variant="caption" color={delta < 0 ? 'error' : 'success.main'}>
              Wijziging: {delta > 0 ? '+' : ''}{delta}
            </Typography>
          )}
          <TextField
            label="Reden"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="bv. Onreglementair gevaren tussen checkpoints"
            multiline
            minRows={2}
            required
            fullWidth
          />
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Annuleren</Button>
        <Button onClick={handleSave} variant="contained" disabled={saving}>
          {saving ? 'Opslaan…' : 'Opslaan'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
