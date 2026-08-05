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
  Smartphone as DigitalIcon,
  EditNote as ManualIcon,
} from '@mui/icons-material';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { teamService } from '../../services/teamService';
import { gameService } from '../../services/gameService';
import { trackerService } from '../../services/trackerService';
import TeamCheckInChip from '../../components/TeamCheckInChip';
import TrackerStatusBox from '../../components/TrackerStatusBox';
import { isPhoneNumber, validateParticipantNames } from '../../utils/validators';
import { teamMatchesQuery } from '../../utils/teamSearch';
import type { AdminTeam, Tracker } from '../../types/entities';

type Step = 'search' | 'overview' | 'manual';

export default function CheckIn() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [step, setStep] = useState<Step>('search');
  const [selectedTeam, setSelectedTeam] = useState<AdminTeam | null>(null);

  // undefined = health not fetched yet, null = fetched but no matching pool record.
  const [selectedTrackerHealth, setSelectedTrackerHealth] = useState<Tracker | null | undefined>(undefined);
  const latestTrackerRequestRef = useRef<string | undefined>(undefined);

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

  // Fetch the selected team's tracker health for the "verify tracker" box. Guarded
  // against out-of-order responses: if the team is switched again before this
  // resolves, latestTrackerRequestRef will have moved on and the stale result is
  // dropped.
  useEffect(() => {
    const trackerId = selectedTeam?.trackerId ?? undefined;
    latestTrackerRequestRef.current = trackerId;
    if (!trackerId) {
      setSelectedTrackerHealth(undefined);
      return;
    }
    setSelectedTrackerHealth(undefined);
    trackerService
      .getTrackers()
      .then((trackers) => {
        if (latestTrackerRequestRef.current !== trackerId) return;
        setSelectedTrackerHealth(trackers.find((tr) => tr.trackerId === trackerId) ?? null);
      })
      .catch((err) => {
        console.error('Failed to load tracker health:', err);
        if (latestTrackerRequestRef.current !== trackerId) return;
        setSelectedTrackerHealth(null);
      });
  }, [selectedTeam?.trackerId]);

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

  // --- Digital branch: mark the team physically checked in and record that they finish
  // check-in on their own phones, so the desk can see who still has to. ---
  const handleDigitalCheckIn = async () => {
    if (!selectedTeam) return;
    setSubmitting(true);
    setError(null);
    try {
      const alreadyDigital = selectedTeam.checkedIn && selectedTeam.checkInMethod === 'DIGITAL';
      if (!alreadyDigital) {
        const updated = await teamService.updateTeam(selectedTeam.id, {
          checkedIn: true,
          checkInMethod: 'DIGITAL',
        });
        if (!updated) {
          setError(t('admin.checkIn.failedCheckIn'));
          return;
        }
      }
      await reloadTeams();
      setSuccessMessage(t('admin.checkIn.successDigital', { name: selectedTeam.name }));
      resetToSearch();
    } catch (err) {
      console.error('Digital check-in error:', err);
      setError(t('admin.checkIn.errorCheckIn'));
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
      setError(t('admin.checkIn.addEmergencyPhone'));
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
        checkInMethod: 'MANUAL',
      });
      if (!updated) {
        setError(t('admin.checkIn.failedCheckIn'));
        return;
      }

      await reloadTeams();
      setSuccessMessage(t('admin.checkIn.successManual', { name: selectedTeam.name }));
      resetToSearch();
    } catch (err) {
      console.error('Manual check-in error:', err);
      setError(t('admin.checkIn.errorCheckIn'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ p: 3, maxWidth: 700, mx: 'auto' }}>
      <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold', mb: 1 }}>
        {t('admin.checkIn.title')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
        {t('admin.checkIn.subtitle')}
      </Typography>

      {!selectedGameId && (
        <Alert severity="info">{t('admin.checkIn.selectGameFirst')}</Alert>
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
                <Typography variant="h6">{t('admin.checkIn.searchTeam')}</Typography>
                <Autocomplete
                  options={sortedTeams}
                  value={selectedTeam}
                  onChange={(_, value) => handleSelectTeam(value)}
                  getOptionLabel={(option) => option.name}
                  isOptionEqualToValue={(option, val) => option.id === val.id}
                  filterOptions={(options, { inputValue }) =>
                    options.filter((option) => teamMatchesQuery(option, inputValue))
                  }
                  renderOption={({ key, ...props }, option) => (
                    <Box
                      key={key}
                      component="li"
                      {...props}
                      sx={{ minHeight: 48, display: 'flex', alignItems: 'center', gap: 1 }}
                    >
                      <Box sx={{ flexGrow: 1 }}>
                        <Typography variant="body1">{option.name}</Typography>
                        {option.description && (
                          <Typography variant="caption" color="text.secondary" display="block">
                            {option.description}
                          </Typography>
                        )}
                      </Box>
                      <TeamCheckInChip team={option} />
                    </Box>
                  )}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label={t('admin.checkIn.teamNameOrBoatNumber')}
                      placeholder={t('admin.checkIn.startTyping')}
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
                    <TeamCheckInChip team={selectedTeam} />
                  </Box>
                </Box>

                <TrackerStatusBox
                  trackerId={selectedTeam.trackerId}
                  tracker={selectedTrackerHealth}
                  label={t('admin.checkIn.verifyTracker')}
                  noTrackerLabel={t('admin.checkIn.noTrackerAssigned')}
                />

                {selectedTeam.checkedIn && (
                  <Alert severity="info">
                    {t('admin.checkIn.alreadyCheckedInNote')}
                  </Alert>
                )}

                <Box>
                  <Typography variant="subtitle1" sx={{ mb: 1.5 }}>
                    {t('admin.checkIn.howDoesTeamCheckIn')}
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
                      {submitting ? <CircularProgress size={24} /> : t('admin.checkIn.teamChecksInDigitally')}
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
                      {t('admin.checkIn.manualCheckIn')}
                    </Button>
                  </Stack>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.5 }}>
                    {t('admin.checkIn.digitalDescription')}
                  </Typography>
                </Box>

                <Button startIcon={<BackIcon />} onClick={resetToSearch} sx={{ alignSelf: 'flex-start' }}>
                  {t('admin.checkIn.anotherTeam')}
                </Button>
              </Stack>
            )}

            {/* STEP 3 — MANUAL DETAILS */}
            {step === 'manual' && selectedTeam && (
              <Stack spacing={2}>
                <Typography variant="h6">{t('admin.checkIn.manualCheckInTitle', { name: selectedTeam.name })}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('admin.checkIn.digitalDescription')}
                </Typography>

                <TrackerStatusBox
                  trackerId={selectedTeam.trackerId}
                  tracker={selectedTrackerHealth}
                  label={t('admin.checkIn.verifyTracker')}
                  noTrackerLabel={t('admin.checkIn.noTrackerAssigned')}
                />

                <TextField
                  label={t('admin.checkIn.participants')}
                  value={participantsText}
                  onChange={(e) => setParticipantsText(e.target.value)}
                  multiline
                  minRows={4}
                  fullWidth
                  helperText={t('admin.checkIn.participantsHint')}
                />
                <TextField
                  label={t('admin.checkIn.emergencyPhones')}
                  value={emergencyPhonesText}
                  onChange={(e) => setEmergencyPhonesText(e.target.value)}
                  multiline
                  minRows={3}
                  fullWidth
                  helperText={t('admin.checkIn.phonesHint')}
                />

                <FormControl fullWidth size="small">
                  <InputLabel id="checkin-tracker-label">{t('admin.checkIn.gpsTracker')}</InputLabel>
                  <Select
                    labelId="checkin-tracker-label"
                    label={t('admin.checkIn.gpsTracker')}
                    value={trackerId}
                    onChange={(e) => setTrackerId(e.target.value)}
                  >
                    <MenuItem value="">
                      <em>{t('admin.checkIn.noTracker')}</em>
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
                  label={t('admin.checkIn.notes')}
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
                    {t('common.back')}
                  </Button>
                  <Button
                    variant="contained"
                    size="large"
                    disabled={submitting}
                    onClick={handleManualSubmit}
                  >
                    {submitting ? <CircularProgress size={24} /> : t('admin.checkIn.checkInAction')}
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
