import { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Autocomplete,
  Checkbox,
  FormControlLabel,
  Box,
  Alert,
  CircularProgress,
  Typography,
  Chip,
  Tooltip,
  Switch,
  FormHelperText,
} from '@mui/material';
import {
  Send as SendIcon,
  Schedule as ScheduleIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';
import type { AdminTeam, MessageTemplate, MessageType, SendAdminMessageRequest } from '../types/entities';
import api from '../services/api';

interface SendMessageDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  gameId: string;
  teams: AdminTeam[];
  templates: MessageTemplate[];
  initialTemplate?: MessageTemplate | null;
}

const MESSAGE_TYPES: { value: MessageType; label: string; description: string }[] = [
  { value: 'general', label: 'Algemeen', description: 'Standaard bericht' },
  { value: 'countdown', label: 'Countdown', description: 'Aftellen/timing updates' },
  { value: 'checkpoint', label: 'Checkpoint', description: 'Checkpoint gerelateerd' },
  { value: 'rallydesk', label: 'Rallydesk', description: 'Kritiek bericht - SMS fallback actief' },
  { value: 'achievement', label: 'Achievement', description: 'Prestatie meldingen' },
  { value: 'rival', label: 'Rivaal', description: 'Rivaal team updates' },
];

const SMS_CHAR_LIMIT = 160;
const SMS_WARN_THRESHOLD = 140;

export default function SendMessageDialog({
  open,
  onClose,
  onSuccess,
  gameId,
  teams,
  templates,
  initialTemplate,
}: SendMessageDialogProps) {
  const [sendToAll, setSendToAll] = useState(true);
  const [selectedTeams, setSelectedTeams] = useState<AdminTeam[]>([]);
  const [messageType, setMessageType] = useState<MessageType>('general');
  const [content, setContent] = useState('');
  const [icon, setIcon] = useState('');
  const [selectedTemplate, setSelectedTemplate] = useState<MessageTemplate | null>(null);
  const [saveAsTemplate, setSaveAsTemplate] = useState(false);
  const [templateName, setTemplateName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [showRallydeskWarning, setShowRallydeskWarning] = useState(false);

  // Initialize from initialTemplate on mount (dialog is remounted via key)
  const [initialized, setInitialized] = useState(false);
  if (!initialized && initialTemplate) {
    setSelectedTemplate(initialTemplate);
    setMessageType(initialTemplate.messageType);
    setContent(initialTemplate.content);
    setIcon(initialTemplate.icon || '');
    setInitialized(true);
  }

  const handleSend = async () => {
    // Validate
    if (!content.trim()) {
      setError('Bericht inhoud is verplicht');
      return;
    }

    if (!sendToAll && selectedTeams.length === 0) {
      setError('Selecteer ten minste één team');
      return;
    }

    if (saveAsTemplate && !templateName.trim()) {
      setError('Template naam is verplicht als je wilt opslaan als template');
      return;
    }

    // Show warning for RALLYDESK messages
    if (messageType === 'rallydesk' && !showRallydeskWarning) {
      setShowRallydeskWarning(true);
      return;
    }

    setLoading(true);
    setError(null);

    const request: SendAdminMessageRequest = {
      gameId,
      teamIds: sendToAll ? undefined : selectedTeams.map((t) => t.id),
      messageType,
      content: content.trim(),
      icon: icon.trim() || undefined,
      saveAsTemplate,
      templateName: saveAsTemplate ? templateName.trim() : undefined,
    };

    const result = await api.sendAdminMessage(request);

    setLoading(false);

    if (result.error) {
      setError(result.error);
      return;
    }

    onSuccess();
    onClose();
  };

  const handleCancel = () => {
    if (showRallydeskWarning) {
      setShowRallydeskWarning(false);
      return;
    }
    onClose();
  };

  const isRallydesk = messageType === 'rallydesk';
  const charCount = content.length;
  const isOverLimit = isRallydesk && charCount > SMS_CHAR_LIMIT;
  const isNearLimit = isRallydesk && charCount > SMS_WARN_THRESHOLD && charCount <= SMS_CHAR_LIMIT;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {showRallydeskWarning ? 'Rallydesk bericht bevestigen' : 'Bericht versturen'}
      </DialogTitle>
      <DialogContent>
        {showRallydeskWarning ? (
          <Alert severity="warning" sx={{ mt: 1 }}>
            <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
              ⚠️ Let op: SMS fallback actief
            </Typography>
            <Typography variant="body2">
              Rallydesk berichten worden automatisch via SMS verstuurd naar teams die inactief zijn.
              Dit kan extra kosten met zich meebrengen.
            </Typography>
            <Typography variant="body2" sx={{ mt: 1 }}>
              Weet je zeker dat je dit bericht wilt versturen naar{' '}
              <strong>{sendToAll ? 'alle teams' : `${selectedTeams.length} geselecteerde teams`}</strong>?
            </Typography>
          </Alert>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {error && (
              <Alert severity="error" onClose={() => setError(null)}>
                {error}
              </Alert>
            )}

            {/* Template selector */}
            <Autocomplete
              options={templates}
              getOptionLabel={(option) => option.name}
              value={selectedTemplate}
              onChange={(_, value) => {
                setSelectedTemplate(value);
                if (value) {
                  setMessageType(value.messageType);
                  setContent(value.content);
                  setIcon(value.icon || '');
                }
              }}
              renderInput={(params) => (
                <TextField {...params} label="Template (optioneel)" placeholder="Selecteer een template..." />
              )}
              renderOption={(props, option) => (
                <li {...props} key={option.id}>
                  <Box>
                    <Typography variant="body1">{option.name}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {option.messageType} - {option.content.substring(0, 50)}...
                    </Typography>
                  </Box>
                </li>
              )}
            />

            {/* Target selection */}
            <Box>
              <FormControlLabel
                control={
                  <Switch
                    checked={sendToAll}
                    onChange={(e) => {
                      setSendToAll(e.target.checked);
                      if (e.target.checked) {
                        setSelectedTeams([]);
                      }
                    }}
                  />
                }
                label="Verstuur naar alle teams"
              />
            </Box>

            {!sendToAll && (
              <Autocomplete
                multiple
                options={teams}
                getOptionLabel={(option) => option.name}
                value={selectedTeams}
                onChange={(_, value) => setSelectedTeams(value)}
                filterOptions={(options, { inputValue }) => {
                  const filterValue = inputValue.toLowerCase();
                  return options.filter(
                    (option) =>
                      option.name.toLowerCase().includes(filterValue)
                  );
                }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Teams selecteren"
                    placeholder="Zoek op teamnaam..."
                  />
                )}
                renderValue={(value, getTagProps) =>
                  value.map((option, index) => {
                    const { key, ...tagProps } = getTagProps({ index });
                    return (
                      <Chip
                        key={key}
                        label={option.name}
                        size="small"
                        {...tagProps}
                      />
                    );
                  })
                }
                renderOption={(props, option, { selected }) => (
                  <li {...props} key={option.id}>
                    <Checkbox checked={selected} sx={{ mr: 1 }} />
                    <Box>
                      <Typography variant="body2">
                        {option.name}
                      </Typography>
                    </Box>
                  </li>
                )}
              />
            )}

            {/* Message type */}
            <FormControl fullWidth>
              <InputLabel>Berichttype</InputLabel>
              <Select
                value={messageType}
                label="Berichttype"
                onChange={(e) => setMessageType(e.target.value as MessageType)}
              >
                {MESSAGE_TYPES.map((type) => (
                  <MenuItem key={type.value} value={type.value}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      {type.label}
                      {type.value === 'rallydesk' && (
                        <Chip label="SMS" size="small" color="warning" />
                      )}
                    </Box>
                  </MenuItem>
                ))}
              </Select>
              <FormHelperText>
                {MESSAGE_TYPES.find((t) => t.value === messageType)?.description}
              </FormHelperText>
            </FormControl>

            {/* Content */}
            <TextField
              label="Bericht"
              multiline
              rows={4}
              value={content}
              onChange={(e) => setContent(e.target.value)}
              fullWidth
              error={isOverLimit}
              helperText={
                isRallydesk ? (
                  <Box
                    component="span"
                    sx={{
                      color: isOverLimit ? 'error.main' : isNearLimit ? 'warning.main' : 'text.secondary',
                    }}
                  >
                    {charCount}/{SMS_CHAR_LIMIT} tekens
                    {isOverLimit && ' - Bericht kan worden afgekapt bij SMS verzending'}
                    {isNearLimit && ' - Bijna bij SMS limiet'}
                  </Box>
                ) : undefined
              }
            />
            <Typography variant="caption" color="text.secondary">
              {'{team}'} wordt vervangen door de naam van het ontvangende team.
            </Typography>

            {/* Icon */}
            <TextField
              label="Icoon (optioneel)"
              value={icon}
              onChange={(e) => setIcon(e.target.value)}
              fullWidth
              placeholder="Bijv: 📢, 🏁, ⚠️"
              helperText="Emoji of tekst icoon voor het bericht"
            />

            {/* Save as template */}
            <Box>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={saveAsTemplate}
                    onChange={(e) => setSaveAsTemplate(e.target.checked)}
                  />
                }
                label="Opslaan als template"
              />
              {saveAsTemplate && (
                <TextField
                  label="Template naam"
                  value={templateName}
                  onChange={(e) => setTemplateName(e.target.value)}
                  fullWidth
                  size="small"
                  sx={{ mt: 1 }}
                />
              )}
            </Box>
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleCancel}>
          {showRallydeskWarning ? 'Terug' : 'Annuleren'}
        </Button>
        <Tooltip title="Binnenkort beschikbaar" arrow>
          <span>
            <Button disabled startIcon={<ScheduleIcon />}>
              Inplannen
            </Button>
          </span>
        </Tooltip>
        <Button
          variant="contained"
          onClick={handleSend}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={20} /> : showRallydeskWarning ? <WarningIcon /> : <SendIcon />}
          color={showRallydeskWarning ? 'warning' : 'primary'}
        >
          {loading ? 'Verzenden...' : showRallydeskWarning ? 'Bevestigen en versturen' : 'Versturen'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
