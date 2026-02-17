/**
 * Check-in Page - Game-scoped token authentication with team selection and data collection
 */
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  CircularProgress,
  Container,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import api from '../../services/api';
import { storage } from '../../services/storage';
import teamService from '../../services/teamService';
import { useTeamStore } from '../../store/useTeamStore';
import { isPhoneNumber, validateParticipantNames } from '../../utils/validators';
import type { PlayerTeam, TeamOption, TokenExchangeResponse } from '../../types/entities';

export function CheckInPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { setTeam } = useTeamStore();

  const [checkInToken, setCheckInToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const [teamOptions, setTeamOptions] = useState<TeamOption[]>([]);
  const [selectedTeam, setSelectedTeam] = useState<TeamOption | null>(null);
  const [gameId, setGameId] = useState<string | null>(null);
  const [teamId, setTeamId] = useState<string | null>(null);

  const [participantsText, setParticipantsText] = useState('');
  const [emergencyPhonesText, setEmergencyPhonesText] = useState('');

  const [step, setStep] = useState<'loading' | 'select' | 'details'>('loading');

  const sortedTeamOptions = useMemo(() => {
    return [...teamOptions].sort((a, b) => {
      const aNum = Number.parseInt(a.boatNumber, 10);
      const bNum = Number.parseInt(b.boatNumber, 10);

      if (!Number.isNaN(aNum) && !Number.isNaN(bNum)) {
        return aNum - bNum;
      }

      if (a.boatNumber == null) {
        return 0;
      } else if (b.boatNumber == null) {
        return 0;
      }

      return a.boatNumber.localeCompare(b.boatNumber);
    });
  }, [teamOptions]);

  useEffect(() => {
    const authenticateWithToken = async () => {
      const token = searchParams.get('token');

      if (!token) {
        setError('Geen authenticatie token gevonden. Vraag de organisatie om een nieuwe link.');
        setLoading(false);
        return;
      }

      setCheckInToken(token);

      try {
        const tokenResponse = await api.exchangeToken(token);

        if (tokenResponse.error) {
          setError('Authenticatie mislukt: ' + tokenResponse.error);
          return;
        }

        if (tokenResponse.data) {
          const data = tokenResponse.data as TokenExchangeResponse;

          if ('teamOptions' in data) {
            setTeamOptions(data.teamOptions ?? []);
            setGameId(data.gameId ?? null);
            setStep('select');
            return;
          }

          if ('accessToken' in data) {
            storage.setAuthToken(data.accessToken);
            storage.setRefreshToken(data.refreshToken);

            const teamResponse = await api.getMyTeam();

            if (teamResponse.error) {
              setError('Kon teamgegevens niet ophalen: ' + teamResponse.error);
              storage.clearCache();
              storage.clearSession();
              return;
            }

            if (teamResponse.data) {
              const teamData = teamResponse.data as PlayerTeam;
              if (teamData.checkedIn) {
                storage.setTeamId(teamData.id);
                storage.setGameId(teamData.gameId);
                storage.setTeamData(teamData);
                setTeam(teamData);
                navigate('/dashboard', { replace: true });
                return;
              }

              setTeamId(teamData.id);
              setGameId(teamData.gameId);
              setStep('details');
            }
          }
        }
      } catch (err) {
        setError('Er is een fout opgetreden tijdens het inloggen.');
        console.error('Authentication error:', err);
      } finally {
        setLoading(false);
      }
    };

    authenticateWithToken();
  }, [searchParams, navigate, setTeam]);

  const handleTeamSelection = async () => {
    if (!checkInToken || !selectedTeam) {
      setFormError('Kies eerst je team.');
      return;
    }

    setSubmitting(true);
    setFormError(null);

    try {
      const response = await api.selectTeam(checkInToken, selectedTeam.id);

      if (response.error) {
        // Special handling for already-checked-in teams
        if (response.status === 409) {
          setFormError('Dit team is al ingecheckt. Vraag de organisatie om hulp als dit niet klopt.');
        } else {
          setFormError('Team selectie mislukt: ' + response.error);
        }
        return;
      }

      if (response.data) {
        const data = response.data as TokenExchangeResponse;

        if ('accessToken' in data) {
          storage.setAuthToken(data.accessToken);
          storage.setRefreshToken(data.refreshToken);

          const teamResponse = await api.getMyTeam();

          if (teamResponse.error) {
            setFormError('Kon teamgegevens niet ophalen: ' + teamResponse.error);
            return;
          }

          if (teamResponse.data) {
            const teamData = teamResponse.data as PlayerTeam;
            if (teamData.checkedIn) {
              storage.setTeamId(teamData.id);
              storage.setGameId(teamData.gameId);
              storage.setTeamData(teamData);
              setTeam(teamData);
              navigate('/dashboard', { replace: true });
              return;
            }

            setTeamId(teamData.id);
            setGameId(teamData.gameId);
            setStep('details');
          }
        }
      }
    } catch (err) {
      setFormError('Er is een fout opgetreden bij het selecteren van je team.');
      console.error('Team selection error:', err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleCheckIn = async () => {
    if (!teamId || !gameId) {
      setFormError('Team gegevens ontbreken. Vraag de organisatie om hulp.');
      return;
    }

    setSubmitting(true);
    setFormError(null);

    const participants = participantsText
      .split('\n')
      .map((name) => name.trim())
      .filter((name) => name.length > 0);

    const emergencyPhones = emergencyPhonesText
      .split('\n')
      .map((phone) => phone.trim())
      .filter((phone) => phone.length > 0);

    const participantsError = validateParticipantNames(participants);
    if (participantsError) {
      setFormError(participantsError);
      setSubmitting(false);
      return;
    }

    if (emergencyPhones.length === 0) {
      setFormError('Voeg minimaal één noodnummer toe.');
      setSubmitting(false);
      return;
    }

    const invalidPhone = emergencyPhones.find((phone) => !isPhoneNumber(phone));
    if (invalidPhone) {
      setFormError(`Ongeldig telefoonnummer: ${invalidPhone}`);
      setSubmitting(false);
      return;
    }

    try {
      const updateParticipantsResponse = await api.updateParticipants(teamId, participants);
      if (updateParticipantsResponse.error) {
        setFormError('Kan deelnemers niet opslaan: ' + updateParticipantsResponse.error);
        return;
      }

      const updatePhonesResponse = await api.updateEmergencyPhones(teamId, emergencyPhones);
      if (updatePhonesResponse.error) {
        setFormError('Kan noodnummers niet opslaan: ' + updatePhonesResponse.error);
        return;
      }

      const checkedInTeam = await teamService.checkIn(teamId);
      if (!checkedInTeam) {
        setFormError('Inchecken mislukt.');
        return;
      }

      const teamResponse = await api.getMyTeam();
      if (teamResponse.data) {
        const teamData = teamResponse.data as PlayerTeam;
        storage.setTeamId(teamData.id);
        storage.setGameId(teamData.gameId);
        storage.setTeamData(teamData);
        setTeam(teamData);
      }

      navigate('/dashboard', { replace: true });
    } catch (err) {
      setFormError('Er is een fout opgetreden tijdens het inchecken.');
      console.error('Check-in error:', err);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Container maxWidth="sm">
      <Box sx={{ mt: 6, mb: 4, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <Paper elevation={3} sx={{ p: 3, width: '100%' }}>
          <Stack spacing={3}>
            <Box sx={{ textAlign: 'center' }}>
              <Typography variant="h4" component="h1" gutterBottom>
                Vlettenrally
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Vul je teamgegevens in om door te gaan.
              </Typography>
            </Box>

            {loading && (
              <Box sx={{ mt: 2, mb: 2, textAlign: 'center' }}>
                <CircularProgress />
                <Typography variant="body1" color="text.secondary" sx={{ mt: 2 }}>
                  Bezig met inloggen...
                </Typography>
              </Box>
            )}

            {error && (
              <Alert severity="error">{error}</Alert>
            )}

            {!loading && !error && step === 'select' && (
              <Stack spacing={2}>
                <Autocomplete
                  options={sortedTeamOptions}
                  value={selectedTeam}
                  onChange={(_, value) => setSelectedTeam(value)}
                  getOptionLabel={(option) => `${option.boatNumber} - ${option.name}`}
                  renderOption={(props, option) => (
                    <Box component="li" {...props} sx={{ minHeight: 48, alignItems: 'center', display: 'flex', gap: 1 }}>
                      <Typography variant="body1" sx={{ fontWeight: 600, minWidth: 56 }}>
                        {option.boatNumber}
                      </Typography>
                      <Typography variant="body1">{option.name}</Typography>
                    </Box>
                  )}
                  renderInput={(params) => (
                    <TextField
                      {...params}
                      label="Kies je team"
                      placeholder="Zoek op bootnummer of teamnaam"
                      fullWidth
                    />
                  )}
                  fullWidth
                  autoHighlight
                  openOnFocus
                />

                {formError && <Alert severity="error">{formError}</Alert>}

                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={!selectedTeam || submitting}
                  onClick={handleTeamSelection}
                >
                  {submitting ? 'Bezig...' : 'Verder'}
                </Button>
              </Stack>
            )}

            {!loading && !error && step === 'details' && (
              <Stack spacing={2}>
                <Typography variant="h6">Teamgegevens</Typography>

                <TextField
                  label="Deelnemers"
                  value={participantsText}
                  onChange={(event) => setParticipantsText(event.target.value)}
                  multiline
                  minRows={4}
                  fullWidth
                  helperText="Zet elke deelnemer op een nieuwe regel"
                />

                <TextField
                  label="Noodnummers"
                  value={emergencyPhonesText}
                  onChange={(event) => setEmergencyPhonesText(event.target.value)}
                  multiline
                  minRows={3}
                  fullWidth
                  helperText="Zet elk noodnummer op een nieuwe regel"
                />

                {formError && <Alert severity="error">{formError}</Alert>}

                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={submitting}
                  onClick={handleCheckIn}
                >
                  {submitting ? 'Bezig met inchecken...' : 'Inchecken'}
                </Button>
              </Stack>
            )}
          </Stack>
        </Paper>
      </Box>
    </Container>
  );
}
