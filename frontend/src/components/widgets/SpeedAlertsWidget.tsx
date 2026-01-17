/**
 * Speed Alerts Widget - Shows recent speed limit violations
 */
import { useState, useEffect } from 'react';
import {
  Card,
  CardContent,
  Typography,
  Box,
  List,
  ListItem,
  ListItemText,
  Chip,
} from '@mui/material';
import { Speed as SpeedIcon, Warning as WarningIcon } from '@mui/icons-material';

interface SpeedAlert {
  id: string;
  teamName: string;
  speed: number;
  timestamp: string;
}

interface SpeedAlertsWidgetProps {
  collapsed?: boolean;
}

export function SpeedAlertsWidget({ collapsed = false }: SpeedAlertsWidgetProps) {
  const [alerts, _setAlerts] = useState<SpeedAlert[]>([]);

  // In a real implementation, this would connect to WebSocket for live updates
  // For now, we'll just show the structure with empty state
  useEffect(() => {
    // Will use _setAlerts when WebSocket is connected
    void _setAlerts;
    // Placeholder for WebSocket connection
    // const ws = new WebSocket(...);
    // ws.onmessage = (event) => {
    //   if (event.type === 'alert:speed') {
    //     setAlerts(prev => [event.data, ...prev].slice(0, 5));
    //   }
    // };
  }, []);

  if (collapsed) {
    return (
      <Box sx={{ textAlign: 'center', py: 1 }}>
        <SpeedIcon color={alerts.length > 0 ? 'error' : 'disabled'} />
        <Typography variant="caption" display="block">
          {alerts.length}
        </Typography>
      </Box>
    );
  }

  return (
    <Card sx={{ mb: 2 }}>
      <CardContent sx={{ pb: '12px !important' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
          <SpeedIcon sx={{ mr: 1, color: 'primary.main' }} fontSize="small" />
          <Typography variant="subtitle2" color="text.secondary">
            Speed Alerts
          </Typography>
          {alerts.length > 0 && (
            <Chip
              label={alerts.length}
              size="small"
              color="error"
              sx={{ ml: 'auto', height: 20 }}
            />
          )}
        </Box>
        {alerts.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No recent alerts
          </Typography>
        ) : (
          <List dense disablePadding>
            {alerts.slice(0, 3).map((alert) => (
              <ListItem key={alert.id} disablePadding sx={{ py: 0.5 }}>
                <WarningIcon color="error" sx={{ fontSize: 16, mr: 1 }} />
                <ListItemText
                  primary={alert.teamName}
                  secondary={`${alert.speed} km/h`}
                  primaryTypographyProps={{ variant: 'body2' }}
                  secondaryTypographyProps={{ variant: 'caption' }}
                />
              </ListItem>
            ))}
          </List>
        )}
      </CardContent>
    </Card>
  );
}
