const API_BASE = '/api/v1';

export interface Team {
  id: string;
  game: string;
  name: string;
  description: string;
  boatNumber: string;
  color: string;
  participants: string[];
  deviceId: string;
  checkedIn: boolean;
  points: number;
  rivals: string[];
  notes: string;
  emergencyPhoneNumbers: string[];
  createdAt: string;
  updatedAt: string;
}

export async function getAllTeams(): Promise<Team[]> {
  const response = await fetch(`${API_BASE}/teams`);
  if (!response.ok) {
    throw new Error('Failed to fetch teams');
  }
  return response.json();
}

export async function getTeamById(teamId: string): Promise<Team> {
  const response = await fetch(`${API_BASE}/teams/${teamId}`);
  if (!response.ok) {
    throw new Error('Failed to fetch team');
  }
  return response.json();
}

export async function getRivalTeams(teamId: string): Promise<Team[]> {
  const response = await fetch(`${API_BASE}/teams/${teamId}/rivals`);
  if (!response.ok) {
    throw new Error('Failed to fetch rival teams');
  }
  return response.json();
}

export async function getMyTeam(accessToken: string): Promise<Team> {
  const response = await fetch(`${API_BASE}/team/me`, {
    headers: {
      'Authorization': `Bearer ${accessToken}`,
    },
  });
  if (!response.ok) {
    throw new Error('Failed to fetch current team');
  }
  return response.json();
}

export async function checkInTeam(teamId: string, accessToken: string): Promise<Team> {
  const response = await fetch(`${API_BASE}/teams/${teamId}/check-in`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
  });
  if (!response.ok) {
    throw new Error('Failed to check in team');
  }
  return response.json();
}

export async function updateEmergencyPhones(
  teamId: string,
  phoneNumbers: string[],
  accessToken: string
): Promise<Team> {
  const response = await fetch(`${API_BASE}/teams/${teamId}/emergency-phones`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ phoneNumbers }),
  });
  if (!response.ok) {
    throw new Error('Failed to update emergency phone numbers');
  }
  return response.json();
}
