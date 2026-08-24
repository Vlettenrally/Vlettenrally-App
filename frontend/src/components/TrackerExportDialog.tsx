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
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { gameService } from '../services/gameService';
import { downloadFile } from '../utils/download';
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

// Ten hours ago, formatted for a datetime-local input using local (not UTC) getters,
// so the value shown matches the browser's clock instead of shifting by the UTC offset.
function defaultSinceLocal(): string {
  const d = new Date(Date.now() - 10 * 60 * 60 * 1000);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export default function TrackerExportDialog({ open, onClose, gameId }: TrackerExportDialogProps) {
  const { t } = useTranslation();

  const [columns, setColumns] = useState<ColumnChoice[]>(DEFAULT_COLUMNS);
  const [includeUnassigned, setIncludeUnassigned] = useState(false);
  const [onlyRecent, setOnlyRecent] = useState(false);
  const [sinceLocal, setSinceLocal] = useState(defaultSinceLocal);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Reset the form the moment the dialog opens (adjust-state-during-render).
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setColumns(DEFAULT_COLUMNS);
      setIncludeUnassigned(false);
      setOnlyRecent(false);
      setSinceLocal(defaultSinceLocal());
      setError(null);
    }
  }

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
      const columns = selected.map(({ field, header }) => ({ field, header: header.trim() }));
      const file = onlyRecent
        ? await gameService.exportTeams(gameId, {
            columns,
            includeUnassigned,
            since: new Date(sinceLocal).toISOString(),
          })
        : await gameService.exportTrackers(gameId, { columns, includeUnassigned });
      downloadFile(file, onlyRecent ? `teams-${gameId}.csv` : `trackers-${gameId}.csv`);
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

          <FormControlLabel
            control={
              <Checkbox
                checked={onlyRecent}
                onChange={(e) => setOnlyRecent(e.target.checked)}
              />
            }
            label={t('admin.teams.exportDialog.onlyRecentlyChanged')}
          />
          {onlyRecent && (
            <TextField
              size="small"
              type="datetime-local"
              label={t('admin.teams.exportDialog.onlyRecentlyChanged')}
              value={sinceLocal}
              onChange={(e) => setSinceLocal(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              sx={{ width: 220 }}
            />
          )}
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
