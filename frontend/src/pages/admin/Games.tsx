import { Box, Typography, Button, Card, CardContent } from '@mui/material';
import { Add as AddIcon } from '@mui/icons-material';

export default function Games() {
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
      <Card>
        <CardContent>
          <Typography color="text.secondary">
            No games created yet
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
