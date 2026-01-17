/**
 * Main App Component with Routing
 */
import type { ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
import { useEffect } from 'react';
import { CheckInPage } from './pages/participant/Checkin';
import { DashboardPage } from './pages/participant/Dashboard';
import { MessagesPage } from './pages/MessagesPage';
import { AppLayout } from './components/AppLayout';
import { AdminLayout } from './components/AdminLayout';
import { storage } from './services/storage';
import { getRoleFromToken } from './services/auth';
import DevTokenPage from './pages/DevTokenPage';
import {
  AdminDashboard,
  Games,
  Teams,
  AdminMessages,
  Automations,
  Analytics,
  AdminLogin,
} from './pages/admin';

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#dc004e',
    },
  },
});

/**
 * Route guard for team/participant routes
 */
function TeamRoute({ children }: { children: ReactNode }) {
  const isCheckedIn = storage.isCheckedIn();
  
  if (!isCheckedIn) {
    return <Navigate to="/check-in" replace />;
  }
  
  return <>{children}</>;
}

/**
 * Route guard for admin routes
 */
function AdminRoute({ children }: { children: ReactNode }) {
  const token = storage.getAuthToken();
  const role = storage.getUserRole();
  
  // If no token, redirect to admin login
  if (!token) {
    return <Navigate to="/admin/login" replace />;
  }
  
  // Try to get role from token if not in storage
  if (!role) {
    const tokenRole = getRoleFromToken(token);
    if (tokenRole) {
      storage.setUserRole(tokenRole);
      if (tokenRole !== 'admin') {
        return <Navigate to="/dashboard" replace />;
      }
    } else {
      return <Navigate to="/admin/login" replace />;
    }
  } else if (role !== 'admin') {
    return <Navigate to="/dashboard" replace />;
  }
  
  return <>{children}</>;
}

/**
 * Root redirect based on authentication state and role
 */
function RootRedirect() {
  const navigate = useNavigate();
  
  useEffect(() => {
    const token = storage.getAuthToken();
    let role = storage.getUserRole();
    
    // Try to extract role from token if not stored
    if (token && !role) {
      role = getRoleFromToken(token);
      if (role) {
        storage.setUserRole(role);
      }
    }
    
    if (role === 'admin') {
      navigate('/admin', { replace: true });
    } else if (storage.isCheckedIn()) {
      navigate('/dashboard', { replace: true });
    } else {
      navigate('/check-in', { replace: true });
    }
  }, [navigate]);
  
  return null;
}

function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<RootRedirect />} />
          
          {/* Team/Participant routes */}
          <Route path="/check-in" element={<CheckInPage />} />
          <Route
            path="/dashboard"
            element={
              <TeamRoute>
                <AppLayout>
                  <DashboardPage />
                </AppLayout>
              </TeamRoute>
            }
          />
          <Route
            path="/messages"
            element={
              <TeamRoute>
                <AppLayout>
                  <MessagesPage />
                </AppLayout>
              </TeamRoute>
            }
          />
          
          {/* Admin routes */}
          <Route path="/admin/login" element={<AdminLogin />} />
          <Route
            path="/admin"
            element={
              <AdminRoute>
                <AdminLayout>
                  <AdminDashboard />
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/games"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Games />
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/teams"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Teams />
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/messages"
            element={
              <AdminRoute>
                <AdminLayout>
                  <AdminMessages />
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/automations"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Automations />
                </AdminLayout>
              </AdminRoute>
            }
          />
          <Route
            path="/admin/analytics"
            element={
              <AdminRoute>
                <AdminLayout>
                  <Analytics />
                </AdminLayout>
              </AdminRoute>
            }
          />
          
          {/* Dev routes */}
          <Route path="/dev/tokens" element={<DevTokenPage />} />
          
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  );
}

export default App;
