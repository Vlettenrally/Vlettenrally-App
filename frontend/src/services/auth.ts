/**
 * Auth Service - JWT parsing and role extraction utilities
 */

import { AdminRole } from '../types/entities';

export type UserRole = 'team' | 'admin';

/**
 * All admin-type role strings. Holding any of these makes a user "admin"/staff.
 * Mirrors the backend SecurityRoles.ADMIN_ROLES set.
 */
export const ADMIN_ROLE_VALUES: string[] = [
  AdminRole.ADMIN,
  AdminRole.SUPER_ADMIN,
  AdminRole.VIEWER,
  AdminRole.GAME_MASTER,
  AdminRole.MESSAGE_SENDER,
  AdminRole.CHECKPOINT_MANAGER,
];

/**
 * Maps admin route paths to the roles allowed to access them. `admin` implicitly grants
 * access to every page (handled in canAccessAdminPath), so it is not listed here.
 * Paths that are not present are considered accessible to any staff user.
 * Mirrors the backend @RolesAllowed gating on the corresponding resources.
 */
export const ADMIN_PAGE_ROLES: Record<string, string[]> = {
  '/admin/map': [AdminRole.GAME_MASTER, AdminRole.VIEWER],
  '/admin/games': [AdminRole.GAME_MASTER],
  '/admin/teams': [AdminRole.GAME_MASTER],
  '/admin/zones': [AdminRole.GAME_MASTER],
  '/admin/automations': [AdminRole.GAME_MASTER],
  '/admin/settings': [AdminRole.GAME_MASTER],
  '/admin/pictures': [AdminRole.GAME_MASTER],
  '/admin/messages': [AdminRole.MESSAGE_SENDER],
  '/admin/points-management': [AdminRole.CHECKPOINT_MANAGER],
  '/admin/checkpoint-station': [AdminRole.CHECKPOINT_MANAGER],
  '/admin/admin-users': [AdminRole.SUPER_ADMIN],
};

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
  if (roles.some(role => ADMIN_ROLE_VALUES.includes(role))) {
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
  return roles.some(role => ADMIN_ROLE_VALUES.includes(role));
}

/**
 * Get the admin-type roles held by the user (intersection with ADMIN_ROLE_VALUES)
 */
export function getAdminRoles(token: string): string[] {
  const roles = getRolesFromToken(token);
  return roles.filter(role => ADMIN_ROLE_VALUES.includes(role));
}

/**
 * Check if the user holds at least one of the given roles
 */
export function hasAnyRole(token: string, roles: string[]): boolean {
  const userRoles = getRolesFromToken(token);
  return roles.some(role => userRoles.includes(role));
}

/**
 * Check if the user may access a given admin route path.
 * A full `admin` always has access. Otherwise the user must hold one of the roles
 * listed for the path in ADMIN_PAGE_ROLES. Unmapped paths are open to any staff user.
 */
export function canAccessAdminPath(token: string, path: string): boolean {
  const allowed = ADMIN_PAGE_ROLES[path];
  if (!allowed) {
    return true;
  }
  if (hasAnyRole(token, allowed)) {
    return true;
  }
  // A full `admin` implicitly holds every capability role EXCEPT super_admin:
  // admin-user management is super_admin-only, matching the backend gating.
  if (hasRole(token, AdminRole.ADMIN)) {
    return allowed.some((role) => role !== AdminRole.SUPER_ADMIN);
  }
  return false;
}
