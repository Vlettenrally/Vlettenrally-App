import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  CircularProgress,
  Alert,
  Typography,
  MenuItem,
  Select,
  InputLabel,
  FormControl,
  FormControlLabel,
  FormHelperText,
  Switch,
  Checkbox,
  IconButton,
  Autocomplete,
  Divider,
} from '@mui/material';
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material';
import { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import api from '../services/api';
import { formatDateTime, formatForDateTimeLocalInput } from '../utils/dateTime';
import { teamMatchesQuery } from '../utils/teamSearch';
import type {
  AdminTeam,
  AutomationAction,
  AutomationActionType,
  AutomationRule,
  AutomationRuleRequest,
  AutomationTriggerConfig,
  AutomationTriggerType,
  Checkpoint,
  MessageTemplate,
} from '../types/entities';

interface AutomationFormDialogProps {
  open: boolean;
  onClose: () => void;
  rule: AutomationRule | null;
  gameId: string;
  onSave?: () => void;
}

function emptyActionForType(type: AutomationActionType): AutomationAction {
  switch (type) {
    case 'SEND_MESSAGE': return { type, messageContent: '', messageType: 'GENERAL' };
    case 'AWARD_POINTS': return { type };
    case 'NOTIFY_ADMINS': return { type };
    case 'FINISH_TEAM': return { type };
    case 'SET_RANKINGS_PUBLISHED': return { type };
    case 'SET_PUBLIC_RANKINGS_ENABLED': return { type };
  }
}

export default function AutomationFormDialog({
  open,
  onClose,
  rule,
  gameId,
  onSave,
}: AutomationFormDialogProps) {
  const { t } = useTranslation();
  const isEdit = rule !== null;
  const isSystem = rule?.system ?? false;

  const TRIGGER_OPTIONS: { value: AutomationTriggerType; label: string }[] = [
    { value: 'CHECKPOINT_ENTRY', label: t('components.automationForm.triggerCheckpointEntry') },
    { value: 'SCHEDULED_TIME', label: t('components.automationForm.triggerScheduledTime') },
    { value: 'GAME_STATUS_CHANGE', label: t('components.automationForm.triggerGameStatusChange') },
    { value: 'TEAM_INACTIVITY', label: t('components.automationForm.triggerTeamInactivity') },
    { value: 'RIVAL_OVERTAKE', label: t('components.automationForm.triggerRivalOvertake') },
    { value: 'SPEED_ALERT', label: t('components.automationForm.triggerSpeedAlert') },
    { value: 'TEAM_CHECK_IN', label: t('components.automationForm.triggerTeamCheckIn') },
  ];

  const ACTION_OPTIONS: { value: AutomationActionType; label: string }[] = [
    { value: 'SEND_MESSAGE', label: t('components.automationForm.actionSendMessage') },
    { value: 'AWARD_POINTS', label: t('components.automationForm.actionAwardPoints') },
    { value: 'NOTIFY_ADMINS', label: t('components.automationForm.actionNotifyAdmins') },
    { value: 'FINISH_TEAM', label: t('components.automationForm.actionFinishTeam') },
    { value: 'SET_RANKINGS_PUBLISHED', label: t('components.automationForm.actionSetRankingsPublished') },
    { value: 'SET_PUBLIC_RANKINGS_ENABLED', label: t('components.automationForm.actionSetPublicRankingsEnabled') },
  ];

  const MESSAGE_TYPE_OPTIONS = [
    { value: 'GENERAL', label: t('components.automationForm.typeGeneral') },
    { value: 'COUNTDOWN', label: t('components.automationForm.typeCountdown') },
    { value: 'CHECKPOINT', label: t('components.automationForm.typeCheckpoint') },
    { value: 'RALLYDESK', label: t('components.automationForm.typeRallydesk') },
    { value: 'ACHIEVEMENT', label: t('components.automationForm.typeAchievement') },
    { value: 'RIVAL', label: t('components.automationForm.typeRival') },
    { value: 'SPEED_WARNING', label: t('components.automationForm.typeSpeedWarning') },
  ];

  const TRIGGER_HELP: Record<AutomationTriggerType, string> = {
    CHECKPOINT_ENTRY: t('components.automationForm.helpCheckpointEntry'),
    SCHEDULED_TIME: t('components.automationForm.helpScheduledTime'),
    GAME_STATUS_CHANGE: t('components.automationForm.helpGameStatusChange'),
    TEAM_INACTIVITY: t('components.automationForm.helpTeamInactivity'),
    RIVAL_OVERTAKE: t('components.automationForm.helpRivalOvertake'),
    DISTANCE_TRAVELED: t('components.automationForm.helpDistanceTraveled'),
    SPEED_ALERT: t('components.automationForm.helpSpeedAlert'),
    TEAM_CHECK_IN: t('components.automationForm.helpTeamCheckIn'),
  };

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [triggerType, setTriggerType] = useState<AutomationTriggerType>('CHECKPOINT_ENTRY');
  const [config, setConfig] = useState<AutomationTriggerConfig>({});
  const [actions, setActions] = useState<AutomationAction[]>([emptyActionForType('SEND_MESSAGE')]);
  const [targetTeamIds, setTargetTeamIds] = useState<string[]>([]);
  const [fireOnce, setFireOnce] = useState(true);
  const [cooldownMinutes, setCooldownMinutes] = useState<number | ''>('');
  const [triggerForFinishedTeams, setTriggerForFinishedTeams] = useState(false);

  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);
  const [templates, setTemplates] = useState<MessageTemplate[]>([]);

  const loadOptions = useCallback(async () => {
    const [teamsResponse, checkpointsResponse, templatesResponse] = await Promise.all([
      api.getTeamsByGame(gameId),
      api.getCheckpoints(gameId),
      api.getMessageTemplates(),
    ]);
    if (teamsResponse.data) setTeams(teamsResponse.data);
    if (checkpointsResponse.data) setCheckpoints(checkpointsResponse.data);
    if (templatesResponse.data) setTemplates(templatesResponse.data);
  }, [gameId]);

  useEffect(() => {
    if (!open) return;
    loadOptions();
    setError(null);
    if (rule) {
      setName(rule.name);
      setEnabled(rule.enabled);
      setTriggerType(rule.triggerType);
      setConfig(rule.triggerConfig ?? {});
      setActions(rule.actions?.length ? rule.actions : rule.system ? [] : [emptyActionForType('SEND_MESSAGE')]);
      setTargetTeamIds(rule.targetTeamIds ?? []);
      setFireOnce(rule.fireOnce);
      setCooldownMinutes(rule.cooldownMinutes ?? '');
      setTriggerForFinishedTeams(rule.triggerForFinishedTeams ?? false);
    } else {
      setName('');
      setEnabled(true);
      setTriggerType('CHECKPOINT_ENTRY');
      setConfig({});
      setActions([emptyActionForType('SEND_MESSAGE')]);
      setTargetTeamIds([]);
      setFireOnce(true);
      setCooldownMinutes('');
      setTriggerForFinishedTeams(false);
    }
  }, [open, rule, loadOptions]);

  const isDistance = triggerType === 'DISTANCE_TRAVELED';

  // Placeholders usable in message/notification text for the selected trigger.
  const placeholderHint =
    t('components.automationForm.placeholderExample') +
    (triggerType === 'CHECKPOINT_ENTRY' ? t('components.automationForm.placeholderCheckpoint') : '') +
    (triggerType === 'RIVAL_OVERTAKE' ? t('components.automationForm.placeholderRival') : '');

  const handleTriggerTypeChange = (value: AutomationTriggerType) => {
    setTriggerType(value);
    setConfig({});
    // Inactivity and speed alerts re-fire with a cooldown; everything else fires once.
    if (value === 'TEAM_INACTIVITY') {
      setFireOnce(false);
      setCooldownMinutes(60);
    } else if (value === 'SPEED_ALERT') {
      setFireOnce(false);
      setCooldownMinutes(5);
    } else {
      setFireOnce(true);
      setCooldownMinutes('');
    }
  };

  const updateAction = (index: number, patch: Record<string, unknown>) => {
    setActions((prev) =>
      prev.map((action, i) => (i === index ? ({ ...action, ...patch } as AutomationAction) : action))
    );
  };

  const handleSubmit = async () => {
    setLoading(true);
    setError(null);
    const request: AutomationRuleRequest = {
      name,
      enabled,
      triggerType,
      triggerConfig: {
        ...config,
        // Rules use `checkpointIds` (existing data was normalised by migration V79);
        // never write the legacy single-checkpoint / exclusion fields back.
        checkpointId: undefined,
        excludedCheckpointIds: undefined,
        absoluteTime: config.absoluteTime ? new Date(config.absoluteTime).toISOString() : undefined,
      },
      actions: isDistance ? [] : actions,
      targetTeamIds: targetTeamIds.length > 0 ? targetTeamIds : undefined,
      fireOnce,
      cooldownMinutes: !fireOnce && cooldownMinutes !== '' ? Number(cooldownMinutes) : undefined,
      triggerForFinishedTeams,
    };
    try {
      const response = isEdit
        ? await api.updateAutomation(gameId, rule.id, request)
        : await api.createAutomation(gameId, request);
      if (response.error) {
        setError(response.error);
      } else {
        onSave?.();
        onClose();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('components.automationForm.saveFailed'));
    } finally {
      setLoading(false);
    }
  };

  // Shared ABSOLUTE/RELATIVE time inputs: the fire-at time of a scheduled rule,
  // or the boundary time of a checkpoint before/after condition.
  const renderTimeSpecFields = () => (
    <>
      <FormControl fullWidth>
        <InputLabel>{t('components.automationForm.when')}</InputLabel>
        <Select
          label={t('components.automationForm.when')}
          value={config.mode ?? 'ABSOLUTE'}
          onChange={(e) =>
            setConfig({
              ...config,
              mode: e.target.value as 'ABSOLUTE' | 'RELATIVE',
              absoluteTime: undefined,
              relativeTo: undefined,
              offsetMinutes: undefined,
            })
          }
        >
          <MenuItem value="ABSOLUTE">{t('components.automationForm.absolute')}</MenuItem>
          <MenuItem value="RELATIVE">{t('components.automationForm.relative')}</MenuItem>
        </Select>
      </FormControl>
      {(config.mode ?? 'ABSOLUTE') === 'ABSOLUTE' ? (
        <TextField
          fullWidth
          type="datetime-local"
          label={t('components.automationForm.timeField')}
          value={config.absoluteTime ? formatForDateTimeLocalInput(config.absoluteTime) : ''}
          onChange={(e) => setConfig({ ...config, mode: 'ABSOLUTE', absoluteTime: e.target.value })}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      ) : (
        <Box sx={{ display: 'flex', gap: 2 }}>
          <FormControl fullWidth>
            <InputLabel>{t('components.automationForm.anchor')}</InputLabel>
            <Select
              label={t('components.automationForm.anchor')}
              value={config.relativeTo ?? 'GAME_START'}
              onChange={(e) =>
                setConfig({
                  ...config,
                  mode: 'RELATIVE',
                  relativeTo: e.target.value as 'GAME_START' | 'GAME_END',
                })
              }
            >
              <MenuItem value="GAME_START">{t('components.automationForm.gameStart')}</MenuItem>
              <MenuItem value="GAME_END">{t('components.automationForm.gameEnd')}</MenuItem>
            </Select>
          </FormControl>
          <TextField
            fullWidth
            type="number"
            label={t('components.automationForm.offset')}
            helperText={t('components.automationForm.offsetHint')}
            value={config.offsetMinutes ?? ''}
            onChange={(e) =>
              setConfig({
                ...config,
                mode: 'RELATIVE',
                relativeTo: config.relativeTo ?? 'GAME_START',
                offsetMinutes: e.target.value === '' ? undefined : Number(e.target.value),
              })
            }
          />
        </Box>
      )}
    </>
  );

  // Human-readable opening window of a checkpoint, or null when it is always open.
  const checkpointWindow = (cp: Checkpoint): string | null => {
    if (cp.openingTime && cp.closingTime) {
      return t('components.automationForm.windowRange', {
        from: formatDateTime(cp.openingTime),
        to: formatDateTime(cp.closingTime),
      });
    }
    if (cp.openingTime) {
      return t('components.automationForm.windowOpenFrom', { from: formatDateTime(cp.openingTime) });
    }
    if (cp.closingTime) {
      return t('components.automationForm.windowOpenUntil', { to: formatDateTime(cp.closingTime) });
    }
    return null;
  };

  // Warn that the checkpoint's opening window gates this automation (it only
  // fires while the checkpoint is open). Shows the concrete window for a specific
  // checkpoint, or a generic note when firing on any checkpoint.
  const renderCheckpointWindowNotice = () => {
    const selectedIds = config.checkpointIds ?? [];
    if (selectedIds.length > 0) {
      const withWindows = checkpoints
        .filter((cp) => selectedIds.includes(cp.id))
        .map((cp) => ({ cp, window: checkpointWindow(cp) }))
        .filter((entry): entry is { cp: Checkpoint; window: string } => entry.window !== null);
      if (withWindows.length === 0) return null;
      return (
        <Alert severity="info">
          {withWindows.map(({ cp, window }) => (
            <div key={cp.id}>
              {t('components.automationForm.windowNoticeSpecificNamed', { name: cp.name, window })}
            </div>
          ))}
        </Alert>
      );
    }
    if (checkpoints.some((cp) => cp.openingTime || cp.closingTime)) {
      return <Alert severity="info">{t('components.automationForm.windowNoticeAll')}</Alert>;
    }
    return null;
  };

  const renderTriggerConfig = () => {
    switch (triggerType) {
      case 'CHECKPOINT_ENTRY':
        return (
          <>
            <Box>
              <Autocomplete
                multiple
                options={checkpoints}
                getOptionLabel={(cp) => cp.name}
                value={checkpoints.filter((cp) => (config.checkpointIds ?? []).includes(cp.id))}
                onChange={(_, value) =>
                  setConfig({
                    ...config,
                    checkpointIds: value.length > 0 ? value.map((cp) => cp.id) : undefined,
                  })
                }
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label={t('components.automationForm.checkpoints')}
                    placeholder={t('components.automationForm.allCheckpoints')}
                    helperText={t('components.automationForm.checkpointsHint')}
                  />
                )}
              />
              <Box sx={{ mt: 0.5, display: 'flex', gap: 1 }}>
                <Button
                  size="small"
                  disabled={checkpoints.length === 0}
                  onClick={() =>
                    setConfig({ ...config, checkpointIds: checkpoints.map((cp) => cp.id) })
                  }
                >
                  {t('components.automationForm.selectAllCheckpoints')}
                </Button>
                <Button
                  size="small"
                  disabled={!config.checkpointIds?.length}
                  onClick={() => setConfig({ ...config, checkpointIds: undefined })}
                >
                  {t('components.automationForm.deselectAllCheckpoints')}
                </Button>
              </Box>
            </Box>
            {renderCheckpointWindowNotice()}
            <FormControl fullWidth>
              <InputLabel>{t('components.automationForm.timeCondition')}</InputLabel>
              <Select
                label={t('components.automationForm.timeCondition')}
                value={config.comparison ?? ''}
                onChange={(e) => {
                  const comparison = (e.target.value || undefined) as 'BEFORE' | 'AFTER' | undefined;
                  setConfig(
                    comparison
                      ? { ...config, comparison, mode: config.mode ?? 'ABSOLUTE' }
                      : { checkpointIds: config.checkpointIds }
                  );
                }}
              >
                <MenuItem value="">{t('components.automationForm.anyPass')}</MenuItem>
                <MenuItem value="BEFORE">{t('components.automationForm.beforeTime')}</MenuItem>
                <MenuItem value="AFTER">{t('components.automationForm.afterTime')}</MenuItem>
              </Select>
              <FormHelperText>
                {t('components.automationForm.timeConditionHint')}
              </FormHelperText>
            </FormControl>
            {config.comparison && renderTimeSpecFields()}
          </>
        );
      case 'SCHEDULED_TIME':
        return renderTimeSpecFields();
      case 'GAME_STATUS_CHANGE':
        return (
          <FormControl fullWidth>
            <InputLabel>{t('components.automationForm.newStatus')}</InputLabel>
            <Select
              label={t('components.automationForm.newStatus')}
              value={config.toStatus ?? ''}
              onChange={(e) => setConfig({ toStatus: e.target.value as 'ACTIVE' | 'COMPLETED' })}
            >
              <MenuItem value="ACTIVE">{t('components.automationForm.statusActive')}</MenuItem>
              <MenuItem value="COMPLETED">{t('components.automationForm.statusCompleted')}</MenuItem>
            </Select>
          </FormControl>
        );
      case 'TEAM_INACTIVITY':
        return (
          <TextField
            fullWidth
            type="number"
            label={t('components.automationForm.inactiveAfter')}
            value={config.inactiveMinutes ?? ''}
            onChange={(e) =>
              setConfig({ inactiveMinutes: e.target.value === '' ? undefined : Number(e.target.value) })
            }
          />
        );
      case 'RIVAL_OVERTAKE':
        return null;
      case 'TEAM_CHECK_IN':
        return null;
      case 'SPEED_ALERT':
        return (
          <TextField
            fullWidth
            type="number"
            label={t('components.automationForm.speedThreshold')}
            value={config.speedThresholdKmh ?? ''}
            onChange={(e) =>
              setConfig({ speedThresholdKmh: e.target.value === '' ? undefined : Number(e.target.value) })
            }
            slotProps={{ htmlInput: { min: 1 } }}
          />
        );
      case 'DISTANCE_TRAVELED':
        return (
          <TextField
            fullWidth
            type="number"
            label={t('components.automationForm.pointsPer100m')}
            value={config.pointsPer100m ?? ''}
            onChange={(e) =>
              setConfig({ pointsPer100m: e.target.value === '' ? undefined : Number(e.target.value) })
            }
            helperText={t('components.automationForm.pointsPer100mHint')}
          />
        );
    }
  };

  const renderActionFields = (action: AutomationAction, index: number) => {
    switch (action.type) {
      case 'SEND_MESSAGE': {
        const selectedTemplate = templates.find((tmpl) => tmpl.id === action.messageTemplateId) ?? null;
        return (
          <>
            <FormControl fullWidth>
              <InputLabel>{t('components.automationForm.template')}</InputLabel>
              <Select
                label={t('components.automationForm.template')}
                value={action.messageTemplateId ?? ''}
                onChange={(e) =>
                  updateAction(index, { messageTemplateId: e.target.value || undefined })
                }
              >
                <MenuItem value="">{t('components.automationForm.noTemplate')}</MenuItem>
                {templates.map((template) => (
                  <MenuItem key={template.id} value={template.id}>
                    {template.name}
                  </MenuItem>
                ))}
              </Select>
              <FormHelperText>
                {t('components.automationForm.templateHint')}
              </FormHelperText>
            </FormControl>

            {selectedTemplate ? (
              <TextField
                fullWidth
                multiline
                minRows={2}
                label={t('components.automationForm.templateContent')}
                value={selectedTemplate.content}
                slotProps={{ input: {readOnly: true } }}
                helperText={placeholderHint}
              />
            ) : (
              <>
                <Box sx={{ display: 'flex', gap: 2 }}>
                  <FormControl sx={{ minWidth: 220 }}>
                    <InputLabel>{t('components.automationForm.messageType')}</InputLabel>
                    <Select
                      label={t('components.automationForm.messageType')}
                      value={action.messageType ?? 'GENERAL'}
                      onChange={(e) => updateAction(index, { messageType: e.target.value })}
                    >
                      {MESSAGE_TYPE_OPTIONS.map((option) => (
                        <MenuItem key={option.value} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField
                    label={t('components.automationForm.iconOptional')}
                    value={action.messageIcon ?? ''}
                    onChange={(e) => updateAction(index, { messageIcon: e.target.value || undefined })}
                    sx={{ width: 140 }}
                  />
                </Box>
                <TextField
                  fullWidth
                  multiline
                  minRows={2}
                  label={t('components.automationForm.message')}
                  value={action.messageContent ?? ''}
                  onChange={(e) => updateAction(index, { messageContent: e.target.value })}
                  helperText={placeholderHint}
                />
              </>
            )}
          </>
        );
      }
      case 'AWARD_POINTS':
        return (
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              type="number"
              label={t('components.automationForm.points')}
              value={action.points ?? ''}
              onChange={(e) =>
                updateAction(index, { points: e.target.value === '' ? undefined : Number(e.target.value) })
              }
              sx={{ width: 140 }}
            />
            <TextField
              fullWidth
              label={t('components.automationForm.pointsDescription')}
              value={action.pointsDescription ?? ''}
              onChange={(e) => updateAction(index, { pointsDescription: e.target.value || undefined })}
            />
          </Box>
        );
      case 'NOTIFY_ADMINS':
        return (
          <TextField
            fullWidth
            label={t('components.automationForm.notificationText')}
            value={action.adminNotification ?? ''}
            onChange={(e) => updateAction(index, { adminNotification: e.target.value })}
            helperText={placeholderHint}
          />
        );
      case 'FINISH_TEAM':
        return (
          <FormHelperText>
            {t('components.automationForm.finishTeamHint')}
          </FormHelperText>
        );
      case 'SET_RANKINGS_PUBLISHED':
        return (
          <>
            <FormControl fullWidth>
              <InputLabel>{t('components.automationForm.setRankingsPublishedLabel')}</InputLabel>
              <Select
                label={t('components.automationForm.setRankingsPublishedLabel')}
                value={action.rankingsPublished === true ? 'true' : action.rankingsPublished === false ? 'false' : ''}
                onChange={(e) =>
                  updateAction(index, { rankingsPublished: e.target.value === 'true' })
                }
              >
                <MenuItem value="true">{t('components.automationForm.setRankingsPublishedPublish')}</MenuItem>
                <MenuItem value="false">{t('components.automationForm.setRankingsPublishedHide')}</MenuItem>
              </Select>
            </FormControl>
            <FormHelperText>{t('components.automationForm.setRankingsPublishedHint')}</FormHelperText>
          </>
        );
      case 'SET_PUBLIC_RANKINGS_ENABLED':
        return (
          <>
            <FormControl fullWidth>
              <InputLabel>{t('components.automationForm.setPublicRankingsEnabledLabel')}</InputLabel>
              <Select
                label={t('components.automationForm.setPublicRankingsEnabledLabel')}
                value={action.publicRankingsEnabled === true ? 'true' : action.publicRankingsEnabled === false ? 'false' : ''}
                onChange={(e) =>
                  updateAction(index, { publicRankingsEnabled: e.target.value === 'true' })
                }
              >
                <MenuItem value="true">{t('components.automationForm.setPublicRankingsEnabledEnable')}</MenuItem>
                <MenuItem value="false">{t('components.automationForm.setPublicRankingsEnabledDisable')}</MenuItem>
              </Select>
            </FormControl>
            <FormHelperText>{t('components.automationForm.setPublicRankingsEnabledHint')}</FormHelperText>
          </>
        );
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {isEdit ? t('components.automationForm.editTitle') : t('components.automationForm.addTitle')}
      </DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}

          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
            <TextField
              fullWidth
              label={t('components.automationForm.nameLabel')}
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            {!isDistance && (
              <FormControlLabel
                control={<Switch checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />}
                label={t('components.automationForm.active')}
              />
            )}
          </Box>

          <FormControl fullWidth disabled={isSystem}>
            <InputLabel>{t('components.automationForm.triggerLabel')}</InputLabel>
            <Select
              label={t('components.automationForm.triggerLabel')}
              value={triggerType}
              onChange={(e) => handleTriggerTypeChange(e.target.value as AutomationTriggerType)}
            >
              {TRIGGER_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
              {isDistance && <MenuItem value="DISTANCE_TRAVELED">{t('components.automationForm.triggerDistanceTraveled')}</MenuItem>}
            </Select>
            <FormHelperText>{TRIGGER_HELP[triggerType]}</FormHelperText>
          </FormControl>

          {renderTriggerConfig()}

          {!isDistance && (
            <>
              <Divider />
              <Typography variant="subtitle2">{t('components.automationForm.actionsLabel')}</Typography>
              {actions.map((action, index) => (
                <Box
                  key={index}
                  sx={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 1.5,
                    p: 1.5,
                    border: 1,
                    borderColor: 'divider',
                    borderRadius: 1,
                  }}
                >
                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                    <FormControl fullWidth size="small">
                      <InputLabel>{t('components.automationForm.actionLabel')}</InputLabel>
                      <Select
                        label={t('components.automationForm.actionLabel')}
                        value={action.type}
                        onChange={(e) =>
                          setActions((prev) =>
                            prev.map((a, i) =>
                              i === index
                                ? emptyActionForType(e.target.value as AutomationActionType)
                                : a
                            )
                          )
                        }
                      >
                        {ACTION_OPTIONS.map((option) => (
                          <MenuItem key={option.value} value={option.value}>
                            {option.label}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <IconButton
                      size="small"
                      color="error"
                      disabled={actions.length === 1}
                      onClick={() => setActions((prev) => prev.filter((_, i) => i !== index))}
                      title={t('components.automationForm.removeAction')}
                    >
                      <DeleteIcon />
                    </IconButton>
                  </Box>
                  {renderActionFields(action, index)}
                </Box>
              ))}
              <Button
                startIcon={<AddIcon />}
                onClick={() => setActions((prev) => [...prev, emptyActionForType('SEND_MESSAGE')])}
                sx={{ alignSelf: 'flex-start' }}
              >
                {t('components.automationForm.addAction')}
              </Button>

              <Divider />

              <Autocomplete
                multiple
                options={teams}
                getOptionLabel={(team) => team.name}
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
                value={teams.filter((team) => targetTeamIds.includes(team.id))}
                onChange={(_, value) => setTargetTeamIds(value.map((team) => team.id))}
                renderInput={(params) => (
                  <TextField {...params} label={t('components.automationForm.teamsLabel')} placeholder={t('components.automationForm.teamsPlaceholder')} />
                )}
              />

              <Box>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={fireOnce}
                      onChange={(e) => {
                        setFireOnce(e.target.checked);
                        if (e.target.checked) setCooldownMinutes('');
                      }}
                    />
                  }
                  label={t('components.automationForm.fireOnce')}
                />
                <FormHelperText sx={{ ml: 0 }}>
                  {t('components.automationForm.fireOnceHint')}
                </FormHelperText>
                {!fireOnce && (
                  <TextField
                    type="number"
                    label={t('components.automationForm.cooldown')}
                    value={cooldownMinutes}
                    onChange={(e) =>
                      setCooldownMinutes(e.target.value === '' ? '' : Number(e.target.value))
                    }
                    sx={{ mt: 1, width: 200 }}
                  />
                )}
              </Box>

              <FormControlLabel
                control={
                  <Checkbox
                    checked={triggerForFinishedTeams}
                    onChange={(e) => setTriggerForFinishedTeams(e.target.checked)}
                  />
                }
                label={t('components.automationForm.triggerForFinishedTeams')}
              />
            </>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={loading || !name.trim()}>
          {loading ? <CircularProgress size={20} /> : t('components.automationForm.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
