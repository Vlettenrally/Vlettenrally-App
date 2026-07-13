import { describe, it, expect, afterEach } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { InviteCodeRedirect } from '../../App';

/**
 * Every scanned invite QR lands on /i/<CODE>, so a break here breaks the whole
 * "add another device" flow silently — the camera would just open a dead page.
 */

// No global setupFiles in vitest.config, so RTL's auto-cleanup is not registered.
afterEach(cleanup);

function CheckInProbe() {
  const location = useLocation();
  return <div data-testid="landed">{location.pathname + location.search}</div>;
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/i/:code" element={<InviteCodeRedirect />} />
        <Route path="/check-in" element={<CheckInProbe />} />
      </Routes>
    </MemoryRouter>,
  );
  return screen.getByTestId('landed').textContent;
}

describe('InviteCodeRedirect', () => {
  it('hands the code to the check-in flow as ?invite=', () => {
    expect(renderAt('/i/K7QM4X9BTD')).toBe('/check-in?invite=K7QM4X9BTD');
  });

  it('accepts the uppercase path the QR actually encodes', () => {
    // The QR encodes HTTPS://HOST/I/<CODE> (uppercase, for QR alphanumeric mode), so the
    // camera hands over an uppercase path. React Router matches it case-insensitively.
    expect(renderAt('/I/K7QM4X9BTD')).toBe('/check-in?invite=K7QM4X9BTD');
  });

  it('normalises a lowercase code to the canonical uppercase form', () => {
    expect(renderAt('/i/k7qm4x9btd')).toBe('/check-in?invite=K7QM4X9BTD');
  });
});
