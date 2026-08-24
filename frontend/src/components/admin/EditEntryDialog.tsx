import { useState } from 'react';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
      setError(t('components.editEntry.invalidPoints'));
      return;
    }
    if (!reason.trim()) {
      setError(t('components.editEntry.reasonRequired'));
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
      <DialogTitle>{t('components.editEntry.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2" color="textSecondary">
            {t('components.editEntry.subtitle', { original: entry.originalAmount, current: entry.currentAmount })}
            {entry.description ? ` · ${entry.description}` : ''}
          </Typography>
          <TextField
            label={t('components.editEntry.newPoints')}
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            slotProps={{ htmlInput: { step: 1, inputMode: 'numeric' } }}
            fullWidth
            autoFocus
            error={!amountIsValid && amount.trim() !== ''}
          />
          {delta !== 0 && (
            <Typography variant="caption" color={delta < 0 ? 'error' : 'success'}>
              {t('components.editEntry.change')} {delta > 0 ? '+' : ''}{delta}
            </Typography>
          )}
          <TextField
            label={t('components.editEntry.reason')}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            multiline
            minRows={2}
            required
            fullWidth
          />
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>{t('common.cancel')}</Button>
        <Button onClick={handleSave} variant="contained" disabled={saving}>
          {saving ? t('components.editEntry.saving') : t('components.editEntry.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
