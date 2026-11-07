const {test, expect} = require('@playwright/test');
const {mockBackPainHome} = require('../fixtures/back-pain-chart.cjs');

test.use({timezoneId: 'Europe/Madrid'});

const sleeps = [2026, 2025].flatMap(year => Array.from({length: 7}, (_, index) => ({
    id: year * 10 + index,
    date: `${year}-08-${String(12 - index).padStart(2, '0')}`,
    bedtimeStart: `${year}-08-${String(11 - index).padStart(2, '0')}T${year === 2026 ? (index ? '22:46' : '22:47') : '23:59'}:00+02:00`,
    bedtimeEnd: `${year}-08-${String(12 - index).padStart(2, '0')}T${year === 2026 ? (index < 4 ? '06:29' : '06:28') : '07:15'}:00+02:00`,
    totalSleepDuration: year === 2026 ? 21600 : 25200, deepSleepDuration: 3600, remSleepDuration: 3600,
    lightSleepDuration: 14400, awakeTime: 600, averageHeartRate: 60, averageHrv: 40
})));

async function openCharts(page) {
    await page.locator('.dashboard-charts-trigger').scrollIntoViewIfNeeded();
    await page.locator('.dashboard-charts').getByRole('tab', {name: 'Sleep', exact: true}).click();
    await expect(page.locator('.dashboard-charts').getByRole('tabpanel', {name: 'Sleep', exact: true}).locator('canvas')).toHaveCount(9);
    const module = require('fs').readdirSync('dist/assets').find(name => name.startsWith('auto.esm-'));
    await page.evaluate(async url => {
        window.testChart = (await import(url)).default;
    }, `/assets/${module}`);
}

for (const width of [1280, 390]) {
    test(`bedtime tooltips display clock times across ranges at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockBackPainHome(page);
        await page.route('**/api/sleeps', route => route.fulfill({json: sleeps}));
        await page.goto('/');
        await openCharts(page);

        for (const range of ['Monthly', 'Year', 'All']) {
            await page.locator('.dashboard-charts label').filter({hasText: new RegExp(`^${range}$`)}).click();
            for (const [chartIndex, current, previous, average] of [[7, '22:46', '23:59', 1366.14], [8, '06:29', '07:15', 388.57], [0, '6.0 h', '7.0 h', 6]]) {
                const canvas = page.locator('.dashboard-charts').getByRole('tabpanel', {name: 'Sleep', exact: true}).locator('canvas').nth(chartIndex);
                await expect.poll(() => canvas.evaluate(el => Boolean(window.testChart.getChart(el)))).toBe(true);
                for (const datasetIndex of range === 'All' ? [0] : [0, 1]) {
                    await canvas.scrollIntoViewIfNeeded();
                    const point = await canvas.evaluate((el, datasetIndex) => {
                        const chart = window.testChart.getChart(el);
                        chart.stop();
                        chart.update('none');
                        const index = chart.data.labels.indexOf('Aug-2026');
                        const {x, y} = chart.getDatasetMeta(datasetIndex).data[index];
                        return {x, y};
                    }, datasetIndex);
                    if (width === 390) await canvas.tap({position: point});
                    else await canvas.hover({position: point});
                    await expect.poll(() => canvas.evaluate(el => {
                        const tooltip = window.testChart.getChart(el).tooltip;
                        return {title: tooltip.title, lines: tooltip.body?.flatMap(item => item.lines)};
                    })).toEqual({title: [datasetIndex ? 'Aug-2025' : 'Aug-2026'], lines: [`${datasetIndex ? 'Year Ago' : 'Current'}: ${datasetIndex ? previous : current}`]});
                    await expect.poll(() => canvas.evaluate(el => window.testChart.getChart(el).tooltip.opacity)).toBe(1);
                    await canvas.screenshot({path: testInfo.outputPath(`${range}-${chartIndex}-${datasetIndex}-${width}.png`)});
                }
                const data = await canvas.evaluate(el => window.testChart.getChart(el).data.datasets[0].data);
                expect(data).toContain(null);
                const august = await canvas.evaluate(el => window.testChart.getChart(el).data.labels.indexOf('Aug-2026'));
                expect(data[august]).toBe(average);
                expect(data.filter(value => value !== null).every(value => typeof value === 'number')).toBe(true);
            }
        }
        await page.reload();
        await openCharts(page);
        const duration = page.locator('.dashboard-charts').getByRole('tabpanel', {name: 'Sleep', exact: true}).locator('canvas').first();
        await expect.poll(() => duration.evaluate(el => window.testChart.getChart(el)?.options.plugins.title.text)).toBe('Total Sleep h');
        const formatting = await page.locator('.dashboard-charts').getByRole('tabpanel', {name: 'Sleep', exact: true}).locator('canvas').nth(7).evaluate(el => {
            const chart = window.testChart.getChart(el);
            return [1366.14, 388.57, 1439.6, 1441, -1].map(value => chart.options.plugins.tooltip.callbacks.label({dataset: {label: 'Current'}, parsed: {y: value}}));
        });
        expect(formatting).toEqual(['Current: 22:46', 'Current: 06:29', 'Current: 00:00', 'Current: 00:01', 'Current: 23:59']);
    });
}

for (const width of [1280, 390]) {
    test(`travel-night charts and edits retain local clocks at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockBackPainHome(page);
        let travel = {
            ...sleeps.find(sleep => sleep.date === '2025-08-12'),
            bedtimeStart: '2025-08-11T23:10:00-05:00', bedtimeEnd: '2025-08-12T09:10:00-05:00',
            bedtimeStartOffsetSeconds: -18000, bedtimeEndOffsetSeconds: -18000,
            totalSleepDuration: 34200, awakeTime: 1800, deepSleepDuration: 3600,
            remSleepDuration: 3600, lightSleepDuration: 27000
        };
        const writes = [];
        await page.route('**/api/sleeps', route => route.fulfill({json: [travel, ...sleeps.filter(sleep => sleep.date.startsWith('2026'))]}));
        await page.route('**/api/sleeps/*', async route => {
            const body = route.request().postDataJSON();
            writes.push(body);
            travel = {...travel, ...body, date: body.sleepDate};
            await route.fulfill({json: {result: travel, recordAchievements: []}});
        });
        await page.goto('/');
        await openCharts(page);
        for (const [index, expected] of [[7, 1390], [8, 550]]) {
            const canvas = page.locator('.dashboard-charts').getByRole('tabpanel', {name: 'Sleep', exact: true}).locator('canvas').nth(index);
            await expect.poll(() => canvas.evaluate(el => {
                const chart = window.testChart.getChart(el);
                return chart?.data.datasets[1].data[chart.data.labels.indexOf('Aug-2026')];
            })).toBe(expected);
            await canvas.screenshot({path: testInfo.outputPath(`travel-chart-${index}-${width}.png`)});
        }
        await page.addInitScript(() => window.history.replaceState({}, '', '/sleep'));
        await page.goto('/');
        const row = page.getByRole('row').filter({hasText: '12/08/2025'});
        if (width === 1280) await expect(row).toContainText('11/08 23:10 - 12/08 09:10');
        await row.getByRole('button', {name: 'Edit', exact: true}).click();
        const dialog = page.getByRole('dialog', {name: 'Sleep', exact: true});
        await expect(dialog.getByLabel('Bedtime Start time', {exact: true})).toHaveValue('23:10');
        await expect(dialog.getByLabel('Bedtime End time', {exact: true})).toHaveValue('09:10');
        await expect(dialog).toContainText('Time in bed: 10:00');
        await dialog.locator('.p-float-label').filter({hasText: 'Average Heart Rate'}).locator('input').fill('61');
        await dialog.screenshot({path: testInfo.outputPath(`travel-editor-${width}.png`)});
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog).not.toBeVisible();
        expect(writes).toHaveLength(1);
        expect(writes[0]).toMatchObject({bedtimeStart: '2025-08-11T23:10:00-05:00', bedtimeEnd: '2025-08-12T09:10:00-05:00', totalSleepDuration: 34200, averageHeartRate: 61});
        await page.goto('/');
        await page.getByRole('row').filter({hasText: '12/08/2025'}).getByRole('button', {name: 'Edit', exact: true}).click();
        await expect(dialog.getByLabel('Bedtime Start time', {exact: true})).toHaveValue('23:10');
        await expect(dialog).toContainText('Time in bed: 10:00');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    });
}

test('travel sleep in the viewing device daylight-saving gap survives editing', async ({page}) => {
    await mockBackPainHome(page);
    await page.clock.setFixedTime(new Date('2025-03-30T20:00:00Z'));
    const travel = {...sleeps[0], id: 50, date: '2025-03-30',
        bedtimeStart: '2025-03-30T02:30:00-05:00', bedtimeEnd: '2025-03-30T10:30:00-05:00',
        bedtimeStartOffsetSeconds: -18000, bedtimeEndOffsetSeconds: -18000,
        totalSleepDuration: 27000, awakeTime: 1800, deepSleepDuration: 3600, remSleepDuration: 3600, lightSleepDuration: 19800};
    await page.route('**/api/sleeps', route => route.fulfill({json: [travel]}));
    let payload;
    await page.route('**/api/sleeps/50', route => {
        payload = route.request().postDataJSON();
        return route.fulfill({json: {result: travel, recordAchievements: []}});
    });
    await page.addInitScript(() => window.history.replaceState({}, '', '/sleep'));
    await page.goto('/');
    await page.getByRole('row').filter({hasText: '30/03/2025'}).getByRole('button', {name: 'Edit', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Sleep', exact: true});
    const clockInput = dialog.getByLabel('Bedtime Start time', {exact: true});
    await expect(clockInput).toHaveValue('02:30');
    await clockInput.fill('02:35');
    await clockInput.press('Tab');
    await expect(clockInput).toHaveValue('02:35');
    await expect(dialog).toContainText('Time in bed: 07:55');
    await clockInput.click();
    const panel = page.locator(`#${await clockInput.getAttribute('aria-controls')}`);
    await panel.getByRole('button', {name: 'Next Minute', exact: true}).click();
    await expect(clockInput).toHaveValue('02:40');
    await clockInput.press('Escape');
    await clockInput.fill('02:30');
    await clockInput.press('Tab');
    await expect(dialog).toContainText('Time in bed: 08:00');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).not.toBeVisible();
    expect(payload).toMatchObject({bedtimeStart: travel.bedtimeStart, bedtimeEnd: travel.bedtimeEnd, totalSleepDuration: 27000});
});

test('new sleep retains ordinary date limits and rejects reversed clocks', async ({page}) => {
    await mockBackPainHome(page);
    const writes = [];
    await page.route('**/api/sleeps', async route => {
        if (route.request().method() === 'GET') return route.fulfill({json: []});
        writes.push(route.request().postDataJSON());
        return route.fulfill({json: {result: {...writes.at(-1), id: 1, date: writes.at(-1).sleepDate}, recordAchievements: []}});
    });
    await page.addInitScript(() => window.history.replaceState({}, '', '/sleep'));
    await page.goto('/');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Sleep', exact: true});
    const start = dialog.getByLabel('Bedtime Start time', {exact: true});
    const end = dialog.getByLabel('Bedtime End time', {exact: true});
    await expect(start).toHaveValue('23:00');
    await expect(end).toHaveValue('07:00');
    const date = dialog.locator('.p-float-label').filter({has: page.locator('label', {hasText: /^Date$/})}).locator('input');
    await date.fill('10/08/2026');
    await date.press('Tab');
    await expect(start).toHaveValue('23:00');
    await expect(end).toHaveValue('07:00');
    await dialog.getByLabel('Bedtime End date', {exact: true}).fill('09/08/2026');
    await dialog.getByLabel('Bedtime End date', {exact: true}).press('Tab');
    await end.fill('22:00');
    await end.press('Tab');
    await expect(dialog).toContainText('Bedtime end must be after bedtime start');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect(writes).toHaveLength(0);
    await dialog.getByLabel('Bedtime End date', {exact: true}).fill('10/08/2026');
    await dialog.getByLabel('Bedtime End date', {exact: true}).press('Tab');
    await end.fill('07:00');
    await end.press('Tab');
    await expect(end).toHaveValue('07:00');
    await expect(dialog).toContainText('Time in bed: 08:00');
    await dialog.locator('.p-float-label').filter({hasText: 'Average Heart Rate'}).locator('input').fill('60');
    await dialog.locator('.p-float-label').filter({hasText: 'Average HRV'}).locator('input').fill('40');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).not.toBeVisible();
    expect(writes).toHaveLength(1);
    expect(writes[0]).toMatchObject({sleepDate: '2026-08-10', bedtimeStart: '2026-08-09T23:00:00+02:00', bedtimeEnd: '2026-08-10T07:00:00+02:00', totalSleepDuration: 28800});
});
