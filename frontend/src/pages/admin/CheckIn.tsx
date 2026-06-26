/**
 * Rallydesk check-in page — a focused, guided alternative to the dense Teams
 * dialog for the repetitive "check teams in one by one at the desk" workflow.
 *
 * Steps: search → overview (choose digital vs manual) → manual details.
 *  - Digital: the team finishes check-in on their own phones, so we only mark the
 *    team physically checked in (unlocks self-service) and return to search.
 *  - Manual: the team won't use the app, so the desk worker fills in the same
 *    info the digital flow collects (participants + emergency phones), confirms the
 *    tracker, optionally adds notes, and completes the check-in.
 */
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import {
  ArrowBack as BackIcon,
  GpsFixed as TrackerIcon,
  Smartphone as DigitalIcon,
  EditNote as ManualIcon,
} from '@mui/icons-material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { teamService } from '../../services/teamService';
import { gameService } from '../../services/gameService';
import { isPhoneNumber, validateParticipantNames } from '../../utils/validators';
import type { AdminTeam } from '../../types/entities';

type Step = 'search' | 'overview' | 'manual';

export default function CheckIn() {
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [step, setStep] = useState<Step>('search');
  const [selectedTeam, setSelectedTeam] = useState<AdminTeam | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Manual-branch form
  const [participantsText, setParticipantsText] = useState('');
  const [emergencyPhonesText, setEmergencyPhonesText] = useState('');
  const [notes, setNotes] = useState('');
  const [trackerId, setTrackerId] = useState('');
  const [availableTrackers, setAvailableTrackers] = useState<string[]>([]);

  const reloadTeams = useCallback(async () => {
    if (!selectedGameId) {
      setTeams([]);
      return;
    }
    const teamData = await teamService.getTeamsByGame(selectedGameId);
    setTeams(teamData);
  }, [selectedGameId]);

  useEffect(() => {
    void reloadTeams();
  }, [reloadTeams]);

  useAdminResync(reloadTeams);

  // Reset the whole flow back to team search (after a completed check-in, or on
  // game change).
  const resetToSearch = useCallback(() => {
    setStep('search');
    setSelectedTeam(null);
    setError(null);
    setParticipantsText('');
    setEmergencyPhonesText('');
    setNotes('');
    setTrackerId('');
    setAvailableTrackers([]);
  }, []);

  // When the selected game changes, abandon any in-progress check-in.
  useEffect(() => {
    resetToSearch();
  }, [selectedGameId, resetToSearch]);

  const sortedTeams = useMemo(
    () =>
      [...teams].sort((a, b) =>
        a.name.localeCompare(b.name, 'nl', { sensitivity: 'base' }),
      ),
    [teams],
  );

  const handleSelectTeam = (team: AdminTeam | null) => {
    if (!team) return;
    setSelectedTeam(team);
    setError(null);
    setStep('overview');
  };

  // --- Digital branch: just ensure the team is physically checked in. ---
  const handleDigitalCheckIn = async () => {
    if (!selectedTeam) return;
    setSubmitting(true);
    setError(null);
    try {
      if (!selectedTeam.checkedIn) {
        const updated = await teamService.updateTeam(selectedTeam.id, { checkedIn: true });
        if (!updated) {
          setError('Inchecken mislukt. Probeer het opnieuw.');
          return;
        }
      }
      await reloadTeams();
      setSuccessMessage(
        `${selectedTeam.name} kan nu digitaal inchecken op de eigen telefoon.`,
      );
      resetToSearch();
    } catch (err) {
      console.error('Digital check-in error:', err);
      setError('Er ging iets mis tijdens het inchecken.');
    } finally {
      setSubmitting(false);
    }
  };

  // --- Manual branch: open the details form, prefilled from existing data. ---
  const handleStartManual = () => {
    if (!selectedTeam) return;
    setParticipantsText(selectedTeam.participants?.join('\n') ?? '');
    setEmergencyPhonesText(selectedTeam.emergencyPhoneNumbers?.join('\n') ?? '');
    setNotes(selectedTeam.notes ?? '');
    setTrackerId(selectedTeam.trackerId ?? '');
    setError(null);
    setStep('manual');

    // Load the pool of unassigned trackers for the picker.
    gameService
      .getAvailableTrackers(selectedTeam.gameId)
      .then(setAvailableTrackers)
      .catch((err) => {
        console.error('Failed to load available trackers:', err);
        setAvailableTrackers([]);
      });
  };

  const handleManualSubmit = async () => {
    if (!selectedTeam) return;

    const participants = participantsText
      .split('\n')
      .map((p) => p.trim())
      .filter((p) => p.length > 0);

    const emergencyPhoneNumbers = emergencyPhonesText
      .split('\n')
      .map((p) => p.trim())
      .filter((p) => p.length > 0);

    const participantsError = validateParticipantNames(participants);
    if (participantsError) {
      setError(participantsError);
      return;
    }
    if (emergencyPhoneNumbers.length === 0) {
      setError('Voeg minimaal één noodnummer toe.');
      return;
    }
    const invalidPhone = emergencyPhoneNumbers.find((phone) => !isPhoneNumber(phone));
    if (invalidPhone) {
      setError(`Ongeldig telefoonnummer: ${invalidPhone}`);
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      // Tracker assignment goes through its own pool-validated endpoint; only call
      // it when the selection actually changed.
      if (trackerId !== (selectedTeam.trackerId ?? '')) {
        const { error: trackerError } = await teamService.assignTracker(
          selectedTeam.id,
          trackerId || null,
        );
        if (trackerError) {
          setError(trackerError);
          return;
        }
      }

      const updated = await teamService.updateTeam(selectedTeam.id, {
        participants,
        emergencyPhoneNumbers,
        notes: notes.trim() || undefined,
        checkedIn: true,
        checkedInDigitally: false,
      });
      if (!updated) {
        setError('Inchecken mislukt. Probeer het opnieuw.');
        return;
      }

      await reloadTeams();
      setSuccessMessage(`${selectedTeam.name} is handmatig ingecheckt.`);
      resetToSearch();
    } catch (err) {
      console.error('Manual check-in error:', err);
      setError('Er ging iets mis tijdens het inchecken.');
    } finally {
      setSubmitting(false);
    }
  };

  const renderTracker = (team: AdminTeam) => (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        p: 2,
        borderRadius: 2,
        border: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.default',
      }}
    >
      <TrackerIcon color={team.trackerId ? 'primary' : 'disabled'} />
      <Box>
        <Typography variant="overline" color="text.secondary" sx={{ display: 'block', lineHeight: 1.2 }}>
          Verifieer de tracker-ID in het tasje
        </Typography>
        <Typography variant="h6" sx={{ wordBreak: 'break-all' }}>
          {team.trackerId || 'Geen tracker toegewezen'}
        </Typography>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ p: 3, maxWidth: 700, mx: 'auto' }}>
      <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold', mb: 1 }}>
        Inchecken
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
        Rallydesk check-in: zoek het team, verifieer de tracker, en kies of het team
        digitaal of handmatig incheckt.
      </Typography>

      {!selectedGameId && (
        <Alert severity="info">Kies eerst een game bovenin om teams in te checken.</Alert>
      )}

      {selectedGameId && (
        <Card>
          <CardContent>
            {error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {error}
              </Alert>
            )}

            {/* STEP 1 — SEARCH */}
            {step === 'search' && (
              <Stack spacing={2}>
                <Typography variant="h6">Zoek het team</Typography>
                <Autocomplete
                  options={sortedTeams}
                  value={selectedTeam}
                  onChange={(_, value) => handleSelectTeam(value)}
                  getOptionLabel={(option) => option.name}
                  isOptionEqualToValue={(option, val) => option.id === val.id}
                  renderOption={(props, option) => (
                    <Box
                      component="li"
                      {...props}
                      sx={{ minHeight: 48, display: 'flex', alignItems: 'center', gap: 1 }}
                    >
                      <Typography variant="body1" sx={{ flexGrow: 1 }}>
                        {option.name}
                      </Typography>
                      {option.checkedIn ? (
                        <Chip size="small" color="success" label="Ingecheckt" />
                      ) : (
                        <Chip size="small" color="default" label="Nog niet" />
                      )}
                      {option.checkedIn && option.checkedInDigitally && (
                        <Chip size="small" color="info" variant="outlined" label="Digitaal" />
                      )}
                    </Box>
                  )}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Teamnaam of bootnummer"
                      placeholder="Begin te typen…"
                      autoFocus
                      fullWidth
                    />
                  )}
                  fullWidth
                  autoHighlight
                  openOnFocus
                />
              </Stack>
            )}

            {/* STEP 2 — OVERVIEW + CHOOSE PATH */}
            {step === 'overview' && selectedTeam && (
              <Stack spacing={3}>
                <Box>
                  <Typography variant="h5" sx={{ fontWeight: 'bold' }}>
                    {selectedTeam.name}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mt: 1 }}>
                    <Chip
                      label={selectedTeam.checkedIn ? 'Ingecheckt' : 'Nog niet ingecheckt'}
                      color={selectedTeam.checkedIn ? 'success' : 'default'}
                      size="small"
                    />
                    {selectedTeam.checkedIn && (
                      <Chip
                        label={selectedTeam.checkedInDigitally ? 'Digitaal' : 'Handmatig'}
                        color={selectedTeam.checkedInDigitally ? 'info' : 'warning'}
                        size="small"
                        variant="outlined"
                      />
                    )}
                  </Box>
                </Box>

                {renderTracker(selectedTeam)}

                {selectedTeam.checkedIn && (
                  <Alert severity="info">
                    Dit team is al ingecheckt. Je kunt de check-in opnieuw verwerken of de
                    gegevens bijwerken.
                  </Alert>
                )}

                <Box>
                  <Typography variant="subtitle1" sx={{ mb: 1.5 }}>
                    Hoe checkt dit team in?
                  </Typography>
                  <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                    <Button
                      variant="contained"
                      size="large"
                      fullWidth
                      startIcon={<DigitalIcon />}
                      disabled={submitting}
                      onClick={handleDigitalCheckIn}
                      sx={{ py: 2 }}
                    >
                      {submitting ? <CircularProgress size={24} /> : 'Team checkt digitaal in'}
                    </Button>
                    <Button
                      variant="outlined"
                      size="large"
                      fullWidth
                      startIcon={<ManualIcon />}
                      disabled={submitting}
                      onClick={handleStartManual}
                      sx={{ py: 2 }}
                    >
                      Handmatig inchecken
                    </Button>
                  </Stack>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
                    Digitaal: het team vult de gegevens zelf in op de eigen telefoon. Handmatig:
                    jij vult de gegevens nu in namens het team.
                  </Typography>
                </Box>

                <Button startIcon={<BackIcon />} onClick={resetToSearch} sx={{ alignSelf: 'flex-start' }}>
                  Ander team
                </Button>
              </Stack>
            )}

            {/* STEP 3 — MANUAL DETAILS */}
            {step === 'manual' && selectedTeam && (
              <Stack spacing={2}>
                <Typography variant="h6">Handmatig inchecken — {selectedTeam.name}</Typography>
                <Typography variant="body2" color="text.secondary">
                  Vul de deelnemers en noodnummers in namens het team, verifieer de tracker en
                  sla op om het team in te checken.
                </Typography>

                {renderTracker(selectedTeam)}

                <TextField
                  label="Deelnemers"
                  value={participantsText}
                  onChange={(e) => setParticipantsText(e.target.value)}
                  multiline
                  minRows={4}
                  fullWidth
                  helperText="Zet elke deelnemer op een nieuwe regel"
                />
                <TextField
                  label="Noodnummers"
                  value={emergencyPhonesText}
                  onChange={(e) => setEmergencyPhonesText(e.target.value)}
                  multiline
                  minRows={3}
                  fullWidth
                  helperText="Zet elk telefoonnummer op een nieuwe regel"
                />

                <FormControl fullWidth size="small">
                  <InputLabel id="checkin-tracker-label">GPS-tracker</InputLabel>
                  <Select
                    labelId="checkin-tracker-label"
                    label="GPS-tracker"
                    value={trackerId}
                    onChange={(e) => setTrackerId(e.target.value)}
                  >
                    <MenuItem value="">
                      <em>Geen tracker</em>
                    </MenuItem>
                    {/* Current tracker first (it's excluded from the available pool), then the pool. */}
                    {selectedTeam.trackerId && (
                      <MenuItem value={selectedTeam.trackerId}>{selectedTeam.trackerId}</MenuItem>
                    )}
                    {availableTrackers.map((id) => (
                      <MenuItem key={id} value={id}>
                        {id}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>

                <TextField
                  label="Notities"
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  multiline
                  minRows={2}
                  fullWidth
                />

                <Stack direction="row" spacing={1} sx={{ justifyContent: 'space-between' }}>
                  <Button
                    startIcon={<BackIcon />}
                    disabled={submitting}
                    onClick={() => {
                      setError(null);
                      setStep('overview');
                    }}
                  >
                    Terug
                  </Button>
                  <Button
                    variant="contained"
                    size="large"
                    disabled={submitting}
                    onClick={handleManualSubmit}
                  >
                    {submitting ? <CircularProgress size={24} /> : 'Inchecken'}
                  </Button>
                </Stack>
              </Stack>
            )}
          </CardContent>
        </Card>
      )}

      <Snackbar
        open={successMessage !== null}
        autoHideDuration={4000}
        onClose={() => setSuccessMessage(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity="success" variant="filled" onClose={() => setSuccessMessage(null)}>
          {successMessage}
        </Alert>
      </Snackbar>
    </Box>
  );
}
