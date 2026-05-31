import { useCallback, useState } from 'react';
import { storage } from '../services/storage';

/**
 * Reads the server-issued device id (set after the first successful register-device
 * response) and the user-supplied nickname. We never generate a UUID client-side —
 * the backend mints it and we just persist what it returns. The {@code deviceId}
 * may be {@code null} on first visit; that's the signal to the API client to
 * request a fresh one from the server.
 */
export function useDeviceIdentity() {
  const [deviceId, setDeviceIdState] = useState<string | null>(() => storage.getDeviceId());
  const [nickname, setNicknameState] = useState<string>(() => storage.getDeviceNickname() ?? '');

  const setNickname = useCallback((next: string) => {
    setNicknameState(next);
    storage.setDeviceNickname(next);
  }, []);

  const setDeviceId = useCallback((next: string) => {
    storage.setDeviceId(next);
    setDeviceIdState(next);
  }, []);

  return { deviceId, nickname, setNickname, setDeviceId };
}

export default useDeviceIdentity;
