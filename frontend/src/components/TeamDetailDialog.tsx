import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Box,
  Typography,
  Chip,
  CircularProgress,
  Alert,
  FormControlLabel,
  Checkbox,
} from '@mui/material';
import { useState, useEffect } from 'react';
import type { AdminTeam } from '../types/entities';
import { teamService } from '../services/teamService';

interface TeamDetailDialogProps {
  open: boolean;
  onClose: () => void;
  team: AdminTeam | null;
  gameId: string;
  onSave?: () => void;
}

export default function TeamDetailDialog({
  open,
  onClose,
  team,
  gameId,
  onSave,
}: TeamDetailDialogProps) {
  const [isEditing, setIsEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rivalTeams, setRivalTeams] = useState<AdminTeam[]>([]);
  const [loadingRivals, setLoadingRivals] = useState(false);
  
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    participants: '',
    emergencyPhoneNumbers: '',
    notes: '',
    checkedIn: false,
    checkedInDigitally: false,
    totalPoints: 0,
  });

  const isCreateMode = !team;

  useEffect(() => {
    if (team) {
      setFormData({
        name: team.name,
        description: team.description,
        participants: team.participants ? team.participants.join('\n') : '',
        emergencyPhoneNumbers: team.emergencyPhoneNumbers ? team.emergencyPhoneNumbers.join('\n') : '',
        notes: team.notes || '',
        checkedIn: team.checkedIn,
        checkedInDigitally: team.checkedInDigitally,
        totalPoints: team.totalPoints,
      });
      setIsEditing(false);
      
      // Load rival teams
      if (team.rivals && team.rivals.length > 0) {
        setLoadingRivals(true);
        Promise.all(
          team.rivals.map((rivalId: string) => 
            teamService.getTeam(rivalId, team.gameId)
          )
        )
          .then((rivals) => {
            setRivalTeams(rivals.filter((r: AdminTeam | null): r is AdminTeam => r !== null));
          })
          .catch((err) => {
            console.error('Failed to load rival teams:', err);
          })
          .finally(() => {
            setLoadingRivals(false);
          });
      } else {
        setRivalTeams([]);
      }
    } else {
      setFormData({
        name: '',
        description: '',
        participants: '',
        emergencyPhoneNumbers: '',
        notes: '',
        checkedIn: false,
        checkedInDigitally: false,
        totalPoints: 0,
      });
      setIsEditing(true);
      setRivalTeams([]);
    }
    setError(null);
  }, [team, gameId]);

  const handleEdit = () => {
    setIsEditing(true);
    setError(null);
  };

  const handleCancel = () => {
    if (isCreateMode) {
      onClose();
    } else {
      setIsEditing(false);
      if (team) {
        setFormData({
          name: team.name,
          description: team.description,
          participants: team.participants.join('\n'),
          emergencyPhoneNumbers: team.emergencyPhoneNumbers ? team.emergencyPhoneNumbers.join('\n') : '',
          notes: team.notes || '',
          checkedIn: team.checkedIn,
          checkedInDigitally: team.checkedInDigitally,
          totalPoints: team.totalPoints,
        });
      }
      setError(null);
    }
  };

  const handleSave = async () => {
    setLoading(true);
    setError(null);

    try {
      const participants = formData.participants
        .split('\n')
        .map((p) => p.trim())
        .filter((p) => p.length > 0);

      const emergencyPhoneNumbers = formData.emergencyPhoneNumbers
        .split('\n')
        .map((p) => p.trim())
        .filter((p) => p.length > 0);

      const teamData = {
        name: formData.name,
        description: formData.description,
        participants,
        emergencyPhoneNumbers,
        notes: formData.notes || undefined,
        checkedIn: formData.checkedIn,
        checkedInDigitally: formData.checkedInDigitally,
        totalPoints: formData.totalPoints,
      };

      if (isCreateMode) {
        await teamService.createTeam({
          ...teamData,
          gameId: gameId,
        });
      } else if (team) {
        await teamService.updateTeam(team.id, teamData);
      }

      setIsEditing(false);
      onSave?.();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save team');
    } finally {
      setLoading(false);
    }
  };

  const handleChange = (field: keyof typeof formData) => (
    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
  ) => {
    setFormData((prev) => ({
      ...prev,
      [field]: event.target.value,
    }));
  };

  const handleCheckboxChange = (field: 'checkedIn' | 'checkedInDigitally') => (
    event: React.ChangeEvent<HTMLInputElement>
  ) => {
    const newValue = event.target.checked;
    setFormData((prev) => {
      // If unchecking checkedIn, also uncheck checkedInDigitally
      if (field === 'checkedIn' && !newValue) {
        return {
          ...prev,
          checkedIn: false,
          checkedInDigitally: false,
        };
      }
      return {
        ...prev,
        [field]: newValue,
      };
    });
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        {isCreateMode ? 'Create New Team' : isEditing ? 'Edit Team' : 'Team Details'}
      </DialogTitle>
      <DialogContent>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
          <TextField
            label="Team Name"
            value={formData.name}
            onChange={handleChange('name')}
            disabled={!isEditing}
            required
            fullWidth
          />

          <TextField
            label="Description"
            value={formData.description}
            onChange={handleChange('description')}
            disabled={!isEditing}
            multiline
            rows={2}
            fullWidth
          />

          <TextField
            label="Participants"
            value={formData.participants}
            onChange={handleChange('participants')}
            disabled={!isEditing}
            multiline
            rows={4}
            fullWidth
            helperText="One participant per line"
        />
                  
          <TextField
            label="Emergency Phone Numbers"
            value={formData.emergencyPhoneNumbers}
            onChange={handleChange('emergencyPhoneNumbers')}
            disabled={!isEditing}
            multiline
            rows={3}
            fullWidth
            helperText="One phone number per line"
          />

          <TextField
            label="Notes"
            value={formData.notes}
            onChange={handleChange('notes')}
            disabled={!isEditing}
            multiline
            rows={3}
            fullWidth
          />

          {!isCreateMode && team && (
            <>
              {isEditing ? (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Check-in Status (Admin Override)
                  </Typography>
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.checkedIn}
                        onChange={handleCheckboxChange('checkedIn')}
                      />
                    }
                    label="Checked In"
                  />
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={formData.checkedInDigitally}
                        onChange={handleCheckboxChange('checkedInDigitally')}
                        disabled={!formData.checkedIn}
                      />
                    }
                    label="Checked In Digitally"
                  />
                </Box>
              ) : (
                <Box>
                  <Typography variant="subtitle2" gutterBottom>
                    Status
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    <Chip
                      label={team.checkedIn ? 'Checked In' : 'Not Checked In'}
                      color={team.checkedIn ? 'success' : 'default'}
                      size="small"
                    />
                    {team.checkedIn && (
                      <Chip
                        label={team.checkedInDigitally ? 'Digital Check-in' : 'Manual Check-in'}
                        color={team.checkedInDigitally ? 'info' : 'warning'}
                        size="small"
                        variant="outlined"
                      />
                    )}
                  </Box>
                </Box>
              )}

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Points
                </Typography>
                {isEditing ? (
                  <TextField
                    type="number"
                    value={formData.totalPoints}
                    onChange={(e) => {
                      const value = Math.max(0, parseInt(e.target.value, 10) || 0);
                      setFormData((prev) => ({ ...prev, totalPoints: value }));
                    }}
                    slotProps={{ htmlInput: { min: 0 } }}
                    size="small"
                    fullWidth
                  />
                ) : (
                  <Typography>{team.totalPoints}</Typography>
                )}
              </Box>

              <Box>
                <Typography variant="subtitle2" gutterBottom>
                  Rival Teams
                </Typography>
                {loadingRivals ? (
                  <CircularProgress size={20} />
                ) : rivalTeams.length > 0 ? (
                  <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                    {rivalTeams.map((rival) => (
                      <Chip key={rival.id} label={rival.name} size="small" />
                    ))}
                  </Box>
                ) : (
                  <Typography color="text.secondary">No rival teams</Typography>
                )}
              </Box>
            </>
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        {isEditing ? (
          <>
            <Button onClick={handleCancel} disabled={loading}>
              Cancel
            </Button>
            <Button
              onClick={handleSave}
              variant="contained"
              disabled={loading || !formData.name}
            >
              {loading ? <CircularProgress size={24} /> : 'Save'}
            </Button>
          </>
        ) : (
          <>
            <Button onClick={onClose}>Close</Button>
            <Button onClick={handleEdit} variant="contained">
              Edit
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
