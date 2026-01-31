import { useState, useEffect } from 'react';
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

export default function TemplateFormDialog({
  open,
  onClose,
  onSuccess,
  template,
}: TemplateFormDialogProps) {
  const [name, setName] = useState('');
  const [messageType, setMessageType] = useState<MessageType>('general');
  const [content, setContent] = useState('');
  const [icon, setIcon] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isEditing = !!template;

  // Reset/populate form when dialog opens or template changes
  useEffect(() => {
    if (open) {
      if (template) {
        setName(template.name);
        setMessageType(template.messageType);
        setContent(template.content);
        setIcon(template.icon || '');
      } else {
        setName('');
        setMessageType('general');
        setContent('');
        setIcon('');
      }
      setError(null);
    }
  }, [open, template]);

  const handleSave = async () => {
    // Validate
    if (!name.trim()) {
      setError('Template naam is verplicht');
      return;
    }

    if (!content.trim()) {
      setError('Bericht inhoud is verplicht');
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
      <DialogTitle>{isEditing ? 'Template bewerken' : 'Nieuwe template'}</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          {error && (
            <Alert severity="error" onClose={() => setError(null)}>
              {error}
            </Alert>
          )}

          {/* Template name */}
          <TextField
            label="Template naam"
            value={name}
            onChange={(e) => setName(e.target.value)}
            fullWidth
            required
          />

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
                  {charCount}/{SMS_CHAR_LIMIT} tekens
                  {isOverLimit && ' - Bericht kan worden afgekapt bij SMS verzending'}
                  {isNearLimit && ' - Bijna bij SMS limiet'}
                </Box>
              ) : (
                'Tip: Gebruik [X] als placeholder voor variabele tekst'
              )
            }
          />

          {/* Icon */}
          <TextField
            label="Icoon (optioneel)"
            value={icon}
            onChange={(e) => setIcon(e.target.value)}
            fullWidth
            placeholder="Bijv: 📢, 🏁, ⚠️"
            helperText="Emoji of tekst icoon voor het bericht"
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Annuleren</Button>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={loading}
        >
          {loading ? <CircularProgress size={24} /> : isEditing ? 'Opslaan' : 'Aanmaken'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
