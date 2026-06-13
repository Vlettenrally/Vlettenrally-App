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
import { RelativeTime } from './RelativeTime';
import type { Message } from '../store/useMessageStore';

const getMessageIcon = (type: string) => {
  switch (type) {
    case 'countdown':
      return <TimerIcon color="primary" />;
    case 'checkpoint':
      return <FlagIcon color="success" />;
    case 'rallydesk':
      return <CampaignIcon color="warning" />;
    case 'achievement':
      return <TrophyIcon color="secondary" />;
    case 'rival':
      return <RivalIcon color="info" />;
    default:
      return <MessageIcon />;
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
      {messages.map((message, index) => (
        <ListItem
          key={message.id}
          ref={getMessageRef(message.id, message.read)}
          sx={{
            borderBottom:
              index < messages.length - 1 ? '1px solid rgba(0, 0, 0, 0.12)' : 'none',
            bgcolor: message.read ? 'inherit' : 'action.hover',
            '&:hover': {
              bgcolor: 'action.selected',
            },
          }}
        >
          <ListItemIcon>{getMessageIcon(message.messageType)}</ListItemIcon>
          <ListItemText
            primary={
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                <Typography variant="body1">{message.content}</Typography>
                {!message.read && <Chip label="Nieuw" color="primary" size="small" />}
              </Box>
            }
            secondary={<RelativeTime date={message.sentAt} />}
          />
        </ListItem>
      ))}
    </List>
  );
}

export default MessageList;
