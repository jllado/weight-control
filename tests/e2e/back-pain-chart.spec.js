const {test, expect} = require('@playwright/test');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const {mockBackPainHome} = require('../fixtures/back-pain-chart.cjs');

function chartModel() {
    const context = {dayjs: require('dayjs')};
    const model = readFileSync(require.resolve('../../src/model/BackPainEpisode.js'), 'utf8')
        .replace(/^import .*;$/gm, '').replace('export default class', 'class').replaceAll('export ', '');
    vm.runInNewContext(model, context);
    const source = readFileSync(require.resolve('../../src/model/BackPainChart.js'), 'utf8')
        .replace(/^import .*;$/gm, '').replaceAll('export ', '');
    vm.runInNewContext(source, context);
    return context;
}

function episode(date, severity, region = 'LOWER', side = 'LEFT', period = 'MORNING') {
    return {date: new Date(`${date}T00:00:00Z`), severity, region, side, period};
}

const selection = {today: '2026-08-12', chartType: 'monthly', area: 'ALL', period: 'ALL'};

test('back pain chart averages check-ins equally and keeps zero separate from missing days', () => {
    const {buildBackPainChart} = chartModel();
    const result = buildBackPainChart([
        episode('2026-08-12', 'EXTREME'), episode('2026-08-10', 'NONE', null, null),
        episode('2026-08-12', 'MILD'), episode('2026-08-12', 'NONE', null, null)
    ], selection);
    const points = result.data.datasets[0].data;
    expect(points.slice(-3)).toEqual([0, null, 5 / 3]);
    expect(result.rows.at(-1).current.count).toBe(3);
    expect(result.description).toContain('12/08/2026: 1.67/4, 3 check-ins');
    expect(result.data.datasets[0].spanGaps).toBe(false);
    expect(result.hasPrevious).toBe(false);
});

test('back pain chart combines exact-area and time filters including explicit no-pain records', () => {
    const {buildBackPainChart} = chartModel();
    const records = [episode('2026-08-12', 'SEVERE'), episode('2026-08-12', 'MILD', 'UPPER', 'RIGHT'),
        episode('2026-08-12', 'EXTREME', 'LOWER', 'LEFT', 'EVENING'),
        episode('2026-08-12', 'NONE', null, null), episode('2026-08-12', 'MODERATE', 'LOWER', null, null)];
    expect(buildBackPainChart(records, {...selection, area: 'LOWER_LEFT', period: 'MORNING'}).rows.at(-1).current)
        .toMatchObject({average: 1.5, count: 2});
    expect(buildBackPainChart(records, {...selection, area: 'MIDDLE_CENTER'}).rows.at(-1).current.average).toBe(0);
    expect(buildBackPainChart(records, selection).rows.at(-1).current).toMatchObject({average: 2, count: 5});
    expect(buildBackPainChart(records, {...selection, period: 'MIDDAY'}).hasData).toBe(false);
});

test('back pain chart retains daily ranges and stable All bounds across filters', () => {
    const {buildBackPainChart} = chartModel();
    const records = [episode('2026-08-13', 'EXTREME'), episode('2025-01-01', 'MILD'), episode('2026-05-12', 'NONE', null, null)];
    const month = buildBackPainChart(records, selection);
    expect(month.rows[0].date).toBe('2026-05-12');
    expect(month.rows.at(-1).date).toBe('2026-08-12');
    expect(month.rows[0].current.average).toBe(0);
    expect(month.rows.at(-1).current).toBeNull();
    expect(buildBackPainChart(records, {...selection, chartType: 'last_year'}).rows[0].date).toBe('2025-08-12');
    const all = buildBackPainChart(records, {...selection, chartType: 'all', area: 'UPPER_RIGHT'});
    expect(all.rows[0].date).toBe('2025-01-01');
    expect(all.data.datasets).toHaveLength(1);
    expect(buildBackPainChart([], {...selection, chartType: 'all'}).hasData).toBe(false);
});

test('back pain chart aligns actual previous-year dates and preserves baseline-only data', () => {
    const {buildBackPainChart} = chartModel();
    const result = buildBackPainChart([episode('2025-08-12', 'MODERATE')], selection);
    expect(result.hasData).toBe(true);
    expect(result.hasPrevious).toBe(true);
    expect(result.data.datasets[1].data.at(-1)).toBe(2);
    expect(result.rows.at(-1).previous.date).toBe('2025-08-12');
    expect(result.description).toContain('12/08/2025: 2.00/4, 1 check-in');
    const leap = buildBackPainChart([episode('2023-02-28', 'SEVERE')], {...selection, today: '2024-02-29'});
    expect(leap.data.datasets[1].data.slice(-2)).toEqual([3, null]);
});

async function openChart(page) {
    await page.locator('.dashboard-charts-trigger').scrollIntoViewIfNeeded();
    const charts = page.locator('.dashboard-charts');
    await charts.getByRole('tab', {name: 'Back pain', exact: true}).click();
    return charts.getByRole('region', {name: 'Back pain chart', exact: true});
}

async function select(page, label, value) {
    await page.getByRole('combobox', {name: label, exact: true}).click();
    await page.getByRole('option', {name: value, exact: true}).click();
}

test('back pain chart loads independently and keeps filters through tabs and ranges', async ({page}, testInfo) => {
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    const state = await mockBackPainHome(page);
    await page.goto('/');
    const chart = await openChart(page);
    const canvas = chart.getByRole('img', {name: 'Daily average back pain'});
    await expect(canvas).toHaveAccessibleDescription(/12\/08\/2026: 1.33\/4, 3 check-ins/);
    await expect(canvas).toHaveAccessibleDescription(/12\/08\/2025: 2.00\/4, 1 check-in/);
    expect(state.reads).toBe(1);
    await select(page, 'Back area', 'Lower left');
    await select(page, 'Time of day', 'Morning');
    await expect(canvas).toHaveAccessibleDescription(/12\/08\/2026: 1.50\/4, 2 check-ins/);
    await page.locator('.dashboard-charts').getByRole('tab', {name: 'Mood', exact: true}).click();
    await openChart(page);
    await expect(chart.getByRole('combobox', {name: 'Back area'})).toHaveText('Lower left');
    for (const range of ['Year', 'All', 'Monthly']) {
        await page.locator('.dashboard-charts label').filter({hasText: new RegExp(`^${range}$`)}).click();
        await expect(chart.getByRole('combobox', {name: 'Time of day'})).toHaveText('Morning');
        await expect(canvas).toHaveAccessibleDescription(/12\/08\/2026: 1.50\/4/);
        if (range === 'All') await expect(canvas).not.toHaveAccessibleDescription(/Year ago:/);
    }
    expect(state.reads).toBe(1);
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 1100});
        await chart.scrollIntoViewIfNeeded();
        await expect(canvas).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        const fields = await chart.locator('.back-pain-chart-field').evaluateAll(nodes => nodes.map(node => ({x: node.getBoundingClientRect().x, y: node.getBoundingClientRect().y})));
        expect(width <= 640 ? fields[0].x === fields[1].x : fields[0].y === fields[1].y).toBe(true);
        await chart.screenshot({path: testInfo.outputPath(`back-pain-chart-${width}.png`)});
    }
    await page.reload();
    await openChart(page);
    await expect(chart.getByRole('combobox', {name: 'Back area'})).toHaveText('All');
    await expect(chart.getByRole('combobox', {name: 'Time of day'})).toHaveText('All');
    expect(errors).toEqual([]);
});

test('back pain chart exposes retry and distinguishes no matches from recorded zero', async ({page}) => {
    const state = await mockBackPainHome(page, {fail: true});
    await page.goto('/');
    const chart = await openChart(page);
    await expect(chart.getByRole('alert')).toContainText('Unable to load back check-ins');
    await expect(chart.getByText('No back check-ins for these filters in the selected period.')).toHaveCount(0);
    state.fail = false;
    await chart.getByRole('button', {name: 'Retry'}).click();
    const canvas = chart.getByRole('img');
    await expect(canvas).toHaveAccessibleDescription(/1.33\/4/);
    await select(page, 'Back area', 'Middle center');
    await select(page, 'Time of day', 'Midday');
    await expect(canvas).toHaveAccessibleDescription(/10\/08\/2026: 0.00\/4, 1 check-in/);
    await expect(chart.getByText('No previous-year data for these filters.')).toBeVisible();
    await select(page, 'Time of day', 'Evening');
    await expect(chart.getByText('No back check-ins for these filters in the selected period.')).toBeVisible();
    await expect(canvas).toHaveCount(0);
});

test('back pain chart refreshes after creating editing and deleting check-ins', async ({page}) => {
    await mockBackPainHome(page, {records: []});
    await page.goto('/');
    const chart = await openChart(page);
    await expect(chart.getByText('No back check-ins for these filters in the selected period.')).toBeVisible();
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Back', exact: true}).click();
    await page.getByRole('button', {name: 'Add check-in', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Back check-in'});
    await dialog.locator('#period').click();
    await page.getByRole('option', {name: 'Morning', exact: true}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'No pain', exact: true}).click();
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).toBeHidden();
    await expect(chart.getByRole('img')).toHaveAccessibleDescription(/12\/08\/2026: 0.00\/4, 1 check-in/);
    await page.locator('.back-pain-episodes').getByRole('button', {name: 'Edit', exact: true}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'Severe', exact: true}).click();
    await dialog.getByRole('button', {name: 'Lower Left', exact: true}).click();
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).toBeHidden();
    await expect(chart.getByRole('img')).toHaveAccessibleDescription(/12\/08\/2026: 3.00\/4, 1 check-in/);
    await page.reload();
    await openChart(page);
    await expect(chart.getByRole('img')).toHaveAccessibleDescription(/12\/08\/2026: 3.00\/4, 1 check-in/);
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Back', exact: true}).click();
    page.once('dialog', dialog => dialog.accept());
    await page.locator('.back-pain-episodes').getByRole('button', {name: 'Delete', exact: true}).click();
    await expect(chart.getByText('No back check-ins for these filters in the selected period.')).toBeVisible();
});
