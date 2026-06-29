import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
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

export default function Automations() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  const TRIGGER_LABELS: Record<AutomationRule['triggerType'], string> = {
    CHECKPOINT_ENTRY: t('components.automationForm.triggerCheckpointEntry'),
    SCHEDULED_TIME: t('components.automationForm.triggerScheduledTime'),
    GAME_STATUS_CHANGE: t('components.automationForm.triggerGameStatusChange'),
    TEAM_INACTIVITY: t('components.automationForm.triggerTeamInactivity'),
    RIVAL_OVERTAKE: t('components.automationForm.triggerRivalOvertake'),
    DISTANCE_TRAVELED: t('components.automationForm.triggerDistanceTraveled'),
    SPEED_ALERT: t('components.automationForm.triggerSpeedAlert'),
    TEAM_CHECK_IN: t('components.automationForm.triggerTeamCheckIn'),
  };

  /** "om 17:00" / "bij einde" / "30 min voor start" */
  function describeTimeSpec(config: AutomationRule['triggerConfig']): string {
    if (config.mode === 'RELATIVE') {
      const anchor = config.relativeTo === 'GAME_END'
        ? t('admin.automations.anchorEnd')
        : t('admin.automations.anchorStart');
      const offset = config.offsetMinutes ?? 0;
      if (offset === 0) return t('admin.automations.timeAtAnchor', { anchor });
      return offset < 0
        ? t('admin.automations.timeOffsetBefore', { minutes: Math.abs(offset), anchor })
        : t('admin.automations.timeOffsetAfter', { minutes: offset, anchor });
    }
    return config.absoluteTime
      ? new Date(config.absoluteTime).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' })
      : '?';
  }

  /** Human summary of the trigger */
  function describeTrigger(rule: AutomationRule): string {
    const config = rule.triggerConfig ?? {};
    switch (rule.triggerType) {
      case 'DISTANCE_TRAVELED':
        return t('admin.automations.descDistanceTraveled', { points: config.pointsPer100m ?? '?' });
      case 'TEAM_INACTIVITY':
        return t('admin.automations.descTeamInactivity', { minutes: config.inactiveMinutes ?? '?' });
      case 'GAME_STATUS_CHANGE':
        return config.toStatus === 'COMPLETED'
          ? t('admin.automations.descGameCompleted')
          : t('admin.automations.descGameStarted');
      case 'SCHEDULED_TIME':
        return describeTimeSpec(config);
      case 'CHECKPOINT_ENTRY': {
        const base = config.checkpointId
          ? t('admin.automations.descCheckpointOne')
          : t('admin.automations.descCheckpointAll');
        if (!config.comparison) return base;
        return config.comparison === 'BEFORE'
          ? t('admin.automations.descCheckpointTimeBefore', { base, time: describeTimeSpec(config) })
          : t('admin.automations.descCheckpointTimeAfter', { base, time: describeTimeSpec(config) });
      }
      case 'RIVAL_OVERTAKE':
        return t('admin.automations.descRivalOvertake');
      case 'SPEED_ALERT':
        return t('admin.automations.descSpeedAlert', { threshold: config.speedThresholdKmh ?? '?' });
      case 'TEAM_CHECK_IN':
        return t('admin.automations.descTeamCheckIn');
    }
  }

  function actionLabel(action: AutomationAction): string {
    switch (action.type) {
      case 'SEND_MESSAGE':
        return t('components.automationForm.actionSendMessage');
      case 'AWARD_POINTS':
        return `${(action.points ?? 0) > 0 ? '+' : ''}${action.points ?? 0} ${t('common.points').toLowerCase()}`;
      case 'NOTIFY_ADMINS':
        return t('components.automationForm.actionNotifyAdmins');
      case 'FINISH_TEAM':
        return t('components.automationForm.actionFinishTeam');
      case 'SET_RANKINGS_PUBLISHED':
        return t('components.automationForm.actionSetRankingsPublished');
      case 'SET_PUBLIC_RANKINGS_ENABLED':
        return t('components.automationForm.actionSetPublicRankingsEnabled');
    }
  }

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
        setError(response.error || t('admin.automations.loadFailed'));
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('admin.automations.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [selectedGameId, t]);

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
      setError(err instanceof Error ? err.message : t('admin.automations.deleteFailed'));
    } finally {
      setDeleting(false);
    }
  };

  if (!selectedGameId) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold', mb: 4 }}>
          {t('admin.automations.title')}
        </Typography>
        <Card>
          <CardContent>
            <Typography color="text.secondary">
              {t('admin.automations.selectGameFirst')}
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
          {t('admin.automations.title')}
        </Typography>
        <Box sx={{ display: 'flex', gap: 1 }}>
          <Button
            variant="outlined"
            startIcon={<CopyIcon />}
            onClick={() => setCopyDialogOpen(true)}
          >
            {t('admin.automations.copyFromGame')}
          </Button>
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => {
              setSelectedRule(null);
              setFormDialogOpen(true);
            }}
          >
            {t('admin.automations.addAutomation')}
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
              {t('admin.automations.noAutomations')}
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <TableContainer component={Paper}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell><strong>{t('admin.automations.columnName')}</strong></TableCell>
                <TableCell><strong>{t('admin.automations.columnTrigger')}</strong></TableCell>
                <TableCell><strong>{t('admin.automations.columnActions')}</strong></TableCell>
                <TableCell><strong>{t('admin.automations.columnTeams')}</strong></TableCell>
                <TableCell><strong>{t('admin.automations.columnActive')}</strong></TableCell>
                <TableCell><strong>{t('admin.automations.columnLastFired')}</strong></TableCell>
                <TableCell align="right"><strong>{t('admin.automations.columnManage')}</strong></TableCell>
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
                      {rule.system && <Chip size="small" label={t('admin.automations.systemLabel')} color="info" />}
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
                      <Chip size="small" label={t('admin.automations.distanceRateLabel')} variant="outlined" />
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
                      ? t('admin.automations.teamsCount', { count: rule.targetTeamIds.length })
                      : t('admin.automations.teamsAll')}
                  </TableCell>
                  <TableCell>
                    {rule.triggerType === 'DISTANCE_TRAVELED' ? (
                      <Tooltip title={t('admin.automations.duringGameTooltip')}>
                        <Chip size="small" label={t('admin.automations.duringGame')} variant="outlined" />
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
                        title={t('admin.automations.viewHistory')}
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
                      title={t('admin.automations.editAutomation')}
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
                        title={t('admin.automations.deleteAutomation')}
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
        <DialogTitle>{t('admin.automations.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('admin.automations.deleteConfirm', { name: ruleToDelete?.name })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteDialogOpen(false)}>{t('common.cancel')}</Button>
          <Button onClick={handleDeleteConfirm} color="error" variant="contained" disabled={deleting}>
            {deleting ? <CircularProgress size={20} /> : t('common.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
