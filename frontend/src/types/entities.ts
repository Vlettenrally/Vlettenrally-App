export interface Game {
  id: string;
  name: string;
  startTime?: Date;
  endTime?: Date;
  checkInStartTime?: Date;
  description?: string;
  participantInfo?: string;
  status?: 'planned' | 'active' | 'paused' | 'completed';
  // Whether the final ranking has been revealed to players (admin-triggered at the ceremony).
  rankingsPublished?: boolean;
  // Whether the ranking is visible on the public scoreboard API (independent of player-app visibility).
  publicRankingsEnabled?: boolean;
  // External event integration
  externalEventId?: number;
  externalEventName?: string;
  // Location tracking configuration
  locationEndpointUrl?: string;
  locationPollingFrequency?: number;
  locationEndpointActive?: boolean;
  // Minutes before the start time to begin polling/ingesting locations (0 = off).
  locationPollingLeadMinutes?: number;
  // Minutes after the end time to keep polling/ingesting once COMPLETED (0 = off).
  locationPollingTailMinutes?: number;
  // Flat points deducted from a team that finishes after endTime (0 = no penalty).
  lateFinishPenaltyPoints?: number;
  // Per-game location-ingest guard overrides (undefined = inherit the global default).
  locMaxSpeedKmh?: number;
  locBoatMaxSpeedKmh?: number;
  locTurnAngleThresholdDegrees?: number;
  locMinBearingSegmentMeters?: number;
  locMaxAccuracyMeters?: number;
  locStaleFixSeconds?: number;
  // Why the rally was stopped. Only set while status is 'paused'.
  pauseReason?: string;
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
  status?: string;
  externalEventId?: number;
  externalEventName?: string;
  locationEndpointUrl?: string;
  locationPollingFrequency?: number;
  locationEndpointActive?: boolean;
  locationPollingLeadMinutes?: number;
  locationPollingTailMinutes?: number;
  // Flat points deducted from a team that finishes after endTime (0 = no penalty).
  lateFinishPenaltyPoints?: number;
  // Per-game location-ingest guard overrides (undefined = inherit the global default).
  locMaxSpeedKmh?: number;
  locBoatMaxSpeedKmh?: number;
  locTurnAngleThresholdDegrees?: number;
  locMinBearingSegmentMeters?: number;
  locMaxAccuracyMeters?: number;
  locStaleFixSeconds?: number;
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
  skipped: number;
  errors: number;
  createdTeams: string[];
  updatedTeams: string[];
  skippedDuplicates: string[];
  errorDetails: string[];
}

/**
 * One team — game pair holding a tracker. A tracker may be assigned in
 * multiple non-completed games at once (per-game uniqueness).
 */
export interface TrackerAssignment {
  teamName: string | null;
  gameName: string | null;
}

/**
 * A physical GPS tracker in the global pool, with the health status last
 * reported by the location feed and every team/game currently holding it.
 */
export interface Tracker {
  trackerId: string;
  enabled: boolean;
  batteryPercentage: number | null;
  lastSeenAt: string | null;
  latitude: number | null;
  longitude: number | null;
  assignments: TrackerAssignment[];
}

/**
 * A team field the tracker CSV export can emit. Must stay in sync with the
 * keys of TrackerAssignmentService.ExportField on the backend.
 */
export type TrackerExportField = 'trackerId' | 'name' | 'description';

/** One CSV column: a team field under a (renameable) column name. */
export interface TrackerExportColumn {
  field: TrackerExportField;
  header: string;
}

/** How to shape one tracker CSV export; omitted entirely means the server defaults. */
export interface TrackerExportOptions {
  columns: TrackerExportColumn[];
  includeUnassigned: boolean;
}

/**
 * Result of bulk-adding tracker IDs to the global pool.
 */
export interface AddTrackersResult {
  added: number;
  duplicates: number;
}

/**
 * Result of bulk-deleting tracker IDs from the global pool. `unassignedTeams`
 * names the teams whose tracker was deleted out from under them.
 */
export interface DeleteTrackersResult {
  deleted: number;
  notFound: number;
  unassignedTeams: string[];
}

/**
 * Result of auto-assigning free trackers from the global pool to a game's teams.
 */
export interface TrackerAssignResult {
  assigned: number;
  teamsWithoutTracker: number;
  unused: number;
  assignedPairs: string[];
  teamsWithoutTrackerNames: string[];
  unusedTrackers: string[];
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
 * Result of backfilling a team's historical track from the external tracker.
 */
export interface BackfillResult {
  inserted: number;
  droppedByGuard: number;
  checkpointPassesDetected: number;
}

/**
 * Team location data from location tracking
 */
export interface TeamLocation {
  teamId: string;
  teamName: string;
  teamDescription?: string;
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
  /** Hex color for map display, e.g. "#ff9800"; absent means default orange */
  color?: string;
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
  /** Hex color for map display, e.g. "#ff9800"; absent means default orange */
  color?: string;
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
 * How a team was checked in: by the rally desk on the team's behalf, or by the team
 * itself on their own phones. See utils/checkInStatus.ts for the derived UI states.
 */
export type CheckInMethod = 'MANUAL' | 'DIGITAL';

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
  /** How the team was checked in. Null/absent while not checked in. */
  checkInMethod?: CheckInMethod | null;
  finished: boolean;
  /** ISO instant the team was marked finished; null/absent while not finished. */
  finishedAt?: string | null;
  traccarEnabled: boolean;
  traccarIdentifier?: string | null;
  trackerId?: string | null;
  trackerHandedIn: boolean;
  /** ISO instant the tracker was marked handed in; null/absent while not handed in. */
  trackerHandedInAt?: string | null;
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
  finished: boolean;
  /** ISO instant the team was marked finished; null/absent while not finished. */
  finishedAt?: string | null;
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
  finished?: boolean;
  /** ISO instant the team was marked finished; null/absent while not finished. */
  finishedAt?: string | null;
}

/**
 * @deprecated Use AdminTeam, PlayerTeam, or PublicTeam instead.
 * Kept for backward compatibility during migration.
 */
export type Team = AdminTeam;

/**
 * Message types
 */
export type MessageType = 'general' | 'countdown' | 'checkpoint' | 'rallydesk' | 'achievement' | 'rival' | 'speed_warning';

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
  /** Force SMS to each team's first emergency number, whatever its devices are doing. Rallydesk only. */
  sendViaSms?: boolean;
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
 * Incoming notification for the admin inbox.
 */
export interface AdminInboxNotification {
  id: string;
  gameId: string;
  teamId?: string;
  teamName?: string;
  source: string;
  content: string;
  metadataJson?: string;
  createdAt: string;
  read: boolean;
  readAt?: string;
  readByAdminId?: string;
}

/**
 * Paginated response for admin inbox notifications.
 */
export interface AdminInboxNotificationListResponse {
  notifications: AdminInboxNotification[];
  page: number;
  pageSize: number;
  totalCount: number;
  totalPages: number;
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
  RALLY_DESK: 'rally_desk',
  RANKING: 'ranking',
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
 * Content attributed to an admin user. An admin can only be deleted permanently when every
 * count here is zero — otherwise deleting would blank out who moderated, sent or authored
 * each record.
 */
export interface AdminReferences {
  moderatedPictures: number;
  sentMessages: number;
  verifiedAchievements: number;
  pointEntries: number;
  automationRules: number;
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

export type PictureStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/**
 * A team-uploaded picture. `viewUrl` is a short-lived pre-signed URL, present only
 * once the picture has been uploaded.
 */
export interface Picture {
  id: string;
  teamId: string;
  gameId: string;
  status: PictureStatus;
  contentType: string | null;
  sizeBytes: number | null;
  viewUrl: string | null;
  createdAt: string | null;
  uploadedAt: string | null;
  moderatedAt: string | null;
  notes: string | null;
  /** Uploading team's name/description (the scouting group); null for admin uploads. */
  teamName: string | null;
  teamDescription: string | null;
}

/**
 * A minimal, public-safe projection of an approved picture, used by the players'
 * photowall. Attributes the photo to the uploading team (name/description) but omits
 * moderation metadata (status, notes) so a shared wall never leaks one team's
 * moderation details to another. Team fields are null for admin-uploaded photos.
 */
export interface PublicPicture {
  id: string;
  gameId: string;
  contentType: string | null;
  viewUrl: string | null;
  uploadedAt: string | null;
  teamName: string | null;
  teamDescription: string | null;
}

/**
 * A zone as drawn on the public narrowcasting map.
 *
 * Mirrors {@link Checkpoint} minus everything a public display has no business showing:
 * `externalId`, `points`, and — deliberately — `hidden`. Hidden zones are filtered out server-side
 * and the flag is not on the wire at all, so there is nothing here to accidentally render.
 */
export interface PublicMapZone {
  id: string;
  name: string;
  latitude?: number;
  longitude?: number;
  zoneType: 'circle' | 'polygon';
  radiusMeters: number;
  /** Polygon vertices as [[lat, lng], ...] — only for polygon zones */
  polygonPoints?: number[][];
  openingTime?: string;
  closingTime?: string;
  /** Hex color for map display, e.g. "#ff9800"; absent means default orange */
  color?: string;
}

/**
 * One poll of the public narrowcasting map: every team's latest position plus the visible zones.
 * Team positions reuse {@link TeamLocation}, the same shape the admin map and the LOCATION_UPDATE
 * websocket event carry.
 */
export interface PublicMapResponse {
  gameId: string;
  gameName: string;
  teams: TeamLocation[];
  zones: PublicMapZone[];
}

/**
 * Position in a keyset-paged picture list. Opaque to the client: echo back whatever the
 * previous page returned to get the next one.
 */
export interface PictureCursor {
  at: string;
  id: string;
}

/**
 * One page of a picture list. Unlike the other paginated endpoints (which return
 * `page`/`pageSize`/`totalCount` for a jump-to-page table), picture lists are cursor-paged
 * for infinite scroll: photos arrive live and moderating one removes it from the pending
 * set, so an offset would re-serve or silently skip photos.
 */
export interface PictureListResponse {
  pictures: Picture[];
  nextCursorAt: string | null;
  nextCursorId: string | null;
  hasMore: boolean;
}

/** Photowall equivalent of {@link PictureListResponse}, carrying the public projection. */
export interface PublicPictureListResponse {
  pictures: PublicPicture[];
  nextCursorAt: string | null;
  nextCursorId: string | null;
  hasMore: boolean;
}

/**
 * Response when reserving an upload slot: the pre-signed PUT URL plus the limits the
 * client should enforce before uploading.
 */
export interface PictureUploadResponse {
  pictureId: string;
  objectKey: string;
  uploadUrl: string;
  contentType: string;
  expiresAt: string;
  maxBytes: number;
  allowedContentTypes: string[];
}

/**
 * One-time token authorising a download of a game's approved-picture archive.
 * Short-lived and spent on first use.
 */
export interface PictureDownloadToken {
  token: string;
  expiresIn: number;
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
  description?: string;
  checkedIn?: boolean;
  hasMainDevice?: boolean;
  // False once the team's digital check-in window (measured from its physical
  // check-in) has closed — the team is then only joinable via its invite QR or an
  // admin, not the regular selection screen.
  acceptingNewDevices?: boolean;
}

/**
 * A team-scoped invite link (POST /check-in/invite-token or
 * POST /teams/{teamId}/invite-token). Rendered as a QR teammates scan to join.
 */
export interface TeamInviteLink {
  /** Shareable form, e.g. https://app/i/K7QM4X9BTD — use this for copy-to-clipboard. */
  url: string;
  /** Uppercase form — encode THIS in the QR; it lets the QR use alphanumeric mode and stay small. */
  qrUrl: string;
  /** The bare invite code. */
  token: string;
  /** Remaining lifetime of the code; re-request before this elapses to keep a displayed QR alive. */
  expiresInSeconds: number;
}

/**
 * Resolved contents of a scanned invite token (GET /check-in/invite/{token}).
 */
export interface TeamInviteResolve {
  gameId: string;
  teamId: string;
  teamName: string;
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
  // Traccar setup data — populated only for the main device of a Traccar-enabled team.
  traccarEnabled: boolean;
  traccarIdentifier: string | null;
  traccarServerUrl: string | null;
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
 * Points ledger entry — one row per change to a team's totalPoints.
 * originalAmount is immutable; currentAmount may be edited (admin "Aftrek" / correction).
 */
export type PointEntryType =
  | 'CHECKPOINT_VERIFICATION'
  | 'MANUAL_ADJUSTMENT'
  | 'AUTOMATION_BONUS'
  | 'DISTANCE_BONUS'
  | 'LATE_FINISH_PENALTY';

export interface PointEntry {
  id: string;
  teamId: string;
  gameId: string;
  entryType: PointEntryType;
  originalAmount: number;
  currentAmount: number;
  /** Credited distance in meters for DISTANCE_BONUS entries; null otherwise. */
  distanceMeters?: number | null;
  sourceAchievementId?: string | null;
  lat?: number | null;
  lng?: number | null;
  description?: string | null;
  createdAt: string;
  updatedAt: string;
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
  | 'DISTANCE_TRAVELED'
  | 'SPEED_ALERT'
  | 'TEAM_CHECK_IN'
  | 'PICTURE_APPROVED';

/**
 * Flat config; which fields apply depends on the rule's trigger type.
 * For CHECKPOINT_ENTRY, `comparison` plus the mode/time fields restrict firing
 * to passes before/after a boundary time (finish bonus/penalty).
 */
export interface AutomationTriggerConfig {
  checkpointIds?: string[] | null;
  /** @deprecated legacy single-checkpoint field, superseded by checkpointIds; still read from older stored rules. */
  checkpointId?: string | null;
  excludedCheckpointIds?: string[] | null;
  mode?: 'ABSOLUTE' | 'RELATIVE' | null;
  absoluteTime?: string | null;
  relativeTo?: 'GAME_START' | 'GAME_END' | null;
  offsetMinutes?: number | null;
  toStatus?: 'ACTIVE' | 'PAUSED' | 'COMPLETED' | null;
  inactiveMinutes?: number | null;
  pointsPer100m?: number | null;
  comparison?: 'BEFORE' | 'AFTER' | null;
  speedThresholdKmh?: number | null;
  /** PICTURE_APPROVED: only fire once the team has at least this many approved pictures. */
  approvedPictureCount?: number | null;
}

export interface SendMessageAction {
  type: 'SEND_MESSAGE';
  messageContent?: string | null;
  messageType?: string | null;
  messageIcon?: string | null;
  messageTemplateId?: string | null;
}

export interface AwardPointsAction {
  type: 'AWARD_POINTS';
  points?: number | null;
  pointsDescription?: string | null;
}

export interface NotifyAdminsAction {
  type: 'NOTIFY_ADMINS';
  adminNotification?: string | null;
}

export interface FinishTeamAction {
  type: 'FINISH_TEAM';
}

export interface SetRankingsPublishedAction {
  type: 'SET_RANKINGS_PUBLISHED';
  rankingsPublished?: boolean | null;
}

export interface SetPublicRankingsEnabledAction {
  type: 'SET_PUBLIC_RANKINGS_ENABLED';
  publicRankingsEnabled?: boolean | null;
}

export type AutomationAction =
  | SendMessageAction
  | AwardPointsAction
  | NotifyAdminsAction
  | FinishTeamAction
  | SetRankingsPublishedAction
  | SetPublicRankingsEnabledAction;

export type AutomationActionType = AutomationAction['type'];

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
  triggerForFinishedTeams: boolean;
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
  triggerForFinishedTeams?: boolean;
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
