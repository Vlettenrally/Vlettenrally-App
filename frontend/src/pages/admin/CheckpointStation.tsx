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
import { useGameStore } from '../../store/useGameStore';
import { teamService } from '../../services/teamService';
import { websocket } from '../../services/websocket';
import api from '../../services/api';
import type { WebSocketMessage } from '../../services/websocket';
import type { AdminTeam, CheckpointTeamStatus } from '../../types/entities';
import type { Checkpoint } from '../../store/useGameStore';

interface CheckpointDetectionPayload {
  checkpointId: string;
  teamName: string;
}

const POLLING_INTERVAL_MS = 30_000;
const HEARTBEAT_INTERVAL_MS = 10_000;

export default function CheckpointStation() {
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
      setSnackbar({ message: `Team ${payload.teamName} gedetecteerd bij dit checkpoint`, severity: 'info' });
    };

    return websocket.on('CHECKPOINT_DETECTION', handler);
  }, [selectedCheckpointId, fetchTeams]);

  useEffect(() => {
    if (!selectedCheckpointId) return;
    const interval = setInterval(() => {
      if (Date.now() - lastEventAt.current >= POLLING_INTERVAL_MS) fetchTeams();
    }, POLLING_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [selectedCheckpointId, fetchTeams]);

  // Heartbeat for the live WebSocket subscription (30s timeout on the backend).
  useEffect(() => {
    if (!selectedCheckpointId) return;
    websocket.send({ type: 'CHECKPOINT_HEARTBEAT', checkpointId: selectedCheckpointId });
    const interval = setInterval(() => {
      websocket.send({ type: 'CHECKPOINT_HEARTBEAT', checkpointId: selectedCheckpointId });
    }, HEARTBEAT_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [selectedCheckpointId]);

  // Durable FCM subscription: subscribe when the operator selects a checkpoint,
  // unsubscribe when they pick a different one or navigate away from this page.
  // We deliberately keep the subscription alive across short WS drops / screen locks.
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
        setSnackbar({ message: detected.error ?? 'Kon bezoek niet registreren', severity: 'error' });
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
        ? `${verifyTarget.teamName} geverifieerd: ${total} punten (${base} + ${bonus} bonus)`
        : `${verifyTarget.teamName} geverifieerd: ${total} punten`;
      setSnackbar({ message, severity: 'success' });
      setVerifyTarget(null);
      fetchTeams();
    } else {
      setSnackbar({ message: res.error ?? 'Verificatie mislukt', severity: 'error' });
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
        setSnackbar({ message: detected.error ?? 'Kon bezoek niet registreren', severity: 'error' });
        setRejecting(false);
        return;
      }
      achievementId = detected.data.id;
    }

    const res = await api.rejectAchievement(achievementId, rejectNotes);
    setRejecting(false);
    if (res.data) {
      setSnackbar({ message: `${rejectTarget.teamName} afgewezen`, severity: 'success' });
      setRejectTarget(null);
      fetchTeams();
    } else {
      setSnackbar({ message: res.error ?? 'Afwijzing mislukt', severity: 'error' });
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
      setSnackbar({
        message: `${bonusTarget.teamName}: bonus aangepast met ${bonusDelta > 0 ? '+' : ''}${bonusDelta} punten`,
        severity: 'success',
      });
      setBonusTarget(null);
      fetchTeams();
    } else {
      setSnackbar({ message: res.error ?? 'Bonuspunten aanpassen mislukt', severity: 'error' });
    }
  };

  const handleManualAdd = async () => {
    if (!addTeam) return;
    setDetecting(true);

    const res = await api.detectProximity(addTeam.id, selectedCheckpointId);
    setDetecting(false);

    if (res.data) {
      setAddDialogOpen(false);
      setAddTeam(null);
      await fetchTeams();
      const newTeam = teams.find((t) => t.teamId === addTeam.id);
      if (newTeam) openVerify({ ...newTeam, achievementId: res.data.id });
    } else if (res.status === 409) {
      setAddDialogOpen(false);
      setAddTeam(null);
      await fetchTeams();
      setSnackbar({ message: 'Team was al geregistreerd — kies een actie in de lijst', severity: 'info' });
    } else {
      setSnackbar({ message: res.error ?? 'Toevoegen mislukt', severity: 'error' });
    }
  };

  const statusChip = (team: CheckpointTeamStatus) => {
    const status = team.achievementStatus;
    if (status === 'PENDING') return <Chip label="Wachten" color="warning" size="small" />;
    if (status === 'VERIFIED') return <Chip label="Geverifieerd" color="success" size="small" />;
    if (status === 'REJECTED') {
      return (
        <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5 }}>
          <Chip label="Afgewezen" color="error" size="small" />
          {team.notes && (
            <Tooltip title={team.notes} arrow>
              <InfoIcon fontSize="small" color="action" sx={{ cursor: 'help' }} />
            </Tooltip>
          )}
        </Box>
      );
    }
    return <Chip label="Nabij" color="default" size="small" variant="outlined" />;
  };

  const formatTime = (iso: string | null) =>
    iso ? new Date(iso).toLocaleTimeString('nl-NL', { hour: '2-digit', minute: '2-digit' }) : '—';

  const actionable = (status: CheckpointTeamStatus['achievementStatus']) =>
    status !== 'VERIFIED' && status !== 'REJECTED';

  const recentVerifiedIds = new Set(
    teams
      .filter((t) => t.achievementStatus === 'VERIFIED')
      .sort((a, b) => (b.verifiedAt ?? '').localeCompare(a.verifiedAt ?? ''))
      .slice(0, 5)
      .map((t) => t.teamId),
  );
  const visibleTeams = teams.filter(
    (t) => t.achievementStatus !== 'VERIFIED' || recentVerifiedIds.has(t.teamId),
  );

  return (
    <Box sx={{ p: 3 }}>
      {/* Page header */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 4, flexWrap: 'wrap', gap: 2 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Checkpoint Beheer
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
          <FormControl sx={{ minWidth: 220 }} size="small">
            <InputLabel>Checkpoint</InputLabel>
            <Select
              value={selectedCheckpointId}
              label="Checkpoint"
              onChange={(e) => setSelectedCheckpointId(e.target.value)}
            >
              <MenuItem value=""><em>— kies —</em></MenuItem>
              {checkpoints.map((cp) => (
                <MenuItem key={cp.id} value={cp.id}>{cp.name}</MenuItem>
              ))}
            </Select>
          </FormControl>
          <Tooltip title="Vernieuwen">
            <span>
              <IconButton onClick={handleRefresh} disabled={!selectedCheckpointId || loading} size="small">
                {loading ? <CircularProgress size={18} /> : <RefreshIcon />}
              </IconButton>
            </span>
          </Tooltip>
          {selectedCheckpointId && (
            isMobile ? (
              <Tooltip title="Team toevoegen">
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
                Team toevoegen
              </Button>
            )
          )}
        </Box>
      </Box>

      {!selectedGameId && (
        <Alert severity="info" sx={{ mb: 3 }}>Selecteer eerst een spel via de balk bovenin.</Alert>
      )}

      {selectedGameId && !selectedCheckpointId && (
        <Alert severity="info" sx={{ mb: 3 }}>Selecteer een checkpoint om de teams te zien.</Alert>
      )}

      {selectedCheckpointId && (
        <>
          {selectedCheckpoint && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Standaard: <strong>{selectedCheckpoint.points} punten</strong> per bezoek
            </Typography>
          )}

          <Card>
            <CardContent sx={{ p: 0, '&:last-child': { pb: 0 } }}>
              {isMobile ? (
                <Stack divider={<Divider />}>
                  {visibleTeams.length === 0 && !loading && (
                    <Box sx={{ p: 3, textAlign: 'center', color: 'text.secondary' }}>
                      Geen teams gedetecteerd
                    </Box>
                  )}
                  {visibleTeams.map((team) => (
                    <Box key={team.teamId} sx={{ p: 2 }}>
                      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.5 }}>
                        <Typography variant="subtitle1" fontWeight="medium">{team.teamName}</Typography>
                        {statusChip(team)}
                      </Box>
                      <Typography variant="body2" color="text.secondary" sx={{ mb: actionable(team.achievementStatus) ? 1.5 : 0 }}>
                        Gedetecteerd om {formatTime(team.detectedAt)}
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
                            Verifiëren
                          </Button>
                          <Button
                            variant="outlined"
                            color="error"
                            size="small"
                            startIcon={<RejectIcon />}
                            onClick={() => openReject(team)}
                            sx={{ flex: 1 }}
                          >
                            Afwijzen
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
                            Bonuspunten ±
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
                        <TableCell><strong>Team</strong></TableCell>
                        <TableCell><strong>Gedetecteerd</strong></TableCell>
                        <TableCell><strong>Status</strong></TableCell>
                        <TableCell align="right"><strong>Actie</strong></TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {visibleTeams.length === 0 && !loading && (
                        <TableRow>
                          <TableCell colSpan={4} align="center" sx={{ py: 4, color: 'text.secondary' }}>
                            Geen teams gedetecteerd
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
                                  Verifiëren
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  color="error"
                                  startIcon={<RejectIcon />}
                                  onClick={() => openReject(team)}
                                >
                                  Afwijzen
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
                                  Bonuspunten ±
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
        <DialogTitle>Bezoek verifiëren — {verifyTarget?.teamName}</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important' }}>
          <TextField
            label="Notities (optioneel)"
            value={verifyNotes}
            onChange={(e) => setVerifyNotes(e.target.value)}
            multiline
            rows={2}
            fullWidth
            size="small"
          />
          <TextField
            label="Bonuspunten"
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
            inputProps={{ min: 0 }}
            size="small"
            fullWidth
            helperText={
              selectedCheckpoint
                ? `Totaal: ${selectedCheckpoint.points} + ${bonusPoints === '' ? 0 : bonusPoints} = ${selectedCheckpoint.points + (bonusPoints === '' ? 0 : bonusPoints)} punten`
                : undefined
            }
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVerifyTarget(null)} disabled={verifying}>Annuleren</Button>
          <Button variant="contained" color="primary" onClick={handleVerify} disabled={verifying}>
            {verifying ? <CircularProgress size={18} /> : 'Verifiëren'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Reject dialog */}
      <Dialog open={!!rejectTarget} onClose={() => setRejectTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Bezoek afwijzen — {rejectTarget?.teamName}</DialogTitle>
        <DialogContent sx={{ pt: '16px !important' }}>
          <TextField
            label="Reden (verplicht)"
            value={rejectNotes}
            onChange={(e) => setRejectNotes(e.target.value)}
            multiline
            rows={3}
            fullWidth
            size="small"
            required
            error={rejecting && !rejectNotes.trim()}
            helperText={rejecting && !rejectNotes.trim() ? 'Geef een reden op' : undefined}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectTarget(null)} disabled={rejecting}>Annuleren</Button>
          <Button variant="contained" color="error" onClick={handleReject} disabled={rejecting || !rejectNotes.trim()}>
            {rejecting ? <CircularProgress size={18} /> : 'Afwijzen'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Bonus adjustment dialog (VERIFIED achievements only) */}
      <Dialog open={!!bonusTarget} onClose={() => setBonusTarget(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Bonuspunten — {bonusTarget?.teamName}</DialogTitle>
        <DialogContent sx={{ pt: '16px !important', display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label="Aanpassing (+ of −)"
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
            helperText="Positief om bij te schrijven, negatief om af te trekken."
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setBonusTarget(null)} disabled={adjustingBonus}>Annuleren</Button>
          <Button
            variant="contained"
            onClick={handleAdjustBonus}
            disabled={adjustingBonus || bonusDelta === '' || bonusDelta === 0}
          >
            {adjustingBonus ? <CircularProgress size={18} /> : 'Toepassen'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Manual add dialog */}
      <Dialog open={addDialogOpen} onClose={() => setAddDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Team handmatig toevoegen</DialogTitle>
        <DialogContent sx={{ pt: '16px !important' }}>
          <Autocomplete
            options={allTeams.filter((t) => !teams.some((r) => r.teamId === t.id && r.achievementStatus === 'VERIFIED'))}
            getOptionLabel={(t) => t.name}
            value={addTeam}
            onChange={(_, v) => setAddTeam(v)}
            renderInput={(params) => (
              <TextField {...params} label="Zoek team" size="small" fullWidth />
            )}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddDialogOpen(false)} disabled={detecting}>Annuleren</Button>
          <Button variant="contained" onClick={handleManualAdd} disabled={!addTeam || detecting}>
            {detecting ? <CircularProgress size={18} /> : 'Toevoegen'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Feedback snackbar */}
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
