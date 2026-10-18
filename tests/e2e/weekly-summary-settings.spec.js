const {test, expect} = require('@playwright/test');

async function mockSettings(page, canSend = true) {
    const state = {
        config: {enabled: true, canSend, recipientEmail: canSend ? 'owner@example.com' : null, deliveryDay: 'MONDAY', deliveryTime: '08:00:00', reminderEnabled: true, reminderDay: 'MONDAY', reminderTime: '08:00:00', timeZone: 'Europe/Madrid'},
        requests: [], sends: 0, failSave: false, pending: null
    };
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') return route.fulfill({json: {email: canSend ? 'owner@example.com' : 'other@example.com', displayName: 'Owner', authenticated: true}});
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
        if (path === '/api/profile') return route.fulfill({json: {}});
        if (path === '/api/push/config') return route.fulfill({json: {enabled: false, publicKey: null, timeZone: 'Europe/Madrid'}});
        if (path === '/api/push/reminder-settings') return route.fulfill({json: {morningTime: '07:30:00', middayTime: '13:30:00', eveningTime: '20:30:00', weightTime: '05:00:00', bloodPressureTime: '05:15:00', weightDay: 'SATURDAY', bloodPressureDay: 'SATURDAY', timeZone: 'Europe/Madrid'}});
        if (path === '/api/nutrition/targets') return route.fulfill({json: {
            overrides: {vitaminDMicrograms: null, omega3Milligrams: null, magnesiumMilligrams: null},
            targets: {vitaminD: {referenceValue: 15}, omega3: {referenceValue: null}, magnesium: {referenceValue: 350}}
        }});
        if (path === '/api/weekly-summary/config') {
            if (request.method() === 'PUT') {
                state.requests.push(request.postDataJSON());
                if (state.pending) await state.pending;
                if (state.failSave) return route.fulfill({status: 503, body: 'Schedule service unavailable'});
                state.config = {...state.config, ...request.postDataJSON()};
            }
            return route.fulfill({json: state.config});
        }
        if (path === '/api/weekly-summary/send') {
            state.sends++;
            return route.fulfill({status: 204});
        }
        return route.fulfill({json: []});
    });
    await page.addInitScript(() => window.history.replaceState({}, '', '/settings'));
    await page.goto('/');
    await expect(page.getByText('Progress summary: Saturday–Friday.', {exact: true})).toBeVisible();
    return state;
}

async function chooseTuesday0830(page) {
    const advanceMinutes = async (input, increments) => {
        await input.click();
        const panel = page.locator(`#${await input.getAttribute('aria-controls')}`);
        for (let minute = 0; minute < increments; minute++) await panel.getByRole('button', {name: 'Next Minute', exact: true}).click();
    };
    await page.getByRole('combobox', {name: 'Delivery day', exact: true}).click();
    await page.getByRole('option', {name: 'Tuesday', exact: true}).click();
    const deliveryTime = page.getByLabel('Delivery time', {exact: true});
    await advanceMinutes(deliveryTime, 6);
    await page.getByText('Progress summary: Saturday–Friday.', {exact: true}).click();
    await expect(deliveryTime).toHaveValue('08:30');
    await page.getByRole('combobox', {name: 'Reminder day', exact: true}).click();
    await page.getByRole('option', {name: 'Thursday', exact: true}).click();
    const reminderTime = page.getByLabel('Reminder time', {exact: true});
    await advanceMinutes(reminderTime, 3);
    await page.getByText('Progress summary: Saturday–Friday.', {exact: true}).click();
    await expect(reminderTime).toHaveValue('08:15');
}

test('weekly email schedule saves Tuesday 08:30 and reloads with readable controls', async ({page}, testInfo) => {
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const state = await mockSettings(page);
    await chooseTuesday0830(page);
    await page.getByText('Remind me to create the weekly summary', {exact: true}).click();
    await expect(page.getByRole('checkbox', {name: 'Remind me to create the weekly summary', exact: true})).not.toBeChecked();
    await expect(page.getByText('Emailed every Monday at 08:00 Europe/Madrid to owner@example.com.', {exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Save schedules', exact: true}).click();
    await expect(page.getByText('Weekly schedules saved', {exact: true})).toBeVisible();
    expect(state.requests).toEqual([{deliveryDay: 'TUESDAY', deliveryTime: '08:30', reminderEnabled: false, reminderDay: 'THURSDAY', reminderTime: '08:15'}]);
    expect(state.sends).toBe(0);
    await page.goto('/');
    await expect(page.getByRole('combobox', {name: 'Delivery day', exact: true})).toHaveText('Tuesday');
    await expect(page.getByLabel('Delivery time', {exact: true})).toHaveValue('08:30');
    await expect(page.getByRole('checkbox', {name: 'Remind me to create the weekly summary', exact: true})).not.toBeChecked();
    await expect(page.getByRole('combobox', {name: 'Reminder day', exact: true})).toHaveText('Thursday');
    await expect(page.getByLabel('Reminder time', {exact: true})).toHaveValue('08:15');
    await expect(page.getByText('Emailed every Tuesday at 08:30 Europe/Madrid to owner@example.com.', {exact: true})).toBeVisible();
    const panel = page.locator('.p-panel').filter({has: page.getByText('Progress summary: Saturday–Friday.', {exact: true})});
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await panel.scrollIntoViewIfNeeded();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    for (const name of ['Save schedules', 'Send weekly summary now']) {
            const button = page.getByRole('button', {name, exact: true});
            await expect(button).toBeVisible();
            expect(await button.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        }
        await panel.screenshot({path: testInfo.outputPath(`weekly-email-${width}.png`)});
    }
    expect(errors).toEqual([]);
    await page.getByRole('button', {name: 'Send weekly summary now', exact: true}).click();
    await expect(page.getByText('The weekly summary was sent.', {exact: true})).toBeVisible();
    expect(state.sends).toBe(1);
});

test('weekly email schedule locks pending fields and preserves draft and saved sentence on failure', async ({page}, testInfo) => {
    const state = await mockSettings(page);
    await chooseTuesday0830(page);
    let releaseSave;
    state.pending = new Promise(resolve => { releaseSave = resolve; });
    state.failSave = true;
    await page.getByRole('button', {name: 'Save schedules', exact: true}).click();
    const saving = page.getByRole('button', {name: 'Saving…', exact: true});
    await expect(saving).toBeDisabled();
    await expect(saving).toHaveAttribute('aria-busy', 'true');
    await expect(page.getByRole('combobox', {name: 'Delivery day', exact: true})).toBeDisabled();
    await expect(page.getByLabel('Delivery time', {exact: true})).toBeDisabled();
    await expect(page.getByRole('checkbox', {name: 'Remind me to create the weekly summary', exact: true})).toBeDisabled();
    await expect(page.getByRole('combobox', {name: 'Reminder day', exact: true})).toBeDisabled();
    await expect(page.getByLabel('Reminder time', {exact: true})).toBeDisabled();
    await expect(page.getByRole('button', {name: 'Send weekly summary now', exact: true})).toBeDisabled();
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        await saving.scrollIntoViewIfNeeded();
        await page.locator('.p-panel').filter({has: page.getByText('Progress summary: Saturday–Friday.', {exact: true})})
            .screenshot({path: testInfo.outputPath(`weekly-email-pending-${width}.png`)});
    }
    releaseSave();
    await expect(page.getByText('Schedule service unavailable', {exact: true})).toBeVisible();
    await expect(page.getByRole('combobox', {name: 'Delivery day', exact: true})).toHaveText('Tuesday');
    await expect(page.getByLabel('Delivery time', {exact: true})).toHaveValue('08:30');
    await expect(page.getByText('Emailed every Monday at 08:00 Europe/Madrid to owner@example.com.', {exact: true})).toBeVisible();
    state.failSave = false;
    state.pending = null;
    await page.getByRole('button', {name: 'Save schedules', exact: true}).click();
    await expect(page.getByText('Weekly schedules saved', {exact: true})).toBeVisible();
    expect(state.requests).toEqual([
        {deliveryDay: 'TUESDAY', deliveryTime: '08:30', reminderEnabled: true, reminderDay: 'THURSDAY', reminderTime: '08:15'},
        {deliveryDay: 'TUESDAY', deliveryTime: '08:30', reminderEnabled: true, reminderDay: 'THURSDAY', reminderTime: '08:15'}
    ]);
    expect(state.sends).toBe(0);
});

test('weekly email schedule keeps nonowners read-only and hides recipient', async ({page}) => {
    await mockSettings(page, false);
    await expect(page.getByRole('button', {name: 'Save schedules', exact: true})).toHaveCount(0);
    await expect(page.getByRole('combobox', {name: 'Delivery day', exact: true})).toHaveCount(0);
    await expect(page.getByRole('button', {name: 'Send weekly summary now', exact: true})).toHaveCount(0);
    await expect(page.getByText('owner@example.com', {exact: false})).toHaveCount(0);
    await expect(page.getByRole('link', {name: 'View weekly summaries', exact: true})).toBeVisible();
});
