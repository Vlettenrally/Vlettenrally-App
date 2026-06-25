import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Typography,
  Chip,
  CircularProgress,
  Alert,
  FormControlLabel,
  Checkbox,
  Stack,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import { useCallback, useState, useEffect } from 'react';
import type { AdminTeam, CheckInDevice } from '../types/entities';
import { teamService } from '../services/teamService';
import { gameService } from '../services/gameService';

interface TeamDetailDialogProps {
  open: boolean;
  onClose: () => void;
  team: AdminTeam | null;
  gameId: string;
  onSave?: () => void;
}

export default function TeamDetailDialog({
  open,
  onClose,
  team,
  gameId,
  onSave,
}: TeamDetailDialogProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rivalTeams, setRivalTeams] = useState<AdminTeam[]>([]);
  const [loadingRivals, setLoadingRivals] = useState(false);
  const [devices, setDevices] = useState<CheckInDevice[]>([]);
  const [loadingDevices, setLoadingDevices] = useState(false);
  const [deviceActionPending, setDeviceActionPending] = useState(false);
  const [confirmReset, setConfirmReset] = useState(false);
  const [deviceError, setDeviceError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [availableTrackers, setAvailableTrackers] = useState<string[]>([]);

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    participants: '',
    emergencyPhoneNumbers: '',
    notes: '',
    checkedIn: false,
    checkedInDigitally: false,
    traccarEnabled: false,
    finished: false,
    totalPoints: 0,
    trackerId: '',
  });

  const isCreateMode = !team;

  useEffect(() => {
    if (team) {
      setFormData({
        name: team.name,
        description: team.description,
        participants: team.participants ? team.participants.join('\n') : '',
        emergencyPhoneNumbers: team.emergencyPhoneNumbers ? team.emergencyPhoneNumbers.join('\n') : '',
        notes: team.notes || '',
        checkedIn: team.checkedIn,
        checkedInDigitally: team.checkedInDigitally,
        traccarEnabled: team.traccarEnabled,
        finished: team.finished,
        totalPoints: team.totalPoints,
        trackerId: team.trackerId ?? '',
      });
      setIsEditing(false);

      // Load the pool of unassigned trackers for the picker.
      gameService
        .getAvailableTrackers(team.gameId)
        .then(setAvailableTrackers)
        .catch((err) => {
          console.error('Failed to load available trackers:', err);
          setAvailableTrackers([]);
        });

      // Load rival teams
      if (team.rivals && team.rivals.length > 0) {
        setLoadingRivals(true);
        Promise.all(
          team.rivals.map((rivalId: string) => 
            teamService.getTeam(rivalId, team.gameId)
          )
        )
          .then((rivals) => {
            setRivalTeams(rivals.filter((r: AdminTeam | null): r is AdminTeam => r !== null));
          })
          .catch((err) => {
            console.error('Failed to load rival teams:', err);
          })
          .finally(() => {
            setLoadingRivals(false);
          });
      } else {
        setRivalTeams([]);
      }

      setLoadingDevices(true);
      setDeviceError(null);
      teamService
        .listDevices(team.id)
        .then((list) => setDevices(list))
        .catch((err) => {
          console.error('Failed to load devices:', err);
          setDeviceError('Apparaten ophalen mislukt.');
          setDevices([]);
        })
        .finally(() => setLoadingDevices(false));
    } else {
      setFormData({
        name: '',
        description: '',
        participants: '',
        emergencyPhoneNumbers: '',
        notes: '',
        checkedIn: false,
        checkedInDigitally: false,
        traccarEnabled: false,
        finished: false,
        totalPoints: 0,
        trackerId: '',
      });
      setIsEditing(true);
      setRivalTeams([]);
      setAvailableTrackers([]);
      setDevices([]);
      setConfirmReset(false);
      setDeviceError(null);
    }
    setConfirmDelete(false);
    setError(null);
  }, [team, gameId]);

  const refreshDevices = useCallback(async () => {
    if (!team) return;
    setLoadingDevices(true);
    setDeviceError(null);
    try {
      const list = await teamService.listDevices(team.id);
      setDevices(list);
    } catch (err) {
      console.error('Failed to refresh devices:', err);
      setDeviceError('Apparaten verversen mislukt.');
    } finally {
      setLoadingDevices(false);
    }
  }, [team]);

  const handleMakeMain = async (deviceId: string) => {
    if (!team) return;
    setDeviceActionPending(true);
    setDeviceError(null);
    try {
      const ok = await teamService.setMainDevice(team.id, deviceId);
      if (!ok) {
        setDeviceError('Wisselen van hoofdtelefoon mislukt.');
        return;
      }
      await refreshDevices();
    } finally {
      setDeviceActionPending(false);
    }
  };

  const handleRemoveDevice = async (deviceId: string) => {
    if (!team) return;
    setDeviceActionPending(true);
    setDeviceError(null);
    try {
      const ok = await teamService.removeDevice(team.id, deviceId);
      if (!ok) {
        setDeviceError('Apparaat verwijderen mislukt.');
        return;
      }
      await refreshDevices();
    } finally {
      setDeviceActionPending(false);
    }
  };

  const handleResetDevices = async () => {
    if (!team) return;
    setDeviceActionPending(true);
    setDeviceError(null);
    try {
      const ok = await teamService.resetDevices(team.id);
      if (!ok) {
        setDeviceError('Resetten van check-in mislukt.');
        return;
      }
      setConfirmReset(false);
      await refreshDevices();
      onSave?.();
    } finally {
      setDeviceActionPending(false);
    }
  };

  const handleEdit = () => {
    setIsEditing(true);
    setError(null);
  };

  const handleCancel = () => {
    if (isCreateMode) {
      onClose();
    } else {
      setIsEditing(false);
      if (team) {
        setFormData({
          name: team.name,
          description: team.description,
          participants: team.participants.join('\n'),
          emergencyPhoneNumbers: team.emergencyPhoneNumbers ? team.emergencyPhoneNumbers.join('\n') : '',
          notes: team.notes || '',
          checkedIn: team.checkedIn,
          checkedInDigitally: team.checkedInDigitally,
          traccarEnabled: team.traccarEnabled,
          finished: team.finished,
          totalPoints: team.totalPoints,
          trackerId: team.trackerId ?? '',
        });
      }
      setError(null);
    }
  };

  const handleSave = async () => {
    setLoading(true);
    setError(null);

    try {
      const participants = formData.participants
        .split('\n')
        .map((p) => p.trim())
        .filter((p) => p.length > 0);

      const emergencyPhoneNumbers = formData.emergencyPhoneNumbers
        .split('\n')
        .map((p) => p.trim())
        .filter((p) => p.length > 0);

      const teamData = {
        name: formData.name,
        description: formData.description,
        participants,
        emergencyPhoneNumbers,
        notes: formData.notes || undefined,
        checkedIn: formData.checkedIn,
        checkedInDigitally: formData.checkedInDigitally,
        traccarEnabled: formData.traccarEnabled,
        finished: formData.finished,
        totalPoints: formData.totalPoints,
      };

      if (isCreateMode) {
        await teamService.createTeam({
          ...teamData,
          gameId: gameId,
        });
      } else if (team) {
        await teamService.updateTeam(team.id, teamData);

        // Tracker assignment goes through its own endpoint (pool validation). Only
        // call it when the selection actually changed.
        if (formData.trackerId !== (team.trackerId ?? '')) {
          const { error: trackerError } = await teamService.assignTracker(
            team.id,
            formData.trackerId || null,
          );
          if (trackerError) {
            setError(trackerError);
            return;
          }
        }
      }

      setIsEditing(false);
      onSave?.();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save team');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!team) return;
    setDeleting(true);
    setError(null);
    try {
      const { error: deleteError } = await teamService.deleteTeam(team.id);
      if (deleteError) {
        setError(deleteError);
        return;
      }
      setConfirmDelete(false);
      onSave?.();
      onClose();
    } finally {
      setDeleting(false);
    }
  };

  const handleChange = (field: keyof typeof formData) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => {
    setFormData((prev) => ({
      ...prev,
      [field]: event.target.value,
    }));
  };

  const handleCheckboxChange = (field: 'checkedIn' | 'checkedInDigitally' | 'traccarEnabled' | 'finished') => (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const newValue = event.target.checked;
    setFormData((prev) => {
      // If unchecking checkedIn, also uncheck checkedInDigitally
      if (field === 'checkedIn' && !newValue) {
        return {
          ...prev,
          checkedIn: false,
          checkedInDigitally: false,
        };
      }
      return {
        ...prev,
        [field]: newValue,
      };
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {isCreateMode ? 'Create New Team' : isEditing ? 'Edit Team' : 'Team Details'}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {confirmDelete && team && (
          <Alert
            severity="error"
            sx={{ mb: 2 }}
            action={
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  color="inherit"
                  disabled={deleting}
                  onClick={() => setConfirmDelete(false)}
                >
                  Annuleer
                </Button>
                <Button
                  size="small"
                  color="error"
                  variant="contained"
                  disabled={deleting}
                  onClick={handleDelete}
                >
                  {deleting ? <CircularProgress size={20} /> : 'Verwijder definitief'}
                </Button>
              </Stack>
            }
          >
            Team "{team.name}" en alle bijbehorende gegevens (punten, foto's, locaties, apparaten) definitief verwijderen?
          </Alert>
        )}

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <TextField
            label="Team Name"
            value={formData.name}
            onChange={handleChange('name')}
            disabled={!isEditing}
            required
            fullWidth
          />

          <TextField
            label="Description"
            value={formData.description}
            onChange={handleChange('description')}
            disabled={!isEditing}
            multiline
            rows={2}
            fullWidth
          />

          <TextField
            label="Participants"
            value={formData.participants}
            onChange={handleChange('participants')}
            disabled={!isEditing}
            multiline
            rows={4}
            fullWidth
            helperText="One participant per line"
        />
                  
          <TextField
            label="Emergency Phone Numbers"
            value={formData.emergencyPhoneNumbers}
            onChange={handleChange('emergencyPhoneNumbers')}
            disabled={!isEditing}
            multiline
            rows={3}
            fullWidth
            helperText="One phone number per line"
          />

          <TextField
            label="Notes"
            value={formData.notes}
            onChange={handleChange('notes')}
            disabled={!isEditing}
            multiline
            rows={3}
            fullWidth
          />

          {!isCreateMode && team && (
            <>
              {isEditing ? (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Check-in Status (Admin Override)
                  </Typography>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.checkedIn}
                        onChange={handleCheckboxChange('checkedIn')}
                      />
                    }
                    label="Checked In"
                  />
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.checkedInDigitally}
                        onChange={handleCheckboxChange('checkedInDigitally')}
                        disabled={!formData.checkedIn}
                      />
                    }
                    label="Checked In Digitally"
                  />
                </Box>
              ) : (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Status
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    <Chip
                      label={team.checkedIn ? 'Checked In' : 'Not Checked In'}
                      color={team.checkedIn ? 'success' : 'default'}
                      size="small"
                    />
                    {team.checkedIn && (
                      <Chip
                        label={team.checkedInDigitally ? 'Digital Check-in' : 'Manual Check-in'}
                        color={team.checkedInDigitally ? 'info' : 'warning'}
                        size="small"
                        variant="outlined"
                      />
                    )}
                  </Box>
                </Box>
              )}

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Traccar-tracking
                </Typography>
                {isEditing ? (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.traccarEnabled}
                        onChange={handleCheckboxChange('traccarEnabled')}
                      />
                    }
                    label="Traccar-app gebruiken voor locatie"
                  />
                ) : (
                  <Chip
                    label={team.traccarEnabled ? 'Ingeschakeld' : 'Uit'}
                    color={team.traccarEnabled ? 'success' : 'default'}
                    size="small"
                  />
                )}
                {team.traccarEnabled && team.traccarIdentifier && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, wordBreak: 'break-all' }}>
                    Apparaat-ID: {team.traccarIdentifier}
                  </Typography>
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  GPS-tracker
                </Typography>
                {isEditing ? (
                  <FormControl fullWidth size="small">
                    <InputLabel id="tracker-select-label">Tracker</InputLabel>
                    <Select
                      labelId="tracker-select-label"
                      label="Tracker"
                      value={formData.trackerId}
                      onChange={(e) =>
                        setFormData((prev) => ({ ...prev, trackerId: e.target.value }))
                      }
                    >
                      <MenuItem value="">
                        <em>Geen tracker</em>
                      </MenuItem>
                      {/* Current tracker first (it's excluded from the available pool), then the pool. */}
                      {team.trackerId && (
                        <MenuItem value={team.trackerId}>{team.trackerId}</MenuItem>
                      )}
                      {availableTrackers.map((id) => (
                        <MenuItem key={id} value={id}>
                          {id}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                ) : (
                  <Chip
                    label={team.trackerId ? team.trackerId : 'Geen tracker toegewezen'}
                    color={team.trackerId ? 'success' : 'default'}
                    size="small"
                  />
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Finish
                </Typography>
                {isEditing ? (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.finished}
                        onChange={handleCheckboxChange('finished')}
                      />
                    }
                    label="Gefinisht (geen automatische punten meer)"
                  />
                ) : (
                  <Chip
                    label={team.finished ? 'Gefinisht' : 'Nog onderweg'}
                    color={team.finished ? 'success' : 'default'}
                    size="small"
                  />
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Points
                </Typography>
                {isEditing ? (
                  <TextField
                    type="number"
                    value={formData.totalPoints}
                    onChange={(e) => {
                      const value = Math.max(0, parseInt(e.target.value, 10) || 0);
                      setFormData((prev) => ({ ...prev, totalPoints: value }));
                    }}
                    slotProps={{ htmlInput: { min: 0 } }}
                    size="small"
                    fullWidth
                  />
                ) : (
                  <Typography>{team.totalPoints}</Typography>
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Rival Teams
                </Typography>
                {loadingRivals ? (
                  <CircularProgress size={20} />
                ) : rivalTeams.length > 0 ? (
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    {rivalTeams.map((rival) => (
                      <Chip key={rival.id} label={rival.name} size="small" />
                    ))}
                  </Box>
                ) : (
                  <Typography color="text.secondary">No rival teams</Typography>
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Apparaten
                </Typography>
                {deviceError && (
                  <Alert severity="error" sx={{ mb: 1 }}>{deviceError}</Alert>
                )}
                {loadingDevices ? (
                  <CircularProgress size={20} />
                ) : devices.length === 0 ? (
                  <Typography color="text.secondary">Nog geen apparaten ingecheckt.</Typography>
                ) : (
                  <Stack spacing={1}>
                    {devices.map((device) => (
                      <Box
                        key={device.deviceId}
                        sx={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 1,
                          p: 1,
                          border: '1px solid',
                          borderColor: 'divider',
                          borderRadius: 1,
                        }}
                      >
                        <Box sx={{ flexGrow: 1 }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="body2">
                              {device.nickname && device.nickname.length > 0 ? device.nickname : '(zonder naam)'}
                            </Typography>
                            {device.isMain && (
                              <Chip size="small" color="primary" label="Hoofdtelefoon" />
                            )}
                          </Box>
                          <Typography variant="caption" color="text.secondary">
                            Laatst gezien: {new Date(device.lastSeen).toLocaleString('nl-NL')}
                          </Typography>
                        </Box>
                        {!device.isMain && (
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={deviceActionPending}
                            onClick={() => handleMakeMain(device.deviceId)}
                          >
                            Maak hoofdtelefoon
                          </Button>
                        )}
                        <Button
                          size="small"
                          color="error"
                          variant="outlined"
                          disabled={deviceActionPending}
                          onClick={() => handleRemoveDevice(device.deviceId)}
                        >
                          Verwijder
                        </Button>
                      </Box>
                    ))}
                  </Stack>
                )}

                <Box sx={{ mt: 2 }}>
                  {confirmReset ? (
                    <Alert
                      severity="warning"
                      action={
                        <Stack direction="row" spacing={1}>
                          <Button
                            size="small"
                            color="inherit"
                            disabled={deviceActionPending}
                            onClick={() => setConfirmReset(false)}
                          >
                            Annuleer
                          </Button>
                          <Button
                            size="small"
                            color="error"
                            variant="contained"
                            disabled={deviceActionPending}
                            onClick={handleResetDevices}
                          >
                            Reset
                          </Button>
                        </Stack>
                      }
                    >
                      Alle apparaten loskoppelen, deelnemers en noodnummers wissen, en check-in resetten?
                    </Alert>
                  ) : (
                    <Button
                      size="small"
                      color="error"
                      variant="outlined"
                      disabled={deviceActionPending}
                      onClick={() => setConfirmReset(true)}
                    >
                      Check-in resetten
                    </Button>
                  )}
                </Box>
              </Box>
            </>
          )}
        </Box>
      </DialogContent>
      <DialogActions sx={{ justifyContent: isEditing || isCreateMode ? 'flex-end' : 'space-between' }}>
        {isEditing ? (
          <>
            <Button onClick={handleCancel} disabled={loading}>
              Cancel
            </Button>
            <Button
              onClick={handleSave}
              variant="contained"
              disabled={loading || !formData.name}
            >
              {loading ? <CircularProgress size={24} /> : 'Save'}
            </Button>
          </>
        ) : (
          <>
            {!isCreateMode && (
              <Button
                color="error"
                onClick={() => setConfirmDelete(true)}
                disabled={deleting || confirmDelete}
              >
                Verwijder team
              </Button>
            )}
            <Box>
              <Button onClick={onClose}>Close</Button>
              <Button onClick={handleEdit} variant="contained" sx={{ ml: 1 }}>
                Edit
              </Button>
            </Box>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
