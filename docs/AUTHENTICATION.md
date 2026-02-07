# Authentication Flow

## Overview
The Vlettenrally app uses a three-token authentication system to enable external registration while maintaining secure access:

1. **Registration Token** (one-time use, 15 min expiry)
2. **Access Token** (1 hour expiry, used for API calls)
3. **Refresh Token** (7 days expiry, used to get new access tokens)

### Game-Scoped Check-in Links
For admin-generated check-in links, the API issues a **game-scoped registration token**. This token allows a participant to select their team from the game and then receive the normal access/refresh tokens.

**Expiry rules for game-scoped tokens:**
- Expires at the earlier of: (1) 5 minutes after all teams check in, or (2) game start time.

**Admin endpoint to generate a game-scoped link:**
```
POST /api/v1/games/{gameId}/check-in-link
```

## Flow Diagram

```
Third Party             API                    Player App
    |                   |                          |
    | 1. POST team data |                          |
    |------------------>|                          |
    |                   | 2. Generate reg token    |
    |                   |    (15 min expiry)       |
    | 3. Return token   |                          |
    |<------------------|                          |
    |                   |                          |
    | 4. Redirect with token                       |
    |--------------------------------------------->|
    |                   |                          |
    |                   | 5. Exchange reg token    |
    |                   |<-------------------------|
    |                   |    for access+refresh    |
    |                   |                          |
    |                   | 6. Return tokens         |
    |                   |------------------------->|
    |                   |    (access: 1h,          |
    |                   |     refresh: 7d)         |
    |                   |                          |
    |                   | 7. GET /team/me          |
    |                   |<-------------------------|
    |                   |    (Bearer: access)      |
    |                   |                          |
    |                   | 8. Return team data      |
    |                   |------------------------->|
    |                   |                          |
    |                   |    [Later: access expired]
    |                   |                          |
    |                   | 9. POST /auth/refresh    |
    |                   |<-------------------------|
    |                   |    (refresh token)       |
    |                   |                          |
    |                   | 10. New access token     |
    |                   |------------------------->|
```

## API Endpoints

### 1. Register Team (Third Party)
```
POST /api/v1/auth/register
Content-Type: application/json

{
  "name": "Scouting Nederland",
  "description": "Team description",
  "boatNumber": "42",
  "participants": ["Jan", "Piet", "Klaas"],
  "notes": "Optional notes"
}

Response (200 OK):
{
  "registrationToken": "abc123...",
  "expiresAt": "2024-01-01T12:15:00Z",
  "redirectUrl": "https://player.vlettenrally.nl/?token=abc123..."
}
```

### 2. Exchange Token (Player App)
```
POST /api/v1/auth/token
Authorization: Bearer abc123...

Response (200 OK):
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "refresh_xyz...",
  "expiresIn": 3600
}

Response for game-scoped token (200 OK):
{
  "gameId": "uuid",
  "teamOptions": [
    { "id": "uuid", "name": "Scouting Nederland", "boatNumber": "42" }
  ]
}
```

### 2b. Select Team (Player App)
```
POST /api/v1/auth/select-team
Authorization: Bearer <checkin-token>
Content-Type: application/json

{
  "teamId": "uuid"
}

Response (200 OK):
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "refresh_xyz...",
  "expiresIn": 3600
}
```

### 3. Refresh Access Token
```
POST /api/v1/auth/refresh
Content-Type: application/json

{
  "refreshToken": "refresh_xyz..."
}

Response (200 OK):
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "refresh_abc...",  // New refresh token
  "expiresIn": 3600
}
```

### 4. Get Team Data
```
GET /api/v1/team/me
Authorization: Bearer eyJhbGciOi...

Response (200 OK):
{
  "id": "uuid",
  "game": "game-uuid",
  "name": "Scouting Nederland",
  "description": "Team description",
  "boatNumber": "42",
  "participants": ["Jan", "Piet", "Klaas"],
  "deviceId": null,
  "checkedIn": true,
  "points": 0,
  "rivals": [],
  "notes": "Optional notes",
  "createdAt": "2024-01-01T12:00:00Z",
  "updatedAt": "2024-01-01T12:00:00Z"
}
```

## Frontend Implementation

### Token Storage
```typescript
// Store tokens after exchange
storage.setAuthToken(accessToken);
storage.setRefreshToken(refreshToken);

// Use access token for API calls (automatic in api.ts)
const response = await api.getMyTeam();

// Clear on logout
storage.clearSession();
```

### Automatic Token Refresh
When the access token expires, the API client should:
1. Detect 401 Unauthorized response
2. Use refresh token to get new access token
3. Retry original request with new token
4. If refresh fails → redirect to check-in page

**TODO**: Implement automatic token refresh in `api.ts`

## Security Considerations

1. **Registration Token**: 
   - Single-use only
   - Short expiry (15 minutes)
   - Invalidated after exchange

2. **Access Token**:
   - Short-lived (1 hour)
   - Used for all API requests
   - Stored in localStorage

3. **Refresh Token**:
   - Longer-lived (7 days)
   - Only used to get new access tokens
   - Rotated on each refresh
   - Stored in localStorage

4. **HTTPS**: All communication must use HTTPS in production

5. **Token Validation**: Backend validates:
   - Token signature (when using JWT)
   - Token expiry
   - Token type (registration vs access vs refresh)
   - One-time use for registration tokens

## Backend Status

### ✅ Implemented
- `/api/v1/auth/register` - Team registration endpoint
- `/api/v1/auth/token` - Token exchange endpoint
- `/api/v1/auth/refresh` - Token refresh endpoint
- `/api/v1/team/me` - Get authenticated team data
- In-memory token storage (ConcurrentHashMap)
- Token expiry validation

### ⚠️ Mocked (Needs Implementation)
- JWT token generation (currently using simple strings)
- Token signature validation
- Database persistence for teams and tokens
- Refresh token rotation

### 🔲 TODO
- Implement proper JWT with SmallRye JWT
- Add database entities (Team, Token)
- Implement refresh token rotation
- Add rate limiting on auth endpoints
- Add audit logging for authentication events
- Implement token revocation (logout)

## Frontend Status

### ✅ Implemented
- CheckInPage with URL token parameter
- Token exchange flow
- Storage service with refresh token support
- API client with Bearer token authentication
- Team store with updated Team interface

### 🔲 TODO
- Automatic token refresh in API client
- Handle 401 responses gracefully
- Logout functionality
- Token expiry warnings
