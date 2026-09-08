const {defineConfig} = require('@playwright/test');
const baseline = require('./playwright.config');

// Opt-in only; preserve all baseline context, viewport, and server settings.
module.exports = defineConfig(baseline, {
    fullyParallel: true,
    workers: 2,
    retries: 0
});
