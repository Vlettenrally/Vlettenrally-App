/**
 * Application Configuration
 * 
 * Toggle mock mode by setting VITE_USE_MOCKS=true in your .env file
 * or by setting the environment variable when running the dev server:
 * 
 *   VITE_USE_MOCKS=true npm run dev
 */

export const config = {
  /**
   * When true, the app uses mock data instead of calling the real API.
   * Useful for frontend development without needing the backend running.
   */
  useMocks: import.meta.env.VITE_USE_MOCKS === 'true',

  /**
   * API base URL for the backend
   */
  apiBaseUrl: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1',

  /**
   * WebSocket URL for real-time updates
   */
  wsUrl: import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws',

  /**
   * Enable verbose logging
   */
  debug: import.meta.env.DEV,

  /**
   * Firebase VAPID key for web push notifications
   */
  firebaseVapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY || '',
};

export default config;
