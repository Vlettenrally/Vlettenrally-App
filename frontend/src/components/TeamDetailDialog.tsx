import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Typography,
  Chip,
  CircularProgress,
  Alert,
  FormControlLabel,
  Checkbox,
  Stack,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from '@mui/material';
import { QrCode2 as QrCodeIcon } from '@mui/icons-material';
import { useCallback, useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import QRCode from 'qrcode';
import type { AdminTeam, CheckInDevice } from '../types/entities';
import { teamService } from '../services/teamService';
import { gameService } from '../services/gameService';
import { storage } from '../services/storage';

interface TeamDetailDialogProps {
  open: boolean;
  onClose: () => void;
  team: AdminTeam | null;
  gameId: string;
  onSave?: () => void;
}

export default function TeamDetailDialog({
  open,
  onClose,
  team,
  gameId,
  onSave,
}: TeamDetailDialogProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rivalTeams, setRivalTeams] = useState<AdminTeam[]>([]);
  const [loadingRivals, setLoadingRivals] = useState(false);
  const [devices, setDevices] = useState<CheckInDevice[]>([]);
  const [loadingDevices, setLoadingDevices] = useState(false);
  const [deviceActionPending, setDeviceActionPending] = useState(false);
  const [confirmReset, setConfirmReset] = useState(false);
  const [deviceError, setDeviceError] = useState<string | null>(null);
  const [qrOpen, setQrOpen] = useState(false);
  const [qrPending, setQrPending] = useState(false);
  const [qrError, setQrError] = useState<string | null>(null);
  const [qrLink, setQrLink] = useState<string | null>(null);
  const qrCanvasRef = useRef<HTMLCanvasElement | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmLedgerReset, setConfirmLedgerReset] = useState(false);
  const [ledgerResetPending, setLedgerResetPending] = useState(false);
  const [availableTrackers, setAvailableTrackers] = useState<string[]>([]);
  const [backfillPending, setBackfillPending] = useState(false);
  const [backfillMessage, setBackfillMessage] = useState<string | null>(null);
  const [backfillError, setBackfillError] = useState(false);

  const [formData, setFormData] = useState({
    name: '',
    description: '',
    participants: '',
    emergencyPhoneNumbers: '',
    notes: '',
    checkedIn: false,
    checkedInDigitally: false,
    traccarEnabled: false,
    finished: false,
    trackerId: '',
  });

  const { t } = useTranslation();
  const isCreateMode = !team;
  const isSuperAdmin = storage.isSuperAdmin();

  useEffect(() => {
    if (team) {
      setFormData({
        name: team.name,
        description: team.description,
        participants: team.participants ? team.participants.join('\n') : '',
        emergencyPhoneNumbers: team.emergencyPhoneNumbers ? team.emergencyPhoneNumbers.join('\n') : '',
        notes: team.notes || '',
        checkedIn: team.checkedIn,
        checkedInDigitally: team.checkedInDigitally,
        traccarEnabled: team.traccarEnabled,
        finished: team.finished,
        trackerId: team.trackerId ?? '',
      });
      setIsEditing(false);
      setBackfillMessage(null);
      setBackfillError(false);

      // Load the pool of unassigned trackers for the picker.
      gameService
        .getAvailableTrackers(team.gameId)
        .then(setAvailableTrackers)
        .catch((err) => {
          console.error('Failed to load available trackers:', err);
          setAvailableTrackers([]);
        });

      // Load rival teams
      if (team.rivals && team.rivals.length > 0) {
        setLoadingRivals(true);
        Promise.all(
          team.rivals.map((rivalId: string) => 
            teamService.getTeam(rivalId, team.gameId)
          )
        )
          .then((rivals) => {
            setRivalTeams(rivals.filter((r: AdminTeam | null): r is AdminTeam => r !== null));
          })
          .catch((err) => {
            console.error('Failed to load rival teams:', err);
          })
          .finally(() => {
            setLoadingRivals(false);
          });
      } else {
        setRivalTeams([]);
      }

      setLoadingDevices(true);
      setDeviceError(null);
      teamService
        .listDevices(team.id)
        .then((list) => setDevices(list))
        .catch((err) => {
          console.error('Failed to load devices:', err);
          setDeviceError(t('components.teamDetail.devicesLoadFailed'));
          setDevices([]);
        })
        .finally(() => setLoadingDevices(false));
    } else {
      setFormData({
        name: '',
        description: '',
        participants: '',
        emergencyPhoneNumbers: '',
        notes: '',
        checkedIn: false,
        checkedInDigitally: false,
        traccarEnabled: false,
        finished: false,
        trackerId: '',
      });
      setIsEditing(true);
      setRivalTeams([]);
      setAvailableTrackers([]);
      setDevices([]);
      setConfirmReset(false);
      setDeviceError(null);
    }
    setConfirmDelete(false);
    setError(null);
    setQrOpen(false);
    setQrLink(null);
    setQrError(null);
  }, [team, gameId, open, t]);

  const refreshDevices = useCallback(async () => {
    if (!team) return;
    setLoadingDevices(true);
    setDeviceError(null);
    try {
      const list = await teamService.listDevices(team.id);
      setDevices(list);
    } catch (err) {
      console.error('Failed to refresh devices:', err);
      setDeviceError(t('components.teamDetail.devicesRefreshFailed'));
    } finally {
      setLoadingDevices(false);
    }
  }, [team, t]);

  const handleMakeMain = async (deviceId: string) => {
    if (!team) return;
    setDeviceActionPending(true);
    setDeviceError(null);
    try {
      const ok = await teamService.setMainDevice(team.id, deviceId);
      if (!ok) {
        setDeviceError(t('components.teamDetail.makeMainPhoneFailed'));
        return;
      }
      await refreshDevices();
    } finally {
      setDeviceActionPending(false);
    }
  };

  const handleRemoveDevice = async (deviceId: string) => {
    if (!team) return;
    setDeviceActionPending(true);
    setDeviceError(null);
    try {
      const ok = await teamService.removeDevice(team.id, deviceId);
      if (!ok) {
        setDeviceError(t('components.teamDetail.removeDeviceFailed'));
        return;
      }
      await refreshDevices();
    } finally {
      setDeviceActionPending(false);
    }
  };

  const handleResetDevices = async () => {
    if (!team) return;
    setDeviceActionPending(true);
    setDeviceError(null);
    try {
      const ok = await teamService.resetDevices(team.id);
      if (!ok) {
        setDeviceError(t('components.teamDetail.resetCheckInFailed'));
        return;
      }
      setConfirmReset(false);
      await refreshDevices();
      onSave?.();
    } finally {
      setDeviceActionPending(false);
    }
  };

  // Mint + reveal a team invite QR. The code is short-lived, but this dialog is too — it is
  // minted when the QR is opened, which is when it gets scanned.
  const handleShowQr = async () => {
    if (!team) return;
    setQrOpen(true);
    setQrPending(true);
    setQrError(null);
    setQrLink(null);
    try {
      const { qrUrl, error: linkError } = await teamService.getInviteLink(team.id);
      if (linkError || !qrUrl) {
        setQrError(t('components.teamDetail.inviteQrError'));
        return;
      }
      setQrLink(qrUrl);
    } catch {
      setQrError(t('components.teamDetail.inviteQrError'));
    } finally {
      setQrPending(false);
    }
  };

  // Render the QR-optimised (short, uppercase) invite URL once link and element are ready.
  useEffect(() => {
    if (!qrOpen || !qrLink || !qrCanvasRef.current) return;
    QRCode.toCanvas(qrCanvasRef.current, qrLink, {
      width: 240,
      margin: 2,
      errorCorrectionLevel: 'Q',
    }).catch(() => setQrError(t('components.teamDetail.inviteQrError')));
  }, [qrOpen, qrLink, t]);

  const handleResetLedger = async () => {
    if (!team) return;
    setLedgerResetPending(true);
    try {
      const { error: ledgerError } = await teamService.resetPointsLedger(team.id, gameId);
      if (ledgerError) {
        setError(ledgerError);
        return;
      }
      setConfirmLedgerReset(false);
      onSave?.();
    } finally {
      setLedgerResetPending(false);
    }
  };

  const handleEdit = () => {
    setIsEditing(true);
    setError(null);
  };

  const handleCancel = () => {
    if (isCreateMode) {
      onClose();
    } else {
      setIsEditing(false);
      if (team) {
        setFormData({
          name: team.name,
          description: team.description,
          participants: team.participants.join('\n'),
          emergencyPhoneNumbers: team.emergencyPhoneNumbers ? team.emergencyPhoneNumbers.join('\n') : '',
          notes: team.notes || '',
          checkedIn: team.checkedIn,
          checkedInDigitally: team.checkedInDigitally,
          traccarEnabled: team.traccarEnabled,
          finished: team.finished,
          trackerId: team.trackerId ?? '',
        });
      }
      setError(null);
    }
  };

  const handleSave = async () => {
    setLoading(true);
    setError(null);

    try {
      const participants = formData.participants
        .split('\n')
        .map((p) => p.trim())
        .filter((p) => p.length > 0);

      const emergencyPhoneNumbers = formData.emergencyPhoneNumbers
        .split('\n')
        .map((p) => p.trim())
        .filter((p) => p.length > 0);

      const teamData = {
        name: formData.name,
        description: formData.description,
        participants,
        emergencyPhoneNumbers,
        notes: formData.notes || undefined,
        checkedIn: formData.checkedIn,
        checkedInDigitally: formData.checkedInDigitally,
        traccarEnabled: formData.traccarEnabled,
        finished: formData.finished,
      };

      if (isCreateMode) {
        await teamService.createTeam({
          ...teamData,
          gameId: gameId,
        });
      } else if (team) {
        await teamService.updateTeam(team.id, teamData);

        // Tracker assignment goes through its own endpoint (pool validation). Only
        // call it when the selection actually changed.
        if (formData.trackerId !== (team.trackerId ?? '')) {
          const { error: trackerError } = await teamService.assignTracker(
            team.id,
            formData.trackerId || null,
          );
          if (trackerError) {
            setError(trackerError);
            return;
          }
        }
      }

      setIsEditing(false);
      onSave?.();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save team');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!team) return;
    setDeleting(true);
    setError(null);
    try {
      const { error: deleteError } = await teamService.deleteTeam(team.id);
      if (deleteError) {
        setError(deleteError);
        return;
      }
      setConfirmDelete(false);
      onSave?.();
      onClose();
    } finally {
      setDeleting(false);
    }
  };

  const handleBackfill = useCallback(async () => {
    if (!team) return;
    setBackfillPending(true);
    setBackfillMessage(null);
    setBackfillError(false);
    try {
      const outcome = await gameService.backfillTeamTrack(team.gameId, team.id);
      if (outcome.status === 'cooldown') {
        setBackfillError(true);
        setBackfillMessage(t('components.teamDetail.backfillCooldown'));
      } else if (outcome.status === 'error') {
        setBackfillError(true);
        setBackfillMessage(outcome.error ?? t('components.teamDetail.backfillFailed'));
      } else {
        setBackfillError(false);
        setBackfillMessage(
          t('components.teamDetail.backfillSuccess', {
            inserted: outcome.result.inserted,
            passes: outcome.result.checkpointPassesDetected,
          }),
        );
      }
    } finally {
      setBackfillPending(false);
    }
  }, [team, t]);

  const handleChange = (field: keyof typeof formData) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => {
    setFormData((prev) => ({
      ...prev,
      [field]: event.target.value,
    }));
  };

  const handleCheckboxChange = (field: 'checkedIn' | 'checkedInDigitally' | 'traccarEnabled' | 'finished') => (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const newValue = event.target.checked;
    setFormData((prev) => {
      // If unchecking checkedIn, also uncheck checkedInDigitally
      if (field === 'checkedIn' && !newValue) {
        return {
          ...prev,
          checkedIn: false,
          checkedInDigitally: false,
        };
      }
      return {
        ...prev,
        [field]: newValue,
      };
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {isCreateMode ? t('components.teamDetail.createTitle') : isEditing ? t('components.teamDetail.editTitle') : t('components.teamDetail.viewTitle')}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {confirmDelete && team && (
          <Alert
            severity="error"
            sx={{ mb: 2 }}
            action={
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  color="inherit"
                  disabled={deleting}
                  onClick={() => setConfirmDelete(false)}
                >
                  {t('common.cancel')}
                </Button>
                <Button
                  size="small"
                  color="error"
                  variant="contained"
                  disabled={deleting}
                  onClick={handleDelete}
                >
                  {deleting ? <CircularProgress size={20} /> : t('components.teamDetail.deleteForever')}
                </Button>
              </Stack>
            }
          >
            {t('components.teamDetail.deleteConfirmQuestion', { name: team.name })}
          </Alert>
        )}

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <TextField
            label={t('components.teamDetail.teamName')}
            value={formData.name}
            onChange={handleChange('name')}
            disabled={!isEditing}
            required
            fullWidth
          />

          <TextField
            label={t('components.teamDetail.description')}
            value={formData.description}
            onChange={handleChange('description')}
            disabled={!isEditing}
            fullWidth
          />

          <TextField
            label={t('components.teamDetail.participants')}
            value={formData.participants}
            onChange={handleChange('participants')}
            disabled={!isEditing}
            multiline
            rows={4}
            fullWidth
            helperText={t('components.teamDetail.participantsHint')}
          />

          <TextField
            label={t('components.teamDetail.emergencyPhones')}
            value={formData.emergencyPhoneNumbers}
            onChange={handleChange('emergencyPhoneNumbers')}
            disabled={!isEditing}
            multiline
            rows={3}
            fullWidth
            helperText={t('components.teamDetail.emergencyPhonesHint')}
          />

          <TextField
            label={t('components.teamDetail.notes')}
            value={formData.notes}
            onChange={handleChange('notes')}
            disabled={!isEditing}
            multiline
            rows={3}
            fullWidth
          />

          {!isCreateMode && team && (
            <>
              {isEditing ? (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    {t('components.teamDetail.checkInStatus')}
                  </Typography>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.checkedIn}
                        onChange={handleCheckboxChange('checkedIn')}
                      />
                    }
                    label={t('components.teamDetail.checkedIn')}
                  />
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.checkedInDigitally}
                        onChange={handleCheckboxChange('checkedInDigitally')}
                        disabled={!formData.checkedIn}
                      />
                    }
                    label={t('components.teamDetail.checkedInDigitally')}
                  />
                </Box>
              ) : (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    {t('components.teamDetail.status')}
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    <Chip
                      label={team.checkedIn ? t('components.teamDetail.checkedIn') : t('components.teamDetail.notCheckedIn')}
                      color={team.checkedIn ? 'success' : 'default'}
                      size="small"
                    />
                    {team.checkedIn && (
                      <Chip
                        label={team.checkedInDigitally ? t('components.teamDetail.digitalCheckIn') : t('components.teamDetail.manualCheckIn')}
                        color={team.checkedInDigitally ? 'info' : 'warning'}
                        size="small"
                        variant="outlined"
                      />
                    )}
                  </Box>
                </Box>
              )}

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('components.teamDetail.traccarTracking')}
                </Typography>
                {isEditing ? (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.traccarEnabled}
                        onChange={handleCheckboxChange('traccarEnabled')}
                      />
                    }
                    label={t('components.teamDetail.useTraccar')}
                  />
                ) : (
                  <Chip
                    label={team.traccarEnabled ? t('components.teamDetail.enabled') : t('components.teamDetail.disabled')}
                    color={team.traccarEnabled ? 'success' : 'default'}
                    size="small"
                  />
                )}
                {team.traccarEnabled && team.traccarIdentifier && (
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, wordBreak: 'break-all' }}>
                    {t('components.teamDetail.traccarDeviceId', { id: team.traccarIdentifier })}
                  </Typography>
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('components.teamDetail.gpsTracker')}
                </Typography>
                {isEditing ? (
                  <FormControl fullWidth size="small">
                    <InputLabel id="tracker-select-label">Tracker</InputLabel>
                    <Select
                      labelId="tracker-select-label"
                      label="Tracker"
                      value={formData.trackerId}
                      onChange={(e) =>
                        setFormData((prev) => ({ ...prev, trackerId: e.target.value }))
                      }
                    >
                      <MenuItem value="">
                        <em>{t('components.teamDetail.noTracker')}</em>
                      </MenuItem>
                      {/* Current tracker first (it's excluded from the available pool), then the pool. */}
                      {team.trackerId && (
                        <MenuItem value={team.trackerId}>{team.trackerId}</MenuItem>
                      )}
                      {availableTrackers.map((id) => (
                        <MenuItem key={id} value={id}>
                          {id}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                ) : (
                  <Chip
                    label={team.trackerId ? team.trackerId : t('components.teamDetail.noTrackerAssigned')}
                    color={team.trackerId ? 'success' : 'default'}
                    size="small"
                  />
                )}
                {!isEditing && team.trackerId && (
                  <Box sx={{ mt: 1 }}>
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={handleBackfill}
                      disabled={backfillPending}
                      startIcon={backfillPending ? <CircularProgress size={16} /> : undefined}
                    >
                      {t('components.teamDetail.backfillTrack')}
                    </Button>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ display: 'block', mt: 0.5 }}
                    >
                      {t('components.teamDetail.backfillHint')}
                    </Typography>
                    {backfillMessage && (
                      <Alert severity={backfillError ? 'warning' : 'success'} sx={{ mt: 1 }}>
                        {backfillMessage}
                      </Alert>
                    )}
                  </Box>
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('components.teamDetail.finish')}
                </Typography>
                {isEditing ? (
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.finished}
                        onChange={handleCheckboxChange('finished')}
                      />
                    }
                    label={t('components.teamDetail.finishedNoAuto')}
                  />
                ) : (
                  <Chip
                    label={team.finished ? t('components.teamDetail.finished') : t('components.teamDetail.stillEnRoute')}
                    color={team.finished ? 'success' : 'default'}
                    size="small"
                  />
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('components.teamDetail.rivalTeams')}
                </Typography>
                {loadingRivals ? (
                  <CircularProgress size={20} />
                ) : rivalTeams.length > 0 ? (
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    {rivalTeams.map((rival) => (
                      <Chip key={rival.id} label={rival.name} size="small" />
                    ))}
                  </Box>
                ) : (
                  <Typography color="text.secondary">{t('components.teamDetail.noRivalTeams')}</Typography>
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  {t('components.teamDetail.devices')}
                </Typography>

                <Box sx={{ mb: 2 }}>
                  {!qrOpen ? (
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={<QrCodeIcon />}
                      onClick={handleShowQr}
                    >
                      {t('components.teamDetail.showInviteQr')}
                    </Button>
                  ) : (
                    <Stack spacing={1} alignItems="center" sx={{ py: 1 }}>
                      <Typography variant="caption" color="text.secondary" align="center">
                        {t('components.teamDetail.inviteQrHint')}
                      </Typography>
                      {qrPending ? (
                        <CircularProgress size={24} />
                      ) : qrError ? (
                        <Alert severity="error" sx={{ width: '100%' }}>{qrError}</Alert>
                      ) : (
                        <Box sx={{ bgcolor: '#fff', p: 1.5, borderRadius: 1, lineHeight: 0 }}>
                          <canvas ref={qrCanvasRef} />
                        </Box>
                      )}
                      <Button size="small" onClick={() => setQrOpen(false)}>
                        {t('common.close')}
                      </Button>
                    </Stack>
                  )}
                </Box>

                {deviceError && (
                  <Alert severity="error" sx={{ mb: 1 }}>{deviceError}</Alert>
                )}
                {loadingDevices ? (
                  <CircularProgress size={20} />
                ) : devices.length === 0 ? (
                  <Typography color="text.secondary">{t('components.teamDetail.noDevices')}</Typography>
                ) : (
                  <Stack spacing={1}>
                    {devices.map((device) => (
                      <Box
                        key={device.deviceId}
                        sx={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 1,
                          p: 1,
                          border: '1px solid',
                          borderColor: 'divider',
                          borderRadius: 1,
                        }}
                      >
                        <Box sx={{ flexGrow: 1 }}>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="body2">
                              {device.nickname && device.nickname.length > 0 ? device.nickname : t('components.teamDetail.unnamed')}
                            </Typography>
                            {device.isMain && (
                              <Chip size="small" color="primary" label={t('components.teamDetail.mainPhone')} />
                            )}
                          </Box>
                          <Typography variant="caption" color="text.secondary">
                            {t('components.teamDetail.lastSeen', { time: new Date(device.lastSeen).toLocaleString() })}
                          </Typography>
                        </Box>
                        {!device.isMain && (
                          <Button
                            size="small"
                            variant="outlined"
                            disabled={deviceActionPending}
                            onClick={() => handleMakeMain(device.deviceId)}
                          >
                            {t('components.teamDetail.makeMainPhone')}
                          </Button>
                        )}
                        <Button
                          size="small"
                          color="error"
                          variant="outlined"
                          disabled={deviceActionPending}
                          onClick={() => handleRemoveDevice(device.deviceId)}
                        >
                          {t('components.teamDetail.removeDevice')}
                        </Button>
                      </Box>
                    ))}
                  </Stack>
                )}

                <Box sx={{ mt: 2 }}>
                  {confirmReset ? (
                    <Alert
                      severity="warning"
                      action={
                        <Stack direction="row" spacing={1}>
                          <Button
                            size="small"
                            color="inherit"
                            disabled={deviceActionPending}
                            onClick={() => setConfirmReset(false)}
                          >
                            {t('common.cancel')}
                          </Button>
                          <Button
                            size="small"
                            color="error"
                            variant="contained"
                            disabled={deviceActionPending}
                            onClick={handleResetDevices}
                          >
                            {t('components.teamDetail.reset')}
                          </Button>
                        </Stack>
                      }
                    >
                      {t('components.teamDetail.confirmResetMessage')}
                    </Alert>
                  ) : (
                    <Button
                      size="small"
                      color="error"
                      variant="outlined"
                      disabled={deviceActionPending}
                      onClick={() => setConfirmReset(true)}
                    >
                      {t('components.teamDetail.resetCheckIn')}
                    </Button>
                  )}
                </Box>

                {isSuperAdmin && (
                  <Box sx={{ mt: 2 }}>
                    {confirmLedgerReset ? (
                      <Alert
                        severity="error"
                        action={
                          <Stack direction="row" spacing={1}>
                            <Button
                              size="small"
                              color="inherit"
                              disabled={ledgerResetPending}
                              onClick={() => setConfirmLedgerReset(false)}
                            >
                              {t('common.cancel')}
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              variant="contained"
                              disabled={ledgerResetPending}
                              onClick={handleResetLedger}
                            >
                              {ledgerResetPending ? <CircularProgress size={20} /> : t('components.teamDetail.reset')}
                            </Button>
                          </Stack>
                        }
                      >
                        {t('components.teamDetail.confirmLedgerResetMessage')}
                      </Alert>
                    ) : (
                      <Button
                        size="small"
                        color="error"
                        variant="outlined"
                        disabled={ledgerResetPending}
                        onClick={() => setConfirmLedgerReset(true)}
                      >
                        {t('components.teamDetail.resetLedger')}
                      </Button>
                    )}
                  </Box>
                )}
              </Box>
            </>
          )}
        </Box>
      </DialogContent>
      <DialogActions sx={{ justifyContent: isEditing || isCreateMode ? 'flex-end' : 'space-between' }}>
        {isEditing ? (
          <>
            <Button onClick={handleCancel} disabled={loading}>
              {t('common.cancel')}
            </Button>
            <Button
              onClick={handleSave}
              variant="contained"
              disabled={loading || !formData.name}
            >
              {loading ? <CircularProgress size={24} /> : t('common.save')}
            </Button>
          </>
        ) : (
          <>
            {!isCreateMode && (
              <Button
                color="error"
                onClick={() => setConfirmDelete(true)}
                disabled={deleting || confirmDelete}
              >
                {t('components.teamDetail.deleteTeam')}
              </Button>
            )}
            <Box>
              <Button onClick={onClose}>{t('common.close')}</Button>
              <Button onClick={handleEdit} variant="contained" sx={{ ml: 1 }}>
                {t('common.edit')}
              </Button>
            </Box>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
