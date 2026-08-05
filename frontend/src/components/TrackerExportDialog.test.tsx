/**
 * Tests for the tracker CSV export dialog: the column spec sent to the backend
 * must reflect what the user unchecked, renamed and reordered — the order of the
 * columns array is the order of the CSV columns.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en' },
  }),
}));

const exportTrackers = vi.hoisted(() => vi.fn());
vi.mock('../services/gameService', () => ({
  gameService: { exportTrackers },
}));

import TrackerExportDialog from './TrackerExportDialog';

const gameId = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';

// jsdom has no object URLs and no real downloads; the click is a no-op on a
// detached anchor, so only the service call is asserted.
function stubDownload() {
  URL.createObjectURL = vi.fn(() => 'blob:mock');
  URL.revokeObjectURL = vi.fn();
}

function renderDialog() {
  stubDownload();
  exportTrackers.mockResolvedValue(new Blob(['csv'], { type: 'text/csv' }));
  return render(<TrackerExportDialog open onClose={vi.fn()} gameId={gameId} />);
}

const clickExport = () =>
  fireEvent.click(screen.getByRole('button', { name: 'admin.teams.exportDialog.export' }));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('TrackerExportDialog', () => {
  it('exports the provider default columns when nothing is changed', async () => {
    renderDialog();

    clickExport();

    await waitFor(() =>
      expect(exportTrackers).toHaveBeenCalledWith(gameId, {
        columns: [
          { field: 'trackerId', header: 'trackerId' },
          { field: 'name', header: 'boatNumber' },
          { field: 'description', header: 'scoutingGroup' },
        ],
        includeUnassigned: false,
      }),
    );
  });

  it('drops unchecked fields and sends renamed headers', async () => {
    renderDialog();

    fireEvent.click(screen.getByLabelText('admin.teams.exportDialog.fieldDescription'));
    const boatNumber = screen.getByDisplayValue('boatNumber');
    fireEvent.change(boatNumber, { target: { value: 'Boot, nr' } });

    clickExport();

    await waitFor(() =>
      expect(exportTrackers).toHaveBeenCalledWith(gameId, {
        columns: [
          { field: 'trackerId', header: 'trackerId' },
          { field: 'name', header: 'Boot, nr' },
        ],
        includeUnassigned: false,
      }),
    );
  });

  it('sends the columns in the order the user arranged them', async () => {
    renderDialog();

    // Second row is `name`; move it above `trackerId`.
    fireEvent.click(screen.getAllByLabelText('admin.teams.exportDialog.moveUp')[1]);
    fireEvent.click(screen.getByLabelText('admin.teams.exportDialog.includeUnassigned'));

    clickExport();

    await waitFor(() =>
      expect(exportTrackers).toHaveBeenCalledWith(gameId, {
        columns: [
          { field: 'name', header: 'boatNumber' },
          { field: 'trackerId', header: 'trackerId' },
          { field: 'description', header: 'scoutingGroup' },
        ],
        includeUnassigned: true,
      }),
    );
  });

  it('blocks export when every column is unchecked', () => {
    renderDialog();

    fireEvent.click(screen.getByLabelText('admin.teams.exportDialog.fieldTrackerId'));
    fireEvent.click(screen.getByLabelText('admin.teams.exportDialog.fieldName'));
    fireEvent.click(screen.getByLabelText('admin.teams.exportDialog.fieldDescription'));

    const exportButton = screen.getByRole('button', {
      name: 'admin.teams.exportDialog.export',
    }) as HTMLButtonElement;
    expect(exportButton.disabled).toBe(true);
  });
});
