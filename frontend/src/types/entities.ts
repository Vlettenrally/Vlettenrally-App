export interface Game {
  id: string;
  name: string;
  startTime?: Date;
  endTime?: Date;
  checkInStartTime?: Date;
  startSignalTime?: Date;
  description?: string;
  participantInfo?: string;
  status?: 'planned' | 'active' | 'completed';
  // External event integration
  externalEventId?: number;
  externalEventName?: string;
  // Location tracking configuration
  locationEndpointUrl?: string;
  locationPollingFrequency?: number;
  locationEndpointActive?: boolean;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Request to create or update a game
 */
export interface GameRequest {
  name: string;
  description?: string;
  participantInfo?: string;
  startTime?: string;
  endTime?: string;
  checkInStartTime?: string;
  startSignalTime?: string;
  status?: string;
  externalEventId?: number;
  externalEventName?: string;
  locationEndpointUrl?: string;
  locationPollingFrequency?: number;
  locationEndpointActive?: boolean;
}

/**
 * External event from pre-registration API
 */
export interface ExternalEvent {
  eventId: number;
  eventName: string;
  bookingCount: number;
}

/**
 * Result of team import operation
 */
export interface TeamImportResult {
  created: number;
  updated: number;
  errors: number;
}

/**
 * Result of location endpoint test
 */
export interface LocationTestResult {
  success: boolean;
  participantCount: number;
  errorMessage?: string;
}

/**
 * Team location data from location tracking
 */
export interface TeamLocation {
  teamId: string;
  teamName: string;
  latitude: number;
  longitude: number;
  speed?: number;
  heading?: number;
  locationTime: string;
}

/**
 * Checkpoint data for map display
 */
export interface Checkpoint {
  id: string;
  gameId: string;
  name: string;
  openingTime?: string;
  closingTime?: string;
  points: number;
  latitude?: number;
  longitude?: number;
  externalId?: string;
  zoneType: 'circle' | 'polygon';
  radiusMeters: number;
  /** Polygon vertices as [[lat, lng], ...] — only for polygon zones */
  polygonPoints?: number[][];
  /** Hidden from teams' map, but still triggers automations */
  hidden: boolean;
}

/**
 * Request to create or update a checkpoint (zone)
 */
export interface CheckpointRequest {
  name: string;
  openingTime?: string;
  closingTime?: string;
  points: number;
  latitude?: number;
  longitude?: number;
  zoneType?: 'circle' | 'polygon';
  radiusMeters?: number;
  /** Polygon vertices as [[lat, lng], ...] — only for polygon zones */
  polygonPoints?: number[][];
  /** Hidden from teams' map, but still triggers automations */
  hidden?: boolean;
}

/**
 * Result of an import-checkpoints run against the Legendstracking /details endpoint.
 */
export interface CheckpointImportResult {
  created: number;
  updated: number;
  errors: number;
}

/**
 * Result of a link-participants run against the Legendstracking /details endpoint.
 */
export interface ParticipantLinkResult {
  linked: number;
  unmatched: number;
  linkedTeams: string[];
  unmatchedParticipants: string[];
}

/**
 * Base team fields shared across all team types
 */
interface TeamBase {
  id: string;
  gameId: string;
  name: string;
  totalPoints: number;
}

/**
 * Full team data for admin views.
 * Contains all fields including sensitive admin-only data.
 */
export interface AdminTeam extends TeamBase {
  description: string;
  participants: string[];
  deviceId?: string;
  externalId?: string;
  checkedIn: boolean;
  checkedInDigitally: boolean;
  rivals: string[];
  notes?: string;
  emergencyPhoneNumbers?: string[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Team data for player's own team view.
 * Excludes sensitive fields like notes, deviceId, participants, emergencyPhoneNumbers.
 */
export interface PlayerTeam extends TeamBase {
  description: string;
  checkedIn: boolean;
  checkedInDigitally: boolean;
  rivals: string[];
  createdAt: string;
}

/**
 * Minimal team data for public views (rivals, rankings).
 * Contains only non-sensitive public information.
 */
export type PublicTeam = TeamBase;

/**
 * One row in the live game ranking. Mirrors backend RankingResponse.
 */
export interface RankingEntry {
  position: number;
  teamId: string;
  teamName: string;
  points: number;
}

/**
 * @deprecated Use AdminTeam, PlayerTeam, or PublicTeam instead.
 * Kept for backward compatibility during migration.
 */
export type Team = AdminTeam;

/**
 * Message types
 */
export type MessageType = 'general' | 'countdown' | 'checkpoint' | 'rallydesk' | 'achievement' | 'rival';

export interface Message {
  id: string;
  teamId: string;
  messageType: MessageType;
  content: string;
  icon?: string;
  sentAt: string;
  delivered: boolean;
  read: boolean;
}

/**
 * Delivery detail for a message to a specific team
 */
export interface DeliveryDetail {
  teamId: string;
  teamName: string;
  delivered: boolean;
  transmittedVia: string[];
  transmissionError?: string;
}

/**
 * Admin message with delivery tracking
 */
export interface AdminMessage {
  id: string;
  gameId: string;
  messageType: MessageType;
  content: string;
  icon?: string;
  sentAt: string;
  source: string;
  sentByAdminId?: string;
  totalRecipients: number;
  deliveredCount: number;
  failedCount: number;
  deliveries: DeliveryDetail[];
}

/**
 * Paginated response for admin messages
 */
export interface AdminMessageListResponse {
  messages: AdminMessage[];
  page: number;
  pageSize: number;
  totalCount: number;
  totalPages: number;
}

/**
 * Message template
 */
export interface MessageTemplate {
  id: string;
  name: string;
  messageType: MessageType;
  content: string;
  icon?: string;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to send an admin message
 */
export interface SendAdminMessageRequest {
  gameId: string;
  teamIds?: string[];
  messageType: string;
  content: string;
  icon?: string;
  saveAsTemplate?: boolean;
  templateName?: string;
}

/**
 * Request to create/update a message template
 */
export interface MessageTemplateRequest {
  name: string;
  messageType: string;
  content: string;
  icon?: string;
}

/**
 * Response from retry operation
 */
export interface RetryMessageResponse {
  retriedCount: number;
  successCount: number;
  failedCount: number;
}

/**
 * Admin role enum
 */
export const AdminRole = {
  ADMIN: 'admin',
  SUPER_ADMIN: 'super_admin',
  VIEWER: 'viewer',
  GAME_MASTER: 'game_master',
  MESSAGE_SENDER: 'message_sender',
  CHECKPOINT_MANAGER: 'checkpoint_manager',
} as const;

export type AdminRole = (typeof AdminRole)[keyof typeof AdminRole];

/**
 * Admin user
 */
export interface AdminUser {
  id: string;
  username: string;
  roles: AdminRole[];
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request to create a new admin user
 */
export interface CreateAdminRequest {
  username: string;
  password: string;
  roles: AdminRole[];
}

/**
 * Request to update admin roles
 */
export interface UpdateAdminRolesRequest {
  roles: AdminRole[];
}

/**
 * Response from password reset
 */
export interface ResetPasswordResponse {
  temporaryPassword: string;
}

/**
 * Achievement with full status (admin / checkpoint-station view)
 */
export interface Achievement {
  id: string;
  teamId: string;
  checkpointId: string;
  pointsEarned: number | null;
  bonusPoints: number;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED';
  detectedAt: string;
  verifiedAt?: string;
  notes?: string;
}

/**
 * Team entry shown in the checkpoint station (GPS + achievement status merged)
 */
export interface CheckpointTeamStatus {
  teamId: string;
  teamName: string;
  detectedAt: string | null;
  achievementStatus: 'PENDING' | 'VERIFIED' | 'REJECTED' | null;
  achievementId: string | null;
  defaultPoints: number;
  pointsEarned: number | null;
  verifiedAt: string | null;
  notes: string | null;
}

/**
 * Team option for check-in team selection
 */
export interface TeamOption {
  id: string;
  name: string;
  checkedIn?: boolean;
  hasMainDevice?: boolean;
}

/**
 * Game taking place today (Europe/Amsterdam) — returned by the public check-in flow.
 */
export interface TodayGame {
  id: string;
  name: string;
  description?: string | null;
  startTime: string;
}

/**
 * Single registered check-in device for a team.
 */
export interface CheckInDevice {
  deviceId: string;
  nickname: string | null;
  isMain: boolean;
  createdAt: string;
  lastSeen: string;
}

/**
 * Current multi-device coordination state for a team.
 */
export interface CheckInState {
  mainDeviceId: string | null;
  mainDeviceNickname: string | null;
  detailsSubmitted: boolean;
  devices: CheckInDevice[];
}

/**
 * Response from POST /api/v1/check-in/register-device. The server-assigned
 * {@code deviceId} is the canonical value to persist; the client's request
 * field is only a HINT for idempotent resumption.
 */
export interface RegisterDeviceResponse {
  deviceId: string;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType?: string;
  isMainDevice: boolean;
  detailsSubmitted: boolean;
  mainDeviceNickname: string | null;
}

/**
 * Response from POST /api/v1/fcm-tokens — includes the server-assigned
 * {@code deviceId} so the caller can persist it for future calls.
 */
export interface FcmTokenRegistrationResponse {
  success: boolean;
  message: string;
  deviceId: string | null;
}

/**
 * Response from token exchange during check-in
 * Either returns auth tokens or team selection options
 */
export type TokenExchangeResponse =
  | {
      accessToken: string;
      refreshToken: string;
      expiresIn: number;
      tokenType?: string;
    }
  | {
      gameId: string;
      teamOptions: TeamOption[];
    };

/**
 * Points ledger entry — one row per change to a team's totalPoints.
 * originalAmount is immutable; currentAmount may be edited (admin "Aftrek" / correction).
 */
export type PointEntryType =
  | 'CHECKPOINT_VERIFICATION'
  | 'MANUAL_ADJUSTMENT'
  | 'AUTOMATION_BONUS'
  | 'DISTANCE_BONUS';

export interface PointEntry {
  id: string;
  teamId: string;
  gameId: string;
  entryType: PointEntryType;
  originalAmount: number;
  currentAmount: number;
  sourceAchievementId?: string | null;
  lat?: number | null;
  lng?: number | null;
  description?: string | null;
  createdAt: string;
  createdBy?: string | null;
  editedAt?: string | null;
  editedBy?: string | null;
  editReason?: string | null;
}

export interface PointEntryListResponse {
  entries: PointEntry[];
  page: number;
  pageSize: number;
  totalCount: number;
  totalPages: number;
}

export interface PointEntryListParams {
  teamId?: string;
  entryType?: PointEntryType;
  page?: number;
  pageSize?: number;
}

export interface CreatePointAdjustmentRequest {
  teamId: string;
  gameId: string;
  amount: number;
  description?: string;
}

export interface EditPointEntryRequest {
  amount: number;
  reason: string;
}

/**
 * Automation rules — admin-configured trigger → actions per game.
 * System rules (the built-in "Afstandspunten" distance rule) cannot be
 * deleted and their trigger type is fixed; rate and enabled are editable.
 */
export type AutomationTriggerType =
  | 'CHECKPOINT_ENTRY'
  | 'SCHEDULED_TIME'
  | 'GAME_STATUS_CHANGE'
  | 'TEAM_INACTIVITY'
  | 'RIVAL_OVERTAKE'
  | 'DISTANCE_TRAVELED';

export type AutomationActionType = 'SEND_MESSAGE' | 'AWARD_POINTS' | 'NOTIFY_ADMINS';

/**
 * Flat config; which fields apply depends on the rule's trigger type.
 * For CHECKPOINT_ENTRY, `comparison` plus the mode/time fields restrict firing
 * to passes before/after a boundary time (finish bonus/penalty).
 */
export interface AutomationTriggerConfig {
  checkpointId?: string | null;
  mode?: 'ABSOLUTE' | 'RELATIVE' | null;
  absoluteTime?: string | null;
  relativeTo?: 'GAME_START' | 'GAME_END' | null;
  offsetMinutes?: number | null;
  toStatus?: 'ACTIVE' | 'COMPLETED' | null;
  inactiveMinutes?: number | null;
  pointsPer100m?: number | null;
  comparison?: 'BEFORE' | 'AFTER' | null;
}

/**
 * Flat action; which fields apply depends on `type`. Text supports {team},
 * {checkpoint} and {rival} placeholders (which apply depends on the trigger).
 * A SEND_MESSAGE action may reference a reusable template via messageTemplateId.
 */
export interface AutomationAction {
  type: AutomationActionType;
  messageContent?: string | null;
  messageType?: string | null;
  messageIcon?: string | null;
  messageTemplateId?: string | null;
  points?: number | null;
  pointsDescription?: string | null;
  adminNotification?: string | null;
}

export interface AutomationRule {
  id: string;
  gameId: string;
  name: string;
  enabled: boolean;
  system: boolean;
  triggerType: AutomationTriggerType;
  triggerConfig: AutomationTriggerConfig;
  actions: AutomationAction[];
  targetTeamIds: string[] | null;
  fireOnce: boolean;
  cooldownMinutes?: number | null;
  createdAt: string;
  updatedAt: string;
  lastFiredAt?: string | null;
  executionCount: number;
}

export interface AutomationRuleRequest {
  name: string;
  enabled: boolean;
  triggerType: AutomationTriggerType;
  triggerConfig: AutomationTriggerConfig;
  actions: AutomationAction[];
  targetTeamIds?: string[] | null;
  fireOnce: boolean;
  cooldownMinutes?: number | null;
}

export interface AutomationExecution {
  id: string;
  ruleId: string;
  teamId?: string | null;
  teamName?: string | null;
  contextKey?: string | null;
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';
  detail?: string | null;
  firedAt: string;
}

export interface AutomationExecutionListResponse {
  executions: AutomationExecution[];
  page: number;
  pageSize: number;
  totalCount: number;
  totalPages: number;
}

export interface CopyAutomationsResult {
  copied: number;
  skippedDuplicate: string[];
  skippedUnmatchedCheckpoint: string[];
  distanceRateCopied: boolean;
}

/**
 * Result of copying zones (checkpoints) from another game.
 */
export interface CopyCheckpointsResult {
  copied: number;
  skippedDuplicate: string[];
}
