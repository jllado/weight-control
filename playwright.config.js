const {defineConfig, devices} = require('@playwright/test');
const port = process.env.WEIGHT_CONTROL_E2E_PORT || 4173;

module.exports = defineConfig({
    testDir: './tests/e2e',
    timeout: 30000,
    use: {
        baseURL: `http://127.0.0.1:${port}`,
        serviceWorkers: 'block',
        ...devices['Pixel 7']
    },
    webServer: {
        command: `http-server dist -p ${port} -c-1`,
        url: `http://127.0.0.1:${port}`,
        reuseExistingServer: !process.env.CI
    }
});
