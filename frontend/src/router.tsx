import { createBrowserRouter } from 'react-router-dom';
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
  Dev,
} from './pages';

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

// Shared routes
const sharedRoutes = [
  { path: '/dev', element: <Dev /> },
];

export const router = createBrowserRouter([
  ...participantRoutes,
  ...adminRoutes,
  ...sharedRoutes,
]);
