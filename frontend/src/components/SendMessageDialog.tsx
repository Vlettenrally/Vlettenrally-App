import { useState } from 'react';
import { useTranslation } from 'react-i18next';
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
  Switch,
  FormHelperText,
} from '@mui/material';
import {
  Send as SendIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';
import type { AdminTeam, MessageTemplate, MessageType, SendAdminMessageRequest } from '../types/entities';
import api from '../services/api';
import { teamMatchesQuery } from '../utils/teamSearch';

interface SendMessageDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  gameId: string;
  teams: AdminTeam[];
  templates: MessageTemplate[];
  initialTemplate?: MessageTemplate | null;
}


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
  const { t } = useTranslation();

  const MESSAGE_TYPES: { value: MessageType; label: string; description: string }[] = [
    { value: 'general', label: t('components.sendMessage.typeGeneral'), description: t('components.sendMessage.typeGeneral') },
    { value: 'countdown', label: t('components.sendMessage.typeCountdown'), description: t('components.sendMessage.typeCountdown') },
    { value: 'checkpoint', label: t('components.sendMessage.typeCheckpoint'), description: t('components.sendMessage.typeCheckpoint') },
    { value: 'rallydesk', label: t('components.sendMessage.typeRallydesk'), description: t('components.sendMessage.typeRallydesk') },
    { value: 'achievement', label: t('components.sendMessage.typeAchievement'), description: t('components.sendMessage.typeAchievement') },
    { value: 'rival', label: t('components.sendMessage.typeRival'), description: t('components.sendMessage.typeRival') },
    { value: 'speed_warning', label: t('components.sendMessage.typeSpeedWarning'), description: t('components.sendMessage.typeSpeedWarningDescription') },
  ];

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
      setError(t('components.sendMessage.messageRequired'));
      return;
    }

    if (!sendToAll && selectedTeams.length === 0) {
      setError(t('components.sendMessage.selectTeamRequired'));
      return;
    }

    if (saveAsTemplate && !templateName.trim()) {
      setError(t('components.sendMessage.templateNameRequired'));
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
        {showRallydeskWarning ? t('components.sendMessage.confirmTitle') : t('components.sendMessage.sendTitle')}
      </DialogTitle>
      <DialogContent>
        {showRallydeskWarning ? (
          <Alert severity="warning" sx={{ mt: 1 }}>
            <Typography variant="subtitle1" fontWeight="bold" gutterBottom>
              {t('components.sendMessage.smsWarning')}
            </Typography>
            <Typography variant="body2">
              {t('components.sendMessage.smsWarningText')}
            </Typography>
            <Typography variant="body2" sx={{ mt: 1 }}>
              {t('components.sendMessage.confirmMessage', {
                recipients: sendToAll ? t('components.sendMessage.allTeams') : t('components.sendMessage.selectedTeams', { count: selectedTeams.length }),
              })}
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
                <TextField {...params} label={t('components.sendMessage.template')} placeholder={t('components.sendMessage.selectTemplate')} />
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
                label={t('components.sendMessage.sendToAll')}
              />
            </Box>

            {!sendToAll && (
              <Autocomplete
                multiple
                options={teams}
                getOptionLabel={(option) => option.name}
                value={selectedTeams}
                onChange={(_, value) => setSelectedTeams(value)}
                filterOptions={(options, { inputValue }) =>
                  options.filter((option) => teamMatchesQuery(option, inputValue))
                }
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label={t('components.sendMessage.selectTeams')}
                    placeholder={t('components.sendMessage.searchTeam')}
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
                      {option.description && (
                        <Typography variant="caption" color="text.secondary" display="block">
                          {option.description}
                        </Typography>
                      )}
                    </Box>
                  </li>
                )}
              />
            )}

            {/* Message type */}
            <FormControl fullWidth>
              <InputLabel>{t('components.sendMessage.messageType')}</InputLabel>
              <Select
                value={messageType}
                label={t('components.sendMessage.messageType')}
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
              label={t('components.sendMessage.message')}
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
                    {t('components.sendMessage.charCount', { count: charCount, max: SMS_CHAR_LIMIT })}
                    {isOverLimit && ' - ' + t('components.sendMessage.smsTruncation')}
                    {isNearLimit && ' - ' + t('components.sendMessage.smsLimitWarning')}
                  </Box>
                ) : undefined
              }
            />
            <Typography variant="caption" color="text.secondary">
              {t('components.sendMessage.teamPlaceholderHint')}
            </Typography>

            {/* Icon */}
            <TextField
              label={t('components.sendMessage.icon')}
              value={icon}
              onChange={(e) => setIcon(e.target.value)}
              fullWidth
              placeholder={t('components.sendMessage.iconPlaceholder')}
              helperText={t('components.sendMessage.iconHint')}
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
                label={t('components.sendMessage.saveAsTemplate')}
              />
              {saveAsTemplate && (
                <TextField
                  label={t('components.sendMessage.templateName')}
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
          {showRallydeskWarning ? t('common.back') : t('common.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={handleSend}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={20} /> : showRallydeskWarning ? <WarningIcon /> : <SendIcon />}
          color={showRallydeskWarning ? 'warning' : 'primary'}
        >
          {loading ? t('components.sendMessage.sending') : showRallydeskWarning ? t('components.sendMessage.confirmAndSend') : t('components.sendMessage.send')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
