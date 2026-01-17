import React, { useEffect, useState } from 'react';
import { type Team, getRivalTeams } from '../services/teamService';

interface RivalTeamsProps {
  teamId: string;
}

export const RivalTeams: React.FC<RivalTeamsProps> = ({ teamId }) => {
  const [rivals, setRivals] = useState<Team[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchRivals = async () => {
      try {
        const rivalTeams = await getRivalTeams(teamId);
        setRivals(rivalTeams);
      } catch (err) {
        setError('Failed to load rival teams:' + (err as Error).message);
      } finally {
        setLoading(false);
      }
    };

    fetchRivals();
  }, [teamId]);

  if (loading) return <div>Loading rivals...</div>;
  if (error) return <div>{error}</div>;
  if (rivals.length === 0) return <div>No rival teams</div>;

  return (
    <div className="rival-teams">
      <h3>Rival Teams ({rivals.length})</h3>
      <ul>
        {rivals.map((rival) => (
          <li key={rival.id} style={{ borderLeft: `4px solid ${rival.color}` }}>
            <span className="rival-name">{rival.name}</span>
            <span className="rival-boat">Boat #{rival.boatNumber}</span>
            <span className="rival-points">{rival.points} points</span>
          </li>
        ))}
      </ul>
    </div>
  );
};
