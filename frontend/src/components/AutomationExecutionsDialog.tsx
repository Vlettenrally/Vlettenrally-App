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
import { useCallback, useEffect, useState } from 'react';
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
  return new Date(iso).toLocaleString('nl-NL', {
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
  const [executions, setExecutions] = useState<AutomationExecution[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(25);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchExecutions = useCallback(async () => {
    if (!rule) return;
    setLoading(true);
    setError(null);
    try {
      const response = await api.getAutomationExecutions(gameId, rule.id, page, pageSize);
      if (response.data) {
        setExecutions(response.data.executions);
        setTotalCount(response.data.totalCount);
      } else {
        setError(response.error || 'Historie laden mislukt');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Historie laden mislukt');
    } finally {
      setLoading(false);
    }
  }, [gameId, rule, page, pageSize]);

  useEffect(() => {
    if (open) {
      fetchExecutions();
    }
  }, [open, fetchExecutions]);

  useEffect(() => {
    if (!open) {
      setPage(0);
    }
  }, [open]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Historie — {rule?.name}</DialogTitle>
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
            Deze automatisering is nog niet uitgevoerd.
          </Typography>
        ) : (
          <>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell><strong>Tijdstip</strong></TableCell>
                  <TableCell><strong>Team</strong></TableCell>
                  <TableCell><strong>Status</strong></TableCell>
                  <TableCell><strong>Details</strong></TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {executions.map((execution) => (
                  <TableRow key={execution.id} hover>
                    <TableCell>{formatTimestamp(execution.firedAt)}</TableCell>
                    <TableCell>{execution.teamName ?? 'Hele game'}</TableCell>
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
              labelRowsPerPage="Per pagina"
            />
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Sluiten</Button>
      </DialogActions>
    </Dialog>
  );
}
