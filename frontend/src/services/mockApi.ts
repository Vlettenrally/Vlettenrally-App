/**
 * Mock API for development without backend
 * Returns fake data for all API endpoints
 */

import type { Game, Picture, PictureCursor, PictureStatus } from '../types/entities';

const MOCK_TEAM_ID = '11111111-1111-1111-1111-111111111111';
const MOCK_GAME_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

// Mock messages
const mockMessages = [
  {
    id: 'msg-1',
    teamId: MOCK_TEAM_ID,
    messageType: 'checkpoint',
    content: 'Je bent op Checkpoint Huis joost geweest. Ga zo door!',
    sentAt: new Date(Date.now() - 5 * 60 * 60 * 1000).toISOString(),
    delivered: true,
    read: true
  },
  {
    id: 'msg-2',
    teamId: MOCK_TEAM_ID,
    messageType: 'checkpoint',
    content: 'Je bent op Checkpoint Waag geweest. Ga zo door!',
    sentAt: new Date(Date.now() - 5.5 * 60 * 60 * 1000).toISOString(),
    delivered: true,
    read: true
  },
  {
    id: 'msg-3',
    teamId: MOCK_TEAM_ID,
    messageType: 'checkpoint',
    content: 'Je bent op Checkpoint Bruggetje geweest. Ga zo door!',
    sentAt: new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString(),
    delivered: true,
    read: true
  },
  {
    id: 'msg-4',
    teamId: MOCK_TEAM_ID,
    messageType: 'rival',
    content: 'Lekker bezig! Je hebt Team Joost ingehaald.',
    sentAt: new Date(Date.now() - 6.2 * 60 * 60 * 1000).toISOString(),
    delivered: true,
    read: true
  },
  {
    id: 'msg-5',
    teamId: MOCK_TEAM_ID,
    messageType: 'rallydesk',
    content: 'De Vlettenrally is gestart! Have fun! 🎉',
    sentAt: new Date(Date.now() - 6.5 * 60 * 60 * 1000).toISOString(),
    delivered: true,
    read: false
  },
  {
    id: 'msg-6',
    teamId: MOCK_TEAM_ID,
    messageType: 'achievement',
    content: 'Gefeliciteerd! Je hebt 3 checkpoints behaald! 🏆',
    sentAt: new Date(Date.now() - 4 * 60 * 60 * 1000).toISOString(),
    delivered: true,
    read: false
  },
  {
    id: 'msg-7',
    teamId: MOCK_TEAM_ID,
    messageType: 'countdown',
    content: 'Nog 2 uur tot het einde van de rally! ⏱️',
    sentAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
    delivered: true,
    read: false
  },
  {
    id: 'msg-8',
    teamId: MOCK_TEAM_ID,
    messageType: 'speed_warning',
    content: 'Let op! Jullie snelheid is te hoog. Vaar alsjeblieft langzamer.',
    sentAt: new Date(Date.now() - 15 * 60 * 1000).toISOString(),
    delivered: true,
    read: false
  }
];

// Mock team
const mockTeam = {
  id: MOCK_TEAM_ID,
  gameId: MOCK_GAME_ID,
  name: 'De Watervlooien',
  description: 'Boot 42',
  totalPoints: 420,
  checkedIn: true,
  checkedInDigitally: true,
  checkInMethod: 'DIGITAL',
  finished: false,
  finishedAt: null,
  rivals: [],
  createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString()
};

// Mock achievements
const mockAchievements = [
  { id: 'ach-1', teamId: MOCK_TEAM_ID, checkpointId: 'cp-1', pointsEarned: 100, achievedAt: new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString() },
  { id: 'ach-2', teamId: MOCK_TEAM_ID, checkpointId: 'cp-2', pointsEarned: 100, achievedAt: new Date(Date.now() - 5.5 * 60 * 60 * 1000).toISOString() },
  { id: 'ach-3', teamId: MOCK_TEAM_ID, checkpointId: 'cp-3', pointsEarned: 100, achievedAt: new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString() },
];

// Mock checkpoints
const mockCheckpoints = [
  { id: 'cp-1', name: 'Huis joost', gameId: MOCK_GAME_ID, color: '#e53935' },
  { id: 'cp-2', name: 'Waag', gameId: MOCK_GAME_ID, color: '#1e88e5' },
  { id: 'cp-3', name: 'Bruggetje', gameId: MOCK_GAME_ID, color: '#43a047' },
  { id: 'cp-4', name: 'Haven', gameId: MOCK_GAME_ID },
  { id: 'cp-5', name: 'Centrum', gameId: MOCK_GAME_ID },
  { id: 'cp-6', name: 'Park', gameId: MOCK_GAME_ID },
];

// Mock game
const mockGame: Game = {
  id: MOCK_GAME_ID,
  name: 'Vlettenrally 2026',
  description: 'De jaarlijkse Vlettenrally',
  startTime: new Date(Date.now() - 6 * 60 * 60 * 1000), // Started 6 hours ago
  endTime: new Date(Date.now() + 2 * 60 * 60 * 1000), // Ends in 2 hours
  status: 'active',
  participantInfo: 'Veel plezier!',
  createdAt: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000),
  updatedAt: new Date(),
};

// Mock pictures (mutable so the upload/moderation flow can be exercised)
let mockPictureSeq = 100;

// Team name/description used to attribute a picture to the uploading scouting group.
const MOCK_TEAM_INFO: Record<string, { name: string; description: string }> = {
  [MOCK_TEAM_ID]: { name: 'De Watervlooien', description: 'Snelle en wendbare vlettenvaarders' },
  '22222222-2222-2222-2222-222222222222': { name: 'Windkracht Tien', description: 'Altijd volle kracht vooruit' },
};
const teamNameFor = (teamId: string | null) => (teamId ? MOCK_TEAM_INFO[teamId]?.name ?? null : null);
const teamDescriptionFor = (teamId: string | null) => (teamId ? MOCK_TEAM_INFO[teamId]?.description ?? null : null);

const mockPictures: Picture[] = ([
  {
    id: 'pic-1', teamId: MOCK_TEAM_ID, gameId: MOCK_GAME_ID, status: 'APPROVED',
    contentType: 'image/jpeg', sizeBytes: 512000, viewUrl: 'https://picsum.photos/seed/pic-1/600/400',
    createdAt: new Date(Date.now() - 120 * 60 * 1000).toISOString(),
    uploadedAt: new Date(Date.now() - 118 * 60 * 1000).toISOString(),
    moderatedAt: new Date(Date.now() - 60 * 60 * 1000).toISOString(), notes: null,
  },
  {
    id: 'pic-2', teamId: MOCK_TEAM_ID, gameId: MOCK_GAME_ID, status: 'PENDING',
    contentType: 'image/jpeg', sizeBytes: 480000, viewUrl: 'https://picsum.photos/seed/pic-2/600/400',
    createdAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
    uploadedAt: new Date(Date.now() - 9 * 60 * 1000).toISOString(),
    moderatedAt: null, notes: null,
  },
  {
    id: 'pic-3', teamId: '22222222-2222-2222-2222-222222222222', gameId: MOCK_GAME_ID, status: 'PENDING',
    contentType: 'image/png', sizeBytes: 720000, viewUrl: 'https://picsum.photos/seed/pic-3/600/400',
    createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    uploadedAt: new Date(Date.now() - 4 * 60 * 1000).toISOString(),
    moderatedAt: null, notes: null,
  },
  // A few more approved pictures (incl. another team's) so the players' photowall is populated.
  {
    id: 'pic-4', teamId: '22222222-2222-2222-2222-222222222222', gameId: MOCK_GAME_ID, status: 'APPROVED',
    contentType: 'image/jpeg', sizeBytes: 530000, viewUrl: 'https://picsum.photos/seed/pic-4/600/400',
    createdAt: new Date(Date.now() - 75 * 60 * 1000).toISOString(),
    uploadedAt: new Date(Date.now() - 73 * 60 * 1000).toISOString(),
    moderatedAt: new Date(Date.now() - 40 * 60 * 1000).toISOString(), notes: null,
  },
  {
    id: 'pic-5', teamId: MOCK_TEAM_ID, gameId: MOCK_GAME_ID, status: 'APPROVED',
    contentType: 'image/jpeg', sizeBytes: 610000, viewUrl: 'https://picsum.photos/seed/pic-5/600/400',
    createdAt: new Date(Date.now() - 50 * 60 * 1000).toISOString(),
    uploadedAt: new Date(Date.now() - 48 * 60 * 1000).toISOString(),
    moderatedAt: new Date(Date.now() - 20 * 60 * 1000).toISOString(), notes: null,
  },
  {
    id: 'pic-6', teamId: '22222222-2222-2222-2222-222222222222', gameId: MOCK_GAME_ID, status: 'APPROVED',
    contentType: 'image/jpeg', sizeBytes: 580000, viewUrl: 'https://picsum.photos/seed/pic-6/600/400',
    createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
    uploadedAt: new Date(Date.now() - 28 * 60 * 1000).toISOString(),
    moderatedAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(), notes: null,
  },
  // Bulk filler so the photowall pages past the first batch in mock mode. Attributed to a third
  // team so the mock upload caps on MOCK_TEAM_ID keep behaving like the real ones.
  ...Array.from({ length: 30 }, (_, i) => {
    const created = Date.now() - (200 + i * 3) * 60 * 1000;
    return {
      id: `pic-fill-${i}`, teamId: '33333333-3333-3333-3333-333333333333',
      gameId: MOCK_GAME_ID, status: 'APPROVED' as PictureStatus,
      contentType: 'image/jpeg', sizeBytes: 500000,
      viewUrl: `https://picsum.photos/seed/pic-fill-${i}/600/400`,
      createdAt: new Date(created).toISOString(),
      uploadedAt: new Date(created + 2 * 60 * 1000).toISOString(),
      moderatedAt: new Date(created + 5 * 60 * 1000).toISOString(), notes: null,
    };
  }),
] as Omit<Picture, 'teamName' | 'teamDescription'>[]).map((p) => ({
  ...p,
  teamName: teamNameFor(p.teamId),
  teamDescription: teamDescriptionFor(p.teamId),
}));

const MOCK_PICTURE_PAGE_SIZE = 24;

/** Sort key of a picture as a comparable string; missing timestamps sort last. */
function sortKey(picture: Picture, key: 'uploadedAt' | 'createdAt'): string {
  return picture[key] ?? '';
}

/**
 * Mirror of the backend's keyset paging: drop everything up to and including the cursor row,
 * then take one batch. `rows` must already be sorted in the direction the caller wants.
 */
function pageOf(
  rows: Picture[],
  cursor: PictureCursor | undefined,
  key: 'uploadedAt' | 'createdAt',
  ascending: boolean,
) {
  const past = cursor
    ? rows.filter((p) => {
        const cmp = sortKey(p, key).localeCompare(cursor.at);
        if (cmp !== 0) return ascending ? cmp > 0 : cmp < 0;
        const byId = p.id.localeCompare(cursor.id);
        return ascending ? byId > 0 : byId < 0;
      })
    : rows;
  const pictures = past.slice(0, MOCK_PICTURE_PAGE_SIZE).map((p) => ({ ...p }));
  const hasMore = past.length > MOCK_PICTURE_PAGE_SIZE;
  const last = pictures[pictures.length - 1];
  return {
    pictures,
    nextCursorAt: hasMore && last ? sortKey(last, key) : null,
    nextCursorId: hasMore && last ? last.id : null,
    hasMore,
  };
}

/** Approved pictures newest first, with the same (uploadedAt, id) total ordering as the API. */
function approvedNewestFirst(gameId?: string): Picture[] {
  return mockPictures
    .filter((p) => p.status === 'APPROVED' && (!gameId || p.gameId === gameId))
    .sort(
      (a, b) =>
        sortKey(b, 'uploadedAt').localeCompare(sortKey(a, 'uploadedAt')) || b.id.localeCompare(a.id),
    );
}

export const mockApi = {
  async getMessages() {
    return { data: mockMessages, status: 200 };
  },
  
  async getMyTeam() {
    return { data: mockTeam, status: 200 };
  },
  
  async getTeamVerifiedAchievements() {
    return { data: mockAchievements, status: 200 };
  },
  
  async getCheckpoints() {
    return { data: mockCheckpoints, status: 200 };
  },

  async getMyLatestLocation() {
    return {
      data: {
        teamId: MOCK_TEAM_ID,
        teamName: 'De Watervlooien',
        latitude: 52.3580,
        longitude: 4.9001,
        speed: 3.5,
        heading: 120,
        locationTime: new Date(Date.now() - 60_000).toISOString(),
      },
      status: 200,
    };
  },
  
  async getGameById() {
    return { data: mockGame, status: 200 };
  },
  
  // Add other endpoints to prevent errors
  async getRival() {
    return { data: null, status: 200 };
  },
  
  async getTeamRankings() {
    return { data: [], status: 200 };
  },
  
  // ==================== Picture endpoints ====================
  async requestPictureUpload(teamId: string, body: { contentType: string; fileName?: string; sizeBytes?: number }) {
    const total = mockPictures.filter((p) => p.teamId === teamId).length;
    if (total >= 10) {
      return { error: 'Upload limit reached', status: 409 };
    }
    const active = mockPictures.filter((p) => p.teamId === teamId && p.status !== 'REJECTED').length;
    if (active >= 5) {
      return { error: 'Picture limit reached (5 per team).', status: 409 };
    }
    const pictureId = `pic-${++mockPictureSeq}`;
    mockPictures.push({
      id: pictureId, teamId, gameId: MOCK_GAME_ID, status: 'PENDING',
      contentType: body.contentType, sizeBytes: body.sizeBytes ?? null, viewUrl: null,
      createdAt: new Date().toISOString(), uploadedAt: null, moderatedAt: null, notes: null,
      teamName: teamNameFor(teamId), teamDescription: teamDescriptionFor(teamId),
    });
    return {
      data: {
        pictureId,
        objectKey: `games/${MOCK_GAME_ID}/teams/${teamId}/${pictureId}`,
        uploadUrl: `http://localhost:8080/mock-upload/${pictureId}`,
        contentType: body.contentType,
        expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
        maxBytes: 10485760,
        allowedContentTypes: ['image/jpeg', 'image/png', 'image/webp'],
      },
      status: 201,
    };
  },

  async uploadFileToS3(_uploadUrl: string, _file: File, _contentType: string, onProgress?: (p: number) => void) {
    if (onProgress) {
      onProgress(50);
      onProgress(100);
    }
    return { ok: true, status: 200 };
  },

  async confirmPictureUpload(_teamId: string, pictureId: string) {
    const picture = mockPictures.find((p) => p.id === pictureId);
    if (!picture) return { error: 'Not found', status: 404 };
    picture.uploadedAt = new Date().toISOString();
    picture.sizeBytes = picture.sizeBytes ?? 512000;
    picture.viewUrl = `https://picsum.photos/seed/${pictureId}/600/400`;
    return { data: { ...picture }, status: 200 };
  },

  async getMyPictures(teamId: string) {
    return { data: mockPictures.filter((p) => p.teamId === teamId).map((p) => ({ ...p })), status: 200 };
  },

  async getPendingPictures(gameId?: string, cursor?: PictureCursor) {
    const rows = mockPictures
      .filter((p) => p.status === 'PENDING' && p.uploadedAt && (!gameId || p.gameId === gameId))
      .sort((a, b) => sortKey(a, 'uploadedAt').localeCompare(sortKey(b, 'uploadedAt')) || a.id.localeCompare(b.id));
    return { data: pageOf(rows, cursor, 'uploadedAt', true), status: 200 };
  },

  async getAllPictures(status?: string, gameId?: string, cursor?: PictureCursor) {
    const upper = status?.toUpperCase() as PictureStatus | undefined;
    const rows = mockPictures
      .filter((p) => (!upper || p.status === upper) && (!gameId || p.gameId === gameId))
      .sort((a, b) => sortKey(b, 'createdAt').localeCompare(sortKey(a, 'createdAt')) || b.id.localeCompare(a.id));
    return { data: pageOf(rows, cursor, 'createdAt', false), status: 200 };
  },

  async getApprovedPictures(gameId?: string, cursor?: PictureCursor) {
    return { data: pageOf(approvedNewestFirst(gameId), cursor, 'uploadedAt', false), status: 200 };
  },

  // Players' photowall: approved pictures for the (single mock) game, newest first.
  // Mirrors the public-safe projection and the cursor paging of the real endpoint.
  async getPhotowall(_teamId?: string, cursor?: PictureCursor) {
    const page = pageOf(approvedNewestFirst(), cursor, 'uploadedAt', false);
    return {
      data: {
        ...page,
        pictures: page.pictures.map((p) => ({
          id: p.id,
          gameId: p.gameId,
          contentType: p.contentType,
          viewUrl: p.viewUrl,
          uploadedAt: p.uploadedAt,
          teamName: p.teamName,
          teamDescription: p.teamDescription,
        })),
      },
      status: 200,
    };
  },

  async approvePicture(pictureId: string) {
    const picture = mockPictures.find((p) => p.id === pictureId);
    if (!picture) return { error: 'Not found', status: 404 };
    picture.status = 'APPROVED';
    picture.moderatedAt = new Date().toISOString();
    return { data: { ...picture }, status: 200 };
  },

  async rejectPicture(pictureId: string, notes?: string) {
    const picture = mockPictures.find((p) => p.id === pictureId);
    if (!picture) return { error: 'Not found', status: 404 };
    picture.status = 'REJECTED';
    picture.moderatedAt = new Date().toISOString();
    picture.notes = notes ?? null;
    return { data: { ...picture }, status: 200 };
  },

  async requestAdminPictureUpload(body: { contentType: string; fileName?: string; sizeBytes?: number }) {
    const pictureId = `pic-admin-${++mockPictureSeq}`;
    mockPictures.push({
      id: pictureId, teamId: null as unknown as string, gameId: MOCK_GAME_ID, status: 'PENDING',
      contentType: body.contentType, sizeBytes: body.sizeBytes ?? null, viewUrl: null,
      createdAt: new Date().toISOString(), uploadedAt: null, moderatedAt: null, notes: null,
      teamName: null, teamDescription: null,
    });
    return {
      data: {
        pictureId,
        objectKey: `games/${MOCK_GAME_ID}/admin/${pictureId}`,
        uploadUrl: `http://localhost:8080/mock-upload/${pictureId}`,
        contentType: body.contentType,
        expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
        maxBytes: 10485760,
        allowedContentTypes: ['image/jpeg', 'image/png', 'image/webp'],
      },
      status: 201,
    };
  },

  async confirmAdminPictureUpload(pictureId: string) {
    const picture = mockPictures.find((p) => p.id === pictureId);
    if (!picture) return { error: 'Not found', status: 404 };
    picture.uploadedAt = new Date().toISOString();
    picture.sizeBytes = picture.sizeBytes ?? 512000;
    picture.viewUrl = `https://picsum.photos/seed/${pictureId}/600/400`;
    picture.status = 'APPROVED';
    picture.moderatedAt = new Date().toISOString();
    return { data: { ...picture }, status: 200 };
  },

  // There is no backend to stream a zip from in mock mode; the page shows a notice instead
  // of navigating, so these only need to exist to keep the api-shaped cast honest.
  async requestPictureDownloadToken() {
    return { data: { token: 'mock-download-token', expiresIn: 60 }, status: 200 };
  },

  pictureArchiveUrl(token: string) {
    return `/mock-download/${encodeURIComponent(token)}`;
  },

  // Catch-all for any other API calls
  async get() {
    return { data: null, status: 200 };
  },
  
  async post() {
    return { data: null, status: 200 };
  },
};
