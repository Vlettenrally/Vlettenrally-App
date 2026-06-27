import { Box, Typography, Card, CardContent, Grid } from '@mui/material';
import { useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { useGameStore } from '../../store/useGameStore';

export default function Analytics() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  useEffect(() => {
    if (!selectedGameId) return;
    // TODO: Fetch game-specific analytics when API is available
  }, [selectedGameId]);

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" component="h1" sx={{ mb: 4, fontWeight: 'bold' }}>
        {t('admin.analytics.title')}
      </Typography>
      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                {t('admin.analytics.teamActivity')}
              </Typography>
              <Typography color="text.secondary">
                {t('admin.analytics.noData')}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                {t('admin.analytics.messageDelivery')}
              </Typography>
              <Typography color="text.secondary">
                {t('admin.analytics.noData')}
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
