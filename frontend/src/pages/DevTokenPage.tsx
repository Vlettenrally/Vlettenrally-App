import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { storage } from '../services/storage';

interface TokenResponse {
  token: string;
  expiresAt?: string;
}

interface MockUser {
  id: string;
  name: string;
  role: string;
  gameId: string;
}

export default function DevTokenPage() {
  const navigate = useNavigate();
  const [mockUsers, setMockUsers] = useState<MockUser[]>([]);
  const [selectedUser, setSelectedUser] = useState<string>('');
  const [token, setToken] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [saved, setSaved] = useState(false);

  const apiUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1';

  // Check current stored values using storage service
  const currentStoredTeam = storage.getTeamId();
  const currentStoredToken = storage.getAuthToken();
  const currentStoredGameId = storage.getGameId();
  const isCurrentlyCheckedIn = storage.isCheckedIn();

  // Fetch available mock users on mount
  useEffect(() => {
    const fetchMockUsers = async () => {
      try {
        const response = await fetch(`${apiUrl}/auth/mock-users`);
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        const users: MockUser[] = await response.json();
        setMockUsers(users);
        if (users.length > 0) {
          setSelectedUser(users[0].id);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to fetch mock users');
      } finally {
        setLoadingUsers(false);
      }
    };

    fetchMockUsers();
  }, [apiUrl]);

  const fetchToken = async () => {
    setLoading(true);
    setError(null);
    setToken(null);
    setSaved(false);

    try {
      const response = await fetch(`${apiUrl}/auth/mock-login`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ userId: selectedUser }),
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data: TokenResponse = await response.json();
      setToken(data.token);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch token');
    } finally {
      setLoading(false);
    }
  };

  const saveToStorage = () => {
    if (!token) return;

    const user = mockUsers.find((u) => u.id === selectedUser);
    if (!user) return;

    // Store using the storage service
    storage.setAuthToken(token);
    storage.setTeamId(user.id);
    storage.setGameId(user.gameId);
    storage.setRefreshToken(`mock_refresh_${user.id}_${Date.now()}`);
    storage.setTeamData({
      id: user.id,
      name: user.name,
      gameId: user.gameId,
      totalPoints: 0,
      description: '',
      checkedIn: true,
      rivals: [],
      createdAt: new Date().toISOString(),
    });

    setSaved(true);
  };

  const clearStorage = () => {
    storage.clearCache();
    storage.clearSession();
    setSaved(false);
    setToken(null);
  };

  const goToDashboard = () => {
    navigate('/dashboard');
  };

  const copyToClipboard = () => {
    if (token) {
      navigator.clipboard.writeText(token);
    }
  };

  const copyAuthHeader = () => {
    if (token) {
      navigator.clipboard.writeText(`Bearer ${token}`);
    }
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '800px', margin: '0 auto' }}>
      <h1>🔧 Dev Token Generator</h1>
      <p style={{ color: '#666' }}>
        Use this page to obtain authentication tokens for testing frontend-backend communication.
      </p>

      {/* Current Status */}
      <div style={{ marginTop: '2rem', padding: '1rem', background: isCurrentlyCheckedIn ? '#d4edda' : '#f8f9fa', borderRadius: '4px' }}>
        <h2 style={{ marginTop: 0 }}>Current Status</h2>
        <p><strong>Checked In:</strong> {isCurrentlyCheckedIn ? '✅ Yes' : '❌ No'}</p>
        {currentStoredTeam && <p><strong>Team ID:</strong> {currentStoredTeam}</p>}
        {currentStoredGameId && <p><strong>Game ID:</strong> {currentStoredGameId}</p>}
        {currentStoredToken && <p><strong>Token:</strong> {currentStoredToken.substring(0, 30)}...</p>}
        <div style={{ marginTop: '0.5rem', display: 'flex', gap: '0.5rem' }}>
          {isCurrentlyCheckedIn && (
            <>
              <button 
                onClick={goToDashboard}
                style={{ padding: '0.5rem 1rem', background: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
              >
                Go to Dashboard
              </button>
              <button 
                onClick={clearStorage} 
                style={{ padding: '0.5rem 1rem', background: '#dc3545', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
              >
                Clear Session
              </button>
            </>
          )}
        </div>
      </div>

      <div style={{ marginTop: '2rem' }}>
        <h2>API Configuration</h2>
        <code style={{ background: '#f4f4f4', padding: '0.5rem', display: 'block' }}>
          {apiUrl}
        </code>
      </div>

      <div style={{ marginTop: '2rem' }}>
        <h2>Select User</h2>
        {loadingUsers ? (
          <p>Loading teams...</p>
        ) : mockUsers.length === 0 ? (
          <p style={{ color: '#c00' }}>No teams available. Make sure mock data is loaded.</p>
        ) : (
          <select
            value={selectedUser}
            onChange={(e) => setSelectedUser(e.target.value)}
            style={{ padding: '0.5rem', fontSize: '1rem', width: '100%' }}
          >
            {mockUsers.map((user) => (
              <option key={user.id} value={user.id}>
                {user.name} ({user.role}) - {user.id}
              </option>
            ))}
          </select>
        )}
      </div>

      <div style={{ marginTop: '1rem', display: 'flex', gap: '0.5rem' }}>
        <button
          onClick={fetchToken}
          disabled={loading || loadingUsers || mockUsers.length === 0}
          style={{
            padding: '0.75rem 1.5rem',
            fontSize: '1rem',
            cursor: loading || loadingUsers || mockUsers.length === 0 ? 'not-allowed' : 'pointer',
            background: '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px',
          }}
        >
          {loading ? 'Fetching...' : 'Get Token'}
        </button>
      </div>

      {error && (
        <div style={{ marginTop: '1rem', padding: '1rem', background: '#fee', color: '#c00', borderRadius: '4px' }}>
          <strong>Error:</strong> {error}
        </div>
      )}

      {token && (
        <div style={{ marginTop: '2rem' }}>
          <h2>Token</h2>
          <textarea
            readOnly
            value={token}
            style={{
              width: '100%',
              height: '100px',
              fontFamily: 'monospace',
              fontSize: '0.875rem',
              padding: '0.5rem',
            }}
          />
          <div style={{ marginTop: '0.5rem', display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button onClick={copyToClipboard} style={{ padding: '0.5rem 1rem' }}>
              Copy Token
            </button>
            <button onClick={copyAuthHeader} style={{ padding: '0.5rem 1rem' }}>
              Copy as "Bearer ..."
            </button>
            <button 
              onClick={saveToStorage} 
              style={{ 
                padding: '0.5rem 1rem', 
                background: saved ? '#28a745' : '#ffc107', 
                border: 'none', 
                borderRadius: '4px',
                cursor: 'pointer',
                fontWeight: 'bold'
              }}
            >
              {saved ? '✅ Saved to App' : '💾 Save & Check In'}
            </button>
          </div>

          {saved && (
            <div style={{ marginTop: '1rem', padding: '1rem', background: '#d4edda', borderRadius: '4px' }}>
              <strong>✅ Session saved!</strong> The app should now recognize you as checked in.
              <button 
                onClick={goToDashboard}
                style={{ marginLeft: '1rem', padding: '0.5rem 1rem', background: '#007bff', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
              >
                Go to Dashboard →
              </button>
            </div>
          )}

          <h3 style={{ marginTop: '1rem' }}>Usage in fetch:</h3>
          <pre style={{ background: '#f4f4f4', padding: '1rem', overflow: 'auto' }}>
{`fetch('${apiUrl}/teams/me', {
  headers: {
    'Authorization': 'Bearer ${token.substring(0, 20)}...',
  },
})`}
          </pre>
        </div>
      )}

      <div style={{ marginTop: '3rem', padding: '1rem', background: '#fff3cd', borderRadius: '4px' }}>
        <strong>⚠️ Development Only</strong>
        <p style={{ margin: '0.5rem 0 0' }}>
          This page should only be available in development mode. Make sure to remove or protect it in production.
        </p>
      </div>
    </div>
  );
}
