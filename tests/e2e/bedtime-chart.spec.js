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
