/**
 * Tests for the admin Trackers page location button: it opens the tracker's last
 * position in a map popup instead of navigating away to the admin map. The page
 * is rendered without a router on purpose — reaching for useNavigate would throw.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';

// Passthrough translations; interpolated values are appended so the popup title
// can be asserted against the tracker it was opened for.
vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) =>
      opts?.trackerId ? `${key}:${opts.trackerId}` : key,
    i18n: { language: 'en' },
  }),
}));

// maplibre-gl does not run in jsdom; stand in for the map and expose the center
// it was mounted with, which is the thing the popup has to get right.
vi.mock('../../components/MapView', () => ({
  default: ({ center }: { center?: [number, number] }) => (
    <div data-testid="map-view" data-center={center ? `${center[0]},${center[1]}` : ''} />
  ),
}));

const getTrackers = vi.hoisted(() => vi.fn());
vi.mock('../../services/trackerService', () => ({
  trackerService: {
    getTrackers,
    addTrackers: vi.fn(),
    deleteTrackers: vi.fn(),
    setEnabled: vi.fn(),
  },
}));

import Trackers from './Trackers';
import type { Tracker } from '../../types/entities';

const located: Tracker = {
  trackerId: '860000000000001',
  enabled: true,
  batteryPercentage: 80,
  lastSeenAt: '2026-07-14T10:00:00Z',
  latitude: 52.1326,
  longitude: 5.2913,
  assignments: [],
};

const alsoLocated: Tracker = {
  trackerId: '860000000000002',
  enabled: true,
  batteryPercentage: 60,
  lastSeenAt: '2026-07-14T10:00:00Z',
  latitude: 53.2194,
  longitude: 6.5665,
  assignments: [],
};

// Never reported a fix, so it has no position to show.
const unlocated: Tracker = {
  trackerId: '860000000000003',
  enabled: true,
  batteryPercentage: null,
  lastSeenAt: null,
  latitude: null,
  longitude: null,
  assignments: [],
};

// The location button is the only icon button on a tracker's row.
function locationButton(trackerId: string): HTMLButtonElement {
  const row = screen.getByText(trackerId).closest('tr');
  if (!row) throw new Error(`no row for tracker ${trackerId}`);
  const button = row.querySelector('button');
  if (!button) throw new Error(`no location button for tracker ${trackerId}`);
  return button as HTMLButtonElement;
}

beforeEach(() => {
  getTrackers.mockResolvedValue([located, alsoLocated, unlocated]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.useRealTimers();
});

describe('admin Trackers page — location popup', () => {
  it('opens a map popup centred on the tracker, keeping the list on screen', async () => {
    render(<Trackers />);
    await waitFor(() => expect(screen.getByText(located.trackerId)).toBeTruthy());

    expect(screen.queryByTestId('map-view')).toBeNull();
    fireEvent.click(locationButton(located.trackerId));

    const map = await screen.findByTestId('map-view');
    expect(map.getAttribute('data-center')).toBe('5.2913,52.1326');
    expect(screen.getByText(`admin.trackers.mapDialogTitle:${located.trackerId}`)).toBeTruthy();
    // The popup sits over the page rather than replacing it.
    expect(screen.getByText('admin.trackers.title')).toBeTruthy();
  });

  it('recentres on the next tracker rather than reusing the first map', async () => {
    render(<Trackers />);
    await waitFor(() => expect(screen.getByText(located.trackerId)).toBeTruthy());

    fireEvent.click(locationButton(located.trackerId));
    expect((await screen.findByTestId('map-view')).getAttribute('data-center')).toBe('5.2913,52.1326');

    fireEvent.click(screen.getByText('components.mapPopup.close'));
    await waitFor(() => expect(screen.queryByTestId('map-view')).toBeNull());

    fireEvent.click(locationButton(alsoLocated.trackerId));
    expect((await screen.findByTestId('map-view')).getAttribute('data-center')).toBe('6.5665,53.2194');
  });

  it('disables the button for a tracker with no known position', async () => {
    render(<Trackers />);
    await waitFor(() => expect(screen.getByText(unlocated.trackerId)).toBeTruthy());

    expect(locationButton(unlocated.trackerId).disabled).toBe(true);
    expect(locationButton(located.trackerId).disabled).toBe(false);
  });

  it('keeps the popup open across a background refresh', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    render(<Trackers />);
    await waitFor(() => expect(screen.getByText(located.trackerId)).toBeTruthy());

    fireEvent.click(locationButton(located.trackerId));
    await screen.findByTestId('map-view');

    // The 30s poll replaces the tracker rows; the popup holds the row it opened with.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(31_000);
    });

    expect(getTrackers.mock.calls.length).toBeGreaterThan(1);
    expect(screen.getByTestId('map-view').getAttribute('data-center')).toBe('5.2913,52.1326');
  });
});
