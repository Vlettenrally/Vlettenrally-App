/**
 * Auth Service - JWT parsing and role extraction utilities
 */

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
 * Extract user role from a JWT token
 * Returns null if the token is invalid or doesn't contain a role
 */
export function getRoleFromToken(token: string): UserRole | null {
  const payload = parseJwtPayload(token);
  if (!payload) {
    return null;
  }
  
  // Check role claim first
  if (payload.role === 'admin' || payload.role === 'team') {
    return payload.role;
  }
  
  // Fall back to checking groups
  if (payload.groups?.includes('admin')) {
    return 'admin';
  }
  if (payload.groups?.includes('team')) {
    return 'team';
  }
  
  return null;
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
