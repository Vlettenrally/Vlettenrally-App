import { Alert, Box, Typography, Stack, Button } from '@mui/material';
import { IosShare, AddBox } from '@mui/icons-material';
import { useTranslation } from 'react-i18next';
import { getIOSBrowser } from '../utils/pwa';

interface IOSInstallGuideProps {
  onComplete: () => void;
  /**
   * Set when the user got here by tapping "I have an invite QR". They have to be told to scan
   * again from inside the installed app — scanning with the camera app would open the invite in
   * the browser, which is a different storage container from the home-screen app.
   */
  showInviteNote?: boolean;
}

export function IOSInstallGuide({ onComplete, showInviteNote = false }: IOSInstallGuideProps) {
  const { t } = useTranslation();
  const browser = getIOSBrowser();
  const isSafari = browser === 'safari';

  return (
    <Stack spacing={2}>
      <Typography variant="h6">{t('components.iosInstall.heading')}</Typography>
      <Typography variant="body2" color="textSecondary">
        {t('components.iosInstall.body')}
      </Typography>

      {showInviteNote && (
        <Alert severity="info">{t('components.iosInstall.inviteNote')}</Alert>
      )}

      <Box component="ol" sx={{ pl: 3, m: 0, '& li': { mb: 2 } }}>
        <Box component="li">
          <Typography variant="body2">
            {isSafari ? t('components.iosInstall.step1Safari') : t('components.iosInstall.step1Other')}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5, ml: 1 }}>
            <IosShare sx={{ color: '#007AFF', fontSize: 24 }} />
            <Typography variant="caption" color="textSecondary">{t('components.iosInstall.step1Caption')}</Typography>
          </Box>
        </Box>

        <Box component="li">
          <Typography variant="body2">
            {t('components.iosInstall.step2')}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mt: 0.5, ml: 1 }}>
            <AddBox sx={{ color: '#007AFF', fontSize: 24 }} />
            <Typography variant="caption" color="textSecondary">{t('components.iosInstall.step2Caption')}</Typography>
          </Box>
        </Box>

        <Typography component="li" variant="body2">
          {t('components.iosInstall.step3')}
        </Typography>
      </Box>

      <Button
        variant="contained"
        size="large"
        fullWidth
        onClick={onComplete}
      >
        {t('components.iosInstall.done')}
      </Button>

      <Typography variant="caption" color="textSecondary" sx={{ textAlign: 'center' }}>
        {t('components.iosInstall.failNote')}
      </Typography>
    </Stack>
  );
}
