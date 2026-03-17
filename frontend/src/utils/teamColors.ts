// Color palette for teams
const TEAM_COLORS = [
  '#e53935', '#1e88e5', '#43a047', '#fb8c00', '#8e24aa',
  '#00acc1', '#d81b60', '#3949ab', '#7cb342', '#f4511e',
  '#6d4c41', '#546e7a', '#c0ca33', '#5e35b1', '#039be5',
];

/**
 * Get a consistent color for a team based on its ID
 */
export function getTeamColor(teamId: string): string {
  let hash = 0;
  for (let i = 0; i < teamId.length; i++) {
    hash = ((hash << 5) - hash) + teamId.charCodeAt(i);
    hash |= 0;
  }
  return TEAM_COLORS[Math.abs(hash) % TEAM_COLORS.length];
}

