const {defineConfig} = require('@playwright/test');
module.exports = defineConfig({
  testDir: './tests', timeout: 30000, workers: 1, retries: 0,
  outputDir: '../../tmp/ui-library-spike/test-results',
  use: {browserName: 'chromium', reducedMotion: 'reduce', serviceWorkers: 'block'},
  projects: ['reference', 'candidate'].map((name, index) => ({name, use: {baseURL: `http://127.0.0.1:${4381 + index}`}})),
  webServer: [
    {command: 'vite preview --mode reference --host 127.0.0.1 --port 4381 --strictPort', url: 'http://127.0.0.1:4381'},
    {command: 'vite preview --host 127.0.0.1 --port 4382 --strictPort', url: 'http://127.0.0.1:4382'}
  ]
});
