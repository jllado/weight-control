const {test, expect} = require('@playwright/test');
const {profile, nutrientTargetsForDate} = require('../fixtures/dashboard.cjs');

async function openSettings(page, setting) {
    const state = {setting, writes: []};
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/sleeps/heart-rate-limit') {
            if (route.request().method() === 'PUT') {
                const body = route.request().postDataJSON();
                state.writes.push(body);
                state.setting = {effectiveFrom: '2025-04-20', bpm: body.bpm};
            }
            return route.fulfill({json: state.setting});
        }
        const responses = {
            '/api/auth/me': {email: 'sleeper@example.com', displayName: 'Sleeper', authenticated: true},
            '/api/urge-pauses': {pause: null, serverNow: new Date().toISOString()},
            '/api/profile': profile,
            '/api/push/config': {enabled: false, publicKey: null, timeZone: 'Europe/Madrid'},
            '/api/push/reminder-settings': {morningTime: '07:30:00', middayTime: '13:30:00', eveningTime: '20:30:00', weightTime: '05:00:00', bloodPressureTime: '05:15:00', weightDay: 'SATURDAY', bloodPressureDay: 'SATURDAY'},
            '/api/weekly-summary/config': {enabled: false, canSend: false, deliveryDay: 'MONDAY', deliveryTime: '08:00:00', reminderEnabled: false, reminderDay: 'MONDAY', reminderTime: '08:00:00', timeZone: 'Europe/Madrid'},
            '/api/nutrition/targets': nutrientTargetsForDate('2025-04-20')
        };
        return route.fulfill({json: responses[path] || []});
    });
    await page.addInitScript(() => window.history.replaceState({}, '', '/settings'));
    await page.goto('/');
    return state;
}

test('personal alert threshold can be edited, saved and reloaded on mobile and desktop', async ({page}, testInfo) => {
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await openSettings(page, {effectiveFrom: '2025-04-12', bpm: 58.375});
    const input = page.getByRole('spinbutton', {name: 'Personal alert threshold (bpm)'});
    const save = page.getByRole('button', {name: 'Save threshold'});
    const panel = page.locator('.p-panel').filter({has: input});
    await expect(input).toHaveValue('58.375');
    await expect(save).toBeDisabled();
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 900});
        await panel.scrollIntoViewIfNeeded();
        expect(await panel.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await panel.screenshot({path: testInfo.outputPath(`heart-rate-settings-${width}.png`)});
    }
    await input.focus();
    await input.press('ControlOrMeta+A');
    await input.pressSequentially('62.25');
    await expect(save).toBeEnabled();
    await save.click();
    await expect(page.getByText('Current threshold: 62.25 bpm since 2025-04-20.')).toBeVisible();
    expect(state.writes).toEqual([{bpm: 62.25}]);
    await page.goto('/');
    await expect(input).toHaveValue('62.25');
    await expect(save).toBeDisabled();
    expect(errors).toEqual([]);
});

test('Settings explains when a personal alert threshold becomes available', async ({page}) => {
    await openSettings(page, {effectiveFrom: null, bpm: null});
    await expect(page.getByText(/Your personal alert threshold will be set 5 bpm above the median/)).toBeVisible();
    await expect(page.getByRole('button', {name: 'Save threshold'})).toHaveCount(0);
});
