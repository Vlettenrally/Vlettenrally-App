export interface Game {
  id: string;
  name: string;
  startTime?: Date;
  endTime?: Date;
  startSignalTime?: Date;
  endSignalTime?: Date;
  description?: string;
  location?: string;
  createdAt: string;
  updatedAt: string;
  status?: 'planned' | 'active' | 'completed';
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
export interface PublicTeam extends TeamBase {
  // Only base fields: id, gameId, name, boatNumber, totalPoints
}

/**
 * @deprecated Use AdminTeam, PlayerTeam, or PublicTeam instead.
 * Kept for backward compatibility during migration.
 */
export type Team = AdminTeam;
