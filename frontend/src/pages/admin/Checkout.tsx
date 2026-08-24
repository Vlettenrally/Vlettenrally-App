/**
 * Rallydesk checkout page — the end-of-game counterpart to CheckIn: find a team,
 * confirm their GPS tracker was physically handed back in, and (if the team
 * hasn't been marked finished yet) finish them in the same action. This page has
 * a single action (mark handed in); reversing a mistaken checkout is only done by
 * editing the team directly, not from this desk flow.
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
  Snackbar,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { AssignmentTurnedIn as HandedInIcon } from '@mui/icons-material';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import { useAsyncData } from '../../hooks/useAsyncData';
import { teamService } from '../../services/teamService';
import { trackerService } from '../../services/trackerService';
import TrackerStatusBox from '../../components/TrackerStatusBox';
import { teamMatchesQuery } from '../../utils/teamSearch';
import type { AdminTeam, Tracker } from '../../types/entities';

export default function Checkout() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  const [teams, setTeams] = useState<AdminTeam[]>([]);
  const [selectedTeam, setSelectedTeam] = useState<AdminTeam | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  // Refetch for handlers (awaited so the list is fresh before the flow resets)
  // and for resync; the game-keyed effect below owns the initial load.
  const reloadTeams = useCallback(async () => {
    if (!selectedGameId) {
      setTeams([]);
      return;
    }
    const teamData = await teamService.getTeamsByGame(selectedGameId);
    setTeams(teamData);
  }, [selectedGameId]);

  useEffect(() => {
    if (!selectedGameId) return;
    let cancelled = false;
    void teamService.getTeamsByGame(selectedGameId).then((teamData) => {
      if (!cancelled) setTeams(teamData);
    });
    return () => {
      cancelled = true;
    };
  }, [selectedGameId]);

  useAdminResync(reloadTeams);

  // Fetch the selected team's tracker health for the hand-in box.
  // useAsyncData drops a response if the team switched while it was in flight.
  const selectedTrackerId = selectedTeam?.trackerId;
  const fetchTrackerHealth = useCallback(async (): Promise<Tracker | null> => {
    try {
      const trackers = await trackerService.getTrackers();
      return trackers.find((tr) => tr.trackerId === selectedTrackerId) ?? null;
    } catch (err) {
      console.error('Failed to load tracker health:', err);
      throw err;
    }
  }, [selectedTrackerId]);
  const {
    data: trackerHealthData,
    loading: trackerHealthLoading,
    error: trackerHealthError,
  } = useAsyncData(fetchTrackerHealth, { enabled: !!selectedTrackerId });
  // undefined = no tracker / not fetched yet, null = fetched but no pool record (or failed).
  const selectedTrackerHealth: Tracker | null | undefined = !selectedTrackerId || trackerHealthLoading
    ? undefined
    : trackerHealthError
      ? null
      : trackerHealthData;

  const resetToSearch = useCallback(() => {
    setSelectedTeam(null);
    setError(null);
  }, []);

  // When the selected game changes, abandon any in-progress checkout and drop
  // the old game's team list (adjust-state-during-render, not an effect).
  const [prevGameId, setPrevGameId] = useState(selectedGameId);
  if (prevGameId !== selectedGameId) {
    setPrevGameId(selectedGameId);
    setTeams([]);
    resetToSearch();
  }

  const sortedTeams = useMemo(
    () =>
      [...teams].sort((a, b) =>
        a.name.localeCompare(b.name, 'nl', { sensitivity: 'base' }),
      ),
    [teams],
  );

  const handleSelectTeam = (team: AdminTeam | null) => {
    setSelectedTeam(team);
    setError(null);
  };

  const handleCheckout = async () => {
    if (!selectedTeam || selectedTeam.trackerHandedIn) return;
    setSubmitting(true);
    setError(null);
    try {
      const updated = await teamService.updateTeam(selectedTeam.id, {
        trackerHandedIn: true,
        ...(selectedTeam.finished ? {} : { finished: true }),
      });
      if (!updated) {
        setError(t('admin.checkout.failedCheckout'));
        return;
      }
      await reloadTeams();
      setSuccessMessage(t('admin.checkout.successHandedIn', { name: selectedTeam.name }));
      resetToSearch();
    } catch (err) {
      console.error('Checkout error:', err);
      setError(t('admin.checkout.errorCheckout'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ p: 3, maxWidth: 700, mx: 'auto' }}>
      <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold', mb: 1 }}>
        {t('admin.checkout.title')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
        {t('admin.checkout.subtitle')}
      </Typography>

      {!selectedGameId && (
        <Alert severity="info">{t('admin.checkout.selectGameFirst')}</Alert>
      )}

      {selectedGameId && (
        <Card>
          <CardContent>
            {error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {error}
              </Alert>
            )}

            <Stack spacing={2}>
              <Typography variant="h6">{t('admin.checkout.searchTeam')}</Typography>
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
                    {option.trackerHandedIn && (
                      <Chip label={t('admin.checkout.handedInChip')} color="success" size="small" />
                    )}
                  </Box>
                )}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label={t('admin.checkout.teamNameOrBoatNumber')}
                    placeholder={t('admin.checkout.startTyping')}
                    autoFocus
                    fullWidth
                  />
                )}
                fullWidth
                autoHighlight
                openOnFocus
              />

              {selectedTeam && (
                <>
                  <Box>
                    <Typography variant="overline" color="text.secondary" sx={{ display: 'block', lineHeight: 1.2 }}>
                      {t('admin.checkout.groupNameLabel')}
                    </Typography>
                    <Typography variant="body1">
                      {selectedTeam.description || t('admin.checkout.noGroupName')}
                    </Typography>
                  </Box>

                  <TrackerStatusBox
                    trackerId={selectedTeam.trackerId}
                    tracker={selectedTrackerHealth}
                    label={t('admin.checkout.trackerLabel')}
                    noTrackerLabel={t('admin.checkout.noTrackerAssigned')}
                  />

                  {selectedTeam.trackerHandedIn ? (
                    <Alert severity="success">{t('admin.checkout.alreadyHandedInNote')}</Alert>
                  ) : (
                    !selectedTeam.finished && (
                      <Alert severity="info">{t('admin.checkout.willAlsoFinish')}</Alert>
                    )
                  )}

                  <Button
                    variant="contained"
                    size="large"
                    fullWidth
                    startIcon={<HandedInIcon />}
                    disabled={submitting || selectedTeam.trackerHandedIn}
                    onClick={handleCheckout}
                    sx={{ py: 2 }}
                  >
                    {submitting ? <CircularProgress size={24} /> : t('admin.checkout.handInAction')}
                  </Button>
                </>
              )}
            </Stack>
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
