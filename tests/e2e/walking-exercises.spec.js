const {test, expect} = require('@playwright/test');
const path = require('node:path');
const {mockBackPainHome} = require('../fixtures/back-pain-chart.cjs');

const choices = ['Indoor walking', 'Outdoor walking'].map((name, index) => ({
    id: 800 + index, name, description: index === 0 ? 'Walk indoors on a treadmill at a comfortable, steady pace. Record the actual walking duration and speed.' : 'Walk outdoors at a comfortable, steady pace. Record the actual walking duration and speed.', trackingMode: 'CARDIO', exerciseType: 'TRAINING',
    cardioMetric: 'SPEED_KPH', imageUrl: `/api/workout-exercises/${800 + index}/image`, hasCustomImage: false, muscleGroups: []
}));

for (const width of [1280, 390]) {
    test(`walking catalog pictures and fixed weekly automation at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockBackPainHome(page);
        await page.route(/\/(workouts|routines)$/, route => route.request().resourceType() === 'document'
            ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')}) : route.fallback());
        await page.route('**/api/workout-exercises', route => route.fulfill({json: choices}));
        await page.route('**/api/workout-exercises/*/image', route => {
            const key = route.request().url().includes('/800/') ? 'indoor' : 'outdoor';
            return route.fulfill({path: path.resolve(__dirname, `../../backend/src/main/resources/exercise-images/${key}-walking.jpg`), contentType: 'image/jpeg'});
        });
        await page.route('**/api/workouts/diary**', route => route.fulfill({json: {items: [], recordEvents: [], totalElements: 0, totalPages: 0, page: 0}}));
        await page.goto('/workouts');
        await page.getByRole('tab', {name: 'Cardio', exact: true}).click();
        for (const exercise of choices) {
            await expect(page.getByText(exercise.name, {exact: true})).toBeVisible();
            await page.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true}).click();
            const dialog = page.getByRole('dialog', {name: exercise.name, exact: true});
            await expect(dialog.getByRole('img')).toBeVisible();
            await expect.poll(() => dialog.getByRole('img').evaluate(img => img.naturalWidth)).toBeGreaterThan(0);
            await dialog.getByRole('button', {name: 'Close', exact: true}).filter({hasText: 'Close'}).click();
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`walking-catalog-${width}.png`), fullPage: true});
        let routine = {id: 52, name: 'Long outside walk', cadence: 'WEEKLY', startDate: '2026-08-08T00:00:00+02:00',
            lastTimeDate: null, types: ['WEIGHT'], times: [], reminders: [], currentStrike: 0, bestStrike: 0,
            personalRecordsEnabled: false, automaticTrigger: 'OUTDOOR_WALK_OVER_40_MINUTES', automaticCompletionEnabled: true};
        await page.route('**/api/routines', route => route.fulfill({json: [routine]}));
        await page.route('**/api/routines/52', route => {
            routine = {...routine, ...route.request().postDataJSON()};
            return route.fulfill({json: routine});
        });
        await page.goto('/routines');
        await page.getByRole('button', {name: 'Edit', exact: true}).click();
        let dialog = page.getByRole('dialog');
        await expect(dialog.getByText('Completes once per week when you log a saved workout with more than 40 minutes of outdoor walking.', {exact: true})).toBeVisible();
        await expect(dialog.getByRole('checkbox', {name: 'Complete automatically', exact: true})).toBeChecked();
        await expect(dialog.getByText('Turning this off keeps existing check-ins.', {exact: true})).toBeVisible();
        await dialog.locator('label[for="routine-automatic-completion"]').click();
        await expect(dialog.getByRole('checkbox', {name: 'Complete automatically', exact: true})).not.toBeChecked();
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog).toHaveCount(0);
        expect(routine.automaticTrigger).toBe('OUTDOOR_WALK_OVER_40_MINUTES');
        expect(routine.automaticCompletionEnabled).toBe(false);
        await page.reload();
        await page.getByRole('button', {name: 'Edit', exact: true}).click();
        dialog = page.getByRole('dialog');
        await expect(dialog.getByRole('checkbox', {name: 'Complete automatically', exact: true})).not.toBeChecked();
        await page.clock.runFor(3500);
        await page.screenshot({path: testInfo.outputPath(`walking-rule-${width}.png`), fullPage: true});
    });
}
