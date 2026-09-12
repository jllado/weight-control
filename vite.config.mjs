import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';
import {VitePWA} from 'vite-plugin-pwa';
import {fileURLToPath, URL} from 'node:url';

export default defineConfig({
  plugins: [vue(), VitePWA({
    filename: 'service-worker.js',
    manifestFilename: 'manifest.json',
    injectRegister: false,
    registerType: 'prompt',
    workbox: {
      cacheId: 'weight-control',
      importScripts: ['/push-service-worker.js'],
      navigateFallback: '/index.html',
      navigateFallbackDenylist: [/^\/api\//],
      globPatterns: ['**/*.{js,css,html,ico,png,svg,woff,woff2,ttf,eot,json,txt,xml}'],
      maximumFileSizeToCacheInBytes: 6 * 1024 * 1024,
      cleanupOutdatedCaches: true,
      clientsClaim: false,
      skipWaiting: false
    },
    manifest: {
      id: '/', name: 'Weight Control', short_name: 'WC', start_url: '/', scope: '/',
      display: 'standalone', background_color: '#ffffff', theme_color: '#0476F2',
      description: 'Track weight, blood pressure, habits, and routines.',
      shortcuts: [
        {name: 'Add Win', short_name: 'Win', description: 'Record a win for the selected dashboard date.', url: '/?decisionOutcome=WIN'},
        {name: 'Add Loss', short_name: 'Loss', description: 'Record a loss for the selected dashboard date.', url: '/?decisionOutcome=MISS'}
      ],
      icons: [
        {src: '/android-chrome-192x192.png', sizes: '192x192', type: 'image/png'},
        {src: '/android-chrome-512x512.png', sizes: '512x512', type: 'image/png'}
      ]
    }
  })],
  resolve: {alias: {'@': fileURLToPath(new URL('./src', import.meta.url))}},
  build: {target: ['chrome111', 'edge111', 'firefox114', 'safari16.4']},
  server: {proxy: {'/api': 'http://localhost:8081'}}
});
