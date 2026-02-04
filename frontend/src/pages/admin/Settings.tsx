/**
 * Admin Settings Page - Change password and manage admin account
 */
import { useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  Typography,
  Alert,
  CircularProgress,
} from '@mui/material';
import { LockReset as LockIcon } from '@mui/icons-material';
import api from '../../services/api';

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
    </Box>
  );
}

export default Settings;
