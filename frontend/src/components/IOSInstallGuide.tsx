import { Box, Typography, Stack, Button } from '@mui/material';
import { IosShare, AddBox } from '@mui/icons-material';
import { getIOSBrowser } from '../utils/pwa';

interface IOSInstallGuideProps {
  onComplete: () => void;
}

export function IOSInstallGuide({ onComplete }: IOSInstallGuideProps) {
  const browser = getIOSBrowser();
  const isSafari = browser === 'safari';

  return (
    <Stack spacing={2}>
      <Typography variant="h6">Hé iPhone gebruiker, we willen je graag notificaties sturen!</Typography>
      <Typography variant="body2" color="text.secondary">
        Op een iPhone kun je alleen meldingen ontvangen als je de app eerst aan je
        beginscherm toevoegt en daarna vanaf daar opent. Zo blijf je tijdens de rally op de hoogte
        van belangrijke berichten.
      </Typography>
      
      <Box component="ol" sx={{ pl: 3, m: 0, '& li': { mb: 2 } }}>
        <Box component="li">
          <Typography variant="body2">
            {isSafari ? 'Tik op het deel-icoon rechts bovenin' : 'Tik rechtsonder op de drie puntjes, dan op het deel-icoon'}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5, ml: 1 }}>
            <IosShare sx={{ color: '#007AFF', fontSize: 24 }} />
            <Typography variant="caption" color="text.secondary">(vierkantje met pijl omhoog)</Typography>
          </Box>
        </Box>
        
        <Box component="li">
          <Typography variant="body2">
            Kies <strong>"Zet op beginscherm"</strong> en bevestig met <strong>"Voeg toe"</strong>.
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5, ml: 1 }}>
            <AddBox sx={{ color: '#007AFF', fontSize: 24 }} />
            <Typography variant="caption" color="text.secondary">(plus-icoon)</Typography>
          </Box>
        </Box>
        
        <Typography component="li" variant="body2">
          Open de Vlettenrally-app vanaf je beginscherm en check daar in.
        </Typography>
      </Box>
      
      <Button
        variant="contained"
        size="large"
        fullWidth
        onClick={onComplete}
      >
        Ik heb het gedaan, ga verder
      </Button>
      
      <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
        Lukt het toevoegen niet? Je kunt ook hier verder inchecken, maar dan ontvang je geen
        meldingen op dit apparaat.
      </Typography>
    </Stack>
  );
}
