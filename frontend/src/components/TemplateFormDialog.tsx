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
  Box,
  Alert,
  CircularProgress,
  FormHelperText,
  Chip,
} from '@mui/material';
import type { MessageTemplate, MessageType, MessageTemplateRequest } from '../types/entities';
import api from '../services/api';

interface TemplateFormDialogProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  template?: MessageTemplate | null; // null for create, template for edit
}

const SMS_CHAR_LIMIT = 160;
const SMS_WARN_THRESHOLD = 140;

export default function TemplateFormDialog({
  open,
  onClose,
  onSuccess,
  template,
}: TemplateFormDialogProps) {
  const { t } = useTranslation();

  const MESSAGE_TYPES: { value: MessageType; label: string; description: string }[] = [
    { value: 'general', label: t('components.sendMessage.typeGeneral'), description: t('components.templateForm.typeGeneralDesc') },
    { value: 'countdown', label: t('components.sendMessage.typeCountdown'), description: t('components.templateForm.typeCountdownDesc') },
    { value: 'checkpoint', label: t('components.sendMessage.typeCheckpoint'), description: t('components.templateForm.typeCheckpointDesc') },
    { value: 'rallydesk', label: t('components.sendMessage.typeRallydesk'), description: t('components.templateForm.typeRallydeskDesc') },
    { value: 'achievement', label: t('components.sendMessage.typeAchievement'), description: t('components.templateForm.typeAchievementDesc') },
    { value: 'rival', label: t('components.sendMessage.typeRival'), description: t('components.templateForm.typeRivalDesc') },
  ];

  const [name, setName] = useState('');
  const [messageType, setMessageType] = useState<MessageType>('general');
  const [content, setContent] = useState('');
  const [icon, setIcon] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isEditing = !!template;

  // Initialize from template on mount (dialog is remounted via key)
  const [initialized, setInitialized] = useState(false);
  if (!initialized && template) {
    setName(template.name);
    setMessageType(template.messageType);
    setContent(template.content);
    setIcon(template.icon || '');
    setInitialized(true);
  }

  const handleSave = async () => {
    // Validate
    if (!name.trim()) {
      setError(t('components.templateForm.nameRequired'));
      return;
    }

    if (!content.trim()) {
      setError(t('components.templateForm.contentRequired'));
      return;
    }

    setLoading(true);
    setError(null);

    const request: MessageTemplateRequest = {
      name: name.trim(),
      messageType,
      content: content.trim(),
      icon: icon.trim() || undefined,
    };

    const result = isEditing
      ? await api.updateMessageTemplate(template!.id, request)
      : await api.createMessageTemplate(request);

    setLoading(false);

    if (result.error) {
      setError(result.error);
      return;
    }

    onSuccess();
    onClose();
  };

  const isRallydesk = messageType === 'rallydesk';
  const charCount = content.length;
  const isOverLimit = isRallydesk && charCount > SMS_CHAR_LIMIT;
  const isNearLimit = isRallydesk && charCount > SMS_WARN_THRESHOLD && charCount <= SMS_CHAR_LIMIT;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{isEditing ? t('components.templateForm.editTitle') : t('components.templateForm.createTitle')}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {error && (
            <Alert severity="error" onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          {/* Template name */}
          <TextField
            label={t('components.templateForm.name')}
            value={name}
            onChange={(e) => setName(e.target.value)}
            fullWidth
            required
          />

          {/* Message type */}
          <FormControl fullWidth>
            <InputLabel>{t('components.templateForm.type')}</InputLabel>
            <Select
              value={messageType}
              label={t('components.templateForm.type')}
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
            label={t('components.templateForm.message')}
            multiline
            rows={4}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            fullWidth
            required
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
              ) : (
                t('components.templateForm.contentHint')
              )
            }
          />

          {/* Icon */}
          <TextField
            label={t('components.templateForm.icon')}
            value={icon}
            onChange={(e) => setIcon(e.target.value)}
            fullWidth
            placeholder={t('components.templateForm.iconPlaceholder')}
            helperText={t('components.templateForm.iconHint')}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={loading}
        >
          {loading ? <CircularProgress size={24} /> : isEditing ? t('common.save') : t('common.create')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
