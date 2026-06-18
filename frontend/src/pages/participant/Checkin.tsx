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
import {
  Celebration as PartyIcon,
  Smartphone as PhoneIcon,
  Notifications as NotificationIcon,
  AccessTime as ClockIcon,
} from '@mui/icons-material';
import { Swiper, SwiperSlide } from 'swiper/react';
import { Pagination } from 'swiper/modules';
import 'swiper/css';
import 'swiper/css/pagination';
import '../../checkin.css';
import api from '../../services/api';
import { storage } from '../../services/storage';
import teamService from '../../services/teamService';
import { useTeamStore } from '../../store/useTeamStore';
import { useTeamWebSocket } from '../../hooks/useWebSocket';
import { useDeviceIdentity } from '../../hooks/useDeviceIdentity';
import { isPhoneNumber, validateParticipantNames } from '../../utils/validators';
import { initializePushNotificationsWithReason } from '../../services/firebaseMessaging';
import { shouldPromptHomescreenInstall } from '../../utils/pwa';
import { WebSocketEventType } from '../../services/websocket';
import { IOSInstallGuide } from '../../components/IOSInstallGuide';
import type {
  CheckInState,
  PlayerTeam,
  RegisterDeviceResponse,
  TeamOption,
  TodayGame,
  TokenExchangeResponse,
} from '../../types/entities';

type Step =
  | 'welcome'
  | 'install-app'
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

  // Runs the actual check-in entry: legacy token shortcut OR public flow.
  const beginCheckIn = useCallback(() => {
    setStep('loading');
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
  }, [searchParams, loadTodayGames]);

  // Entry: Show welcome screen for first-time users, then proceed to install-app or check-in.
  useEffect(() => {
    const hasSeenWelcome = localStorage.getItem('vlettenrally_welcome_seen');
    const isReturningUser = localStorage.getItem('vlettenrally_device_id');
    
    if (isReturningUser && hasSeenWelcome) {
      // Returning user: skip welcome
      if (shouldPromptHomescreenInstall()) {
        setStep('install-app');
      } else {
        beginCheckIn();
      }
    } else {
      // New user: show welcome
      setStep('welcome');
      localStorage.setItem('vlettenrally_welcome_seen', 'true');
    }
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
            {step !== 'welcome' && (
              <Box sx={{ textAlign: 'center' }}>
                <Typography variant="h4" component="h1" gutterBottom>
                  Vlettenrally
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {step === 'install-app'
                    ? 'Zet de app eerst op je beginscherm zodat je meldingen kunt ontvangen.'
                    : step === 'permissions'
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
            )}

            {error && <Alert severity="error">{error}</Alert>}

            {step === 'welcome' && (
              <Box sx={{ width: '100%', minHeight: 400 }}>
                <Swiper
                  modules={[Pagination]}
                  pagination={{ clickable: true }}
                  spaceBetween={20}
                  slidesPerView={1}
                  style={{ paddingBottom: '40px' }}
                >
                  {/* Slide 1: Welkom + Logo */}
                  <SwiperSlide>
                    <Stack spacing={2} alignItems="center" sx={{ py: 2, height: '100%', justifyContent: 'space-between' }}>
                      {/* Logo bovenaan */}
                      <Box
                        component="img"
                        src="/logo.svg"
                        alt="Vlettenrally logo"
                        sx={{
                          width: '85%',
                          maxWidth: 280,
                          mt: 1,
                          animation: 'logoEntrance 1.2s cubic-bezier(0.34, 1.56, 0.64, 1)',
                          '@keyframes logoEntrance': {
                            '0%': { opacity: 0, transform: 'scale(0.3) rotate(-10deg)' },
                            '100%': { opacity: 1, transform: 'scale(1) rotate(0deg)' },
                          },
                        }}
                      />
                      
                      {/* Tekst in het midden - groter */}
                      <Box sx={{ textAlign: 'center', px: 2 }}>
                        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1, mb: 2, animation: 'slideInLeft 0.8s ease-out 0.3s both' }}>
                          <Typography variant="h4" sx={{ fontWeight: 700, color: '#165656' }}>
                            Welkom!
                          </Typography>
                        </Box>
                        <Typography 
                          variant="h6" 
                          align="center" 
                          sx={{ 
                            fontWeight: 500,
                            color: '#165656',
                            mb: 1.5,
                            animation: 'slideInRight 0.8s ease-out 0.5s both' 
                          }}
                        >
                          Tijd om je lelievlet op te tuigen!
                        </Typography>
                        <Typography 
                          variant="body1" 
                          align="center" 
                          color="text.secondary"
                          sx={{ animation: 'slideInUp 0.8s ease-out 0.7s both' }}
                        >
                          Vaart jouw boot verder dan de rest?
                        </Typography>
                      </Box>

                      {/* Bootje onderaan - vaart over het scherm */}
                      <Box sx={{ width: '100%', position: 'relative', height: 120, overflow: 'visible', mb: 1 }}>
                        <Box
                          component="img"
                          src="/boat.svg"
                          alt="Vlettenrally boot"
                          sx={{
                            width: 100,
                            height: 100,
                            position: 'absolute',
                            bottom: 10,
                            animation: 'sailAcross 10s ease-in-out infinite',
                            '@keyframes sailAcross': {
                              '0%': { left: '-120px', transform: 'translateY(0px) rotate(-3deg)' },
                              '50%': { left: 'calc(50% - 50px)', transform: 'translateY(-8px) rotate(0deg)' },
                              '100%': { left: 'calc(100% + 20px)', transform: 'translateY(0px) rotate(3deg)' },
                            },
                          }}
                        />
                      </Box>

                      {/* Swipe hint */}
                      <Typography 
                        variant="caption" 
                        color="text.secondary" 
                        sx={{ 
                          animation: 'pulse 2s ease-in-out infinite',
                          pb: 1,
                        }}
                      >
                        Swipe voor meer →
                      </Typography>
                    </Stack>
                  </SwiperSlide>

                  {/* Slide 2: Het Spel */}
                  <SwiperSlide>
                    <Stack spacing={3} sx={{ py: 2, height: '100%', justifyContent: 'center' }}>
                      <Box sx={{ textAlign: 'center' }}>
                        {/* Gestileerde kaart met pulserende checkpoints */}
                        <Box sx={{ 
                          position: 'relative', 
                          width: '100%', 
                          maxWidth: 300, 
                          height: 200,
                          mx: 'auto',
                          mb: 2,
                        }}>
                          {/* Kaart achtergrond */}
                          <Box sx={{
                            width: '100%',
                            height: '100%',
                            borderRadius: 2,
                            border: '3px solid #165656',
                            position: 'relative',
                            overflow: 'hidden',
                            boxShadow: '0 6px 16px rgba(22, 86, 86, 0.25)',
                            background: '#e8f5e9',
                          }}>
                            {/* SVG voor land en water (Wolderwijd stijl) */}
                            <svg 
                              viewBox="0 0 300 200" 
                              style={{ 
                                position: 'absolute', 
                                top: 0, 
                                left: 0, 
                                width: '100%', 
                                height: '100%',
                              }}
                            >
                              <defs>
                                <linearGradient id="waterGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                                  <stop offset="0%" style={{ stopColor: '#90caf9', stopOpacity: 1 }} />
                                  <stop offset="50%" style={{ stopColor: '#64b5f6', stopOpacity: 1 }} />
                                  <stop offset="100%" style={{ stopColor: '#42a5f5', stopOpacity: 1 }} />
                                </linearGradient>
                                <linearGradient id="landGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                                  <stop offset="0%" style={{ stopColor: '#c8e6c9', stopOpacity: 1 }} />
                                  <stop offset="100%" style={{ stopColor: '#a5d6a7', stopOpacity: 1 }} />
                                </linearGradient>
                              </defs>
                              
                              {/* Land achtergrond (hele kaart) */}
                              <rect x="0" y="0" width="300" height="200" fill="url(#landGradient)" />
                              
                              {/* Centraal watervlak (Wolderwijd vorm) - verticaal langwerpig */}
                              <path 
                                d="M 80,10 
                                   Q 60,15 55,30 
                                   L 45,60 
                                   Q 40,80 45,100 
                                   L 50,130 
                                   Q 55,150 60,165 
                                   L 70,185 
                                   Q 80,195 100,195 
                                   L 130,190 
                                   Q 150,188 165,185 
                                   L 190,175 
                                   Q 210,165 220,150 
                                   L 235,120 
                                   Q 245,95 240,70 
                                   L 230,40 
                                   Q 220,20 200,15 
                                   L 170,10 
                                   Q 140,8 110,10 
                                   Z" 
                                fill="url(#waterGradient)"
                              />
                              
                              {/* Kustlijn accent */}
                              <path 
                                d="M 80,10 Q 60,15 55,30 L 45,60 Q 40,80 45,100 L 50,130 Q 55,150 60,165 L 70,185 Q 80,195 100,195 L 130,190 Q 150,188 165,185 L 190,175 Q 210,165 220,150 L 235,120 Q 245,95 240,70 L 230,40 Q 220,20 200,15 L 170,10 Q 140,8 110,10 Z" 
                                stroke="#81c784"
                                strokeWidth="2"
                                fill="none"
                                opacity="0.5"
                              />
                              
                              {/* Water rimpelingen */}
                              <circle cx="120" cy="80" r="15" fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1">
                                <animate attributeName="r" values="10;20;10" dur="5s" repeatCount="indefinite"/>
                                <animate attributeName="opacity" values="0.4;0;0.4" dur="5s" repeatCount="indefinite"/>
                              </circle>
                              <circle cx="160" cy="120" r="12" fill="none" stroke="rgba(255,255,255,0.4)" strokeWidth="1">
                                <animate attributeName="r" values="8;18;8" dur="6s" repeatCount="indefinite"/>
                                <animate attributeName="opacity" values="0.4;0;0.4" dur="6s" repeatCount="indefinite"/>
                              </circle>
                              
                              {/* Groeiende route lijn door het water - langs checkpoints */}
                              <path 
                                d="M 70,170 Q 90,150 110,135 L 135,110 Q 145,95 155,75 L 160,60" 
                                stroke="#165656" 
                                strokeWidth="3"
                                fill="none"
                                strokeDasharray="10,6"
                                strokeDashoffset="500"
                                opacity="0.6"
                              >
                                <animate 
                                  attributeName="stroke-dashoffset" 
                                  from="500" 
                                  to="0" 
                                  dur="12s" 
                                  repeatCount="indefinite"
                                />
                              </path>
                            </svg>

                            {/* Rode checkpoint 1 (op water, onderaan) */}
                            <Box sx={{
                              position: 'absolute',
                              top: '85%', left: '23%',
                              width: 16, height: 16,
                              borderRadius: '50%',
                              bgcolor: '#f44336',
                              border: '2px solid white',
                              boxShadow: '0 0 0 0 rgba(244, 67, 54, 0.7), 0 2px 4px rgba(0,0,0,0.2)',
                              animation: 'checkpoint-pulse 2s infinite',
                              zIndex: 4,
                              '@keyframes checkpoint-pulse': {
                                '0%, 100%': { 
                                  boxShadow: '0 0 0 0 rgba(244, 67, 54, 0.7), 0 2px 4px rgba(0,0,0,0.2)',
                                  transform: 'scale(1)',
                                },
                                '50%': { 
                                  boxShadow: '0 0 0 10px rgba(244, 67, 54, 0), 0 2px 8px rgba(0,0,0,0.3)',
                                  transform: 'scale(1.15)',
                                },
                              },
                            }} />

                            {/* Rode checkpoint 2 (op kustlijn, links) */}
                            <Box sx={{
                              position: 'absolute',
                              top: '50%', left: '15%',
                              width: 16, height: 16,
                              borderRadius: '50%',
                              bgcolor: '#f44336',
                              border: '2px solid white',
                              boxShadow: '0 0 0 0 rgba(244, 67, 54, 0.7), 0 2px 4px rgba(0,0,0,0.2)',
                              animation: 'checkpoint-pulse 2s infinite 0.3s',
                              zIndex: 4,
                            }} />

                            {/* Rode checkpoint 3 (op water, midden) */}
                            <Box sx={{
                              position: 'absolute',
                              top: '55%', left: '45%',
                              width: 16, height: 16,
                              borderRadius: '50%',
                              bgcolor: '#f44336',
                              border: '2px solid white',
                              boxShadow: '0 0 0 0 rgba(244, 67, 54, 0.7), 0 2px 4px rgba(0,0,0,0.2)',
                              animation: 'checkpoint-pulse 2s infinite 0.6s',
                              zIndex: 4,
                            }} />

                            {/* Rode checkpoint 4 (op kustlijn, rechts) */}
                            <Box sx={{
                              position: 'absolute',
                              top: '60%', left: '73%',
                              width: 16, height: 16,
                              borderRadius: '50%',
                              bgcolor: '#f44336',
                              border: '2px solid white',
                              boxShadow: '0 0 0 0 rgba(244, 67, 54, 0.7), 0 2px 4px rgba(0,0,0,0.2)',
                              animation: 'checkpoint-pulse 2s infinite 0.9s',
                              zIndex: 4,
                            }} />

                            {/* Gouden super-checkpoint (op water, boven) */}
                            <Box sx={{
                              position: 'absolute',
                              top: '30%', left: '53%',
                              width: 20, height: 20,
                              borderRadius: '50%',
                              bgcolor: '#ffcb0f',
                              border: '2px solid white',
                              boxShadow: '0 0 0 0 rgba(255, 203, 15, 0.8), 0 3px 6px rgba(0,0,0,0.25)',
                              animation: 'checkpoint-pulse 2s infinite 1.2s',
                              zIndex: 4,
                              '&::before': {
                                content: '""',
                                position: 'absolute',
                                top: '50%',
                                left: '50%',
                                transform: 'translate(-50%, -50%)',
                                width: 8,
                                height: 8,
                                borderRadius: '50%',
                                bgcolor: 'white',
                              },
                            }} />

                            {/* Rode checkpoint 5 (op kustlijn, boven) */}
                            <Box sx={{
                              position: 'absolute',
                              top: '15%', left: '67%',
                              width: 16, height: 16,
                              borderRadius: '50%',
                              bgcolor: '#f44336',
                              border: '2px solid white',
                              boxShadow: '0 0 0 0 rgba(244, 67, 54, 0.7), 0 2px 4px rgba(0,0,0,0.2)',
                              animation: 'checkpoint-pulse 2s infinite 1.5s',
                              zIndex: 4,
                            }} />
                          </Box>
                        </Box>

                        <Typography variant="h4" align="center" sx={{ fontWeight: 700, color: '#165656', mb: 3 }}>
                          Ontdek & Win Punten!
                        </Typography>
                      </Box>
                      
                      <Box component="ul" sx={{ pl: 3, '& li': { mb: 1.5 } }}>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          Vaar zoveel mogelijk kilometers
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          Vind checkpoints op de kaart
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          Doe mee met pitstop activiteiten
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          Zoek geheime super-checkpoints
                        </Typography>
                      </Box>

                      <Alert 
                        severity="warning" 
                        icon={<ClockIcon />}
                        sx={{ 
                          mt: 2,
                          '& .MuiAlert-message': {
                            width: '100%',
                            textAlign: 'center',
                          }
                        }}
                      >
                        <Typography variant="body1" sx={{ fontWeight: 600 }}>
                          Finish voor 23:00u bij de Rallydesk!
                        </Typography>
                      </Alert>
                    </Stack>
                  </SwiperSlide>

                  {/* Slide 3: Online Volgen */}
                  <SwiperSlide>
                    <Stack spacing={3} sx={{ py: 2, height: '100%', justifyContent: 'center' }}>
                      <Box sx={{ textAlign: 'center' }}>
                        <PhoneIcon sx={{ color: '#165656', fontSize: 48, mb: 1 }} />
                        <Typography variant="h4" align="center" sx={{ fontWeight: 700, color: '#165656', mb: 3 }}>
                          Volg Alles Live!
                        </Typography>
                      </Box>
                      
                      <Box component="ul" sx={{ pl: 3, '& li': { mb: 1.5 } }}>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          Zie je punten en positie
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          Ontvang berichten van de Rallydesk
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          Check je rivalen
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          GPS-tracker volgt je route
                        </Typography>
                      </Box>

                      <Alert 
                        severity="info" 
                        icon={<NotificationIcon />}
                        sx={{ 
                          mt: 2,
                          '& .MuiAlert-message': {
                            width: '100%',
                            textAlign: 'center',
                          }
                        }}
                      >
                        <Typography variant="body1" sx={{ fontWeight: 600 }}>
                          Blijf online tijdens de rally voor live updates!
                        </Typography>
                      </Alert>
                    </Stack>
                  </SwiperSlide>

                  {/* Slide 4: Call-to-Action */}
                  <SwiperSlide>
                    <Stack spacing={3} alignItems="center" sx={{ py: 2, height: '100%', justifyContent: 'center' }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <PartyIcon sx={{ color: '#ffcb0f', fontSize: 40 }} />
                        <Typography variant="h4" align="center" sx={{ fontWeight: 700, color: '#165656' }}>
                          Klaar voor vertrek?
                        </Typography>
                      </Box>
                      <Box
                        component="img"
                        src="/boat.svg"
                        alt="Boot"
                        sx={{
                          width: 140,
                          height: 140,
                          animation: 'bounce 1s ease-in-out infinite',
                          '@keyframes bounce': {
                            '0%, 100%': { transform: 'translateY(0)' },
                            '50%': { transform: 'translateY(-15px)' },
                          },
                        }}
                      />
                      <Button
                        variant="contained"
                        size="large"
                        fullWidth
                        onClick={() => {
                          if (shouldPromptHomescreenInstall()) {
                            setStep('install-app');
                          } else {
                            beginCheckIn();
                          }
                        }}
                        sx={{
                          py: 2,
                          fontSize: '1.1rem',
                          fontWeight: 700,
                          background: 'linear-gradient(135deg, #165656 0%, #2a7a7a 100%)',
                          boxShadow: '0 4px 15px rgba(22, 86, 86, 0.4)',
                          '&:hover': {
                            background: 'linear-gradient(135deg, #0d3333 0%, #165656 100%)',
                            boxShadow: '0 6px 20px rgba(22, 86, 86, 0.6)',
                          },
                        }}
                      >
                        Start Inchecken! 🚀
                      </Button>
                      <Typography variant="caption" color="text.secondary" align="center">
                        Heb je al ingecheckt? Log opnieuw in via deze pagina.
                      </Typography>
                    </Stack>
                  </SwiperSlide>
                </Swiper>
              </Box>
            )}

            {step === 'install-app' && (
              <IOSInstallGuide onComplete={beginCheckIn} />
            )}

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
