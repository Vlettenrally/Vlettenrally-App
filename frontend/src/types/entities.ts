export interface Game {
  id: string;
  name: string;
  startTime?: Date;
  endTime?: Date;
  startingAreaActiveTime?: Date;
  startSignalTime?: Date;
  description?: string;
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
  startTime?: string;
  endTime?: string;
  startingAreaActiveTime?: string;
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
 * Base team fields shared across all team types
 */
interface TeamBase {
  id: string;
  gameId: string;
  name: string;
  boatNumber: string;
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
  rivals: string[];
  createdAt: string;
}

/**
 * Minimal team data for public views (rivals, rankings).
 * Contains only non-sensitive public information.
 */
export type PublicTeam = TeamBase;

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
  boatNumber: string;
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
