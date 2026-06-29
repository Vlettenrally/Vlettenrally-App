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
import { useTranslation } from 'react-i18next';
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
  Warning as WarningIcon,
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
  const { t } = useTranslation();
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
      setError(t('participant.checkin.errorLoadingGames') + ': ' + response.error);
      return;
    }
    const games = response.data ?? [];
    setTodayGames(games);
    if (games.length === 0) {
      setError(t('participant.checkin.noGamesToday'));
      return;
    }
    if (games.length === 1) {
      setSelectedGameId(games[0].id);
      setStep('select-team');
    } else {
      setStep('select-game');
    }
  }, [t]);

  const loadTeamsForGame = useCallback(async (gameId: string) => {
    setError(null);
    setFormError(null);
    const response = await api.getCheckInTeams(gameId);
    if (response.error) {
      setFormError(t('participant.checkin.errorLoadingTeams') + ': ' + response.error);
      return;
    }
    setTeamOptions(response.data ?? []);
  }, [t]);

  // Silent refresh used by the polling loop — does not clear errors so "needs rally desk"
  // messages stay visible while the team is waiting for physical check-in.
  const refreshTeamsForGame = useCallback(async (gameId: string) => {
    const response = await api.getCheckInTeams(gameId);
    if (!response.error && response.data) {
      setTeamOptions(response.data);
    }
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
            setError(t('participant.checkin.authFailed') + ': ' + tokenResponse.error);
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
            setError(t('participant.checkin.noTeamData'));
            return;
          }
        } catch (err) {
          console.error('Legacy token flow error:', err);
          setError(t('participant.checkin.loginError'));
          return;
        }
      }

      await loadTodayGames();
    };

    init().catch((err) => {
      console.error('Check-in init error:', err);
      setError(t('participant.checkin.generalError'));
    });
  }, [searchParams, loadTodayGames, t]);

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

  // Poll the team list every 5 s while on select-team so the physical check-in chip
  // updates automatically without a manual page reload.
  useEffect(() => {
    if (step !== 'select-team' || !selectedGameId) return;
    const interval = setInterval(() => { void refreshTeamsForGame(selectedGameId); }, POLL_STATE_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [step, selectedGameId, refreshTeamsForGame]);

  // When the polled team list arrives, sync selectedTeam so the chip flips to green
  // and the "needs rally desk" error clears as soon as physical check-in is recorded.
  useEffect(() => {
    if (!selectedTeam) return;
    const updated = teamOptions.find(t => t.id === selectedTeam.id);
    if (updated && updated.checkedIn !== selectedTeam.checkedIn) {
      setSelectedTeam(updated);
      if (updated.checkedIn) setFormError(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [teamOptions]); // intentionally omits selectedTeam to avoid a state-update loop

  const sortedTeamOptions = useMemo(() => {
    return [...teamOptions].sort((a, b) => (a.name ?? '').localeCompare(b.name ?? '', 'nl', { sensitivity: 'base' }));
  }, [teamOptions]);

  const handleGameSelection = (gameId: string) => {
    setSelectedGameId(gameId);
    setStep('select-team');
  };

  const handleTeamSelection = () => {
    if (!selectedTeam || !selectedGameId) {
      setFormError(t('participant.checkin.selectTeamFirst'));
      return;
    }

    // Validate that team has physically checked in at Rally Desk first
    if (!selectedTeam.checkedIn) {
      setFormError(t('participant.checkin.needsRallyDeskFull'));
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
      setFormError(t('participant.checkin.missingTeamData'));
      return;
    }
    const trimmed = nicknameInput.trim();
    if (trimmed.length === 0) {
      setFormError(t('participant.checkin.nicknameRequired'));
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
        setFormError(t('participant.checkin.registerFailed') + ' ' + (response.error ?? t('common.unknown')));
        return;
      }
      await applyRegisterResponse(response.data, resolvedTeam.gameId, resolvedTeam.id);
    } catch (err) {
      console.error('register-device error:', err);
      setFormError(t('participant.checkin.registerError'));
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
      setFormError(t('participant.checkin.emergencyPhoneRequired'));
      setSubmitting(false);
      return;
    }

    const invalidPhone = emergencyPhones.find((phone) => !isPhoneNumber(phone));
    if (invalidPhone) {
      setFormError(t('participant.checkin.invalidPhone', { phone: invalidPhone }));
      setSubmitting(false);
      return;
    }

    try {
      const updateParticipantsResponse = await api.updateParticipants(resolvedTeam.id, participants);
      if (updateParticipantsResponse.error) {
        setFormError(t('participant.checkin.saveParticipantsFailed') + ' ' + updateParticipantsResponse.error);
        return;
      }
      const updatePhonesResponse = await api.updateEmergencyPhones(resolvedTeam.id, emergencyPhones);
      if (updatePhonesResponse.error) {
        setFormError(t('participant.checkin.savePhonesFailed') + ' ' + updatePhonesResponse.error);
        return;
      }
      const checkedInTeam = await teamService.checkIn(resolvedTeam.id);
      if (!checkedInTeam) {
        setFormError(t('participant.checkin.checkinFailed'));
        return;
      }
      const teamResponse = await api.getMyTeam();
      if (teamResponse.data) {
        persistTeamData(teamResponse.data as PlayerTeam);
      }
      setStep(traccarSetup ? 'traccar-setup' : 'rivals');
    } catch (err) {
      console.error('details submit error:', err);
      setFormError(t('participant.checkin.checkinError'));
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
        setFormError(t('participant.checkin.loadRivalsFailed'));
      })
      .finally(() => {
        if (!cancelled) setLoadingRivals(false);
      });

    return () => {
      cancelled = true;
    };
  }, [step, resolvedTeam, t]);

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
      setFormError(t('participant.checkin.saveRivalsFailed') + ' ' + result.error);
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
            ? t('participant.checkin.notificationDenied')
            : result.reason === 'no-token'
              ? t('participant.checkin.notificationNoToken')
              : t('participant.checkin.notificationServerFailed');
        setNotificationError(message);
      }
    } catch (err) {
      console.error('Notification permission error:', err);
      setNotificationError(t('participant.checkin.notificationError'));
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
                    ? t('participant.checkin.descriptionInstallApp')
                    : step === 'permissions'
                    ? t('participant.checkin.descriptionPermissions')
                    : step === 'rivals'
                      ? t('participant.checkin.descriptionRivals')
                      : step === 'traccar-setup'
                        ? t('participant.checkin.descriptionTraccar')
                        : step === 'wait'
                          ? t('participant.checkin.descriptionWait')
                          : t('participant.checkin.descriptionDefault')}
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
                        alt={t('participant.checkin.logoAlt')}
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
                            {t('participant.checkin.welcomeSlide1Title')}
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
                          {t('participant.checkin.welcomeSlide1Subtitle')}
                        </Typography>
                        <Typography 
                          variant="body1" 
                          align="center" 
                          color="text.secondary"
                          sx={{ animation: 'slideInUp 0.8s ease-out 0.7s both' }}
                        >
                          {t('participant.checkin.slide1Body')}
                        </Typography>
                      </Box>

                      {/* Bootje onderaan - vaart over het scherm */}
                      <Box sx={{ width: '100%', position: 'relative', height: 120, overflow: 'visible', mb: 1 }}>
                        <Box
                          component="img"
                          src="/boat.svg"
                          alt={t('participant.checkin.boatAlt')}
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
                        {t('participant.checkin.swipeHint')}
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
                          {t('participant.checkin.slide2Title')}
                        </Typography>
                      </Box>
                      
                      <Box component="ul" sx={{ pl: 3, '& li': { mb: 1.5 } }}>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          {t('participant.checkin.slide2Bullet1')}
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          {t('participant.checkin.slide2Bullet2')}
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          {t('participant.checkin.slide2Bullet3')}
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          {t('participant.checkin.slide2Bullet4')}
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
                          {t('participant.checkin.finishTime')}
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
                          {t('participant.checkin.slide3Title')}
                        </Typography>
                      </Box>
                      
                      <Box component="ul" sx={{ pl: 3, '& li': { mb: 1.5 } }}>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          {t('participant.checkin.slide3Bullet1')}
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          {t('participant.checkin.slide3Bullet2')}
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          {t('participant.checkin.slide3Bullet3')}
                        </Typography>
                        <Typography component="li" variant="body1" sx={{ fontWeight: 500 }}>
                          {t('participant.checkin.slide3Bullet4')}
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
                          {t('participant.checkin.slide3Alert')}
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
                          {t('participant.checkin.readyToGo')}
                        </Typography>
                      </Box>
                      <Box
                        component="img"
                        src="/boat.svg"
                        alt={t('participant.checkin.boatAlt2')}
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
                        {t('participant.checkin.startCheckin')}
                      </Button>
                      <Typography variant="caption" color="text.secondary" align="center">
                        {t('participant.checkin.alreadyCheckedIn')}
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
                <Typography variant="h6">{t('participant.checkin.chooseRally')}</Typography>
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
                <Typography variant="h6">{t('participant.checkin.searchBoatNumber')}</Typography>
                <Autocomplete
                  options={sortedTeamOptions}
                  value={selectedTeam}
                  onChange={(_, value) => setSelectedTeam(value)}
                  getOptionLabel={(option) => option.name ?? ''}
                  renderOption={({ key, ...props }, option) => (
                    <Box key={key} component="li" {...props} sx={{ minHeight: 48, display: 'flex', alignItems: 'center', gap: 1 }}>
                      <Typography variant="body1" sx={{ flexGrow: 1 }}>{option.name}</Typography>
                      {!option.checkedIn && (
                        <Chip
                          size="small"
                          color="warning"
                          icon={<WarningIcon />}
                          label={t('participant.checkin.needsRallyDeskShort')}
                        />
                      )}
                      {option.checkedIn && <Chip size="small" color="success" label={t('participant.checkin.checkedIn')} />}
                    </Box>
                  )}
                  renderInput={(params) => (
                    <TextField {...params} label={t('participant.checkin.boatNumberPlaceholder')} fullWidth />
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
                  {t('common.next')}
                </Button>
              </Stack>
            )}

            {step === 'nickname' && (
              <Stack spacing={2}>
                <Typography variant="h6">{t('participant.checkin.deviceName')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('participant.checkin.deviceNameHint')}
                </Typography>
                <TextField
                  label={t('participant.checkin.deviceNameLabel')}
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
                  {submitting ? t('participant.checkin.loadingShort') : t('common.next')}
                </Button>
              </Stack>
            )}

            {step === 'details' && (
              <Stack spacing={2}>
                <Typography variant="h6">{t('participant.checkin.teamDetails')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('participant.checkin.teamDetails')}
                </Typography>
                <TextField
                  label={t('participant.checkin.participantNames')}
                  value={participantsText}
                  onChange={(e) => setParticipantsText(e.target.value)}
                  multiline
                  minRows={4}
                  fullWidth
                  helperText={t('participant.checkin.participantNamesHint')}
                />
                <TextField
                  label={t('participant.checkin.phonesOnBoard')}
                  value={emergencyPhonesText}
                  onChange={(e) => setEmergencyPhonesText(e.target.value)}
                  multiline
                  minRows={3}
                  fullWidth
                  helperText={t('participant.checkin.phonesOnBoardHint')}
                />
                {formError && <Alert severity="error">{formError}</Alert>}
                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={submitting}
                  onClick={handleDetailsSubmit}
                >
                  {submitting ? t('participant.checkin.checkingIn') : t('participant.checkin.checkinAction')}
                </Button>
              </Stack>
            )}

            {step === 'traccar-setup' && traccarSetup && (
              <Stack spacing={2}>
                <Typography variant="h6">{t('participant.checkin.traccarSetupTitle')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('participant.checkin.traccarDescription')}
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
                  {t('participant.checkin.traccarOpenButton')}
                </Button>
                <Typography variant="caption" color="text.secondary" sx={{ mt: -1 }}>
                  {t('participant.checkin.traccarOpenHint')}
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
                    {t('participant.checkin.traccarScanTitle')}
                  </Typography>
                  {traccarQr ? (
                    <Box
                      component="img"
                      src={traccarQr}
                      alt={t('participant.checkin.traccarQrAlt')}
                      sx={{ width: 240, height: 240, maxWidth: '100%' }}
                    />
                  ) : (
                    <CircularProgress />
                  )}
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                    {t('participant.checkin.traccarScanHint')}
                  </Typography>
                </Box>

                <Box>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    {t('participant.checkin.traccarManualHint')}
                  </Typography>
                  <Stack spacing={1.5} sx={{ mt: 1 }}>
                    <TextField
                      label={t('participant.checkin.traccarServerUrl')}
                      value={traccarSetup.serverUrl}
                      fullWidth
                      size="small"
                      slotProps={{ input: { readOnly: true } }}
                      onFocus={(e) => e.target.select()}
                    />
                    <TextField
                      label={t('participant.checkin.traccarDeviceIdLabel')}
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
                      {t('participant.checkin.traccarCopyDeviceId')}
                    </Button>
                  </Stack>
                </Box>

                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  onClick={() => setStep('rivals')}
                >
                  {t('participant.checkin.traccarDone')}
                </Button>
              </Stack>
            )}

            {step === 'rivals' && (
              <Stack spacing={2}>
                <Typography variant="h6">{t('participant.checkin.selectRivals')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('participant.checkin.selectRivalsDescription', { count: MAX_RIVALS })}
                </Typography>
                <Autocomplete
                  multiple
                  options={rivalOptions}
                  value={rivalSelection}
                  loading={loadingRivals}
                  onChange={(_, value) => {
                    if (value.length > MAX_RIVALS) {
                      setFormError(t('participant.checkin.maxRivalsError', { count: MAX_RIVALS }));
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
                    <TextField {...params} label={t('participant.checkin.searchBoatNumberShort')} placeholder={t('participant.checkin.startTyping')} />
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
                  {submitting ? t('participant.checkin.loadingShort') : t('common.next')}
                </Button>
                <Button
                  variant="text"
                  size="small"
                  fullWidth
                  disabled={submitting}
                  onClick={() => setStep('permissions')}
                >
                  {t('common.skip')}
                </Button>
              </Stack>
            )}

            {step === 'wait' && (
              <Stack spacing={2} sx={{ alignItems: 'center', textAlign: 'center' }}>
                <CircularProgress />
                <Typography variant="body1">
                  {mainDeviceNickname
                    ? t('participant.checkin.waitingForLeader', { name: mainDeviceNickname })
                    : t('participant.checkin.waitingForMainPhone')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('participant.checkin.waitingDetails')}
                </Typography>
              </Stack>
            )}

            {step === 'permissions' && (
              <Stack spacing={2}>
                <Typography variant="h6">{t('participant.checkin.allowNotifications')}</Typography>
                <Typography variant="body2" color="text.secondary">
                  {t('participant.checkin.notificationsDesc')}
                </Typography>

                {notificationError && <Alert severity="warning">{notificationError}</Alert>}
                <Button
                  variant={notificationsDone ? 'outlined' : 'contained'}
                  size="large"
                  fullWidth
                  disabled={notificationsDone}
                  onClick={handleEnableNotifications}
                >
                  {notificationsDone ? t('participant.checkin.notificationsSet') : t('participant.checkin.grantNotifications')}
                </Button>

                <Button
                  variant="contained"
                  color="primary"
                  size="large"
                  fullWidth
                  disabled={!notificationsDone}
                  onClick={handleFinishPermissions}
                >
                  {t('participant.checkin.toDashboard')}
                </Button>
                <Button variant="text" size="small" fullWidth onClick={handleFinishPermissions}>
                  {t('participant.checkin.skipAndContinue')}
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
