import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Button,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Chip,
  IconButton,
  Tooltip,
  CircularProgress,
  Alert,
  Collapse,
  TablePagination,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material';
import {
  Send as SendIcon,
  Refresh as RefreshIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Add as AddIcon,
  CheckCircle as CheckCircleIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
} from '@mui/icons-material';
import { useGameStore } from '../../store/useGameStore';
import type { AdminMessage, MessageTemplate, AdminTeam } from '../../types/entities';
import api from '../../services/api';
import websocket, { WebSocketEventType } from '../../services/websocket';
import SendMessageDialog from '../../components/SendMessageDialog';
import TemplateFormDialog from '../../components/TemplateFormDialog';

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;
  return (
    <div role="tabpanel" hidden={value !== index} {...other}>
      {value === index && <Box sx={{ py: 3 }}>{children}</Box>}
    </div>
  );
}

export default function AdminMessages() {
  const selectedGameId = useGameStore((s) => s.selectedGameId);

  // Tab state
  const [tabValue, setTabValue] = useState(0);

  // Teams state (fetched from API)
  const [teams, setTeams] = useState<AdminTeam[]>([]);

  // Messages state
  const [messages, setMessages] = useState<AdminMessage[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messagesError, setMessagesError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalCount, setTotalCount] = useState(0);
  const [expandedMessageId, setExpandedMessageId] = useState<string | null>(null);

  // Templates state
  const [templates, setTemplates] = useState<MessageTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templatesError, setTemplatesError] = useState<string | null>(null);

  // Dialog state
  const [sendDialogOpen, setSendDialogOpen] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<MessageTemplate | null>(null);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [templateToDelete, setTemplateToDelete] = useState<MessageTemplate | null>(null);
  const [selectedTemplateForSend, setSelectedTemplateForSend] = useState<MessageTemplate | null>(null);

  // Retry state
  const [retrying, setRetrying] = useState<string | null>(null);
  const [retryAllLoading, setRetryAllLoading] = useState(false);

  // Fetch teams
  const fetchTeams = useCallback(async (gameId: string) => {
    const result = await api.getTeamsByGame(gameId);
    if (result.data) {
      setTeams(result.data);
    }
  }, []);

  // Fetch messages
  const fetchMessages = useCallback(async () => {
    if (!selectedGameId) return;

    setMessagesLoading(true);
    setMessagesError(null);

    const result = await api.getAdminMessagesByGame(selectedGameId, page, pageSize);

    setMessagesLoading(false);

    if (result.error) {
      setMessagesError(result.error);
      return;
    }

    if (result.data) {
      setMessages(result.data.messages);
      setTotalCount(result.data.totalCount);
    }
  }, [selectedGameId, page, pageSize]);

  // Fetch templates
  const fetchTemplates = useCallback(async () => {
    setTemplatesLoading(true);
    setTemplatesError(null);

    const result = await api.getMessageTemplates();

    setTemplatesLoading(false);

    if (result.error) {
      setTemplatesError(result.error);
      return;
    }

    if (result.data) {
      setTemplates(result.data);
    }
  }, []);

  // Load data when game changes
  useEffect(() => {
    if (selectedGameId) {
      const loadData = async () => {
        await fetchTeams(selectedGameId);
        await fetchMessages();
      };
      void loadData();
    }
  }, [selectedGameId, fetchTeams, fetchMessages]);

  // Load templates on mount
  useEffect(() => {
    const loadData = async () => {
      await fetchTemplates();
    };
    void loadData();
  }, [fetchTemplates]);

  // Subscribe to WebSocket for real-time delivery updates
  useEffect(() => {
    if (!selectedGameId) return;

    // Subscribe to delivery updates
    const unsubscribe = websocket.on(WebSocketEventType.MESSAGE_DELIVERY_UPDATE, () => {
      // Refresh messages when delivery status changes
      void fetchMessages();
    });

    return () => {
      unsubscribe();
    };
  }, [selectedGameId, fetchMessages]);

  // Handle retry single message
  const handleRetry = async (messageId: string) => {
    setRetrying(messageId);
    await api.retryFailedDeliveries(messageId);
    setRetrying(null);
    fetchMessages();
  };

  // Handle retry all failed
  const handleRetryAll = async () => {
    if (!selectedGameId) return;
    setRetryAllLoading(true);
    await api.retryAllFailedDeliveries(selectedGameId);
    setRetryAllLoading(false);
    fetchMessages();
  };

  // Handle delete template
  const handleDeleteTemplate = async () => {
    if (!templateToDelete) return;
    await api.deleteMessageTemplate(templateToDelete.id);
    setDeleteConfirmOpen(false);
    setTemplateToDelete(null);
    fetchTemplates();
  };

  // Calculate total failed deliveries
  const totalFailedDeliveries = messages.reduce((sum, m) => sum + m.failedCount, 0);

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleString('nl-NL', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const getMessageTypeLabel = (type: string) => {
    const labels: Record<string, string> = {
      general: 'Algemeen',
      countdown: 'Countdown',
      checkpoint: 'Checkpoint',
      rallydesk: 'Rallydesk',
      achievement: 'Achievement',
      rival: 'Rivaal',
    };
    return labels[type] || type;
  };

  const getMessageTypeColor = (type: string): 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning' => {
    const colors: Record<string, 'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'> = {
      general: 'default',
      countdown: 'info',
      checkpoint: 'success',
      rallydesk: 'warning',
      achievement: 'secondary',
      rival: 'primary',
    };
    return colors[type] || 'default';
  };

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Berichten
        </Typography>
        <Button
          variant="contained"
          startIcon={<SendIcon />}
          onClick={() => setSendDialogOpen(true)}
          disabled={!selectedGameId}
        >
          Bericht versturen
        </Button>
      </Box>

      {!selectedGameId ? (
        <Alert severity="info">Selecteer een spel in de header om berichten te beheren.</Alert>
      ) : (
        <>
          <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tabs value={tabValue} onChange={(_, newValue) => setTabValue(newValue)}>
              <Tab label="Berichten" />
              <Tab label="Templates" />
            </Tabs>
          </Box>

          {/* Messages Tab */}
          <TabPanel value={tabValue} index={0}>
            {messagesError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {messagesError}
              </Alert>
            )}

            {/* Retry all button */}
            {totalFailedDeliveries > 0 && (
              <Box sx={{ mb: 2 }}>
                <Button
                  variant="outlined"
                  color="warning"
                  startIcon={retryAllLoading ? <CircularProgress size={20} /> : <RefreshIcon />}
                  onClick={handleRetryAll}
                  disabled={retryAllLoading}
                >
                  Alle mislukte verzendingen opnieuw proberen ({totalFailedDeliveries})
                </Button>
              </Box>
            )}

            <TableContainer component={Paper}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell width={40} />
                    <TableCell>Tijd</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Bericht</TableCell>
                    <TableCell>Bezorging</TableCell>
                    <TableCell>Acties</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {messagesLoading ? (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                        <CircularProgress />
                      </TableCell>
                    </TableRow>
                  ) : messages.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                        <Typography color="text.secondary">
                          Nog geen berichten verstuurd
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    messages.map((message) => (
                      <>
                        <TableRow key={message.id} hover>
                          <TableCell>
                            <IconButton
                              size="small"
                              onClick={() =>
                                setExpandedMessageId(
                                  expandedMessageId === message.id ? null : message.id
                                )
                              }
                            >
                              {expandedMessageId === message.id ? (
                                <ExpandLessIcon />
                              ) : (
                                <ExpandMoreIcon />
                              )}
                            </IconButton>
                          </TableCell>
                          <TableCell>{formatDate(message.sentAt)}</TableCell>
                          <TableCell>
                            <Chip
                              label={getMessageTypeLabel(message.messageType)}
                              color={getMessageTypeColor(message.messageType)}
                              size="small"
                            />
                          </TableCell>
                          <TableCell>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              {message.icon && <span>{message.icon}</span>}
                              <Typography
                                variant="body2"
                                sx={{
                                  maxWidth: 300,
                                  overflow: 'hidden',
                                  textOverflow: 'ellipsis',
                                  whiteSpace: 'nowrap',
                                }}
                              >
                                {message.content}
                              </Typography>
                            </Box>
                          </TableCell>
                          <TableCell>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                              {message.failedCount === 0 ? (
                                <Tooltip title="Alle bezorgd">
                                  <CheckCircleIcon color="success" fontSize="small" />
                                </Tooltip>
                              ) : message.deliveredCount === 0 ? (
                                <Tooltip title="Geen bezorgd">
                                  <ErrorIcon color="error" fontSize="small" />
                                </Tooltip>
                              ) : (
                                <Tooltip title="Gedeeltelijk bezorgd">
                                  <WarningIcon color="warning" fontSize="small" />
                                </Tooltip>
                              )}
                              <Typography variant="body2">
                                {message.deliveredCount}/{message.totalRecipients}
                              </Typography>
                            </Box>
                          </TableCell>
                          <TableCell>
                            {message.failedCount > 0 && (
                              <Tooltip title="Mislukte verzendingen opnieuw proberen">
                                <IconButton
                                  size="small"
                                  onClick={() => handleRetry(message.id)}
                                  disabled={retrying === message.id}
                                >
                                  {retrying === message.id ? (
                                    <CircularProgress size={20} />
                                  ) : (
                                    <RefreshIcon />
                                  )}
                                </IconButton>
                              </Tooltip>
                            )}
                          </TableCell>
                        </TableRow>

                        {/* Expanded delivery details */}
                        <TableRow key={`${message.id}-details`}>
                          <TableCell colSpan={6} sx={{ py: 0, borderBottom: expandedMessageId === message.id ? undefined : 'none' }}>
                            <Collapse in={expandedMessageId === message.id}>
                              <Box sx={{ py: 2, px: 2, bgcolor: 'grey.50' }}>
                                <Typography variant="subtitle2" gutterBottom>
                                  Bezorging details
                                </Typography>
                                <Table size="small">
                                  <TableHead>
                                    <TableRow>
                                      <TableCell>Team</TableCell>
                                      <TableCell>Bootnummer</TableCell>
                                      <TableCell>Status</TableCell>
                                      <TableCell>Kanalen</TableCell>
                                      <TableCell>Fout</TableCell>
                                    </TableRow>
                                  </TableHead>
                                  <TableBody>
                                    {message.deliveries.map((delivery) => (
                                      <TableRow key={delivery.teamId}>
                                        <TableCell>{delivery.teamName}</TableCell>
                                        <TableCell>{delivery.boatNumber}</TableCell>
                                        <TableCell>
                                          {delivery.delivered ? (
                                            <Chip label="Bezorgd" color="success" size="small" />
                                          ) : (
                                            <Chip label="Mislukt" color="error" size="small" />
                                          )}
                                        </TableCell>
                                        <TableCell>
                                          {delivery.transmittedVia.map((channel) => (
                                            <Chip
                                              key={channel}
                                              label={channel}
                                              size="small"
                                              variant="outlined"
                                              sx={{ mr: 0.5 }}
                                            />
                                          ))}
                                        </TableCell>
                                        <TableCell>
                                          {delivery.transmissionError && (
                                            <Typography variant="caption" color="error">
                                              {delivery.transmissionError}
                                            </Typography>
                                          )}
                                        </TableCell>
                                      </TableRow>
                                    ))}
                                  </TableBody>
                                </Table>
                              </Box>
                            </Collapse>
                          </TableCell>
                        </TableRow>
                      </>
                    ))
                  )}
                </TableBody>
              </Table>
              <TablePagination
                component="div"
                count={totalCount}
                page={page}
                onPageChange={(_, newPage) => setPage(newPage)}
                rowsPerPage={pageSize}
                onRowsPerPageChange={(e) => {
                  setPageSize(parseInt(e.target.value, 10));
                  setPage(0);
                }}
                rowsPerPageOptions={[5, 10, 25, 50]}
                labelRowsPerPage="Rijen per pagina:"
                labelDisplayedRows={({ from, to, count }) =>
                  `${from}-${to} van ${count !== -1 ? count : `meer dan ${to}`}`
                }
              />
            </TableContainer>
          </TabPanel>

          {/* Templates Tab */}
          <TabPanel value={tabValue} index={1}>
            {templatesError && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {templatesError}
              </Alert>
            )}

            <Box sx={{ mb: 2 }}>
              <Button
                variant="outlined"
                startIcon={<AddIcon />}
                onClick={() => {
                  setEditingTemplate(null);
                  setTemplateDialogOpen(true);
                }}
              >
                Nieuwe template
              </Button>
            </Box>

            <TableContainer component={Paper}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Naam</TableCell>
                    <TableCell>Type</TableCell>
                    <TableCell>Bericht</TableCell>
                    <TableCell>Acties</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {templatesLoading ? (
                    <TableRow>
                      <TableCell colSpan={4} align="center" sx={{ py: 4 }}>
                        <CircularProgress />
                      </TableCell>
                    </TableRow>
                  ) : templates.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4} align="center" sx={{ py: 4 }}>
                        <Typography color="text.secondary">
                          Nog geen templates aangemaakt
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    templates.map((template) => (
                      <TableRow key={template.id} hover>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            {template.icon && <span>{template.icon}</span>}
                            <Typography fontWeight="medium">{template.name}</Typography>
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Chip
                            label={getMessageTypeLabel(template.messageType)}
                            color={getMessageTypeColor(template.messageType)}
                            size="small"
                          />
                        </TableCell>
                        <TableCell>
                          <Typography
                            variant="body2"
                            sx={{
                              maxWidth: 400,
                              overflow: 'hidden',
                              textOverflow: 'ellipsis',
                              whiteSpace: 'nowrap',
                            }}
                          >
                            {template.content}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Tooltip title="Bewerken">
                            <IconButton
                              size="small"
                              onClick={() => {
                                setEditingTemplate(template);
                                setTemplateDialogOpen(true);
                              }}
                            >
                              <EditIcon />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Verwijderen">
                            <IconButton
                              size="small"
                              onClick={() => {
                                setTemplateToDelete(template);
                                setDeleteConfirmOpen(true);
                              }}
                            >
                              <DeleteIcon />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Gebruiken">
                            <Button
                              size="small"
                              variant="outlined"
                              onClick={() => {
                                setSelectedTemplateForSend(template);
                                setSendDialogOpen(true);
                              }}
                              sx={{ ml: 1 }}
                            >
                              Gebruiken
                            </Button>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          </TabPanel>
        </>
      )}

      {/* Send Message Dialog */}
      {sendDialogOpen && (
        <SendMessageDialog
          key={selectedTemplateForSend?.id ?? 'new'}
          open={sendDialogOpen}
          onClose={() => {
            setSendDialogOpen(false);
            setSelectedTemplateForSend(null);
          }}
          onSuccess={() => {
            fetchMessages();
            fetchTemplates();
          }}
          gameId={selectedGameId || ''}
          teams={teams}
          templates={templates}
          initialTemplate={selectedTemplateForSend}
        />
      )}

      {/* Template Form Dialog */}
      {templateDialogOpen && (
        <TemplateFormDialog
          key={editingTemplate?.id ?? 'new'}
          open={templateDialogOpen}
          onClose={() => {
            setTemplateDialogOpen(false);
            setEditingTemplate(null);
          }}
          onSuccess={fetchTemplates}
          template={editingTemplate}
        />
      )}

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteConfirmOpen} onClose={() => setDeleteConfirmOpen(false)}>
        <DialogTitle>Template verwijderen?</DialogTitle>
        <DialogContent>
          <Typography>
            Weet je zeker dat je de template "{templateToDelete?.name}" wilt verwijderen?
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmOpen(false)}>Annuleren</Button>
          <Button onClick={handleDeleteTemplate} color="error" variant="contained">
            Verwijderen
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
