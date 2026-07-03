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
  Autocomplete,
  Box,
  Typography,
} from '@mui/material';
import api from '../../services/api';
import { teamMatchesQuery } from '../../utils/teamSearch';
import type { AdminTeam, PointEntry } from '../../types/entities';

interface CreateAdjustmentDialogProps {
  open: boolean;
  onClose: () => void;
  gameId: string;
  teams: AdminTeam[];
  onSaved: (entry: PointEntry) => void;
}

/**
 * Records a manual ledger entry. The amount is signed: a positive value awards
 * points, a negative value deducts them. A reason/description is required so
 * the entry is meaningful in the audit log.
 */
export default function CreateAdjustmentDialog({ open, onClose, gameId, teams, onSaved }: CreateAdjustmentDialogProps) {
  const { t } = useTranslation();
  const [wasOpen, setWasOpen] = useState(false);
  const [team, setTeam] = useState<AdminTeam | null>(null);
  // String-backed so the field can be cleared while typing
  const [amount, setAmount] = useState<string>('');
  const [description, setDescription] = useState<string>('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reset form when the dialog transitions from closed -> open (derived-state pattern)
  if (open && !wasOpen) {
    setWasOpen(true);
    setTeam(null);
    setAmount('');
    setDescription('');
    setError(null);
  }
  if (!open && wasOpen) {
    setWasOpen(false);
  }

  const parsedAmount = Number(amount);
  const amountIsValid = amount.trim() !== '' && Number.isFinite(parsedAmount);

  const handleSave = async () => {
    if (!team) {
      setError(t('components.createAdjustment.chooseTeam'));
      return;
    }
    if (!amountIsValid) {
      setError(t('components.createAdjustment.invalidPoints'));
      return;
    }
    if (parsedAmount === 0) {
      setError(t('components.createAdjustment.zeroPoints'));
      return;
    }
    if (!description.trim()) {
      setError(t('components.createAdjustment.reasonRequired'));
      return;
    }
    setSaving(true);
    setError(null);
    const response = await api.createPointAdjustment({
      teamId: team.id,
      gameId,
      amount: parsedAmount,
      description: description.trim(),
    });
    setSaving(false);
    if (response.error || !response.data) {
      setError(response.error || 'Onbekende fout');
      return;
    }
    onSaved(response.data);
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('components.createAdjustment.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Autocomplete
            value={team}
            onChange={(_, newValue) => setTeam(newValue)}
            options={teams}
            getOptionLabel={(opt) => opt.name}
            filterOptions={(options, { inputValue }) =>
              options.filter((option) => teamMatchesQuery(option, inputValue))
            }
            renderOption={({ key, ...props }, option) => (
              <Box key={key} component="li" {...props}>
                <Box>
                  <Typography variant="body2">{option.name}</Typography>
                  {option.description && (
                    <Typography variant="caption" color="text.secondary" display="block">
                      {option.description}
                    </Typography>
                  )}
                </Box>
              </Box>
            )}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderInput={(params) => <TextField {...params} label={t('common.team')} required />}
            fullWidth
          />
          <TextField
            label={t('components.createAdjustment.points')}
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            slotProps={{ htmlInput: { step: 1, inputMode: 'numeric' } }}
            fullWidth
            required
            error={!amountIsValid && amount.trim() !== ''}
          />
          <TextField
            label={t('components.createAdjustment.reason')}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
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
          {saving ? t('components.createAdjustment.saving') : t('components.createAdjustment.add')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
