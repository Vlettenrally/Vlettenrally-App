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
import { gameService } from '../services/gameService';
import type { Game, GameRequest, ExternalEvent, LocationTestResult } from '../types/entities';

interface GameFormDialogProps {
  open: boolean;
  onClose: () => void;
  game: Game | null;
  onSave?: () => void;
}

const POLLING_FREQUENCY_OPTIONS = [
  { value: 15, label: '15 seconds' },
  { value: 30, label: '30 seconds' },
  { value: 60, label: '1 minute' },
  { value: 300, label: '5 minutes' },
];

export default function GameFormDialog({
  open,
  onClose,
  game,
  onSave,
}: GameFormDialogProps) {
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
    startTime: '',
    endTime: '',
    checkInStartTime: '',
    startSignalTime: '',
    status: 'planned',
    externalEventId: undefined,
    externalEventName: '',
    locationEndpointUrl: '',
    locationPollingFrequency: 15,
    locationEndpointActive: false,
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
        startTime: game.startTime ? formatDateTimeLocal(game.startTime) : '',
        endTime: game.endTime ? formatDateTimeLocal(game.endTime) : '',
        checkInStartTime: game.checkInStartTime ? formatDateTimeLocal(game.checkInStartTime) : '',
        startSignalTime: game.startSignalTime ? formatDateTimeLocal(game.startSignalTime) : '',
        status: game.status || 'planned',
        externalEventId: game.externalEventId,
        externalEventName: game.externalEventName || '',
        locationEndpointUrl: game.locationEndpointUrl || '',
        locationPollingFrequency: game.locationPollingFrequency || 15,
        locationEndpointActive: game.locationEndpointActive || false,
      });
    } else {
      setFormData({
        name: '',
        description: '',
        startTime: '',
        endTime: '',
        checkInStartTime: '',
        startSignalTime: '',
        status: 'planned',
        externalEventId: undefined,
        externalEventName: '',
        locationEndpointUrl: '',
        locationPollingFrequency: 15,
        locationEndpointActive: false,
      });
    }
    setError(null);
    setLocationTestResult(null);
  }, [game, open]);

  const formatDateTimeLocal = (date: Date | string): string => {
    try {
      if (typeof date === 'string') {
        return new Date(date).toISOString().slice(0, 16);
      }
      return date.toISOString().slice(0, 16);
    } catch {
      return '';
    }
  };

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
      setError('Game name is required');
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

      if (isCreateMode) {
        await gameService.createGame(requestData);
      } else if (game) {
        await gameService.updateGame(game.id, requestData);
      }

      onSave?.();
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
        {isCreateMode ? 'Create New Game' : 'Edit Game'}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mt: 1 }}>
          {/* Basic Info Section */}
          <Typography variant="h6" color="primary">Basic Information</Typography>
          
          <TextField
            label="Game Name"
            value={formData.name}
            onChange={handleChange('name')}
            required
            fullWidth
          />

          <TextField
            label="Description"
            value={formData.description}
            onChange={handleChange('description')}
            multiline
            rows={2}
            fullWidth
          />

          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <TextField
              label="Start Time"
              type="datetime-local"
              value={formData.startTime}
              onChange={handleChange('startTime')}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            <TextField
              label="End Time"
              type="datetime-local"
              value={formData.endTime}
              onChange={handleChange('endTime')}
              InputLabelProps={{ shrink: true }}
              fullWidth
            />
            <TextField
              label="Check-in Start Time"
              type="datetime-local"
              value={formData.checkInStartTime}
              onChange={handleChange('checkInStartTime')}
              InputLabelProps={{ shrink: true }}
              helperText="When the check-in area opens"
              fullWidth
            />
            <TextField
              label="Start Signal Time"
              type="datetime-local"
              value={formData.startSignalTime}
              onChange={handleChange('startSignalTime')}
              InputLabelProps={{ shrink: true }}
              helperText="When the race starts (auto-activates game)"
              fullWidth
            />
          </Box>

          <FormControl fullWidth>
            <InputLabel>Status</InputLabel>
            <Select
              value={formData.status}
              label="Status"
              onChange={(e) => handleChange('status')({ target: { value: e.target.value } })}
            >
              <MenuItem value="planned">Planned</MenuItem>
              <MenuItem value="active">Active</MenuItem>
              <MenuItem value="completed">Completed</MenuItem>
            </Select>
          </FormControl>

          <Divider />

          {/* External Event Section */}
          <Typography variant="h6" color="primary">Team Import Configuration</Typography>
          
          <FormControl fullWidth>
            <InputLabel>External Event</InputLabel>
            <Select
              value={formData.externalEventId || ''}
              label="External Event"
              onChange={(e) => handleExternalEventChange(e.target.value as number)}
              disabled={loadingEvents}
            >
              <MenuItem value="">
                <em>None</em>
              </MenuItem>
              {externalEvents.map((event) => (
                <MenuItem key={event.eventId} value={event.eventId}>
                  {event.eventName} ({event.bookingCount} registrations)
                </MenuItem>
              ))}
            </Select>
            {loadingEvents && <CircularProgress size={20} sx={{ position: 'absolute', right: 40, top: 16 }} />}
          </FormControl>

          <Divider />

          {/* Location Tracking Section */}
          <Typography variant="h6" color="primary">Location Tracking</Typography>
          
          <TextField
            label="Location Endpoint URL"
            value={formData.locationEndpointUrl}
            onChange={handleChange('locationEndpointUrl')}
            placeholder="https://api.example.com/locations"
            fullWidth
          />

          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 2 }}>
            <FormControl fullWidth>
              <InputLabel>Polling Frequency</InputLabel>
              <Select
                value={formData.locationPollingFrequency}
                label="Polling Frequency"
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
              label="Location Tracking Active"
              sx={{ ml: 1 }}
            />
          </Box>

          {!isCreateMode && formData.locationEndpointUrl && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
              <Button
                variant="outlined"
                startIcon={testingLocation ? <CircularProgress size={20} /> : <TestIcon />}
                onClick={handleTestLocation}
                disabled={testingLocation}
              >
                {testingLocation ? 'Testing...' : 'Test Connection'}
              </Button>
              {locationTestResult && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  {locationTestResult.success ? (
                    <>
                      <SuccessIcon color="success" />
                      <Typography color="success.main">
                        Connected - {locationTestResult.participantCount} participants found
                      </Typography>
                    </>
                  ) : (
                    <>
                      <ErrorIcon color="error" />
                      <Typography color="error.main">
                        {locationTestResult.errorMessage || 'Connection failed'}
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
        <Button onClick={onClose}>Cancel</Button>
        <Button
          onClick={handleSave}
          variant="contained"
          disabled={loading}
        >
          {loading ? <CircularProgress size={20} /> : isCreateMode ? 'Create' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
