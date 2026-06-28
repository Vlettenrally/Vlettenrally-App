import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { teamService } from '../services/teamService';
import type { PublicTeam } from '../types/entities';

interface RivalTeamsProps {
  teamId: string;
}

export const RivalTeams: React.FC<RivalTeamsProps> = ({ teamId }) => {
  const { t } = useTranslation();
  const [rivals, setRivals] = useState<PublicTeam[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchRivals = async () => {
      try {
        const rivalTeams: PublicTeam[] = await teamService.getRivalTeams(teamId);
        setRivals(rivalTeams);
      } catch (err) {
        setError(t('components.rivalTeams.loadFailed') + (err as Error).message);
      } finally {
        setLoading(false);
      }
    };

    fetchRivals();
  }, [teamId, t]);

  if (loading) return <div>{t('components.rivalTeams.loading')}</div>;
  if (error) return <div>{error}</div>;
  if (rivals.length === 0) return <div>{t('components.rivalTeams.noRivals')}</div>;

  return (
    <div className="rival-teams">
      <h3>{t('components.rivalTeams.title', { count: rivals.length })}</h3>
      <ul>
        {rivals.map((rival) => (
          <li key={rival.id} style={{ borderLeft: `4px solid` }}>
            <span className="rival-name">{rival.name}</span>
            <span className="rival-points">{t('components.rivalTeams.points', { count: rival.totalPoints })}</span>
          </li>
        ))}
      </ul>
    </div>
  );
};
