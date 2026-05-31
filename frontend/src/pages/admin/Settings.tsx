/**
 * Admin Settings Page - Change password, manage admin account, manage own devices
 */
import { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Alert,
  CircularProgress,
  Chip,
  Stack,
  IconButton,
} from '@mui/material';
import {
  LockReset as LockIcon,
  PhoneAndroid as DeviceIcon,
  Edit as EditIcon,
  Save as SaveIcon,
  Close as CloseIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material';
import api from '../../services/api';
import storage from '../../services/storage';
import type { CheckInDevice } from '../../types/entities';

interface PasswordForm {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export function Settings() {
  const [form, setForm] = useState<PasswordForm>({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const [devices, setDevices] = useState<CheckInDevice[]>([]);
  const [devicesLoading, setDevicesLoading] = useState(false);
  const [devicesError, setDevicesError] = useState<string | null>(null);
  const [editingDeviceId, setEditingDeviceId] = useState<string | null>(null);
  const [editingNickname, setEditingNickname] = useState('');
  const [deviceActionPending, setDeviceActionPending] = useState(false);

  const currentDeviceId = storage.getAdminDeviceId();

  const loadDevices = useCallback(async () => {
    setDevicesLoading(true);
    setDevicesError(null);
    try {
      const response = await api.getMyAdminDevices();
      if (response.error) {
        setDevicesError(response.error);
        return;
      }
      setDevices(response.data ?? []);
    } finally {
      setDevicesLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDevices();
  }, [loadDevices]);

  const startRename = (device: CheckInDevice) => {
    setEditingDeviceId(device.deviceId);
    setEditingNickname(device.nickname ?? '');
  };

  const cancelRename = () => {
    setEditingDeviceId(null);
    setEditingNickname('');
  };

  const saveRename = async (deviceId: string) => {
    setDeviceActionPending(true);
    setDevicesError(null);
    try {
      const response = await api.renameMyAdminDevice(deviceId, editingNickname.trim());
      if (response.error) {
        setDevicesError(response.error);
        return;
      }
      cancelRename();
      await loadDevices();
    } finally {
      setDeviceActionPending(false);
    }
  };

  const removeDevice = async (deviceId: string) => {
    setDeviceActionPending(true);
    setDevicesError(null);
    try {
      const response = await api.removeMyAdminDevice(deviceId);
      if (response.error) {
        setDevicesError(response.error);
        return;
      }
      await loadDevices();
    } finally {
      setDeviceActionPending(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(null);

    // Validate passwords match
    if (form.newPassword !== form.confirmPassword) {
      setError('Nieuwe wachtwoorden komen niet overeen');
      return;
    }

    // Validate password strength
    if (form.newPassword.length < 8) {
      setError('Nieuw wachtwoord moet minimaal 8 tekens lang zijn');
      return;
    }

    setLoading(true);

    try {
      const response = await api.post('/auth/admin/update-password', {
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      });

      if (response.error) {
        throw new Error(response.error);
      }

      setSuccess('Wachtwoord succesvol gewijzigd');
      setForm({
        currentPassword: '',
        newPassword: '',
        confirmPassword: '',
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Er is een fout opgetreden');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (field: keyof PasswordForm) => (
    e: React.ChangeEvent<HTMLInputElement>
  ) => {
    setForm({ ...form, [field]: e.target.value });
    // Clear messages when user starts typing
    if (error) setError(null);
    if (success) setSuccess(null);
  };

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', p: 3 }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 3 }}>
        Instellingen
      </Typography>

      <Card>
        <CardContent sx={{ p: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
            <LockIcon sx={{ fontSize: 32, color: '#1a237e', mr: 2 }} />
            <Typography variant="h6">Wachtwoord wijzigen</Typography>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          {success && (
            <Alert severity="success" sx={{ mb: 2 }}>
              {success}
            </Alert>
          )}

          <form onSubmit={handleSubmit}>
            <TextField
              fullWidth
              label="Huidig wachtwoord"
              type="password"
              value={form.currentPassword}
              onChange={handleChange('currentPassword')}
              margin="normal"
              required
              autoComplete="current-password"
            />
            <TextField
              fullWidth
              label="Nieuw wachtwoord"
              type="password"
              value={form.newPassword}
              onChange={handleChange('newPassword')}
              margin="normal"
              required
              autoComplete="new-password"
              helperText="Minimaal 8 tekens"
            />
            <TextField
              fullWidth
              label="Bevestig nieuw wachtwoord"
              type="password"
              value={form.confirmPassword}
              onChange={handleChange('confirmPassword')}
              margin="normal"
              required
              autoComplete="new-password"
            />
            <Button
              type="submit"
              fullWidth
              variant="contained"
              size="large"
              disabled={loading}
              sx={{
                mt: 3,
                bgcolor: '#1a237e',
                '&:hover': { bgcolor: '#0d1b5e' },
              }}
            >
              {loading ? (
                <CircularProgress size={24} sx={{ color: 'white' }} />
              ) : (
                'Wachtwoord wijzigen'
              )}
            </Button>
          </form>
        </CardContent>
      </Card>

      <Card sx={{ mt: 3 }}>
        <CardContent sx={{ p: 3 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
            <DeviceIcon sx={{ fontSize: 32, color: '#1a237e', mr: 2 }} />
            <Typography variant="h6">Mijn apparaten</Typography>
          </Box>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Browsers waarop je meldingen hebt ingeschakeld. Verwijder een apparaat om daar geen pushberichten
            meer naar te sturen.
          </Typography>

          {devicesError && <Alert severity="error" sx={{ mb: 2 }}>{devicesError}</Alert>}

          {devicesLoading ? (
            <CircularProgress size={24} />
          ) : devices.length === 0 ? (
            <Typography color="text.secondary">
              Geen apparaten geregistreerd. Sta meldingen toe om dit apparaat toe te voegen.
            </Typography>
          ) : (
            <Stack spacing={1}>
              {devices.map((device) => {
                const isCurrent = currentDeviceId === device.deviceId;
                const isEditing = editingDeviceId === device.deviceId;
                return (
                  <Box
                    key={device.deviceId}
                    sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 1,
                      p: 1.5,
                      border: '1px solid',
                      borderColor: 'divider',
                      borderRadius: 1,
                    }}
                  >
                    <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                      {isEditing ? (
                        <TextField
                          size="small"
                          fullWidth
                          value={editingNickname}
                          onChange={(e) => setEditingNickname(e.target.value)}
                          placeholder="Naam van apparaat"
                          autoFocus
                        />
                      ) : (
                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                          <Typography variant="body2">
                            {device.nickname && device.nickname.length > 0 ? device.nickname : '(Onbenoemd)'}
                          </Typography>
                          {isCurrent && <Chip size="small" color="primary" label="Dit apparaat" />}
                        </Box>
                      )}
                      <Typography variant="caption" color="text.secondary">
                        Laatst gezien: {new Date(device.lastSeen).toLocaleString('nl-NL')}
                      </Typography>
                    </Box>
                    {isEditing ? (
                      <>
                        <IconButton
                          size="small"
                          color="primary"
                          disabled={deviceActionPending}
                          onClick={() => saveRename(device.deviceId)}
                          aria-label="Opslaan"
                        >
                          <SaveIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          disabled={deviceActionPending}
                          onClick={cancelRename}
                          aria-label="Annuleren"
                        >
                          <CloseIcon fontSize="small" />
                        </IconButton>
                      </>
                    ) : (
                      <>
                        <IconButton
                          size="small"
                          disabled={deviceActionPending}
                          onClick={() => startRename(device)}
                          aria-label="Hernoem"
                        >
                          <EditIcon fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          color="error"
                          disabled={deviceActionPending}
                          onClick={() => removeDevice(device.deviceId)}
                          aria-label="Verwijder"
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </>
                    )}
                  </Box>
                );
              })}
            </Stack>
          )}
        </CardContent>
      </Card>
    </Box>
  );
}

export default Settings;
