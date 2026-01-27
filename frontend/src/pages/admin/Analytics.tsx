import { Box, Typography, Card, CardContent, Grid } from '@mui/material';
import { useEffect } from 'react';
import { useGameStore } from '../../store/useGameStore';

export default function Analytics() {
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  useEffect(() => {
    if (!selectedGameId) return;
    // TODO: Fetch game-specific analytics when API is available
  }, [selectedGameId]);

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" component="h1" sx={{ mb: 4, fontWeight: 'bold' }}>
        Analytics
      </Typography>
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Team Activity
              </Typography>
              <Typography color="text.secondary">
                No data available
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Message Delivery
              </Typography>
              <Typography color="text.secondary">
                No data available
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
