import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Box,
  Checkbox,
  Divider,
  FormControl,
  FormControlLabel,
  IconButton,
  InputLabel,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Select,
  Slide,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import type { SelectChangeEvent } from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import PushPinIcon from '@mui/icons-material/PushPin';
import PushPinOutlinedIcon from '@mui/icons-material/PushPinOutlined';
import CenterFocusStrongIcon from '@mui/icons-material/CenterFocusStrong';
import { RelativeTime } from '../../components/RelativeTime';
import { STALE_AFTER_MS } from './mapConstants';
import { teamMatchesQuery } from '../../utils/teamSearch';

export type TrackWindowMinutes = 15 | 30 | 60 | 120 | 720 | 0; // 0 = all time

export interface LegendTeam {
  id: string;
  name: string;
  description?: string;
  color: string;
  /** ISO timestamp of the team's most recent location fix. */
  locationTime: string;
}

export interface LegendCheckpoint {
  id: string;
  name: string;
  /** Whether the checkpoint has coordinates — controls the zoom-to button. */
  hasLocation: boolean;
}

interface MapLegendSidebarProps {
  open: boolean;
  pinned: boolean;
  onClose: () => void;
  onTogglePin: () => void;
  teams: LegendTeam[];
  checkpoints: LegendCheckpoint[];
  hiddenTeamIds: Set<string>;
  hiddenCheckpointIds: Set<string>;
  trackTeamIds: Set<string>;
  windowMinutes: TrackWindowMinutes;
  /** Current time (ms) supplied by the parent's staleness tick, used to flag stale teams. */
  now: number;
  onToggleTeam: (id: string) => void;
  onToggleCheckpoint: (id: string) => void;
  onToggleTrack: (id: string) => void;
  onSetTeamsHidden: (ids: string[], hidden: boolean) => void;
  onSetCheckpointsHidden: (ids: string[], hidden: boolean) => void;
  onWindowChange: (mins: TrackWindowMinutes) => void;
  /** Fly the map camera to a team's current position. */
  onZoomTeam: (id: string) => void;
  /** Fly the map camera to a checkpoint's position. */
  onZoomCheckpoint: (id: string) => void;
}

interface MasterState {
  checked: boolean;
  indeterminate: boolean;
}

function masterState<T extends { id: string }>(items: T[], hidden: Set<string>): MasterState {
  if (items.length === 0) return { checked: false, indeterminate: false };
  const visibleCount = items.reduce((n, item) => n + (hidden.has(item.id) ? 0 : 1), 0);
  if (visibleCount === items.length) return { checked: true, indeterminate: false };
  if (visibleCount === 0) return { checked: false, indeterminate: false };
  return { checked: false, indeterminate: true };
}

export function MapLegendSidebar({
  open,
  pinned,
  onClose,
  onTogglePin,
  teams,
  checkpoints,
  hiddenTeamIds,
  hiddenCheckpointIds,
  trackTeamIds,
  windowMinutes,
  now,
  onToggleTeam,
  onToggleCheckpoint,
  onToggleTrack,
  onSetTeamsHidden,
  onSetCheckpointsHidden,
  onWindowChange,
  onZoomTeam,
  onZoomCheckpoint,
}: MapLegendSidebarProps) {
  const { t } = useTranslation();
  const [teamQuery, setTeamQuery] = useState('');

  const WINDOW_OPTIONS: { value: TrackWindowMinutes; label: string }[] = [
    { value: 15, label: t('admin.map.window15min') },
    { value: 30, label: t('admin.map.window30min') },
    { value: 60, label: t('admin.map.window60min') },
    { value: 120, label: t('admin.map.window120min') },
    { value: 720, label: t('admin.map.window12h') },
    { value: 0, label: t('admin.map.windowAll') },
  ];

  const filteredTeams = useMemo(
    () => teams.filter(team => teamMatchesQuery(team, teamQuery)),
    [teams, teamQuery],
  );

  // Master checkboxes operate on the FULL list, ignoring the search filter.
  const teamsMaster = masterState(teams, hiddenTeamIds);
  const checkpointsMaster = masterState(checkpoints, hiddenCheckpointIds);

  const handleWindowChange = (e: SelectChangeEvent<number>) => {
    onWindowChange(Number(e.target.value) as TrackWindowMinutes);
  };

  const handleTeamsMasterChange = () => {
    const newHidden = teamsMaster.checked;
    onSetTeamsHidden(teams.map(team => team.id), newHidden);
  };

  const handleCheckpointsMasterChange = () => {
    const newHidden = checkpointsMaster.checked;
    onSetCheckpointsHidden(checkpoints.map(c => c.id), newHidden);
  };

  const pinLabel = pinned ? t('admin.map.unpin') : t('admin.map.pin');

  return (
    <Slide direction="left" in={open} mountOnEnter unmountOnExit>
      <Box
        onMouseDown={(e) => e.stopPropagation()}
        sx={{
          position: 'absolute',
          top: 0,
          right: 0,
          bottom: 0,
          width: 320,
          bgcolor: 'background.paper',
          boxShadow: 4,
          display: 'flex',
          flexDirection: 'column',
          zIndex: 1200,
        }}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 2,
            py: 1,
            borderBottom: 1,
            borderColor: 'divider',
            position: 'sticky',
            top: 0,
            bgcolor: 'background.paper',
            zIndex: 1,
          }}
        >
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t('admin.map.filters')}</Typography>
          <Box>
            <Tooltip title={pinLabel}>
              <IconButton size="small" onClick={onTogglePin} aria-label={pinLabel}>
                {pinned ? <PushPinIcon fontSize="small" /> : <PushPinOutlinedIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
            <Tooltip title={t('admin.map.close')}>
              <IconButton size="small" onClick={onClose} aria-label={t('admin.map.close')}>
                <CloseIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>

        <Box sx={{ p: 2, flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          {/* Teams section: grows to consume remaining height, list scrolls internally */}
          <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 0.5, flexShrink: 0 }}>
              <Checkbox
                size="small"
                checked={teamsMaster.checked}
                indeterminate={teamsMaster.indeterminate}
                disabled={teams.length === 0}
                onChange={handleTeamsMasterChange}
                sx={{ p: 0.5 }}
              />
              <Typography variant="subtitle2">{t('admin.map.teams')}</Typography>
            </Box>
            <TextField
              size="small"
              fullWidth
              placeholder={t('admin.map.searchTeam')}
              value={teamQuery}
              onChange={(e) => setTeamQuery(e.target.value)}
              sx={{ mb: 1, flexShrink: 0 }}
            />
            <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
              {teams.length === 0 ? (
                <Typography variant="body2" color="text.secondary">{t('admin.map.noTeams')}</Typography>
              ) : filteredTeams.length === 0 ? (
                <Typography variant="body2" color="text.secondary">{t('admin.map.noTeamsFound')}</Typography>
              ) : (
                <List dense disablePadding>
                  {filteredTeams.map(team => {
                const visible = !hiddenTeamIds.has(team.id);
                const trackOn = trackTeamIds.has(team.id);
                const ts = Date.parse(team.locationTime);
                const stale = !Number.isNaN(ts) && now - ts > STALE_AFTER_MS;
                return (
                  <ListItem
                    key={team.id}
                    disableGutters
                    sx={{ py: 0.25 }}
                    secondaryAction={
                      <Box sx={{ display: 'flex', alignItems: 'center' }}>
                        <Tooltip title={t('admin.map.zoomToLocation')}>
                          <IconButton
                            size="small"
                            edge={false}
                            onClick={() => onZoomTeam(team.id)}
                            aria-label={t('admin.map.zoomToLocation')}
                          >
                            <CenterFocusStrongIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <FormControlLabel
                          sx={{ mr: 0 }}
                          control={
                            <Checkbox
                              size="small"
                              checked={trackOn}
                              disabled={!visible}
                              onChange={() => onToggleTrack(team.id)}
                            />
                          }
                          label={<Typography variant="caption">{t('admin.map.track')}</Typography>}
                        />
                      </Box>
                    }
                  >
                    <Checkbox
                      size="small"
                      checked={visible}
                      onChange={() => onToggleTeam(team.id)}
                      sx={{ p: 0.5 }}
                    />
                    <Box
                      sx={{
                        width: 12,
                        height: 12,
                        borderRadius: '50%',
                        bgcolor: team.color,
                        mr: 1,
                        flexShrink: 0,
                      }}
                    />
                    <ListItemText
                      primary={team.description ? `${team.name} · ${team.description}` : team.name}
                      secondary={<RelativeTime date={team.locationTime} />}
                      slotProps={{
                        primary: { variant: 'body2', noWrap: true },
                        secondary: {
                          variant: 'caption',
                          noWrap: true,
                          color: stale ? 'warning.main' : 'text.secondary',
                        },
                      }}
                    />
                  </ListItem>
                );
              })}
                </List>
              )}
            </Box>
          </Box>

          <Divider sx={{ my: 1.5, flexShrink: 0 }} />

          {/* Checkpoints section */}
          <Box sx={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 0.5, flexShrink: 0 }}>
              <Checkbox
                size="small"
                checked={checkpointsMaster.checked}
                indeterminate={checkpointsMaster.indeterminate}
                disabled={checkpoints.length === 0}
                onChange={handleCheckpointsMasterChange}
                sx={{ p: 0.5 }}
              />
              <Typography variant="subtitle2">{t('admin.map.checkpoints')}</Typography>
            </Box>
            <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
              {checkpoints.length === 0 ? (
                <Typography variant="body2" color="text.secondary">{t('admin.map.noCheckpoints')}</Typography>
              ) : (
                <List dense disablePadding>
                  {checkpoints.map(cp => {
                    const visible = !hiddenCheckpointIds.has(cp.id);
                    return (
                      <ListItem
                        key={cp.id}
                        disableGutters
                        sx={{ py: 0.25 }}
                        secondaryAction={
                          <Tooltip title={t('admin.map.zoomToLocation')}>
                            <span>
                              <IconButton
                                size="small"
                                edge={false}
                                disabled={!cp.hasLocation}
                                onClick={() => onZoomCheckpoint(cp.id)}
                                aria-label={t('admin.map.zoomToLocation')}
                              >
                                <CenterFocusStrongIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                        }
                      >
                        <Checkbox
                          size="small"
                          checked={visible}
                          onChange={() => onToggleCheckpoint(cp.id)}
                          sx={{ p: 0.5 }}
                        />
                        <Box
                          sx={{
                            width: 12,
                            height: 12,
                            borderRadius: '50%',
                            bgcolor: '#ff9800',
                            mr: 1,
                            flexShrink: 0,
                          }}
                        />
                        <ListItemText
                          primary={cp.name}
                          slotProps={{ primary: { variant: 'body2', noWrap: true } }}
                        />
                      </ListItem>
                    );
                  })}
                </List>
              )}
            </Box>
          </Box>

          <Divider sx={{ my: 1.5, flexShrink: 0 }} />

          <FormControl fullWidth size="small" disabled={trackTeamIds.size === 0} sx={{ flexShrink: 0 }}>
            <InputLabel id="track-window-label">{t('admin.map.trackWindow')}</InputLabel>
            <Select
              labelId="track-window-label"
              label={t('admin.map.trackWindow')}
              value={windowMinutes}
              onChange={handleWindowChange}
            >
              {WINDOW_OPTIONS.map(o => (
                <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>
      </Box>
    </Slide>
  );
}

export default MapLegendSidebar;
