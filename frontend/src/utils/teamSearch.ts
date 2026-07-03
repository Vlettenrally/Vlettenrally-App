export function teamMatchesQuery(team: { name: string; description?: string }, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return true;
  return team.name.toLowerCase().includes(q) || (team.description ?? '').toLowerCase().includes(q);
}
