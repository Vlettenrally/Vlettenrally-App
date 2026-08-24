import { useCallback, useEffect, useMemo, useState } from 'react';
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
  TableSortLabel,
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
import { useAdminResync } from '../../hooks/useAdminSync';
import { useAdminTopicHeartbeat, ADMIN_TOPICS } from '../../hooks/useAdminHeartbeat';
import { RelativeTime } from '../../components/RelativeTime';
import AutomationFormDialog from '../../components/AutomationFormDialog';
import AutomationExecutionsDialog from '../../components/AutomationExecutionsDialog';
import CopyAutomationsDialog from '../../components/CopyAutomationsDialog';
import type { AutomationAction, AutomationRule } from '../../types/entities';

type SortColumn = 'name' | 'trigger' | 'teams' | 'active' | 'lastFired';
type SortOrder = 'asc' | 'desc';

const compareByName = (a: AutomationRule, b: AutomationRule) =>
  a.name.localeCompare(b.name, 'nl', { sensitivity: 'base' });

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
    PICTURE_APPROVED: t('components.automationForm.triggerPictureApproved'),
  };

  function compareRules(a: AutomationRule, b: AutomationRule, column: SortColumn): number {
    switch (column) {
      case 'name':
        return compareByName(a, b);
      case 'trigger':
        return TRIGGER_LABELS[a.triggerType].localeCompare(
          TRIGGER_LABELS[b.triggerType],
          'nl',
          { sensitivity: 'base' },
        );
      case 'teams':
        return (a.targetTeamIds?.length ?? 0) - (b.targetTeamIds?.length ?? 0);
      case 'active':
        return Number(a.enabled) - Number(b.enabled);
      case 'lastFired':
        return (
          new Date(a.lastFiredAt ?? 0).getTime() - new Date(b.lastFiredAt ?? 0).getTime()
        );
    }
  }

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
        if (config.toStatus === 'COMPLETED') return t('admin.automations.descGameCompleted');
        if (config.toStatus === 'PAUSED') return t('admin.automations.descGamePaused');
        return t('admin.automations.descGameStarted');
      case 'SCHEDULED_TIME':
        return describeTimeSpec(config);
      case 'CHECKPOINT_ENTRY': {
        const ids = config.checkpointIds ?? [];
        const base =
          ids.length === 0
            ? t('admin.automations.descCheckpointAll')
            : ids.length === 1
              ? t('admin.automations.descCheckpointOne')
              : t('admin.automations.descCheckpointMany', { count: ids.length });
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
      case 'PICTURE_APPROVED':
        return config.approvedPictureCount
          ? t('admin.automations.descPictureApprovedThreshold', { count: config.approvedPictureCount })
          : t('admin.automations.descPictureApproved');
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
  const [orderBy, setOrderBy] = useState<SortColumn>('name');
  const [order, setOrder] = useState<SortOrder>('asc');
  const [loading, setLoading] = useState(() => !!selectedGameId);
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
    // setError/setRules/setLoading are stable useState setters — listed to satisfy
    // react-hooks/preserve-manual-memoization's dependency inference, not because
    // they're reactive.
  }, [selectedGameId, t, setError, setRules, setLoading]);

  // Drop the previous game's rules the moment the selection changes
  // (adjust-state-during-render, not an effect).
  const [prevGameId, setPrevGameId] = useState(selectedGameId);
  if (prevGameId !== selectedGameId) {
    setPrevGameId(selectedGameId);
    setRules([]);
    setError(null);
    setLoading(!!selectedGameId);
  }

  useEffect(() => {
    if (!selectedGameId) return;
    let cancelled = false;
    api
      .getAutomations(selectedGameId)
      .then((response) => {
        if (cancelled) return;
        if (response.data) setRules(response.data);
        else setError(response.error || t('admin.automations.loadFailed'));
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : t('admin.automations.loadFailed'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedGameId, t]);

  // Recover any AUTOMATION_TRIGGERED events missed while the page was backgrounded.
  useAdminResync(() => void fetchRules());

  // Only receive automation-fired events from the backend while this page is mounted.
  useAdminTopicHeartbeat(ADMIN_TOPICS.AUTOMATIONS);

  // Live refresh when a rule fires.
  useEffect(() => {
    if (!selectedGameId) return;
    const unsubscribe = websocket.on(WebSocketEventType.AUTOMATION_TRIGGERED, () => {
      fetchRules();
    });
    return () => unsubscribe();
  }, [selectedGameId, fetchRules]);

  const handleSort = (column: SortColumn) => {
    if (orderBy === column) {
      setOrder(order === 'asc' ? 'desc' : 'asc');
    } else {
      setOrderBy(column);
      setOrder('asc');
    }
  };

  const sortedRules = useMemo(() => {
    const sign = order === 'asc' ? 1 : -1;
    return [...rules].sort(
      (a, b) => sign * compareRules(a, b, orderBy) || compareByName(a, b),
    );
    // compareRules closes over the translated TRIGGER_LABELS; rules/orderBy/order drive re-sort.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rules, orderBy, order]);

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
                <TableCell sortDirection={orderBy === 'name' ? order : false}>
                  <TableSortLabel
                    active={orderBy === 'name'}
                    direction={orderBy === 'name' ? order : 'asc'}
                    onClick={() => handleSort('name')}
                  >
                    <strong>{t('admin.automations.columnName')}</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell sortDirection={orderBy === 'trigger' ? order : false}>
                  <TableSortLabel
                    active={orderBy === 'trigger'}
                    direction={orderBy === 'trigger' ? order : 'asc'}
                    onClick={() => handleSort('trigger')}
                  >
                    <strong>{t('admin.automations.columnTrigger')}</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell><strong>{t('admin.automations.columnActions')}</strong></TableCell>
                <TableCell sortDirection={orderBy === 'teams' ? order : false}>
                  <TableSortLabel
                    active={orderBy === 'teams'}
                    direction={orderBy === 'teams' ? order : 'asc'}
                    onClick={() => handleSort('teams')}
                  >
                    <strong>{t('admin.automations.columnTeams')}</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell sortDirection={orderBy === 'active' ? order : false}>
                  <TableSortLabel
                    active={orderBy === 'active'}
                    direction={orderBy === 'active' ? order : 'asc'}
                    onClick={() => handleSort('active')}
                  >
                    <strong>{t('admin.automations.columnActive')}</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell sortDirection={orderBy === 'lastFired' ? order : false}>
                  <TableSortLabel
                    active={orderBy === 'lastFired'}
                    direction={orderBy === 'lastFired' ? order : 'asc'}
                    onClick={() => handleSort('lastFired')}
                  >
                    <strong>{t('admin.automations.columnLastFired')}</strong>
                  </TableSortLabel>
                </TableCell>
                <TableCell align="right"><strong>{t('admin.automations.columnManage')}</strong></TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {sortedRules.map((rule) => (
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
