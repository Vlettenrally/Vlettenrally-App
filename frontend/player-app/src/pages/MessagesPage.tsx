/**
 * Messages Page - View all messages
 */
import { useEffect } from 'react';
import {
  Box,
  Container,
  Typography,
  Paper,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Chip,
  Alert,
} from '@mui/material';
import {
  Message as MessageIcon,
  Timer as TimerIcon,
  Flag as FlagIcon,
  Campaign as CampaignIcon,
  EmojiEvents as TrophyIcon,
  CompareArrows as RivalIcon,
} from '@mui/icons-material';
import { useMessageStore } from '../store/useMessageStore';
import { api } from '../services/api';
import { storage } from '../services/storage';
import { formatRelativeTime } from '../utils/dateTime';

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

export function MessagesPage() {
  const { messages, setMessages, markAsRead, isLoading, error } = useMessageStore();
  const teamId = storage.getTeamId();

  useEffect(() => {
    if (!teamId) return;

    const loadMessages = async () => {
      const response = await api.getMessages(teamId);
      if (response.data) {
        setMessages(response.data as Array<any>);
      }
    };

    loadMessages();
  }, [teamId, setMessages]);

  const handleMessageClick = async (messageId: string) => {
    markAsRead(messageId);
    await api.markMessageAsRead(messageId);
  };

  if (error) {
    return (
      <Container maxWidth="md">
        <Box sx={{ mt: 3 }}>
          <Alert severity="error">{error}</Alert>
        </Box>
      </Container>
    );
  }

  return (
    <Container maxWidth="md">
      <Box sx={{ mt: 3, mb: 3 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Berichten
        </Typography>

        {messages.length === 0 && !isLoading && (
          <Paper elevation={2} sx={{ p: 4, textAlign: 'center' }}>
            <MessageIcon sx={{ fontSize: 60, color: 'text.secondary', mb: 2 }} />
            <Typography variant="h6" color="text.secondary">
              Nog geen berichten
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Je ontvangt hier updates tijdens de rally
            </Typography>
          </Paper>
        )}

        {messages.length > 0 && (
          <Paper elevation={2}>
            <List>
              {messages.map((message, index) => (
                <ListItem
                  key={message.id}
                  sx={{
                    borderBottom:
                      index < messages.length - 1 ? '1px solid rgba(0, 0, 0, 0.12)' : 'none',
                    bgcolor: message.read ? 'inherit' : 'action.hover',
                    cursor: 'pointer',
                    '&:hover': {
                      bgcolor: 'action.selected',
                    },
                  }}
                  onClick={() => handleMessageClick(message.id)}
                >
                  <ListItemIcon>{getMessageIcon(message.messageType)}</ListItemIcon>
                  <ListItemText
                    primary={
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                        <Typography variant="body1">{message.content}</Typography>
                        {!message.read && (
                          <Chip label="Nieuw" color="primary" size="small" />
                        )}
                      </Box>
                    }
                    secondary={formatRelativeTime(message.sentAt)}
                  />
                </ListItem>
              ))}
            </List>
          </Paper>
        )}
      </Box>
    </Container>
  );
}
