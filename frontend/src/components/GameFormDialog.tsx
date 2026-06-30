import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Typography,
  FormControlLabel,
  Switch,
  Select,
  MenuItem,
  FormControl,
  FormHelperText,
  InputLabel,
  CircularProgress,
  Alert,
  Divider,
} from '@mui/material';
import {
  NetworkCheck as TestIcon,
  CheckCircle as SuccessIcon,
  Error as ErrorIcon,
} from '@mui/icons-material';
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { gameService } from '../services/gameService';
import { formatForDateTimeLocalInput } from '../utils/dateTime';
import type { Game, GameRequest, ExternalEvent, LocationTestResult } from '../types/entities';

interface GameFormDialogProps {
  open: boolean;
  onClose: () => void;
  game: Game | null;
  onSave?: (game: Game) => void;
}

export default function GameFormDialog({
  open,
  onClose,
  game,
  onSave,
}: GameFormDialogProps) {
  const { t } = useTranslation();

  const POLLING_FREQUENCY_OPTIONS = [
    { value: 15, label: t('components.gameForm.polling15s', { defaultValue: '15 seconds' }) },
    { value: 30, label: t('components.gameForm.polling30s', { defaultValue: '30 seconds' }) },
    { value: 60, label: t('components.gameForm.polling1m', { defaultValue: '1 minute' }) },
    { value: 300, label: t('components.gameForm.polling5m', { defaultValue: '5 minutes' }) },
  ];

  const POLLING_LEAD_OPTIONS = [
    { value: 0, label: t('components.gameForm.pollingLeadOff', { defaultValue: 'Off' }) },
    { value: 15, label: t('components.gameForm.pollingLead15m', { defaultValue: '15 minutes' }) },
    { value: 30, label: t('components.gameForm.pollingLead30m', { defaultValue: '30 minutes' }) },
    { value: 60, label: t('components.gameForm.pollingLead1h', { defaultValue: '1 hour' }) },
  ];

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [externalEvents, setExternalEvents] = useState<ExternalEvent[]>([]);
  const [loadingEvents, setLoadingEvents] = useState(false);
  
  // Location test state
  const [testingLocation, setTestingLocation] = useState(false);
  const [locationTestResult, setLocationTestResult] = useState<LocationTestResult | null>(null);
  
  const [formData, setFormData] = useState<GameRequest>({
    name: '',
    description: '',
    participantInfo: '',
    startTime: '',
    endTime: '',
    checkInStartTime: '',
    startSignalTime: '',
    status: 'planned',
    externalEventId: undefined,
    externalEventName: '',
    locationEndpointUrl: '',
    locationPollingFrequency: 30,
    locationEndpointActive: false,
    locationPollingLeadMinutes: 30,
  });

  const isCreateMode = !game;

  // Load external events when dialog opens
  useEffect(() => {
    if (open) {
      setLoadingEvents(true);
      gameService.getExternalEvents()
        .then(setExternalEvents)
        .catch((err) => console.error('Failed to load external events:', err))
        .finally(() => setLoadingEvents(false));
    }
  }, [open]);

  // Populate form when editing
  useEffect(() => {
    if (game) {
      setFormData({
        name: game.name,
        description: game.description || '',
        participantInfo: game.participantInfo || '',
        startTime: game.startTime ? formatForDateTimeLocalInput(game.startTime) : '',
        endTime: game.endTime ? formatForDateTimeLocalInput(game.endTime) : '',
        checkInStartTime: game.checkInStartTime ? formatForDateTimeLocalInput(game.checkInStartTime) : '',
        startSignalTime: game.startSignalTime ? formatForDateTimeLocalInput(game.startSignalTime) : '',
        status: game.status || 'planned',
        externalEventId: game.externalEventId,
        externalEventName: game.externalEventName || '',
        locationEndpointUrl: game.locationEndpointUrl || '',
        locationPollingFrequency: game.locationPollingFrequency || 30,
        locationEndpointActive: game.locationEndpointActive || false,
        locationPollingLeadMinutes: game.locationPollingLeadMinutes ?? 30,
      });
    } else {
      setFormData({
        name: '',
        description: '',
        participantInfo: '',
        startTime: '',
        endTime: '',
        checkInStartTime: '',
        startSignalTime: '',
        status: 'planned',
        externalEventId: undefined,
        externalEventName: '',
        locationEndpointUrl: '',
        locationPollingFrequency: 30,
        locationEndpointActive: false,
        locationPollingLeadMinutes: 30,
      });
    }
    setError(null);
    setLocationTestResult(null);
  }, [game, open]);

  const formatToISOString = (localDateTime: string): string | undefined => {
    if (!localDateTime) return undefined;
    try {
      return new Date(localDateTime).toISOString();
    } catch {
      return undefined;
    }
  };

  const handleSave = async () => {
    if (!formData.name.trim()) {
      setError(t('components.gameForm.gameNameRequired'));
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const requestData: GameRequest = {
        ...formData,
        startTime: formatToISOString(formData.startTime || ''),
        endTime: formatToISOString(formData.endTime || ''),
        checkInStartTime: formatToISOString(formData.checkInStartTime || ''),
        startSignalTime: formatToISOString(formData.startSignalTime || ''),
      };

      let savedGame: Game;
      if (isCreateMode) {
        savedGame = await gameService.createGame(requestData);
      } else if (game) {
        savedGame = await gameService.updateGame(game.id, requestData);
      } else {
        throw new Error('Invalid state: no game to update');
      }

      onSave?.(savedGame);
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save game');
    } finally {
      setLoading(false);
    }
  };

  const handleTestLocation = async () => {
    if (!game) return;
    
    setTestingLocation(true);
    setLocationTestResult(null);
    setError(null);

    try {
      const result = await gameService.testLocationEndpoint(game.id);
      setLocationTestResult(result);
    } catch (err) {
      setLocationTestResult({
        success: false,
        participantCount: 0,
        errorMessage: err instanceof Error ? err.message : 'Connection test failed',
      });
    } finally {
      setTestingLocation(false);
    }
  };

  const handleExternalEventChange = (eventId: number) => {
    const event = externalEvents.find(e => e.eventId === eventId);
    setFormData(prev => ({
      ...prev,
      externalEventId: eventId,
      externalEventName: event?.eventName || '',
    }));
  };

  const handleChange = (field: keyof GameRequest) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement> | { target: { value: unknown } }
  ) => {
    const value = event.target.value;
    setFormData((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {isCreateMode ? t('components.gameForm.createTitle') : t('components.gameForm.editTitle')}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 1 }}>
          {/* Basic Info Section */}
          <Typography variant="h6" color="primary">{t('components.gameForm.basicInfo')}</Typography>

          <TextField
            label={t('components.gameForm.gameName')}
            value={formData.name}
            onChange={handleChange('name')}
            required
            fullWidth
          />

          <TextField
            label={t('components.gameForm.description')}
            value={formData.description}
            onChange={handleChange('description')}
            multiline
            rows={2}
            fullWidth
          />

          <TextField
            label={t('components.gameForm.participantInfo')}
            value={formData.participantInfo}
            onChange={handleChange('participantInfo')}
            multiline
            rows={6}
            fullWidth
            helperText={t('components.gameForm.participantInfoHint')}
          />

          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField
              label={t('components.gameForm.startTime')}
              type="datetime-local"
              value={formData.startTime}
              onChange={handleChange('startTime')}
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
            <TextField
              label={t('components.gameForm.endTime')}
              type="datetime-local"
              value={formData.endTime}
              onChange={handleChange('endTime')}
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
            <TextField
              label={t('components.gameForm.checkInStart')}
              type="datetime-local"
              value={formData.checkInStartTime}
              onChange={handleChange('checkInStartTime')}
              slotProps={{ inputLabel: { shrink: true } }}
              helperText={t('components.gameForm.checkInStartHint')}
              fullWidth
            />
            <TextField
              label={t('components.gameForm.startSignal')}
              type="datetime-local"
              value={formData.startSignalTime}
              onChange={handleChange('startSignalTime')}
              slotProps={{ inputLabel: { shrink: true } }}
              helperText={t('components.gameForm.startSignalHint')}
              fullWidth
            />
          </Box>

          <FormControl fullWidth>
            <InputLabel>{t('components.gameForm.status')}</InputLabel>
            <Select
              value={formData.status}
              label={t('components.gameForm.status')}
              onChange={(e) => handleChange('status')({ target: { value: e.target.value } })}
            >
              <MenuItem value="planned">{t('components.gameForm.statusPlanned')}</MenuItem>
              <MenuItem value="active">{t('components.gameForm.statusActive')}</MenuItem>
              <MenuItem value="completed">{t('components.gameForm.statusCompleted')}</MenuItem>
            </Select>
          </FormControl>

          <Divider />

          {/* External Event Section */}
          <Typography variant="h6" color="primary">{t('components.gameForm.teamImport')}</Typography>

          <FormControl fullWidth>
            <InputLabel>{t('components.gameForm.externalEvent')}</InputLabel>
            <Select
              value={formData.externalEventId || ''}
              label={t('components.gameForm.externalEvent')}
              onChange={(e) => handleExternalEventChange(e.target.value as number)}
              disabled={loadingEvents}
            >
              <MenuItem value="">
                <em>{t('components.gameForm.none')}</em>
              </MenuItem>
              {externalEvents.map((event) => (
                <MenuItem key={event.eventId} value={event.eventId}>
                  {event.eventName} ({t('components.gameForm.registrations', { count: event.bookingCount })})
                </MenuItem>
              ))}
            </Select>
            {loadingEvents && <CircularProgress size={20} sx={{ position: 'absolute', right: 40, top: 16 }} />}
          </FormControl>

          <Divider />

          {/* Location Tracking Section */}
          <Typography variant="h6" color="primary">{t('components.gameForm.locationTracking')}</Typography>

          <TextField
            label={t('components.gameForm.trackerBaseUrl')}
            value={formData.locationEndpointUrl}
            onChange={handleChange('locationEndpointUrl')}
            placeholder="https://host/api/event/3265"
            helperText={t('components.gameForm.trackerBaseUrlHint')}
            fullWidth
          />

          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <FormControl fullWidth>
              <InputLabel>{t('components.gameForm.pollingFrequency')}</InputLabel>
              <Select
                value={formData.locationPollingFrequency}
                label={t('components.gameForm.pollingFrequency')}
                onChange={(e) => handleChange('locationPollingFrequency')({ target: { value: e.target.value } })}
              >
                {POLLING_FREQUENCY_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <FormControlLabel
              control={
                <Switch
                  checked={formData.locationEndpointActive || false}
                  onChange={(e) => setFormData(prev => ({ ...prev, locationEndpointActive: e.target.checked }))}
                />
              }
              label={t('components.gameForm.trackingActive')}
              sx={{ ml: 1 }}
            />
          </Box>

          <FormControl fullWidth>
            <InputLabel>{t('components.gameForm.pollingLead')}</InputLabel>
            <Select
              value={formData.locationPollingLeadMinutes ?? 30}
              label={t('components.gameForm.pollingLead')}
              onChange={(e) => handleChange('locationPollingLeadMinutes')({ target: { value: e.target.value } })}
            >
              {POLLING_LEAD_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
            <FormHelperText>
              {t('components.gameForm.pollingLeadHint', {
                defaultValue: 'Start tracking this long before the start signal, so teams already in a zone are detected at kickoff.',
              })}
            </FormHelperText>
          </FormControl>

          {!isCreateMode && formData.locationEndpointUrl && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Button
                variant="outlined"
                startIcon={testingLocation ? <CircularProgress size={20} /> : <TestIcon />}
                onClick={handleTestLocation}
                disabled={testingLocation}
              >
                {testingLocation ? t('components.gameForm.testing') : t('components.gameForm.testConnection')}
              </Button>
              {locationTestResult && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  {locationTestResult.success ? (
                    <>
                      <SuccessIcon color="success" />
                      <Typography color="success.main">
                        {t('components.gameForm.connected', { count: locationTestResult.participantCount })}
                      </Typography>
                    </>
                  ) : (
                    <>
                      <ErrorIcon color="error" />
                      <Typography color="error.main">
                        {locationTestResult.errorMessage || t('components.gameForm.connectionFailed')}
                      </Typography>
                    </>
                  )}
                </Box>
              )}
            </Box>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t('common.cancel')}</Button>
        <Button
          onClick={handleSave}
          variant="contained"
          disabled={loading}
        >
          {loading ? <CircularProgress size={20} /> : isCreateMode ? t('common.create') : t('common.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
