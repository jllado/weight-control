const {test, expect} = require('@playwright/test');
const path = require('node:path');
const {mockBackPainHome} = require('../fixtures/back-pain-chart.cjs');

const routines = Array.from({length: 24}, (_, index) => ({
    id: index + 1, name: `${index % 3 === 0 ? 'Walk' : 'Review'} ${String(index + 1).padStart(2, '0')}`,
    cadence: index % 2 === 0 ? 'WEEKLY' : 'DAILY', startDate: '2026-08-08T00:00:00+02:00',
    lastTimeDate: null, types: ['WEIGHT'], times: [], reminders: [], currentStrike: 0, bestStrike: 0,
    personalRecordsEnabled: false, automaticTrigger: 'NONE', automaticCompletionEnabled: false
}));

for (const width of [1280, 390]) {
    test(`cadence filter combines search, resets pages and stays local at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockBackPainHome(page);
        await page.route(/\/(routines|coach-notes)$/, route => route.request().resourceType() === 'document'
            ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')}) : route.fallback());
        const writes = [];
        await page.route('**/api/routines', route => {
            if (route.request().method() !== 'GET') writes.push(route.request().method());
            return route.fulfill({json: routines});
        });
        await page.goto('/routines');
        const cadence = page.getByRole('combobox', {name: 'Filter by cadence', exact: true});
        const table = page.locator('.p-datatable');
        const report = text => expect(table.getByText(text, {exact: true})).toBeVisible();
        const select = async label => {
            await cadence.click();
            await page.getByRole('option', {name: label, exact: true}).click();
        };
        await expect(cadence).toHaveText('All');
        await report('1 to 10 of 24');
        await table.getByRole('button', {name: 'Next Page', exact: true}).click();
        await report('11 to 20 of 24');
        await cadence.focus();
        await cadence.press('ArrowDown');
        await page.keyboard.press('End');
        await page.keyboard.press('Enter');
        await expect(cadence).toHaveText('Weekly');
        await report('1 to 10 of 12');
        await expect(table.locator('tbody tr')).toHaveCount(10);
        for (const row of await table.locator('tbody tr').all()) await expect(row.getByText('Weekly', {exact: true})).toBeVisible();
        await table.getByRole('button', {name: 'Next Page', exact: true}).click();
        await report('11 to 12 of 12');
        await select('Daily');
        await report('1 to 10 of 12');
        for (const row of await table.locator('tbody tr').all()) await expect(row.getByText('Daily', {exact: true})).toBeVisible();
        await page.getByPlaceholder('Search', {exact: true}).fill('Walk');
        await report('1 to 4 of 4');
        await expect(table.locator('tbody tr')).toHaveCount(4);
        await expect(table.getByText('Walk 04', {exact: true})).toBeVisible();
        await expect(table.getByText('Walk 01', {exact: true})).toHaveCount(0);
        await select('Weekly');
        await report('1 to 4 of 4');
        await expect(table.getByText('Walk 01', {exact: true})).toBeVisible();
        await expect(table.getByText('Walk 04', {exact: true})).toHaveCount(0);
        await page.getByPlaceholder('Search', {exact: true}).fill('No match');
        await report('0 to 0 of 0');
        await page.getByPlaceholder('Search', {exact: true}).fill('Walk');
        await page.getByRole('tab', {name: 'Analytics', exact: true}).click();
        await page.getByRole('tab', {name: 'Manage', exact: true}).click();
        await expect(cadence).toHaveText('Weekly');
        await report('1 to 4 of 4');
        await cadence.scrollIntoViewIfNeeded();
        await page.screenshot({path: testInfo.outputPath(`cadence-${width}.png`), fullPage: true});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await table.getByRole('button', {name: 'Clear cadence filter', exact: true}).click();
        await expect(cadence).toHaveText('All');
        await report('1 to 8 of 8');
        await select('Daily');
        await select('All');
        await report('1 to 8 of 8');
        await select('Weekly');
        await page.reload();
        await expect(cadence).toHaveText('All');
        await expect(page.getByPlaceholder('Search', {exact: true})).toHaveValue('');
        await report('1 to 10 of 24');
        await select('Daily');
        await page.goto('/coach-notes');
        await page.goto('/routines');
        await expect(cadence).toHaveText('All');
        await report('1 to 10 of 24');
        expect(writes).toEqual([]);
    });
}
