const {defineConfig, devices} = require('@playwright/test');

module.exports = defineConfig({
    testDir: './tests/e2e',
    timeout: 30000,
    use: {
        baseURL: 'http://127.0.0.1:4173',
        serviceWorkers: 'block',
        ...devices['Pixel 7']
    },
    webServer: {
        command: 'http-server dist -p 4173 -c-1',
        url: 'http://127.0.0.1:4173',
        reuseExistingServer: !process.env.CI
    }
});
