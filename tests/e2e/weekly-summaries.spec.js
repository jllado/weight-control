const {test, expect} = require('@playwright/test');

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
    reflection: null
};

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
        if (path === '/api/weekly-summary/config') return json({enabled: true, canSend: true, recipientEmail: 'owner@example.com', deliveryDay: 'MONDAY', deliveryTime: '08:00:00', timeZone: 'Europe/Madrid'});
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

    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }

    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-preview-${width}.png`), fullPage: true});
    }
    await page.getByRole('button', {name: 'Create weekly summary'}).click();
    const confirm = page.getByRole('dialog');
    await expect(confirm).toContainText('Save the available evidence with the missing-data warnings?');
    await confirm.getByRole('button', {name: 'Save anyway'}).click();
    await expect(confirm.getByRole('button', {name: 'Saving…'})).toBeDisabled();
    await expect(confirm.getByRole('button', {name: 'Cancel'})).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(confirm).toBeVisible();
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await confirm.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-pending-${width}.png`)});
    }
    releaseCreation();
    await expect(confirm).toContainText('Summary service unavailable');
    await expect(confirm.getByRole('button', {name: 'Cancel'})).toBeEnabled();
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await confirm.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-error-${width}.png`)});
    }
    await confirm.getByRole('button', {name: 'Save anyway'}).click();
    await expect(confirm).toBeHidden();
    await expect(page.getByRole('heading', {name: '8 Aug 2026 – 14 Aug 2026'})).toBeVisible();
    await expect(page.getByText('fat 14.0 kg')).toBeVisible();
    await expect(page.getByText('Protein 100.0 g/day · 5 days')).toBeVisible();
    await expect(page.getByText(/Deep 1\.5 h · REM 2\.0 h · Light 3\.5 h · Awake 0\.5 h/)).toBeVisible();
    await expect(page.getByText('3.5 / 5 · 2 days')).toBeVisible();
    await expect(page.getByText(/Flexibility 75% · Mind 50%/)).toBeVisible();
    await expect(page.getByText('Below 60%', {exact: true})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Add reflection'})).toBeVisible();
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await expect(page.getByRole('heading', {name: '8 Aug 2026 – 14 Aug 2026'})).toBeVisible();
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-detail-${width}.png`), fullPage: true});
    }
});

test('weekly summaries show dated outcomes and their independent saved reflection', async ({page}, testInfo) => {
    const section = {summary: 'Recorded progress with limited coverage.', nextAction: 'Review the recorded evidence next week.'};
    const summary = {...savedSummary, snapshot: {...weeklySnapshot, warnings: [], outcomes: {
        weight: {measuredDate: '2026-08-15', weightKg: 69.5, fatPercentage: 20, fatKg: 13.9, muscleKg: 52, musclePercentage: 74.8},
        bloodPressure: {measuredDate: '2026-08-16', systolic: 118, diastolic: 78}
    }}, reflection: {fridayDate, generatedAt: '2026-08-17T08:00:00Z', model: 'ChatGPT', title: 'Weekly progress', summary: 'A separate weekly review of recorded evidence.', bodyComposition: section, bloodPressure: section, routines: section, nutrition: section, trainingRecovery: section, goalProgress: section, nextWeekActions: ['Continue recording comparable evidence.']}};
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
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
    await expect(page.getByRole('heading', {name: 'Weekly progress'})).toBeVisible();
    await expect(page.getByText('69.5 kg · 15 Aug 2026')).toBeVisible();
    await expect(page.getByText('118 / 78 mmHg · 16 Aug 2026')).toBeVisible();
    await expect(page.getByRole('button', {name: 'Update reflection'})).toBeVisible();
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-dated-reflection-${width}.png`), fullPage: true});
    }
});

test('weekly archive opens standalone summaries with collapsible sections and legacy links redirect', async ({page}) => {
    const dateLabel = value => {
        const [year, month, day] = value.split('-');
        return `${Number(day)} ${['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][Number(month) - 1]} ${year}`;
    };
    const summaries = Array.from({length: 25}, (_, index) => {
        const end = new Date(Date.UTC(2026, 8, 25 - index * 7));
        const start = new Date(end.getTime() - 6 * 24 * 60 * 60 * 1000);
        const fridayDate = end.toISOString().slice(0, 10);
        const periodStart = start.toISOString().slice(0, 10);
        return {...savedSummary, fridayDate, periodStart, snapshot: {...weeklySnapshot, fridayDate, periodStart, backPain: {
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
    await expect(archive.getByText('1–10 of 25 weeks')).toBeVisible();
    await archive.getByRole('button', {name: 'Next Page'}).click();
    await expect(archive.getByText('11–20 of 25 weeks')).toBeVisible();
    await archive.getByRole('button', {name: 'Next Page'}).click();
    await expect(archive.getByText('21–25 of 25 weeks')).toBeVisible();

    const oldest = summaries[24];
    await archive.getByRole('button', {name: new RegExp(dateLabel(oldest.fridayDate))}).click();
    await expect(page).toHaveURL(new RegExp(`/weekly-summaries/${oldest.fridayDate}$`));
    await expect(archive).toBeHidden();
    await expect(detail).toContainText('2 pain episodes across 2 days');
    await expect(detail).toContainText('Severity');
    await expect(detail).toContainText('mild: 1 · moderate: 1');
    const metricsToggle = page.getByText('Recorded metrics', {exact: true});
    await expect(metricsToggle).toBeVisible();
    await metricsToggle.click();
    await expect(page.getByRole('table', {name: 'Weekly recorded metrics'})).toBeHidden();
    await metricsToggle.click();
    await expect(page.getByRole('table', {name: 'Weekly recorded metrics'})).toBeVisible();

    await page.getByRole('button', {name: 'Back to weekly summaries'}).click();
    await expect(archive).toBeVisible();
    await expect(archive.getByText('1–10 of 25 weeks')).toBeVisible();
    await page.goto(`/weekly-summaries?date=${oldest.fridayDate}`);
    await expect(page).toHaveURL(new RegExp(`/weekly-summaries/${oldest.fridayDate}$`));
    await expect(archive).toBeHidden();
    await expect(detail).toContainText(dateLabel(oldest.periodStart));
});
