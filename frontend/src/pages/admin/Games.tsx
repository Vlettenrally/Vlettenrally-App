import { useEffect, useState } from 'react';
import { Box, Typography, Button, Card, CardContent } from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';
import { gameService } from '../../services/gameService';

type Game = {
  id: string;
  name: string;
  description: string;
  status: string;
};

const Games = () => {
  const [games, setGames] = useState<Game[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchGames = async () => {
      try {
        setLoading(true);
        const games = await gameService.getGames();
        if (games) {
          setGames(games as Game[]);
        } else {
          setError('Failed to fetch games.');
        }
      } catch (err) {
        console.error(err);
        setError('An unexpected error occurred.');
      } finally {
        setLoading(false);
      }
    };

    fetchGames();
  }, []);

  if (loading) {
    return <div>Loading...</div>;
  }

  if (error) {
    return <div>{error}</div>;
  }

  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Games
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />}>
          Create Game
        </Button>
      </Box>
      {games.length === 0 ? (
        <Card>
          <CardContent>
            <Typography color="text.secondary">
              No games created yet
            </Typography>
          </CardContent>
        </Card>
      ) : (
        <ul>
          {games.map((game) => (
            <Card key={game.id}>
              <CardContent>
                <Typography variant="h6" component="div">
                  {game.name}
                </Typography>
                <Typography color="text.secondary">
                  {game.description}
                </Typography>
                <Typography variant="body2">
                  Status: {game.status}
                </Typography>
              </CardContent>
            </Card>
          ))}
        </ul>
      )}
    </Box>
  );
};

export default Games;
