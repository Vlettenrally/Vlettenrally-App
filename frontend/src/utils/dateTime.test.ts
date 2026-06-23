import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  formatCountdown,
  formatCountdownShort,
  getSecondsUntil,
  isInPast,
  isInFuture,
} from './dateTime';

describe('formatCountdown', () => {
  it('clamps zero and negative seconds to 00:00:00', () => {
    expect(formatCountdown(0)).toBe('00:00:00');
    expect(formatCountdown(-5)).toBe('00:00:00');
  });

  it('formats sub-minute, sub-hour, and multi-hour values with zero-padding', () => {
    expect(formatCountdown(5)).toBe('00:00:05');
    expect(formatCountdown(59)).toBe('00:00:59');
    expect(formatCountdown(60)).toBe('00:01:00');
    expect(formatCountdown(3599)).toBe('00:59:59');
    expect(formatCountdown(3600)).toBe('01:00:00');
    expect(formatCountdown(3661)).toBe('01:01:01');
  });

  it('does not cap hours at 24', () => {
    expect(formatCountdown(90000)).toBe('25:00:00');
  });
});

describe('formatCountdownShort', () => {
  it('returns Nu! at or below zero', () => {
    expect(formatCountdownShort(0)).toBe('Nu!');
    expect(formatCountdownShort(-1)).toBe('Nu!');
  });

  it('switches units at each boundary', () => {
    expect(formatCountdownShort(59)).toBe('59s');
    expect(formatCountdownShort(60)).toBe('1m');
    expect(formatCountdownShort(3599)).toBe('59m');
    expect(formatCountdownShort(3600)).toBe('1u');
    expect(formatCountdownShort(86399)).toBe('23u');
    expect(formatCountdownShort(86400)).toBe('1d');
    expect(formatCountdownShort(172800)).toBe('2d');
  });
});

describe('getSecondsUntil', () => {
  afterEach(() => vi.useRealTimers());

  it('is positive for future and negative for past dates', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-24T12:00:00.000Z'));
    expect(getSecondsUntil('2026-06-24T12:00:30.000Z')).toBe(30);
    expect(getSecondsUntil('2026-06-24T11:59:30.000Z')).toBe(-30);
  });

  it('accepts a Date object as well as an ISO string', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-24T12:00:00.000Z'));
    expect(getSecondsUntil(new Date('2026-06-24T12:01:00.000Z'))).toBe(60);
  });
});

describe('isInPast / isInFuture', () => {
  afterEach(() => vi.useRealTimers());

  it('classifies dates relative to now', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-06-24T12:00:00.000Z'));
    expect(isInPast('2026-06-24T11:00:00.000Z')).toBe(true);
    expect(isInFuture('2026-06-24T13:00:00.000Z')).toBe(true);
    expect(isInPast('2026-06-24T13:00:00.000Z')).toBe(false);
    expect(isInFuture('2026-06-24T11:00:00.000Z')).toBe(false);
  });
});
