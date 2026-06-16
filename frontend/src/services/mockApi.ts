/**
 * Mock API for development without backend
 * Returns fake data for all API endpoints
 */

import type { Game } from '../types/entities';

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
  finished: false,
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
  { id: 'cp-1', name: 'Huis joost', gameId: MOCK_GAME_ID },
  { id: 'cp-2', name: 'Waag', gameId: MOCK_GAME_ID },
  { id: 'cp-3', name: 'Bruggetje', gameId: MOCK_GAME_ID },
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
  
  // Catch-all for any other API calls
  async get() {
    return { data: null, status: 200 };
  },
  
  async post() {
    return { data: null, status: 200 };
  },
};
