/**
 * Shared visual mapping for message types (icon + background tint).
 *
 * Used by the Dashboard message feed (MessageList) and the in-app new-message
 * banner (NewMessageBanner) so both surfaces stay visually consistent.
 */
import {
  Message as MessageIcon,
  Timer as TimerIcon,
  Flag as FlagIcon,
  Campaign as CampaignIcon,
  EmojiEvents as TrophyIcon,
  CompareArrows as RivalIcon,
  Speed as SpeedIcon,
} from '@mui/icons-material';

export const getMessageIcon = (type: string) => {
  switch (type) {
    case 'countdown':
      return <TimerIcon sx={{ color: '#165656' }} />;
    case 'checkpoint':
      return <FlagIcon sx={{ color: '#4caf50' }} />;
    case 'rallydesk':
      return <CampaignIcon sx={{ color: '#ff9800' }} />;
    case 'achievement':
      return <TrophyIcon sx={{ color: '#ffcb0f' }} />;
    case 'rival':
      return <RivalIcon sx={{ color: '#2196f3' }} />;
    case 'speed_warning':
      return <SpeedIcon sx={{ color: '#f44336' }} />;
    default:
      return <MessageIcon />;
  }
};

export const getMessageBackground = (type: string) => {
  switch (type) {
    case 'countdown':
      return 'rgba(22, 86, 86, 0.08)'; // Light teal - belangrijk
    case 'rallydesk':
      return 'rgba(255, 152, 0, 0.08)'; // Light orange - belangrijk
    case 'speed_warning':
      return 'rgba(244, 67, 54, 0.08)'; // Light red - waarschuwing
    // Normale berichten (checkpoint, achievement, rival) geen achtergrond
    default:
      return 'transparent';
  }
};
