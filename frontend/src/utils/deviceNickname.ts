/**
 * Best-effort friendly nickname for the current browser/device, used as the
 * default label for admin/team device rows. Admins can rename in settings.
 */
export function suggestDeviceNickname(userAgent: string = navigator.userAgent): string {
  const ua = userAgent;
  const browser = detectBrowser(ua);
  const platform = detectPlatform(ua);
  if (browser && platform) return `${browser} op ${platform}`;
  if (browser) return browser;
  if (platform) return `Browser op ${platform}`;
  return 'Nieuw apparaat';
}

function detectBrowser(ua: string): string | null {
  if (/Edg\//i.test(ua)) return 'Edge';
  if (/OPR\//i.test(ua)) return 'Opera';
  if (/Firefox\//i.test(ua)) return 'Firefox';
  if (/Chrome\//i.test(ua) && !/Chromium/i.test(ua)) return 'Chrome';
  if (/Safari\//i.test(ua) && !/Chrome\//i.test(ua)) return 'Safari';
  return null;
}

function detectPlatform(ua: string): string | null {
  if (/iPhone|iPad|iPod/i.test(ua)) return 'iOS';
  if (/Android/i.test(ua)) return 'Android';
  if (/Mac OS X|Macintosh/i.test(ua)) return 'macOS';
  if (/Windows/i.test(ua)) return 'Windows';
  if (/Linux/i.test(ua)) return 'Linux';
  return null;
}
