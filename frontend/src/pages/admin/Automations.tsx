import { useCallback, useEffect, useState } from 'react';
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
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  CircularProgress,
  Alert,
  Chip,
  Switch,
  Tooltip,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  History as HistoryIcon,
  ContentCopy as CopyIcon,
} from '@mui/icons-material';
import api from '../../services/api';
import websocket, { WebSocketEventType } from '../../services/websocket';
import { useGameStore } from '../../store/useGameStore';
import { RelativeTime } from '../../components/RelativeTime';
import AutomationFormDialog from '../../components/AutomationFormDialog';
import AutomationExecutionsDialog from '../../components/AutomationExecutionsDialog';
import CopyAutomationsDialog from '../../components/CopyAutomationsDialog';
import type { AutomationAction, AutomationRule } from '../../types/entities';

const TRIGGER_LABELS: Record<AutomationRule['triggerType'], string> = {
  CHECKPOINT_ENTRY: 'Checkpoint bereikt',
  SCHEDULED_TIME: 'Tijdstip',
  GAME_STATUS_CHANGE: 'Game-status',
  TEAM_INACTIVITY: 'Team inactief',
  RIVAL_OVERTAKE: 'Rivaal ingehaald',
  DISTANCE_TRAVELED: 'Afgelegde afstand',
};

/** "om 17:00" / "bij einde" / "30 min voor start" — the configured boundary or fire-at time. */
function describeTimeSpec(config: AutomationRule['triggerConfig']): string {
  if (config.mode === 'RELATIVE') {
    const anchor = config.relativeTo === 'GAME_END' ? 'einde' : 'start';
    const offset = config.offsetMinutes ?? 0;
    if (offset === 0) return `bij ${anchor}`;
    return `${Math.abs(offset)} min ${offset < 0 ? 'voor' : 'na'} ${anchor}`;
  }
  return config.absoluteTime
    ? new Date(config.absoluteTime).toLocaleString('nl-NL', { dateStyle: 'short', timeStyle: 'short' })
    : '?';
}

/** Human summary of the trigger, e.g. "1 punt per 100 m" or "30 min inactief". */
function describeTrigger(rule: AutomationRule): string {
  const config = rule.triggerConfig ?? {};
  switch (rule.triggerType) {
    case 'DISTANCE_TRAVELED':
      return `${config.pointsPer100m ?? '?'} punt(en) per 100 m`;
    case 'TEAM_INACTIVITY':
      return `${config.inactiveMinutes ?? '?'} min geen locatie`;
    case 'GAME_STATUS_CHANGE':
      return config.toStatus === 'COMPLETED' ? 'Game afgerond' : 'Game gestart';
    case 'SCHEDULED_TIME':
      return describeTimeSpec(config);
    case 'CHECKPOINT_ENTRY': {
      const base = config.checkpointId ? 'Eén checkpoint' : 'Elk checkpoint';
      if (!config.comparison) return base;
      return `${base} · ${config.comparison === 'BEFORE' ? 'vóór' : 'na'} ${describeTimeSpec(config)}`;
    }
    case 'RIVAL_OVERTAKE':
      return 'Rivaal voorbij in punten';
  }
}

function actionLabel(action: AutomationAction): string {
  switch (action.type) {
    case 'SEND_MESSAGE':
      return 'Bericht';
    case 'AWARD_POINTS':
      return `${(action.points ?? 0) > 0 ? '+' : ''}${action.points ?? 0} punten`;
    case 'NOTIFY_ADMINS':
      return 'Admin-melding';
    case 'FINISH_TEAM':
      return 'Finish';
  }
}

export default function Automations() {
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  const [rules, setRules] = useState<AutomationRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [selectedRule, setSelectedRule] = useState<AutomationRule | null>(null);

  const [historyRule, setHistoryRule] = useState<AutomationRule | null>(null);

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [ruleToDelete, setRuleToDelete] = useState<AutomationRule | null>(null);
  const [deleting, setDeleting] = useState(false);

  const [copyDialogOpen, setCopyDialogOpen] = useState(false);

  const fetchRules = useCallback(async () => {
    if (!selectedGameId) return;
    setLoading(true);
    setError(null);
    try {
      const response = await api.getAutomations(selectedGameId);
      if (response.data) {
        setRules(response.data);
      } else {
        setError(response.error || 'Automatiseringen laden mislukt');
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Automatiseringen laden mislukt');
    } finally {
      setLoading(false);
    }
  }, [selectedGameId]);

  useEffect(() => {
    if (selectedGameId) {
      fetchRules();
    } else {
      setRules([]);
    }
  }, [selectedGameId, fetchRules]);

  // Live refresh when a rule fires.
  useEffect(() => {
    if (!selectedGameId) return;
    const unsubscribe = websocket.on(WebSocketEventType.AUTOMATION_TRIGGERED, () => {
      fetchRules();
    });
    return () => unsubscribe();
  }, [selectedGameId, fetchRules]);

  const handleToggleEnabled = async (rule: AutomationRule, enabled: boolean) => {
    if (!selectedGameId) return;
    // Optimistic toggle; reconcile with the server response.
    setRules((prev) => prev.map((r) => (r.id === rule.id ? { ...r, enabled } : r)));
    const response = await api.setAutomationEnabled(selectedGameId, rule.id, enabled);
    if (response.error) {
      setError(response.error);
      await fetchRules();
    }
  };

  const handleDeleteConfirm = async () => {
    if (!ruleToDelete || !selectedGameId) return;
    setDeleting(true);
    try {
      const response = await api.deleteAutomation(selectedGameId, ruleToDelete.id);
      if (response.error) {
        setError(response.error);
      } else {
        setDeleteDialogOpen(false);
        setRuleToDelete(null);
        await fetchRules();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Verwijderen mislukt');
    } finally {
      setDeleting(false);
    }
  };

  if (!selectedGameId) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold', mb: 4 }}>
          Automatiseringen
        </Typography>
        <Card>
          <CardContent>
            <Typography color="text.secondary">
              Selecteer een game in de zijbalk om automatiseringen te beheren.
            </Typography>
          </CardContent>
        </Card>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Automatiseringen
        </Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<CopyIcon />}
            onClick={() => setCopyDialogOpen(true)}
          >
            Kopiëren van andere game
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => {
              setSelectedRule(null);
              setFormDialogOpen(true);
            }}
          >
            Automatisering toevoegen
          </Button>
        </Box>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {loading && rules.length === 0 ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      ) : rules.length === 0 ? (
        <Card>
          <CardContent>
            <Typography color="text.secondary">
              Nog geen automatiseringen. Voeg er een toe om te beginnen.
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>Naam</strong></TableCell>
                <TableCell><strong>Trigger</strong></TableCell>
                <TableCell><strong>Acties</strong></TableCell>
                <TableCell><strong>Teams</strong></TableCell>
                <TableCell><strong>Actief</strong></TableCell>
                <TableCell><strong>Laatst gevuurd</strong></TableCell>
                <TableCell align="right"><strong>Beheer</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rules.map((rule) => (
                <TableRow key={rule.id} hover>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Typography variant="body1" fontWeight="medium">
                        {rule.name}
                      </Typography>
                      {rule.system && <Chip size="small" label="Systeem" color="info" />}
                    </Box>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{TRIGGER_LABELS[rule.triggerType]}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {describeTrigger(rule)}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    {rule.triggerType === 'DISTANCE_TRAVELED' ? (
                      <Chip size="small" label="Punten per afstand" variant="outlined" />
                    ) : (
                      <Box sx={{ display: 'flex', gap: 0.5, flexWrap: 'wrap' }}>
                        {rule.actions.map((action, index) => (
                          <Chip key={index} size="small" label={actionLabel(action)} variant="outlined" />
                        ))}
                      </Box>
                    )}
                  </TableCell>
                  <TableCell>
                    {rule.targetTeamIds && rule.targetTeamIds.length > 0
                      ? `${rule.targetTeamIds.length} team(s)`
                      : 'Alle'}
                  </TableCell>
                  <TableCell>
                    {rule.triggerType === 'DISTANCE_TRAVELED' ? (
                      <Tooltip title="Punten worden automatisch toegekend zolang de game actief is">
                        <Chip size="small" label="Tijdens de game" variant="outlined" />
                      </Tooltip>
                    ) : (
                      <Switch
                        checked={rule.enabled}
                        onChange={(e) => handleToggleEnabled(rule, e.target.checked)}
                        size="small"
                      />
                    )}
                  </TableCell>
                  <TableCell>
                    {rule.lastFiredAt ? (
                      <>
                        <RelativeTime date={rule.lastFiredAt} />
                        <Typography variant="caption" color="text.secondary" display="block">
                          {rule.executionCount}×
                        </Typography>
                      </>
                    ) : (
                      '–'
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {rule.triggerType !== 'DISTANCE_TRAVELED' && (
                      <IconButton
                        size="small"
                        onClick={() => setHistoryRule(rule)}
                        title="Historie bekijken"
                      >
                        <HistoryIcon />
                      </IconButton>
                    )}
                    <IconButton
                      size="small"
                      onClick={() => {
                        setSelectedRule(rule);
                        setFormDialogOpen(true);
                      }}
                      title="Automatisering bewerken"
                    >
                      <EditIcon />
                    </IconButton>
                    {!rule.system && (
                      <IconButton
                        size="small"
                        color="error"
                        onClick={() => {
                          setRuleToDelete(rule);
                          setDeleteDialogOpen(true);
                        }}
                        title="Automatisering verwijderen"
                      >
                        <DeleteIcon />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <AutomationFormDialog
        open={formDialogOpen}
        onClose={() => {
          setFormDialogOpen(false);
          setSelectedRule(null);
        }}
        rule={selectedRule}
        gameId={selectedGameId}
        onSave={fetchRules}
      />

      <AutomationExecutionsDialog
        open={historyRule !== null}
        onClose={() => setHistoryRule(null)}
        rule={historyRule}
        gameId={selectedGameId}
      />

      <CopyAutomationsDialog
        open={copyDialogOpen}
        onClose={() => setCopyDialogOpen(false)}
        gameId={selectedGameId}
        onCopied={fetchRules}
      />

      <Dialog open={deleteDialogOpen} onClose={() => setDeleteDialogOpen(false)}>
        <DialogTitle>Automatisering verwijderen</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Weet je zeker dat je "{ruleToDelete?.name}" wilt verwijderen? De historie van deze
            automatisering wordt ook verwijderd. Deze actie kan niet ongedaan worden gemaakt.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>Annuleren</Button>
          <Button onClick={handleDeleteConfirm} color="error" variant="contained" disabled={deleting}>
            {deleting ? <CircularProgress size={20} /> : 'Verwijderen'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
