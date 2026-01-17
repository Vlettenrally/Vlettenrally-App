/**
 * Date/Time Utilities
 */
import { format, formatDistanceToNow, differenceInSeconds, parseISO } from 'date-fns';
import { nl } from 'date-fns/locale';

export function formatDate(date: string | Date, formatStr = 'dd-MM-yyyy'): string {
  const dateObj = typeof date === 'string' ? parseISO(date) : date;
  return format(dateObj, formatStr, { locale: nl });
}

export function formatTime(date: string | Date, formatStr = 'HH:mm'): string {
  const dateObj = typeof date === 'string' ? parseISO(date) : date;
  return format(dateObj, formatStr, { locale: nl });
}

export function formatDateTime(date: string | Date, formatStr = 'dd-MM-yyyy HH:mm'): string {
  const dateObj = typeof date === 'string' ? parseISO(date) : date;
  return format(dateObj, formatStr, { locale: nl });
}

export function formatRelativeTime(date: string | Date): string {
  const dateObj = typeof date === 'string' ? parseISO(date) : date;
  return formatDistanceToNow(dateObj, { addSuffix: true, locale: nl });
}

export function getSecondsUntil(targetDate: string | Date): number {
  const target = typeof targetDate === 'string' ? parseISO(targetDate) : targetDate;
  return differenceInSeconds(target, new Date());
}

export function formatCountdown(seconds: number): string {
  if (seconds <= 0) return '00:00:00';
  
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;
  
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

export function formatCountdownShort(seconds: number): string {
  if (seconds <= 0) return 'Nu!';
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}u`;
  return `${Math.floor(seconds / 86400)}d`;
}

export function isInPast(date: string | Date): boolean {
  const dateObj = typeof date === 'string' ? parseISO(date) : date;
  return dateObj < new Date();
}

export function isInFuture(date: string | Date): boolean {
  return !isInPast(date);
}
