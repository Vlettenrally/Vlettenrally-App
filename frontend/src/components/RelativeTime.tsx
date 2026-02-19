/**
 * RelativeTime Component
 *
 * Displays a timestamp as relative time (e.g., "2 minutes ago")
 * and automatically updates every minute to keep it fresh.
 */
import { useEffect, useState } from 'react';
import { formatRelativeTime } from '../utils/dateTime';

interface RelativeTimeProps {
  date: string | Date;
  updateInterval?: number; // milliseconds
}

export function RelativeTime({ date, updateInterval = 60000 }: RelativeTimeProps) {
  const [, setTick] = useState(0);

  useEffect(() => {
    // Update every updateInterval milliseconds
    const timer = setInterval(() => {
      setTick(tick => tick + 1);
    }, updateInterval);

    return () => clearInterval(timer);
  }, [updateInterval]);

  return <>{formatRelativeTime(date)}</>;
}

