const {test, expect} = require('@playwright/test');
const vm = require('node:vm');

function loadPushWorker(source, {fetch = async () => ({ok: true}), windowClients = []} = {}) {
    const listeners = {};
    const notifications = [];
    const openedUrls = [];
    const context = {
        URL,
        fetch,
        self: {
            location: {origin: 'https://weightcontrol.test'},
            registration: {
                showNotification(title, options) {
                    notifications.push({title, options});
                    return Promise.resolve();
                }
            },
            clients: {
                matchAll: async () => windowClients,
                openWindow: async url => openedUrls.push(url)
            },
            addEventListener(type, listener) {
                listeners[type] = listener;
            }
        }
    };
    vm.runInNewContext(source, context);
    return {listeners, notifications, openedUrls};
}

async function dispatchWorkerEvent(listener, event) {
    let pending = Promise.resolve();
    listener({...event, waitUntil: promise => pending = promise});
    await pending;
}

function plain(value) {
    return JSON.parse(JSON.stringify(value));
}

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

function madridDate(date = new Date()) {
    const formatter = new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Europe/Madrid',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
    const parts = Object.fromEntries(formatter.formatToParts(date).map(part => [part.type, part.value]));
    return `${parts.year}-${parts.month}-${parts.day}`;
}

function routineReminderDashboard(date, routinesDone = 0) {
    const mood = {average: null, morning: null, midday: null, evening: null};
    const status = {
        id: 1,
        date,
        weight: null,
        bloodPressure: null,
        totalRoutines: 1,
        totalWeightRoutines: 1,
        totalBloodPressureRoutines: 0,
        totalFlexibilityRoutines: 0,
        totalMindRoutines: 0,
        routinesDone,
        weightDone: routinesDone,
        bloodPressureDone: 0,
        flexibilityDone: 0,
        mindDone: 0,
        mood,
        routinesPercentage: routinesDone * 100,
        weightPercentage: routinesDone * 100,
        bloodPressurePercentage: 0,
        flexibilityPercentage: 0,
        mindPercentage: 0,
        moodTrend: null,
        routinesScore: routinesDone,
        weightScore: routinesDone,
        bloodPressureScore: 0,
        flexibilityScore: 0,
        mindScore: 0,
        routinesStatus: routinesDone * 100,
        weightStatus: routinesDone * 100,
        bloodPressureStatus: 0,
        flexibilityStatus: 0,
        mindStatus: 0
    };
    const week = {
        saturday: status,
        sunday: null,
        monday: null,
        tuesday: null,
        wednesday: null,
        thursday: null,
        friday: null,
        routinesPercentage: routinesDone * 100,
        weightPercentage: routinesDone * 100,
        bloodPressurePercentage: 0,
        flexibilityPercentage: 0,
        mindPercentage: 0,
        moodAverage: null
    };
    const outcome = {wins: 0, misses: 0, winRate: null};
    return {
        anchorDate: date,
        lastCompletedDashboardDate: null,
        dailyStatus: status,
        lastWeekDailyStatus: status,
        weekStatus: week,
        weekAgoStatus: week,
        winsAndMissesStatus: {
            selectedDate: outcome,
            rolling30Days: outcome,
            previous30Days: outcome,
            allTime: outcome,
            winRateChange: null,
            currentWinStreak: 0
        }
    };
}

async function mockRoutineReminderHome(page, initialRoutines, {requiresLogin = false, snoozeExpires = false, pushEnabled = false, initialMoods = []} = {}) {
    let routines = initialRoutines.map(item => ({...item, times: [...item.times]}));
    let moods = initialMoods.map(item => ({...item}));
    let backPainEpisodes = [];
    let reminderSettings = {morningTime: '07:30:00', middayTime: '13:30:00', eveningTime: '20:30:00', timeZone: 'Europe/Madrid'};
    let routinesDone = routines.filter(item => item.times.length > 0).length;
    const date = madridDate();
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') {
            return route.fulfill({
                status: requiresLogin ? 403 : 200,
                contentType: 'application/json',
                body: requiresLogin ? '{}' : JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true})
            });
        }
        if (path === '/api/auth/google') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true})});
        }
        if (path === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        if (path === '/api/routines' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(routines)});
        }
        if (path === '/api/moods' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(moods)});
        }
        if (path === '/api/moods' && request.method() === 'POST') {
            const mood = {id: moods.length + 1, ...request.postDataJSON()};
            moods = [mood, ...moods];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(mood)});
        }
        if (path === '/api/back-pain-episodes' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(backPainEpisodes)});
        }
        if (path === '/api/back-pain-episodes' && request.method() === 'POST') {
            const episode = {id: backPainEpisodes.length + 1, time: '12:34:00', timeFormat: '12:34', ...request.postDataJSON()};
            backPainEpisodes = [episode, ...backPainEpisodes];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(episode)});
        }
        if (path === '/api/weights') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify([{
                id: 1,
                date: `${date}T08:00:00+02:00`,
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
            }])});
        }
        const checkinMatch = path.match(/^\/api\/routines\/(\d+)\/checkins$/);
        if (checkinMatch && request.method() === 'POST') {
            const id = Number(checkinMatch[1]);
            const checkedAt = request.postDataJSON().date;
            routines = routines.map(item => item.id === id ? {...item, times: [...item.times, checkedAt]} : item);
            routinesDone = routines.filter(item => item.times.length > 0).length;
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(routines.find(item => item.id === id))});
        }
        const snoozeMatch = path.match(/^\/api\/routines\/(\d+)\/reminder-snooze$/);
        if (snoozeMatch && request.method() === 'POST') {
            const {minutes} = request.postDataJSON();
            const nextReminderAt = snoozeExpires ? null : new Date(Date.now() + minutes * 60 * 1000).toISOString();
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({nextReminderAt})});
        }
        if (path === '/api/dashboard' || path === '/api/dashboard/refresh') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(routineReminderDashboard(date, routinesDone))});
        }
        if (path === '/api/reflections') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({reflections: [], actionConfigured: false})});
        }
        if (path === '/api/push/config') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({enabled: pushEnabled, publicKey: pushEnabled ? 'test-public-key' : null, timeZone: 'Europe/Madrid'})});
        }
        if (path === '/api/push/reminder-settings' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(reminderSettings)});
        }
        if (path === '/api/push/reminder-settings' && request.method() === 'PUT') {
            reminderSettings = {...request.postDataJSON(), timeZone: 'Europe/Madrid'};
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(reminderSettings)});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

async function mockAuthenticatedDashboard(page, selectedDate = dashboard.anchorDate, {requiresLogin = false, backPainEpisodes = []} = {}) {
    let authenticated = !requiresLogin;
    const decisionOutcomes = [];
    const lastWeekDate = new Date(`${selectedDate}T12:00:00Z`);
    lastWeekDate.setUTCDate(lastWeekDate.getUTCDate() - 7);
    const selectedDashboard = {
        ...dashboard,
        anchorDate: selectedDate,
        dailyStatus: dashboardDailyStatus(selectedDate),
        lastWeekDailyStatus: dashboardDailyStatus(lastWeekDate.toISOString().slice(0, 10))
    };
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') {
            return route.fulfill({
                status: authenticated ? 200 : 403,
                contentType: 'application/json',
                body: authenticated ? JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true}) : '{}'
            });
        }
        if (path === '/api/auth/google') {
            authenticated = true;
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true})});
        }
        if (!authenticated) {
            return route.fulfill({status: 403, contentType: 'application/json', body: '{}'});
        }
        if (path === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        if (path === '/api/dashboard') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(selectedDashboard)});
        }
        if (path === '/api/weights') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(dashboardWeights)});
        }
        if (path === '/api/blood-pressures') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(dashboardBloodPressures)});
        }
        if (path === '/api/back-pain-episodes') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(backPainEpisodes)});
        }
        if (path === '/api/reflections') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({reflections: [], actionConfigured: false})});
        }
        if (path === '/api/decision-outcomes' && request.method() === 'POST') {
            const outcome = request.postDataJSON();
            decisionOutcomes.push(outcome);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({id: decisionOutcomes.length, ...outcome})});
        }
        if (path === '/api/moods' && request.method() === 'POST') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({id: 1, ...request.postDataJSON()})});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
    return decisionOutcomes;
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

test('routine pushes expose snooze and dismiss device actions', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const worker = loadPushWorker(source);
    const routinePayload = {
        title: 'Routine reminder',
        body: 'Morning weigh-in',
        url: '/?routineReminderId=1&routineReminderDate=2026-08-14',
        tag: 'routine-reminder-1',
        snoozeUrl: '/api/routines/1/reminder-snooze'
    };

    await dispatchWorkerEvent(worker.listeners.push, {data: {json: () => routinePayload}});
    await dispatchWorkerEvent(worker.listeners.push, {data: {json: () => ({...routinePayload, title: 'Notification test', snoozeUrl: null})}});

    expect(plain(worker.notifications[0])).toEqual({
        title: 'Routine reminder',
        options: {
            body: 'Morning weigh-in',
            icon: '/android-chrome-192x192.png',
            tag: 'routine-reminder-1',
            actions: [
                {action: 'snooze', title: 'Snooze 15 min'},
                {action: 'dismiss', title: 'Dismiss'}
            ],
            data: {
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14',
                snoozeUrl: '/api/routines/1/reminder-snooze'
            }
        }
    });
    expect(plain(worker.notifications[1].options.actions)).toEqual([]);
});

test('device dismiss closes the routine notification without making a request', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const requests = [];
    const worker = loadPushWorker(source, {fetch: async (...args) => requests.push(args)});
    let closed = false;

    await dispatchWorkerEvent(worker.listeners.notificationclick, {
        action: 'dismiss',
        notification: {
            data: {
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14',
                snoozeUrl: '/api/routines/1/reminder-snooze'
            },
            close: () => closed = true
        }
    });

    expect(closed).toBe(true);
    expect(requests).toEqual([]);
    expect(worker.openedUrls).toEqual([]);
});

test('device snooze posts a 15-minute delay without opening the app', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const requests = [];
    const worker = loadPushWorker(source, {
        fetch: async (...args) => {
            requests.push(args);
            return {ok: true};
        }
    });

    await dispatchWorkerEvent(worker.listeners.notificationclick, {
        action: 'snooze',
        notification: {
            data: {
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14',
                snoozeUrl: '/api/routines/1/reminder-snooze'
            },
            close() {}
        }
    });

    expect(plain(requests)).toEqual([[
        '/api/routines/1/reminder-snooze',
        {
            method: 'POST',
            credentials: 'include',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({minutes: 15})
        }
    ]]);
    expect(worker.openedUrls).toEqual([]);
});

for (const failure of [
    {name: 'API failure', fetch: async () => ({ok: false})},
    {name: 'network failure', fetch: async () => Promise.reject(new Error('offline'))}
]) {
    test(`device snooze opens the routine reminder after ${failure.name}`, async ({request}) => {
        const source = await (await request.get('/push-service-worker.js')).text();
        const worker = loadPushWorker(source, {fetch: failure.fetch});

        await dispatchWorkerEvent(worker.listeners.notificationclick, {
            action: 'snooze',
            notification: {
                data: {
                    url: '/?routineReminderId=1&routineReminderDate=2026-08-14',
                    snoozeUrl: '/api/routines/1/reminder-snooze'
                },
                close() {}
            }
        });

        expect(worker.openedUrls).toEqual(['https://weightcontrol.test/?routineReminderId=1&routineReminderDate=2026-08-14']);
    });
}

test('clicking the notification body keeps the existing focus-and-navigate behavior', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const navigatedUrls = [];
    let focused = false;
    const existingClient = {
        url: 'https://weightcontrol.test/routines',
        async navigate(url) {
            navigatedUrls.push(url);
            return {focus: async () => focused = true};
        }
    };
    const worker = loadPushWorker(source, {windowClients: [existingClient]});

    await dispatchWorkerEvent(worker.listeners.notificationclick, {
        action: '',
        notification: {
            data: {url: '/?routineReminderId=1&routineReminderDate=2026-08-14', snoozeUrl: null},
            close() {}
        }
    });

    expect(navigatedUrls).toEqual(['https://weightcontrol.test/?routineReminderId=1&routineReminderDate=2026-08-14']);
    expect(focused).toBe(true);
    expect(worker.openedUrls).toEqual([]);
});

test('generated manifest exposes the decision outcome shortcuts', async ({request}) => {
    const response = await request.get('/manifest.json');

    expect(response.ok()).toBe(true);
    expect((await response.json()).shortcuts).toEqual([
        {
            name: 'Add Win',
            short_name: 'Win',
            description: 'Record a win for the selected dashboard date.',
            url: '/?decisionOutcome=WIN'
        },
        {
            name: 'Add Loss',
            short_name: 'Loss',
            description: 'Record a loss for the selected dashboard date.',
            url: '/?decisionOutcome=MISS'
        }
    ]);
});

for (const shortcut of [
    {label: 'win', outcome: 'WIN'},
    {label: 'loss', outcome: 'MISS'}
]) {
    test(`decision outcome ${shortcut.label} shortcut records once for the selected dashboard date`, async ({page}) => {
        const decisionOutcomes = await mockAuthenticatedDashboard(page, '2026-08-11');

        await page.goto(`/?decisionOutcome=${shortcut.outcome}`);

        await expect(page).toHaveURL('http://127.0.0.1:4173/');
        await expect(page.getByText(`${shortcut.outcome} recorded`)).toBeVisible();
        expect(decisionOutcomes).toEqual([{date: '2026-08-11', outcome: shortcut.outcome}]);

        await page.reload();
        await expect(page.getByText('Dashboard Date')).toBeVisible();
        expect(decisionOutcomes).toHaveLength(1);
    });
}

test('login preserves and records a pending decision outcome shortcut', async ({page}) => {
    const decisionOutcomes = await mockAuthenticatedDashboard(page, '2026-08-11', {requiresLogin: true});

    await page.goto('/?decisionOutcome=WIN');
    await expect(page).toHaveURL('http://127.0.0.1:4173/login?decisionOutcome=WIN');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page).toHaveURL('http://127.0.0.1:4173/');
    await expect(page.getByText('WIN recorded')).toBeVisible();
    expect(decisionOutcomes).toEqual([{date: '2026-08-11', outcome: 'WIN'}]);
});

test.describe('notification permission prompt', () => {
    test.use({serviceWorkers: 'allow'});

    test('is available without routine reminders', async ({page}) => {
        await mockRoutineReminderHome(page, [], {pushEnabled: true});
        await page.addInitScript(() => Object.defineProperty(Notification, 'permission', {configurable: true, get: () => 'default'}));

        await page.goto('/');

        await expect(page.getByText('Enable notifications')).toBeVisible();
        await expect(page.getByText('Receive daily Mood and Back reminders, routine reminders, and notifications when a new app update is available.')).toBeVisible();
    });
});

test('daily reminder settings show and save the three default times', async ({page}) => {
    await mockRoutineReminderHome(page, []);

    await openSpaRoute(page, '/settings');

    await expect(page.locator('#morning-reminder-time')).toHaveValue('07:30');
    await expect(page.locator('#midday-reminder-time')).toHaveValue('13:30');
    await expect(page.locator('#evening-reminder-time')).toHaveValue('20:30');
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/push/reminder-settings') && request.method() === 'PUT');
    await page.getByRole('button', {name: 'Save reminder times'}).click();
    expect((await saveRequest).postDataJSON()).toEqual({morningTime: '07:30', middayTime: '13:30', eveningTime: '20:30'});
    await expect(page.getByText('Reminder times saved')).toBeVisible();
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

test('routine reminder can be snoozed repeatedly with preset delays', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')]);

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}`);
    let dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await expect(dialog).toContainText('Morning weigh-in');
    await expect(dialog).toContainText('07:30');
    await expect(dialog.locator('.p-dropdown-label')).toHaveText('15 minutes');
    let snoozeRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/1/reminder-snooze') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Snooze'}).click();

    expect((await snoozeRequest).postDataJSON()).toEqual({minutes: 15});
    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
    await expect(page.getByText('Routine reminder snoozed for 15 minutes')).toBeVisible();

    await page.goto(`/?routineReminderId=1&routineReminderDate=${date}`);
    dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await dialog.getByLabel('Snooze for').click();
    await page.getByRole('option', {name: '30 minutes'}).click();
    snoozeRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/1/reminder-snooze') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Snooze'}).click();

    expect((await snoozeRequest).postDataJSON()).toEqual({minutes: 30});
    await expect(page.getByText('Routine reminder snoozed for 30 minutes')).toBeVisible();
});

test('routine reminder content and actions remain visible at mobile and desktop sizes', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')]);

    await page.setViewportSize({width: 1280, height: 800});
    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});

    for (const viewport of [{width: 1280, height: 800}, {width: 393, height: 851}]) {
        await page.setViewportSize(viewport);
        await expect(dialog.getByText("It's time for")).toBeVisible();
        await expect(dialog.getByText('Morning weigh-in')).toBeVisible();
        await expect(dialog.getByText('Scheduled time')).toBeVisible();
        await expect(dialog.getByText('07:30')).toBeVisible();
        await expect(dialog.getByText('Europe/Madrid')).toBeVisible();
        await expect(dialog.getByLabel('Snooze for')).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Snooze'})).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Mark as done'})).toBeVisible();
    }
});

test('routine reminder expires when its snooze crosses midnight', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {snoozeExpires: true});

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await dialog.getByRole('button', {name: 'Snooze'}).click();

    await expect(dialog).not.toBeVisible();
    await expect(page.getByText('No more routine reminders today')).toBeVisible();
});

test('routine reminder can mark the routine as done', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')]);

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    const checkinRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/1/checkins') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Mark as done'}).click();

    expect(new Date((await checkinRequest).postDataJSON().date).toString()).not.toBe('Invalid Date');
    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
    await expect(page.getByText('Routine marked as done')).toBeVisible();
});

for (const reminder of [
    {name: 'stale', id: 1, date: '2026-01-01', routines: [routine(1, 'Morning weigh-in', '07:30:00')]},
    {name: 'missing', id: 99, date: madridDate(), routines: [routine(1, 'Morning weigh-in', '07:30:00')]},
    {name: 'completed', id: 1, date: madridDate(), routines: [{...routine(1, 'Morning weigh-in', '07:30:00'), times: [`${madridDate()}T08:00:00+02:00`]}]}
]) {
    test(`${reminder.name} routine reminder opens Home without a modal`, async ({page}) => {
        await mockRoutineReminderHome(page, reminder.routines);

        await openSpaRoute(page, `/?routineReminderId=${reminder.id}&routineReminderDate=${reminder.date}`);

        await expect(page.getByRole('dialog', {name: 'Routine reminder'})).toHaveCount(0);
        await expect(page).toHaveURL('http://127.0.0.1:4173/');
    });
}

test('login preserves a pending routine reminder', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {requiresLogin: true});

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}`);
    await expect(page).toHaveURL(`http://127.0.0.1:4173/login?routineReminderId=1&routineReminderDate=${date}`);
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page.getByRole('dialog', {name: 'Routine reminder'})).toContainText('Morning weigh-in');
});

test('mood reminder can be dismissed without creating an entry', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, []);

    await openSpaRoute(page, `/?checkInReminder=mood&checkInPeriod=MORNING&checkInReminderDate=${date}`);
    const dialog = page.getByRole('dialog', {name: 'Morning mood reminder'});
    await expect(dialog).toContainText('Record your morning mood.');
    await dialog.getByRole('button', {name: 'Dismiss'}).click();

    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('mood reminder records the fixed date and period', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, []);

    await openSpaRoute(page, `/?checkInReminder=mood&checkInPeriod=MIDDAY&checkInReminderDate=${date}`);
    await page.getByRole('dialog', {name: 'Midday mood reminder'}).getByRole('button', {name: 'Record'}).click();
    const dialog = page.getByRole('dialog', {name: 'Mood'});
    await expect(dialog.locator('#period')).toContainText('Midday');
    await expect(dialog.locator('#period')).toHaveClass(/p-disabled/);
    await dialog.locator('#value').click();
    await page.getByRole('option', {name: /Great/}).click();
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/moods') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();

    expect((await saveRequest).postDataJSON()).toMatchObject({date, period: 'MIDDAY', value: 5});
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('back reminder opens an optional pain episode form', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, []);

    await openSpaRoute(page, `/?checkInReminder=back&checkInPeriod=EVENING&checkInReminderDate=${date}`);
    const reminder = page.getByRole('dialog', {name: 'Evening back reminder'});
    await expect(reminder).toContainText('Record a back pain episode if needed.');
    await reminder.getByRole('button', {name: 'Record'}).click();
    const dialog = page.getByRole('dialog', {name: 'Back Pain Episode'});
    await dialog.getByRole('button', {name: 'Lower Right'}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'Moderate', exact: true}).click();
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();

    expect((await saveRequest).postDataJSON()).toMatchObject({date, region: 'LOWER', side: 'RIGHT', severity: 'MODERATE'});
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

for (const reminder of [
    {name: 'stale', date: '2026-01-01', moods: []},
    {name: 'completed', date: madridDate(), moods: [{id: 1, date: madridDate(), period: 'MORNING', value: 4, note: null}]}
]) {
    test(`${reminder.name} mood reminder opens Home without a modal`, async ({page}) => {
        await mockRoutineReminderHome(page, [], {initialMoods: reminder.moods});

        await openSpaRoute(page, `/?checkInReminder=mood&checkInPeriod=MORNING&checkInReminderDate=${reminder.date}`);

        await expect(page.getByRole('dialog', {name: 'Morning mood reminder'})).toHaveCount(0);
        await expect(page).toHaveURL('http://127.0.0.1:4173/');
    });
}

test('login preserves a pending mood reminder', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [], {requiresLogin: true});

    await openSpaRoute(page, `/?checkInReminder=mood&checkInPeriod=EVENING&checkInReminderDate=${date}`);
    await expect(page).toHaveURL(`http://127.0.0.1:4173/login?checkInReminder=mood&checkInPeriod=EVENING&checkInReminderDate=${date}`);
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page.getByRole('dialog', {name: 'Evening mood reminder'})).toBeVisible();
});

test('back pain history accepts multiple episodes on the same day', async ({page}) => {
    await mockAuthenticatedBackPainEpisodes(page);
    await openSpaRoute(page, '/back');

    await page.getByRole('button', {name: 'Add Episode'}).click();
    let dialog = page.getByRole('dialog', {name: 'Back Pain Episode'});
    await dialog.getByRole('button', {name: 'Upper Left'}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'Moderate', exact: true}).click();
    await dialog.locator('#note').fill('After lifting');
    const firstRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await firstRequest).postDataJSON()).toMatchObject({region: 'UPPER', side: 'LEFT', severity: 'MODERATE', note: 'After lifting'});

    await page.getByRole('button', {name: 'Add Episode'}).click();
    dialog = page.getByRole('dialog', {name: 'Back Pain Episode'});
    await dialog.getByRole('button', {name: 'Lower Right'}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'Severe', exact: true}).click();
    const secondRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await secondRequest).postDataJSON()).toMatchObject({region: 'LOWER', side: 'RIGHT', severity: 'SEVERE'});

    const rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('Lower Right');
    await expect(rows.nth(0)).toContainText('Severe');
    await expect(rows.nth(1)).toContainText('Upper Left');
    await expect(rows.nth(1)).toContainText('Moderate');
});

test('dashboard entry modals hide the selected dashboard date', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-11');
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    const scenarios = [
        {tab: 'Body', button: 'New', buttonIndex: 0, dialog: 'Weight'},
        {tab: 'Body', button: 'New', buttonIndex: 1, dialog: 'Blood Preasure'},
        {tab: 'Back', button: 'Add Episode', buttonIndex: 0, dialog: 'Back Pain Episode'},
        {tab: 'Sleep', button: 'New', buttonIndex: 0, dialog: 'Sleep'},
        {tab: 'Mood', button: 'New', buttonIndex: 0, dialog: 'Mood'},
        {tab: 'Calories', button: 'New', buttonIndex: 0, dialog: 'Calories'},
        {tab: 'Workout', button: 'New', buttonIndex: 0, dialog: 'Workout'}
    ];

    for (const scenario of scenarios) {
        await tabs.getByRole('tab', {name: scenario.tab}).click();
        const activePanel = tabs.locator('.p-tabview-panel:visible');
        if (scenario.tab === 'Back') {
            await expect(activePanel.locator('.back-pain-summary-card').filter({hasText: 'Selected Day'})).toContainText('None');
        }
        await activePanel.getByRole('button', {name: scenario.button, exact: true}).nth(scenario.buttonIndex).click();
        const dialog = page.getByRole('dialog', {name: scenario.dialog});
        await expect(dialog).toBeVisible();
        await expect(dialog.locator('label').filter({hasText: /^Date$/})).toHaveCount(0);
        await expect(dialog.locator('.back-pain-date')).toHaveCount(0);
        await dialog.getByRole('button', {name: 'Cancel'}).click();
        await expect(dialog).not.toBeVisible();
    }
});

test('dashboard summarizes categorical back pain severity', async ({page}) => {
    const episodes = [
        {id: 1, date: '2026-08-12', dateFormat: '12/08/2026', time: '08:00:00', timeFormat: '08:00', region: 'LOWER', side: 'LEFT', severity: 'MILD', note: null},
        {id: 2, date: '2026-08-12', dateFormat: '12/08/2026', time: '09:00:00', timeFormat: '09:00', region: 'UPPER', side: 'RIGHT', severity: 'SEVERE', note: null},
        {id: 3, date: '2026-08-05', dateFormat: '05/08/2026', time: '08:00:00', timeFormat: '08:00', region: 'MIDDLE', side: 'CENTER', severity: 'MODERATE', note: null},
        {id: 4, date: '2026-07-20', dateFormat: '20/07/2026', time: '08:00:00', timeFormat: '08:00', region: 'LOWER', side: 'RIGHT', severity: 'EXTREME', note: null}
    ];
    await mockAuthenticatedDashboard(page, '2026-08-12', {backPainEpisodes: episodes});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Back'}).click();
    const summary = tabs.locator('.p-tabview-panel:visible .back-pain-summary');
    await expect(summary.locator('.back-pain-summary-card').filter({hasText: 'Selected Day'})).toContainText('Severe');
    await expect(summary.locator('.back-pain-summary-card').filter({hasText: 'Last Week'})).toContainText('Moderate');
    await expect(summary.locator('.back-pain-summary-card').filter({hasText: 'Change'})).toContainText('Worse');
    await expect(summary.locator('.back-pain-summary-card').filter({hasText: '30-Day Worst'})).toContainText('Extreme');
});

test('dashboard mood uses readable dropdowns and saves the selected dashboard date', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-11');
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Mood'}).click();
    await tabs.locator('.p-tabview-panel:visible').getByRole('button', {name: 'New'}).click();
    const dialog = page.getByRole('dialog', {name: 'Mood'});
    const period = dialog.locator('#period');
    const mood = dialog.locator('#value');
    await expect(period).toContainText('Select period');
    await expect(mood).toContainText('Select mood');
    await expect(period.locator('.p-dropdown-trigger')).toBeVisible();
    await expect(mood.locator('.p-dropdown-trigger')).toBeVisible();
    expect((await period.boundingBox()).width).toBeGreaterThan(150);
    expect((await mood.boundingBox()).width).toBeGreaterThan(150);

    await period.click();
    await page.getByRole('option', {name: 'Morning'}).click();
    await mood.click();
    await page.getByRole('option', {name: /Great/}).click();
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/moods') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await saveRequest).postDataJSON()).toMatchObject({date: '2026-08-11', period: 'MORNING', value: 5});
});

test('history forms keep their date controls', async ({page}) => {
    await mockAuthenticatedDashboard(page);
    await openSpaRoute(page, '/moods');

    await page.getByRole('button', {name: 'New'}).click();
    const dialog = page.getByRole('dialog', {name: 'Mood'});
    await expect(dialog.locator('label').filter({hasText: /^Date$/})).toBeVisible();
});

test('sickness form uses readable dropdowns', async ({page}) => {
    await mockAuthenticatedDashboard(page);
    await openSpaRoute(page, '/sicknesses');

    await page.getByRole('button', {name: 'New'}).click();
    const dialog = page.getByRole('dialog', {name: 'Sickness'});
    const type = dialog.locator('#type');
    const severity = dialog.locator('#severity');
    await expect(type).toContainText('Select type');
    await expect(severity).toContainText('Select severity');
    await expect(type.locator('.p-dropdown-trigger')).toBeVisible();
    await expect(severity.locator('.p-dropdown-trigger')).toBeVisible();
    expect((await type.boundingBox()).width).toBeGreaterThan(150);
    expect((await severity.boundingBox()).width).toBeGreaterThan(150);
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
