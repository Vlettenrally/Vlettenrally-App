import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { EOL } from 'node:os'
import { fileURLToPath } from 'node:url'
import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import license, { type Dependency } from 'rollup-plugin-license'

const outDir = fileURLToPath(new URL('./dist', import.meta.url))

// The app and the service worker are separate Rollup builds, each with its own
// license plugin instance. Both feed this map, and every write emits the union,
// so THIRD-PARTY-LICENSES.txt lists everything regardless of which build ends last.
const bundledDependencies = new Map<string, Dependency>()

function thirdPartyLicenses() {
  return license({
    thirdParty: {
      includePrivate: false,
      output: (dependencies) => {
        for (const dependency of dependencies) {
          bundledDependencies.set(`${dependency.name}@${dependency.version}`, dependency)
        }
        const sorted = [...bundledDependencies.values()].sort((a, b) =>
          (a.name ?? '').localeCompare(b.name ?? ''),
        )
        mkdirSync(outDir, { recursive: true })
        writeFileSync(
          `${outDir}/THIRD-PARTY-LICENSES.txt`,
          sorted.map((dependency) => dependency.text()).join(`${EOL}${EOL}---${EOL}${EOL}`),
        )
      },
    },
  })
}

// The fonts are imported as CSS. rollup-plugin-license only counts modules that
// render JavaScript, so it misses some of them (Roboto, for one); their OFL texts
// are copied here explicitly.
function fontLicenses(): Plugin {
  const fonts = ['poppins', 'roboto']
  return {
    name: 'font-licenses',
    apply: 'build',
    generateBundle() {
      for (const font of fonts) {
        this.emitFile({
          type: 'asset',
          fileName: `licenses/${font}-OFL.txt`,
          source: readFileSync(
            fileURLToPath(new URL(`./node_modules/@fontsource/${font}/LICENSE`, import.meta.url)),
          ),
        })
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    { ...(thirdPartyLicenses() as Plugin), apply: 'build' },
    fontLicenses(),
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
        buildPlugins: {
          rollup: [thirdPartyLicenses()],
        },
      },
      devOptions: {
        enabled: true,
        type: 'module',
      },
    }),
  ],
  // maplibre-gl v6 loads its worker with `new Worker(url, { type: 'module' })`,
  // so the chunk Vite emits for it has to be a real ES module.
  worker: {
    format: 'es',
  },
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
