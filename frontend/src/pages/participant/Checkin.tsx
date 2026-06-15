/**
 * Public self-service check-in flow with multi-device coordination.
 *
 * Steps: loading → select-game → select-team → nickname → register →
 *        (details + rivals if main / wait if not) → permissions → /dashboard
 *
 * Legacy `?token=` URLs (WordPress) skip the public game/team pickers and jump
 * straight to the nickname step for the pre-resolved team.
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import QRCode from 'qrcode';
import api from '../../services/api';
import { storage } from '../../services/storage';
import teamService from '../../services/teamService';
import { useTeamStore } from '../../store/useTeamStore';
import { useTeamWebSocket } from '../../hooks/useWebSocket';
import { useDeviceIdentity } from '../../hooks/useDeviceIdentity';
import { isPhoneNumber, validateParticipantNames } from '../../utils/validators';
import { initializePushNotificationsWithReason } from '../../services/firebaseMessaging';
import { WebSocketEventType } from '../../services/websocket';
import type {
  CheckInState,
  PlayerTeam,
  RegisterDeviceResponse,
  TeamOption,
  TodayGame,
  TokenExchangeResponse,
} from '../../types/entities';

type Step =
  | 'loading'
  | 'select-game'
  | 'select-team'
  | 'nickname'
  | 'details'
  | 'traccar-setup'
  | 'rivals'
  | 'wait'
  | 'permissions';

interface TraccarSetup {
  identifier: string;
  serverUrl: string;
}

const POLL_STATE_INTERVAL_MS = 5000;
const MAX_RIVALS = 5;

// Shared Traccar Client settings, emitted in two formats below. `interval` is
// seconds and stays above the backend's min-interval guard so fixes aren't
// dropped as too-frequent; `highest` keeps GPS continuously on.
const TRACCAR_SETTINGS = {
  interval: '30',
  distance: '0',
  angle: '0',
  accuracy: 'highest',
  buffer: 'true',
  wakelock: 'true',
} as const;

/**
 * Deep link that opens the installed Traccar Client app pre-configured — one tap
 * on the same phone. Here the ingestion endpoint is passed as the `url` param.
 */
function buildTraccarDeepLink(serverUrl: string, identifier: string): string {
  const params = new URLSearchParams({
    url: serverUrl,
    id: identifier,
    ...TRACCAR_SETTINGS,
  });
  return `org.traccar.client://config?${params.toString()}`;
}

/**
 * The string the Traccar Client's QR scanner expects (for setup from a second
 * phone): the ingestion URL as the base, with settings as query params. The app
 * stores the base as its server url and the params as device settings.
 */
function buildTraccarQrConfig(serverUrl: string, identifier: string): string {
  const params = new URLSearchParams({
    id: identifier,
    ...TRACCAR_SETTINGS,
  });
  return `${serverUrl}?${params.toString()}`;
}

export function CheckInPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { setTeam } = useTeamStore();
  const { deviceId, setDeviceId, nickname: storedNickname, setNickname: persistNickname } = useDeviceIdentity();

  const [step, setStep] = useState<Step>('loading');
  const [error, setError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [todayGames, setTodayGames] = useState<TodayGame[]>([]);
  const [selectedGameId, setSelectedGameId] = useState<string | null>(null);
  const [teamOptions, setTeamOptions] = useState<TeamOption[]>([]);
  const [selectedTeam, setSelectedTeam] = useState<TeamOption | null>(null);
  const [resolvedTeam, setResolvedTeam] = useState<{ id: string; gameId: string; name: string } | null>(null);

  const [nicknameInput, setNicknameInput] = useState(storedNickname);
  const [mainDeviceNickname, setMainDeviceNickname] = useState<string | null>(null);
  // Traccar app setup data, present only for the main device of a Traccar-enabled team.
  const [traccarSetup, setTraccarSetup] = useState<TraccarSetup | null>(null);
  const [traccarQr, setTraccarQr] = useState<string | null>(null);

  const [participantsText, setParticipantsText] = useState('');
  const [emergencyPhonesText, setEmergencyPhonesText] = useState('');

  const [rivalOptions, setRivalOptions] = useState<TeamOption[]>([]);
  const [rivalSelection, setRivalSelection] = useState<TeamOption[]>([]);
  const [loadingRivals, setLoadingRivals] = useState(false);

  const [notificationError, setNotificationError] = useState<string | null>(null);
  const [notificationsDone, setNotificationsDone] = useState(false);

  const legacyTokenRef = useRef<string | null>(null);

  const persistTeamData = useCallback((teamData: PlayerTeam) => {
    storage.setTeamId(teamData.id);
    storage.setGameId(teamData.gameId);
    storage.setTeamData(teamData);
    setTeam(teamData);
  }, [setTeam]);

  const loadTodayGames = useCallback(async () => {
    setError(null);
    const response = await api.getCheckInGamesToday();
    if (response.error) {
      setError('Kon vandaag de games niet ophalen: ' + response.error);
      return;
    }
    const games = response.data ?? [];
    setTodayGames(games);
    if (games.length === 0) {
      setError('Er zijn vandaag geen rally’s. Neem contact op met de organisatie.');
      return;
    }
    if (games.length === 1) {
      setSelectedGameId(games[0].id);
      setStep('select-team');
    } else {
      setStep('select-game');
    }
  }, []);

  const loadTeamsForGame = useCallback(async (gameId: string) => {
    setError(null);
    setFormError(null);
    const response = await api.getCheckInTeams(gameId);
    if (response.error) {
      setFormError('Teams ophalen mislukt: ' + response.error);
      return;
    }
    setTeamOptions(response.data ?? []);
  }, []);

  // Entry: legacy token shortcut OR public flow.
  useEffect(() => {
    const init = async () => {
      const token = searchParams.get('token');
      if (token) {
        legacyTokenRef.current = token;
        try {
          const tokenResponse = await api.exchangeToken(token);
          if (tokenResponse.error) {
            setError('Authenticatie mislukt: ' + tokenResponse.error);
            return;
          }
          const data = tokenResponse.data as TokenExchangeResponse;
          if ('teamOptions' in data) {
            setSelectedGameId(data.gameId ?? null);
            setTeamOptions(data.teamOptions ?? []);
            setStep('select-team');
            return;
          }
          if ('accessToken' in data) {
            // Single-team token: temporarily hold these tokens so we can fetch /team/me,
            // then continue into the public flow (where register-device re-issues a
            // device-bound team token).
            storage.setAuthToken(data.accessToken);
            storage.setRefreshToken(data.refreshToken);
            const teamResponse = await api.getMyTeam();
            if (teamResponse.data) {
              const team = teamResponse.data as PlayerTeam;
              setResolvedTeam({ id: team.id, gameId: team.gameId, name: team.name });
              setSelectedGameId(team.gameId);
              setStep('nickname');
              return;
            }
            setError('Kon teamgegevens niet ophalen.');
            return;
          }
        } catch (err) {
          console.error('Legacy token flow error:', err);
          setError('Er ging iets mis tijdens het inloggen.');
          return;
        }
      }

      await loadTodayGames();
    };

    init().catch((err) => {
      console.error('Check-in init error:', err);
      setError('Er ging iets mis. Probeer de pagina opnieuw te laden.');
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Whenever a game is chosen and we don't yet have a resolved team, fetch its teams.
  useEffect(() => {
    if (step === 'select-team' && selectedGameId && teamOptions.length === 0 && !resolvedTeam) {
      void loadTeamsForGame(selectedGameId);
    }
  }, [step, selectedGameId, teamOptions.length, resolvedTeam, loadTeamsForGame]);

  const sortedTeamOptions = useMemo(() => {
    return [...teamOptions].sort((a, b) => (a.name ?? '').localeCompare(b.name ?? '', 'nl', { sensitivity: 'base' }));
  }, [teamOptions]);

  const handleGameSelection = (gameId: string) => {
    setSelectedGameId(gameId);
    setStep('select-team');
  };

  const handleTeamSelection = () => {
    if (!selectedTeam || !selectedGameId) {
      setFormError('Kies eerst je team.');
      return;
    }
    setResolvedTeam({ id: selectedTeam.id, gameId: selectedGameId, name: selectedTeam.name });
    setFormError(null);
    setStep('nickname');
  };

  const applyRegisterResponse = useCallback(async (response: RegisterDeviceResponse, teamGameId: string, teamId: string) => {
    storage.setAuthToken(response.accessToken);
    storage.setRefreshToken(response.refreshToken);
    // Server assigns the canonical deviceId; persist it for future visits.
    setDeviceId(response.deviceId);
    setMainDeviceNickname(response.mainDeviceNickname ?? null);
    setTraccarSetup(
      response.traccarEnabled && response.traccarIdentifier && response.traccarServerUrl
        ? { identifier: response.traccarIdentifier, serverUrl: response.traccarServerUrl }
        : null
    );

    const teamResponse = await api.getMyTeam();
    if (teamResponse.data) {
      const teamData = teamResponse.data as PlayerTeam;
      persistTeamData(teamData);
    } else {
      storage.setTeamId(teamId);
      storage.setGameId(teamGameId);
    }

    if (response.detailsSubmitted) {
      setStep('permissions');
    } else if (response.isMainDevice) {
      setStep('details');
    } else {
      setStep('wait');
    }
  }, [persistTeamData, setDeviceId]);

  // Pre-render the Traccar config as a QR the app can scan to configure itself.
  useEffect(() => {
    if (!traccarSetup) {
      setTraccarQr(null);
      return;
    }
    let cancelled = false;
    const configUrl = buildTraccarQrConfig(traccarSetup.serverUrl, traccarSetup.identifier);
    QRCode.toDataURL(configUrl, { width: 256, margin: 2, errorCorrectionLevel: 'Q' })
      .then((url) => { if (!cancelled) setTraccarQr(url); })
      .catch(() => { if (!cancelled) setTraccarQr(null); });
    return () => { cancelled = true; };
  }, [traccarSetup]);

  const handleNicknameSubmit = async () => {
    if (!resolvedTeam || !selectedGameId) {
      setFormError('Team gegevens ontbreken. Vraag de organisatie om hulp.');
      return;
    }
    const trimmed = nicknameInput.trim();
    if (trimmed.length === 0) {
      setFormError('Geef je telefoon een naam zodat je deze kunt herkennen.');
      return;
    }
    setSubmitting(true);
    setFormError(null);
    persistNickname(trimmed);

    try {
      const response = await api.registerCheckInDevice({
        gameId: selectedGameId,
        teamId: resolvedTeam.id,
        // deviceId is a HINT — the server uses it only to resume an existing
        // registration; if it doesn't match a row owned by this team, the
        // server allocates a fresh UUID. May be null on first visit.
        deviceId: deviceId ?? null,
        nickname: trimmed,
      });
      if (response.error || !response.data) {
        setFormError('Aanmelden van apparaat mislukt: ' + (response.error ?? 'onbekende fout'));
        return;
      }
      await applyRegisterResponse(response.data, resolvedTeam.gameId, resolvedTeam.id);
    } catch (err) {
      console.error('register-device error:', err);
      setFormError('Er ging iets mis bij het aanmelden van dit apparaat.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDetailsSubmit = async () => {
    if (!resolvedTeam) return;
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
      const updateParticipantsResponse = await api.updateParticipants(resolvedTeam.id, participants);
      if (updateParticipantsResponse.error) {
        setFormError('Kan deelnemers niet opslaan: ' + updateParticipantsResponse.error);
        return;
      }
      const updatePhonesResponse = await api.updateEmergencyPhones(resolvedTeam.id, emergencyPhones);
      if (updatePhonesResponse.error) {
        setFormError('Kan noodnummers niet opslaan: ' + updatePhonesResponse.error);
        return;
      }
      const checkedInTeam = await teamService.checkIn(resolvedTeam.id);
      if (!checkedInTeam) {
        setFormError('Inchecken mislukt.');
        return;
      }
      const teamResponse = await api.getMyTeam();
      if (teamResponse.data) {
        persistTeamData(teamResponse.data as PlayerTeam);
      }
      setStep(traccarSetup ? 'traccar-setup' : 'rivals');
    } catch (err) {
      console.error('details submit error:', err);
      setFormError('Er is een fout opgetreden tijdens het inchecken.');
    } finally {
      setSubmitting(false);
    }
  };

  // --- RIVALS step (main device only, after check-in) ---
  useEffect(() => {
    if (step !== 'rivals' || !resolvedTeam) return;

    let cancelled = false;
    setLoadingRivals(true);
    teamService
      .getEligibleRivalTeams(resolvedTeam.gameId, resolvedTeam.id)
      .then((teams) => {
        if (cancelled) return;
        teams.sort((a, b) => a.name.localeCompare(b.name, 'nl'));
        setRivalOptions(teams);
      })
      .catch((err) => {
        if (cancelled) return;
        console.warn('Failed to load eligible rivals', err);
        setFormError('Kon de teamlijst niet laden.');
      })
      .finally(() => {
        if (!cancelled) setLoadingRivals(false);
      });

    return () => {
      cancelled = true;
    };
  }, [step, resolvedTeam]);

  const handleRivalsSubmit = async () => {
    if (!resolvedTeam) return;
    if (rivalSelection.length === 0) {
      setStep('permissions');
      return;
    }
    setSubmitting(true);
    setFormError(null);
    const result = await teamService.setRivalTeams(
      resolvedTeam.id,
      rivalSelection.map((s) => s.id)
    );
    setSubmitting(false);
    if (result.error) {
      setFormError('Kan rivalen niet opslaan: ' + result.error);
      return;
    }
    setStep('permissions');
  };

  // --- WAIT step: subscribe to coordination events + poll as a fallback ---
  const waitGameId = step === 'wait' && resolvedTeam ? resolvedTeam.gameId : null;
  const waitTeamId = step === 'wait' && resolvedTeam ? resolvedTeam.id : null;
  const { subscribe } = useTeamWebSocket(waitGameId, waitTeamId);

  useEffect(() => {
    if (step !== 'wait' || !resolvedTeam) return;

    const unsubmitted = subscribe(WebSocketEventType.CHECKIN_DETAILS_SUBMITTED, () => {
      setStep('permissions');
    });
    const unreset = subscribe(WebSocketEventType.CHECKIN_RESET, () => {
      storage.clearSession();
      setResolvedTeam(null);
      setTeamOptions([]);
      setSelectedTeam(null);
      setStep('loading');
      void loadTodayGames();
    });
    const unmain = subscribe(WebSocketEventType.CHECKIN_MAIN_CHANGED, (msg) => {
      const payload = msg.payload as { mainDeviceId?: string; mainDeviceNickname?: string } | undefined;
      if (payload?.mainDeviceId === deviceId) {
        setStep('details');
      } else if (payload?.mainDeviceNickname !== undefined) {
        setMainDeviceNickname(payload.mainDeviceNickname);
      }
    });
    const unremoved = subscribe(WebSocketEventType.CHECKIN_DEVICE_REMOVED, (msg) => {
      const payload = msg.payload as { deviceId?: string } | undefined;
      if (payload?.deviceId === deviceId) {
        storage.clearSession();
        setResolvedTeam(null);
        setTeamOptions([]);
        setSelectedTeam(null);
        setStep('loading');
        void loadTodayGames();
      }
    });

    return () => {
      unsubmitted();
      unreset();
      unmain();
      unremoved();
    };
  }, [step, resolvedTeam, subscribe, deviceId, loadTodayGames]);

  useEffect(() => {
    if (step !== 'wait' || !resolvedTeam) return;

    let cancelled = false;

    const poll = async () => {
      const response = await api.getCheckInState();
      if (cancelled || response.error || !response.data) return;
      const state: CheckInState = response.data;
      setMainDeviceNickname(state.mainDeviceNickname);
      if (state.detailsSubmitted) {
        setStep('permissions');
        return;
      }
      if (state.mainDeviceId === deviceId) {
        setStep('details');
      }
    };

    const interval = setInterval(poll, POLL_STATE_INTERVAL_MS);
    void poll();
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [step, resolvedTeam, deviceId]);

  // --- PERMISSIONS step ---
  const handleEnableNotifications = async () => {
    setNotificationError(null);
    try {
      const result = await initializePushNotificationsWithReason();
      if (!result.ok) {
        const message =
          result.reason === 'permission-denied'
            ? 'Meldingen zijn geweigerd. Je kunt verder zonder meldingen.'
            : result.reason === 'no-token'
              ? 'Meldingen kunnen niet worden ingesteld op dit apparaat (geen geldige tokenconfiguratie).'
              : 'Aanmelden van meldingen bij de server is mislukt. Je kunt verder zonder meldingen.';
        setNotificationError(message);
      }
    } catch (err) {
      console.error('Notification permission error:', err);
      setNotificationError('Er is een fout opgetreden bij het inschakelen van meldingen.');
    } finally {
      setNotificationsDone(true);
    }
  };

  const handleFinishPermissions = () => navigate('/dashboard', { replace: true });

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
                {step === 'permissions'
                  ? 'Sta meldingen toe om updates te ontvangen tijdens de rally.'
                  : step === 'rivals'
                    ? 'Kies welke teams jullie in de gaten willen houden.'
                    : step === 'traccar-setup'
                      ? 'Stel de tracking-app in zodat jullie locatie zichtbaar is.'
                      : step === 'wait'
                        ? 'Een andere telefoon van jullie team vult op dit moment de gegevens in.'
                        : 'Volg het spel online en check je team in voor de Vlettenrally.'}
              </Typography>
            </Box>

            {error && <Alert severity="error">{error}</Alert>}

            {step === 'loading' && (
              <Box sx={{ textAlign: 'center' }}>
                <CircularProgress />
              </Box>
            )}

            {step === 'select-game' && (
              <Stack spacing={2}>
                <Typography variant="h6">Kies de rally van vandaag</Typography>
                {todayGames.map((g) => (
                  <Button
                    key={g.id}
                    variant="outlined"
                    size="large"
                    fullWidth
                    onClick={() => handleGameSelection(g.id)}
                  >
                    {g.name}
                  </Button>
                ))}
              </Stack>
            )}

            {step === 'select-team' && (
              <Stack spacing={2}>
                <Typography variant="h6">Zoek je bootnummer</Typography>
                <Autocomplete
                  options={sortedTeamOptions}
                  value={selectedTeam}
                  onChange={(_, value) => setSelectedTeam(value)}
                  getOptionLabel={(option) => option.name ?? ''}
                  renderOption={(props, option) => (
                    <Box component="li" {...props} sx={{ minHeight: 48, display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Typography variant="body1" sx={{ flexGrow: 1 }}>{option.name}</Typography>
                      {option.checkedIn && <Chip size="small" color="success" label="Ingecheckt" />}
                    </Box>
                  )}
                  renderInput={(params) => (
                    <TextField {...params} label="Bijv: 1234" placeholder="Zoek op bootnummer" fullWidth />
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
                  Verder
                </Button>
              </Stack>
            )}

            {step === 'nickname' && (
              <Stack spacing={2}>
                <Typography variant="h6">Naam van deze telefoon</Typography>
                <Typography variant="body2" color="text.secondary">
                  Geef je telefoon een naam zodat de organisatie en je teamgenoten dit apparaat kunnen herkennen
                  (bijv. “Jan’s iPhone”).
                </Typography>
                <TextField
                  label="Naam apparaat"
                  value={nicknameInput}
                  onChange={(e) => setNicknameInput(e.target.value)}
                  fullWidth
                  autoFocus
                />
                {formError && <Alert severity="error">{formError}</Alert>}
                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={submitting}
                  onClick={handleNicknameSubmit}
                >
                  {submitting ? 'Bezig...' : 'Verder'}
                </Button>
              </Stack>
            )}

            {step === 'details' && (
              <Stack spacing={2}>
                <Typography variant="h6">Teamgegevens</Typography>
                <Typography variant="body2" color="text.secondary">
                  Vul de namen en noodnummers in voor jullie team. Alleen jij kunt dit invoeren namens iedereen. Andere telefoons die nu inchecken wachten tot je klaar bent.
                </Typography>
                <TextField
                  label="Deelnemers namen"
                  value={participantsText}
                  onChange={(e) => setParticipantsText(e.target.value)}
                  multiline
                  minRows={4}
                  fullWidth
                  helperText="Zet elke deelnemer op een nieuwe regel"
                />
                <TextField
                  label="Telefoonnummers van telefoons aan boord"
                  value={emergencyPhonesText}
                  onChange={(e) => setEmergencyPhonesText(e.target.value)}
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
                  onClick={handleDetailsSubmit}
                >
                  {submitting ? 'Bezig met inchecken...' : 'Inchecken'}
                </Button>
              </Stack>
            )}

            {step === 'traccar-setup' && traccarSetup && (
              <Stack spacing={2}>
                <Typography variant="h6">Tracking-app instellen</Typography>
                <Typography variant="body2" color="text.secondary">
                  Jullie team wordt gevolgd via de gratis <strong>Traccar Client</strong>-app.
                  Installeer de app eerst en tik daarna op de knop om hem in te stellen.
                </Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    size="small"
                    component="a"
                    href="https://apps.apple.com/app/traccar-client/id843156974"
                    target="_blank"
                    rel="noopener"
                  >
                    iPhone
                  </Button>
                  <Button
                    variant="outlined"
                    size="small"
                    component="a"
                    href="https://play.google.com/store/apps/details?id=org.traccar.client"
                    target="_blank"
                    rel="noopener"
                  >
                    Android
                  </Button>
                </Stack>

                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  component="a"
                  href={buildTraccarDeepLink(traccarSetup.serverUrl, traccarSetup.identifier)}
                >
                  ⚡ Open en configureer de Traccar-app
                </Button>
                <Typography variant="caption" color="text.secondary" sx={{ mt: -1 }}>
                  De app opent met alles ingevuld — daarna hoef je alleen nog de tracking aan te zetten.
                </Typography>

                <Box
                  sx={{
                    p: 2,
                    borderRadius: 2,
                    bgcolor: 'background.default',
                    border: '1px solid',
                    borderColor: 'divider',
                    textAlign: 'center',
                  }}
                >
                  <Typography variant="subtitle2" gutterBottom>
                    Trackt een andere telefoon? Scan deze code
                  </Typography>
                  {traccarQr ? (
                    <Box
                      component="img"
                      src={traccarQr}
                      alt="Traccar configuratie QR-code"
                      sx={{ width: 240, height: 240, maxWidth: '100%' }}
                    />
                  ) : (
                    <CircularProgress />
                  )}
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                    Open op de andere telefoon de Traccar Client-app, tik op het QR-icoon rechtsboven en scan deze code.
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Lukt het niet? Vul dan handmatig in:
                  </Typography>
                  <Stack spacing={1.5} sx={{ mt: 1 }}>
                    <TextField
                      label="Server-URL"
                      value={traccarSetup.serverUrl}
                      fullWidth
                      size="small"
                      slotProps={{ input: { readOnly: true } }}
                      onFocus={(e) => e.target.select()}
                    />
                    <TextField
                      label="Apparaat-ID"
                      value={traccarSetup.identifier}
                      fullWidth
                      size="small"
                      slotProps={{ input: { readOnly: true } }}
                      onFocus={(e) => e.target.select()}
                    />
                    <Button
                      variant="outlined"
                      size="small"
                      onClick={() => {
                        navigator.clipboard?.writeText(traccarSetup.identifier).catch(() => {});
                      }}
                    >
                      Apparaat-ID kopiëren
                    </Button>
                  </Stack>
                </Box>

                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  onClick={() => setStep('rivals')}
                >
                  Klaar, ga verder
                </Button>
              </Stack>
            )}

            {step === 'rivals' && (
              <Stack spacing={2}>
                <Typography variant="h6">Selecteer jullie rivalen</Typography>
                <Typography variant="body2" color="text.secondary">
                  Kies maximaal {MAX_RIVALS} teams om hun stand bij te houden tijdens de Vlettenrally. Dit kan later nog
                  aangepast worden via het dashboard.
                </Typography>
                <Autocomplete
                  multiple
                  options={rivalOptions}
                  value={rivalSelection}
                  loading={loadingRivals}
                  onChange={(_, value) => {
                    if (value.length > MAX_RIVALS) {
                      setFormError(`Je kunt maximaal ${MAX_RIVALS} rivalen kiezen.`);
                      return;
                    }
                    setFormError(null);
                    setRivalSelection(value);
                  }}
                  isOptionEqualToValue={(option, val) => option.id === val.id}
                  getOptionLabel={(option) => option.name}
                  openOnFocus
                  filterSelectedOptions
                  renderValue={(value, getTagProps) =>
                    value.map((option, index) => (
                      <Chip label={option.name} {...getTagProps({ index })} key={option.id} />
                    ))
                  }
                  renderInput={(params) => (
                    <TextField {...params} label="Zoek een bootnummer" placeholder="Begin te typen…" />
                  )}
                />
                {formError && <Alert severity="error">{formError}</Alert>}
                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={submitting || loadingRivals || rivalSelection.length > MAX_RIVALS}
                  onClick={handleRivalsSubmit}
                >
                  {submitting ? 'Bezig...' : 'Verder'}
                </Button>
                <Button
                  variant="text"
                  size="small"
                  fullWidth
                  disabled={submitting}
                  onClick={() => setStep('permissions')}
                >
                  Overslaan
                </Button>
              </Stack>
            )}

            {step === 'wait' && (
              <Stack spacing={2} sx={{ alignItems: 'center', textAlign: 'center' }}>
                <CircularProgress />
                <Typography variant="body1">
                  {mainDeviceNickname
                    ? `Wachten op ${mainDeviceNickname} om de teamgegevens in te vullen.`
                    : 'Wachten op de hoofdtelefoon van jullie team.'}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Zodra de gegevens zijn ingevuld krijg je hier automatisch de volgende stap.
                </Typography>
              </Stack>
            )}

            {step === 'permissions' && (
              <Stack spacing={2}>
                <Typography variant="h6">Meldingen toestaan</Typography>
                <Typography variant="body2" color="text.secondary">
                  Sta meldingen toe zodat je updates en berichten ontvangt tijdens de rally.
                </Typography>

                {notificationError && <Alert severity="warning">{notificationError}</Alert>}
                <Button
                  variant={notificationsDone ? 'outlined' : 'contained'}
                  size="large"
                  fullWidth
                  disabled={notificationsDone}
                  onClick={handleEnableNotifications}
                >
                  {notificationsDone ? 'Meldingen ingesteld' : 'Sta meldingen toe'}
                </Button>

                <Button
                  variant="contained"
                  color="primary"
                  size="large"
                  fullWidth
                  disabled={!notificationsDone}
                  onClick={handleFinishPermissions}
                >
                  Naar het dashboard
                </Button>
                <Button variant="text" size="small" fullWidth onClick={handleFinishPermissions}>
                  Overslaan en verder
                </Button>
              </Stack>
            )}
          </Stack>
        </Paper>
      </Box>
    </Container>
  );
}

export default CheckInPage;
