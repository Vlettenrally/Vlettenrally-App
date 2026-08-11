import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Box, Typography, Card, CardContent, Grid, CircularProgress, Alert } from '@mui/material';
import {
  Groups as TeamsIcon,
  HowToReg as CheckedInIcon,
  Block as DisqualifiedIcon,
  Straighten as DistanceIcon,
  Flag as CheckpointsIcon,
  VerifiedUser as VerificationIcon,
  Scoreboard as PointsIcon,
  TrendingUp as DistanceBonusIcon,
  Bolt as EarlyFinishIcon,
} from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { useGameStore } from '../../store/useGameStore';
import { useAdminResync } from '../../hooks/useAdminSync';
import api from '../../services/api';
import { formatDistance } from '../../utils/distance';
import type { GameStatistics } from '../../types/entities';

interface StatTileProps {
  icon: ReactNode;
  label: string;
  value: ReactNode;
}

function StatTile({ icon, label, value }: StatTileProps) {
  return (
    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
      <Card>
        <CardContent>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
            <Box sx={{ mr: 1, color: 'primary.main', display: 'flex' }}>{icon}</Box>
            <Typography variant="subtitle2" color="text.secondary">
              {label}
            </Typography>
          </Box>
          <Typography variant="h4" component="div">
            {value}
          </Typography>
        </CardContent>
      </Card>
    </Grid>
  );
}

export default function Statistics() {
  const { t } = useTranslation();
  const selectedGameId = useGameStore((s) => s.selectedGameId);
  const [stats, setStats] = useState<GameStatistics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadStats = useCallback(async () => {
    if (!selectedGameId) return;
    setLoading(true);
    try {
      const response = await api.getGameStatistics(selectedGameId);
      if (response.error || !response.data) {
        setError(response.error || t('admin.statistics.failedToLoad'));
        return;
      }
      setError(null);
      setStats(response.data);
    } finally {
      setLoading(false);
    }
  }, [selectedGameId, t]);

  useEffect(() => {
    void loadStats();
  }, [loadStats]);

  useAdminResync(loadStats);

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h4" component="h1" sx={{ mb: 4, fontWeight: 'bold' }}>
        {t('admin.statistics.title')}
      </Typography>

      {!selectedGameId && <Alert severity="info">{t('admin.statistics.noGameSelected')}</Alert>}
      {selectedGameId && error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {selectedGameId && loading && !stats && <CircularProgress />}

      {selectedGameId && stats && (
        <>
          <Typography variant="h6" sx={{ mb: 2 }}>
            {t('admin.statistics.teamsSection')}
          </Typography>
          <Grid container spacing={2} sx={{ mb: 4 }}>
            <StatTile icon={<TeamsIcon />} label={t('admin.statistics.totalTeams')} value={stats.totalTeams} />
            <StatTile
              icon={<CheckedInIcon />}
              label={t('admin.statistics.checkedInTeams')}
              value={stats.checkedInTeams}
            />
            <StatTile
              icon={<DisqualifiedIcon />}
              label={t('admin.statistics.disqualifiedTeams')}
              value={stats.disqualifiedTeams}
            />
          </Grid>

          <Typography variant="h6" sx={{ mb: 2 }}>
            {t('admin.statistics.distanceSection')}
          </Typography>
          <Grid container spacing={2} sx={{ mb: 4 }}>
            <StatTile
              icon={<DistanceIcon />}
              label={t('admin.statistics.totalDistance')}
              value={formatDistance(stats.totalDistanceMeters)}
            />
            <StatTile
              icon={<DistanceIcon />}
              label={t('admin.statistics.averageDistance')}
              value={formatDistance(stats.averageDistanceMeters)}
            />
            <StatTile
              icon={<DistanceIcon />}
              label={t('admin.statistics.minDistance')}
              value={formatDistance(stats.minDistanceMeters)}
            />
            <StatTile
              icon={<DistanceIcon />}
              label={t('admin.statistics.maxDistance')}
              value={formatDistance(stats.maxDistanceMeters)}
            />
          </Grid>

          <Typography variant="h6" sx={{ mb: 2 }}>
            {t('admin.statistics.checkpointsSection')}
          </Typography>
          <Grid container spacing={2} sx={{ mb: 4 }}>
            <StatTile
              icon={<CheckpointsIcon />}
              label={t('admin.statistics.totalCheckpoints')}
              value={stats.totalCheckpoints}
            />
            <StatTile
              icon={<VerificationIcon />}
              label={t('admin.statistics.checkpointVerifications')}
              value={stats.checkpointVerifications}
            />
          </Grid>

          <Typography variant="h6" sx={{ mb: 2 }}>
            {t('admin.statistics.pointsSection')}
          </Typography>
          <Grid container spacing={2}>
            <StatTile icon={<PointsIcon />} label={t('admin.statistics.totalPoints')} value={stats.totalPoints} />
            <StatTile
              icon={<DistanceBonusIcon />}
              label={t('admin.statistics.distanceBonusPoints')}
              value={stats.distanceBonusPoints}
            />
            <StatTile
              icon={<EarlyFinishIcon />}
              label={t('admin.statistics.earlyFinishBonusCount')}
              value={stats.earlyFinishBonusCount}
            />
          </Grid>
        </>
      )}
    </Box>
  );
}
