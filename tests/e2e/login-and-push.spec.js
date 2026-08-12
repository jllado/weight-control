const {test, expect} = require('@playwright/test');

const googleClientScript = `
window.google = {
    accounts: {
        id: {
            initialize(configuration) {
                window.googleCredentialCallback = configuration.callback;
            },
            renderButton(element) {
                const button = document.createElement('button');
                button.textContent = 'Sign in with Google';
                button.addEventListener('click', () => window.googleCredentialCallback({
                    credential: 'android-id-token',
                    select_by: 'btn'
                }));
                element.appendChild(button);
            },
            cancel() {}
        }
    }
};
`;

const profile = {
    birthDate: null,
    heightCm: 180,
    sex: 'MALE',
    fitnessLevel: 'ACTIVE',
    takesMedication: false,
    weeklyAverageCalorieMaximum: 2500,
    typicalCaloriesPerDay: {
        saturday: 2983,
        sunday: 2983,
        monday: 1853,
        tuesday: 1853,
        wednesday: 1853,
        thursday: 1853,
        friday: 1122
    },
    calorieShortcuts: {
        onPlan: 1850,
        flexible: 3000,
        offPlan: 4000,
        binge: 5000
    }
};

function dashboardDailyStatus(date) {
    return {
        id: date,
        date,
        weight: null,
        bloodPressure: null,
        totalRoutines: 0,
        totalWeightRoutines: 0,
        totalBloodPressureRoutines: 0,
        totalFlexibilityRoutines: 0,
        totalMindRoutines: 0,
        routinesDone: 0,
        weightDone: 0,
        bloodPressureDone: 0,
        flexibilityDone: 0,
        mindDone: 0,
        mood: {average: null, morning: null, midday: null, evening: null},
        routinesPercentage: 0,
        weightPercentage: 0,
        bloodPressurePercentage: 0,
        flexibilityPercentage: 0,
        mindPercentage: 0,
        moodTrend: null,
        routinesScore: 0,
        weightScore: 0,
        bloodPressureScore: 0,
        flexibilityScore: 0,
        mindScore: 0,
        routinesStatus: 0,
        weightStatus: 0,
        bloodPressureStatus: 0,
        flexibilityStatus: 0,
        mindStatus: 0
    };
}

function dashboardWeek() {
    return {
        saturday: null,
        sunday: null,
        monday: null,
        tuesday: null,
        wednesday: null,
        thursday: null,
        friday: null,
        routinesPercentage: 0,
        weightPercentage: 0,
        bloodPressurePercentage: 0,
        flexibilityPercentage: 0,
        mindPercentage: 0,
        moodAverage: null
    };
}

const noDecisionMetrics = {wins: 0, misses: 0, winRate: null};
const dashboard = {
    anchorDate: '2026-08-12',
    lastCompletedDashboardDate: null,
    dailyStatus: dashboardDailyStatus('2026-08-12'),
    lastWeekDailyStatus: dashboardDailyStatus('2026-08-05'),
    weekStatus: dashboardWeek(),
    weekAgoStatus: dashboardWeek(),
    winsAndMissesStatus: {
        selectedDate: noDecisionMetrics,
        rolling30Days: noDecisionMetrics,
        previous30Days: noDecisionMetrics,
        allTime: noDecisionMetrics,
        winRateChange: null,
        currentWinStreak: 0
    }
};
const dashboardWeights = [{
    id: 1,
    date: '2026-08-01T08:00:00+02:00',
    weight: 80,
    lostWeight: 0,
    fat: 16,
    fatPercentage: 20,
    lostFat: 0,
    muscle: 64,
    musclePercentage: 80,
    lostMuscle: 0,
    photoFront: null,
    photoRight: null,
    photoLeft: null
}];
const dashboardBloodPressures = [{id: 1, date: '2026-08-01T08:00:00+02:00', upper: 120, lower: 80, lostUpper: 0, lostLower: 0}];

async function mockLogin(page, loginStatus = 200) {
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') {
            return route.fulfill({status: 403, contentType: 'application/json', body: '{}'});
        }
        if (path === '/api/auth/google') {
            return route.fulfill({
                status: loginStatus,
                contentType: loginStatus === 200 ? 'application/json' : 'text/plain',
                body: loginStatus === 200 ? JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true}) : 'Invalid Google token'
            });
        }
        if (path === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

async function mockAuthenticatedRoutines(page, initialRoutines) {
    let routines = initialRoutines.map(routine => ({...routine}));
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true})});
        }
        if (path === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        if (path === '/api/routines' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(routines)});
        }
        const routineMatch = path.match(/^\/api\/routines\/(\d+)$/);
        if (routineMatch && request.method() === 'PUT') {
            const id = Number(routineMatch[1]);
            const payload = request.postDataJSON();
            routines = routines.map(routine => routine.id === id ? {...routine, ...payload} : routine);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(routines.find(routine => routine.id === id))});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

async function mockAuthenticatedBackPainEpisodes(page) {
    let episodes = [];
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true})});
        }
        if (path === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        if (path === '/api/back-pain-episodes' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(episodes)});
        }
        if (path === '/api/back-pain-episodes' && request.method() === 'POST') {
            const payload = request.postDataJSON();
            const [year, month, day] = payload.date.split('-');
            const episode = {...payload, id: episodes.length + 1, dateFormat: `${day}/${month}/${year}`, time: '12:34:00', timeFormat: '12:34'};
            episodes = [episode, ...episodes];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(episode)});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

async function mockAuthenticatedDashboard(page) {
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/auth/me') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true})});
        }
        if (path === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        if (path === '/api/dashboard') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(dashboard)});
        }
        if (path === '/api/weights') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(dashboardWeights)});
        }
        if (path === '/api/blood-pressures') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(dashboardBloodPressures)});
        }
        if (path === '/api/reflections') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({reflections: [], actionConfigured: false})});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

function routine(id, name, reminderTime) {
    return {
        id,
        startDate: '2026-08-01T00:00:00+02:00',
        lastTimeDate: null,
        name,
        reminderTime,
        currentStrike: 0,
        bestStrike: 0,
        types: ['WEIGHT'],
        times: []
    };
}

async function openSpaRoute(page, path) {
    await page.addInitScript(route => window.history.replaceState({}, '', route), path);
    await page.goto('/');
}

test('credential-only Google response signs in from an Android-sized app window', async ({page}) => {
    await mockLogin(page);
    const loginRequest = page.waitForRequest(request => request.url().endsWith('/api/auth/google') && request.method() === 'POST');

    await page.goto('/');
    await expect(page).toHaveURL('http://127.0.0.1:4173/login');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    expect((await loginRequest).postDataJSON()).toEqual({credential: 'android-id-token'});
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('authentication failure is visible on the login page', async ({page}) => {
    await mockLogin(page, 400);

    await page.goto('/');
    await expect(page).toHaveURL('http://127.0.0.1:4173/login');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page.getByText('Unable to sign in. Please try again.')).toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/login');
});

test('generated service worker imports the push handlers', async ({request}) => {
    const serviceWorker = await request.get('/service-worker.js');
    const pushWorker = await request.get('/push-service-worker.js');

    expect(serviceWorker.ok()).toBe(true);
    expect(await serviceWorker.text()).toContain('push-service-worker.js');
    expect(pushWorker.ok()).toBe(true);
    expect(await pushWorker.text()).toContain("addEventListener('push'");
    expect(await pushWorker.text()).toContain("addEventListener('notificationclick'");
});

test('scheduled routines are ordered by time and can have their reminder cleared', async ({page}) => {
    await mockAuthenticatedRoutines(page, [
        routine(1, 'Evening walk', '18:00:00'),
        routine(2, 'No reminder', null),
        routine(3, 'Morning weigh-in', '07:30:00')
    ]);

    await openSpaRoute(page, '/routines');
    await page.getByRole('tab', {name: 'Scheduled'}).click();

    const scheduledPanel = page.locator('.p-tabview-panel');
    const rows = scheduledPanel.locator('tbody tr');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('07:30');
    await expect(rows.nth(0)).toContainText('Morning weigh-in');
    await expect(rows.nth(1)).toContainText('18:00');
    await expect(rows.nth(1)).toContainText('Evening walk');
    await expect(scheduledPanel).not.toContainText('No reminder');

    await rows.nth(0).getByRole('button', {name: 'Edit routine'}).click();
    const dialog = page.getByRole('dialog', {name: 'Routine'});
    await expect(dialog.locator('#routine')).toHaveValue('Morning weigh-in');
    await dialog.locator('#routine-reminder-time').click();
    await page.getByRole('button', {name: 'Clear'}).click();
    const updateRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/3') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await updateRequest).postDataJSON().reminderTime).toBeNull();
    await expect(rows).toHaveCount(1);
    await expect(scheduledPanel).not.toContainText('Morning weigh-in');
});

test('scheduled routines tab explains how to add the first reminder', async ({page}) => {
    await mockAuthenticatedRoutines(page, [routine(1, 'No reminder', null)]);

    await openSpaRoute(page, '/routines');
    await page.getByRole('tab', {name: 'Scheduled'}).click();

    await expect(page.getByText('No scheduled routines. Add a reminder time in the Manage tab.')).toBeVisible();
});

test('back pain history accepts multiple episodes on the same day', async ({page}) => {
    await mockAuthenticatedBackPainEpisodes(page);
    await openSpaRoute(page, '/back');

    await page.getByRole('button', {name: 'Add Episode'}).click();
    let dialog = page.getByRole('dialog', {name: 'Back Pain Episode'});
    await dialog.getByRole('button', {name: 'Upper Left'}).click();
    await dialog.locator('#pain input').fill('4');
    await dialog.locator('#note').fill('After lifting');
    const firstRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await firstRequest).postDataJSON()).toMatchObject({region: 'UPPER', side: 'LEFT', pain: 4, note: 'After lifting'});

    await page.getByRole('button', {name: 'Add Episode'}).click();
    dialog = page.getByRole('dialog', {name: 'Back Pain Episode'});
    await dialog.getByRole('button', {name: 'Lower Right'}).click();
    await dialog.locator('#pain input').fill('7');
    const secondRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await secondRequest).postDataJSON()).toMatchObject({region: 'LOWER', side: 'RIGHT', pain: 7});

    const rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('Lower Right');
    await expect(rows.nth(1)).toContainText('Upper Left');
});

test('home tabs hide the right arrow after mobile navigation reaches the end', async ({page}) => {
    await mockAuthenticatedDashboard(page);
    await page.setViewportSize({width: 430, height: 932});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    const nextButton = tabs.locator('.p-tabview-nav-next');
    await expect(nextButton).toBeVisible();
    const content = tabs.locator('.p-tabview-nav-content');
    await content.evaluate(element => element.style.scrollBehavior = 'auto');
    await tabs.evaluate(async (element, tabCount) => {
        for (let index = 0; index < tabCount; index++) {
            const button = element.querySelector('.p-tabview-nav-next');
            if (!button) {
                return;
            }
            button.click();
            await new Promise(resolve => requestAnimationFrame(resolve));
        }
    }, await tabs.getByRole('tab').count());
    await expect(nextButton).toHaveCount(0);

    const winsTab = tabs.getByRole('tab', {name: 'Wins'});
    const tabBounds = await winsTab.boundingBox();
    const contentBounds = await content.boundingBox();
    expect(tabBounds.x).toBeGreaterThanOrEqual(contentBounds.x - 1);
    expect(tabBounds.x + tabBounds.width).toBeLessThanOrEqual(contentBounds.x + contentBounds.width + 1);
});

test('home tabs treat a fractional mobile scroll position as the end', async ({page}) => {
    await mockAuthenticatedDashboard(page);
    await page.setViewportSize({width: 430, height: 932});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    const nextButton = tabs.locator('.p-tabview-nav-next');
    await expect(nextButton).toBeVisible();
    await tabs.locator('.p-tabview-nav-content').evaluate(content => {
        Object.defineProperties(content, {
            scrollLeft: {configurable: true, value: 99.5},
            scrollWidth: {configurable: true, value: 500},
            clientWidth: {configurable: true, value: 400}
        });
        content.dispatchEvent(new Event('scroll'));
    });
    await expect(nextButton).toHaveCount(0);
});
