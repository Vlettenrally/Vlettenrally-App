/**
 * Application Configuration
 * 
 * Configuration is loaded in the following priority order:
 * 1. Runtime config from window.__CONFIG__ (injected at container startup)
 * 2. Build-time Vite environment variables (VITE_*)
 * 3. Default fallback values
 * 
 * For local development, use .env file or environment variables:
 *   VITE_USE_MOCKS=true npm run dev
 * 
 * For production (Kubernetes), config is injected via ConfigMap -> /config.js
 */

/**
 * Firebase configuration interface
 */
export interface FirebaseConfig {
  apiKey: string;
  authDomain: string;
  projectId: string;
  storageBucket: string;
  messagingSenderId: string;
  appId: string;
}

/**
 * Runtime configuration interface (injected via /config.js)
 */
interface RuntimeConfig {
  apiBaseUrl?: string;
  wsUrl?: string;
  firebaseVapidKey?: string;
  useMocks?: boolean;
  firebaseConfig?: FirebaseConfig;
}

declare global {
  interface Window {
    __CONFIG__?: RuntimeConfig;
  }
}

/**
 * Default Firebase configuration (development)
 */
const defaultFirebaseConfig: FirebaseConfig = {
  apiKey: "AIzaSyCOvcN9Ns5j3FiSBt0oXySR0n058PH8X-A",
  authDomain: "vlettenrally-dev.firebaseapp.com",
  projectId: "vlettenrally-dev",
  storageBucket: "vlettenrally-dev.firebaseapp.com",
  messagingSenderId: "85616821581",
  appId: "1:85616821581:web:8d39f0e4719f22a1182304",
};

export const config = {
  /**
   * When true, the app uses mock data instead of calling the real API.
   * Useful for frontend development without needing the backend running.
   */
  useMocks: window.__CONFIG__?.useMocks ?? import.meta.env.VITE_USE_MOCKS === 'true',

  /**
   * API base URL for the backend
   */
  apiBaseUrl: window.__CONFIG__?.apiBaseUrl ?? import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1',

  /**
   * WebSocket URL for real-time updates
   */
  wsUrl: window.__CONFIG__?.wsUrl ?? import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws',

  /**
   * Enable verbose logging
   */
  debug: import.meta.env.DEV,

  /**
   * Firebase configuration
   */
  firebaseConfig: window.__CONFIG__?.firebaseConfig ?? defaultFirebaseConfig,

  /**
   * Firebase VAPID key for web push notifications
   */
  firebaseVapidKey: window.__CONFIG__?.firebaseVapidKey ?? import.meta.env.VITE_FIREBASE_VAPID_KEY ?? '',
};

export default config;
