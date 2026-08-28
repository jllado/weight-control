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
            routines = routines.map(routine => routine.id === id ? {
                ...routine,
                name: payload.name,
                types: payload.types,
                reminders: payload.reminderTimes.map((time, index) => routine.reminders.find(reminder => reminder.time.slice(0, 5) === time)?.id
                    ? routine.reminders.find(reminder => reminder.time.slice(0, 5) === time)
                    : {id: id * 10 + index, time})
            } : routine);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(routines.find(routine => routine.id === id))});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

async function mockAuthenticatedSettings(page, initialPlan) {
    let coachingPlan = {...initialPlan};
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
        if (path === '/api/coaching-plan' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(coachingPlan)});
        }
        if (path === '/api/coaching-plan' && request.method() === 'PUT') {
            coachingPlan = {...request.postDataJSON(), updatedAt: '2026-08-23T12:00:00Z'};
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(coachingPlan)});
        }
        if (path === '/api/health-constraints') {
            return route.fulfill({contentType: 'application/json', body: '[]'});
        }
        if (path === '/api/push/config') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({enabled: false, publicKey: null, timeZone: 'Europe/Madrid'})});
        }
        if (path === '/api/push/reminder-settings') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({morningTime: '07:30:00', middayTime: '13:30:00', eveningTime: '20:30:00', timeZone: 'Europe/Madrid'})});
        }
        if (path === '/api/weekly-summary/config') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({enabled: false, recipientEmail: 'jllado@gmail.com', deliveryDay: 'SATURDAY', deliveryTime: '08:00:00', timeZone: 'Europe/Madrid'})});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

function workoutResponse(id, payload, exercises) {
    const [year, month, day] = payload.workoutDate.split('-');
    return {
        id,
        workoutDate: payload.workoutDate,
        workoutDateFormat: `${day}/${month}/${year}`,
        note: payload.note,
        assessment: null,
        lines: payload.lines.map((line, position) => {
            const exercise = exercises.find(item => item.id === line.exerciseId);
            const segments = line.segments.map((segment, segmentPosition) => ({position: segmentPosition, ...segment}));
            return {
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                exerciseDescription: exercise.description,
                trackingMode: exercise.trackingMode,
                position,
                calories: line.calories,
                averageHeartRate: line.averageHeartRate,
                sets: exercise.trackingMode === 'CARDIO' ? [] : segments,
                intervals: exercise.trackingMode === 'CARDIO' ? segments : []
            };
        })
    };
}

async function mockAuthenticatedWorkouts(page, initialWorkouts, exercises, {currentRecords = [], historyEvents = [], achievements = [], catalog = [], initialNotifications = [], failWorkoutEvents = false} = {}) {
    let workouts = initialWorkouts.map(workout => ({...workout, lines: workout.lines.map(line => ({...line}))}));
    let notifications = initialNotifications.map(notification => ({...notification}));
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
        if (path === '/api/notifications/pending') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(notifications)});
        }
        const notificationDismissMatch = path.match(/^\/api\/notifications\/(\d+)\/dismiss$/);
        if (notificationDismissMatch && request.method() === 'POST') {
            notifications = notifications.filter(notification => notification.id !== Number(notificationDismissMatch[1]));
            return route.fulfill({status: 204});
        }
        if (path === '/api/workout-exercises' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(exercises)});
        }
        if (path === '/api/workouts' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(workouts)});
        }
        if (path === '/api/personal-records/current') {
            const exerciseId = new URL(request.url()).searchParams.get('exerciseId');
            const records = exerciseId ? currentRecords.filter(record => record.subject.id === Number(exerciseId)) : currentRecords;
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(records)});
        }
        if (path === '/api/personal-records/catalog') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(catalog)});
        }
        if (path === '/api/personal-records/settings' && request.method() === 'PUT') {
            const overrides = new Map(request.postDataJSON().overrides.map(override => [override.metric, override.mode]));
            catalog = catalog.map(metric => ({...metric, mode: overrides.get(metric.key) || metric.defaultMode}));
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(catalog)});
        }
        if (path === '/api/personal-records/history') {
            if (failWorkoutEvents) {
                return route.abort('failed');
            }
            const eventKey = new URL(request.url()).searchParams.get('eventKey');
            const events = eventKey ? historyEvents.filter(event => event.eventKey === eventKey) : historyEvents;
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({items: events, page: 0, size: 100, totalElements: events.length, totalPages: events.length ? 1 : 0})});
        }
        if (path === '/api/workouts' && request.method() === 'POST') {
            const id = workouts.reduce((maximum, workout) => Math.max(maximum, workout.id), 0) + 1;
            const workout = workoutResponse(id, request.postDataJSON(), exercises);
            workouts = [workout, ...workouts];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: workout, recordAchievements: achievements})});
        }
        const workoutMatch = path.match(/^\/api\/workouts\/(\d+)$/);
        if (workoutMatch && request.method() === 'PUT') {
            const id = Number(workoutMatch[1]);
            const current = workouts.find(item => item.id === id);
            const workout = {
                ...workoutResponse(id, request.postDataJSON(), exercises),
                assessment: current.assessment ? {...current.assessment, outdated: true} : null
            };
            workouts = workouts.map(item => item.id === id ? workout : item);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: workout, recordAchievements: []})});
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

async function mockAuthenticatedReflections(page, reflection = null) {
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
        if (path === '/api/reflections') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({
                firstTrackedDate: '2026-07-01',
                lastCompletedDate: '2026-08-13',
                actionConfigured: reflection !== null,
                reflections: reflection === null ? [] : [{
                    reflectionDate: reflection.reflectionDate,
                    generatedAt: reflection.generatedAt,
                    title: reflection.title,
                    planProgressScore: reflection.planProgressScore
                }]
            })});
        }
        if (path === '/api/reflections/2026-08-13') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(reflection)});
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

async function mockRoutineReminderHome(page, initialRoutines, {requiresLogin = false, snoozeExpires = false, pushEnabled = false, initialMoods = [], initialBackPainEpisodes = [], initialNotifications = [], initialWeights = null, initialBloodPressures = [], medicationDose = null, today = madridDate(), dashboardLoad = Promise.resolve(), checkinDelay = 0} = {}) {
    let routines = initialRoutines.map(item => ({...item, reminders: item.reminders.map(reminder => ({...reminder})), times: [...item.times]}));
    let moods = initialMoods.map(item => ({...item}));
    let backPainEpisodes = initialBackPainEpisodes.map(item => ({...item}));
    let notifications = initialNotifications.map(item => ({...item}));
    let reminderSettings = {morningTime: '07:30:00', middayTime: '13:30:00', eveningTime: '20:30:00', timeZone: 'Europe/Madrid'};
    let routinesDone = routines.filter(item => item.times.length > 0).length;
    const date = today;
    let weights = (initialWeights ?? [{
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
    }]).map(item => ({...item}));
    let bloodPressures = initialBloodPressures.map(item => ({...item}));
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', async route => {
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
        if (path === '/api/notifications/pending' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(notifications)});
        }
        if (path === '/api/notifications/dismiss-all' && request.method() === 'POST') {
            notifications = [];
            return route.fulfill({status: 204});
        }
        const notificationDismissMatch = path.match(/^\/api\/notifications\/(\d+)\/dismiss$/);
        if (notificationDismissMatch && request.method() === 'POST') {
            const id = Number(notificationDismissMatch[1]);
            notifications = notifications.filter(notification => notification.id !== id);
            return route.fulfill({status: 204});
        }
        if (path === '/api/routines' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(routines)});
        }
        const medicationDoseMatch = path.match(/^\/api\/medications\/doses\/(\d+)$/);
        if (medicationDoseMatch && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(medicationDose)});
        }
        const medicationTakeMatch = path.match(/^\/api\/medications\/doses\/(\d+)\/take$/);
        if (medicationTakeMatch && request.method() === 'POST') {
            medicationDose = {...medicationDose, status: 'TAKEN', takenAt: request.postDataJSON().takenAt};
            notifications = notifications.filter(notification => notification.type !== 'MEDICATION');
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(medicationDose)});
        }
        const medicationSnoozeMatch = path.match(/^\/api\/medications\/doses\/(\d+)\/snooze$/);
        if (medicationSnoozeMatch && request.method() === 'POST') {
            const nextReminderAt = new Date(Date.now() + request.postDataJSON().minutes * 60 * 1000).toISOString();
            medicationDose = {...medicationDose, status: 'SNOOZED', snoozedUntil: nextReminderAt};
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({nextReminderAt})});
        }
        if (path === '/api/moods' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(moods)});
        }
        if (path === '/api/moods' && request.method() === 'POST') {
            const mood = {id: moods.length + 1, ...request.postDataJSON()};
            moods = [mood, ...moods];
            notifications = notifications.filter(notification => notification.type !== 'MOOD');
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: mood, recordAchievements: []})});
        }
        if (path === '/api/back-pain-episodes' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(backPainEpisodes)});
        }
        if (path === '/api/back-pain-episodes' && request.method() === 'POST') {
            const episode = {id: backPainEpisodes.length + 1, time: '12:34:00', timeFormat: '12:34', ...request.postDataJSON()};
            backPainEpisodes = [episode, ...backPainEpisodes];
            notifications = notifications.filter(notification => notification.type !== 'BACK');
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(episode)});
        }
        if (path === '/api/weights' && request.method() === 'GET') {
            await dashboardLoad;
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(weights)});
        }
        if (path === '/api/weights' && request.method() === 'POST') {
            const payload = request.postDataJSON();
            const weight = {
                id: weights.length + 1,
                ...payload,
                lostWeight: 0,
                fat: payload.weight * payload.fatPercentage / 100,
                lostFat: 0,
                musclePercentage: payload.muscle * 100 / payload.weight,
                lostMuscle: 0,
                photoFront: null,
                photoRight: null,
                photoLeft: null
            };
            weights = [weight, ...weights];
            notifications = notifications.filter(notification => notification.type !== 'WEIGHT');
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: weight, recordAchievements: []})});
        }
        if (path === '/api/blood-pressures' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(bloodPressures)});
        }
        if (path === '/api/blood-pressures' && request.method() === 'POST') {
            const payload = request.postDataJSON();
            const bloodPressure = {id: bloodPressures.length + 1, ...payload, lostUpper: 0, lostLower: 0};
            bloodPressures = [bloodPressure, ...bloodPressures];
            notifications = notifications.filter(notification => notification.type !== 'BLOOD_PRESSURE');
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: bloodPressure, recordAchievements: []})});
        }
        const checkinMatch = path.match(/^\/api\/routines\/(\d+)\/checkins$/);
        if (checkinMatch && request.method() === 'POST') {
            await new Promise(resolve => setTimeout(resolve, checkinDelay));
            const id = Number(checkinMatch[1]);
            const checkedAt = request.postDataJSON().date;
            routines = routines.map(item => item.id === id ? {...item, times: [...item.times, checkedAt], currentStrike: item.currentStrike + 1, bestStrike: Math.max(item.bestStrike, item.currentStrike + 1), lastTimeDate: checkedAt} : item);
            routinesDone = routines.filter(item => item.times.length > 0).length;
            notifications = notifications.filter(notification => notification.type !== 'ROUTINE');
            const routineSummary = {...routines.find(item => item.id === id)};
            delete routineSummary.times;
            return route.fulfill({
                contentType: 'application/json',
                body: JSON.stringify({
                    result: {
                        routine: routineSummary,
                        checkedAt,
                        changed: true,
                        dashboard: routineReminderDashboard(date, routinesDone)
                    },
                    recordAchievements: []
                })
            });
        }
        const snoozeMatch = path.match(/^\/api\/routines\/(\d+)\/reminders\/(\d+)\/snooze$/);
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

async function mockAuthenticatedDashboard(page, selectedDate = dashboard.anchorDate, {requiresLogin = false, backPainEpisodes = [], initialMeals = [], initialFastingPeriods = [], initialLipidPanels = [], initialSleeps = [], initialWorkouts = [], sleepLoad = Promise.resolve(), workoutLoad = Promise.resolve(), currentRecords = [], dashboardResponse, onApiRequest} = {}) {
    let authenticated = !requiresLogin;
    const decisionOutcomes = [];
    let meals = initialMeals.map(meal => ({...meal}));
    let fastingPeriods = initialFastingPeriods.map(period => ({...period}));
    let lipidPanels = initialLipidPanels.map(panel => ({...panel}));
    const sleeps = initialSleeps.map(sleep => ({...sleep}));
    const workouts = initialWorkouts.map(workout => ({...workout, lines: workout.lines.map(line => ({...line}))}));
    const lastWeekDate = new Date(`${selectedDate}T12:00:00Z`);
    lastWeekDate.setUTCDate(lastWeekDate.getUTCDate() - 7);
    const selectedDashboard = dashboardResponse ?? {
        ...dashboard,
        anchorDate: selectedDate,
        dailyStatus: dashboardDailyStatus(selectedDate),
        lastWeekDailyStatus: dashboardDailyStatus(lastWeekDate.toISOString().slice(0, 10))
    };
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const path = url.pathname;
        onApiRequest?.(path);
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
        if (path === '/api/personal-records/current') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(currentRecords)});
        }
        if (path === '/api/personal-records/history') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({items: [], page: 0, size: 100, totalElements: 0, totalPages: 0})});
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
        if (path === '/api/sleeps') {
            await sleepLoad;
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(sleeps)});
        }
        if (path === '/api/workouts/dashboard' && request.method() === 'GET') {
            await workoutLoad;
            const date = url.searchParams.get('date');
            const previousWeekDate = new Date(`${date}T12:00:00Z`);
            previousWeekDate.setUTCDate(previousWeekDate.getUTCDate() - 7);
            const previousWeek = previousWeekDate.toISOString().slice(0, 10);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({
                currentWorkout: workouts.find(workout => workout.workoutDate === date) || null,
                previousWeekWorkout: workouts.find(workout => workout.workoutDate === previousWeek) || null,
                preloadWorkouts: workouts.filter(workout => workout.workoutDate < date).slice(0, 10),
                recordEvents: []
            })});
        }
        if (path === '/api/lipid-panels' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(lipidPanels)});
        }
        if (path === '/api/lipid-panels' && request.method() === 'POST') {
            const payload = request.postDataJSON();
            const id = lipidPanels.reduce((maximum, panel) => Math.max(maximum, panel.id), 0) + 1;
            const panel = {id, dateFormat: payload.date.split('-').reverse().join('/'), ...payload};
            lipidPanels = [panel, ...lipidPanels].sort((left, right) => right.date.localeCompare(left.date));
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: panel, recordAchievements: []})});
        }
        const lipidPanelMatch = path.match(/^\/api\/lipid-panels\/(\d+)$/);
        if (lipidPanelMatch && request.method() === 'PUT') {
            const id = Number(lipidPanelMatch[1]);
            const payload = request.postDataJSON();
            lipidPanels = lipidPanels
                .map(panel => panel.id === id ? {...panel, ...payload, dateFormat: payload.date.split('-').reverse().join('/')} : panel)
                .sort((left, right) => right.date.localeCompare(left.date));
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: lipidPanels.find(panel => panel.id === id), recordAchievements: []})});
        }
        if (lipidPanelMatch && request.method() === 'DELETE') {
            const id = Number(lipidPanelMatch[1]);
            lipidPanels = lipidPanels.filter(panel => panel.id !== id);
            return route.fulfill({status: 200, contentType: 'application/json', body: '{}'});
        }
        if (path === '/api/back-pain-episodes') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(backPainEpisodes)});
        }
        if (path === '/api/meals' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(meals)});
        }
        if (path === '/api/meals' && request.method() === 'POST') {
            const payload = request.postDataJSON();
            const existingSnackSequences = meals.filter(meal => meal.date === payload.date && meal.mealType === 'SNACK').map(meal => meal.mealSequence);
            let mealSequence = 1;
            while (existingSnackSequences.includes(mealSequence)) {
                mealSequence++;
            }
            const meal = {id: meals.length + 1, dateFormat: payload.date.split('-').reverse().join('/'), mealSequence: payload.mealType === 'SNACK' ? mealSequence : 1, source: 'MANUAL', ...payload};
            meals = [...meals, meal];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: meal, recordAchievements: []})});
        }
        const mealMatch = path.match(/^\/api\/meals\/(\d+)$/);
        if (mealMatch && request.method() === 'PUT') {
            const id = Number(mealMatch[1]);
            meals = meals.map(meal => meal.id === id ? {...meal, ...request.postDataJSON()} : meal);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: meals.find(meal => meal.id === id), recordAchievements: []})});
        }
        if (mealMatch && request.method() === 'DELETE') {
            const id = Number(mealMatch[1]);
            meals = meals.filter(meal => meal.id !== id);
            return route.fulfill({status: 200, contentType: 'application/json', body: '{}'});
        }
        if (path === '/api/calories') {
            const totals = Object.values(meals.reduce((result, meal) => {
                result[meal.date] = result[meal.date] || {date: meal.date, dateFormat: meal.dateFormat, calories: 0};
                result[meal.date].calories += meal.calories;
                return result;
            }, {}));
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(totals)});
        }
        if (path === '/api/nutrition/daily-summaries') {
            const summaries = Object.values(meals.reduce((result, meal) => {
                result[meal.date] = result[meal.date] || {date: meal.date, dateFormat: meal.dateFormat, calories: 0, meals: []};
                result[meal.date].calories += meal.calories;
                result[meal.date].meals.push(meal);
                return result;
            }, {})).map(summary => ({
                date: summary.date,
                dateFormat: summary.dateFormat,
                calories: summary.calories,
                proteinGrams: totalRecorded(summary.meals, 'proteinGrams'),
                carbohydrateGrams: totalRecorded(summary.meals, 'carbohydrateGrams'),
                fatGrams: totalRecorded(summary.meals, 'fatGrams'),
                macrosComplete: summary.meals.every(meal => meal.proteinGrams !== null && meal.carbohydrateGrams !== null && meal.fatGrams !== null)
            }));
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(summaries)});
        }
        if (path === '/api/fasting-periods' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(fastingPeriods)});
        }
        if (path === '/api/fasting-periods' && request.method() === 'POST') {
            const payload = request.postDataJSON();
            const period = {id: fastingPeriods.length + 1, startTimeFormat: payload.startTime, endTimeFormat: payload.endTime, ...payload};
            fastingPeriods = [period, ...fastingPeriods];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(period)});
        }
        const fastingPeriodMatch = path.match(/^\/api\/fasting-periods\/(\d+)$/);
        if (fastingPeriodMatch && request.method() === 'PUT') {
            const id = Number(fastingPeriodMatch[1]);
            fastingPeriods = fastingPeriods.map(period => period.id === id ? {...period, ...request.postDataJSON()} : period);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(fastingPeriods.find(period => period.id === id))});
        }
        if (fastingPeriodMatch && request.method() === 'DELETE') {
            const id = Number(fastingPeriodMatch[1]);
            fastingPeriods = fastingPeriods.filter(period => period.id !== id);
            return route.fulfill({status: 200, contentType: 'application/json', body: '{}'});
        }
        if (path === '/api/reflections') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({reflections: [], actionConfigured: false})});
        }
        if (path === '/api/decision-outcomes' && request.method() === 'POST') {
            const outcome = request.postDataJSON();
            decisionOutcomes.push(outcome);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: {id: decisionOutcomes.length, ...outcome}, recordAchievements: []})});
        }
        if (path === '/api/moods' && request.method() === 'POST') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: {id: 1, ...request.postDataJSON()}, recordAchievements: []})});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
    return decisionOutcomes;
}

function totalRecorded(meals, field) {
    const values = meals.map(meal => meal[field]).filter(value => value !== null);
    return values.length ? values.reduce((total, value) => total + value, 0) : null;
}

function routine(id, name, reminderTimes) {
    const times = Array.isArray(reminderTimes) ? reminderTimes : reminderTimes ? [reminderTimes] : [];
    return {
        id,
        startDate: '2026-08-01T00:00:00+02:00',
        lastTimeDate: null,
        name,
        reminders: times.map((time, index) => ({id: id * 10 + index, time})),
        currentStrike: 0,
        bestStrike: 0,
        types: ['WEIGHT'],
        times: []
    };
}

function reminderWeight(date) {
    return {
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
    };
}

function medicationReminderDose(status = 'PENDING') {
    return {
        id: 50,
        medicationId: 5,
        scheduledAt: '2026-08-22T08:00:00+02:00',
        status,
        source: 'SCHEDULED',
        takenAt: null,
        snoozedUntil: null,
        medicationName: 'Vitamin D',
        doseAmount: 1,
        doseUnit: 'tablet',
        notes: 'Take with breakfast'
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

test('Coach launcher is authenticated and opens the configured GPT in a new tab', async ({page}) => {
    await mockLogin(page);
    await page.goto('/');
    await expect(page).toHaveURL('http://127.0.0.1:4173/login');
    await expect(page.getByRole('button', {name: 'Open Coach'})).toHaveCount(0);

    const authenticatedPage = await page.context().newPage();
    await mockAuthenticatedReflections(authenticatedPage);
    await authenticatedPage.context().route('https://chatgpt.test/**', route => route.fulfill({
        contentType: 'text/html',
        body: '<title>Weight Control Coach</title>'
    }));
    await openSpaRoute(authenticatedPage, '/reflections');

    const coachPagePromise = authenticatedPage.context().waitForEvent('page');
    await authenticatedPage.getByRole('button', {name: 'Open Coach'}).click();
    const coachPage = await coachPagePromise;
    await expect(coachPage).toHaveURL('https://chatgpt.test/g/weight-control-coach');
    expect(await coachPage.evaluate(() => window.opener)).toBeNull();
    await coachPage.close();
    await authenticatedPage.close();
});

test('workout diary shows Coach assessments and opens a dated reassessment prompt', async ({page, context}) => {
    const exercises = [{id: 1, name: 'Bench press', description: 'Horizontal press.', trackingMode: 'REPS'}];
    const workout = {
        id: 7,
        workoutDate: '2026-08-20',
        workoutDateFormat: '20/08/2026',
        note: 'Upper body',
        assessment: {
            goalAlignmentScore: 8,
            estimatedTrainingDemandScore: 7,
            rationale: 'Strong alignment with the current strength goal.',
            strength: 'Consistent compound work.',
            improvement: 'Add one pulling set.',
            nextWorkoutAction: 'Repeat with controlled progression.',
            goalSnapshot: 'Improve upper-body strength',
            createdAt: '2026-08-20T18:30:00Z',
            updatedAt: '2026-08-20T18:30:00Z',
            outdated: true
        },
        lines: [{exerciseId: 1, exerciseName: 'Bench press', exerciseDescription: 'Horizontal press.', trackingMode: 'REPS', position: 0, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 8, durationSeconds: null, weight: 60}], intervals: []}]
    };
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route('https://chatgpt.test/**', route => route.fulfill({
        contentType: 'text/html',
        body: '<title>Weight Control Coach</title>'
    }));
    await mockAuthenticatedWorkouts(page, [workout], exercises);
    await openSpaRoute(page, '/workouts');

    const row = page.locator('tbody tr').filter({hasText: 'Bench press'});
    await expect(row.getByText('Goal 8 · Demand 7')).toBeVisible();
    await expect(row.getByText('Outdated', {exact: true})).toBeVisible();
    await row.getByText('Goal 8 · Demand 7').click();
    const dialog = page.getByRole('dialog', {name: 'Workout assessment'});
    await expect(dialog).toContainText('This assessment is outdated because the workout changed.');
    await expect(dialog).toContainText('Improve upper-body strength');
    await expect(dialog).toContainText('Add one pulling set.');
    await dialog.locator('.p-dialog-footer').getByRole('button', {name: 'Close'}).click();

    const coachPagePromise = context.waitForEvent('page');
    await row.getByRole('button', {name: 'Reassess with Coach'}).click();
    const coachPage = await coachPagePromise;
    await expect(coachPage).toHaveURL('https://chatgpt.test/g/weight-control-coach');
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('Assess my workout on 2026-08-20 against my active coaching plan.');
    await coachPage.close();
});

test('workout exercises can be reordered while editing or preloading a new workout', async ({page}) => {
    const exercises = [
        {id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS'},
        {id: 2, name: 'Bench press', description: 'Horizontal press.', trackingMode: 'REPS'},
        {id: 3, name: 'Plank', description: 'Static core brace.', trackingMode: 'SECONDS'}
    ];
    const workout = {
        id: 7,
        workoutDate: '2026-08-10',
        workoutDateFormat: '10/08/2026',
        note: 'Strength',
        lines: [
            {exerciseId: 1, exerciseName: 'Squat', exerciseDescription: 'Lower-body squat.', trackingMode: 'REPS', position: 0, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 10, durationSeconds: null, weight: 40}], intervals: []},
            {exerciseId: 2, exerciseName: 'Bench press', exerciseDescription: 'Horizontal press.', trackingMode: 'REPS', position: 1, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 8, durationSeconds: null, weight: 50}], intervals: []},
            {exerciseId: 3, exerciseName: 'Plank', exerciseDescription: 'Static core brace.', trackingMode: 'SECONDS', position: 2, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: null, durationSeconds: 60, weight: null}], intervals: []}
        ]
    };
    await page.clock.setFixedTime(new Date('2026-08-20T08:00:00Z'));
    await mockAuthenticatedWorkouts(page, [workout], exercises);
    await openSpaRoute(page, '/workouts');

    await page.locator('tbody tr').filter({hasText: 'Squat'}).getByRole('button', {name: 'Edit workout'}).click();
    let dialog = page.getByRole('dialog', {name: 'Workout'});
    let cards = dialog.locator('.workout-line-card');
    await expect(cards).toHaveCount(3);
    await expect(cards.nth(0).getByRole('button', {name: 'Move exercise 1 up'})).toBeDisabled();
    await expect(cards.nth(2).getByRole('button', {name: 'Move exercise 3 down'})).toBeDisabled();
    await cards.nth(0).getByRole('button', {name: 'Move exercise 1 down'}).click();
    await cards.nth(2).getByRole('button', {name: 'Move exercise 3 up'}).click();
    await expect(cards.nth(0)).toContainText('Bench press');
    await expect(cards.nth(1)).toContainText('Plank');
    await expect(cards.nth(2)).toContainText('Squat');
    const updateRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts/7') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await updateRequest).postDataJSON().lines.map(line => line.exerciseId)).toEqual([2, 3, 1]);
    await expect(dialog).not.toBeVisible();
    await expect(page.locator('tbody tr').first()).toContainText('Bench press');
    await expect(page.locator('tbody tr').first()).toContainText('Plank');
    await expect(page.locator('tbody tr').first()).toContainText('Squat');

    await page.getByRole('button', {name: 'New', exact: true}).click();
    dialog = page.getByRole('dialog', {name: 'Workout'});
    const preloadField = dialog.locator('.p-field').filter({hasText: 'Preload workout'});
    await preloadField.locator('.p-dropdown').click();
    await page.getByRole('option', {name: '10/08/2026 - Bench press'}).click();
    cards = dialog.locator('.workout-line-card');
    await expect(cards).toHaveCount(3);
    await cards.nth(0).getByRole('button', {name: 'Move exercise 1 down'}).click();
    const createRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await createRequest).postDataJSON().lines.map(line => line.exerciseId)).toEqual([3, 2, 1]);
    await expect(dialog).not.toBeVisible();
});

test('records page shows current records and paginated progression history', async ({page}) => {
    const exercises = [{id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS'}];
    const bodyRecord = personalRecord({metric: 'BODY_WEIGHT', metricLabel: 'Lowest weight', domain: 'BODY', value: 79, unit: 'KG', subject: {type: 'BODY', id: null, label: 'Body'}});
    const workoutRecord = personalRecord({metric: 'WORKOUT_REPETITIONS', metricLabel: 'Most repetitions', domain: 'WORKOUT', value: 12, unit: 'REPETITIONS', subject: {type: 'EXERCISE', id: 1, label: 'Squat'}, qualifier: {loadKg: 40, label: '40 kg'}});
    const moodRecord = personalRecord({metric: 'MOOD_MAXIMUM', metricLabel: 'Highest mood', domain: 'RECOVERY', value: 5, unit: 'SCORE_OUT_OF_FIVE', subject: {type: 'RECOVERY', id: null, label: 'Mood'}});
    const bmiRecord = personalRecord({metric: 'BODY_BMI_MINIMUM', metricLabel: 'Lowest BMI', domain: 'BODY', value: 24.69, unit: 'KG_PER_SQUARE_METER', subject: {type: 'BODY_CHANGE', id: null, label: 'BMI'}});
    const volumeRecord = personalRecord({metric: 'WORKOUT_STRENGTH_VOLUME_MAXIMUM', metricLabel: 'Highest strength volume', domain: 'WORKOUT', value: 1200, unit: 'KG_REPETITIONS', subject: {type: 'WORKOUT_TOTAL', id: null, label: 'Workout session'}});
    const habitRecord = personalRecord({metric: 'HABIT_COMPLETION_TOTAL_MAXIMUM', metricLabel: 'Most habit completions', domain: 'BEHAVIOR', value: 12, unit: 'COMPLETIONS', recordDate: null, subject: {type: 'HABIT', id: 3, label: 'Read'}, source: {type: 'HABIT_BASELINE', id: 4, linePosition: null, segmentPosition: null}});
    const historyEvents = [
        {...workoutRecord, kind: 'TIED', previousValue: 12, currentRecord: true, source: {type: 'WORKOUT', id: 7, linePosition: 0, segmentPosition: 0}},
        {...bodyRecord, kind: 'IMPROVED', previousValue: 80, currentRecord: true, source: {type: 'WEIGHT', id: 2, linePosition: null, segmentPosition: null}},
        {...habitRecord, kind: 'FIRST', previousValue: null, currentRecord: true}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises, {currentRecords: [bodyRecord, workoutRecord, moodRecord, bmiRecord, volumeRecord, habitRecord], historyEvents});

    await openSpaRoute(page, '/records');
    const currentPanel = page.locator('.p-tabview-panel:visible');
    await expect(currentPanel.getByText('Lowest weight', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Most repetitions', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Highest mood', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('5/5', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('24.69 kg/m²', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('1200 kg·reps', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Most habit completions', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Legacy baseline', {exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'History'}).click();
    const historyPanel = page.locator('.p-tabview-panel:visible');
    await expect(historyPanel.getByText('Tied PR', {exact: true})).toBeVisible();
    await expect(historyPanel.getByText('79 kg', {exact: true})).toBeVisible();
    await expect(historyPanel.getByText('Legacy baseline', {exact: true})).toBeVisible();
    await page.setViewportSize({width: 390, height: 844});
    await expect(historyPanel).toBeVisible();
});

test('record settings save overrides atomically and reset to defaults', async ({page}) => {
    const catalog = [
        {key: 'BODY_WEIGHT', label: 'Body weight', domain: 'BODY', unit: 'KG', precision: 2, defaultMode: 'MINIMUM', mode: 'MINIMUM', directions: [
            {direction: 'MINIMUM', metric: 'BODY_WEIGHT', label: 'Lowest weight'},
            {direction: 'MAXIMUM', metric: 'BODY_WEIGHT_MAXIMUM', label: 'Highest weight'}
        ]},
        {key: 'MOOD', label: 'Mood', domain: 'RECOVERY', unit: 'SCORE_OUT_OF_FIVE', precision: 0, defaultMode: 'MAXIMUM', mode: 'MAXIMUM', directions: [
            {direction: 'MINIMUM', metric: 'MOOD_MINIMUM', label: 'Lowest mood'},
            {direction: 'MAXIMUM', metric: 'MOOD_MAXIMUM', label: 'Highest mood'}
        ]}
    ];
    await mockAuthenticatedWorkouts(page, [], [], {catalog});
    await openSpaRoute(page, '/records');
    await page.getByRole('tab', {name: 'Settings'}).click();

    const weightSetting = page.locator('.record-setting-row').filter({hasText: 'Body weight'});
    await expect(weightSetting).toContainText('default: Minimum');
    await weightSetting.locator('.p-dropdown').click();
    await page.getByRole('option', {name: 'Both'}).click();
    const saveOverride = page.waitForRequest(request => request.url().endsWith('/api/personal-records/settings') && request.method() === 'PUT');
    await page.getByRole('button', {name: 'Save'}).click();
    expect((await saveOverride).postDataJSON()).toEqual({overrides: [{metric: 'BODY_WEIGHT', mode: 'BOTH'}]});
    await expect(page.getByText('Personal record settings updated')).toBeVisible();
    await expect(page.getByRole('dialog', {name: 'Personal records'})).not.toBeVisible();

    await page.getByRole('button', {name: 'Reset to defaults'}).click();
    const saveDefaults = page.waitForRequest(request => request.url().endsWith('/api/personal-records/settings') && request.method() === 'PUT');
    await page.getByRole('button', {name: 'Save'}).click();
    expect((await saveDefaults).postDataJSON()).toEqual({overrides: []});
});

test('habit check-ins expose legacy context and can be completed and undone', async ({page}) => {
    const today = madridDate();
    let habit = {
        id: 3,
        startDate: '2025-01-01T00:00:00+01:00',
        duration: 30,
        lastTimeDate: null,
        name: 'Read',
        times: 12,
        currentStrike: 3,
        bestStrike: 7,
        checkins: [],
        legacyBaseline: {completionTotal: 12, currentStreak: 3, bestStreak: 7, lastDate: null}
    };
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({contentType: 'application/javascript', body: googleClientScript}));
    await page.route('**/api/**', route => {
        const request = route.request();
        const url = new URL(request.url());
        if (url.pathname === '/api/auth/me') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: 'jllado@gmail.com', authenticated: true})});
        }
        if (url.pathname === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        if (url.pathname === '/api/habits' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify([habit])});
        }
        if (url.pathname === '/api/habits/3/complete' && request.method() === 'POST') {
            habit = {...habit, times: 13, currentStrike: 1, checkins: [today], lastTimeDate: `${today}T00:00:00+02:00`};
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: habit, recordAchievements: []})});
        }
        if (url.pathname === '/api/habits/3/checkins' && request.method() === 'DELETE') {
            habit = {...habit, times: 12, currentStrike: 3, checkins: [], lastTimeDate: null};
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(habit)});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });

    await openSpaRoute(page, '/habits');
    await expect(page.getByText('Includes a legacy baseline with no recorded date.')).toBeVisible();
    const completeRequest = page.waitForRequest(request => request.url().includes(`/api/habits/3/complete?date=${today}`));
    await page.getByRole('button', {name: 'Complete today'}).click();
    await completeRequest;
    await expect(page.getByRole('button', {name: 'Undo today'})).toBeVisible();
    const undoRequest = page.waitForRequest(request => request.url().includes(`/api/habits/3/checkins?date=${today}`) && request.method() === 'DELETE');
    await page.getByRole('button', {name: 'Undo today'}).click();
    await undoRequest;
    await expect(page.getByRole('button', {name: 'Complete today'})).toBeVisible();
});

test('workout records provide context and celebrate without a blocking record dialog', async ({page}) => {
    await page.emulateMedia({reducedMotion: 'reduce'});
    await page.clock.setFixedTime(new Date('2026-08-20T08:00:00Z'));
    const exercises = [{id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS'}];
    const workout = {
        id: 7,
        workoutDate: '2026-08-10',
        workoutDateFormat: '10/08/2026',
        note: 'Strength',
        lines: [{exerciseId: 1, exerciseName: 'Squat', exerciseDescription: 'Lower-body squat.', trackingMode: 'REPS', position: 0, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 10, durationSeconds: null, weight: 40}], intervals: []}]
    };
    const heaviest = personalRecord({metric: 'WORKOUT_HEAVIEST_LOAD', metricLabel: 'Heaviest load', domain: 'WORKOUT', value: 50, unit: 'KG', subject: {type: 'EXERCISE', id: 1, label: 'Squat'}});
    const zeroLoad = personalRecord({metric: 'WORKOUT_HEAVIEST_LOAD', metricLabel: 'Heaviest load', domain: 'WORKOUT', value: 0, unit: 'KG', subject: {type: 'EXERCISE', id: 1, label: 'Squat'}});
    const repetitions = personalRecord({metric: 'WORKOUT_REPETITIONS', metricLabel: 'Most repetitions', domain: 'WORKOUT', value: 10, unit: 'REPETITIONS', subject: {type: 'EXERCISE', id: 1, label: 'Squat'}, qualifier: {loadKg: 40, label: '40 kg'}});
    const source = {type: 'WORKOUT', id: 7, linePosition: 0, segmentPosition: 0};
    const achievement = {...heaviest, value: 55, kind: 'IMPROVED', previousValue: 40, source: {type: 'WORKOUT', id: 8, linePosition: 0, segmentPosition: 0}};
    await mockAuthenticatedWorkouts(page, [workout], exercises, {
        currentRecords: [heaviest, zeroLoad, repetitions],
        historyEvents: [{...heaviest, kind: 'IMPROVED', previousValue: 40, currentRecord: true, source}, {...repetitions, kind: 'TIED', previousValue: 10, currentRecord: true, source}],
        achievements: [achievement]
    });

    await openSpaRoute(page, '/workouts');
    const row = page.locator('tbody tr').filter({hasText: 'Squat'});
    await expect(row.getByText('PR', {exact: true})).toBeVisible();
    await expect(row.getByText('Tied PR', {exact: true})).toBeVisible();
    await row.getByRole('button', {name: 'Edit workout'}).click();
    const editDialog = page.getByRole('dialog', {name: 'Workout'});
    await expect(editDialog.getByText('Weight', {exact: true}).locator('..').locator('.field-record-context')).toHaveText('Heaviest load: 50 kg');
    await expect(editDialog.getByText('Repetitions').locator('..').locator('.field-record-context')).toHaveText('Most repetitions: 10 reps');
    await editDialog.getByRole('button', {name: 'Cancel'}).click();

    await page.getByRole('button', {name: 'New', exact: true}).click();
    const createDialog = page.getByRole('dialog', {name: 'Workout'});
    await createDialog.getByText('Select exercise').click();
    await page.getByRole('option', {name: 'Squat'}).click();
    await createDialog.getByText('Repetitions').locator('..').locator('input').fill('8');
    await createDialog.getByText('Weight', {exact: true}).locator('..').locator('input').fill('55');
    await createDialog.getByRole('button', {name: 'Save'}).click();

    await expect(page.locator('.win-celebration')).toBeVisible();
    await expect(page.getByRole('dialog', {name: 'Personal records'})).not.toBeVisible();
});

test('workout diary clears loading when record history fails', async ({page}) => {
    const exercises = [{id: 1, name: 'Deadlift', description: 'Hip hinge.', trackingMode: 'REPS'}];
    const workout = {
        id: 7,
        workoutDate: '2026-08-10',
        workoutDateFormat: '10/08/2026',
        note: 'Strength',
        lines: [{exerciseId: 1, exerciseName: 'Deadlift', exerciseDescription: 'Hip hinge.', trackingMode: 'REPS', position: 0, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 5, durationSeconds: null, weight: 80}], intervals: []}]
    };
    await mockAuthenticatedWorkouts(page, [workout], exercises, {failWorkoutEvents: true});

    await openSpaRoute(page, '/workouts');

    await expect(page.locator('.p-datatable-loading-overlay')).toHaveCount(0);
    await page.getByRole('tab', {name: 'Exercises'}).click();
    await expect(page.locator('tbody tr').filter({hasText: 'Deadlift'})).toBeVisible();
});

test('workout records appear below their related cardio inputs', async ({page}) => {
    const exercises = [{id: 1, name: 'Walking', description: 'Steady-state walking cardio.', trackingMode: 'CARDIO'}];
    const workout = {
        id: 7,
        workoutDate: '2026-08-10',
        workoutDateFormat: '10/08/2026',
        note: null,
        lines: [{exerciseId: 1, exerciseName: 'Walking', exerciseDescription: 'Steady-state walking cardio.', trackingMode: 'CARDIO', position: 0, calories: 128, averageHeartRate: 94, sets: [], intervals: [{position: 0, durationSeconds: 1800, speedKph: 3, distanceKm: null, inclinePercent: 12, resistanceLevel: null}]}]
    };
    const records = [
        personalRecord({metric: 'CARDIO_DURATION', metricLabel: 'Longest interval', domain: 'WORKOUT', value: 2700, unit: 'SECONDS', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'CARDIO_SPEED', metricLabel: 'Highest speed', domain: 'WORKOUT', value: 6, unit: 'KM_PER_HOUR', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'CARDIO_DISTANCE', metricLabel: 'Longest distance', domain: 'WORKOUT', value: 163, unit: 'KM', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'CARDIO_INCLINE', metricLabel: 'Highest incline', domain: 'WORKOUT', value: 12, unit: 'PERCENT', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'CARDIO_RESISTANCE', metricLabel: 'Highest resistance', domain: 'WORKOUT', value: 8, unit: 'LEVEL', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'WORKOUT_CALORIES', metricLabel: 'Highest workout calories', domain: 'WORKOUT', value: 355, unit: 'KCAL', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'WORKOUT_AVERAGE_HEART_RATE', metricLabel: 'Highest workout heart rate', domain: 'WORKOUT', value: 160, unit: 'BPM', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}})
    ];
    await mockAuthenticatedWorkouts(page, [workout], exercises, {currentRecords: records});
    await openSpaRoute(page, '/workouts');

    await page.locator('tbody tr').filter({hasText: 'Walking'}).getByRole('button', {name: 'Edit workout'}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    await expect(dialog.getByText('Calories').locator('..').locator('.field-record-context')).toHaveText('Highest workout calories: 355 kcal');
    await expect(dialog.getByText('Average Heart Rate (bpm)').locator('..').locator('.field-record-context')).toHaveText('Highest workout heart rate: 160 bpm');
    await expect(dialog.getByText('Minutes').locator('..').locator('.field-record-context')).toHaveText('Longest interval: 45:00');
    await expect(dialog.getByText('Speed (km/h)').locator('..').locator('.field-record-context')).toHaveText('Highest speed: 6 km/h');
    await expect(dialog.getByText('Distance (km)').locator('..').locator('.field-record-context')).toHaveText('Longest distance: 163 km');
    await expect(dialog.getByText('Incline (%)').locator('..').locator('.field-record-context')).toHaveText('Highest incline: 12%');
    await expect(dialog.getByText('Resistance').locator('..').locator('.field-record-context')).toHaveText('Highest resistance: Level 8');
    await page.setViewportSize({width: 1440, height: 900});
    await expect(dialog.getByText('Speed (km/h)').locator('..').locator('.field-record-context')).toHaveText('Highest speed: 6 km/h');
});

test('duration exercise records appear below their related inputs', async ({page}) => {
    const exercises = [{id: 1, name: 'Plank', description: 'Static core brace.', trackingMode: 'SECONDS'}];
    const workout = {
        id: 7,
        workoutDate: '2026-08-10',
        workoutDateFormat: '10/08/2026',
        note: null,
        lines: [{exerciseId: 1, exerciseName: 'Plank', exerciseDescription: 'Static core brace.', trackingMode: 'SECONDS', position: 0, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: null, durationSeconds: 75, weight: 5}], intervals: []}]
    };
    const records = [
        personalRecord({metric: 'WORKOUT_HEAVIEST_LOAD', metricLabel: 'Heaviest load', domain: 'WORKOUT', value: 10, unit: 'KG', subject: {type: 'EXERCISE', id: 1, label: 'Plank'}}),
        personalRecord({metric: 'WORKOUT_DURATION', metricLabel: 'Longest duration', domain: 'WORKOUT', value: 90, unit: 'SECONDS', subject: {type: 'EXERCISE', id: 1, label: 'Plank'}, qualifier: {loadKg: 5, label: '5 kg'}})
    ];
    await mockAuthenticatedWorkouts(page, [workout], exercises, {currentRecords: records});
    await openSpaRoute(page, '/workouts');

    await page.locator('tbody tr').filter({hasText: 'Plank'}).getByRole('button', {name: 'Edit workout'}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    await expect(dialog.getByText('Weight', {exact: true}).locator('..').locator('.field-record-context')).toHaveText('Heaviest load: 10 kg');
    await expect(dialog.getByText('Seconds').locator('..').locator('.field-record-context')).toHaveText('Longest duration: 01:30');
});

test('personal-record notifications dismiss on click and open the exact history event', async ({page}) => {
    const eventKey = 'exact-record-event';
    const record = {...personalRecord({metric: 'ROUTINE_BEST_STREAK_MAXIMUM', metricLabel: 'Highest routine best streak', domain: 'BEHAVIOR', value: 60, unit: 'DAYS', subject: {type: 'ROUTINE', id: 1, label: 'Morning walk'}}), eventKey, kind: 'IMPROVED', previousValue: 21, currentRecord: true};
    const notification = {id: 30, type: 'PERSONAL_RECORD', title: 'Routine streak milestone', message: 'Morning walk: 60 days', reminderDate: '2026-08-20', availableAt: '2026-08-20T08:00:00+02:00', actionUrl: `/records?tab=history&eventKey=${eventKey}`};
    await mockAuthenticatedWorkouts(page, [], [], {historyEvents: [record], initialNotifications: [notification]});

    await openSpaRoute(page, '/records');
    await page.getByRole('button', {name: '1 pending notification'}).click();
    const dismissRequest = page.waitForRequest(request => request.url().endsWith('/api/notifications/30/dismiss') && request.method() === 'POST');
    await page.locator('.notification-content').filter({hasText: 'Morning walk: 60 days'}).click();
    await dismissRequest;

    await expect(page).toHaveURL(`http://127.0.0.1:4173/records?tab=history&eventKey=${eventKey}`);
    await expect(page.getByText('Showing the record linked from your notification.')).toBeVisible();
    await expect(page.locator('.p-tabview-panel:visible').getByText('60 days', {exact: true})).toBeVisible();
    await expect(page.getByRole('button', {name: '0 pending notifications'})).toBeVisible();
});

test('Home shows compact all-time body records', async ({page}) => {
    const currentRecords = [
        personalRecord({metric: 'BODY_WEIGHT', metricLabel: 'Lowest weight', domain: 'BODY', value: 79, unit: 'KG', subject: {type: 'BODY', id: null, label: 'Body'}}),
        personalRecord({metric: 'BODY_MUSCLE_MASS', metricLabel: 'Highest muscle mass', domain: 'BODY', value: 65, unit: 'KG', subject: {type: 'BODY', id: null, label: 'Body'}})
    ];
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {currentRecords});
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Body'}).click();
    const panel = page.locator('.home-panels-tabs .p-tabview-panel:visible');
    await expect(panel.getByText('All-time Records')).toBeVisible();
    await expect(panel.getByText('Lowest weight', {exact: true})).toBeVisible();
    await expect(panel.getByText('79 kg', {exact: false})).toBeVisible();
});

test('Home shows sleep duration records in the sleep duration format', async ({page}) => {
    const currentRecords = [
        personalRecord({metric: 'SLEEP_TOTAL_DURATION_MAXIMUM', metricLabel: 'Longest total sleep', domain: 'RECOVERY', value: 23760, unit: 'SECONDS', subject: {type: 'SLEEP', id: null, label: 'Sleep'}}),
        personalRecord({metric: 'SLEEP_AWAKE_TIME_MINIMUM', metricLabel: 'Shortest awake time', domain: 'RECOVERY', value: 420, unit: 'SECONDS', subject: {type: 'SLEEP', id: null, label: 'Sleep'}})
    ];
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {currentRecords});
    await openSpaRoute(page, '/');
    await expect(page.getByText('Dashboard Date')).toBeVisible();
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Sleep'}).click();

    const panel = page.locator('.home-panels-tabs .p-tabview-panel:visible');
    await expect(panel.getByText('6.6 h', {exact: true})).toBeVisible();
    await expect(panel.getByText('7 min', {exact: true})).toBeVisible();
    await page.setViewportSize({width: 393, height: 851});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('Home Calories tab does not show optional nutrition personal records', async ({page}) => {
    const nutritionRecord = personalRecord({metric: 'DAILY_CALORIES_MAXIMUM', metricLabel: 'Highest daily calories', domain: 'NUTRITION', value: 6381, unit: 'KCAL', subject: {type: 'NUTRITION_DAY', id: null, label: 'Daily nutrition'}});
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {currentRecords: [nutritionRecord]});
    await openSpaRoute(page, '/');

    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Calories'}).click();

    const panel = page.locator('.home-panels-tabs .p-tabview-panel:visible');
    await expect(panel.getByText('All-time Records')).toHaveCount(0);
});

test('Home preloads week-summary data and loads remaining dashboard data when needed', async ({page}) => {
    const requestedPaths = [];
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {onApiRequest: path => requestedPaths.push(path)});

    await openSpaRoute(page, '/');
    await expect(page.getByText('Dashboard Date')).toBeVisible();
    expect(requestedPaths).not.toContain('/api/weights');
    expect(requestedPaths).not.toContain('/api/blood-pressures');
    await expect.poll(() => requestedPaths).toContain('/api/sleeps');
    await expect.poll(() => requestedPaths).toContain('/api/calories');
    await expect(page.getByRole('button', {name: 'Show charts'})).toHaveCount(0);

    await page.getByRole('tab', {name: 'Body'}).click();
    await expect(page.getByText('Last Weight')).toBeVisible();
    await expect.poll(() => requestedPaths).toContain('/api/weights');
    await expect.poll(() => requestedPaths).toContain('/api/blood-pressures');

    await page.locator('.dashboard-charts-trigger').scrollIntoViewIfNeeded();
    await expect.poll(() => requestedPaths).toContain('/api/moods');

    await page.setViewportSize({width: 393, height: 851});
    await expect(page.getByText('Monthly', {exact: true})).toBeVisible();
});

test('week summary shows recorded sleep and calories without opening their tabs', async ({page}) => {
    const selectedDate = '2026-08-12';
    const completedDates = ['2026-08-08', '2026-08-09', '2026-08-10', '2026-08-11'];
    const completedDays = completedDates.map(dashboardDailyStatus);
    const dashboardResponse = {
        ...dashboard,
        anchorDate: selectedDate,
        lastCompletedDashboardDate: completedDates.at(-1),
        dailyStatus: dashboardDailyStatus(selectedDate),
        weekStatus: {
            ...dashboardWeek(),
            saturday: completedDays[0],
            sunday: completedDays[1],
            monday: completedDays[2],
            tuesday: completedDays[3],
            wednesday: dashboardDailyStatus(selectedDate)
        }
    };
    const initialMeals = completedDates.map((date, index) => ({
        id: index + 1,
        date,
        dateFormat: date.split('-').reverse().join('/'),
        mealType: 'LUNCH',
        mealSequence: 1,
        calories: 1800,
        proteinGrams: null,
        carbohydrateGrams: null,
        fatGrams: null,
        source: 'MANUAL'
    }));
    await mockAuthenticatedDashboard(page, selectedDate, {
        dashboardResponse,
        initialMeals,
        initialSleeps: sleepHistory(selectedDate).filter(sleep => completedDates.includes(sleep.date))
    });

    await openSpaRoute(page, '/');

    const weekScore = page.locator('.week-status');
    await expect(weekScore.locator('span').filter({hasText: /^7\.0 h$/})).toHaveCount(5);
    await expect(weekScore.locator('span').filter({hasText: /^1800 kcal$/})).toHaveCount(5);
});

test('Home keeps lazy panels in a loading state until their data is ready', async ({page}) => {
    let finishSleepLoad;
    let finishWorkoutLoad;
    const requestedPaths = [];
    const sleepLoad = new Promise(resolve => finishSleepLoad = resolve);
    const workoutLoad = new Promise(resolve => finishWorkoutLoad = resolve);
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {
        initialSleeps: sleepHistory(dashboard.anchorDate),
        initialWorkouts: [dashboardWorkout(dashboard.anchorDate)],
        sleepLoad,
        workoutLoad,
        onApiRequest: path => requestedPaths.push(path)
    });
    await page.setViewportSize({width: 393, height: 851});

    await openSpaRoute(page, '/');
    await expect(page.getByRole('status', {name: 'Loading sleep data'})).toBeVisible();
    await expect(page.getByRole('status', {name: 'Loading workout data'})).toBeVisible();
    await expect.poll(() => requestedPaths).toContain('/api/workouts/dashboard');

    await page.getByRole('tab', {name: 'Sleep'}).click();
    await expect(page.getByText('Loading sleep data…')).toBeVisible();
    await expect(page.getByText('Not enough data (0/30)')).toHaveCount(0);
    finishSleepLoad();
    await expect(page.getByText('EXCELLENT (4/4)')).toBeVisible();
    const sleepTab = page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Sleep'});
    await expect(sleepTab.locator('[aria-label="Missing entry for selected date"]')).toHaveCount(0);

    finishWorkoutLoad();
    const workoutTab = page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Workout'});
    await expect(workoutTab.getByRole('status', {name: 'Loading workout data'})).toHaveCount(0);
    await workoutTab.click();
    await expect(page.getByText('Strength session')).toBeVisible();
    expect(requestedPaths).toContain('/api/workouts/dashboard');
    expect(requestedPaths).not.toContain('/api/workouts');
    await page.setViewportSize({width: 1440, height: 900});
    await expect(page.getByText('Strength session')).toBeVisible();
});

test('Home shows a missing metric badge after its lazy request completes', async ({page}) => {
    let finishSleepLoad;
    const sleepLoad = new Promise(resolve => finishSleepLoad = resolve);
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {sleepLoad});

    await openSpaRoute(page, '/');
    const sleepTab = page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Sleep'});
    await expect(sleepTab.getByRole('status', {name: 'Loading sleep data'})).toBeVisible();
    await sleepTab.click();
    await expect(page.getByText('Loading sleep data…')).toBeVisible();
    finishSleepLoad();
    await expect(sleepTab.getByRole('img', {name: 'Missing entry for selected date'})).toBeVisible();
});

function sleepHistory(endDate) {
    return Array.from({length: 30}, (_, index) => {
        const date = new Date(`${endDate}T12:00:00Z`);
        date.setUTCDate(date.getUTCDate() - index);
        const value = date.toISOString().slice(0, 10);
        return {
            id: index + 1,
            date: value,
            dateFormat: value.split('-').reverse().join('/'),
            bedtimeStart: `${value}T00:00:00Z`,
            bedtimeEnd: `${value}T08:00:00Z`,
            totalSleepDuration: 7 * 60 * 60,
            deepSleepDuration: 90 * 60,
            remSleepDuration: 90 * 60,
            lightSleepDuration: 4 * 60 * 60,
            awakeTime: 60 * 60,
            averageHeartRate: 60,
            averageHrv: 30
        };
    });
}

function dashboardWorkout(date) {
    return {
        id: 1,
        workoutDate: date,
        workoutDateFormat: date.split('-').reverse().join('/'),
        note: 'Strength session',
        lines: [{
            exerciseId: 1,
            exerciseName: 'Squat',
            exerciseDescription: null,
            trackingMode: 'REPS',
            position: 0,
            calories: null,
            averageHeartRate: null,
            sets: [],
            intervals: []
        }]
    };
}

function personalRecord(overrides) {
    return {
        metric: overrides.metric,
        metricLabel: overrides.metricLabel,
        domain: overrides.domain,
        direction: overrides.domain === 'BODY' && !overrides.metric.includes('MUSCLE') ? 'MINIMUM' : 'MAXIMUM',
        value: overrides.value,
        unit: overrides.unit,
        recordDate: Object.prototype.hasOwnProperty.call(overrides, 'recordDate') ? overrides.recordDate : '2026-08-10',
        subject: overrides.subject,
        qualifier: overrides.qualifier || null,
        source: overrides.source || {type: overrides.domain === 'BODY' ? 'WEIGHT' : 'WORKOUT', id: 1, linePosition: null, segmentPosition: null}
    };
}

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
        url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
        tag: 'routine-reminder-10',
        snoozeUrl: '/api/routines/1/reminders/10/snooze'
    };

    await dispatchWorkerEvent(worker.listeners.push, {data: {json: () => routinePayload}});
    await dispatchWorkerEvent(worker.listeners.push, {data: {json: () => ({...routinePayload, title: 'Notification test', snoozeUrl: null})}});

    expect(plain(worker.notifications[0])).toEqual({
        title: 'Routine reminder',
        options: {
            body: 'Morning weigh-in',
            icon: '/android-chrome-192x192.png',
            tag: 'routine-reminder-10',
            actions: [
                {action: 'snooze', title: 'Snooze 15 min'},
                {action: 'dismiss', title: 'Dismiss'}
            ],
            data: {
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
                snoozeUrl: '/api/routines/1/reminders/10/snooze'
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
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
                snoozeUrl: '/api/routines/1/reminders/10/snooze'
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
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
                snoozeUrl: '/api/routines/1/reminders/10/snooze'
            },
            close() {}
        }
    });

    expect(plain(requests)).toEqual([[
        '/api/routines/1/reminders/10/snooze',
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
                    url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
                    snoozeUrl: '/api/routines/1/reminders/10/snooze'
                },
                close() {}
            }
        });

        expect(worker.openedUrls).toEqual(['https://weightcontrol.test/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10']);
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
            data: {url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10', snoozeUrl: null},
            close() {}
        }
    });

    expect(navigatedUrls).toEqual(['https://weightcontrol.test/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10']);
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
        if (shortcut.outcome === 'MISS') {
            await expect(page.locator('.win-celebration--miss')).toBeVisible();
            await expect(page.locator('.win-celebration-title')).toHaveText('MISS');
        } else {
            await expect(page.locator('.win-celebration-title')).toHaveText('WIN');
        }
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
        await expect(page.getByText('Receive daily Mood and Back reminders, weekly Weight and Blood Pressure reminders, routine reminders, and notifications when a new app update is available.')).toBeVisible();
    });
});

test('daily reminder settings show and save the three default times', async ({page}) => {
    await mockRoutineReminderHome(page, []);

    await openSpaRoute(page, '/settings');

    await expect(page.locator('#morning-reminder-time')).toHaveValue('07:30');
    await expect(page.locator('#midday-reminder-time')).toHaveValue('13:30');
    await expect(page.locator('#evening-reminder-time')).toHaveValue('20:30');
    await expect(page.getByText('Weekly Weight and Blood Pressure reminders are sent on Saturday at 05:00 and 05:15.')).toBeVisible();
    await expect(page.getByText('Active coaching plan', {exact: true})).toHaveCount(0);
    await expect(page.getByText('Health constraints', {exact: true})).toHaveCount(0);
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/push/reminder-settings') && request.method() === 'PUT');
    await page.getByRole('button', {name: 'Save reminder times'}).click();
    expect((await saveRequest).postDataJSON()).toEqual({morningTime: '07:30', middayTime: '13:30', eveningTime: '20:30'});
    await expect(page.getByText('Reminder times saved')).toBeVisible();
});

test('goal and plan page explains concepts, preserves the contract, and adapts to the viewport', async ({page}) => {
    await page.setViewportSize({width: 1280, height: 900});
    await mockAuthenticatedSettings(page, {
        goal: 'Build strength safely',
        principles: ['Protect my lower back'],
        priorities: ['Training consistency', 'Recovery'],
        actions: ['Complete three strength sessions each week'],
        startDate: '2026-08-01',
        reviewDate: '2026-09-01',
        notes: 'Review progress monthly',
        updatedAt: '2026-08-01T12:00:00Z'
    });

    await openSpaRoute(page, '/plan');

    const panel = page.locator('.p-panel').filter({hasText: 'Active coaching plan'});
    await expect(panel.getByText('Define what you want to achieve and how the Coach should help you.')).toBeVisible();
    await expect(panel.getByText('The result you want to work toward.')).toBeVisible();
    await expect(panel.getByText('Rules the Coach should follow when helping you.')).toBeVisible();
    await expect(panel.getByText('What matters most, listed from highest to lowest priority.')).toBeVisible();
    await expect(panel.getByText('Specific steps you have agreed to take.')).toBeVisible();
    await expect(panel.getByLabel('Goal', {exact: true})).toHaveValue('Build strength safely');
    await expect(panel.getByLabel('Guidelines', {exact: true})).toHaveValue('Protect my lower back');
    await expect(panel.getByLabel('Focus areas', {exact: true})).toHaveValue('Training consistency\nRecovery');
    await expect(panel.getByLabel('Next actions', {exact: true})).toHaveValue('Complete three strength sessions each week');

    const guidelinesField = panel.getByLabel('Guidelines', {exact: true}).locator('..');
    const focusAreasField = panel.getByLabel('Focus areas', {exact: true}).locator('..');
    const guidelinesDesktopBox = await guidelinesField.boundingBox();
    const focusAreasDesktopBox = await focusAreasField.boundingBox();
    expect(Math.abs(guidelinesDesktopBox.y - focusAreasDesktopBox.y)).toBeLessThan(2);
    expect(focusAreasDesktopBox.x).toBeGreaterThan(guidelinesDesktopBox.x);

    await panel.getByLabel('Guidelines', {exact: true}).fill('Protect my lower back\nProgress gradually');
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/coaching-plan') && request.method() === 'PUT');
    await panel.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saveRequest).postDataJSON()).toEqual({
        goal: 'Build strength safely',
        principles: ['Protect my lower back', 'Progress gradually'],
        priorities: ['Training consistency', 'Recovery'],
        actions: ['Complete three strength sessions each week'],
        startDate: '2026-08-01',
        reviewDate: '2026-09-01',
        notes: 'Review progress monthly'
    });

    await page.setViewportSize({width: 390, height: 844});
    const guidelinesMobileBox = await guidelinesField.boundingBox();
    const focusAreasMobileBox = await focusAreasField.boundingBox();
    expect(Math.abs(guidelinesMobileBox.x - focusAreasMobileBox.x)).toBeLessThan(2);
    expect(focusAreasMobileBox.y).toBeGreaterThan(guidelinesMobileBox.y + guidelinesMobileBox.height);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('scheduled routines flatten several ordered times and can have their reminders cleared', async ({page}) => {
    await mockAuthenticatedRoutines(page, [
        routine(1, 'Evening walk', '18:00:00'),
        routine(2, 'No reminder', null),
        routine(3, 'Morning weigh-in', ['07:30:00', '12:30:00'])
    ]);

    await openSpaRoute(page, '/routines');
    await page.getByRole('tab', {name: 'Scheduled'}).click();

    const scheduledPanel = page.locator('.p-tabview-panel');
    const rows = scheduledPanel.locator('tbody tr');
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(0)).toContainText('07:30');
    await expect(rows.nth(0)).toContainText('Morning weigh-in');
    await expect(rows.nth(1)).toContainText('12:30');
    await expect(rows.nth(1)).toContainText('Morning weigh-in');
    await expect(rows.nth(2)).toContainText('18:00');
    await expect(rows.nth(2)).toContainText('Evening walk');
    await expect(scheduledPanel).not.toContainText('No reminder');

    await rows.nth(0).getByRole('button', {name: 'Edit routine'}).click();
    const dialog = page.getByRole('dialog', {name: 'Routine'});
    await expect(dialog.locator('#routine')).toHaveValue('Morning weigh-in');
    await dialog.getByRole('button', {name: 'Remove reminder 1'}).click();
    await dialog.getByRole('button', {name: 'Remove reminder 1'}).click();
    const updateRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/3') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await updateRequest).postDataJSON().reminderTimes).toEqual([]);
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

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
    let dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await expect(dialog).toContainText('Morning weigh-in');
    await expect(dialog).toContainText('07:30');
    await expect(dialog.locator('.p-dropdown-label')).toHaveText('15 minutes');
    let snoozeRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/1/reminders/10/snooze') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Snooze'}).click();

    expect((await snoozeRequest).postDataJSON()).toEqual({minutes: 15});
    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
    await expect(page.getByText('Routine reminder snoozed for 15 minutes')).toBeVisible();

    await page.goto(`/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
    dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await dialog.getByLabel('Snooze for').click();
    await page.getByRole('option', {name: '30 minutes'}).click();
    snoozeRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/1/reminders/10/snooze') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Snooze'}).click();

    expect((await snoozeRequest).postDataJSON()).toEqual({minutes: 30});
    await expect(page.getByText('Routine reminder snoozed for 30 minutes')).toBeVisible();
});

test('medication reminder records the exact dose as taken', async ({page}) => {
    await mockRoutineReminderHome(page, [], {medicationDose: medicationReminderDose()});
    await openSpaRoute(page, '/?medicationDoseId=50');

    const dialog = page.getByRole('dialog', {name: 'Medication reminder'});
    await expect(dialog).toContainText("It's time to take");
    await expect(dialog).toContainText('Vitamin D');
    await expect(dialog).toContainText('1 tablet');
    await expect(dialog).toContainText('Take with breakfast');

    const takeRequest = page.waitForRequest(request => request.url().endsWith('/api/medications/doses/50/take') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Mark as taken'}).click();

    expect((await takeRequest).postDataJSON().takenAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    await expect(page.getByText('Medication marked as taken')).toBeVisible();
    await expect(dialog).toHaveCount(0);
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('medication reminder can be snoozed for a selected delay', async ({page}) => {
    await mockRoutineReminderHome(page, [], {medicationDose: medicationReminderDose()});
    await openSpaRoute(page, '/?medicationDoseId=50');

    const dialog = page.getByRole('dialog', {name: 'Medication reminder'});
    await dialog.getByLabel('Snooze medication for').click();
    await page.getByRole('option', {name: '30 minutes'}).click();
    const snoozeRequest = page.waitForRequest(request => request.url().endsWith('/api/medications/doses/50/snooze') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Snooze', exact: true}).click();

    expect((await snoozeRequest).postDataJSON()).toEqual({minutes: 30});
    await expect(page.getByText('Medication reminder snoozed for 30 minutes')).toBeVisible();
    await expect(dialog).toHaveCount(0);
});

test('each routine reminder opens and snoozes its own scheduled time', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Medication', ['07:30:00', '18:00:00'])]);

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=11`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await expect(dialog).toContainText('Medication');
    await expect(dialog).toContainText('18:00');
    const snoozeRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/1/reminders/11/snooze') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Snooze'}).click();

    expect((await snoozeRequest).postDataJSON()).toEqual({minutes: 15});
});

test('routine reminder is actionable before dashboard data finishes loading', async ({page}) => {
    const date = madridDate();
    let finishDashboardLoad;
    const dashboardLoad = new Promise(resolve => finishDashboardLoad = resolve);
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {dashboardLoad});

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await expect(dialog).toContainText('Morning weigh-in');
    const snoozeRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/1/reminders/10/snooze') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Snooze'}).click();

    expect((await snoozeRequest).postDataJSON()).toEqual({minutes: 15});
    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
    finishDashboardLoad();
    await expect(page.getByText('Dashboard Date')).toBeVisible();
});

for (const reminder of [
    {type: 'mood', period: 'MIDDAY', title: 'Midday mood reminder', form: 'Mood'},
    {type: 'back', period: 'EVENING', title: 'Evening back reminder', form: 'Back Pain Episode'}
]) {
    test(`${reminder.type} reminder is actionable before dashboard data finishes loading`, async ({page}) => {
        const date = madridDate();
        let finishDashboardLoad;
        const dashboardLoad = new Promise(resolve => finishDashboardLoad = resolve);
        await mockRoutineReminderHome(page, [], {dashboardLoad});

        await openSpaRoute(page, `/?checkInReminder=${reminder.type}&checkInPeriod=${reminder.period}&checkInReminderDate=${date}`);
        const dialog = page.getByRole('dialog', {name: reminder.title});
        await expect(dialog).toBeVisible();
        await dialog.getByRole('button', {name: 'Record'}).click();
        await expect(page.getByRole('dialog', {name: reminder.form, exact: true})).toBeVisible();

        finishDashboardLoad();
        await expect(page.getByText('Dashboard Date')).toBeVisible();
    });
}

test('routine reminder content and actions remain visible at mobile and desktop sizes', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')]);

    await page.setViewportSize({width: 1280, height: 800});
    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});

    for (const viewport of [{width: 1280, height: 800}, {width: 655, height: 500}, {width: 393, height: 851}]) {
        await page.setViewportSize(viewport);
        await expect(dialog.getByText("It's time for")).toBeVisible();
        await expect(dialog.getByText('Morning weigh-in')).toBeVisible();
        await expect(dialog.getByText('Scheduled time')).toBeVisible();
        await expect(dialog.getByText('07:30')).toBeVisible();
        await expect(dialog.getByText('Europe/Madrid')).toBeVisible();
        await expect(dialog.getByLabel('Snooze for')).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Snooze'})).toBeVisible();
        const completeButton = dialog.getByRole('button', {name: 'Mark as done'});
        await expect(completeButton).toBeVisible();
        expect(await completeButton.evaluate(button => {
            const label = button.querySelector('.p-button-label');
            return label.scrollWidth <= label.clientWidth;
        })).toBe(true);
    }
});

test('routine reminder expires when its snooze crosses midnight', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {snoozeExpires: true});

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await dialog.getByRole('button', {name: 'Snooze'}).click();

    await expect(dialog).not.toBeVisible();
    await expect(page.getByText('This reminder will not fire again today')).toBeVisible();
});

test('routine reminder can mark the routine as done', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')]);
    let dashboardRefreshRequests = 0;
    page.on('request', request => {
        if (new URL(request.url()).pathname === '/api/dashboard/refresh') {
            dashboardRefreshRequests++;
        }
    });

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    const checkinRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/1/checkins') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Mark as done'}).click();

    expect(new Date((await checkinRequest).postDataJSON().date).toString()).not.toBe('Invalid Date');
    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
    await expect(page.getByText('Routine marked as done')).toBeVisible();
    expect(dashboardRefreshRequests).toBe(0);
});

test('different routines can be completed rapidly with compact streak context on mobile', async ({page}) => {
    await page.setViewportSize({width: 390, height: 844});
    await mockRoutineReminderHome(page, [routine(1, 'Morning walk', null), routine(2, 'Brush teeth', null)], {checkinDelay: 150});
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Routines'}).click();
    const panel = page.locator('.home-panels-tabs .p-tabview-panel:visible');
    const firstRow = panel.locator('tbody tr').filter({hasText: 'Morning walk'});
    const secondRow = panel.locator('tbody tr').filter({hasText: 'Brush teeth'});

    const checkins = Promise.all([
        page.waitForResponse(response => response.url().endsWith('/api/routines/1/checkins') && response.request().method() === 'POST'),
        page.waitForResponse(response => response.url().endsWith('/api/routines/2/checkins') && response.request().method() === 'POST')
    ]);
    await firstRow.locator('.p-button-success').click();
    await secondRow.locator('.p-button-success').click();
    await checkins;

    await expect(firstRow.getByText('Best: 1 days', {exact: true})).toBeVisible();
    await expect(secondRow.getByText('Best: 1 days', {exact: true})).toBeVisible();
    await expect(panel.getByText('Streak', {exact: true})).toBeVisible();
    await expect(page.getByRole('dialog', {name: 'Personal records'})).not.toBeVisible();
    const nameCell = await firstRow.locator('.routine-name-cell').boundingBox();
    expect(nameCell.x).toBeGreaterThanOrEqual(0);
    expect(nameCell.x + nameCell.width).toBeLessThanOrEqual(390);
});

test('grouped navigation keeps destinations and utilities accessible on desktop and mobile', async ({page}) => {
    const desktopViewport = {width: 1440, height: 900};
    await page.setViewportSize(desktopViewport);
    await mockRoutineReminderHome(page, []);
    await openSpaRoute(page, '/');

    const menubar = page.locator('.app-menubar');
    const home = menubar.getByText('Home', {exact: true});
    const track = menubar.getByText('Track', {exact: true});
    const plan = menubar.getByText('Plan', {exact: true});
    const review = menubar.getByText('Review', {exact: true});
    const topLevelBoxes = await Promise.all([home, track, plan, review].map(item => item.boundingBox()));
    expect(Math.max(...topLevelBoxes.map(box => box.y)) - Math.min(...topLevelBoxes.map(box => box.y))).toBeLessThanOrEqual(1);

    const menubarBox = await menubar.boundingBox();
    const bellBox = await page.getByRole('button', {name: '0 pending notifications'}).boundingBox();
    const coachBox = await page.getByRole('button', {name: 'Open Coach'}).boundingBox();
    const accountBox = await page.getByRole('button', {name: 'Account'}).boundingBox();
    expect(bellBox.x).toBeGreaterThanOrEqual(menubarBox.x);
    expect(accountBox.x + accountBox.width).toBeLessThanOrEqual(menubarBox.x + menubarBox.width);
    expect(coachBox.x + coachBox.width).toBeLessThanOrEqual(bellBox.x);
    expect(bellBox.x + bellBox.width).toBeLessThanOrEqual(accountBox.x);

    await track.click();
    const trackMenu = menubar.locator('.p-submenu-list').filter({hasText: 'Progress Photos'});
    await expect(trackMenu).toBeVisible();
    await expect(trackMenu).toContainText('Weight');
    await expect(trackMenu).toContainText('Blood Pressure');
    await expect(trackMenu).toContainText('Nutrition');
    await trackMenu.getByText('Nutrition', {exact: true}).click();
    await expect(page).toHaveURL('http://127.0.0.1:4173/calories');

    await plan.click();
    const planMenu = menubar.locator('.p-submenu-list').filter({hasText: 'Goal and plan'});
    await expect(planMenu).toBeVisible();
    await planMenu.getByText('Goal and plan', {exact: true}).click();
    await expect(page).toHaveURL('http://127.0.0.1:4173/plan');

    await review.click();
    const reviewMenu = menubar.locator('.p-submenu-list').filter({hasText: 'Personal Records'});
    await expect(reviewMenu).toBeVisible();
    await expect(reviewMenu).toContainText('Reflections');
    await expect(reviewMenu.getByText('Personal Records', {exact: true}).locator('..').locator('.pi-star')).toBeVisible();

    await page.getByRole('button', {name: 'Account'}).click();
    await expect(page.getByText('Backup', {exact: true})).toHaveCount(0);
    await page.getByText('Settings', {exact: true}).click();
    await expect(page).toHaveURL('http://127.0.0.1:4173/settings');

    const mobileViewport = {width: 393, height: 851};
    await page.setViewportSize(mobileViewport);
    await page.goto('/');
    await expect(page.getByRole('button', {name: '0 pending notifications'})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Account'})).toBeVisible();
    await menubar.locator('.p-menubar-button').click();
    await expect(track).toBeVisible();
    await track.click();
    await expect(trackMenu.getByText('Progress Photos', {exact: true})).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(mobileViewport.width);

    await page.getByRole('button', {name: 'Account'}).click();
    const logoutRequest = page.waitForRequest(request => request.url().endsWith('/api/auth/logout') && request.method() === 'POST');
    await page.getByText('Log out', {exact: true}).click();
    await logoutRequest;
    await expect(page).toHaveURL('http://127.0.0.1:4173/login');
});

test('notification bell opens pending actions and dismisses them individually', async ({page}) => {
    const date = madridDate();
    const initialNotifications = [
        {
            id: 10,
            type: 'ROUTINE',
            title: 'Routine reminder',
            message: 'Morning weigh-in',
            reminderDate: date,
            availableAt: `${date}T07:30:00+02:00`,
            actionUrl: `/?routineReminderId=1&routineReminderScheduleId=10&routineReminderDate=${date}&notificationId=10`
        },
        {
            id: 11,
            type: 'MOOD',
            title: 'Midday mood reminder',
            message: 'Record your midday mood.',
            reminderDate: date,
            availableAt: `${date}T13:30:00+02:00`,
            actionUrl: `/?checkInReminder=mood&checkInPeriod=MIDDAY&checkInReminderDate=${date}&notificationId=11`
        }
    ];
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {initialNotifications});

    await openSpaRoute(page, '/');
    let bell = page.getByRole('button', {name: '2 pending notifications'});
    await expect(bell).toBeVisible();
    const bellBox = await bell.boundingBox();
    const coachBox = await page.getByRole('button', {name: 'Open Coach'}).boundingBox();
    const accountBox = await page.getByRole('button', {name: 'Account'}).boundingBox();
    expect(coachBox.x + coachBox.width).toBeLessThanOrEqual(bellBox.x);
    expect(bellBox.x + bellBox.width).toBeLessThanOrEqual(accountBox.x);
    expect(coachBox.x).toBeGreaterThanOrEqual(0);
    await bell.click();

    const items = page.locator('.notification-item');
    await expect(items).toHaveCount(2);
    await expect(items.nth(0)).toContainText('Morning weigh-in');
    await expect(items.nth(1)).toContainText('Record your midday mood.');
    const dismissRequest = page.waitForRequest(request => request.url().endsWith('/api/notifications/11/dismiss') && request.method() === 'POST');
    await page.getByRole('button', {name: 'Dismiss Midday mood reminder'}).click();
    await dismissRequest;

    bell = page.getByRole('button', {name: '1 pending notification'});
    await expect(bell).toBeVisible();
    await page.getByRole('button', {name: 'Morning weigh-in'}).click();
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', {name: 'Mark as done'}).click();

    bell = page.getByRole('button', {name: '0 pending notifications'});
    await expect(bell).toBeVisible();
    await bell.click();
    await expect(page.getByText('No pending notifications.')).toBeVisible();
});

test('notification panel dismisses all pending notifications', async ({page}) => {
    const date = madridDate();
    const initialNotifications = [
        {
            id: 40,
            type: 'MOOD',
            title: 'Morning mood reminder',
            message: 'Record your morning mood.',
            reminderDate: date,
            availableAt: `${date}T07:30:00+02:00`,
            actionUrl: '/'
        },
        {
            id: 41,
            type: 'APP_UPDATE',
            title: 'Weight Control update available',
            message: 'New feature',
            reminderDate: date,
            availableAt: `${date}T08:00:00+02:00`,
            actionUrl: '/'
        }
    ];
    await mockRoutineReminderHome(page, [], {initialNotifications});

    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '2 pending notifications'}).click();
    const dismissAllRequest = page.waitForRequest(request => request.url().endsWith('/api/notifications/dismiss-all') && request.method() === 'POST');
    await page.getByRole('button', {name: 'Dismiss all'}).click();
    await dismissAllRequest;

    await expect(page.getByRole('button', {name: '0 pending notifications'})).toBeVisible();
    await expect(page.getByText('Notifications dismissed')).toBeVisible();
    await expect(page.getByText('No pending notifications.')).toBeVisible();
});

test('notification panel fits a mobile viewport without horizontal scrolling', async ({page}) => {
    const date = madridDate();
    const viewport = {width: 401, height: 896};
    const initialNotifications = Array.from({length: 6}, (_, index) => ({
        id: 30 + index,
        type: 'ROUTINE',
        title: 'Routine reminder',
        message: 'RELAXATION ROUTINE: BREATHING AND FLEXIBILITY',
        reminderDate: date,
        availableAt: `${date}T07:30:00+02:00`,
        actionUrl: '/'
    }));
    await page.setViewportSize(viewport);
    await mockRoutineReminderHome(page, [], {initialNotifications});

    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '6 pending notifications'}).click();
    const panel = page.locator('.notification-panel');
    await expect(panel).toBeVisible();
    const panelBox = await panel.boundingBox();
    expect(panelBox.x).toBeGreaterThanOrEqual(0);
    expect(panelBox.x + panelBox.width).toBeLessThanOrEqual(viewport.width);
    const overflow = await page.evaluate(() => {
        const list = document.querySelector('.notification-list');
        return {
            documentWidth: document.documentElement.scrollWidth,
            listWidth: list.clientWidth,
            listScrollWidth: list.scrollWidth
        };
    });
    expect(overflow.documentWidth).toBeLessThanOrEqual(viewport.width);
    expect(overflow.listScrollWidth).toBeLessThanOrEqual(overflow.listWidth);
});

test('notification bell shows the deployed feature name until dismissed', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [], {
        initialNotifications: [{
            id: 12,
            type: 'APP_UPDATE',
            title: 'Weight Control update available',
            message: 'Allow workout exercise reordering',
            reminderDate: '2026-08-18',
            availableAt: '2026-08-18T21:45:00+02:00',
            actionUrl: '/'
        }],
        today: date
    });

    await openSpaRoute(page, '/');
    let bell = page.getByRole('button', {name: '1 pending notification'});
    await bell.click();
    const notification = page.locator('.notification-item');
    await expect(notification).toContainText('Weight Control update available');
    await expect(notification).toContainText('Allow workout exercise reordering');

    const dismissRequest = page.waitForRequest(request => request.url().endsWith('/api/notifications/12/dismiss') && request.method() === 'POST');
    await page.getByRole('button', {name: 'Dismiss Weight Control update available'}).click();
    await dismissRequest;
    bell = page.getByRole('button', {name: '0 pending notifications'});
    await expect(bell).toBeVisible();
});

test('weight notification opens an actual-date form and clears after saving', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {
        today: date,
        initialWeights: [reminderWeight('2026-08-15')],
        initialNotifications: [{
            id: 20,
            type: 'WEIGHT',
            title: 'Weight reminder',
            message: 'Record your weight.',
            reminderDate: date,
            availableAt: `${date}T05:00:00+02:00`,
            actionUrl: `/?measurementReminder=weight&measurementReminderDate=${date}&notificationId=20`
        }]
    });

    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '1 pending notification'}).click();
    await page.locator('.notification-content').filter({hasText: 'Weight reminder'}).click();

    const dialog = page.getByRole('dialog', {name: 'Weight'});
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('Date')).toBeVisible();
    await dialog.locator('#weight input').fill('79.5');
    await dialog.locator('#fat-percentage input').fill('20');
    const muscleInput = dialog.locator('#muscle input');
    await muscleInput.pressSequentially('63');
    await expect(dialog.locator('#muscle')).toHaveClass(/p-inputwrapper-filled/);
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/weights') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).dispatchEvent('click');

    const payload = (await saveRequest).postDataJSON();
    expect(payload).toMatchObject({weight: 79.5, fatPercentage: 20, muscle: 63});
    expect(payload.date.startsWith('2026-08-22T')).toBe(true);
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', {name: '0 pending notifications'})).toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('blood pressure notification opens the fixed Saturday form and clears after saving', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {
        today: date,
        initialNotifications: [{
            id: 21,
            type: 'BLOOD_PRESSURE',
            title: 'Blood pressure reminder',
            message: 'Record your blood pressure.',
            reminderDate: date,
            availableAt: `${date}T05:15:00+02:00`,
            actionUrl: `/?measurementReminder=blood-pressure&measurementReminderDate=${date}&notificationId=21`
        }]
    });

    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '1 pending notification'}).click();
    await page.locator('.notification-content').filter({hasText: 'Blood pressure reminder'}).click();

    const dialog = page.getByRole('dialog', {name: 'Blood Pressure'});
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('Date')).toHaveCount(0);
    await dialog.locator('#upper input').fill('120');
    await dialog.locator('#lower input').fill('80');
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/blood-pressures') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();

    const payload = (await saveRequest).postDataJSON();
    expect(payload).toMatchObject({upper: 120, lower: 80});
    expect(payload.date.startsWith('2026-08-22T')).toBe(true);
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', {name: '0 pending notifications'})).toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('cancelling a weight reminder form keeps the notification pending', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {
        today: date,
        initialWeights: [reminderWeight('2026-08-15')],
        initialNotifications: [{
            id: 20,
            type: 'WEIGHT',
            title: 'Weight reminder',
            message: 'Record your weight.',
            reminderDate: date,
            availableAt: `${date}T05:00:00+02:00`,
            actionUrl: `/?measurementReminder=weight&measurementReminderDate=${date}&notificationId=20`
        }]
    });

    await openSpaRoute(page, `/?measurementReminder=weight&measurementReminderDate=${date}&notificationId=20`);
    const dialog = page.getByRole('dialog', {name: 'Weight'});
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', {name: 'Cancel'}).click();

    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', {name: '1 pending notification'})).toBeVisible();
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('completed measurement reminder opens Home without a form', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {today: date});

    await openSpaRoute(page, `/?measurementReminder=weight&measurementReminderDate=${date}`);

    await expect(page.getByRole('dialog', {name: 'Weight'})).toHaveCount(0);
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('login preserves a pending measurement reminder', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {requiresLogin: true, today: date, initialWeights: [reminderWeight('2026-08-15')]});

    await openSpaRoute(page, `/?measurementReminder=weight&measurementReminderDate=${date}`);
    await expect(page).toHaveURL(`http://127.0.0.1:4173/login?measurementReminder=weight&measurementReminderDate=${date}`);
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page.getByRole('dialog', {name: 'Weight'})).toBeVisible();
});

for (const reminder of [
    {name: 'stale', id: 1, date: '2026-01-01', routines: [routine(1, 'Morning weigh-in', '07:30:00')]},
    {name: 'missing', id: 99, date: madridDate(), routines: [routine(1, 'Morning weigh-in', '07:30:00')]},
    {name: 'completed', id: 1, date: madridDate(), routines: [{...routine(1, 'Morning weigh-in', '07:30:00'), times: [`${madridDate()}T08:00:00+02:00`]}]}
]) {
    test(`${reminder.name} routine reminder opens Home without a modal`, async ({page}) => {
        await mockRoutineReminderHome(page, reminder.routines);

        await openSpaRoute(page, `/?routineReminderId=${reminder.id}&routineReminderDate=${reminder.date}&routineReminderScheduleId=10`);

        await expect(page.getByRole('dialog', {name: 'Routine reminder'})).toHaveCount(0);
        await expect(page).toHaveURL('http://127.0.0.1:4173/');
    });
}

test('login preserves a pending routine reminder', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {requiresLogin: true});

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
    await expect(page).toHaveURL(`http://127.0.0.1:4173/login?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
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
    await expect(dialog.locator('#period')).toContainText('Evening');
    await expect(dialog.locator('#period')).toHaveClass(/p-disabled/);
    await expect(dialog.locator('label').filter({hasText: /^Time$/})).toHaveCount(0);
    await dialog.getByRole('button', {name: 'Lower Right'}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'Moderate', exact: true}).click();
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();

    expect((await saveRequest).postDataJSON()).toMatchObject({date, period: 'EVENING', region: 'LOWER', side: 'RIGHT', severity: 'MODERATE'});
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

test('completed back reminder opens Home without a modal', async ({page}) => {
    const date = madridDate();
    const episode = {id: 1, date, period: 'MORNING', time: '08:12:00', timeFormat: '08:12', region: 'LOWER', side: 'LEFT', severity: 'MILD', note: null};
    await mockRoutineReminderHome(page, [], {initialBackPainEpisodes: [episode]});

    await openSpaRoute(page, `/?checkInReminder=back&checkInPeriod=MORNING&checkInReminderDate=${date}`);

    await expect(page.getByRole('dialog', {name: 'Morning back reminder'})).toHaveCount(0);
    await expect(page).toHaveURL('http://127.0.0.1:4173/');
});

test('login preserves a pending mood reminder', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [], {requiresLogin: true});

    await openSpaRoute(page, `/?checkInReminder=mood&checkInPeriod=EVENING&checkInReminderDate=${date}`);
    await expect(page).toHaveURL(`http://127.0.0.1:4173/login?checkInReminder=mood&checkInPeriod=EVENING&checkInReminderDate=${date}`);
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page.getByRole('dialog', {name: 'Evening mood reminder'})).toBeVisible();
});

test('back pain history saves an episode without a save-and-add action', async ({page}) => {
    await mockAuthenticatedBackPainEpisodes(page);
    await openSpaRoute(page, '/back');

    await page.getByRole('button', {name: 'Add Episode'}).click();
    const dialog = page.getByRole('dialog', {name: 'Back Pain Episode'});
    const actionFooter = dialog.locator('.back-pain-actions');
    const saveButton = actionFooter.getByRole('button', {name: 'Save', exact: true});
    await expect(actionFooter.getByRole('button', {name: 'Save & add', exact: true})).toHaveCount(0);
    await dialog.locator('#period').click();
    await page.getByRole('option', {name: 'Morning', exact: true}).click();
    await dialog.getByRole('button', {name: 'Upper Left'}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'Moderate', exact: true}).click();
    await dialog.locator('#note').fill('After lifting');
    const request = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
    await saveButton.click();
    expect((await request).postDataJSON()).toMatchObject({period: 'MORNING', region: 'UPPER', side: 'LEFT', severity: 'MODERATE', note: 'After lifting'});

    const rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(1);
    await expect(rows.nth(0)).toContainText('12:34');
    await expect(rows.nth(0)).toContainText('Morning');
    await expect(rows.nth(0)).toContainText('Upper Left');
    await expect(rows.nth(0)).toContainText('Moderate');
});

test('week totals use status thresholds instead of previous-week comparisons', async ({page}) => {
    const currentDate = '2026-08-08';
    const previousDate = '2026-08-01';
    const currentDay = {...dashboardDailyStatus(currentDate), flexibilityPercentage: 80, mindPercentage: 60};
    const previousDay = {...dashboardDailyStatus(previousDate), flexibilityPercentage: 100, mindPercentage: 50};
    const dashboardResponse = {
        ...dashboard,
        anchorDate: currentDate,
        lastCompletedDashboardDate: currentDate,
        dailyStatus: currentDay,
        lastWeekDailyStatus: previousDay,
        weekStatus: {...dashboardWeek(), saturday: currentDay},
        weekAgoStatus: {...dashboardWeek(), saturday: previousDay}
    };
    await mockAuthenticatedDashboard(page, currentDate, {dashboardResponse});

    await openSpaRoute(page, '/');

    const weekScore = page.locator('.week-status');
    await expect(weekScore.locator('.week-status-cell span.perfect', {hasText: /^80$/})).toHaveCount(2);
    await expect(weekScore.locator('.week-status-cell span.bad', {hasText: /^80$/})).toHaveCount(0);
    await expect(weekScore.locator('.week-status-cell span.good', {hasText: /^60$/})).toHaveCount(2);
    await expect(weekScore.locator('.week-status-cell span.perfect', {hasText: /^60$/})).toHaveCount(0);
});

test('dashboard entry modals hide the selected dashboard date', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-11');
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    const scenarios = [
        {tab: 'Body', button: 'New', buttonIndex: 0, dialog: 'Weight'},
        {tab: 'Body', button: 'New', buttonIndex: 1, dialog: 'Blood Pressure'},
        {tab: 'Back', button: 'Add Episode', buttonIndex: 0, dialog: 'Back Pain Episode'},
        {tab: 'Sleep', button: 'New', buttonIndex: 0, dialog: 'Sleep'},
        {tab: 'Mood', button: 'New', buttonIndex: 0, dialog: 'Mood'},
        {tab: 'Calories', button: 'New', buttonIndex: 0, dialog: 'Meal'},
        {tab: 'Workout', button: 'New', buttonIndex: 0, dialog: 'Workout'}
    ];

    for (const scenario of scenarios) {
        await tabs.getByRole('tab', {name: scenario.tab}).click();
        const activePanel = tabs.locator('.p-tabview-panel:visible');
        if (scenario.tab === 'Back') {
            await expect(activePanel.locator('.back-pain-summary-value').first()).toContainText('None');
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

test('dashboard records meal calories and optional macronutrients', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Calories'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel.locator('.meal-total')).toContainText('Total:');
    await expect(panel.locator('.meal-total')).toContainText('0 kcal');
    await panel.getByRole('button', {name: 'New', exact: true}).click();
    let dialog = page.getByRole('dialog', {name: 'Meal'});
    await dialog.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Lunch', exact: true}).click();
    await dialog.getByRole('button', {name: 'On plan · 925 kcal'}).click();
    await dialog.getByLabel('Protein (g)').fill('42.5');
    await dialog.getByLabel('Carbohydrates (g)').fill('80.25');
    await dialog.getByLabel('Fat (g)').fill('20');
    const lunchRequest = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();

    expect((await lunchRequest).postDataJSON()).toEqual({
        date: '2026-08-12',
        mealType: 'LUNCH',
        calories: 925,
        proteinGrams: 42.5,
        carbohydrateGrams: 80.25,
        fatGrams: 20,
        mealTime: null,
        notes: null
    });
    const lunch = panel.locator('.meal-entry').filter({hasText: 'Lunch'});
    await expect(lunch).toContainText('925 kcal');
    await expect(lunch.locator('.meal-entry-main')).not.toContainText('P 42.5 g');
    await expect(lunch.locator('.meal-entry-macros')).toHaveText('P 42.5 g · C 80.25 g · F 20 g');
    await expect(panel.locator('.meal-total')).toContainText('925 kcal');

    for (const calories of [150, 250]) {
        await panel.getByRole('button', {name: 'New', exact: true}).click();
        dialog = page.getByRole('dialog', {name: 'Meal'});
        await dialog.locator('#meal-type').click();
        await page.getByRole('option', {name: 'Snack', exact: true}).click();
        await dialog.getByLabel('Calories').fill(String(calories));
        await dialog.getByRole('button', {name: 'Save'}).click();
    }

    await expect(panel.locator('.meal-entry').filter({hasText: 'Snack 1'})).toContainText('150 kcal');
    const snack2 = panel.locator('.meal-entry').filter({hasText: 'Snack 2'});
    await expect(snack2).toContainText('250 kcal');
    await expect(panel.locator('.meal-total')).toContainText('1325 kcal');

    await lunch.getByRole('button', {name: 'Edit'}).click();
    dialog = page.getByRole('dialog', {name: 'Meal'});
    await dialog.getByLabel('Protein (g)').fill('45');
    const updateRequest = page.waitForRequest(request => /\/api\/meals\/\d+$/.test(request.url()) && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await updateRequest).postDataJSON().proteinGrams).toBe(45);
    await expect(lunch).toContainText('P 45 g');

    page.once('dialog', confirmation => confirmation.accept());
    const deleteRequest = page.waitForRequest(request => /\/api\/meals\/\d+$/.test(request.url()) && request.method() === 'DELETE');
    await snack2.getByRole('button', {name: 'Delete'}).click();
    await deleteRequest;
    await expect(panel.locator('.meal-entry').filter({hasText: 'Snack 2'})).toHaveCount(0);
    await expect(panel.locator('.meal-total')).toContainText('1075 kcal');
});

test('meal form and growl fit a mobile viewport', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await page.setViewportSize({width: 393, height: 851});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Calories'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await panel.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Meal'});
    const fieldWidths = await dialog.evaluate(element => ({
        mealType: element.querySelector('.entry-dropdown').getBoundingClientRect().width,
        calories: element.querySelector('#calories').getBoundingClientRect().width
    }));
    expect(Math.abs(fieldWidths.mealType - fieldWidths.calories)).toBeLessThanOrEqual(1);
    await dialog.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Lunch', exact: true}).click();
    await expect(dialog.locator('#meal-type')).toHaveText('Lunch');
    await dialog.getByLabel('Calories').fill('500');
    await dialog.getByRole('button', {name: 'Save'}).click();

    const growl = page.locator('.p-toast-message').filter({hasText: 'Meal saved'});
    await expect(growl).toBeVisible();
    const growlBounds = await growl.boundingBox();
    expect(growlBounds.x).toBeGreaterThanOrEqual(0);
    expect(growlBounds.x + growlBounds.width).toBeLessThanOrEqual(393);
    const mealRowLayout = await panel.locator('.meal-entry-main').evaluate(element => {
        const actions = element.querySelector('.meal-entry-actions').getBoundingClientRect();
        const row = element.getBoundingClientRect();
        return {flexDirection: getComputedStyle(element).flexDirection, actionsRight: actions.right, rowRight: row.right};
    });
    expect(mealRowLayout.flexDirection).toBe('row');
    expect(mealRowLayout.actionsRight).toBeLessThanOrEqual(mealRowLayout.rowRight);
});

test('reflection mobile panel and date navigation match the dashboard dimensions', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-13');
    await page.setViewportSize({width: 393, height: 851});
    await openSpaRoute(page, '/');

    const dashboardBounds = await page.locator('.dashboard-date-header').boundingBox();
    const dashboardPreviousBounds = await page.getByRole('button', {name: 'Previous Day'}).boundingBox();

    const reflectionPage = await page.context().newPage();
    await mockAuthenticatedReflections(reflectionPage, {
        reflectionDate: '2026-08-13',
        generatedAt: '2026-08-13T20:00:00Z',
        title: 'Plan progress reflection',
        summary: 'Private reflection summary.',
        planProgressScore: 7,
        planProgressRationale: 'Completed the agreed strength sessions consistently.',
        positiveSignals: ['Private positive signal.'],
        watchouts: ['Private watchout.'],
        nextActions: ['Private next action.']
    });
    await reflectionPage.setViewportSize({width: 393, height: 851});
    await openSpaRoute(reflectionPage, '/reflections');

    const reflectionBounds = await reflectionPage.locator('.date-console').boundingBox();
    const previousButton = reflectionPage.getByRole('button', {name: 'Previous Day'});
    const nextButton = reflectionPage.getByRole('button', {name: 'Next Day'});
    const previousBounds = await previousButton.boundingBox();
    const nextBounds = await nextButton.boundingBox();
    expect(Math.abs(dashboardBounds.width - reflectionBounds.width)).toBeLessThanOrEqual(1);
    expect(Math.abs(dashboardPreviousBounds.width - previousBounds.width)).toBeLessThanOrEqual(1);
    expect(Math.abs(dashboardPreviousBounds.height - previousBounds.height)).toBeLessThanOrEqual(1);
    expect(Math.abs(previousBounds.width - nextBounds.width)).toBeLessThanOrEqual(1);
    expect(Math.abs(previousBounds.height - nextBounds.height)).toBeLessThanOrEqual(1);
    await expect(previousButton.locator('.p-button-label')).toHaveCSS('white-space', 'nowrap');
    await expect(nextButton.locator('.p-button-label')).toHaveCSS('white-space', 'nowrap');
    expect(await previousButton.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    expect(await nextButton.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    const historyScore = reflectionPage.locator('.history-score');
    await expect(historyScore).toHaveText('7/10');
    expect(await historyScore.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    await reflectionPage.close();
});

test('reflection advice copies only a short natural Coach request', async ({page, context}) => {
    const reflection = {
        reflectionDate: '2026-08-13',
        windowStart: '2026-05-16',
        detailedWindowStart: '2026-07-15',
        windowEnd: '2026-08-13',
        generatedAt: '2026-08-13T20:00:00Z',
        model: 'ChatGPT',
        title: 'Private reflection title',
        summary: 'Private reflection summary.',
        planProgressScore: 7,
        planProgressRationale: 'Completed the agreed strength sessions consistently.',
        positiveSignals: ['Private positive signal.'],
        watchouts: ['Private watchout.'],
        nextActions: ['Private next action.']
    };
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await mockAuthenticatedReflections(page, reflection);
    await context.route('https://chatgpt.test/**', route => route.fulfill({
        contentType: 'text/html',
        body: '<title>Weight Control Coach</title>'
    }));
    await openSpaRoute(page, '/reflections');

    await expect(page.getByLabel('Plan progress rating')).toContainText('Plan progress: 7/10');
    await expect(page.locator('.history-score')).toHaveText('7/10');

    const coachPagePromise = context.waitForEvent('page');
    await page.getByRole('button', {name: 'Ask the Coach for current advice'}).click();
    const coachPage = await coachPagePromise;
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('What should I do now and for the rest of today?');
    await coachPage.close();
});

test('nutrition history summarizes macros and manages meals and fasting periods', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [
        {id: 1, date: '2026-08-12', dateFormat: '12/08/2026', mealType: 'LUNCH', mealSequence: 1, mealTime: '13:15:00', calories: 925, proteinGrams: 42.5, carbohydrateGrams: 80.25, fatGrams: 20, notes: 'Chicken and rice', source: 'MANUAL'},
        {id: 2, date: '2026-08-12', dateFormat: '12/08/2026', mealType: 'SNACK', mealSequence: 1, mealTime: null, calories: 150, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, notes: null, source: 'MANUAL'},
        {id: 3, date: '2026-08-11', dateFormat: '11/08/2026', mealType: 'DINNER', mealSequence: 1, mealTime: null, calories: 780, proteinGrams: 50, carbohydrateGrams: 100, fatGrams: 20, notes: null, source: 'MANUAL'}
    ], initialFastingPeriods: [
        {id: 1, startTime: '2026-08-11T20:00:00+02:00', endTime: '2026-08-12T12:00:00+02:00', startTimeFormat: '11/08/2026 20:00', endTimeFormat: '12/08/2026 12:00', notes: 'Overnight fast'}
    ]});
    await openSpaRoute(page, '/calories');

    await expect(page.getByRole('tab', {name: 'Daily summaries'})).toHaveAttribute('aria-selected', 'true');
    let rows = page.locator('.p-tabview-panel:visible tbody tr');
    await expect(rows).toContainText(['12/08/2026']);
    await expect(rows).toContainText(['1075 kcal']);
    await expect(rows).toContainText(['Incomplete']);
    await expect(rows.nth(1)).toContainText('50 g · 26%');
    await expect(rows.nth(1)).toContainText('100 g · 51%');
    await expect(rows.nth(1)).toContainText('20 g · 23%');

    await page.getByRole('tab', {name: 'Meals'}).click();
    rows = page.locator('.p-tabview-panel:visible tbody tr');
    await expect(rows).toHaveCount(3);
    await expect(rows.nth(0)).toContainText('Lunch');
    await expect(rows.nth(0)).toContainText('925 kcal');
    await expect(rows.nth(0)).toContainText('42.5 g');
    await expect(rows.nth(0)).toContainText('13:15');
    await expect(rows.nth(0)).toContainText('Chicken and rice');
    await expect(rows.nth(0)).toContainText('Manual');
    await expect(rows.nth(0)).toContainText('42.5 g · 25%');
    await expect(rows.nth(0)).toContainText('80.25 g · 48%');
    await expect(rows.nth(0)).toContainText('20 g · 27%');
    await expect(rows.nth(1)).toContainText('Snack 1');
    await expect(rows.nth(1)).toContainText('150 kcal');
    await expect(rows.nth(1)).toContainText('—');

    await page.getByRole('tab', {name: 'Fasting periods'}).click();
    rows = page.locator('.p-datatable', {hasText: 'Manual fasting periods'}).locator('tbody tr');
    await expect(rows).toHaveCount(1);
    await expect(rows.first()).toContainText('16h 0m');
    await expect(rows.first()).toContainText('Overnight fast');

    await page.locator('.p-tabview-panel:visible').getByRole('button', {name: 'New'}).click();
    let dialog = page.getByRole('dialog', {name: 'Fasting Period'});
    await dialog.getByLabel('Notes (optional)').fill('Created fast');
    const createRequest = page.waitForRequest(request => request.url().endsWith('/api/fasting-periods') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    const createdPayload = (await createRequest).postDataJSON();
    expect((new Date(createdPayload.endTime) - new Date(createdPayload.startTime)) / 3600000).toBe(16);
    const createdRow = page.locator('.p-datatable', {hasText: 'Manual fasting periods'}).locator('tbody tr').filter({hasText: 'Created fast'});
    await expect(createdRow).toHaveCount(1);

    await createdRow.getByRole('button', {name: 'Edit fasting period'}).click();
    dialog = page.getByRole('dialog', {name: 'Fasting Period'});
    await dialog.getByLabel('Notes (optional)').fill('Updated fast');
    const updateRequest = page.waitForRequest(request => /\/api\/fasting-periods\/\d+$/.test(request.url()) && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    await updateRequest;
    const updatedRow = page.locator('.p-tabview-panel:visible tbody tr').filter({hasText: 'Updated fast'});
    await expect(updatedRow).toHaveCount(1);

    page.once('dialog', confirmation => confirmation.accept());
    const deleteRequest = page.waitForRequest(request => /\/api\/fasting-periods\/\d+$/.test(request.url()) && request.method() === 'DELETE');
    await updatedRow.getByRole('button', {name: 'Delete fasting period'}).click();
    await deleteRequest;
    await expect(updatedRow).toHaveCount(0);
});

test('dashboard summarizes categorical back pain severity', async ({page}) => {
    const episodes = [
        {id: 1, date: '2026-08-12', dateFormat: '12/08/2026', time: '08:00:00', timeFormat: '08:00', period: 'MORNING', region: 'LOWER', side: 'LEFT', severity: 'MILD', note: null},
        {id: 2, date: '2026-08-12', dateFormat: '12/08/2026', time: '13:00:00', timeFormat: '13:00', period: 'MIDDAY', region: 'UPPER', side: 'RIGHT', severity: 'SEVERE', note: null},
        {id: 3, date: '2026-08-05', dateFormat: '05/08/2026', time: '08:00:00', timeFormat: '08:00', period: 'MORNING', region: 'MIDDLE', side: 'CENTER', severity: 'MODERATE', note: null},
        {id: 4, date: '2026-07-20', dateFormat: '20/07/2026', time: '20:00:00', timeFormat: '20:00', period: 'EVENING', region: 'LOWER', side: 'RIGHT', severity: 'EXTREME', note: null}
    ];
    await mockAuthenticatedDashboard(page, '2026-08-12', {backPainEpisodes: episodes});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Back'}).click();
    const summary = tabs.locator('.p-tabview-panel:visible .back-pain-summary');
    await expect(summary.locator('.p-col-5')).toHaveText(['Selected Day:', 'Last Week:', 'Change:', '30-Day Worst:']);
    await expect(summary.locator('.back-pain-summary-value')).toHaveText(['Severe', 'Moderate', 'Worse', 'Extreme']);
    const episodesTable = tabs.locator('.p-tabview-panel:visible .back-pain-episodes');
    await expect(episodesTable).toContainText('Morning');
    await expect(episodesTable).toContainText('Midday');
    await expect(episodesTable).not.toContainText('08:00');
    await expect(episodesTable).not.toContainText('13:00');
});

test.describe('mood period inference', () => {
    test.use({timezoneId: 'UTC'});

    test('dashboard mood infers an editable period and saves the selected dashboard date', async ({page}) => {
        await page.clock.setFixedTime(new Date('2026-08-11T11:59:00Z'));
        await mockAuthenticatedDashboard(page, '2026-08-11');
        await openSpaRoute(page, '/');

        const tabs = page.locator('.home-panels-tabs');
        await tabs.getByRole('tab', {name: 'Mood'}).click();
        const newMoodButton = tabs.locator('.p-tabview-panel:visible').getByRole('button', {name: 'New'});
        const dialog = page.getByRole('dialog', {name: 'Mood'});

        for (const scenario of [
            {time: '2026-08-11T11:59:00Z', period: 'Morning'},
            {time: '2026-08-11T12:00:00Z', period: 'Midday'},
            {time: '2026-08-11T17:59:00Z', period: 'Midday'},
            {time: '2026-08-11T18:00:00Z', period: 'Evening'}
        ]) {
            await page.clock.setFixedTime(new Date(scenario.time));
            await newMoodButton.click();
            await expect(dialog.locator('#period')).toContainText(scenario.period);
            if (scenario.period !== 'Evening') {
                await dialog.getByRole('button', {name: 'Cancel'}).click();
            }
        }

        const period = dialog.locator('#period');
        const mood = dialog.locator('#value');
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
});

test('history forms keep their date controls', async ({page}) => {
    await mockAuthenticatedDashboard(page);
    await openSpaRoute(page, '/moods');

    await page.getByRole('button', {name: 'New'}).click();
    const dialog = page.getByRole('dialog', {name: 'Mood'});
    await expect(dialog.locator('label').filter({hasText: /^Date$/})).toBeVisible();
});

test('cholesterol history shows changes and supports CRUD', async ({page}) => {
    const initialLipidPanels = [
        {id: 2, date: '2026-02-02', dateFormat: '02/02/2026', totalCholesterol: 211, hdlCholesterol: 63, ldlCholesterol: 133, triglycerides: 77},
        {id: 1, date: '2025-09-15', dateFormat: '15/09/2025', totalCholesterol: 210, hdlCholesterol: 60, ldlCholesterol: 138, triglycerides: 65}
    ];
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialLipidPanels});
    await openSpaRoute(page, '/cholesterol');

    let rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('211 mg/dL');
    await expect(rows.nth(0)).toContainText('+1 mg/dL');
    await expect(rows.nth(0)).toContainText('+3 mg/dL');
    await expect(rows.nth(0)).toContainText('-5 mg/dL');
    await expect(rows.nth(0)).toContainText('+12 mg/dL');
    await expect(rows.nth(1)).toContainText('—');

    await page.getByRole('button', {name: 'New', exact: true}).click();
    let dialog = page.getByRole('dialog', {name: 'Lipid Panel'});
    await dialog.getByLabel('Date').fill('16/08/2026');
    await dialog.getByLabel('Date').press('Escape');
    await dialog.getByLabel('Total Cholesterol').fill('205');
    await dialog.getByLabel('HDL Cholesterol').fill('64');
    await dialog.getByLabel('LDL Cholesterol').fill('130');
    await dialog.getByLabel('Triglycerides').fill('70');
    const createRequest = page.waitForRequest(request => request.url().endsWith('/api/lipid-panels') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await createRequest).postDataJSON()).toEqual({
        date: '2026-08-16',
        totalCholesterol: 205,
        hdlCholesterol: 64,
        ldlCholesterol: 130,
        triglycerides: 70
    });

    rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(3);
    await rows.nth(0).locator('button').nth(0).click();
    dialog = page.getByRole('dialog', {name: 'Lipid Panel'});
    await dialog.getByLabel('Total Cholesterol').fill('204');
    const updateRequest = page.waitForRequest(request => /\/api\/lipid-panels\/\d+$/.test(request.url()) && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await updateRequest).postDataJSON().totalCholesterol).toBe(204);
    await expect(rows.nth(0)).toContainText('204 mg/dL');

    page.once('dialog', confirmation => confirmation.accept());
    const deleteRequest = page.waitForRequest(request => /\/api\/lipid-panels\/\d+$/.test(request.url()) && request.method() === 'DELETE');
    await rows.nth(0).locator('button').nth(1).click();
    await deleteRequest;
    await expect(rows).toHaveCount(2);
});

test('home shows the latest lipid panel and cholesterol charts', async ({page}) => {
    const initialLipidPanels = [
        {id: 2, date: '2026-02-02', dateFormat: '02/02/2026', totalCholesterol: 211, hdlCholesterol: 63, ldlCholesterol: 133, triglycerides: 77},
        {id: 1, date: '2025-09-15', dateFormat: '15/09/2025', totalCholesterol: 210, hdlCholesterol: 60, ldlCholesterol: 138, triglycerides: 65}
    ];
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialLipidPanels});
    const consoleErrors = [];
    page.on('console', message => {
        if (message.type() === 'error') {
            consoleErrors.push(message.text());
        }
    });
    await openSpaRoute(page, '/');

    const homeTabs = page.locator('.home-panels-tabs');
    await page.locator('.dashboard-charts-trigger').scrollIntoViewIfNeeded();
    await page.locator('label[for="chart_type_all"]').click();
    await expect(page.getByRole('radio', {name: 'All'})).toBeChecked();
    const charts = page.locator('#measures-chart');
    await charts.getByRole('tab', {name: 'Cholesterol'}).click();
    const cholesterolCharts = charts.getByRole('tabpanel', {name: 'Cholesterol'});
    await expect(cholesterolCharts.locator('canvas')).toHaveCount(4);
    await expect(cholesterolCharts).not.toContainText('No lipid panel data');
    expect(consoleErrors).not.toEqual(expect.arrayContaining([expect.stringContaining('Container is not set or can not be properly recognized')]));

    await homeTabs.getByRole('tab', {name: 'Body'}).click();
    const bodyPanel = homeTabs.locator('.p-tabview-panel:visible');
    await expect(bodyPanel).toContainText('Latest Lipid Panel');
    await expect(bodyPanel).toContainText('02/02/2026');
    await expect(bodyPanel).toContainText('211 mg/dL');
    await expect(bodyPanel).toContainText('+1 mg/dL');
    await expect(bodyPanel.locator('#fat-bar-status svg')).toBeVisible();
    await expect(bodyPanel.locator('#bmi-bar-status svg')).toBeVisible();
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
