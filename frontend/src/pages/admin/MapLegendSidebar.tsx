import { useMemo, useState } from 'react';
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
import { RelativeTime } from '../../components/RelativeTime';
import { STALE_AFTER_MS } from './mapConstants';

export type TrackWindowMinutes = 15 | 30 | 60 | 120 | 720 | 0; // 0 = all time

export interface LegendTeam {
  id: string;
  name: string;
  color: string;
  /** ISO timestamp of the team's most recent location fix. */
  locationTime: string;
}

export interface LegendCheckpoint {
  id: string;
  name: string;
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
}

const WINDOW_OPTIONS: { value: TrackWindowMinutes; label: string }[] = [
  { value: 15, label: '15 minuten' },
  { value: 30, label: '30 minuten' },
  { value: 60, label: '60 minuten' },
  { value: 120, label: '120 minuten' },
  { value: 720, label: '12 uur' },
  { value: 0, label: 'Alles' },
];

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
}: MapLegendSidebarProps) {
  const [teamQuery, setTeamQuery] = useState('');

  const filteredTeams = useMemo(() => {
    const q = teamQuery.trim().toLowerCase();
    if (!q) return teams;
    return teams.filter(t => t.name.toLowerCase().includes(q));
  }, [teams, teamQuery]);

  // Master checkboxes operate on the FULL list, ignoring the search filter.
  const teamsMaster = masterState(teams, hiddenTeamIds);
  const checkpointsMaster = masterState(checkpoints, hiddenCheckpointIds);

  const handleWindowChange = (e: SelectChangeEvent<number>) => {
    onWindowChange(Number(e.target.value) as TrackWindowMinutes);
  };

  const handleTeamsMasterChange = () => {
    const newHidden = teamsMaster.checked;
    onSetTeamsHidden(teams.map(t => t.id), newHidden);
  };

  const handleCheckpointsMasterChange = () => {
    const newHidden = checkpointsMaster.checked;
    onSetCheckpointsHidden(checkpoints.map(c => c.id), newHidden);
  };

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
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>Filters</Typography>
          <Box>
            <Tooltip title={pinned ? 'Losmaken' : 'Vastpinnen'}>
              <IconButton size="small" onClick={onTogglePin} aria-label={pinned ? 'Losmaken' : 'Vastpinnen'}>
                {pinned ? <PushPinIcon fontSize="small" /> : <PushPinOutlinedIcon fontSize="small" />}
              </IconButton>
            </Tooltip>
            <Tooltip title="Sluiten">
              <IconButton size="small" onClick={onClose} aria-label="Sluiten">
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
              <Typography variant="subtitle2">Teams</Typography>
            </Box>
            <TextField
              size="small"
              fullWidth
              placeholder="Zoek team…"
              value={teamQuery}
              onChange={(e) => setTeamQuery(e.target.value)}
              sx={{ mb: 1, flexShrink: 0 }}
            />
            <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
              {teams.length === 0 ? (
                <Typography variant="body2" color="text.secondary">Geen teams</Typography>
              ) : filteredTeams.length === 0 ? (
                <Typography variant="body2" color="text.secondary">Geen teams gevonden</Typography>
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
                        label={<Typography variant="caption">spoor</Typography>}
                      />
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
                      primary={team.name}
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

          {/* Checkpoints section: shares remaining height with the teams section, scrolls internally */}
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
              <Typography variant="subtitle2">Checkpoints</Typography>
            </Box>
            <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
              {checkpoints.length === 0 ? (
                <Typography variant="body2" color="text.secondary">Geen checkpoints</Typography>
              ) : (
                <List dense disablePadding>
                  {checkpoints.map(cp => {
                    const visible = !hiddenCheckpointIds.has(cp.id);
                    return (
                      <ListItem key={cp.id} disableGutters sx={{ py: 0.25 }}>
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
            <InputLabel id="track-window-label">Spoorvenster</InputLabel>
            <Select
              labelId="track-window-label"
              label="Spoorvenster"
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
