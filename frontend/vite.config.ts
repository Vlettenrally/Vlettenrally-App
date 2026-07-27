import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'service-worker.ts',
      includeAssets: ['favicon.ico', 'apple-touch-icon.png'],
      manifest: {
        name: 'Vlettenrally App',
        short_name: 'Vlettenrally',
        description: 'Vlettenrally application',
        theme_color: '#165656',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          {
            src: '/icon-192x192.png',
            sizes: '192x192',
            type: 'image/png',
            purpose: 'any',
          },
          {
            src: '/icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any',
          },
          {
            // Has the 80% safe zone Android needs, so the launcher shape
            // crops the icon instead of letterboxing it.
            src: '/icon-512x512-maskable.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      injectManifest: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
      },
      devOptions: {
        enabled: true,
        type: 'module',
      },
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: (id) => {
          if (!id.includes('node_modules')) {
            return
          }

          if (
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('/react-router-dom/')
          ) {
            return 'vendor-react'
          }

          if (
            id.includes('/@mui/material/') ||
            id.includes('/@mui/icons-material/') ||
            id.includes('/@emotion/react/') ||
            id.includes('/@emotion/styled/')
          ) {
            return 'vendor-mui'
          }

          if (id.includes('/@mui/x-data-grid/')) {
            return 'vendor-mui-datagrid'
          }

          if (id.includes('/firebase/')) {
            return 'vendor-firebase'
          }

          if (
            id.includes('/maplibre-gl/') ||
            id.includes('/@turf/circle/') ||
            id.includes('/@turf/helpers/')
          ) {
            return 'vendor-maplibre'
          }

          if (id.includes('/date-fns/')) {
            return 'vendor-date-fns'
          }

          if (id.includes('/qrcode/')) {
            return 'vendor-qrcode'
          }
        },
      },
    },
  },
})
