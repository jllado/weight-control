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

test('weekly summary warns before a missing-outcome save and stays usable at target widths', async ({page}) => {
    let created = false;
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const json = value => route.fulfill({contentType: 'application/json', body: JSON.stringify(value)});
        if (path === '/api/auth/me') return json({email: 'owner@example.com', displayName: 'Owner', authenticated: true});
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: '2026-08-17T07:00:00Z'});
        if (path === '/api/profile') return json({});
        if (path === '/api/weekly-summary/config') return json({enabled: true, canSend: true, recipientEmail: 'owner@example.com', deliveryDay: 'MONDAY', deliveryTime: '08:00:00', timeZone: 'Europe/Madrid'});
        if (path === '/api/weekly-summary') return json({latestEligibleFriday: fridayDate, actionConfigured: true, summaries: created ? [{periodStart: savedSummary.periodStart, fridayDate, createdAt: savedSummary.createdAt, reflectionSaved: false}] : []});
        if (path === '/api/weekly-summary/preview') return json({periodStart: '2026-08-08', fridayDate, canCreate: !created, alreadySaved: created, snapshot: weeklySnapshot});
        if (path === `/api/weekly-summary/${fridayDate}`) return json(savedSummary);
        if (path === '/api/weekly-summary/create' && request.method() === 'POST') {
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

    await page.getByRole('button', {name: 'Create weekly summary'}).click();
    const confirm = page.getByRole('dialog');
    await expect(confirm).toContainText('Save the available evidence with the missing-data warnings?');
    await confirm.getByRole('button', {name: 'Save anyway'}).click();
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
    }
});
