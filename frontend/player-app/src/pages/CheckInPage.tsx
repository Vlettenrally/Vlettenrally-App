/**
 * Check-in Page - Token-based authentication
 */
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box,
  Container,
  Typography,
  Paper,
  Alert,
  CircularProgress,
} from '@mui/material';
import { api } from '../services/api';
import { storage } from '../services/storage';
import { useTeamStore, type Team } from '../store/useTeamStore';

export function CheckInPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { setTeam } = useTeamStore();
  
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const authenticateWithToken = async () => {
      // Get registration token from URL parameters
      const registrationToken = searchParams.get('token');
      
      if (!registrationToken) {
        setError('Geen authenticatie token gevonden. Vraag de organisatie om een nieuwe link.');
        setLoading(false);
        return;
      }

      try {
        // Exchange registration token for access + refresh tokens
        const tokenResponse = await api.exchangeToken(registrationToken);

        if (tokenResponse.error) {
          setError('Authenticatie mislukt: ' + tokenResponse.error);
          return;
        }

        if (tokenResponse.data) {
          const tokens = tokenResponse.data as { 
            accessToken: string; 
            refreshToken: string;
            expiresIn: number;
          };
          
          // Store tokens
          storage.setAuthToken(tokens.accessToken);
          storage.setRefreshToken(tokens.refreshToken);

          // Fetch team data using the access token
          const teamResponse = await api.getMyTeam();

          if (teamResponse.error) {
            setError('Kon teamgegevens niet ophalen: ' + teamResponse.error);
            storage.clearSession();
            return;
          }

          if (teamResponse.data) {
            const teamData = teamResponse.data as Team;
            
            // Store team and game data
            storage.setTeamId(teamData.id);
            storage.setGameId(teamData.game);
            storage.setTeamData(teamData);
            setTeam(teamData);

            // Navigate to dashboard
            navigate('/dashboard', { replace: true });
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

  return (
    <Container maxWidth="sm">
      <Box sx={{ mt: 8, mb: 4, display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <Paper elevation={3} sx={{ p: 4, width: '100%', textAlign: 'center' }}>
          <Typography variant="h4" component="h1" gutterBottom>
            Vlettenrally
          </Typography>
          
          {loading && (
            <Box sx={{ mt: 4, mb: 4 }}>
              <CircularProgress />
              <Typography variant="body1" color="text.secondary" sx={{ mt: 2 }}>
                Bezig met inloggen...
              </Typography>
            </Box>
          )}

          {error && (
            <Box sx={{ mt: 3 }}>
              <Alert severity="error">{error}</Alert>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                Neem contact op met de organisatie voor een nieuwe login link.
              </Typography>
            </Box>
          )}

          {!loading && !error && (
            <Typography variant="body1" color="text.secondary" sx={{ mt: 2 }}>
              U wordt doorgestuurd naar het dashboard...
            </Typography>
          )}
        </Paper>
      </Box>
    </Container>
  );
}
