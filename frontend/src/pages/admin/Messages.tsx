import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
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
import { useAdminResync } from '../../hooks/useAdminSync';
import { useAdminTopicHeartbeat, ADMIN_TOPICS } from '../../hooks/useAdminHeartbeat';
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
  const { t } = useTranslation();
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

  const getMessageTypeLabel = (type: string): string => {
    const key = `components.sendMessage.type${type.charAt(0).toUpperCase() + type.slice(1)}` as const;
    return t(key, { defaultValue: type });
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

  // Reload on resync (app resume / reconnect) to catch messages/templates whose
  // delivery events were missed while suspended.
  useAdminResync(() => {
    void fetchMessages();
    void fetchTemplates();
  });

  // Only receive delivery updates from the backend while this page is mounted.
  useAdminTopicHeartbeat(ADMIN_TOPICS.MESSAGES);

  // Subscribe to WebSocket for real-time delivery updates
  useEffect(() => {
    if (!selectedGameId) return;

    const unsubscribe = websocket.on(WebSocketEventType.MESSAGE_DELIVERY_UPDATE, (wsMessage) => {
      const payload = wsMessage.payload as {
        messageId: string;
        batchId: string;
        teamId: string;
        delivered: boolean;
        read: boolean;
      };

      setMessages(prevMessages =>
        prevMessages.map(msg => {
          if (msg.id !== payload.batchId) return msg;

          const updatedDeliveries = msg.deliveries.map(delivery =>
            delivery.teamId === payload.teamId
              ? { ...delivery, delivered: payload.delivered }
              : delivery
          );

          const deliveredCount = updatedDeliveries.filter(d => d.delivered).length;
          const failedCount = updatedDeliveries.length - deliveredCount;

          return {
            ...msg,
            deliveries: updatedDeliveries,
            deliveredCount,
            failedCount,
          };
        })
      );
    });

    return () => {
      unsubscribe();
    };
  }, [selectedGameId]);

  const handleRetry = async (messageId: string) => {
    setRetrying(messageId);
    await api.retryFailedDeliveries(messageId);
    setRetrying(null);
    fetchMessages();
  };

  const handleRetryAll = async () => {
    if (!selectedGameId) return;
    setRetryAllLoading(true);
    await api.retryAllFailedDeliveries(selectedGameId);
    setRetryAllLoading(false);
    fetchMessages();
  };

  const handleDeleteTemplate = async () => {
    if (!templateToDelete) return;
    await api.deleteMessageTemplate(templateToDelete.id);
    setDeleteConfirmOpen(false);
    setTemplateToDelete(null);
    fetchTemplates();
  };

  const totalFailedDeliveries = messages.reduce((sum, m) => sum + m.failedCount, 0);

  const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    return date.toLocaleString(undefined, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          {t('admin.messages.title')}
        </Typography>
        <Button
          variant="contained"
          startIcon={<SendIcon />}
          onClick={() => setSendDialogOpen(true)}
          disabled={!selectedGameId}
        >
          {t('admin.messages.sendMessage')}
        </Button>
      </Box>

      {!selectedGameId ? (
        <Alert severity="info">{t('admin.messages.selectGame')}</Alert>
      ) : (
        <>
          <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tabs value={tabValue} onChange={(_, newValue) => setTabValue(newValue)}>
              <Tab label={t('admin.messages.tabMessages')} />
              <Tab label={t('admin.messages.tabTemplates')} />
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
                  {t('admin.messages.retryAll', { count: totalFailedDeliveries })}
                </Button>
              </Box>
            )}

            <TableContainer component={Paper}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell width={40} />
                    <TableCell>{t('admin.messages.columnTime')}</TableCell>
                    <TableCell>{t('admin.messages.columnType')}</TableCell>
                    <TableCell>{t('admin.messages.columnMessage')}</TableCell>
                    <TableCell>{t('admin.messages.columnDelivery')}</TableCell>
                    <TableCell>{t('admin.messages.columnActions')}</TableCell>
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
                        <Typography color="textSecondary">
                          {t('admin.messages.noMessages')}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    messages.map((message) => (
                      <React.Fragment key={message.id}>
                        <TableRow hover>
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
                                <Tooltip title={t('admin.messages.statusAllDelivered')}>
                                  <CheckCircleIcon color="success" fontSize="small" />
                                </Tooltip>
                              ) : message.deliveredCount === 0 ? (
                                <Tooltip title={t('admin.messages.statusNoneDelivered')}>
                                  <ErrorIcon color="error" fontSize="small" />
                                </Tooltip>
                              ) : (
                                <Tooltip title={t('admin.messages.statusPartialDelivery')}>
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
                              <Tooltip title={t('admin.messages.retryFailed')}>
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
                        <TableRow>
                          <TableCell colSpan={6} sx={{ py: 0, borderBottom: expandedMessageId === message.id ? undefined : 'none' }}>
                            <Collapse in={expandedMessageId === message.id}>
                              <Box sx={{ py: 2, px: 2, bgcolor: 'grey.50' }}>
                                <Typography variant="subtitle2" gutterBottom>
                                  {t('admin.messages.deliveryDetails')}
                                </Typography>
                                <Table size="small">
                                  <TableHead>
                                    <TableRow>
                                      <TableCell>{t('admin.messages.team')}</TableCell>
                                      <TableCell>{t('common.status')}</TableCell>
                                      <TableCell>{t('admin.messages.channels')}</TableCell>
                                      <TableCell>{t('admin.messages.error')}</TableCell>
                                    </TableRow>
                                  </TableHead>
                                  <TableBody>
                                    {message.deliveries.map((delivery) => (
                                      <TableRow key={delivery.teamId}>
                                        <TableCell>{delivery.teamName}</TableCell>
                                        <TableCell>
                                          {delivery.delivered ? (
                                            <Chip label={t('admin.messages.statusDelivered')} color="success" size="small" />
                                          ) : (
                                            <Chip label={t('admin.messages.statusFailed')} color="error" size="small" />
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
                      </React.Fragment>
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
                labelRowsPerPage={t('admin.messages.rowsPerPage')}
                labelDisplayedRows={({ from, to, count }) =>
                  count !== -1
                    ? t('admin.messages.paginationRange', { from, to, count })
                    : t('admin.messages.paginationRange', { from, to, count: t('admin.messages.paginationRangeMore', { to }) })
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
                {t('admin.messages.newTemplate')}
              </Button>
            </Box>

            <TableContainer component={Paper}>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('admin.messages.columnTemplateName')}</TableCell>
                    <TableCell>{t('admin.messages.columnTemplateType')}</TableCell>
                    <TableCell>{t('admin.messages.columnTemplateMessage')}</TableCell>
                    <TableCell>{t('admin.messages.columnTemplateActions')}</TableCell>
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
                        <Typography color="textSecondary">
                          {t('admin.messages.noTemplates')}
                        </Typography>
                      </TableCell>
                    </TableRow>
                  ) : (
                    templates.map((template) => (
                      <TableRow key={template.id} hover>
                        <TableCell>
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            {template.icon && <span>{template.icon}</span>}
                            <Typography sx={{
                              fontWeight: "medium"
                            }}>{template.name}</Typography>
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
                          <Tooltip title={t('admin.messages.editTemplate')}>
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
                          <Tooltip title={t('admin.messages.deleteTemplate')}>
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
                          <Tooltip title={t('admin.messages.useTemplate')}>
                            <Button
                              size="small"
                              variant="outlined"
                              onClick={() => {
                                setSelectedTemplateForSend(template);
                                setSendDialogOpen(true);
                              }}
                              sx={{ ml: 1 }}
                            >
                              {t('admin.messages.useTemplate')}
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
        <DialogTitle>{t('admin.messages.deleteTemplateTitle')}</DialogTitle>
        <DialogContent>
          <Typography>
            {t('admin.messages.deleteTemplateConfirm', { name: templateToDelete?.name })}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteConfirmOpen(false)}>{t('common.cancel')}</Button>
          <Button onClick={handleDeleteTemplate} color="error" variant="contained">
            {t('common.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
