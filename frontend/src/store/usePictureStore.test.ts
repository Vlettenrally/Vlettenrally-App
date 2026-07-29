import { describe, it, expect, beforeEach } from 'vitest';
import { usePictureStore } from './usePictureStore';
import type { Picture } from '../types/entities';

function picture(id: string): Picture {
  return {
    id,
    teamId: 'team-1',
    gameId: 'game-1',
    status: 'PENDING',
    contentType: 'image/jpeg',
    sizeBytes: 1024,
    viewUrl: `https://example.test/${id}`,
    createdAt: '2026-07-29T10:00:00Z',
    uploadedAt: '2026-07-29T10:01:00Z',
    moderatedAt: null,
    notes: null,
    teamName: 'De Watervlooien',
    teamDescription: null,
  };
}

describe('usePictureStore.appendModerationPictures', () => {
  beforeEach(() => {
    usePictureStore.getState().reset();
  });

  it('appends a batch after the pictures already loaded', () => {
    const { setModerationPictures, appendModerationPictures } = usePictureStore.getState();
    setModerationPictures([picture('a'), picture('b')]);

    appendModerationPictures([picture('c'), picture('d')]);

    expect(usePictureStore.getState().moderationPictures.map((p) => p.id)).toEqual([
      'a', 'b', 'c', 'd',
    ]);
  });

  it('skips ids already held, so a live upsert is not duplicated by the next batch', () => {
    const { setModerationPictures, upsertModerationPicture, appendModerationPictures } =
      usePictureStore.getState();
    setModerationPictures([picture('a')]);
    // Arrives over the websocket and is prepended...
    upsertModerationPicture(picture('live'));

    // ...then the next page returns it again alongside genuinely new photos.
    appendModerationPictures([picture('live'), picture('b')]);

    expect(usePictureStore.getState().moderationPictures.map((p) => p.id)).toEqual([
      'live', 'a', 'b',
    ]);
  });

  it('keeps the same array identity when a batch adds nothing', () => {
    const { setModerationPictures, appendModerationPictures } = usePictureStore.getState();
    setModerationPictures([picture('a')]);
    const before = usePictureStore.getState().moderationPictures;

    appendModerationPictures([picture('a')]);

    expect(usePictureStore.getState().moderationPictures).toBe(before);
  });
});
