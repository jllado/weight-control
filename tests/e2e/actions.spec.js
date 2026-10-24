const {test, expect} = require('@playwright/test');
const {googleClientScript} = require('../fixtures/dashboard.cjs');
const today = new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'}).format(new Date());
const source = {sourceKey: 'weekly:42:' + 'a'.repeat(64), wording: 'No phone in bed', sourceLabel: 'Weekly summary · 2026-10-23', provenance: ['Next week', 'Routines'], existingAttemptId: null};

async function mockActions(page, signedOut = false) {
    let rows = [];
    const writes = [];
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({contentType: 'application/javascript', body: googleClientScript}));
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const path = url.pathname;
        const json = value => route.fulfill({json: value});
        if (path === '/api/auth/me') return signedOut ? route.fulfill({status: 403, json: {}}) : json({authenticated: true, email: 'owner@example.com'});
        if (path === '/api/auth/google') { signedOut = false; return json({authenticated: true, email: 'owner@example.com'}); }
        if (path === '/api/profile') return json({});
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: new Date().toISOString()});
        if (path.startsWith('/api/actions/suggestions/')) return json(source);
        if (path === '/api/actions/suggestions') return json([source]);
        if (path === '/api/actions' && request.method() === 'POST') {
            const body = request.postDataJSON(); writes.push(body);
            rows.push({...body, id: rows.length + 1, goalLocked: false, status: 'ACTIVE', streak: 0, bestStreak: 0, percentage: 0, checked: false, checkinDates: [], sourceLabel: source.sourceLabel});
            return json(rows[rows.length - 1]);
        }
        if (path === '/api/actions') return json(url.searchParams.get('view') === 'HISTORY' ? [] : rows);
        const check = path.match(/^\/api\/actions\/(\d+)\/checkins\/(.+)$/);
        if (check) {
            const row = rows.find(row => row.id === Number(check[1]));
            row.checked = request.method() === 'PUT'; row.goalLocked = true; row.streak = row.checked ? 1 : 0; row.percentage = row.checked ? 7 : 0;
            row.checkinDates = row.checked ? [check[2]] : [];
            return json(row);
        }
        if (/^\/api\/actions\/\d+$/.test(path)) return json(rows.find(row => row.id === Number(path.split('/').pop())));
        return json([]);
    });
    return writes;
}

test('email activation preserves its draft through login and creates only on Save', async ({page}) => {
    const writes = await mockActions(page, true);
    await page.addInitScript(url => window.history.replaceState({}, '', url), '/actions?source=' + encodeURIComponent(source.sourceKey));
    await page.goto('/');
    await expect(page).toHaveURL(/\/login\?.*actionReturn=/);
    expect(writes).toHaveLength(0);
    await page.getByRole('button', {name: 'Sign in with Google'}).click();
    await expect(page).toHaveURL(/\/actions\?source=/);
    await expect(page.getByLabel('Action', {exact: true})).toHaveValue('No phone in bed');
    await expect(page.getByLabel('Target consecutive days')).toHaveValue('14');
    expect(writes).toHaveLength(0);
    await page.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page.getByRole('heading', {name: 'No phone in bed'})).toBeVisible();
    expect(writes).toHaveLength(1);
    expect(writes[0]).toEqual({wording: source.wording, startDate: today, targetDays: 14, sourceKey: source.sourceKey});
    await page.getByRole('button', {name: 'Complete No phone in bed'}).click();
    await expect(page.getByText('1 / 14 consecutive days')).toBeVisible();
    await page.getByRole('button', {name: 'Undo No phone in bed'}).click();
    await page.getByRole('button', {name: 'Edit No phone in bed'}).click();
    await expect(page.getByLabel('Start date')).toBeDisabled();
    await expect(page.getByLabel('Target consecutive days')).toBeDisabled();
});

test('Actions inline form and compact controls remain usable at approved widths and by keyboard', async ({page}, testInfo) => {
    await mockActions(page);
    await page.addInitScript(() => window.history.replaceState({}, '', '/actions'));
    await page.goto('/');
    await page.getByRole('button', {name: 'New action'}).click();
    await page.getByLabel('Action', {exact: true}).fill('A sufficiently long personal action that wraps safely on a narrow mobile screen');
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath('actions-form-' + width + '.png'), fullPage: true});
    }
    await page.getByLabel('Action', {exact: true}).focus();
    await page.keyboard.press('Tab');
    await expect(page.getByLabel('Start date')).toBeFocused();
    await page.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page.getByRole('button', {name: /^Complete A sufficiently/})).toBeVisible();
});

test('delayed authentication preserves a direct existing protected route without mounting it early', async ({page}) => {
    await mockActions(page, true);
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    let routineReads = 0;
    page.on('request', request => { if (new URL(request.url()).pathname === '/api/routines') routineReads++; });
    await page.route('**/api/auth/me', async route => { await pending; await route.fallback(); });
    await page.addInitScript(() => window.history.replaceState({}, '', '/routines'));
    await page.goto('/');
    await expect(page).toHaveURL('/routines');
    expect(routineReads).toBe(0);
    release();
    await expect(page).toHaveURL('/login');
    await expect(page.getByRole('button', {name: 'Sign in with Google'})).toBeVisible();
    expect(routineReads).toBe(0);
});

test('dashboard inserts at most three isolated Actions rows between the unchanged overview and Week Score', async ({page}, testInfo) => {
    const {profile, dashboard, dashboardDailyStatus} = require('../fixtures/dashboard.cjs');
    let selectedDate = dashboard.anchorDate;
    let releaseOld;
    const oldRead = new Promise(resolve => { releaseOld = resolve; });
    let failingActions = true;
    const rows = Array.from({length: 5}, (_, index) => ({
        id: index + 1, wording: 'A personal action ' + (index + 1), startDate: '2026-08-01', targetDays: 14,
        status: 'ACTIVE', streak: index, bestStreak: index, percentage: index * 7, checked: index === 1, checkinDates: []
    }));
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    const consoleErrors = []; page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url()); const path = url.pathname;
        const json = value => route.fulfill({json: value});
        if (path === '/api/auth/me') return json({authenticated: true, email: 'owner@example.com'});
        if (path === '/api/profile') return json(profile);
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: new Date().toISOString()});
        if (path === '/api/dashboard') return json(dashboard);
        if (path === '/api/dashboard/retreat') {
            const previous = new Date(selectedDate + 'T12:00:00Z'); previous.setUTCDate(previous.getUTCDate() - 1);
            selectedDate = previous.toISOString().slice(0, 10);
            return json({...dashboard, anchorDate: selectedDate, dailyStatus: dashboardDailyStatus(selectedDate)});
        }
        if (path === '/api/workouts/dashboard') return json({currentWorkouts: [], previousWeekWorkouts: [], preloadWorkouts: [], recordEvents: [], days: []});
        if (path === '/api/dashboard/overall-progress') return json({status: null, score: null, contributions: []});
        if (path === '/api/coach-warnings') return json({active: [], hasHistory: false});
        if (path === '/api/actions') {
            const date = url.searchParams.get('date');
            if (date === '2026-08-11') { await oldRead; return json([{...rows[0], wording: 'Stale action'}]); }
            if (date === '2026-08-10') return json([{...rows[0], wording: 'Current date action'}]);
            if (date === '2026-08-09' && failingActions) return route.fulfill({status: 503, json: {message: 'Actions unavailable'}});
            return json(rows);
        }
        if (path.startsWith('/api/weekly-calorie-plans/')) return route.fulfill({status: 204});
        if (path === '/api/dashboard/coach-metrics') {
            const totals = {workoutCount: 0, totalDurationSeconds: 0, strengthVolumeKg: 0, totalDistanceKm: 0, totalCalories: 0};
            const week = {startDate: '2026-08-08', endDate: '2026-08-14', reflections: [], workouts: [], totals};
            return json({selectedWeek: week, previousWeek: week, selectedWeekToDate: week, previousWeekToDate: week,
                reflections: [], workouts: [], weeklyWorkouts: [], planProgressTrend: {latestScore: null, previousScore: null}});
        }
        return json([]);
    });
    await page.goto('/');
    const panel = page.locator('.dashboard-actions');
    await expect(panel.getByText('Actions (5)')).toBeVisible();
    await expect(panel.getByRole('heading', {name: /^A personal action/})).toHaveCount(3);
    await expect(panel.getByRole('link', {name: '+2 more actions'})).toBeVisible();
    await expect(panel.getByText('12/08/2026 · 1/5 checked')).toBeVisible();
    await expect(page.locator('.p-toast-message-error')).toHaveCount(0);
    const before = await page.locator('.progress-overview-card').evaluate(element => element.outerHTML);
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 1100});
        const overviewBox = await page.locator('.progress-overview-card').boundingBox();
        const panelBox = await panel.boundingBox();
        const scoreBox = await page.locator('.week-status').boundingBox();
        expect(overviewBox.y + overviewBox.height).toBeLessThanOrEqual(panelBox.y);
        expect(panelBox.y + panelBox.height).toBeLessThanOrEqual(scoreBox.y);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath('dashboard-actions-' + width + '.png'), fullPage: true});
    }
    expect(await page.locator('.progress-overview-card').evaluate(element => element.outerHTML)).toBe(before);
    expect(errors).toEqual([]);
    expect(consoleErrors).toEqual([]);
    await expect(page.locator('.p-toast-message-error')).toHaveCount(0);
    await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
    await expect(panel.getByText('Loading actions…')).toBeVisible();
    await expect(page.locator('.week-status')).toBeVisible();
    await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
    await expect(panel.getByRole('heading', {name: 'Current date action'})).toBeVisible();
    const oldResponse = page.waitForResponse(response => response.url().includes('/api/actions?date=2026-08-11'));
    releaseOld();
    await (await oldResponse).finished();
    await expect(panel.getByRole('heading', {name: 'Current date action'})).toBeVisible();
    await expect(panel.getByRole('heading', {name: 'Stale action'})).toHaveCount(0);
    await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
    await expect(panel.getByText('Actions unavailable', {exact: false})).toBeVisible();
    await expect(page.locator('.progress-overview-card')).toBeVisible();
    await expect(page.locator('.week-status')).toBeVisible();
    failingActions = false;
    await panel.getByRole('button', {name: 'Retry'}).click();
    await expect(panel.getByRole('heading', {name: 'A personal action 1'})).toBeVisible();
    expect(errors).toEqual([]);
    expect(consoleErrors.filter(message => !message.includes('status of 503'))).toEqual([]);
});

test('History can undo a retained later check after an earlier correction moves completion back', async ({page}) => {
    await mockActions(page);
    const previous = new Date(today + 'T12:00:00Z'); previous.setUTCDate(previous.getUTCDate() - 1);
    const completedDate = previous.toISOString().slice(0, 10);
    const row = {id: 9, wording: 'A corrected attempt', startDate: '2026-01-01', targetDays: 1, goalLocked: true,
        status: 'COMPLETED', streak: 1, bestStreak: 2, percentage: 100, checked: true, completedDate, checkinDates: [completedDate, today]};
    await page.route('**/api/actions?*', route => route.fulfill({json: [row]}));
    await page.route('**/api/actions/9/checkins/*', route => route.fulfill({json: row}));
    await page.addInitScript(() => window.history.replaceState({}, '', '/actions'));
    await page.goto('/');
    await page.getByRole('tab', {name: 'History', exact: true}).click();
    await page.getByText('View check-ins', {exact: true}).click();
    await page.getByLabel('Check-in date', {exact: true}).fill(today);
    const undo = page.getByRole('button', {name: 'Undo check-in for ' + today});
    await expect(undo).toBeEnabled();
    const request = page.waitForRequest(request => request.url().endsWith('/actions/9/checkins/' + today) && request.method() === 'DELETE');
    await undo.click();
    await request;
});

test('replaced email recommendation shows an explicit stale message and performs no creation', async ({page}) => {
    const writes = await mockActions(page);
    await page.route('**/api/actions/suggestions/*', route => route.fulfill({status: 400, json: {message: 'This recommendation has changed. Open its report or Suggestions.'}}));
    await page.addInitScript(key => window.history.replaceState({}, '', '/actions?source=' + key), source.sourceKey);
    await page.goto('/');
    await expect(page.getByText('This recommendation has changed. Open its report or Suggestions.')).toBeVisible();
    await expect(page.getByRole('button', {name: 'Save', exact: true})).toHaveCount(0);
    expect(writes).toHaveLength(0);
});
