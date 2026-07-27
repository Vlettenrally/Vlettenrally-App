import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Checkbox,
  CircularProgress,
  Alert,
  Typography,
  TextField,
  FormControlLabel,
  IconButton,
  List,
  ListItem,
  Box,
} from '@mui/material';
import { ArrowUpward, ArrowDownward } from '@mui/icons-material';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { gameService } from '../services/gameService';
import type { TrackerExportField } from '../types/entities';

interface TrackerExportDialogProps {
  open: boolean;
  onClose: () => void;
  gameId: string;
}

interface ColumnChoice {
  field: TrackerExportField;
  header: string;
  enabled: boolean;
}

// The format the provider has always received; the dialog opens on this every time.
const DEFAULT_COLUMNS: ColumnChoice[] = [
  { field: 'trackerId', header: 'trackerId', enabled: true },
  { field: 'name', header: 'boatNumber', enabled: true },
  { field: 'description', header: 'scoutingGroup', enabled: true },
];

const fieldLabelKeys: Record<TrackerExportField, string> = {
  trackerId: 'admin.teams.exportDialog.fieldTrackerId',
  name: 'admin.teams.exportDialog.fieldName',
  description: 'admin.teams.exportDialog.fieldDescription',
};

export default function TrackerExportDialog({ open, onClose, gameId }: TrackerExportDialogProps) {
  const { t } = useTranslation();

  const [columns, setColumns] = useState<ColumnChoice[]>(DEFAULT_COLUMNS);
  const [includeUnassigned, setIncludeUnassigned] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setColumns(DEFAULT_COLUMNS);
      setIncludeUnassigned(false);
      setError(null);
    }
  }, [open]);

  const updateColumn = (index: number, patch: Partial<ColumnChoice>) => {
    setColumns((prev) => prev.map((c, i) => (i === index ? { ...c, ...patch } : c)));
  };

  const moveColumn = (index: number, delta: number) => {
    setColumns((prev) => {
      const next = [...prev];
      const [moved] = next.splice(index, 1);
      next.splice(index + delta, 0, moved);
      return next;
    });
  };

  const selected = columns.filter((c) => c.enabled);
  const canExport = selected.length > 0 && selected.every((c) => c.header.trim() !== '');

  const handleExport = async () => {
    setLoading(true);
    setError(null);
    try {
      const csv = await gameService.exportTrackers(gameId, {
        columns: selected.map(({ field, header }) => ({ field, header: header.trim() })),
        includeUnassigned,
      });
      const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `trackers-${gameId}.csv`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : t('admin.teams.exportFailed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('admin.teams.exportDialog.title')}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            {t('admin.teams.exportDialog.description')}
          </Typography>

          {error && <Alert severity="error">{error}</Alert>}

          <List disablePadding>
            {columns.map((column, index) => (
              <ListItem key={column.field} disableGutters sx={{ gap: 1 }}>
                <Checkbox
                  edge="start"
                  checked={column.enabled}
                  onChange={(e) => updateColumn(index, { enabled: e.target.checked })}
                  slotProps={{ input: { 'aria-label': t(fieldLabelKeys[column.field]) } }}
                />
                <Typography variant="body2" sx={{ flex: 1 }}>
                  {t(fieldLabelKeys[column.field])}
                </Typography>
                <TextField
                  size="small"
                  label={t('admin.teams.exportDialog.columnHeaderLabel')}
                  value={column.header}
                  disabled={!column.enabled}
                  error={column.enabled && column.header.trim() === ''}
                  onChange={(e) => updateColumn(index, { header: e.target.value })}
                  sx={{ width: 180 }}
                />
                <IconButton
                  size="small"
                  aria-label={t('admin.teams.exportDialog.moveUp')}
                  disabled={index === 0}
                  onClick={() => moveColumn(index, -1)}
                >
                  <ArrowUpward fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  aria-label={t('admin.teams.exportDialog.moveDown')}
                  disabled={index === columns.length - 1}
                  onClick={() => moveColumn(index, 1)}
                >
                  <ArrowDownward fontSize="small" />
                </IconButton>
              </ListItem>
            ))}
          </List>

          <FormControlLabel
            control={
              <Checkbox
                checked={includeUnassigned}
                onChange={(e) => setIncludeUnassigned(e.target.checked)}
              />
            }
            label={t('admin.teams.exportDialog.includeUnassigned')}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button onClick={handleExport} variant="contained" disabled={loading || !canExport}>
          {loading ? <CircularProgress size={20} /> : t('admin.teams.exportDialog.export')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
