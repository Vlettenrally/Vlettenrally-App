/**
 * Firebase Cloud Messaging Service
 * 
 * Handles push notification registration and foreground message handling.
 * Background messages are handled by the service worker.
 */

import { initializeApp, getApps, getApp } from 'firebase/app';
import { getMessaging, getToken, onMessage, type Messaging } from 'firebase/messaging';
import { config } from '../config';
import api from './api';

// Firebase configuration (matches service-worker.ts)
const firebaseConfig = {
  apiKey: "AIzaSyCOvcN9Ns5j3FiSBt0oXySR0n058PH8X-A",
  authDomain: "vlettenrally-dev.firebaseapp.com",
  projectId: "vlettenrally-dev",
  storageBucket: "vlettenrally-dev.firebaseapp.com",
  messagingSenderId: "85616821581",
  appId: "1:85616821581:web:8d39f0e4719f22a1182304",
};

let messaging: Messaging | null = null;
let messagingPromise: Promise<Messaging | null> | null = null;

// Track last registered token and timestamp to prevent duplicate registrations
let lastRegisteredToken: string | null = null;
let lastRegistrationTime: number = 0;
const REGISTRATION_COOLDOWN_MS = 5000; // Don't re-register the same token within 5 seconds

/**
 * Initialize Firebase app and messaging
 * Waits for service worker to be ready before initializing messaging
 * to prevent duplicate service worker registration
 */
async function getFirebaseMessaging(): Promise<Messaging | null> {
  if (messaging) {
    return messaging;
  }

  // Return existing promise if initialization is in progress
  if (messagingPromise) {
    return messagingPromise;
  }

  messagingPromise = (async () => {
    try {
      // Check if Firebase is supported
      if (!('serviceWorker' in navigator) || !('PushManager' in window)) {
        console.warn('[FCM] Push notifications not supported in this browser');
        return null;
      }

      // Wait for service worker to be ready before initializing messaging
      // This prevents Firebase from registering its own service worker
      await navigator.serviceWorker.ready;

      // Initialize or get existing Firebase app
      const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();
      messaging = getMessaging(app);
      return messaging;
    } catch (error) {
      console.error('[FCM] Failed to initialize Firebase messaging:', error);
      return null;
    }
  })();

  return messagingPromise;
}

/**
 * Request notification permission from the user
 * @returns Permission state: 'granted', 'denied', or 'default'
 */
export async function requestNotificationPermission(): Promise<NotificationPermission> {
  if (!('Notification' in window)) {
    console.warn('[FCM] Notifications not supported');
    return 'denied';
  }

  const permission = await Notification.requestPermission();
  if (config.debug) {
    console.log('[FCM] Notification permission:', permission);
  }
  return permission;
}

/**
 * Get the FCM registration token
 * This requires notification permission to be granted first
 * @returns The FCM token or null if unavailable
 */
export async function getFcmToken(): Promise<string | null> {
  const fcmMessaging = await getFirebaseMessaging();
  if (!fcmMessaging) {
    return null;
  }

  if (!config.firebaseVapidKey) {
    console.error('[FCM] VAPID key not configured. Set VITE_FIREBASE_VAPID_KEY in .env');
    return null;
  }

  try {
    // Service worker is already ready (getFirebaseMessaging waits for it)
    const registration = await navigator.serviceWorker.ready;
    
    const token = await getToken(fcmMessaging, {
      vapidKey: config.firebaseVapidKey,
      serviceWorkerRegistration: registration,
    });

    if (config.debug) {
      console.log('[FCM] Token retrieved:', token ? `${token.substring(0, 20)}...` : 'null');
    }

    return token;
  } catch (error) {
    console.error('[FCM] Failed to get token:', error);
    return null;
  }
}

/**
 * Register the FCM token with the backend
 * @param token The FCM token to register
 * @returns true if registration succeeded
 */
export async function registerTokenWithBackend(token: string): Promise<boolean> {
  // Check if this token was recently registered (prevents duplicate calls)
  const now = Date.now();
  if (lastRegisteredToken === token && (now - lastRegistrationTime) < REGISTRATION_COOLDOWN_MS) {
    if (config.debug) {
      console.log('[FCM] Token was recently registered, skipping duplicate registration');
    }
    return true;
  }

  try {
    const response = await api.registerFcmToken(token);
    if (response.error) {
      console.error('[FCM] Failed to register token with backend:', response.error);
      return false;
    }
    
    // Update tracking variables after successful registration
    lastRegisteredToken = token;
    lastRegistrationTime = now;
    
    if (config.debug) {
      console.log('[FCM] Token registered with backend');
    }
    return true;
  } catch (error) {
    console.error('[FCM] Failed to register token with backend:', error);
    return false;
  }
}

/**
 * Set up foreground message handler
 * Called when a message is received while the app is in the foreground
 * @param callback Function to call when a message is received
 * @returns Unsubscribe function or null if messaging not available
 */
export async function onForegroundMessage(
  callback: (payload: { notification?: { title?: string; body?: string }; data?: Record<string, string> }) => void
): Promise<(() => void) | null> {
  const fcmMessaging = await getFirebaseMessaging();
  if (!fcmMessaging) {
    return null;
  }

  return onMessage(fcmMessaging, (payload) => {
    if (config.debug) {
      console.log('[FCM] Foreground message received:', payload);
    }
    callback(payload);
  });
}

/**
 * Initialize push notifications
 * Requests permission, gets token, and registers with backend
 * NOTE: This must be called from a user gesture (click handler) due to browser restrictions
 * @returns true if successfully initialized
 */
export async function initializePushNotifications(): Promise<boolean> {
  // Check if already denied
  if (Notification.permission === 'denied') {
    console.warn('[FCM] Notification permission was denied');
    return false;
  }

  // Request permission if not granted (must be from user gesture)
  if (Notification.permission !== 'granted') {
    const permission = await requestNotificationPermission();
    if (permission !== 'granted') {
      console.warn('[FCM] Notification permission not granted');
      return false;
    }
  }

  // Get FCM token
  const token = await getFcmToken();
  if (!token) {
    console.error('[FCM] Failed to get FCM token');
    return false;
  }

  // Register with backend
  const registered = await registerTokenWithBackend(token);
  if (!registered) {
    console.error('[FCM] Failed to register token with backend');
    return false;
  }

  if (config.debug) {
    console.log('[FCM] Push notifications initialized successfully');
  }
  return true;
}

/**
 * Register FCM token if permission is already granted
 * Safe to call on app load (no user gesture required)
 * @returns true if token was registered, false if permission not granted or registration failed
 */
export async function registerIfPermissionGranted(): Promise<boolean> {
  // Only proceed if permission is already granted
  if (Notification.permission !== 'granted') {
    if (config.debug) {
      console.log('[FCM] Permission not granted, skipping automatic registration');
    }
    return false;
  }

  // Get FCM token
  const token = await getFcmToken();
  if (!token) {
    console.error('[FCM] Failed to get FCM token');
    return false;
  }

  // Register with backend
  const registered = await registerTokenWithBackend(token);
  if (!registered) {
    console.error('[FCM] Failed to register token with backend');
    return false;
  }

  if (config.debug) {
    console.log('[FCM] FCM token registered (permission was already granted)');
  }
  return true;
}

/**
 * Get current notification permission state
 */
export function getNotificationPermission(): NotificationPermission {
  if (!('Notification' in window)) {
    return 'denied';
  }
  return Notification.permission;
}

export default {
  requestNotificationPermission,
  getFcmToken,
  registerTokenWithBackend,
  onForegroundMessage,
  initializePushNotifications,
  registerIfPermissionGranted,
  getNotificationPermission,
};
