const {test, expect} = require('@playwright/test');
const {sleepWarning, nutritionWarning} = require('../fixtures/warning-metrics.cjs');

const fridayDate = '2026-08-14';
const weeklySnapshot = {
    schemaVersion: 1,
    periodStart: '2026-08-08',
    fridayDate,
    progress: {
        completeWeek: true,
        currentPeriod: {
            startDate: '2026-08-08', endDate: fridayDate,
            dashboard: {routinesPercentage: 50, weightPercentage: null, bloodPressurePercentage: null, flexibilityPercentage: 75, mindPercentage: 50},
            routineCheckins: 4,
            routineCompletion: {completed: 4, opportunities: 7, percentage: 57.14, days: []},
            weight: {weightKg: 70, fatPercentage: 20, fatKg: 14, musclePercentage: 75, muscleKg: 52.5, measurementCount: 2},
            bloodPressure: {systolic: 120, diastolic: 80, measurementCount: 2},
            moodAverage: 3.5, moodDayCount: 2, sleep: {totalSleepSeconds: 25200, deepSleepSeconds: 5400, remSleepSeconds: 7200, lightSleepSeconds: 12600, awakeSeconds: 1800, averageHeartRate: 58, averageHrv: 42, nightCount: 5},
            calories: {entryCount: 5, averageCalories: 1800, averageProteinGrams: 100, proteinDayCount: 5, averageCarbohydrateGrams: 200, carbohydrateDayCount: 5, averageFatGrams: 60, fatDayCount: 5},
            workouts: {workoutCount: 0, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0, durationReadingCount: 0, distanceReadingCount: 0, calorieReadingCount: 0, strengthSetCount: 0},
            sicknessesByType: {}, sicknessesBySeverity: {}, decisions: {wins: 0, misses: 0, winRate: null}
        },
        previousComparablePeriod: {dashboard: null, routineCompletion: {completed: 3, opportunities: 7, percentage: 42.86}, weight: null, bloodPressure: null, moodAverage: null, sleep: null, calories: {averageCalories: null, averageProteinGrams: null, proteinDayCount: 0, averageCarbohydrateGrams: null, carbohydrateDayCount: 0, averageFatGrams: null, fatDayCount: 0}, workouts: {workoutCount: 0, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0, durationReadingCount: 0, distanceReadingCount: 0, calorieReadingCount: 0, strengthSetCount: 0}, sicknessesByType: {}, sicknessesBySeverity: {}, decisions: {wins: 0, misses: 0, winRate: null}},
        yearAgoComparablePeriod: {dashboard: null, routineCompletion: {completed: 4, opportunities: 7, percentage: 57.14}, weight: null, bloodPressure: null, moodAverage: null, sleep: null, calories: {averageCalories: null, averageProteinGrams: null, proteinDayCount: 0, averageCarbohydrateGrams: null, carbohydrateDayCount: 0, averageFatGrams: null, fatDayCount: 0}, workouts: {workoutCount: 0, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0, durationReadingCount: 0, distanceReadingCount: 0, calorieReadingCount: 0, strengthSetCount: 0}, sicknessesByType: {}, sicknessesBySeverity: {}, decisions: {wins: 0, misses: 0, winRate: null}}
    },
    outcomes: {weight: null, bloodPressure: null},
    routines: [{name: 'Morning walk', completedDays: 4, eligibleDays: 7, percentage: 57.14}],
    goalEvidence: {available: false, goal: null, startDate: null, reviewDate: null, unavailableReason: 'The coaching plan version applicable to this week is not available.'},
    personalRecords: [],
    warnings: ['No weight measurement was recorded Friday, Saturday, or Sunday.', 'No blood pressure measurement was recorded Friday, Saturday, or Sunday.']
};

const savedSummary = {
    periodStart: '2026-08-08',
    fridayDate,
    createdAt: '2026-08-17T06:00:00Z',
    snapshot: weeklySnapshot,
    reflection: null,
    presentation: {
        highlights: [
            {key: 'sleep', label: 'Average sleep', value: '7 h 0 min', comparison: 'Previous week not available', coverage: '5 nights', currentWidth: 100, previousWidth: 0},
            {key: 'calories', label: 'Daily calories', value: '1800 kcal', comparison: 'Previous week not available', coverage: '5 recorded days', currentWidth: 100, previousWidth: 0},
            {key: 'workouts', label: 'Workouts', value: '0 sessions', comparison: '0 sessions vs previous week', coverage: 'Recorded sessions', currentWidth: 0, previousWidth: 0},
            {key: 'routines', label: 'Routines', value: '57%', comparison: '+14 pp vs previous week', coverage: '4/7 opportunities', currentWidth: 100, previousWidth: 75},
            {key: 'records', label: 'Personal records', value: '0 new', coverage: 'No new records this week'}
        ],
        recommendations: [], nutritionRows: [], periods: []
    }
};

function refinedReviewFixture() {
    const summary = structuredClone(savedSummary);
    summary.snapshot.schemaVersion = 9;
    summary.snapshot.workoutTargets = null;
    const missing = {label: 'Plan unavailable', color: '#486277'};
    summary.presentation.visualSummary = {
        routines: {value: '57%', completed: 4, opportunities: 7, days: [], previous: {text: '+14 pp'}, yearAgo: {text: '0 pp'}, watchouts: []},
        overview: ['sleep', 'calories', 'weight', 'mood', 'weekendWeight', 'bloodPressure'].map(key => ({key, label: key === 'sleep' ? 'Average sleep with a longer descriptive label' : key, value: '12', detail: 'Saved evidence', status: {label: 'Goal met', color: '#287d4b'}, previous: {text: '+2 units', color: '#63717b'}, yearAgo: {text: 'Not available', color: '#63717b'}})),
        workouts: [{label: 'Strength sessions', current: '3', previous: '6', yearAgo: '0', target: 'Plan unavailable', status: missing, previousTarget: 'Plan unavailable', previousStatus: missing, previousDelta: {text: '-3', color: '#486277'}, yearAgoTarget: 'Plan unavailable', yearAgoStatus: missing, yearAgoDelta: {text: '+3', color: '#486277'}}],
        nutrition: {rows: [], references: []}
    };
    return summary;
}

async function openRefinedReview(page, summary, actionState = {rows: []}) {
    const reads = [], writes = [], errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.clock.install({time: new Date('2026-11-08T23:30:00Z')});
    await page.route('**/api/**', route => {
        const request = route.request(), url = new URL(request.url()), path = url.pathname;
        if (request.method() !== 'GET') writes.push(path);
        if (path === '/api/actions') {
            reads.push(Object.fromEntries(url.searchParams));
            return actionState.error ? route.fulfill({status: 503, body: 'Actions temporarily unavailable'}) : route.fulfill({json: actionState.rows});
        }
        const values = {'/api/auth/me': {authenticated: true, email: 'owner@example.com'}, '/api/profile': {}, '/api/urge-pauses': {pause: null, serverNow: '2026-11-08T23:30:00Z'}, '/api/weekly-summary': {actionConfigured: true, items: [], page: 0, totalPages: 0}, '/api/weekly-summary/preview': {alreadySaved: true, snapshot: summary.snapshot}, ['/api/weekly-summary/' + fridayDate]: summary};
        return route.fulfill({json: values[path] || []});
    });
    await page.addInitScript(date => window.history.replaceState({}, '', '/weekly-summaries/' + date), fridayDate);
    await page.goto('/');
    return {reads, writes, errors};
}

test('weekly refinements keep both tab bars on one scrolling row and reveal keyboard selections', async ({page}, testInfo) => {
    const {errors} = await openRefinedReview(page, refinedReviewFixture());
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        const review = page.getByRole('tablist', {name: 'Weekly review', exact: true});
        await expect.poll(() => review.getByRole('tab').evaluateAll(tabs => new Set(tabs.map(tab => Math.round(tab.getBoundingClientRect().top))).size)).toBe(1);
        await review.getByRole('tab', {name: 'Overview', exact: true}).focus();
        await page.keyboard.press('End');
        await expect(review.getByRole('tab', {name: 'Reflection & actions', exact: true})).toBeFocused();
        await expect(review.getByRole('tab', {name: 'Reflection & actions', exact: true})).toHaveAttribute('aria-selected', 'true');
        await page.keyboard.press('Enter');
        await expect(page.getByRole('tabpanel', {name: 'Reflection & actions', exact: true})).toBeVisible();
        await review.getByRole('tab', {name: 'Measurements', exact: true}).click();
        const measurements = page.getByRole('tablist', {name: 'Measurement groups', exact: true});
        await expect.poll(() => measurements.getByRole('tab').evaluateAll(tabs => new Set(tabs.map(tab => Math.round(tab.getBoundingClientRect().top))).size)).toBe(1);
        await measurements.getByRole('tab').first().focus();
        await page.keyboard.press('End');
        const last = measurements.getByRole('tab', {name: 'Other metrics', exact: true});
        await expect(last).toBeFocused();
        await expect(last).toHaveAttribute('aria-selected', 'true');
        await page.keyboard.press('Enter');
        await expect(last).toHaveAttribute('aria-selected', 'true');
        await expect.poll(() => last.evaluate(el => {const r = el.getBoundingClientRect(); return r.left >= 0 && r.right <= innerWidth;})).toBe(true);
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`weekly-tabs-${width}.png`), animations: 'disabled'});
    }
    expect(errors).toEqual([]);
});

test('weekly refinements keep keyboard-selected middle tabs clear of overflow arrows', async ({page}) => {
    await openRefinedReview(page, refinedReviewFixture());
    await page.setViewportSize({width: 376, height: 900});
    await page.getByRole('button', {name: 'View all routines', exact: true}).click();
    const tabs = page.getByRole('tablist', {name: 'Measurement groups', exact: true});
    for (const key of ['ArrowLeft', 'ArrowLeft', 'ArrowLeft', 'ArrowRight', 'ArrowRight', 'ArrowRight']) {
        await page.keyboard.press(key);
        const tab = tabs.getByRole('tab', {selected: true});
        await expect(tab).toBeFocused();
        await expect.poll(() => tab.evaluate(el => {
            const rect = el.getBoundingClientRect(), container = el.closest('.p-tabview-nav-container'), bounds = container.getBoundingClientRect();
            const previous = container.querySelector('[data-pc-section="previousbutton"]'), next = container.querySelector('[data-pc-section="nextbutton"]');
            return rect.left >= bounds.left + (previous?.getBoundingClientRect().width || 0) - 1 && rect.right <= bounds.right - (next?.getBoundingClientRect().width || 0) + 1;
        })).toBe(true);
    }
});

test('weekly refinements reveal each shortcut after reopening a previously scrolled measurement bar', async ({page}) => {
    await openRefinedReview(page, refinedReviewFixture());
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        for (const [shortcut, name] of [['View all routines', 'Routines'], ['Training details', 'Training & back pain'], ['Comparisons & details', 'Nutrition'], ['Explore measurements', 'Body & pressure']]) {
            await page.getByRole('tab', {name: 'Overview', exact: true}).click();
            await page.getByRole('button', {name: shortcut, exact: true}).click();
            const tab = page.getByRole('tab', {name, exact: true});
            await expect(tab).toBeFocused();
            await expect(tab).toHaveAttribute('aria-selected', 'true');
            await expect.poll(() => tab.evaluate(el => {
                const rect = el.getBoundingClientRect(), container = el.closest('.p-tabview-nav-container'), bounds = container.getBoundingClientRect();
                const previous = container.querySelector('[data-pc-section="previousbutton"]'), next = container.querySelector('[data-pc-section="nextbutton"]');
                return rect.left >= bounds.left + (previous?.getBoundingClientRect().width || 0) - 1 && rect.right <= bounds.right - (next?.getBoundingClientRect().width || 0) + 1;
            })).toBe(true);
        }
    }
});

test('weekly refinements show both overview comparisons with measured heading spacing', async ({page}, testInfo) => {
    await openRefinedReview(page, refinedReviewFixture());
    const overview = page.getByRole('tabpanel', {name: 'Overview', exact: true});
    await expect(overview.getByLabel('Compare with')).toHaveCount(0);
    const cards = overview.locator('.weekly-highlight');
    await expect(cards).toHaveCount(6);
    for (const card of await cards.all()) {
        await expect(card).toContainText('+2 units · vs last week');
        await expect(card).toContainText('Not available · vs 52 weeks ago');
        await expect(card).toContainText('Goal met');
    }
    for (const width of [376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        const section = overview.locator('.detail-section').filter({has: page.getByRole('heading', {name: 'Your week at a glance'})});
        await expect.poll(() => section.evaluate(el => el.querySelector('.weekly-highlights').getBoundingClientRect().top - el.querySelector('.section-heading').getBoundingClientRect().bottom)).toBeGreaterThanOrEqual(12);
        await section.screenshot({path: testInfo.outputPath(`weekly-comparisons-${width}.png`), animations: 'disabled'});
    }
});

for (const legacy of [true, false]) test(`weekly refinements explain ${legacy ? 'legacy' : 'incomplete captured'} workout targets without repeated labels`, async ({page}) => {
    const summary = refinedReviewFixture();
    if (!legacy) {
        summary.snapshot.schemaVersion = 10;
        summary.snapshot.workoutTargets = {currentPeriod: null, previousComparablePeriod: null, yearAgoComparablePeriod: null};
        summary.presentation.visualSummary.workouts[0].status = {label: 'Planned count reached', color: '#287d4b'};
        summary.presentation.visualSummary.workouts[0].target = 'Plan: 3';
    }
    const original = JSON.stringify(summary);
    const {writes} = await openRefinedReview(page, summary);
    for (const label of ['Workout activity overview', 'Weekly workout activity']) {
        if (label.startsWith('Weekly')) await page.getByRole('button', {name: 'Training details', exact: true}).click();
        const table = page.getByRole('table', {name: label, exact: true});
        await expect(table).not.toContainText('Plan unavailable');
        await expect(table).toContainText('3 sessions');
        await expect(table).toContainText('-3 vs last week');
        const panel = page.getByRole('tabpanel', {name: label.startsWith('Weekly') ? 'Training & back pain' : 'Overview', exact: true});
        await expect(panel.getByText('Workout targets weren’t saved with this review.', {exact: true})).toHaveCount(legacy ? 1 : 0);
        await expect(table.getByText('Target unavailable', {exact: true})).toHaveCount(legacy ? 0 : 2);
        if (!legacy) await expect(table).toContainText('Planned count reached');
    }
    expect(JSON.stringify(summary)).toBe(original);
    expect(writes).toEqual([]);
});

test('weekly refinements show all current actions read-only with dated retry and refresh', async ({page}, testInfo) => {
    const actionState = {error: true, rows: Array.from({length: 5}, (_, index) => ({id: index + 1, wording: `Current action ${index + 1}`, status: 'ACTIVE', streak: index, bestStreak: index, targetDays: 14, percentage: index * 100 / 14, startDate: '2026-10-23', checked: false, checkinDates: []}))};
    const {reads, writes} = await openRefinedReview(page, refinedReviewFixture(), actionState);
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    const panel = page.getByRole('region', {name: 'Current actions', exact: true});
    await expect(panel).toContainText('Actions temporarily unavailable');
    actionState.error = false;
    await panel.getByRole('button', {name: 'Retry', exact: true}).click();
    await expect(panel.getByRole('article')).toHaveCount(5);
    await expect(panel).toContainText('09/11/2026');
    await expect(panel).toContainText('4 / 14 consecutive days');
    await expect(panel.getByRole('button', {name: /Complete |Undo |Edit |Stop |Start again/})).toHaveCount(0);
    for (const width of [376, 390, 1280]) { await page.setViewportSize({width, height: 900}); await panel.screenshot({path: testInfo.outputPath(`weekly-current-actions-${width}.png`), animations: 'disabled'}); }
    actionState.rows[0].wording = 'Updated current action';
    await page.getByRole('button', {name: 'Refresh reflection', exact: true}).click();
    await expect(panel).toContainText('Updated current action');
    await panel.getByRole('button', {name: 'Manage actions', exact: true}).click();
    await expect(page).toHaveURL(/\/actions\?date=2026-11-09$/);
    actionState.rows = [];
    await page.goBack();
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    await expect(panel).toContainText('No active actions.');
    expect(reads.length).toBeGreaterThanOrEqual(4);
    expect(reads.every(read => read.date === '2026-11-09' && read.view === 'ACTIVE')).toBe(true);
    expect(writes).toEqual([]);
});

const shiftDate = (date, days) => {
    const value = new Date(`${date}T00:00:00Z`);
    value.setUTCDate(value.getUTCDate() + days);
    return value.toISOString().slice(0, 10);
};
const snapshotForFriday = friday => ({
    ...weeklySnapshot,
    periodStart: shiftDate(friday, -6),
    fridayDate: friday,
    progress: {...weeklySnapshot.progress, ...Object.fromEntries([
        ['currentPeriod', 0], ['previousComparablePeriod', 7], ['yearAgoComparablePeriod', 364]
    ].map(([key, days]) => [key, {...weeklySnapshot.progress[key], startDate: shiftDate(friday, -days - 6), endDate: shiftDate(friday, -days)}]))}
});
savedSummary.snapshot = snapshotForFriday(fridayDate);

test('weekly summary warns before a missing-outcome save and stays usable at target widths', async ({page}, testInfo) => {
    let created = false;
    let firstAttempt = true;
    let releaseCreation;
    const pendingCreation = new Promise(resolve => { releaseCreation = resolve; });
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const json = value => route.fulfill({contentType: 'application/json', body: JSON.stringify(value)});
        if (path === '/api/auth/me') return json({email: 'owner@example.com', displayName: 'Owner', authenticated: true});
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: '2026-08-17T07:00:00Z'});
        if (path === '/api/profile') return json({});
        if (path === '/api/weekly-summary/config') return json({enabled: true, canSend: true, recipientEmail: 'owner@example.com', deliveryDay: 'MONDAY', deliveryTime: '08:00:00', reminderEnabled: true, reminderDay: 'MONDAY', reminderTime: '08:00:00', timeZone: 'Europe/Madrid'});
        if (path === '/api/weekly-summary') return json({latestEligibleFriday: fridayDate, actionConfigured: true, items: created ? [{periodStart: savedSummary.periodStart, fridayDate, createdAt: savedSummary.createdAt, reflectionSaved: false}] : [], page: 0, size: 10, totalElements: created ? 1 : 0, totalPages: created ? 1 : 0});
        if (path === '/api/weekly-summary/preview') return json({periodStart: '2026-08-08', fridayDate, canCreate: !created, alreadySaved: created, snapshot: weeklySnapshot});
        if (path === `/api/weekly-summary/${fridayDate}`) return json(savedSummary);
        if (path === '/api/weekly-summary/create' && request.method() === 'POST') {
            if (firstAttempt) {
                firstAttempt = false;
                await pendingCreation;
                return route.fulfill({status: 503, body: 'Summary service unavailable'});
            }
            created = true;
            return json(savedSummary);
        }
        return json([]);
    });

    await page.addInitScript(() => window.history.replaceState({}, '', '/weekly-summaries'));
    await page.goto('/');
    await expect(page.getByRole('heading', {name: 'Weekly summaries'})).toBeVisible();
    await expect(page.getByText('No weight measurement was recorded Friday, Saturday, or Sunday.')).toBeVisible();
    await expect(page.getByText('Available from Saturday. Saving fixes the summary; later measurements won’t be included.')).toBeVisible();
    await expect(page.getByRole('button', {name: 'Create weekly summary'})).toBeEnabled();

    for (const width of [320, 376, 390, 640, 760, 761, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }

    for (const width of [320, 376, 390, 640, 760, 761, 1280]) {
        await page.setViewportSize({width, height: 900});
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-preview-${width}.png`), fullPage: true});
    }
    await page.getByRole('button', {name: 'Create weekly summary'}).click();
    const confirm = page.getByRole('dialog');
    await expect(confirm).toContainText('Save the available evidence with the missing-data warnings?');
    await expect(confirm.getByRole('button', {name: 'Save anyway'})).toBeEnabled();
    await page.setViewportSize({width: 320, height: 900});
    await confirm.screenshot({animations: 'disabled', path: testInfo.outputPath('weekly-ready-to-save-320.png')});
    await confirm.getByRole('button', {name: 'Save anyway'}).click();
    await expect(confirm.getByRole('button', {name: 'Saving…'})).toBeDisabled();
    await expect(confirm.getByRole('button', {name: 'Cancel'})).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(confirm).toBeVisible();
    for (const width of [320, 376, 390, 640, 760, 761, 1280]) {
        await page.setViewportSize({width, height: 900});
        await confirm.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-pending-${width}.png`)});
    }
    releaseCreation();
    await expect(confirm).toContainText('Summary service unavailable');
    await expect(confirm.getByRole('button', {name: 'Cancel'})).toBeEnabled();
    for (const width of [320, 376, 390, 640, 760, 761, 1280]) {
        await page.setViewportSize({width, height: 900});
        await confirm.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-error-${width}.png`)});
    }
    await confirm.getByRole('button', {name: 'Save anyway'}).click();
    await expect(confirm).toBeHidden();
    await expect(page.getByRole('heading', {name: '8 Aug 2026 – 14 Aug 2026'})).toBeVisible();
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Add reflection'})).toBeVisible();
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await expect(page.getByRole('table', {name: 'Weekly recorded metrics'}).getByRole('row', {name: /Fat mass/})).toContainText('14.0 kg');
    await page.getByRole('tab', {name: 'Nutrition', exact: true}).click();
    await expect(page.getByRole('table', {name: 'Weekly nutrition evidence'}).getByRole('row', {name: /Protein/})).toContainText('100.0 g/day');
    await page.getByRole('tab', {name: 'Sleep & mood', exact: true}).click();
    await expect(page.getByRole('table', {name: 'Sleep and mood measurements'}).getByRole('row', {name: /Deep sleep/})).toContainText('1.5 h');
    await expect(page.getByRole('row', {name: /Mood 3.5/})).toContainText('2 days');
    await page.getByRole('tab', {name: 'Other metrics', exact: true}).click();
    await expect(page.getByText(/Flexibility 75% · Mind 50%/)).toBeVisible();
    await page.getByRole('tab', {name: 'Routines', exact: true}).click();
    await expect(page.getByText('Below 60%', {exact: true})).toBeVisible();
    for (const width of [320, 376, 390, 640, 760, 761, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await expect(page.getByRole('heading', {name: '8 Aug 2026 – 14 Aug 2026'})).toBeVisible();
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-detail-${width}.png`), fullPage: true});
    }
    await page.getByRole('button', {name: 'Back to weekly summaries'}).click();
    await expect(page.getByRole('button', {name: 'Open latest summary'})).toBeEnabled();
    await expect(page.getByRole('button', {name: 'Create weekly summary'})).toBeHidden();
    await page.getByRole('button', {name: 'Open latest summary'}).click();
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Add reflection'})).toBeVisible();
});

test('weekly summaries separate archive and compact detail, distinguish averages, and refetch reflection', async ({page}, testInfo) => {
    const fridayDate = '2026-10-02';
    const snapshot = snapshotForFriday(fridayDate);
    let detailReads = 0;
    let updated = false;
    const section = {summary: 'Recorded progress with limited coverage.', nextAction: 'Review the recorded evidence next week.'};
    const summary = {...savedSummary, fridayDate, periodStart: snapshot.periodStart, createdAt: '2026-10-05T06:00:00Z', snapshot: {...snapshot,
        progress: {...snapshot.progress, currentPeriod: {...snapshot.progress.currentPeriod, weight: {...snapshot.progress.currentPeriod.weight, weightKg: 70.4}}},
        warnings: [], outcomes: {
        weight: {measuredDate: '2026-10-03', weightKg: 69.1, fatPercentage: 20, fatKg: 13.82, muscleKg: 52, musclePercentage: 75.3},
        bloodPressure: {measuredDate: '2026-10-04', systolic: 118, diastolic: 78}
    }}, reflection: {fridayDate, generatedAt: '2026-10-05T08:00:00Z', model: 'ChatGPT', title: 'Weekly progress', summary: 'A separate weekly review of recorded evidence.', bodyComposition: section, bloodPressure: section, routines: section, nutrition: section, trainingRecovery: section, goalProgress: section, nextWeekActions: ['Continue recording comparable evidence.']}};
    summary.presentation = {...savedSummary.presentation, recommendations: [
        {sourceKey: 'weekly:42:' + 'a'.repeat(64), wording: 'Continue recording comparable evidence.', provenance: ['Next week']},
        {sourceKey: 'weekly:42:' + 'b'.repeat(64), wording: 'Review the recorded evidence next week.', provenance: ['Body composition', 'Blood pressure', 'Routines', 'Nutrition', 'Training and recovery', 'Goal progress']}
    ]};
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        if (path === `/api/weekly-summary/${fridayDate}`) {
            detailReads++;
            return route.fulfill({json: updated ? {...summary, reflection: {...summary.reflection, title: 'Updated weekly progress', summary: 'The newly saved reflection is now visible.'}} : summary});
        }
        const values = {
            '/api/auth/me': {email: 'owner@example.com', displayName: 'Owner', authenticated: true},
            '/api/urge-pauses': {pause: null, serverNow: '2026-08-17T07:00:00Z'},
            '/api/profile': {},
            '/api/weekly-summary': {latestEligibleFriday: fridayDate, actionConfigured: true, items: [{periodStart: summary.periodStart, fridayDate, createdAt: summary.createdAt, reflectionSaved: true}], page: 0, size: 10, totalElements: 1, totalPages: 1},
            '/api/weekly-summary/preview': {periodStart: summary.periodStart, fridayDate, canCreate: false, alreadySaved: true, snapshot: summary.snapshot},
            [`/api/weekly-summary/${fridayDate}`]: summary
        };
        return route.fulfill({json: values[path] || []});
    });
    await page.addInitScript(() => window.history.replaceState({}, '', '/weekly-summaries'));
    await page.goto('/');
    const archive = page.getByRole('complementary', {name: 'Saved weekly summaries'});
    const detail = page.getByRole('region', {name: 'Weekly summary details'});
    await expect(archive).toBeVisible();
    await expect(detail).toHaveCount(0);
    await expect(page.getByRole('heading', {name: 'Weekly progress'})).toHaveCount(0);
    expect(detailReads).toBe(0);
    for (const width of [320, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-archive-${width}.png`), fullPage: true});
    }
    await archive.getByRole('button', {name: /26 Sep 2026 – 2 Oct 2026/}).click();
    await expect(page).toHaveURL(/\/weekly-summaries\/2026-10-02$/);
    await expect(archive).toHaveCount(0);
    await expect(detail).toBeVisible();
    await expect(detail.getByRole('heading', {name: 'Your week at a glance'})).toBeVisible();
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    await expect(detail.getByRole('heading', {name: 'Weekly progress'})).toBeVisible();
    await expect(page.getByRole('tabpanel', {name: 'Reflection & actions', exact: true}).getByText('A separate weekly review of recorded evidence.')).toBeVisible();
    await expect(detail.getByText('Continue recording comparable evidence.')).toBeVisible();
    await expect(detail.getByRole('link', {name: 'Activate action'})).toHaveCount(1);
    await detail.getByText('More recommendations · 1', {exact: true}).click();
    await expect(detail.getByRole('link', {name: 'Activate action'})).toHaveCount(2);
    await expect(detail.getByText('Review the recorded evidence next week.', {exact: true})).toHaveCount(1);
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await expect(detail.getByText('69.1 kg · 3 Oct 2026')).toBeVisible();
    await expect(detail.getByText('118 / 78 mmHg · 4 Oct 2026')).toBeVisible();
    await expect(detail.getByText('2 Oct 2026 – 4 Oct 2026', {exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Update reflection'})).toBeVisible();
    for (const width of [320, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-compact-detail-${width}.png`), fullPage: true});
    }
    const metricsToggle = page.getByRole('tab', {name: 'Measurements', exact: true});
    await metricsToggle.focus();
    await page.keyboard.press('Enter');
    const metrics = page.getByRole('table', {name: 'Weekly recorded metrics'});
    await expect(metrics).toBeVisible();
    await expect(metrics.getByRole('columnheader', {name: /This week.*26 Sep 2026 – 2 Oct 2026/})).toBeVisible();
    await expect(metrics.getByRole('columnheader', {name: /Last week.*19 Sep 2026 – 25 Sep 2026/})).toBeVisible();
    await expect(metrics.getByRole('row', {name: /Average weight/})).toContainText('70.4 kg');
    await expect(metrics.getByRole('row', {name: /Average weight/})).toContainText('2 readings');
    await expect(metrics.getByText('Not recorded', {exact: true}).first()).toBeVisible();
    for (const width of [320, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-metrics-${width}.png`), fullPage: true});
    }
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    const reflectionToggle = detail.locator('summary').filter({hasText: 'Reflection details'});
    await reflectionToggle.focus();
    await page.keyboard.press('Space');
    await expect(detail.getByRole('heading', {name: 'Body composition', exact: true})).toBeVisible();
    await expect(detail.getByText(section.summary, {exact: true})).toHaveCount(6);
    updated = true;
    const readsBeforeRefresh = detailReads;
    await detail.getByRole('button', {name: 'Refresh reflection'}).click();
    await expect(detail.getByRole('heading', {name: 'Updated weekly progress'})).toBeVisible();
    await expect(page.getByRole('tabpanel', {name: 'Reflection & actions', exact: true}).getByText('The newly saved reflection is now visible.')).toBeVisible();
    expect(detailReads).toBe(readsBeforeRefresh + 1);
    await expect(page).toHaveURL(/\/weekly-summaries\/2026-10-02$/);
});

test('weekly archive opens standalone summaries, copies selected evidence windows, and redirects legacy links', async ({page, context}) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route('https://chatgpt.test/**', route => route.fulfill({body: '<title>Coach</title>'}));
    const dateLabel = value => {
        const [year, month, day] = value.split('-');
        return `${Number(day)} ${['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][Number(month) - 1]} ${year}`;
    };
    const summaries = Array.from({length: 25}, (_, index) => {
        const end = new Date(Date.UTC(2026, 8, 25 - index * 7));
        const start = new Date(end.getTime() - 6 * 24 * 60 * 60 * 1000);
        const fridayDate = end.toISOString().slice(0, 10);
        const periodStart = start.toISOString().slice(0, 10);
        return {...savedSummary, fridayDate, periodStart, snapshot: {...snapshotForFriday(fridayDate), backPain: {
            checkInCount: 3, episodeCount: 2, painDayCount: 2,
            episodesBySeverity: {MILD: 1, MODERATE: 1}, episodesByRegion: {LOWER: 2}, episodesBySide: {LEFT: 1, CENTER: 1}
        }}};
    });
    const byDate = new Map(summaries.map(summary => [summary.fridayDate, summary]));
    await page.route('**/weekly-summaries**', async route => {
        const response = await route.fetch({url: new URL('/', route.request().url()).href});
        await route.fulfill({response});
    });
    await page.route('**/api/**', route => {
        const url = new URL(route.request().url());
        const path = url.pathname;
        const json = value => route.fulfill({contentType: 'application/json', body: JSON.stringify(value)});
        if (path === '/api/auth/me') return json({email: 'owner@example.com', displayName: 'Owner', authenticated: true});
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: '2026-08-17T07:00:00Z'});
        if (path === '/api/profile') return json({});
        if (path === '/api/weekly-summary/preview') {
            const summary = summaries[0];
            return json({periodStart: summary.periodStart, fridayDate: summary.fridayDate, canCreate: false, alreadySaved: true, snapshot: summary.snapshot});
        }
        if (path === '/api/weekly-summary') {
            const page = Number(url.searchParams.get('page') || 0);
            const size = Number(url.searchParams.get('size') || 10);
            const selectedDate = url.searchParams.get('selectedFridayDate');
            const selectedIndex = summaries.findIndex(summary => summary.fridayDate === selectedDate);
            const actualPage = selectedIndex >= 0 ? Math.floor(selectedIndex / size) : page;
            return json({
                latestEligibleFriday: summaries[0].fridayDate,
                actionConfigured: true,
                items: summaries.slice(actualPage * size, actualPage * size + size).map(({periodStart, fridayDate, createdAt}) => ({periodStart, fridayDate, createdAt, reflectionSaved: false})),
                page: actualPage, size, totalElements: summaries.length, totalPages: Math.ceil(summaries.length / size)
            });
        }
        if (path.startsWith('/api/weekly-summary/')) return json(byDate.get(path.slice('/api/weekly-summary/'.length)) || {});
        return json([]);
    });

    await page.goto('/weekly-summaries');
    const archive = page.getByRole('complementary', {name: 'Saved weekly summaries'});
    const detail = page.getByRole('region', {name: 'Weekly summary details'});
    const copySelectedPrompt = async summary => {
        const coachPagePromise = context.waitForEvent('page');
        await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
        await detail.getByRole('button', {name: 'Add reflection'}).click();
        const coachPage = await coachPagePromise;
        await expect(coachPage).toHaveTitle('Coach');
        await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
            .toContain(`summary ending ${summary.fridayDate} (${summary.periodStart} to ${summary.fridayDate})`);
        const prompt = await page.evaluate(() => navigator.clipboard.readText());
        expect(prompt).toContain(`Friday–Sunday window ${summary.fridayDate} to ${shiftDate(summary.fridayDate, 2)}`);
        expect(prompt).toMatch(/Saturday–Friday average.*period dates and reading count/);
        expect(prompt).toMatch(/outcomes as readings with their measured dates/);
        expect(prompt).toContain('review every current Coach warning using today');
        expect(prompt).toContain('rolling window, current metric and goal');
        expect(prompt).toContain('confirm the exact reflection and warningReview before saving them together');
        expect(prompt).toContain('Make no writes before my confirmation.');
        await coachPage.close();
        return prompt;
    };
    await expect(detail).toHaveCount(0);
    await expect(archive.getByText('1–10 of 25 weeks')).toBeVisible();
    await archive.getByRole('button', {name: 'Next Page'}).click();
    await expect(archive.getByText('11–20 of 25 weeks')).toBeVisible();
    await archive.getByRole('button', {name: 'Next Page'}).click();
    await expect(archive.getByText('21–25 of 25 weeks')).toBeVisible();

    const oldest = summaries[24];
    await archive.getByRole('button', {name: new RegExp(dateLabel(oldest.fridayDate))}).click();
    await expect(page).toHaveURL(new RegExp(`/weekly-summaries/${oldest.fridayDate}$`));
    await expect(archive).toBeHidden();
    await expect(detail.locator('details[open]')).toHaveCount(0);
    const oldestPrompt = await copySelectedPrompt(oldest);
    expect(oldestPrompt).not.toContain(summaries[0].fridayDate);
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await page.getByRole('tab', {name: 'Training & back pain', exact: true}).click();
    await expect(detail).toContainText('2 pain episodes across 2 days');
    await expect(detail).toContainText('Severity');
    await expect(detail).toContainText('mild: 1 · moderate: 1');
    const metricsToggle = page.getByRole('tab', {name: 'Body & pressure', exact: true});
    await expect(metricsToggle).toBeVisible();
    await metricsToggle.click();
    await expect(page.getByRole('table', {name: 'Weekly recorded metrics'})).toBeVisible();
    await page.getByRole('tab', {name: 'Overview', exact: true}).click();
    await expect(page.getByRole('table', {name: 'Weekly recorded metrics'})).toBeHidden();

    await page.getByRole('button', {name: 'Back to weekly summaries'}).click();
    await expect(archive).toBeVisible();
    await expect(archive.getByText('1–10 of 25 weeks')).toBeVisible();
    await archive.getByRole('button', {name: new RegExp(dateLabel(summaries[0].fridayDate))}).click();
    await expect(page).toHaveURL(new RegExp(`/weekly-summaries/${summaries[0].fridayDate}$`));
    await expect(detail.locator('details[open]')).toHaveCount(0);
    const latestPrompt = await copySelectedPrompt(summaries[0]);
    expect(latestPrompt).not.toContain(oldest.fridayDate);
    await page.goto(`/weekly-summaries?date=${oldest.fridayDate}`);
    await expect(page).toHaveURL(new RegExp(`/weekly-summaries/${oldest.fridayDate}$`));
    await expect(archive).toBeHidden();
    await expect(detail.locator('details[open]')).toHaveCount(0);
    await expect(detail).toContainText(dateLabel(oldest.periodStart));
});

test('weekly summary reminder opens its exact week and keeps creation explicit', async ({page}) => {
    const reminderFriday = '2026-08-14';
    const createRequests = [];
    const requestedUrls = [];
    const pageErrors = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    let created = false;
    await page.route('**/weekly-summaries**', async route => {
        const response = await route.fetch({url: new URL('/', route.request().url()).href});
        await route.fulfill({response});
    });
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        requestedUrls.push(url.pathname + url.search);
        const path = url.pathname;
        const json = value => route.fulfill({contentType: 'application/json', body: JSON.stringify(value)});
        if (path === '/api/auth/me') return json({email: 'owner@example.com', displayName: 'Owner', authenticated: true});
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: '2026-08-25T07:00:00Z'});
        if (path === '/api/profile') return json({});
        if (path === '/api/weekly-summary/config') return json({enabled: true, canSend: true, recipientEmail: 'owner@example.com', deliveryDay: 'MONDAY', deliveryTime: '08:00:00', reminderEnabled: true, reminderDay: 'MONDAY', reminderTime: '08:00:00', timeZone: 'Europe/Madrid'});
        if (path === '/api/weekly-summary/preview') {
            const fridayDate = url.searchParams.get('selectedFridayDate') || '2026-08-21';
            return json({periodStart: shiftDate(fridayDate, -6), fridayDate, canCreate: true, alreadySaved: false, snapshot: snapshotForFriday(fridayDate)});
        }
        if (path === '/api/weekly-summary/create' && request.method() === 'POST') {
            createRequests.push(url.searchParams.get('selectedFridayDate'));
            created = true;
            return json(savedSummary);
        }
        if (path === '/api/weekly-summary') return json({latestEligibleFriday: '2026-08-21', actionConfigured: true, items: created ? [{periodStart: savedSummary.periodStart, fridayDate: reminderFriday, createdAt: savedSummary.createdAt, reflectionSaved: false}] : [], page: 0, size: 10, totalElements: created ? 1 : 0, totalPages: created ? 1 : 0});
        if (path === `/api/weekly-summary/${reminderFriday}`) return json(savedSummary);
        return json([]);
    });
    await page.addInitScript(path => window.history.replaceState({}, '', path), `/weekly-summaries?selectedFridayDate=${reminderFriday}`);
    await page.goto('/');

    await expect(page.locator('.preview-card')).toBeVisible();
    expect(requestedUrls).toContain(`/api/weekly-summary/preview?selectedFridayDate=${reminderFriday}`);
    expect(pageErrors).toEqual([]);
    await expect(page.getByText('Reminder week', {exact: true})).toBeVisible();
    await expect(page.locator('.preview-heading h2')).toContainText('14 Aug 2026');
    await expect(page.getByRole('button', {name: 'Create weekly summary', exact: true})).toBeVisible();
    expect(createRequests).toEqual([]);

    await page.getByRole('button', {name: 'Create weekly summary', exact: true}).click();
    const confirm = page.getByRole('dialog', {name: 'Save with missing outcome measurements?', exact: true});
    await expect(confirm).toBeVisible();
    await confirm.getByRole('button', {name: 'Cancel', exact: true}).click();
    expect(createRequests).toEqual([]);

    await page.getByRole('button', {name: 'Create weekly summary', exact: true}).click();
    await confirm.getByRole('button', {name: 'Save anyway', exact: true}).click();
    await expect(page).toHaveURL(new RegExp(`/weekly-summaries/${reminderFriday}$`));
    expect(createRequests).toEqual([reminderFriday]);
});

test('saved workout activity compares overlapping categories and exact cardio minutes without inventing legacy zeros', async ({page}, testInfo) => {
    const snapshot = snapshotForFriday(fridayDate);
    snapshot.progress.currentPeriod.workouts = {...snapshot.progress.currentPeriod.workouts, workoutCount: 3, strengthSessionCount: 2, cardioSessionCount: 2, stretchingSessionCount: 2, activeCardioSeconds: 90, cardioDurationComplete: false, totalDistanceKm: 2.4, distanceReadingCount: 1, totalCalories: 180, calorieReadingCount: 1, strengthVolumeKg: 240, strengthSetCount: 3};
    snapshot.progress.previousComparablePeriod.workouts = {...snapshot.progress.previousComparablePeriod.workouts, strengthSessionCount: 1, cardioSessionCount: 0, stretchingSessionCount: 0, activeCardioSeconds: 30, cardioDurationComplete: true};
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.route('**/weekly-summaries**', async route => {
        const response = await route.fetch({url: new URL('/', route.request().url()).href});
        await route.fulfill({response});
    });
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        const json = value => route.fulfill({contentType: 'application/json', body: JSON.stringify(value)});
        if (path === '/api/auth/me') return json({email: 'owner@example.com', displayName: 'Owner', authenticated: true});
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: '2026-08-17T07:00:00Z'});
        if (path === '/api/profile') return json({});
        if (path === '/api/weekly-summary') return json({actionConfigured: false, items: [], page: 0, totalPages: 0, totalElements: 0});
        if (path === '/api/weekly-summary/preview') return json({alreadySaved: true, snapshot});
        if (path === `/api/weekly-summary/${fridayDate}`) return json({...savedSummary, snapshot});
        return json([]);
    });
    await page.goto(`/weekly-summaries/${fridayDate}`);
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await page.getByRole('tab', {name: 'Training & back pain', exact: true}).click();
    const activity = page.getByRole('rowgroup', {name: 'Workout activity'});
    await expect(activity).toBeVisible();
    const strength = activity.getByRole('row').filter({has: page.getByRole('rowheader', {name: 'Strength sessions', exact: true})});
    await expect(strength).toContainText('2 sessions');
    await expect(strength).toContainText('+1 session vs last week');
    await expect(strength).toContainText('Not available');
    const cardio = activity.getByRole('row').filter({has: page.getByRole('rowheader', {name: 'Active cardio minutes', exact: true})});
    await expect(cardio).toContainText('1.5 min · Partial coverage');
    await expect(cardio).toContainText('0.5 min');
    await expect(cardio).toContainText('Not available vs last week');
    await expect(activity.getByRole('row').filter({has: page.getByRole('rowheader', {name: 'Cardio sessions', exact: true})})).toContainText('0 sessions');
    await expect(page.getByRole('table', {name: 'Workout measurements', exact: true})).toContainText('2.4 km');
    await expect(activity.getByRole('row').filter({has: page.getByRole('rowheader', {name: 'Cardio sessions', exact: true})}).getByRole('cell').first()).toContainText('2 sessions');
    await expect(activity.getByRole('row').filter({has: page.getByRole('rowheader', {name: 'Stretching sessions', exact: true})}).getByRole('cell').first()).toContainText('2 sessions');
    await expect(page.getByRole('table', {name: 'Weekly workout activity'})).not.toContainText('6 sessions');
    for (const width of [320, 376, 390, 640, 760, 761, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await expect(activity).toBeVisible();
        const cell = strength.getByRole('cell').first();
        const periodLabel = await cell.evaluate(element => getComputedStyle(element, '::before').content);
        if (width <= 760) expect(periodLabel).toContain('This week');
        else expect(periodLabel).toBe('none');
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-workout-activity-${width}.png`), fullPage: true});
    }
    await page.setViewportSize({width: 390, height: 900});
    snapshot.progress.currentPeriod.workouts = {...snapshot.progress.yearAgoComparablePeriod.workouts};
    await page.reload();
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await page.getByRole('tab', {name: 'Training & back pain', exact: true}).click();
    await expect(activity.getByRole('row').filter({has: page.getByRole('rowheader', {name: 'Strength sessions', exact: true})}).getByRole('cell').first()).toHaveText('Not available');
    await page.screenshot({animations: 'disabled', path: testInfo.outputPath('weekly-workout-legacy-390.png'), fullPage: true});
    expect(errors).toEqual([]);
});

test('new weekly nutrition renders captured coverage and targets without fetching live settings', async ({page}) => {
    const captured = {...savedSummary, snapshot: {...savedSummary.snapshot, nutrition: {
        capturedAt: '2026-08-17T06:00:00Z', targetLabel: 'Current targets captured when saved'
    }}, presentation: {...savedSummary.presentation, nutritionRows: [
        {label: 'EPA+DHA', current: '250 mg · 1 complete day · 3/4 foods covered · 2 estimated · Current target ≥250 mg',
            previous: 'Not available · 0 complete days · Current target ≥250 mg', yearAgo: 'Not available · 0 complete days · Current target ≥250 mg'},
        {label: 'ALA', current: 'Not available · 0 complete days · Current target ≥1600 mg',
            previous: 'Not available · 0 complete days · Current target ≥1600 mg', yearAgo: 'Not available · 0 complete days · Current target ≥1600 mg'}
    ]}};
    let liveTargetReads = 0;
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        if (path.startsWith('/api/nutrition/targets') || path.startsWith('/api/weekly-calorie-plans')) liveTargetReads++;
        const values = {
            '/api/auth/me': {authenticated: true, email: 'owner@example.com'},
            '/api/profile': {}, '/api/urge-pauses': {pause: null, serverNow: '2026-08-17T07:00:00Z'},
            '/api/weekly-summary': {actionConfigured: true, items: [], page: 0, size: 10, totalElements: 0, totalPages: 0},
            '/api/weekly-summary/preview': {periodStart: captured.periodStart, fridayDate, alreadySaved: true, snapshot: captured.snapshot},
            ['/api/weekly-summary/' + fridayDate]: captured
        };
        return route.fulfill({json: values[path] || []});
    });
    await page.addInitScript(date => window.history.replaceState({}, '', '/weekly-summaries/' + date), fridayDate);
    await page.goto('/');
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await page.getByRole('tab', {name: 'Nutrition', exact: true}).click();
    const table = page.getByRole('table', {name: 'Weekly nutrition evidence'});
    await expect(table.getByText('250 mg · 1 complete day · 3/4 foods covered · 2 estimated · Current target ≥250 mg')).toBeVisible();
    await expect(table.getByText('Not available · 0 complete days · Current target ≥1600 mg')).toHaveCount(3);
    await expect(page.locator('canvas')).toHaveCount(0);
    expect(liveTargetReads).toBe(0);
});


test('weekly reflection shows immutable rolling warning snapshots and preserves legacy and empty reviews', async ({page}, testInfo) => {
    const section = {summary: 'Recorded progress.', nextAction: 'Keep recording.'};
    const summary = {...savedSummary, reflection: {
        fridayDate, generatedAt: '2026-10-26T09:15:00Z', title: 'Weekly reflection with warning goals', summary: 'Saved weekly review.',
        bodyComposition: section, bloodPressure: section, routines: section, nutrition: section, trainingRecovery: section, goalProgress: section,
        nextWeekActions: ['Keep recording.'], warningReview: {reviewedDate: '2026-10-26', items: [sleepWarning(), nutritionWarning()]}
    }};
    const writes = [];
    await page.route('**/api/**', route => {
        const request = route.request();
        if (request.method() !== 'GET') writes.push(request.method());
        const path = new URL(request.url()).pathname;
        const responses = {
            '/api/auth/me': {email: 'owner@example.com', displayName: 'Owner', authenticated: true},
            '/api/urge-pauses': {pause: null, serverNow: '2026-10-26T09:15:00Z'},
            '/api/profile': {},
            '/api/weekly-summary': {latestEligibleFriday: fridayDate, actionConfigured: true, items: [{periodStart: summary.periodStart, fridayDate, createdAt: summary.createdAt, reflectionSaved: true}], page: 0, size: 10, totalElements: 1, totalPages: 1},
            '/api/weekly-summary/preview': {periodStart: summary.periodStart, fridayDate, canCreate: false, alreadySaved: true, snapshot: summary.snapshot},
            [`/api/weekly-summary/${fridayDate}`]: summary
        };
        return route.fulfill({json: responses[path] || []});
    });
    await page.addInitScript(friday => window.history.replaceState({}, '', `/weekly-summaries/${friday}`), fridayDate);
    await page.goto('/');
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    const review = page.getByRole('region', {name: 'Warning progress'});
    await expect(review.getByRole('article')).toHaveCount(2);
    await expect(review).toContainText(/Triggered 26 Sept? 2026/);
    await expect(review).toContainText('Triggered 1 Oct 2026');
    await expect(review.getByRole('row', {name: /Asleep/})).toContainText('42h');
    await expect(review.getByRole('row', {name: /In bed/})).toContainText('56h 59m');
    await expect(review.getByRole('row', {name: /Calories/})).toContainText('2,882.71');
    await expect(review.getByRole('article').first()).toContainText('Goals met');
    await expect(review.getByRole('article').last()).toContainText('Goals not met');
    await expect(review.locator('details')).toHaveCount(0);
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await review.scrollIntoViewIfNeeded();
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        await page.screenshot({path: testInfo.outputPath(`weekly-warning-progress-${width}.png`), animations: 'disabled'});
    }
    // Reopen the saved route through the static test server's SPA entry document.
    await page.goto('/');
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    await expect(review.getByRole('row', {name: /Asleep/})).toContainText('42h');
    await expect(review.getByRole('row', {name: /Calories/})).toContainText('2,882.71');
    summary.reflection.warningReview = null;
    await page.getByRole('button', {name: 'Refresh reflection'}).click();
    await expect(review).toContainText('Not recorded');
    summary.reflection.warningReview = {reviewedDate: '2026-10-26', items: []};
    await page.getByRole('button', {name: 'Refresh reflection'}).click();
    await expect(review).toContainText('No active warnings to review');
    await expect(review.getByRole('article')).toHaveCount(0);
    expect(writes).toEqual([]);
});

test('weekly review starts with routines and separates searchable measurements from saved priorities', async ({page}, testInfo) => {
    const summary = structuredClone(savedSummary);
    summary.snapshot.routines = Array.from({length: 5}, (_, index) => ({name: `Routine ${index + 1}`, completedDays: index, eligibleDays: 7, percentage: index * 100 / 7}));
    summary.snapshot.weeklyRoutines = [{name: 'Weekly check', completedWeeks: 1, eligibleWeeks: 1, percentage: 100, checkinDates: ['2026-08-14']}];
    summary.presentation.recommendations = Array.from({length: 5}, (_, index) => ({sourceKey: `weekly:42:${String(index).repeat(64)}`, wording: `Saved priority ${index + 1}`, provenance: [index < 4 ? 'Next week' : 'Nutrition']}));
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        const values = {
            '/api/auth/me': {authenticated: true, email: 'owner@example.com'}, '/api/profile': {},
            '/api/urge-pauses': {pause: null, serverNow: '2026-08-17T07:00:00Z'},
            '/api/weekly-summary': {actionConfigured: true, items: [], page: 0, totalPages: 0, totalElements: 0},
            '/api/weekly-summary/preview': {alreadySaved: true, snapshot: summary.snapshot},
            ['/api/weekly-summary/' + fridayDate]: summary
        };
        return route.fulfill({json: values[path] || []});
    });
    await page.addInitScript(date => window.history.replaceState({}, '', '/weekly-summaries/' + date), fridayDate);
    await page.goto('/');
    const overview = page.getByRole('tabpanel', {name: 'Overview', exact: true});
    await expect(overview.getByRole('heading', {name: 'Daily routines', exact: true})).toBeVisible();
    await expect(overview.locator('.routine-attention')).toHaveCount(3);
    await expect(page.getByRole('tab', {name: 'Overview', exact: true})).toHaveAttribute('aria-selected', 'true');
    await page.getByRole('tab', {name: 'Overview', exact: true}).focus();
    await page.keyboard.press('ArrowRight');
    await expect(page.getByRole('tab', {name: 'Measurements', exact: true})).toHaveAttribute('aria-selected', 'true');
    await page.getByRole('tab', {name: 'Routines', exact: true}).click();
    await page.getByRole('textbox', {name: 'Find a routine'}).fill('Routine 5');
    const routines = page.getByRole('tabpanel', {name: 'Routines', exact: true});
    await expect(routines.getByText('Routine 5', {exact: true})).toBeVisible();
    await expect(routines.getByText('Routine 1', {exact: true})).toHaveCount(0);
    await expect(routines.getByText('Weekly check', {exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'Reflection & actions', exact: true}).click();
    const reflection = page.getByRole('tabpanel', {name: 'Reflection & actions', exact: true});
    await expect(reflection.getByText('Saved priority 3', {exact: true})).toBeVisible();
    await expect(reflection.getByText('Saved priority 4', {exact: true})).toBeHidden();
    await reflection.getByText('More recommendations · 2', {exact: true}).click();
    await expect(reflection.getByText('Saved priority 4', {exact: true})).toBeVisible();
    await expect(reflection.getByRole('link', {name: 'Activate action'}).first()).toHaveAttribute('href', '/actions?source=weekly:42:' + '0'.repeat(64));
    for (const width of [376, 390, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-review-priorities-${width}.png`), fullPage: true});
    }
});

test('weekly saved classifications and compact nutrient references remain separate from neutral weight changes', async ({page}, testInfo) => {
    const summary = structuredClone(savedSummary);
    const good = {label: 'Goal met', color: '#287d4b'};
    const below = {label: 'Below goal', color: '#b33b35'};
    const neutral = {label: 'Reference only', color: '#63717b'};
    summary.snapshot.emailGoals = {rules: {version: 2, sleepMinimumSeconds: 21600, bodyFatStages: [{label: 'EXCELLENT', threshold: 13, color: '#287d4b'}], sleepStages: [{label: 'EXCELLENT', threshold: 4, color: '#287d4b'}, {label: 'FAIR', threshold: 2, color: '#63717b'}], bloodPressureStages: [{label: 'NORMAL', systolicAbove: null, diastolicAbove: null, color: '#287d4b'}], moodGoodMinimum: 3, moodNeutralMinimum: 2}};
    summary.snapshot.progress.currentPeriod.sleep.statusScore = 4;
    const nutrient = (key, label, current, target, status) => ({key, label, current, target, status, previous: 'Not available', previousTarget: 'Unavailable', previousStatus: {label: 'Unavailable', color: '#63717b'}, previousDelta: {text: 'Not available', color: '#63717b'}, yearAgo: 'Not available', yearAgoTarget: 'Unavailable', yearAgoStatus: {label: 'Unavailable', color: '#63717b'}, yearAgoDelta: {text: 'Not available', color: '#63717b'}, coverage: '7 complete days'});
    summary.presentation.visualSummary = {
        routines: {value: '57%', completed: 4, opportunities: 7, days: [], previous: {text: '+14 pp'}, yearAgo: {text: '0 pp'}, watchouts: [], watchoutCount: 0}, overview: [], workouts: [{label: 'Strength sessions', current: '2', previous: '1', yearAgo: 'Not available', target: 'Plan: 2', status: {label: 'Planned count reached', color: '#287d4b'}, previousDelta: {text: '+1', color: '#63717b'}, yearAgoDelta: {text: 'Not available', color: '#63717b'}}],
        nutrition: {note: 'Saved targets', rows: [nutrient('protein', 'Protein', '138.2 g', '≥115 g', good), nutrient('carbohydrate', 'Carbs', '310.6 g', '≤293.2 g', {label: 'Above goal', color: '#b33b35'}), nutrient('fat', 'Fat', '81.2 g', '≤77.8 g', {label: 'Above goal', color: '#b33b35'}), nutrient('vitaminD', 'Vitamin D', '5.3 µg', '15 µg reference', neutral), nutrient('omega3', 'Total omega-3', '1433 mg', 'EPA+DHA and ALA goals', below), nutrient('fiber', 'Fiber', '42.7 g', '≥25 g', good), nutrient('magnesium', 'Magnesium', '382 mg', '≥350 mg', good)], references: [nutrient('epaDha', 'EPA+DHA', '427 mg', '≥250 mg', good), nutrient('ala', 'ALA', '896 mg', '≥1600 mg', below)]}
    };
    let liveReads = 0;
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        if (path.includes('/nutrition/targets') || path.includes('/weekly-calorie-plans')) liveReads++;
        const values = {'/api/auth/me': {authenticated: true}, '/api/profile': {}, '/api/urge-pauses': {pause: null, serverNow: '2026-08-17T07:00:00Z'}, '/api/weekly-summary': {actionConfigured: true, items: [], page: 0, totalPages: 0}, '/api/weekly-summary/preview': {alreadySaved: true, snapshot: summary.snapshot}, ['/api/weekly-summary/' + fridayDate]: summary};
        return route.fulfill({json: values[path] || []});
    });
    await page.addInitScript(date => window.history.replaceState({}, '', '/weekly-summaries/' + date), fridayDate);
    await page.goto('/');
    const overview = page.getByRole('tabpanel', {name: 'Overview', exact: true});
    await expect(overview.getByRole('table', {name: 'Nutrition daily averages'}).getByRole('rowgroup').getByRole('row')).toHaveCount(7);
    await expect(overview.getByRole('table', {name: 'Workout activity overview'})).toBeVisible();
    await overview.getByRole('button', {name: 'View references'}).click();
    await expect(page.getByRole('table', {name: 'Omega-3 references'})).toBeVisible();
    await page.getByRole('tab', {name: 'Body & pressure', exact: true}).click();
    const body = page.getByRole('table', {name: 'Weekly recorded metrics'});
    await expect(body.getByRole('row', {name: /Body fat/})).toContainText('EXCELLENT');
    await expect(body.getByRole('row', {name: /Average blood pressure/})).toContainText('NORMAL');
    await expect(body.getByRole('row', {name: /Average weight/})).not.toContainText('EXCELLENT');
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-body-statuses-${width}.png`), fullPage: true});
    }
    await page.getByRole('tab', {name: 'Sleep & mood', exact: true}).click();
    await expect(page.getByRole('row', {name: /Total sleep/})).toContainText('EXCELLENT');
    await page.getByRole('tab', {name: 'Nutrition', exact: true}).click();
    const nutrients = page.getByRole('table', {name: 'Weekly nutrition evidence'});
    await expect(nutrients.getByRole('row')).toHaveCount(8);
    const missingBaseline = nutrients.getByRole('row', {name: /Vitamin D/}).getByRole('cell').nth(1);
    await expect(missingBaseline).toHaveText('Not available');
    await expect(nutrients.getByRole('row', {name: /Vitamin D/})).toContainText('Reference only');
    await expect(nutrients.getByRole('row', {name: /Total omega-3/})).toContainText('Below goal');
    await expect(nutrients).not.toContainText('EPA+DHA');
    await expect(nutrients).not.toContainText('ALA goals');
    await page.getByText('Omega-3 references', {exact: true}).click();
    await expect(page.getByRole('table', {name: 'Omega-3 references'})).toBeHidden();
    await nutrients.getByRole('button', {name: 'View references'}).click();
    await expect(page.getByRole('table', {name: 'Omega-3 references'}).getByRole('row', {name: /ALA/})).toContainText('Below goal');
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-nutrition-statuses-${width}.png`), fullPage: true});
    }
    summary.snapshot.progress.currentPeriod.sleep.statusScore = 2;
    await page.goto('/');
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await page.getByRole('tab', {name: 'Sleep & mood', exact: true}).click();
    await expect(page.getByRole('row', {name: /Total sleep/})).toContainText('FAIR');
    await expect(page.getByRole('row', {name: /Total sleep/})).not.toContainText('EXCELLENT');
    summary.snapshot.progress.currentPeriod.sleep.statusScore = null;
    await page.goto('/');
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await page.getByRole('tab', {name: 'Sleep & mood', exact: true}).click();
    await expect(page.getByRole('row', {name: /Total sleep/})).toContainText('Goal met');
    summary.snapshot.emailGoals = {inShapeBodyFatLimit: 17, rules: {version: 1, sleepMinimumSeconds: 21600}};
    await page.goto('/');
    await page.getByRole('tab', {name: 'Measurements', exact: true}).click();
    await expect(page.getByRole('row', {name: /Body fat/})).toContainText('Above goal');
    await expect(page.getByRole('row', {name: /Body fat/})).not.toContainText('EXCELLENT');
    await page.getByRole('tab', {name: 'Sleep & mood', exact: true}).click();
    await expect(page.getByRole('row', {name: /Total sleep/})).toContainText('Goal met');
    expect(liveReads).toBe(0);
});
