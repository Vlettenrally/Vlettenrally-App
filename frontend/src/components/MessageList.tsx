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
import { useTranslation } from 'react-i18next';
import { useInViewMarkAsRead } from '../hooks/useInViewMarkAsRead';
import type { Message } from '../store/useMessageStore';
import { getMessageIcon, getMessageBackground } from '../utils/messageAppearance';

interface MessageListProps {
  messages: Message[];
  emptyText?: string;
}

export function MessageList({ messages, emptyText }: MessageListProps) {
  const { t } = useTranslation();
  const { getMessageRef } = useInViewMarkAsRead();
  const effectiveEmptyText = emptyText ?? t('components.messageList.empty');

  if (messages.length === 0) {
    return (
      <Box sx={{ p: 2, textAlign: 'center' }}>
        <Typography variant="body2" color="textSecondary">
          {effectiveEmptyText}
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
                  {!message.read && <Chip label={t('components.messageList.new')} color="primary" size="small" sx={{ height: 20, fontSize: '0.7rem' }} />}
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
