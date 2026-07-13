import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import i18n from '../../i18n';
import { useTeamStore } from '../../store/useTeamStore';
import { useGameStore } from '../../store/useGameStore';
import type { RankingEntry } from '../../types/entities';
import Rankings from './Rankings';

/**
 * The FAB is the only way to find yourself in a field of dozens of teams, so a
 * regression here silently turns the final standings back into a manual hunt.
 */

vi.mock('../../services/api', () => ({
  default: { getRankings: vi.fn() },
}));

const api = (await import('../../services/api')).default as unknown as {
  getRankings: ReturnType<typeof vi.fn>;
};

const GAME_ID = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
const OWN_TEAM_ID = '11111111-1111-1111-1111-111111111111';

const RANKINGS: RankingEntry[] = [
  { position: 1, teamId: 'aaaa', teamName: 'Windkracht Tien', points: 90 },
  { position: 2, teamId: 'bbbb', teamName: 'De Zeehonden', points: 80 },
  { position: 3, teamId: OWN_TEAM_ID, teamName: 'De Watervlooien', points: 70 },
];

/** Set by the IntersectionObserver stub; drives whether the own-team row is on screen. */
let emitIntersection: (isIntersecting: boolean) => void;

// No global setupFiles in vitest.config, so RTL's auto-cleanup is not registered.
afterEach(cleanup);

beforeEach(async () => {
  await i18n.changeLanguage('nl');

  vi.stubGlobal(
    'IntersectionObserver',
    class {
      constructor(cb: IntersectionObserverCallback) {
        emitIntersection = (isIntersecting) =>
          act(() => {
            cb([{ isIntersecting } as IntersectionObserverEntry], this as never);
          });
      }
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );

  // jsdom implements neither of these.
  Element.prototype.scrollIntoView = vi.fn();
  window.scrollTo = vi.fn();
  window.scrollY = 0;

  api.getRankings.mockResolvedValue({ data: RANKINGS });

  useGameStore.setState({ game: { id: GAME_ID, name: 'Vlettenrally' } as never });
  useTeamStore.setState({ team: { id: OWN_TEAM_ID } as never, rankings: [] });
});

/** Renders the page and waits for the fetched standings to land. */
async function renderRankings() {
  render(<Rankings />);
  await screen.findByText('De Watervlooien');
}

function scrollTo(y: number) {
  window.scrollY = y;
  act(() => {
    window.dispatchEvent(new Event('scroll'));
  });
}

const goToMyTeam = () => i18n.t('participant.rankings.goToMyTeam');
const backToTop = () => i18n.t('participant.rankings.backToTop');

describe('Rankings floating action button', () => {
  it('stays hidden at the top of the page while your row is on screen', async () => {
    await renderRankings();
    emitIntersection(true);

    expect(screen.queryByRole('button')).toBeNull();
  });

  it('offers "go to my team" once your row scrolls out of view', async () => {
    await renderRankings();
    emitIntersection(false);

    fireEvent.click(screen.getByRole('button', { name: goToMyTeam() }));

    expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({
      behavior: 'smooth',
      block: 'center',
    });
  });

  it('still offers "go to my team" when scrolled down, as your row can be above the viewport', async () => {
    await renderRankings();
    scrollTo(800);
    emitIntersection(false);

    expect(screen.getByRole('button', { name: goToMyTeam() })).toBeTruthy();
  });

  it('flips to "back to top" once you are scrolled down with your row on screen', async () => {
    await renderRankings();
    scrollTo(800);
    emitIntersection(true);

    fireEvent.click(screen.getByRole('button', { name: backToTop() }));

    expect(window.scrollTo).toHaveBeenCalledWith({ top: 0, behavior: 'smooth' });
  });

  it('only ever offers "back to top" when you have no row in the standings', async () => {
    useTeamStore.setState({ team: null });
    await renderRankings();

    // No own row means the observer never fires; scrolling alone must still let you back up.
    scrollTo(800);

    expect(screen.getByRole('button', { name: backToTop() })).toBeTruthy();
    expect(screen.queryByRole('button', { name: goToMyTeam() })).toBeNull();
  });
});
