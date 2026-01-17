import { Box, Typography, Button, Card, CardContent } from '@mui/material';
import { Send as SendIcon } from '@mui/icons-material';

export default function AdminMessages() {
  return (
    <Box sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
        <Typography variant="h4" component="h1" sx={{ fontWeight: 'bold' }}>
          Messages
        </Typography>
        <Button variant="contained" startIcon={<SendIcon />}>
          Send Message
        </Button>
      </Box>
      <Card>
        <CardContent>
          <Typography color="text.secondary">
            No message templates yet
          </Typography>
        </CardContent>
      </Card>
    </Box>
  );
}
