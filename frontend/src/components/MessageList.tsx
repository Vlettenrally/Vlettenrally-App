/**
 * MessageList Component
 *
 * Renders a list of team messages. Unread messages are highlighted and
 * automatically marked as read once they have been visible long enough
 * (via useInViewMarkAsRead). Reused by the Dashboard for both the recent
 * messages preview and the full "all messages" dialog.
 */
import {
  Box,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Chip,
  Typography,
} from '@mui/material';
import {
  Message as MessageIcon,
  Timer as TimerIcon,
  Flag as FlagIcon,
  Campaign as CampaignIcon,
  EmojiEvents as TrophyIcon,
  CompareArrows as RivalIcon,
} from '@mui/icons-material';
import { useInViewMarkAsRead } from '../hooks/useInViewMarkAsRead';
import type { Message } from '../store/useMessageStore';

const getMessageIcon = (type: string) => {
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
    default:
      return <MessageIcon />;
  }
};

const getMessageBackground = (type: string) => {
  switch (type) {
    case 'countdown':
      return 'rgba(22, 86, 86, 0.08)'; // Light teal - belangrijk
    case 'rallydesk':
      return 'rgba(255, 152, 0, 0.08)'; // Light orange - belangrijk
    // Normale berichten (checkpoint, achievement, rival) geen achtergrond
    default:
      return 'transparent';
  }
};

interface MessageListProps {
  messages: Message[];
  emptyText?: string;
}

export function MessageList({ messages, emptyText = 'Nog geen berichten' }: MessageListProps) {
  const { getMessageRef } = useInViewMarkAsRead();

  if (messages.length === 0) {
    return (
      <Box sx={{ p: 2, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          {emptyText}
        </Typography>
      </Box>
    );
  }

  return (
    <List disablePadding>
      {messages.map((message) => {
        const messageTime = new Date(message.sentAt);
        const timeString = messageTime.toLocaleTimeString('nl-NL', { 
          hour: '2-digit', 
          minute: '2-digit' 
        });
        
        return (
          <ListItem
            key={message.id}
            ref={getMessageRef(message.id, message.read)}
            sx={{
              mb: 0.5,
              borderRadius: 2,
              bgcolor: getMessageBackground(message.messageType),
              border: `1px solid ${getMessageBackground(message.messageType) === 'transparent' ? 'rgba(0,0,0,0.08)' : 'transparent'}`,
              '&:hover': {
                bgcolor: message.messageType === 'general' ? 'action.hover' : getMessageBackground(message.messageType),
                filter: 'brightness(0.95)',
              },
            }}
          >
            <ListItemIcon sx={{ minWidth: 40 }}>
              {getMessageIcon(message.messageType)}
            </ListItemIcon>
            <ListItemText
              primary={
                <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, flexDirection: 'column' }}>
                  <Typography variant="body2" sx={{ fontSize: '0.9rem' }}>
                    {message.content}
                  </Typography>
                  {!message.read && <Chip label="Nieuw" color="primary" size="small" sx={{ height: 20, fontSize: '0.7rem' }} />}
                </Box>
              }
              secondary={
                <Typography variant="caption" sx={{ fontSize: '0.75rem', color: 'text.secondary' }}>
                  {timeString}
                </Typography>
              }
            />
          </ListItem>
        );
      })}
    </List>
  );
}

export default MessageList;
