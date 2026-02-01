import { createBrowserRouter } from 'react-router-dom';
import { type ComponentType } from 'react';
import {
  CheckIn,
  ParticipantDashboard,
  ParticipantMessages,
  Rankings,
  Rivals,
  AdminDashboard,
  Games,
  Teams,
  AdminMessages,
  Automations,
  Analytics,
} from './pages';

// Only import Dev page in development builds
let Dev: ComponentType | null = null;
if (import.meta.env.DEV) {
  const devModule = await import('./pages/shared/Dev');
  Dev = devModule.default;
}

// Participant routes
const participantRoutes = [
  { path: '/', element: <ParticipantDashboard /> },
  { path: '/checkin', element: <CheckIn /> },
  { path: '/messages', element: <ParticipantMessages /> },
  { path: '/rankings', element: <Rankings /> },
  { path: '/rivals', element: <Rivals /> },
];

// Admin routes
const adminRoutes = [
  { path: '/admin', element: <AdminDashboard /> },
  { path: '/admin/games', element: <Games /> },
  { path: '/admin/teams', element: <Teams /> },
  { path: '/admin/messages', element: <AdminMessages /> },
  { path: '/admin/automations', element: <Automations /> },
  { path: '/admin/analytics', element: <Analytics /> },
];

// Dev routes (only in development builds)
const devRoutes = Dev ? [{ path: '/dev', element: <Dev /> }] : [];

export const router = createBrowserRouter([
  ...participantRoutes,
  ...adminRoutes,
  ...devRoutes,
]);
