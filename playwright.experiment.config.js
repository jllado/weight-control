const {defineConfig} = require('@playwright/test');
const baseline = require('./playwright.config');

// Used by concurrent release modes; standalone checks retain the baseline configuration.
module.exports = defineConfig(baseline, {
    fullyParallel: true,
    workers: 2,
    retries: 0
});
