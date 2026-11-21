const {test, expect} = require('@playwright/test');
const {profile} = require('../fixtures/dashboard.cjs');

async function mockSettings(page, canSend = true) {
    const state = {
        config: {enabled: true, canSend, recipientEmail: canSend ? 'owner@example.com' : null, deliveryDay: 'MONDAY', deliveryTime: '08:00:00', reminderEnabled: true, reminderDay: 'MONDAY', reminderTime: '08:00:00', timeZone: 'Europe/Madrid'},
        profile: {...profile, birthDate: '1980-01-01', planningWeekChanges: []}, profileWrites: [], effectiveDate: '2026-08-16',
        requests: [], sends: 0, failSave: false, pending: null
    };
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') return route.fulfill({json: {email: canSend ? 'owner@example.com' : 'other@example.com', displayName: 'Owner', authenticated: true}});
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
        if (path === '/api/profile') {
            if (request.method() === 'PUT') {
                const body = request.postDataJSON();
                state.profileWrites.push(body);
                if (body.firstDayOfPlanningWeek) {
                    state.profile.planningWeekChanges = body.expectedPlanningWeekEffectiveDate === null ? [] : [{effectiveDate: state.effectiveDate, firstDay: body.firstDayOfPlanningWeek}];
                }
            }
            return route.fulfill({json: state.profile});
        }
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

for (const width of [390, 1280]) {
    test(`planning week Settings previews every weekday and saves the effective range at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await page.clock.setFixedTime(new Date('2026-08-12T12:00:00Z'));
        const state = await mockSettings(page);
        const choice = page.getByRole('combobox', {name: 'First day of planning week'});
        const settings = page.locator('.p-panel').filter({has: choice});
        await expect(choice).toHaveText('Saturday');
        await choice.click();
        await expect(page.getByRole('option')).toHaveText(['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']);
        await page.getByRole('option', {name: 'Wednesday', exact: true}).click();
        await expect(settings).toContainText('Wednesday–Tuesday from 2026-08-19 through 2026-08-25');
        await choice.click();
        await page.getByRole('option', {name: 'Sunday', exact: true}).click();
        await expect(settings).toContainText('Short transition · Sat 15 Aug 2026 – Sat 15 Aug 2026');
        await expect(settings).toContainText('Sunday–Saturday from 2026-08-16 through 2026-08-22');
        expect(state.profileWrites).toHaveLength(0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await choice.locator('../..').screenshot({path: testInfo.outputPath(`planning-week-settings-${width}.png`)});
        await settings.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(page.getByText('Settings updated', {exact: true})).toBeVisible();
        expect(state.profileWrites[0]).toMatchObject({firstDayOfPlanningWeek: 'SUNDAY', expectedPlanningWeekEffectiveDate: '2026-08-16'});
        expect(state.requests).toHaveLength(0);
        await page.goto('/');
        await expect(choice).toHaveText('Sunday');
        await expect(settings).toContainText('Sunday–Saturday from 2026-08-16 through 2026-08-22');
        await expect(page.getByRole('combobox', {name: 'Delivery day', exact: true})).toHaveText('Monday');
        await choice.click();
        await page.getByRole('option', {name: 'Saturday', exact: true}).click();
        await settings.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(choice).toHaveText('Saturday');
        await expect.poll(() => state.profileWrites.length).toBe(2);
        expect(state.profileWrites[1]).toMatchObject({firstDayOfPlanningWeek: 'SATURDAY', expectedPlanningWeekEffectiveDate: null});
    });
}

test('planning week preview crosses Madrid midnight, resumes and rechecks the date before saving', async ({page}) => {
    await page.clock.install({time: new Date('2026-12-31T22:58:00Z')});
    const state = await mockSettings(page);
    const choice = page.getByRole('combobox', {name: 'First day of planning week'});
    const settings = page.locator('.p-panel').filter({has: choice});
    await choice.click();
    await page.getByRole('option', {name: 'Friday', exact: true}).click();
    await expect(settings).toContainText('Friday–Thursday from 2027-01-01 through 2027-01-07');

    await page.clock.fastForward(120000);
    await expect(settings).toContainText('Friday–Thursday from 2027-01-08 through 2027-01-14');
    await page.clock.setSystemTime(new Date('2027-01-08T09:00:00Z'));
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    await expect(settings).toContainText('Friday–Thursday from 2027-01-15 through 2027-01-21');

    await page.clock.setSystemTime(new Date('2027-01-15T09:00:00Z'));
    state.effectiveDate = '2027-01-22';
    const save = settings.getByRole('button', {name: 'Save', exact: true});
    await save.click();
    await expect(page.getByText('Planning dates updated', {exact: true})).toBeVisible();
    await expect(settings).toContainText('Friday–Thursday from 2027-01-22 through 2027-01-28');
    expect(state.profileWrites).toHaveLength(0);
    await save.click();
    await expect(page.getByText('Settings updated', {exact: true})).toBeVisible();
    expect(state.profileWrites.map(write => write.firstDayOfPlanningWeek)).toEqual(['FRIDAY']);
});

test('planning week conflict reloads history and preserves drafts for a reviewed retry', async ({page}) => {
    await page.clock.install({time: new Date('2026-12-31T22:58:00Z')});
    const state = await mockSettings(page);
    const choice = page.getByRole('combobox', {name: 'First day of planning week'});
    const settings = page.locator('.p-panel').filter({has: choice});
    const limit = settings.getByRole('spinbutton', {name: 'In-shape body-fat limit (%)', exact: true});
    await limit.fill('18');
    await limit.press('Tab');
    await choice.click();
    await page.getByRole('option', {name: 'Friday', exact: true}).click();
    await expect(settings).toContainText('Friday–Thursday from 2027-01-01 through 2027-01-07');

    let release;
    const pending = new Promise(resolve => { release = resolve; });
    await page.route('**/api/profile', async route => {
        const request = route.request();
        if (request.method() !== 'PUT' || state.profileWrites.length) return route.fallback();
        state.profileWrites.push(request.postDataJSON());
        await pending;
        return route.fulfill({status: 409, json: {message: 'Planning week effective date changed.'}});
    });
    const save = settings.getByRole('button', {name: 'Save', exact: true});
    await save.click();
    await expect(settings.getByRole('button', {name: 'Saving…', exact: true})).toBeDisabled();
    await expect.poll(() => state.profileWrites.length).toBe(1);
    expect(state.profileWrites[0]).toMatchObject({expectedPlanningWeekEffectiveDate: '2027-01-01', firstDayOfPlanningWeek: 'FRIDAY', inShapeBodyFatLimit: 18});
    await page.clock.setSystemTime(new Date('2026-12-31T23:01:00Z'));
    state.profile.planningWeekChanges = [{effectiveDate: '2026-12-30', firstDay: 'WEDNESDAY'}];
    state.effectiveDate = '2027-01-08';
    release();

    await expect(page.getByText('Planning dates updated', {exact: true})).toBeVisible();
    await expect(page.getByText('Progress summary: Wednesday–Tuesday.', {exact: true})).toBeVisible();
    await expect(choice).toHaveText('Friday');
    await expect(limit).toHaveValue('18.00%');
    await expect(settings).toContainText('Friday–Thursday from 2027-01-08 through 2027-01-14');
    expect(state.profileWrites).toHaveLength(1);
    await save.click();
    await expect(page.getByText('Settings updated', {exact: true})).toBeVisible();
    expect(state.profileWrites[1]).toMatchObject({expectedPlanningWeekEffectiveDate: '2027-01-08', firstDayOfPlanningWeek: 'FRIDAY', inShapeBodyFatLimit: 18});
});
