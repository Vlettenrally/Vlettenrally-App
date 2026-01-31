/**
 * Admin Login Page - Login form for administrators
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
import { AdminPanelSettings as AdminIcon } from '@mui/icons-material';
import { storage } from '../../services/storage';
import { getRoleFromToken } from '../../services/auth';
import { config } from '../../config';

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

interface ApiError {
  message: string;
}

export function AdminLogin() {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const response = await fetch(`${config.apiBaseUrl}/auth/admin/login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ username, password }),
      });

      if (!response.ok) {
        const errorData: ApiError = await response.json();
        throw new Error(errorData.message || 'Login failed');
      }

      const data: TokenResponse = await response.json();
      
      // Store both access and refresh tokens
      storage.setAuthToken(data.accessToken);
      storage.setRefreshToken(data.refreshToken);
      
      // Parse and store role from token
      const role = getRoleFromToken(data.accessToken);
      if (role) {
        storage.setUserRole(role);
      }

      // Navigate to admin dashboard
      navigate('/admin', { replace: true });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: '#1a237e',
        p: 2,
      }}
    >
      <Card sx={{ maxWidth: 400, width: '100%' }}>
        <CardContent sx={{ p: 4 }}>
          <Box sx={{ textAlign: 'center', mb: 3 }}>
            <AdminIcon sx={{ fontSize: 48, color: '#1a237e', mb: 1 }} />
            <Typography variant="h5" component="h1" gutterBottom>
              Admin Login
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Vlettenrally Beheerpaneel
            </Typography>
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}

          <form onSubmit={handleSubmit}>
            <TextField
              fullWidth
              label="Gebruikersnaam"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              margin="normal"
              required
              autoComplete="username"
              autoFocus
            />
            <TextField
              fullWidth
              label="Wachtwoord"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              margin="normal"
              required
              autoComplete="current-password"
            />
            <Button
              type="submit"
              fullWidth
              variant="contained"
              size="large"
              disabled={loading}
              sx={{ mt: 3, mb: 2, bgcolor: '#1a237e', '&:hover': { bgcolor: '#0d1642' } }}
            >
              {loading ? <CircularProgress size={24} color="inherit" /> : 'Inloggen'}
            </Button>
          </form>

          <Box sx={{ textAlign: 'center', mt: 2 }}>
            <Button
              color="primary"
              onClick={() => navigate('/check-in')}
              sx={{ textTransform: 'none' }}
            >
              ← Terug naar team login
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  );
}

export default AdminLogin;
