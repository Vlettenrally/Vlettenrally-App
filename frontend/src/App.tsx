/**
 * Main App Component with Routing
 */
import type { ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { ThemeProvider, createTheme, CssBaseline } from '@mui/material';
import { useEffect } from 'react';
import { CheckInPage } from './pages/CheckInPage';
import { DashboardPage } from './pages/DashboardPage';
import { MessagesPage } from './pages/MessagesPage';
import { AppLayout } from './components/AppLayout';
import { storage } from './services/storage';
import DevTokenPage from './pages/DevTokenPage';

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

function ProtectedRoute({ children }: { children: ReactNode }) {
  const isCheckedIn = storage.isCheckedIn();
  
  if (!isCheckedIn) {
    return <Navigate to="/check-in" replace />;
  }
  
  return <>{children}</>;
}

function RootRedirect() {
  const navigate = useNavigate();
  
  useEffect(() => {
    const isCheckedIn = storage.isCheckedIn();
    if (isCheckedIn) {
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
          <Route path="/check-in" element={<CheckInPage />} />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <AppLayout>
                  <DashboardPage />
                </AppLayout>
              </ProtectedRoute>
            }
          />
          <Route
            path="/messages"
            element={
              <ProtectedRoute>
                <AppLayout>
                  <MessagesPage />
                </AppLayout>
              </ProtectedRoute>
            }
          />
          <Route path="/dev/tokens" element={<DevTokenPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ThemeProvider>
  );
}

export default App;
