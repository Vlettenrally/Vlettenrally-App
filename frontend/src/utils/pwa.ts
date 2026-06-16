/**
 * PWA install / display-mode detection.
 *
 * iOS Safari only delivers web push when the app is launched from the home
 * screen (standalone mode). These helpers let the check-in flow gate iOS users
 * into installing the app first. Style mirrors the UA-sniffing in
 * `deviceNickname.ts`.
 */

/** True for iPhone/iPad/iPod, including iPadOS that masquerades as macOS. */
export function isIOS(): boolean {
  if (/iPad|iPhone|iPod/.test(navigator.userAgent)) return true;
  // iPadOS 13+ reports a Mac user agent but is a touch device.
  return navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
}

/** True when the app is running as an installed PWA rather than a browser tab. */
export function isRunningStandalone(): boolean {
  if (window.matchMedia?.('(display-mode: standalone)').matches) return true;
  // iOS Safari exposes this non-standard flag instead of display-mode.
  return (navigator as { standalone?: boolean }).standalone === true;
}

/**
 * True when we should ask the user to add the app to their home screen before
 * continuing — i.e. an iOS device that isn't already running standalone, where
 * push notifications otherwise can't work.
 */
export function shouldPromptHomescreenInstall(): boolean {
  return isIOS() && !isRunningStandalone();
}
