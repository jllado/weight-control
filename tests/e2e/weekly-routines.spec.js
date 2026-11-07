const {test, expect} = require('@playwright/test');
const {mockBackPainHome} = require('../fixtures/back-pain-chart.cjs');
const {dashboard} = require('../fixtures/dashboard.cjs');

test.use({timezoneId: 'Europe/Madrid'});
const routine = (id, name, cadence, times = []) => ({id, name, cadence, startDate: '2026-07-04T00:00:00+02:00',
    lastTimeDate: times.at(-1) || null, types: ['WEIGHT'], times, reminders: [], currentStrike: 0, bestStrike: 0,
    personalRecordsEnabled: cadence === 'DAILY', automaticTrigger: 'NONE', automaticCompletionEnabled: false});

for (const width of [1280, 390]) {
    test(`daily and weekly share controls, persist and preserve evidence at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockBackPainHome(page);
        let routines = [routine(1, 'Daily walk', 'DAILY'), routine(2, 'Check weight', 'WEEKLY')];
        await page.route('**/api/routines**', async route => {
            const request = route.request();
            if (request.method() === 'GET') return route.fulfill({json: routines});
            const item = routines.find(value => value.id === Number(new URL(request.url()).pathname.split('/')[3]));
            const date = request.postDataJSON().date;
            item.times = request.method() === 'DELETE' ? [] : [date];
            return route.fulfill({json: request.method() === 'DELETE'
                ? {routine: item, checkedAt: date, changed: true, dashboard}
                : {result: {routine: item, checkedAt: date, changed: true, dashboard}, recordAchievements: []}});
        });
        await page.goto('/?tab=routines&date=2026-08-12');
        const daily = page.getByRole('tabpanel', {name: 'Daily', exact: true});
        await expect(daily.getByText('Daily walk', {exact: true})).toBeVisible();
        await expect(daily.getByText('Check weight', {exact: true})).toHaveCount(0);
        await page.getByRole('tab', {name: 'Weekly', exact: true}).click();
        let weekly = page.getByRole('tabpanel', {name: 'Weekly', exact: true});
        await expect(weekly.getByText('Check weight', {exact: true})).toBeVisible();
        for (const name of ['Streak', 'Fails']) await expect(weekly.getByRole('columnheader', {name})).toBeVisible();
        await weekly.getByRole('button', {name: 'Complete Check weight', exact: true}).click();
        await expect(weekly.getByRole('button', {name: 'Undo Check weight', exact: true})).toBeVisible();
        await expect(weekly.getByText('Best: 1 week', {exact: true})).toBeVisible();
        await page.reload();
        await page.getByRole('tab', {name: 'Weekly', exact: true}).click();
        weekly = page.getByRole('tabpanel', {name: 'Weekly', exact: true});
        await expect(weekly.getByRole('button', {name: 'Undo Check weight', exact: true})).toBeVisible();
        await weekly.getByRole('button', {name: 'Undo Check weight', exact: true}).click();
        await expect(weekly.getByRole('button', {name: 'Complete Check weight', exact: true})).toBeVisible();
        routines[1].times = ['2026-08-13T08:00:00+02:00'];
        await page.reload();
        await page.getByRole('tab', {name: 'Weekly', exact: true}).click();
        await expect(page.getByRole('button', {name: 'Complete Check weight', exact: true})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`weekly-${width}.png`), fullPage: true});
    });
}

test('weekly editor saves pairs, rejects duplicates and fixes cadence after creation', async ({page}, testInfo) => {
    await page.setViewportSize({width: 390, height: 900});
    await page.route('**/routines', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: require('node:path').resolve(__dirname, '../../dist/index.html')}) : route.fallback());
    await mockBackPainHome(page);
    let routines = [];
    const writes = [];
    await page.route('**/api/routines', async route => {
        if (route.request().method() === 'GET') return route.fulfill({json: routines});
        const body = route.request().postDataJSON();
        writes.push(body);
        routines = [{...routine(10, body.name, body.cadence), reminders: body.weeklyReminders.map((value, index) => ({...value, id: index + 1}))}];
        return route.fulfill({json: routines[0]});
    });
    await page.goto('/routines');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    await page.getByRole('textbox', {name: 'Routine', exact: true}).fill('Review progress');
    await page.getByRole('combobox', {name: 'Cadence', exact: true}).click();
    await page.getByRole('option', {name: 'Weekly', exact: true}).click();
    await page.getByText('Select types', {exact: true}).click();
    await page.getByRole('option', {name: 'WEIGHT', exact: true}).click();
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Add reminder', exact: true}).click();
    await page.getByRole('button', {name: 'Add reminder', exact: true}).click();
    await expect(page.getByText('Each reminder must have a unique day and time.')).toBeVisible();
    await page.getByRole('button', {name: 'Save', exact: true}).click();
    expect(writes).toHaveLength(0);
    await page.getByRole('button', {name: 'Remove reminder 2', exact: true}).click();
    await page.getByRole('button', {name: 'Add reminder', exact: true}).click();
    await page.getByRole('combobox', {name: 'Reminder 2 day', exact: true}).click();
    await page.getByRole('option', {name: 'Friday', exact: true}).click();
    await page.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(writes[0]).toMatchObject({cadence: 'WEEKLY', reminderTimes: [], weeklyReminders: [{day: 'SATURDAY', time: '08:00'}, {day: 'FRIDAY', time: '08:00'}]});
    await page.getByRole('button', {name: 'Edit', exact: true}).click();
    await expect(page.getByRole('combobox', {name: 'Cadence', exact: true})).toBeDisabled();
    await expect(page.getByText('Cadence is fixed when the routine is created.')).toBeVisible();
    await page.clock.runFor(3500);
    await expect(page.getByRole('dialog')).not.toHaveClass(/p-dialog-enter-active/);
    await expect(page.getByRole('dialog')).toHaveCSS('opacity', '1');
    await expect(page.locator('.p-toast-message')).toHaveCount(0);
    await page.screenshot({path: testInfo.outputPath('weekly-editor.png'), fullPage: true});
});


test('saving a measurement refreshes weekly completion without reloading the page', async ({page}) => {
    await mockBackPainHome(page);
    const weekly = {...routine(2, 'Check weight', 'WEEKLY'), automaticTrigger: 'WEIGHT_MEASUREMENT', automaticCompletionEnabled: true};
    let weights = [];
    await page.route('**/api/routines', route => route.fulfill({json: [weekly]}));
    await page.route('**/api/weights', route => {
        if (route.request().method() === 'GET') return route.fulfill({json: weights});
        const body = route.request().postDataJSON();
        weekly.times = [body.date];
        const weight = {...body, id: 4, fat: 14, musclePercentage: 60, lostWeight: 0, lostFat: 0, lostMuscle: 0};
        weights = [weight];
        return route.fulfill({json: {result: weight, recordAchievements: []}});
    });
    await page.goto('/?tab=routines&date=2026-08-12');
    await page.getByRole('tab', {name: 'Weekly', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Complete Check weight', exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'Body', exact: true}).click();
    const weightPanel = page.locator('.p-panel').filter({has: page.getByText('Last Weight', {exact: true})});
    await weightPanel.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Weight', exact: true});
    await dialog.locator('#weight input').fill('70');
    await dialog.locator('#fat-percentage input').fill('20');
    await dialog.locator('#muscle input').fill('42');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).toHaveCount(0);
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Routines', exact: true}).click();
    await page.getByRole('tab', {name: 'Weekly', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Undo Check weight', exact: true})).toBeVisible();
});


test('daily aggregate All chart keeps its daily history range when weekly history is older', async ({page}) => {
    await mockBackPainHome(page);
    const weekly = {...routine(2, 'Check weight', 'WEEKLY'), startDate: '2025-01-04T00:00:00+01:00'};
    await page.route('**/api/routines', route => route.fulfill({json: [routine(1, 'Daily walk', 'DAILY'), weekly]}));
    await page.goto('/');
    await page.locator('.dashboard-charts-trigger').scrollIntoViewIfNeeded();
    const charts = page.locator('.dashboard-charts');
    await charts.getByRole('tab', {name: 'Routines', exact: true}).click();
    await charts.locator('label').filter({hasText: /^All$/}).click();
    const module = require('node:fs').readdirSync('dist/assets').find(name => name.startsWith('auto.esm-'));
    await page.evaluate(async url => {window.testChart = (await import(url)).default;}, `/assets/${module}`);
    const canvas = charts.getByRole('tabpanel', {name: 'Routines', exact: true}).locator('canvas').first();
    await expect.poll(() => canvas.evaluate(el => window.testChart.getChart(el)?.data.labels[0])).toBe('Jul-2026');
});
