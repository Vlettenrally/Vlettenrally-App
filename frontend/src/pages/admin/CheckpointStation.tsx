import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Snackbar,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import {
  Add as AddIcon,
  CheckCircle as VerifyIcon,
  Cancel as RejectIcon,
  Refresh as RefreshIcon,
  InfoOutlined as InfoIcon,
} from '@mui/icons-material';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGameStore } from '../../store/useGameStore';
import { teamService } from '../../services/teamService';
import { websocket } from '../../services/websocket';
import api from '../../services/api';
import { teamMatchesQuery } from '../../utils/teamSearch';
import type { WebSocketMessage } from '../../services/websocket';
import type { AdminTeam, CheckpointTeamStatus } from '../../types/entities';
import type { Checkpoint } from '../../types/entities.ts';

interface CheckpointDetectionPayload {
  checkpointId: string;
  teamName: string;
}

const POLLING_INTERVAL_MS = 30_000;
const HEARTBEAT_INTERVAL_MS = 10_000;

export default function CheckpointStation() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);
  const checkpoints = useGameStore((s) => s.checkpoints);
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const [selectedCheckpointId, setSelectedCheckpointId] = useState<string>('');
  const [teams, setTeams] = useState<CheckpointTeamStatus[]>([]);
  const [allTeams, setAllTeams] = useState<AdminTeam[]>([]);
  const [loading, setLoading] = useState(false);

  const [verifyTarget, setVerifyTarget] = useState<CheckpointTeamStatus | null>(null);
  const [bonusPoints, setBonusPoints] = useState<number | ''>('');
  const [verifyNotes, setVerifyNotes] = useState('');
  const [verifying, setVerifying] = useState(false);

  const [rejectTarget, setRejectTarget] = useState<CheckpointTeamStatus | null>(null);
  const [rejectNotes, setRejectNotes] = useState('');
  const [rejecting, setRejecting] = useState(false);

  const [bonusTarget, setBonusTarget] = useState<CheckpointTeamStatus | null>(null);
  const [bonusDelta, setBonusDelta] = useState<number | ''>('');
  const [adjustingBonus, setAdjustingBonus] = useState(false);

  const [addDialogOpen, setAddDialogOpen] = useState(false);
  const [addTeam, setAddTeam] = useState<AdminTeam | null>(null);
  const [detecting, setDetecting] = useState(false);

  const [snackbar, setSnackbar] = useState<{ message: string; severity: 'success' | 'error' | 'info' } | null>(null);

  const lastEventAt = useRef<number>(0);

  const [prevGameId, setPrevGameId] = useState(selectedGameId);
  if (prevGameId !== selectedGameId) {
    setPrevGameId(selectedGameId);
    setSelectedCheckpointId('');
  }
  const [prevCheckpointId, setPrevCheckpointId] = useState(selectedCheckpointId);
  if (prevCheckpointId !== selectedCheckpointId) {
    setPrevCheckpointId(selectedCheckpointId);
    if (!selectedCheckpointId) setTeams([]);
  }

  const selectedCheckpoint: Checkpoint | undefined = checkpoints.find((c) => c.id === selectedCheckpointId);

  const fetchTeams = useCallback(async () => {
    if (!selectedGameId || !selectedCheckpointId) return;
    const res = await api.getTeamsNearCheckpoint(selectedGameId, selectedCheckpointId);
    if (res.data) setTeams(res.data);
  }, [selectedGameId, selectedCheckpointId]);

  const handleRefresh = useCallback(async () => {
    if (!selectedCheckpointId) return;
    setLoading(true);
    try {
      await fetchTeams();
    } finally {
      setLoading(false);
    }
  }, [selectedCheckpointId, fetchTeams]);

  useEffect(() => {
    if (!selectedGameId) return;
    if (checkpoints.length === 0) {
      api.getCheckpoints(selectedGameId).then((res) => {
        if (res.data) useGameStore.getState().setCheckpoints(res.data as unknown as Checkpoint[]);
      });
    }
    teamService.getTeamsByGame(selectedGameId).then(setAllTeams);
  }, [selectedGameId, checkpoints.length]);

  useEffect(() => {
    if (selectedCheckpointId) fetchTeams();
  }, [selectedCheckpointId, fetchTeams]);

  useEffect(() => {
    if (!selectedCheckpointId) return;

    const handler = (msg: WebSocketMessage) => {
      const payload = msg.payload as CheckpointDetectionPayload | undefined;
      if (payload?.checkpointId !== selectedCheckpointId) return;
      lastEventAt.current = Date.now();
      fetchTeams();
      setSnackbar({
        message: t('admin.checkpointStation.detectionSnackbar', { teamName: payload.teamName }),
        severity: 'info',
      });
    };

    return websocket.on('CHECKPOINT_DETECTION', handler);
  }, [selectedCheckpointId, fetchTeams, t]);

  useEffect(() => {
    if (!selectedCheckpointId) return;
    const interval = setInterval(() => {
      if (Date.now() - lastEventAt.current >= POLLING_INTERVAL_MS) fetchTeams();
    }, POLLING_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [selectedCheckpointId, fetchTeams]);

  useEffect(() => {
    if (!selectedCheckpointId) return;
    websocket.send({ type: 'CHECKPOINT_HEARTBEAT', checkpointId: selectedCheckpointId });
    const interval = setInterval(() => {
      websocket.send({ type: 'CHECKPOINT_HEARTBEAT', checkpointId: selectedCheckpointId });
    }, HEARTBEAT_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [selectedCheckpointId]);

  useEffect(() => {
    if (!selectedCheckpointId) return;
    websocket.send({ type: 'CHECKPOINT_SUBSCRIBE', checkpointId: selectedCheckpointId });
    return () => {
      websocket.send({ type: 'CHECKPOINT_UNSUBSCRIBE', checkpointId: selectedCheckpointId });
    };
  }, [selectedCheckpointId]);

  const openVerify = (team: CheckpointTeamStatus) => {
    setVerifyTarget(team);
    setBonusPoints('');
    setVerifyNotes('');
  };

  const handleVerify = async () => {
    if (!verifyTarget) return;
    setVerifying(true);

    let achievementId = verifyTarget.achievementId;
    if (!achievementId) {
      const detected = await api.detectProximity(verifyTarget.teamId, selectedCheckpointId);
      if (!detected.data) {
        setSnackbar({
          message: detected.error ?? t('admin.checkpointStation.verifyCannotRegister'),
          severity: 'error',
        });
        setVerifying(false);
        return;
      }
      achievementId = detected.data.id;
    }

    const res = await api.verifyAchievement(achievementId, verifyNotes || undefined, bonusPoints !== '' ? bonusPoints : undefined);
    setVerifying(false);
    if (res.data) {
      const total = res.data.pointsEarned ?? 0;
      const bonus = res.data.bonusPoints;
      const base = total - bonus;
      const message = bonus > 0
        ? t('admin.checkpointStation.verifiedWithBonus', { teamName: verifyTarget.teamName, total, base, bonus })
        : t('admin.checkpointStation.verifiedMessage', { teamName: verifyTarget.teamName, points: total });
      setSnackbar({ message, severity: 'success' });
      setVerifyTarget(null);
      fetchTeams();
    } else {
      setSnackbar({
        message: res.error ?? t('admin.checkpointStation.verifyFailed'),
        severity: 'error',
      });
    }
  };

  const openReject = (team: CheckpointTeamStatus) => {
    setRejectTarget(team);
    setRejectNotes('');
  };

  const handleReject = async () => {
    if (!rejectTarget || !rejectNotes.trim()) return;
    setRejecting(true);

    let achievementId = rejectTarget.achievementId;
    if (!achievementId) {
      const detected = await api.detectProximity(rejectTarget.teamId, selectedCheckpointId);
      if (!detected.data) {
        setSnackbar({
          message: detected.error ?? t('admin.checkpointStation.rejectCannotRegister'),
          severity: 'error',
        });
        setRejecting(false);
        return;
      }
      achievementId = detected.data.id;
    }

    const res = await api.rejectAchievement(achievementId, rejectNotes);
    setRejecting(false);
    if (res.data) {
      setSnackbar({
        message: t('admin.checkpointStation.rejectedMessage', { teamName: rejectTarget.teamName }),
        severity: 'success',
      });
      setRejectTarget(null);
      fetchTeams();
    } else {
      setSnackbar({
        message: res.error ?? t('admin.checkpointStation.rejectFailed'),
        severity: 'error',
      });
    }
  };

  const openAdjustBonus = (team: CheckpointTeamStatus) => {
    setBonusTarget(team);
    setBonusDelta('');
  };

  const handleAdjustBonus = async () => {
    if (!bonusTarget || !bonusTarget.achievementId || bonusDelta === '' || bonusDelta === 0) return;
    setAdjustingBonus(true);
    const res = await api.adjustBonusPoints(bonusTarget.achievementId, bonusDelta);
    setAdjustingBonus(false);
    if (res.data) {
      const delta = `${bonusDelta > 0 ? '+' : ''}${bonusDelta}`;
      setSnackbar({
        message: t('admin.checkpointStation.bonusSuccess', { teamName: bonusTarget.teamName, delta }),
        severity: 'success',
      });
      setBonusTarget(null);
      fetchTeams();
    } else {
      setSnackbar({
        message: res.error ?? t('admin.checkpointStation.bonusFailed'),
        severity: 'error',
      });
    }
  };

  const handleManualAdd = async () => {
    if (!addTeam) return;
    setDetecting(true);

    const res = await api.detectProximity(addTeam.id, selectedCheckpointId);

    if (res.data) {
      // A manual entry is an explicit operator confirmation, so verify it
      // straight away rather than leaving a PENDING hit for a second review.
      const verified = await api.verifyAchievement(res.data.id);
      setDetecting(false);
      setAddDialogOpen(false);
      setAddTeam(null);
      await fetchTeams();
      if (verified.data) {
        const total = verified.data.pointsEarned ?? 0;
        setSnackbar({
          message: t('admin.checkpointStation.verifiedMessage', { teamName: addTeam.name, points: total }),
          severity: 'success',
        });
      } else {
        setSnackbar({
          message: verified.error ?? t('admin.checkpointStation.verifyFailed'),
          severity: 'error',
        });
      }
      return;
    }

    setDetecting(false);
    if (res.status === 409) {
      setAddDialogOpen(false);
      setAddTeam(null);
      await fetchTeams();
      setSnackbar({
        message: t('admin.checkpointStation.addAlreadyRegistered'),
        severity: 'info',
      });
    } else {
      setSnackbar({
        message: res.error ?? t('admin.checkpointStation.addFailed'),
        severity: 'error',
      });
    }
  };

  const statusChip = (team: CheckpointTeamStatus) => {
    const status = team.achievementStatus;
    if (status === 'PENDING') return <Chip label={t('admin.checkpointStation.statusPending')} color="warning" size="small" />;
    if (status === 'VERIFIED') return <Chip label={t('admin.checkpointStation.statusVerified')} color="success" size="small" />;
    if (status === 'REJECTED') {
      return (
        <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
          <Chip label={t('admin.checkpointStation.statusRejected')} color="error" size="small" />
          {team.notes && (
            <Tooltip title={team.notes} arrow>
              <InfoIcon fontSize="small" color="action" sx={{ cursor: 'help' }} />
            </Tooltip>
          )}
        </Box>
      );
    }
    return <Chip label={t('admin.checkpointStation.statusNearby')} color="default" size="small" variant="outlined" />;
  };

  const formatTime = (iso: string | null) =>
    iso ? new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }) : '—';

  const actionable = (status: CheckpointTeamStatus['achievementStatus']) =>
    status !== 'VERIFIED' && status !== 'REJECTED';

  const oneHourAgo = Date.now() - 60 * 60 * 1000;
  const sortedVerified = teams
    .filter((team) => team.achievementStatus === 'VERIFIED')
    .sort((a, b) => (b.verifiedAt ?? '').localeCompare(a.verifiedAt ?? ''));

  const recentVerifiedIds = new Set(
    sortedVerified
      .filter(
        (team, index) =>
          index < 20 ||
          (team.verifiedAt != null && new Date(team.verifiedAt).getTime() >= oneHourAgo),
      )
      .map((team) => team.teamId),
  );
  const visibleTeams = teams.filter(
    (team) => team.achievementStatus !== 'VERIFIED' || recentVerifiedIds.has(team.teamId),
  );

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 4, flexWrap: 'wrap', gap: 2 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          {t('admin.checkpointStation.title')}
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <FormControl sx={{ minWidth: 220 }} size="small">
            <InputLabel>Checkpoint</InputLabel>
            <Select
              value={selectedCheckpointId}
              label="Checkpoint"
              onChange={(e) => setSelectedCheckpointId(e.target.value)}
            >
              <MenuItem value=""><em>{t('admin.checkpointStation.chooseCheckpoint')}</em></MenuItem>
              {checkpoints.map((cp) => (
                <MenuItem key={cp.id} value={cp.id}>{cp.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Tooltip title={t('admin.checkpointStation.refreshTooltip')}>
            <span>
              <IconButton onClick={handleRefresh} disabled={!selectedCheckpointId || loading} size="small">
                {loading ? <CircularProgress size={18} /> : <RefreshIcon />}
              </IconButton>
            </span>
          </Tooltip>
          {selectedCheckpointId && (
            isMobile ? (
              <Tooltip title={t('admin.checkpointStation.addTeam')}>
                <IconButton size="small" onClick={() => { setAddTeam(null); setAddDialogOpen(true); }}>
                  <AddIcon />
                </IconButton>
              </Tooltip>
            ) : (
              <Button
                variant="outlined"
                startIcon={<AddIcon />}
                size="small"
                onClick={() => { setAddTeam(null); setAddDialogOpen(true); }}
              >
                {t('admin.checkpointStation.addTeam')}
              </Button>
            )
          )}
        </Box>
      </Box>

      {!selectedGameId && (
        <Alert severity="info" sx={{ mb: 3 }}>{t('admin.checkpointStation.selectGame')}</Alert>
      )}

      {selectedGameId && !selectedCheckpointId && (
        <Alert severity="info" sx={{ mb: 3 }}>{t('admin.checkpointStation.selectCheckpoint')}</Alert>
      )}

      {selectedCheckpointId && (
        <>
          {selectedCheckpoint && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              {t('admin.checkpointStation.defaultPoints', { points: selectedCheckpoint.points })}
            </Typography>
          )}

          <Card>
            <CardContent sx={{ p: 0, '&:last-child': { pb: 0 } }}>
              {isMobile ? (
                <Stack divider={<Divider />}>
                  {visibleTeams.length === 0 && !loading && (
                    <Box sx={{ p: 3, textAlign: 'center', color: 'text.secondary' }}>
                      {t('admin.checkpointStation.noTeamsDetected')}
                    </Box>
                  )}
                  {visibleTeams.map((team) => (
                    <Box key={team.teamId} sx={{ p: 2 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.5 }}>
                        <Typography variant="subtitle1" fontWeight="medium">{team.teamName}</Typography>
                        {statusChip(team)}
                      </Box>
                      <Typography variant="body2" color="text.secondary" sx={{ mb: actionable(team.achievementStatus) ? 1.5 : 0 }}>
                        {t('admin.checkpointStation.detected', { time: formatTime(team.detectedAt) })}
                      </Typography>
                      {actionable(team.achievementStatus) && (
                        <Box sx={{ display: 'flex', gap: 1 }}>
                          <Button
                            variant="contained"
                            color="success"
                            size="small"
                            startIcon={<VerifyIcon />}
                            onClick={() => openVerify(team)}
                            sx={{ flex: 1 }}
                          >
                            {t('admin.checkpointStation.verify')}
                          </Button>
                          <Button
                            variant="outlined"
                            color="error"
                            size="small"
                            startIcon={<RejectIcon />}
                            onClick={() => openReject(team)}
                            sx={{ flex: 1 }}
                          >
                            {t('admin.checkpointStation.reject')}
                          </Button>
                        </Box>
                      )}
                      {team.achievementStatus === 'VERIFIED' && team.achievementId && (
                        <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
                          <Button
                            variant="outlined"
                            size="small"
                            onClick={() => openAdjustBonus(team)}
                            sx={{ flex: 1 }}
                          >
                            {t('admin.checkpointStation.bonusPointsAdjust')}
                          </Button>
                        </Box>
                      )}
                    </Box>
                  ))}
                </Stack>
              ) : (
                <TableContainer>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell><strong>{t('admin.checkpointStation.columnTeam')}</strong></TableCell>
                        <TableCell><strong>{t('admin.checkpointStation.columnDetected')}</strong></TableCell>
                        <TableCell><strong>{t('admin.checkpointStation.columnStatus')}</strong></TableCell>
                        <TableCell align="right"><strong>{t('admin.checkpointStation.columnAction')}</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {visibleTeams.length === 0 && !loading && (
                        <TableRow>
                          <TableCell colSpan={4} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                            {t('admin.checkpointStation.noTeamsDetected')}
                          </TableCell>
                        </TableRow>
                      )}
                      {visibleTeams.map((team) => (
                        <TableRow key={team.teamId} hover>
                          <TableCell>{team.teamName}</TableCell>
                          <TableCell>{formatTime(team.detectedAt)}</TableCell>
                          <TableCell>{statusChip(team)}</TableCell>
                          <TableCell align="right">
                            {actionable(team.achievementStatus) && (
                              <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
                                <Button
                                  size="small"
                                  variant="contained"
                                  color="primary"
                                  startIcon={<VerifyIcon />}
                                  onClick={() => openVerify(team)}
                                >
                                  {t('admin.checkpointStation.verify')}
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="error"
                                  startIcon={<RejectIcon />}
                                  onClick={() => openReject(team)}
                                >
                                  {t('admin.checkpointStation.reject')}
                                </Button>
                              </Box>
                            )}
                            {team.achievementStatus === 'VERIFIED' && team.achievementId && (
                              <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: actionable(team.achievementStatus) ? 1 : 0 }}>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  onClick={() => openAdjustBonus(team)}
                                >
                                  {t('admin.checkpointStation.bonusPointsAdjust')}
                                </Button>
                              </Box>
                            )}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </CardContent>
          </Card>
        </>
      )}

      {/* Verify dialog */}
      <Dialog open={!!verifyTarget} onClose={() => setVerifyTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('admin.checkpointStation.verifyDialogTitle', { teamName: verifyTarget?.teamName })}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
          <TextField
            label={t('admin.checkpointStation.verifyNotes')}
            value={verifyNotes}
            onChange={(e) => setVerifyNotes(e.target.value)}
            multiline
            rows={2}
            fullWidth
            size="small"
          />
          <TextField
            label={t('admin.checkpointStation.verifyBonusPoints')}
            type="number"
            value={bonusPoints}
            onChange={(e) => {
              const val = e.target.value;
              if (val === '') {
                setBonusPoints('');
              } else {
                const n = parseInt(val);
                if (!isNaN(n) && n >= 0) setBonusPoints(n);
              }
            }}
            slotProps={{ htmlInput: { min: 0 }}}
            size="small"
            fullWidth
            helperText={
              selectedCheckpoint
                ? t('admin.checkpointStation.verifyBonusTotals', {
                    base: selectedCheckpoint.points,
                    bonus: bonusPoints === '' ? 0 : bonusPoints,
                    total: selectedCheckpoint.points + (bonusPoints === '' ? 0 : bonusPoints),
                  })
                : undefined
            }
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVerifyTarget(null)} disabled={verifying}>{t('common.cancel')}</Button>
          <Button variant="contained" color="primary" onClick={handleVerify} disabled={verifying}>
            {verifying ? <CircularProgress size={18} /> : t('admin.checkpointStation.verifyAction')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reject dialog */}
      <Dialog open={!!rejectTarget} onClose={() => setRejectTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('admin.checkpointStation.rejectDialogTitle', { teamName: rejectTarget?.teamName })}</DialogTitle>
        <DialogContent sx={{ pt: '16px !important' }}>
          <TextField
            label={t('admin.checkpointStation.rejectReason')}
            value={rejectNotes}
            onChange={(e) => setRejectNotes(e.target.value)}
            multiline
            rows={3}
            fullWidth
            size="small"
            required
            error={rejecting && !rejectNotes.trim()}
            helperText={rejecting && !rejectNotes.trim() ? t('admin.checkpointStation.rejectReasonRequired') : undefined}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectTarget(null)} disabled={rejecting}>{t('common.cancel')}</Button>
          <Button variant="contained" color="error" onClick={handleReject} disabled={rejecting || !rejectNotes.trim()}>
            {rejecting ? <CircularProgress size={18} /> : t('admin.checkpointStation.rejectAction')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Bonus adjustment dialog */}
      <Dialog open={!!bonusTarget} onClose={() => setBonusTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('admin.checkpointStation.bonusDialogTitle', { teamName: bonusTarget?.teamName })}</DialogTitle>
        <DialogContent sx={{ pt: '16px !important', display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label={t('admin.checkpointStation.bonusAdjustment')}
            type="number"
            value={bonusDelta}
            onChange={(e) => {
              const val = e.target.value;
              if (val === '' || val === '-') {
                setBonusDelta(val === '-' ? '' : '');
                return;
              }
              const n = parseInt(val);
              if (!isNaN(n)) setBonusDelta(n);
            }}
            size="small"
            fullWidth
            helperText={t('admin.checkpointStation.bonusHint')}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBonusTarget(null)} disabled={adjustingBonus}>{t('common.cancel')}</Button>
          <Button
            variant="contained"
            onClick={handleAdjustBonus}
            disabled={adjustingBonus || bonusDelta === '' || bonusDelta === 0}
          >
            {adjustingBonus ? <CircularProgress size={18} /> : t('admin.checkpointStation.bonusApply')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Manual add dialog */}
      <Dialog open={addDialogOpen} onClose={() => setAddDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{t('admin.checkpointStation.addDialogTitle')}</DialogTitle>
        <DialogContent sx={{ pt: '16px !important' }}>
          <Autocomplete
            options={allTeams.filter((team) => !teams.some((r) => r.teamId === team.id && r.achievementStatus === 'VERIFIED'))}
            getOptionLabel={(team) => team.name}
            filterOptions={(options, { inputValue }) =>
              options.filter((option) => teamMatchesQuery(option, inputValue))
            }
            value={addTeam}
            onChange={(_, v) => setAddTeam(v)}
            renderOption={({ key, ...props }, option) => (
              <Box key={key} component="li" {...props}>
                <Box>
                  <Typography variant="body2">{option.name}</Typography>
                  {option.description && (
                    <Typography variant="caption" color="text.secondary" display="block">
                      {option.description}
                    </Typography>
                  )}
                </Box>
              </Box>
            )}
            renderInput={(params) => (
              <TextField {...params} label={t('admin.checkpointStation.addSearchTeam')} size="small" fullWidth />
            )}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddDialogOpen(false)} disabled={detecting}>{t('common.cancel')}</Button>
          <Button variant="contained" onClick={handleManualAdd} disabled={!addTeam || detecting}>
            {detecting ? <CircularProgress size={18} /> : t('admin.checkpointStation.addAction')}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={!!snackbar}
        autoHideDuration={5000}
        onClose={() => setSnackbar(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert onClose={() => setSnackbar(null)} severity={snackbar?.severity ?? 'info'} sx={{ width: '100%' }}>
          {snackbar?.message}
        </Alert>
      </Snackbar>
    </Box>
  );
}
