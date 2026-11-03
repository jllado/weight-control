const {test, expect} = require('@playwright/test');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const {mockBackPainHome} = require('../fixtures/back-pain-chart.cjs');
const {dashboard, dashboardDailyStatus} = require('../fixtures/dashboard.cjs');

function trendModel() {
    const context = {dayjs: require('dayjs')};
    for (const name of ['BackPainEpisode', 'BackPainDaily', 'BackPainTrends']) {
        const source = readFileSync(require.resolve(`../../src/model/${name}.js`), 'utf8')
            .replace(/^import .*;$/gm, '').replace('export default class', 'class').replaceAll('export ', '');
        vm.runInNewContext(source, context);
    }
    return context;
}

function record(date, severity, region = 'LOWER', side = 'LEFT') {
    return {date: new Date(`${date}T00:00:00Z`), severity, region, side};
}

test('back trends weight observed days equally and compare exact inclusive windows', () => {
    const {buildBackPainTrends} = trendModel();
    const result = buildBackPainTrends([
        record('2026-08-12', 'EXTREME'), record('2026-08-12', 'NONE', null, null),
        record('2026-08-12', 'MODERATE'), record('2026-07-14', 'MILD'),
        record('2026-07-13', 'SEVERE'), record('2026-06-14', 'MILD'),
        record('2026-06-13', 'EXTREME'), record('2026-08-13', 'EXTREME')
    ], '2026-08-12');
    expect(result).toMatchObject({start: '2026-07-14', end: '2026-08-12', previousStart: '2026-06-14', previousEnd: '2026-07-13'});
    expect(result.rows[0]).toMatchObject({current: {average: 1.5, days: 2}, previous: {average: 2, days: 2}, delta: -0.5});
});

test('back trends include global zero days and only currently painful exact areas in stable order', () => {
    const {buildBackPainTrends} = trendModel();
    const result = buildBackPainTrends([
        record('2026-08-12', 'SEVERE'), record('2026-08-11', 'MILD', 'UPPER', 'RIGHT'),
        record('2026-08-10', 'NONE', null, null), record('2026-08-09', 'EXTREME', 'MIDDLE', null),
        record('2026-07-01', 'MODERATE', 'MIDDLE', 'CENTER')
    ], '2026-08-12');
    expect(result.rows.map(row => row.value)).toEqual(['ALL', 'UPPER_RIGHT', 'LOWER_LEFT']);
    expect(result.rows[0]).toMatchObject({current: {average: 2, days: 4}, previous: {average: 2, days: 1}, delta: 0});
    expect(result.rows[1]).toMatchObject({current: {average: 0.5, days: 2}, previous: {average: null, days: 0}, delta: null});
    expect(result.rows[2]).toMatchObject({current: {average: 1.5, days: 2}, previous: {average: null, days: 0}, delta: null});
});

test('back trends preserve missing evidence, explicit zero, Madrid dates and unrounded deltas', () => {
    const {buildBackPainTrends, backPainTrendDelta} = trendModel();
    expect(buildBackPainTrends([], '2026-08-12').rows).toMatchObject([{value: 'ALL', current: {average: null, days: 0}, delta: null}]);
    expect(buildBackPainTrends([record('2026-08-12', 'NONE')], '2026-08-12').rows).toMatchObject([{value: 'ALL', current: {average: 0, days: 1}, delta: null}]);
    const midnight = {...record('2026-08-11', 'MILD'), date: new Date('2026-08-11T22:30:00Z')};
    expect(buildBackPainTrends([midnight], '2026-08-11').rows[0].current.average).toBeNull();
    expect(buildBackPainTrends([midnight], '2026-08-12').rows[0].current.average).toBe(1);
    const records = [record('2026-08-12', 'MILD'), ...Array.from({length: 5}, () => record('2026-08-12', 'NONE')),
        record('2026-07-01', 'MILD'), ...Array.from({length: 8}, () => record('2026-07-01', 'NONE'))];
    const row = buildBackPainTrends(records, '2026-08-12').rows[0];
    expect(row.delta).toBeCloseTo(1 / 6 - 1 / 9, 12);
    expect(backPainTrendDelta(row.delta)).toBe('+0.06');
    expect(backPainTrendDelta(-0.001)).toBe('0');
    expect(backPainTrendDelta(null)).toBe('—');
});

async function openBack(page) {
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Back', exact: true}).click();
    return page.getByRole('region', {name: 'Back pain trends', exact: true});
}

test('Back trends show signed worsening and neutral unchanged values after loading', async ({page}) => {
    await mockBackPainHome(page, {records: [
        {id: 1, date: '2026-08-12', period: 'MORNING', severity: 'SEVERE', region: 'UPPER', side: 'CENTER'},
        {id: 2, date: '2026-08-12', period: 'EVENING', severity: 'MILD', region: 'LOWER', side: 'LEFT'},
        {id: 3, date: '2026-07-01', period: 'MORNING', severity: 'MILD', region: 'UPPER', side: 'CENTER'},
        {id: 4, date: '2026-07-01', period: 'EVENING', severity: 'MILD', region: 'LOWER', side: 'LEFT'}
    ]});
    let release;
    const held = new Promise(resolve => { release = resolve; });
    await page.route('**/api/back-pain-episodes', async route => { await held; await route.fallback(); });
    await page.goto('/');
    const trends = await openBack(page);
    await expect(page.getByText('Loading back pain data…')).toBeVisible();
    await expect(trends).toHaveCount(0);
    release();
    await expect(trends.locator('.trend-average')).toHaveText(['2 / 4', '3 / 4', '1 / 4']);
    await expect(trends.locator('.trend-change')).toHaveText(['+1', '+2', '0']);
    await expect(trends.locator('.trend-change').first()).toHaveClass(/bad/);
    await expect(trends.locator('.trend-change').last()).not.toHaveClass(/good|bad/);
});

test('Back panel shows compact trends and deltas and recomputes with dashboard navigation', async ({page}, testInfo) => {
    const state = await mockBackPainHome(page, {records: [
        {id: 1, date: '2026-08-12', period: 'MORNING', severity: 'SEVERE', region: 'LOWER', side: 'LEFT'},
        {id: 2, date: '2026-08-11', period: 'EVENING', severity: 'MILD', region: 'UPPER', side: 'RIGHT'},
        {id: 3, date: '2026-08-10', period: 'MIDDAY', severity: 'NONE', region: null, side: null},
        {id: 4, date: '2026-07-01', period: 'MORNING', severity: 'MODERATE', region: 'LOWER', side: 'LEFT'}
    ]});
    await page.route('**/api/dashboard/retreat', route => route.fulfill({json: {...dashboard, anchorDate: '2026-08-11', dailyStatus: dashboardDailyStatus('2026-08-11')}}));
    await page.route('**/api/dashboard/advance', route => route.fulfill({json: dashboard}));
    await page.goto('/');
    const trends = await openBack(page);
    const rows = trends.locator('.back-pain-trend-row');
    await expect(rows.locator('.p-col-5')).toHaveText(['Trend Back:', 'Trend Upper right:', 'Trend Lower left:']);
    await expect(rows.locator('.trend-average')).toHaveText(['1.33 / 4', '0.5 / 4', '1.5 / 4']);
    await expect(rows.locator('.trend-change')).toHaveText(['-0.67', '—', '-0.5']);
    await expect(rows.first().locator('.trend-change')).toHaveClass(/good/);
    await expect(rows.first()).toContainText('2026-07-14 to 2026-08-12: 3 observed days');
    await expect(rows.first()).toContainText('2026-06-14 to 2026-07-13: 1 observed day');
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 1100});
        await trends.scrollIntoViewIfNeeded();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        const boxes = await rows.locator('.back-pain-trend-result').evaluateAll(nodes => nodes.map(node => node.getBoundingClientRect().x));
        expect(new Set(boxes).size).toBe(1);
        await trends.locator('..').screenshot({path: testInfo.outputPath(`back-trends-${width}.png`)});
    }
    await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
    await expect(rows.locator('.p-col-5')).toHaveText(['Trend Back:', 'Trend Upper right:']);
    await expect(rows.first().locator('.trend-average')).toHaveText('0.5 / 4');
    await expect(rows.first().locator('.trend-change')).toHaveText('-1.5');
    await page.getByRole('button', {name: 'New Day', exact: true}).click();
    await expect(rows).toHaveCount(3);
    await page.reload();
    await openBack(page);
    await expect(rows.first().locator('.trend-average')).toHaveText('1.33 / 4');
    expect(state.reads).toBeGreaterThan(0);
});

test('Back panel distinguishes loading failure, missing evidence and observed zero with Retry', async ({page}) => {
    const state = await mockBackPainHome(page, {records: [], fail: true});
    await page.goto('/');
    const trends = await openBack(page);
    await expect(page.getByRole('alert').filter({hasText: 'Unable to load back check-ins'})).toBeVisible();
    await expect(trends).toHaveCount(0);
    state.fail = false;
    await page.getByRole('button', {name: 'Retry', exact: true}).click();
    await expect(trends.locator('.trend-average')).toHaveText('—');
    await expect(trends.locator('.trend-change')).toHaveText('—');
    await page.getByRole('button', {name: 'Add check-in', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Back check-in'});
    await dialog.locator('#period').click();
    await page.getByRole('option', {name: 'Morning', exact: true}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'No pain', exact: true}).click();
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).toBeHidden();
    await expect(trends.locator('.trend-average')).toHaveText('0 / 4');
    await expect(trends.locator('.trend-change')).toHaveText('—');
    await page.locator('.back-pain-episodes').getByRole('button', {name: 'Edit', exact: true}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'Severe', exact: true}).click();
    await dialog.getByRole('button', {name: 'Lower Left', exact: true}).click();
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).toBeHidden();
    await expect(trends.locator('.trend-average')).toHaveText(['3 / 4', '3 / 4']);
    await page.reload();
    await openBack(page);
    await expect(trends.locator('.trend-average')).toHaveText(['3 / 4', '3 / 4']);
    page.once('dialog', dialog => dialog.accept());
    await page.locator('.back-pain-episodes').getByRole('button', {name: 'Delete', exact: true}).click();
    await expect(trends.locator('.trend-average')).toHaveText(['—']);
});
