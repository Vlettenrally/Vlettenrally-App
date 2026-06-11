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
import api from '../services/api';
import { formatForDateTimeLocalInput } from '../utils/dateTime';
import type {
  AdminTeam,
  AutomationAction,
  AutomationActionType,
  AutomationRule,
  AutomationRuleRequest,
  AutomationTriggerConfig,
  AutomationTriggerType,
  Checkpoint,
} from '../types/entities';

interface AutomationFormDialogProps {
  open: boolean;
  onClose: () => void;
  rule: AutomationRule | null;
  gameId: string;
  onSave?: () => void;
}

const TRIGGER_OPTIONS: { value: AutomationTriggerType; label: string }[] = [
  { value: 'CHECKPOINT_ENTRY', label: 'Checkpoint bereikt' },
  { value: 'SCHEDULED_TIME', label: 'Tijdstip' },
  { value: 'GAME_STATUS_CHANGE', label: 'Game-status wijziging' },
  { value: 'TEAM_INACTIVITY', label: 'Team inactief' },
  { value: 'RIVAL_OVERTAKE', label: 'Rivaal ingehaald' },
];

const ACTION_OPTIONS: { value: AutomationActionType; label: string }[] = [
  { value: 'SEND_MESSAGE', label: 'Bericht naar team(s)' },
  { value: 'AWARD_POINTS', label: 'Punten toekennen' },
  { value: 'NOTIFY_ADMINS', label: 'Melding aan beheerders' },
];

// Backend MessageType enum values
const MESSAGE_TYPE_OPTIONS = [
  { value: 'GENERAL', label: 'Algemeen' },
  { value: 'COUNTDOWN', label: 'Countdown' },
  { value: 'CHECKPOINT', label: 'Checkpoint' },
  { value: 'RALLYDESK', label: 'Rallydesk (kritiek, SMS-fallback)' },
  { value: 'ACHIEVEMENT', label: 'Achievement' },
  { value: 'RIVAL', label: 'Rivaal' },
];

const TRIGGER_HELP: Record<AutomationTriggerType, string> = {
  CHECKPOINT_ENTRY: 'Vuurt zodra een team een checkpoint-zone bereikt (geverifieerde doorkomst).',
  SCHEDULED_TIME: 'Vuurt op een vast tijdstip of relatief aan het start-/eindsignaal van de game.',
  GAME_STATUS_CHANGE: 'Vuurt wanneer de game van status wisselt (actief of afgerond).',
  TEAM_INACTIVITY: 'Vuurt wanneer een team langere tijd geen locatie heeft doorgegeven.',
  RIVAL_OVERTAKE: 'Vuurt zodra een team in punten over een van zijn rivalen heen gaat.',
  DISTANCE_TRAVELED:
    'Systeemregel: teams krijgen automatisch punten per afgelegde afstand. ' +
    'Alleen het tarief is instelbaar.',
};

function emptyAction(): AutomationAction {
  return { type: 'SEND_MESSAGE', messageContent: '', messageType: 'GENERAL' };
}

export default function AutomationFormDialog({
  open,
  onClose,
  rule,
  gameId,
  onSave,
}: AutomationFormDialogProps) {
  const isEdit = rule !== null;
  const isSystem = rule?.system ?? false;

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [triggerType, setTriggerType] = useState<AutomationTriggerType>('CHECKPOINT_ENTRY');
  const [config, setConfig] = useState<AutomationTriggerConfig>({});
  const [actions, setActions] = useState<AutomationAction[]>([emptyAction()]);
  const [targetTeamIds, setTargetTeamIds] = useState<string[]>([]);
  const [fireOnce, setFireOnce] = useState(true);
  const [cooldownMinutes, setCooldownMinutes] = useState<number | ''>('');

  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [checkpoints, setCheckpoints] = useState<Checkpoint[]>([]);

  const loadOptions = useCallback(async () => {
    const [teamsResponse, checkpointsResponse] = await Promise.all([
      api.getTeamsByGame(gameId),
      api.getCheckpoints(gameId),
    ]);
    if (teamsResponse.data) setTeams(teamsResponse.data);
    if (checkpointsResponse.data) setCheckpoints(checkpointsResponse.data);
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
      setActions(rule.actions?.length ? rule.actions : rule.system ? [] : [emptyAction()]);
      setTargetTeamIds(rule.targetTeamIds ?? []);
      setFireOnce(rule.fireOnce);
      setCooldownMinutes(rule.cooldownMinutes ?? '');
    } else {
      setName('');
      setEnabled(true);
      setTriggerType('CHECKPOINT_ENTRY');
      setConfig({});
      setActions([emptyAction()]);
      setTargetTeamIds([]);
      setFireOnce(true);
      setCooldownMinutes('');
    }
  }, [open, rule, loadOptions]);

  const isDistance = triggerType === 'DISTANCE_TRAVELED';

  const handleTriggerTypeChange = (value: AutomationTriggerType) => {
    setTriggerType(value);
    setConfig({});
    // Inactivity re-fires with a cooldown by default; everything else fires once.
    if (value === 'TEAM_INACTIVITY') {
      setFireOnce(false);
      setCooldownMinutes(60);
    } else {
      setFireOnce(true);
      setCooldownMinutes('');
    }
  };

  const updateAction = (index: number, patch: Partial<AutomationAction>) => {
    setActions((prev) => prev.map((action, i) => (i === index ? { ...action, ...patch } : action)));
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
        absoluteTime: config.absoluteTime ? new Date(config.absoluteTime).toISOString() : undefined,
      },
      actions: isDistance ? [] : actions,
      targetTeamIds: targetTeamIds.length > 0 ? targetTeamIds : undefined,
      fireOnce,
      cooldownMinutes: !fireOnce && cooldownMinutes !== '' ? Number(cooldownMinutes) : undefined,
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
      setError(err instanceof Error ? err.message : 'Opslaan mislukt');
    } finally {
      setLoading(false);
    }
  };

  // Shared ABSOLUTE/RELATIVE time inputs: the fire-at time of a scheduled rule,
  // or the boundary time of a checkpoint before/after condition.
  const renderTimeSpecFields = () => (
    <>
      <FormControl fullWidth>
        <InputLabel>Wanneer</InputLabel>
        <Select
          label="Wanneer"
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
          <MenuItem value="ABSOLUTE">Vast tijdstip</MenuItem>
          <MenuItem value="RELATIVE">Relatief aan start/einde</MenuItem>
        </Select>
      </FormControl>
      {(config.mode ?? 'ABSOLUTE') === 'ABSOLUTE' ? (
        <TextField
          fullWidth
          type="datetime-local"
          label="Tijdstip"
          value={config.absoluteTime ? formatForDateTimeLocalInput(config.absoluteTime) : ''}
          onChange={(e) => setConfig({ ...config, mode: 'ABSOLUTE', absoluteTime: e.target.value })}
          InputLabelProps={{ shrink: true }}
        />
      ) : (
        <Box sx={{ display: 'flex', gap: 2 }}>
          <FormControl fullWidth>
            <InputLabel>Anker</InputLabel>
            <Select
              label="Anker"
              value={config.relativeTo ?? 'GAME_START'}
              onChange={(e) =>
                setConfig({
                  ...config,
                  mode: 'RELATIVE',
                  relativeTo: e.target.value as 'GAME_START' | 'GAME_END',
                })
              }
            >
              <MenuItem value="GAME_START">Startsignaal</MenuItem>
              <MenuItem value="GAME_END">Eindtijd</MenuItem>
            </Select>
          </FormControl>
          <TextField
            fullWidth
            type="number"
            label="Offset (minuten)"
            helperText="Negatief = ervoor, positief = erna"
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

  const renderTriggerConfig = () => {
    switch (triggerType) {
      case 'CHECKPOINT_ENTRY':
        return (
          <>
            <FormControl fullWidth>
              <InputLabel>Checkpoint</InputLabel>
              <Select
                label="Checkpoint"
                value={config.checkpointId ?? ''}
                onChange={(e) => setConfig({ ...config, checkpointId: e.target.value || undefined })}
              >
                <MenuItem value="">Alle checkpoints</MenuItem>
                {checkpoints.map((cp) => (
                  <MenuItem key={cp.id} value={cp.id}>
                    {cp.name}
                  </MenuItem>
                ))}
              </Select>
              <FormHelperText>Leeg = vuurt bij elk checkpoint (één keer per checkpoint per team)</FormHelperText>
            </FormControl>
            <FormControl fullWidth>
              <InputLabel>Tijdsvoorwaarde</InputLabel>
              <Select
                label="Tijdsvoorwaarde"
                value={config.comparison ?? ''}
                onChange={(e) => {
                  const comparison = (e.target.value || undefined) as 'BEFORE' | 'AFTER' | undefined;
                  setConfig(
                    comparison
                      ? { ...config, comparison, mode: config.mode ?? 'ABSOLUTE' }
                      : { checkpointId: config.checkpointId }
                  );
                }}
              >
                <MenuItem value="">Geen — elke doorkomst telt</MenuItem>
                <MenuItem value="BEFORE">Alleen vóór een tijdstip (bijv. finishbonus)</MenuItem>
                <MenuItem value="AFTER">Alleen na een tijdstip (bijv. te-laat-straf)</MenuItem>
              </Select>
              <FormHelperText>
                De doorkomsttijd van het team wordt vergeleken met het tijdstip — combineer met een
                punten-actie (negatief = straf, positief = bonus)
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
            <InputLabel>Nieuwe status</InputLabel>
            <Select
              label="Nieuwe status"
              value={config.toStatus ?? ''}
              onChange={(e) => setConfig({ toStatus: e.target.value as 'ACTIVE' | 'COMPLETED' })}
            >
              <MenuItem value="ACTIVE">Actief (startsignaal)</MenuItem>
              <MenuItem value="COMPLETED">Afgerond</MenuItem>
            </Select>
          </FormControl>
        );
      case 'TEAM_INACTIVITY':
        return (
          <TextField
            fullWidth
            type="number"
            label="Inactief na (minuten)"
            value={config.inactiveMinutes ?? ''}
            onChange={(e) =>
              setConfig({ inactiveMinutes: e.target.value === '' ? undefined : Number(e.target.value) })
            }
          />
        );
      case 'RIVAL_OVERTAKE':
        return null;
      case 'DISTANCE_TRAVELED':
        return (
          <TextField
            fullWidth
            type="number"
            label="Punten per 100 meter"
            value={config.pointsPer100m ?? ''}
            onChange={(e) =>
              setConfig({ pointsPer100m: e.target.value === '' ? undefined : Number(e.target.value) })
            }
            helperText="Teams krijgen automatisch dit aantal punten per 100 afgelegde meters"
          />
        );
    }
  };

  const renderActionFields = (action: AutomationAction, index: number) => {
    switch (action.type) {
      case 'SEND_MESSAGE':
        return (
          <>
            <Box sx={{ display: 'flex', gap: 2 }}>
              <FormControl sx={{ minWidth: 220 }}>
                <InputLabel>Berichttype</InputLabel>
                <Select
                  label="Berichttype"
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
                label="Icoon (optioneel)"
                value={action.messageIcon ?? ''}
                onChange={(e) => updateAction(index, { messageIcon: e.target.value || undefined })}
                sx={{ width: 140 }}
              />
            </Box>
            <TextField
              fullWidth
              multiline
              minRows={2}
              label="Bericht"
              value={action.messageContent ?? ''}
              onChange={(e) => updateAction(index, { messageContent: e.target.value })}
              helperText="{team} wordt vervangen door de teamnaam"
            />
          </>
        );
      case 'AWARD_POINTS':
        return (
          <Box sx={{ display: 'flex', gap: 2 }}>
            <TextField
              type="number"
              label="Punten"
              value={action.points ?? ''}
              onChange={(e) =>
                updateAction(index, { points: e.target.value === '' ? undefined : Number(e.target.value) })
              }
              sx={{ width: 140 }}
            />
            <TextField
              fullWidth
              label="Omschrijving (optioneel)"
              value={action.pointsDescription ?? ''}
              onChange={(e) => updateAction(index, { pointsDescription: e.target.value || undefined })}
            />
          </Box>
        );
      case 'NOTIFY_ADMINS':
        return (
          <TextField
            fullWidth
            label="Meldingstekst"
            value={action.adminNotification ?? ''}
            onChange={(e) => updateAction(index, { adminNotification: e.target.value })}
            helperText="{team} wordt vervangen door de teamnaam"
          />
        );
    }
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        {isEdit ? 'Automatisering bewerken' : 'Automatisering toevoegen'}
      </DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}

          <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
            <TextField
              fullWidth
              label="Naam"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <FormControlLabel
              control={<Switch checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />}
              label="Actief"
            />
          </Box>

          <FormControl fullWidth disabled={isSystem}>
            <InputLabel>Trigger</InputLabel>
            <Select
              label="Trigger"
              value={triggerType}
              onChange={(e) => handleTriggerTypeChange(e.target.value as AutomationTriggerType)}
            >
              {TRIGGER_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
              {isDistance && <MenuItem value="DISTANCE_TRAVELED">Afgelegde afstand (systeem)</MenuItem>}
            </Select>
            <FormHelperText>{TRIGGER_HELP[triggerType]}</FormHelperText>
          </FormControl>

          {renderTriggerConfig()}

          {!isDistance && (
            <>
              <Divider />
              <Typography variant="subtitle2">Acties</Typography>
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
                      <InputLabel>Actie</InputLabel>
                      <Select
                        label="Actie"
                        value={action.type}
                        onChange={(e) =>
                          updateAction(index, {
                            type: e.target.value as AutomationActionType,
                          })
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
                      title="Actie verwijderen"
                    >
                      <DeleteIcon />
                    </IconButton>
                  </Box>
                  {renderActionFields(action, index)}
                </Box>
              ))}
              <Button
                startIcon={<AddIcon />}
                onClick={() => setActions((prev) => [...prev, emptyAction()])}
                sx={{ alignSelf: 'flex-start' }}
              >
                Actie toevoegen
              </Button>

              <Divider />

              <Autocomplete
                multiple
                options={teams}
                getOptionLabel={(team) => team.name}
                value={teams.filter((team) => targetTeamIds.includes(team.id))}
                onChange={(_, value) => setTargetTeamIds(value.map((team) => team.id))}
                renderInput={(params) => (
                  <TextField {...params} label="Teams" placeholder="Alle teams" />
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
                  label="Eénmalig per gebeurtenis"
                />
                <FormHelperText sx={{ ml: 0 }}>
                  Punten die door een automatisering worden toegekend kunnen geen volgende
                  automatisering activeren (kettingreacties zijn uitgesloten).
                </FormHelperText>
                {!fireOnce && (
                  <TextField
                    type="number"
                    label="Cooldown (minuten)"
                    value={cooldownMinutes}
                    onChange={(e) =>
                      setCooldownMinutes(e.target.value === '' ? '' : Number(e.target.value))
                    }
                    sx={{ mt: 1, width: 200 }}
                  />
                )}
              </Box>
            </>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Annuleren</Button>
        <Button onClick={handleSubmit} variant="contained" disabled={loading || !name.trim()}>
          {loading ? <CircularProgress size={20} /> : 'Opslaan'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
