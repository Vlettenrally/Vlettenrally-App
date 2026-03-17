/**
 * Application Configuration
 * 
 * Configuration is loaded from Vite environment variables (VITE_*) at build time.
 * 
 * For local development, use .env.local file or environment variables:
 *   VITE_USE_MOCKS=true npm run dev
 * 
 * For production, .env.production is used during the build.
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

export const config = {
  /**
   * When true, the app uses mock data instead of calling the real API.
   * Useful for frontend development without needing the backend running.
   */
  useMocks: import.meta.env.VITE_USE_MOCKS === 'true',

  /**
   * API base URL for the backend
   */
  apiBaseUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1',

  /**
   * WebSocket URL for real-time updates
   */
  wsUrl: import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws',

  /**
   * Map style URL for MapLibre.
   * When set, used as the full style (e.g. OpenFreeMap in dev).
   * When empty, the local /map-style.json is used with self-hosted tiles.
   */
  mapStyleUrl: import.meta.env.VITE_MAP_STYLE_URL ?? '',

  /**
   * Tile URL template for self-hosted map tiles (used when mapStyleUrl is empty).
   * Must include {z}/{x}/{y} placeholders.
   */
  tileUrl: import.meta.env.VITE_TILE_URL ?? '/tiles/netherlands/{z}/{x}/{y}',

  /**
   * Enable verbose logging
   */
  debug: import.meta.env.DEV,

  /**
   * Firebase configuration
   */
  firebaseConfig: {
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? '',
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? '',
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? '',
    storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET ?? '',
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '',
    appId: import.meta.env.VITE_FIREBASE_APP_ID ?? '',
  },

  /**
   * Firebase VAPID key for web push notifications
   */
  firebaseVapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY ?? '',
};

export default config;
