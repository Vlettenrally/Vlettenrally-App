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
import { useTranslation } from 'react-i18next';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { teamService } from '../../services/teamService';
import { gameService } from '../../services/gameService';
import { isPhoneNumber, validateParticipantNames } from '../../utils/validators';
import { teamMatchesQuery } from '../../utils/teamSearch';
import type { AdminTeam } from '../../types/entities';

type Step = 'search' | 'overview' | 'manual';

export default function CheckIn() {
  const { t } = useTranslation();
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
          {t('admin.checkIn.verifyTracker')}
        </Typography>
        <Typography variant="h6" sx={{ wordBreak: 'break-all' }}>
          {team.trackerId || t('admin.checkIn.noTrackerAssigned')}
        </Typography>
      </Box>
    </Box>
  );

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
                      <Typography variant="body1" sx={{ flexGrow: 1 }}>
                        {option.name}
                      </Typography>
                      {option.checkedIn ? (
                        <Chip size="small" color="success" label={t('admin.checkIn.checkedIn')} />
                      ) : (
                        <Chip size="small" color="default" label={t('admin.checkIn.notYet')} />
                      )}
                      {option.checkedIn && option.checkedInDigitally && (
                        <Chip size="small" color="info" variant="outlined" label={t('admin.checkIn.digital')} />
                      )}
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
                    <Chip
                      label={selectedTeam.checkedIn ? t('admin.checkIn.checkedIn') : t('admin.checkIn.notCheckedIn')}
                      color={selectedTeam.checkedIn ? 'success' : 'default'}
                      size="small"
                    />
                    {selectedTeam.checkedIn && (
                      <Chip
                        label={selectedTeam.checkedInDigitally ? t('admin.checkIn.digital') : t('admin.checkIn.manual')}
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

                {renderTracker(selectedTeam)}

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
