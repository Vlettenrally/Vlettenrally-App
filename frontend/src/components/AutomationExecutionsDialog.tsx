import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TablePagination,
  Chip,
  Typography,
  CircularProgress,
  Box,
  Alert,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import type { AutomationExecution, AutomationRule } from '../types/entities';

interface AutomationExecutionsDialogProps {
  open: boolean;
  onClose: () => void;
  rule: AutomationRule | null;
  gameId: string;
}

const STATUS_COLORS: Record<AutomationExecution['status'], 'success' | 'warning' | 'error'> = {
  SUCCESS: 'success',
  PARTIAL: 'warning',
  FAILED: 'error',
};

function formatTimestamp(iso: string): string {
  return new Date(iso).toLocaleString(undefined, {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

export default function AutomationExecutionsDialog({
  open,
  onClose,
  rule,
  gameId,
}: AutomationExecutionsDialogProps) {
  const { t } = useTranslation();
  const [executions, setExecutions] = useState<AutomationExecution[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !rule) return;
    let cancelled = false;
    async function loadExecutions() {
      setLoading(true);
      setError(null);
      try {
        const response = await api.getAutomationExecutions(gameId, rule!.id, page, pageSize);
        if (cancelled) return;
        if (response.data) {
          setExecutions(response.data.executions);
          setTotalCount(response.data.totalCount);
        } else {
          setError(response.error || t('components.automationExecutions.loadFailed'));
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : t('components.automationExecutions.loadFailed'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    void loadExecutions();
    return () => {
      cancelled = true;
    };
  }, [open, gameId, rule, page, pageSize, t]);

  // Jump back to the first page when the dialog closes (adjust-state-during-render).
  const [wasOpen, setWasOpen] = useState(open);
  if (open !== wasOpen) {
    setWasOpen(open);
    if (!open) setPage(0);
  }

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>{t('components.automationExecutions.title', { name: rule?.name })}</DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        {loading && executions.length === 0 ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress />
          </Box>
        ) : executions.length === 0 ? (
          <Typography color="text.secondary" sx={{ p: 2 }}>
            {t('components.automationExecutions.noExecutions')}
          </Typography>
        ) : (
          <>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell><strong>{t('components.automationExecutions.columnTime')}</strong></TableCell>
                  <TableCell><strong>{t('components.automationExecutions.columnTeam')}</strong></TableCell>
                  <TableCell><strong>{t('components.automationExecutions.columnStatus')}</strong></TableCell>
                  <TableCell><strong>{t('components.automationExecutions.columnDetails')}</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {executions.map((execution) => (
                  <TableRow key={execution.id} hover>
                    <TableCell>{formatTimestamp(execution.firedAt)}</TableCell>
                    <TableCell>{execution.teamName ?? t('components.automationExecutions.wholeGame')}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={execution.status}
                        color={STATUS_COLORS[execution.status]}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography
                        variant="caption"
                        sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}
                      >
                        {execution.detail ?? '–'}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <TablePagination
              component="div"
              count={totalCount}
              page={page}
              onPageChange={(_, newPage) => setPage(newPage)}
              rowsPerPage={pageSize}
              onRowsPerPageChange={(e) => {
                setPageSize(parseInt(e.target.value, 10));
                setPage(0);
              }}
              labelRowsPerPage={t('components.automationExecutions.perPage')}
            />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('components.automationExecutions.close')}</Button>
      </DialogActions>
    </Dialog>
  );
}
