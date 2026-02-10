/**
 * Auth Service - JWT parsing and role extraction utilities
 */

import { AdminRole } from '../types/entities';

export type UserRole = 'team' | 'admin';

interface JwtPayload {
  sub: string;
  name?: string;
  role?: string;
  groups?: string[];
  exp: number;
  iat?: number;
  iss?: string;
}

/**
 * Parse JWT payload from a token string
 * Returns null if the token is invalid or malformed
 */
export function parseJwtPayload(token: string): JwtPayload | null {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }
    const base64Payload = parts[1];
    // Handle URL-safe base64
    const base64 = base64Payload.replace(/-/g, '+').replace(/_/g, '/');
    const payload = JSON.parse(atob(base64));
    return payload as JwtPayload;
  } catch {
    return null;
  }
}

/**
 * Determine UserRole from an array of role strings
 * Returns 'admin' if any admin role is present, 'team' if team role is present, null otherwise
 */
export function getUserRoleFromRoles(roles: string[]): UserRole | null {
  const adminRoles = ['admin', 'super_admin', 'viewer', 'game_master', 'message_sender', 'checkpoint_manager'];
  if (roles.some(role => adminRoles.includes(role))) {
    return 'admin';
  }
  if (roles.includes('team')) {
    return 'team';
  }
  return null;
}

/**
 * Extract user role from a JWT token
 * Returns null if the token is invalid or doesn't contain a role
 * @deprecated Use getRolesFromToken and getUserRoleFromRoles instead for multi-role support
 */
export function getRoleFromToken(token: string): UserRole | null {
  const roles = getRolesFromToken(token);
  return getUserRoleFromRoles(roles);
}

/**
 * Check if a JWT token is expired
 */
export function isTokenExpired(token: string): boolean {
  const payload = parseJwtPayload(token);
  if (!payload || !payload.exp) {
    return true;
  }
  // exp is in seconds, Date.now() is in milliseconds
  return Date.now() >= payload.exp * 1000;
}

/**
 * Check if a JWT token is expiring soon
 * @param token - JWT token to check
 * @param minutesThreshold - Number of minutes before expiry to consider "expiring soon" (default: 5)
 * @returns true if token expires within the threshold or is already expired
 */
export function isTokenExpiringSoon(token: string, minutesThreshold: number = 5): boolean {
  const payload = parseJwtPayload(token);
  if (!payload || !payload.exp) {
    return true;
  }
  // exp is in seconds, Date.now() is in milliseconds
  const expiryTime = payload.exp * 1000;
  const thresholdTime = Date.now() + (minutesThreshold * 60 * 1000);
  return expiryTime <= thresholdTime;
}

/**
 * Get the subject (user/team ID) from a JWT token
 */
export function getSubjectFromToken(token: string): string | null {
  const payload = parseJwtPayload(token);
  return payload?.sub ?? null;
}

/**
 * Get the name claim from a JWT token
 */
export function getNameFromToken(token: string): string | null {
  const payload = parseJwtPayload(token);
  return payload?.name ?? null;
}

/**
 * Get all roles from a JWT token
 * Returns an array of role names from the groups claim
 */
export function getRolesFromToken(token: string): string[] {
  const payload = parseJwtPayload(token);
  if (!payload) {
    return [];
  }
  return payload.groups || [];
}

/**
 * Check if the user has a specific role
 */
export function hasRole(token: string, role: AdminRole | string): boolean {
  const roles = getRolesFromToken(token);
  return roles.includes(role);
}

/**
 * Check if the user is a super admin
 */
export function isSuperAdmin(token: string): boolean {
  return hasRole(token, AdminRole.SUPER_ADMIN);
}

/**
 * Check if the user is an admin (has any admin role)
 */
export function isAdmin(token: string): boolean {
  const roles = getRolesFromToken(token);
  const adminRoleValues = ['admin', 'super_admin', 'viewer', 'game_master', 'message_sender', 'checkpoint_manager'];
  return roles.some(role => adminRoleValues.includes(role));
}
