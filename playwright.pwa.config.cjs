const {defineConfig} = require('@playwright/test');
module.exports = defineConfig({
  testDir: './tests/pwa',
  outputDir: './tmp/pwa-test-results',
  timeout: 60000,
  workers: 1,
  retries: 0,
  use: {browserName: 'chromium', channel: 'chromium', serviceWorkers: 'allow', viewport: {width: 1280, height: 900}}
});
