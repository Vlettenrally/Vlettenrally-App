import { Box, Typography, Button, Card, CardContent } from '@mui/material';
import { Upload as ImportIcon } from '@mui/icons-material';

export default function Teams() {
  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Teams
        </Typography>
        <Button variant="contained" startIcon={<ImportIcon />}>
          Import Teams
        </Button>
      </Box>
      <Card>
        <CardContent>
          <Typography color="text.secondary">
            No teams registered yet
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
