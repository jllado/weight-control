const {test, expect} = require('@playwright/test');
const path = require('node:path');
const vm = require('node:vm');

const coachUrl = process.env.VITE_CHATGPT_COACH_URL || 'https://chatgpt.test/g/weight-control-coach';
const coachOriginPattern = `${new URL(coachUrl).origin}/**`;

async function expectWholeWords(locator) {
    expect(await locator.evaluateAll(elements => elements.every(element => {
        const text = element.firstChild;
        return [...text.textContent.matchAll(/\S+/g)].every(match => {
            const range = document.createRange();
            range.setStart(text, match.index);
            range.setEnd(text, match.index + match[0].length);
            return range.getClientRects().length === 1;
        });
    }))).toBe(true);
}

async function expectChatGptIcon(button) {
    const icon = button.locator('.p-button-icon.chatgpt-icon');
    await expect(icon).toBeVisible();
    await expect(icon).toHaveAttribute('aria-hidden', 'true');
    const appearance = await icon.evaluate(element => ({
        width: element.getBoundingClientRect().width,
        height: element.getBoundingClientRect().height,
        rem: parseFloat(getComputedStyle(document.documentElement).fontSize),
        background: getComputedStyle(element).backgroundImage
    }));
    expect(appearance.width).toBeCloseTo(appearance.rem, 1);
    expect(appearance.height).toBeCloseTo(appearance.rem, 1);
    expect(appearance.background).toContain('chatgpt-icon');
}

function loadPushWorker(source, {fetch = async () => ({ok: true}), windowClients = [], shownNotifications = []} = {}) {
    const listeners = {};
    const notifications = [];
    const openedUrls = [];
    const context = {
        URL,
        fetch,
        self: {
            location: {origin: 'https://weightcontrol.test'},
            registration: {
                getNotifications: async () => shownNotifications,
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

const {googleClientScript, profile, dashboard, dashboardDailyStatus, dashboardWeek} = require('../fixtures/dashboard.cjs');

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
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
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
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
        if (path === '/api/auth/me') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true})});
        }
        if (path === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        if (path === '/api/routines' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(routines)});
        }
        if (path === '/api/routines' && request.method() === 'POST') {
            const payload = request.postDataJSON();
            const created = {id: Math.max(0, ...routines.map(routine => routine.id)) + 1, startDate: '2026-08-01T00:00:00+02:00', lastTimeDate: null, currentStrike: 0, bestStrike: 0, times: [], ...payload, reminders: payload.reminderTimes.map((time, index) => ({id: 100 + index, time}))};
            routines = [...routines, created];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(created)});
        }
        const routineMatch = path.match(/^\/api\/routines\/(\d+)$/);
        if (routineMatch && request.method() === 'PUT') {
            const id = Number(routineMatch[1]);
            const payload = request.postDataJSON();
            routines = routines.map(routine => routine.id === id ? {
                ...routine,
                name: payload.name,
                types: payload.types,
                personalRecordsEnabled: payload.personalRecordsEnabled,
                automaticTrigger: payload.automaticTrigger,
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
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
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
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({morningTime: '07:30:00', middayTime: '13:30:00', eveningTime: '20:30:00', weightTime: '05:00:00', bloodPressureTime: '05:15:00', weightDay: 'SATURDAY', bloodPressureDay: 'SATURDAY', timeZone: 'Europe/Madrid'})});
        }
        if (path === '/api/weekly-summary/config') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({enabled: false, canSend: true, recipientEmail: 'jllado@gmail.com', deliveryDay: 'MONDAY', deliveryTime: '08:00:00', timeZone: 'Europe/Madrid'})});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

function workoutResponse(id, payload, exercises) {
    const [year, month, day] = payload.workoutDate.split('-');
    return {
        id,
        sessionReference: `session-${id}`,
        workoutDate: payload.workoutDate,
        workoutDateFormat: `${day}/${month}/${year}`,
        note: payload.note,
        startTime: payload.startTime ?? null,
        endTime: payload.endTime ?? null,
        durationMinutes: payload.durationMinutes ?? null,
        warmUpMinutes: payload.warmUpMinutes ?? null,
        trainingMinutes: payload.trainingMinutes ?? null,
        stretchingMinutes: payload.stretchingMinutes ?? null,
        cardioMinutes: payload.cardioMinutes ?? null,
        plannedSessionName: payload.plannedSessionName ?? null,
        plannedTargets: payload.plannedTargets ?? null,
        saunaSession: payload.saunaSession ?? false,
        saunaRoundsMinutes: payload.saunaRoundsMinutes ?? [],
        plannedSaunaRoundsMinutes: payload.plannedSaunaRoundsMinutes ?? null,
        assessment: null,
        lines: payload.lines.map((line, position) => {
            const exercise = exercises.find(item => item.id === line.exerciseId);
            const segments = line.segments.map((segment, segmentPosition) => ({position: segmentPosition, ...segment}));
            return {
                exerciseId: exercise.id,
                exerciseName: exercise.name,
                exerciseDescription: exercise.description,
                trackingMode: exercise.trackingMode,
                stretchingUnit: line.stretchingUnit ?? 'SECONDS',
                exerciseType: exercise.exerciseType || 'TRAINING',
                supersetGroupId: line.supersetGroupId ?? null,
                position,
                calories: line.calories,
                averageHeartRate: line.averageHeartRate,
                exerciseDurationSeconds: line.exerciseDurationSeconds,
                sets: exercise.trackingMode === 'CARDIO' ? [] : segments,
                intervals: exercise.trackingMode === 'CARDIO' ? segments : []
            };
        })
    };
}

function workoutDays(workouts) {
    const days = new Map();
    workouts.forEach(workout => {
        if (!days.has(workout.workoutDate)) days.set(workout.workoutDate, {workoutDate: workout.workoutDate, workoutDateFormat: workout.workoutDateFormat, sessions: [], assessment: null});
        const day = days.get(workout.workoutDate);
        day.sessions.push(workout);
        if (workout.assessment) day.assessment = workout.assessment;
    });
    return [...days.values()];
}

async function mockAuthenticatedWorkouts(page, initialWorkouts, exercises, {currentRecords = [], historyEvents = [], achievements = [], catalog = [], initialNotifications = [], failWorkoutEvents = false, accountEmail = 'jllado@gmail.com'} = {}) {
    await page.setViewportSize({width: 1440, height: 900});
    exercises.forEach(exercise => { if (exercise.exerciseType === 'TRAINING' && exercise.trackingMode !== 'CARDIO') exercise.primaryMuscleGroup ??= 'CORE'; });
    let workouts = initialWorkouts.map(workout => ({...workout, sessionReference: workout.sessionReference || `session-${workout.id}`, lines: workout.lines.map(line => ({...line}))}));
    let notifications = initialNotifications.map(notification => ({...notification}));
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
        if (path === '/api/auth/me') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: accountEmail, displayName: 'Jordi', authenticated: true})});
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
        const notificationRescheduleMatch = path.match(/^\/api\/notifications\/(\d+)\/reschedule$/);
        if (notificationRescheduleMatch && request.method() === 'POST') {
            const id = Number(notificationRescheduleMatch[1]);
            const {date, time} = request.postDataJSON();
            const notification = notifications.find(item => item.id === id);
            Object.assign(notification, {reminderDate: date, availableAt: `${date}T${time}:00+02:00`});
            notifications = notifications.filter(item => item.id !== id);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(notification)});
        }
        if (path === '/api/stretching-sets') return route.fulfill({json: []});
        if (path === '/api/workout-exercises' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(exercises)});
        }
        if (path === '/api/workouts/training-balance') return route.fulfill({json: balanceResponse(new URL(request.url()).searchParams.get('date'))});
        if (path === '/api/workouts/diary' && request.method() === 'GET') {
            const pageNumber = Number(new URL(request.url()).searchParams.get('page') || 0);
            const size = Number(new URL(request.url()).searchParams.get('size') || 10);
            const days = workoutDays(workouts);
            const items = days.slice(pageNumber * size, (pageNumber + 1) * size);
            const ids = new Set(items.flatMap(day => day.sessions).map(workout => workout.id));
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({
                items,
                recordEvents: historyEvents.filter(event => ids.has(event.source?.id)),
                page: pageNumber,
                size,
                totalElements: days.length,
                totalPages: Math.ceil(days.length / size)
            })});
        }
        if (path === '/api/workouts/preload' && request.method() === 'GET') {
            const through = new URL(request.url()).searchParams.get('through');
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(workouts.filter(workout => workout.workoutDate <= through).sort((left, right) => right.workoutDate.localeCompare(left.workoutDate)).slice(0, 40))});
        }
        if (path === '/api/workouts' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(workouts)});
        }
        if (path === '/api/workout-plans/current') return route.fulfill({status: 204});
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
        if (workoutMatch && request.method() === 'DELETE') {
            workouts = workouts.filter(workout => workout.id !== Number(workoutMatch[1]));
            return route.fulfill({status: 204});
        }
        if (workoutMatch && request.method() === 'PUT') {
            const id = Number(workoutMatch[1]);
            const workout = {
                ...workoutResponse(id, request.postDataJSON(), exercises),
                assessment: null
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
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
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
        if (path.startsWith('/api/back-pain-episodes/') && request.method() === 'PUT') {
            const id = Number(path.split('/').pop());
            const episode = {...episodes.find(item => item.id === id), ...request.postDataJSON()};
            episodes = episodes.map(item => item.id === id ? episode : item);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(episode)});
        }
        if (path.startsWith('/api/back-pain-episodes/') && request.method() === 'DELETE') {
            episodes = episodes.filter(item => item.id !== Number(path.split('/').pop()));
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

async function mockAuthenticatedAgenda(page, agenda) {
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
        if (path === '/api/auth/me') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({email: 'jllado@gmail.com', displayName: 'Jordi', authenticated: true})});
        }
        if (path === '/api/profile') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profile)});
        }
        if (path === '/api/push/agenda') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(agenda)});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

async function mockAuthenticatedReflections(page, reflection = null, actionConfigured = reflection !== null) {
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({
        contentType: 'application/javascript',
        body: googleClientScript
    }));
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
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
                actionConfigured,
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
    let reminderSettings = {morningTime: '07:30:00', middayTime: '13:30:00', eveningTime: '20:30:00', weightTime: '05:00:00', bloodPressureTime: '05:15:00', weightDay: 'SATURDAY', bloodPressureDay: 'SATURDAY', timeZone: 'Europe/Madrid'};
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
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
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

function foodWithNutrients(food) {
    return {vitaminDMicrograms: 0, omega3Milligrams: 1, magnesiumMilligrams: 10, nutrientSource: 'Test composition', nutrientsEstimated: false,
        ...food, ...(food.reference ? {reference: {vitaminDMicrograms: 0, omega3Milligrams: 1, magnesiumMilligrams: 10, ...food.reference}} : {})};
}

function fixtureNutrientSummary(meals) {
    const foods = meals.flatMap(meal => meal.dishes || []);
    const fields = ['vitaminDMicrograms', 'omega3Milligrams', 'magnesiumMilligrams'];
    const known = foods.filter(food => fields.every(key => food[key] != null));
    return {...Object.fromEntries(fields.map(key => [key, known.length ? known.reduce((sum, food) => sum + food[key], 0) : null])),
        foodsWithValues: known.length, totalFoods: foods.length, estimatedFoods: known.filter(food => food.nutrientsEstimated).length,
        mealsWithoutFoods: meals.filter(meal => !meal.dishes?.length).length};
}

async function enterFoodNutrients(dialog) {
    await dialog.getByLabel('Vitamin D (µg)', {exact: true}).fill('0');
    await dialog.getByLabel('Omega-3 (mg)', {exact: true}).fill('1');
    await dialog.getByLabel('Magnesium (mg)', {exact: true}).fill('10');
    await dialog.getByLabel('Nutrient source', {exact: true}).fill('Test composition');
}

async function mockAuthenticatedDashboard(page, selectedDate = dashboard.anchorDate, {requiresLogin = false, backPainEpisodes = [], initialMeals = [], initialFastingPeriods = [], fastingAchievements = [], initialLipidPanels = [], initialSleeps = [], initialWorkouts = [], workoutExercises = [], sleepLoad = Promise.resolve(), workoutLoad = Promise.resolve(), currentRecords = [], dashboardResponse, coachMetricsResponse, overallProgressResponse, profileResponse = profile, onApiRequest} = {}) {
    let authenticated = !requiresLogin;
    const decisionOutcomes = [];
    let meals = initialMeals.map(meal => ({...meal, dishes: (meal.dishes || []).map(foodWithNutrients)}));
    const catalog = new Map();
    [...meals].sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id).forEach(meal => [...(meal.dishes || [])].reverse().forEach(food => {
        const name = food.name.trim().toLowerCase();
        if (!catalog.has(name)) catalog.set(name, {...food, name: food.name.trim(), id: catalog.size + 1});
    }));
    let foods = [...catalog.values()];
    let nextFoodId = foods.length + 1;
    let fastingPeriods = initialFastingPeriods.map(period => ({...period}));
    let lipidPanels = initialLipidPanels.map(panel => ({...panel}));
    const sleeps = initialSleeps.map(sleep => ({...sleep}));
    let workouts = initialWorkouts.map(workout => ({...workout, sessionReference: workout.sessionReference || `session-${workout.id}`, lines: workout.lines.map(line => ({...line}))}));
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
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
        if (path === '/api/coach-warnings') return route.fulfill({json: {active: [], hasHistory: false}});
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
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(profileResponse)});
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
        if (path === '/api/dashboard/overall-progress') {
            const progressDate = url.searchParams.get('selectedDate');
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(overallProgressResponse ?? {
                status: null, score: null, currentStart: progressDate, currentEnd: progressDate,
                previousStart: progressDate, previousEnd: progressDate, contributions: []
            })});
        }
        if (path === '/api/dashboard/coach-metrics') {
            if (coachMetricsResponse) {
                return route.fulfill({contentType: 'application/json', body: JSON.stringify(coachMetricsResponse)});
            }
            const selectedCoachDate = url.searchParams.get('selectedDate');
            const selectedCoachDateValue = new Date(`${selectedCoachDate}T12:00:00Z`);
            selectedCoachDateValue.setUTCDate(selectedCoachDateValue.getUTCDate() - ((selectedCoachDateValue.getUTCDay() + 1) % 7));
            const weekStart = selectedCoachDateValue.toISOString().slice(0, 10);
            const weekEndValue = new Date(selectedCoachDateValue);
            weekEndValue.setUTCDate(weekEndValue.getUTCDate() + 6);
            const weekEnd = weekEndValue.toISOString().slice(0, 10);
            const toWorkoutMetric = workout => ({
                date: workout.workoutDate,
                dateFormat: workout.workoutDate.split('-').reverse().join('/'),
                summary: workout.sessions.flatMap(session => session.lines).map(line => line.exerciseName).join(', '),
                goalAlignmentScore: workout.assessment?.goalAlignmentScore ?? null,
                estimatedTrainingDemandScore: workout.assessment?.estimatedTrainingDemandScore ?? null,
                totals: {workoutCount: 1, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}
            });
            const totalsFor = workoutMetrics => ({workoutCount: workoutMetrics.length, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0});
            const selectedWorkouts = workoutDays(workouts.filter(workout => workout.workoutDate >= weekStart && workout.workoutDate <= weekEnd)).map(toWorkoutMetric);
            const previousWeekStartValue = new Date(selectedCoachDateValue);
            previousWeekStartValue.setUTCDate(previousWeekStartValue.getUTCDate() - 7);
            const previousWeekStart = previousWeekStartValue.toISOString().slice(0, 10);
            const previousWeekEndValue = new Date(previousWeekStartValue);
            previousWeekEndValue.setUTCDate(previousWeekEndValue.getUTCDate() + 6);
            const previousWeekEnd = previousWeekEndValue.toISOString().slice(0, 10);
            const previousWeekWorkouts = workoutDays(workouts.filter(workout => workout.workoutDate >= previousWeekStart && workout.workoutDate <= previousWeekEnd)).map(toWorkoutMetric);
            const previousWeekToDate = new Date(`${selectedCoachDate}T12:00:00Z`);
            previousWeekToDate.setUTCDate(previousWeekToDate.getUTCDate() - 7);
            const previousWeekToDateEnd = previousWeekToDate.toISOString().slice(0, 10);
            const totals = totalsFor(selectedWorkouts);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({
                selectedWeek: {startDate: weekStart, endDate: weekEnd, reflections: [], workouts: selectedWorkouts, totals},
                previousWeek: {startDate: previousWeekStart, endDate: previousWeekEnd, reflections: [], workouts: previousWeekWorkouts, totals: totalsFor(previousWeekWorkouts)},
                selectedWeekToDate: {startDate: weekStart, endDate: selectedCoachDate, reflections: [], workouts: selectedWorkouts.filter(workout => workout.date <= selectedCoachDate), totals: totalsFor(selectedWorkouts.filter(workout => workout.date <= selectedCoachDate))},
                previousWeekToDate: {startDate: previousWeekStart, endDate: previousWeekToDateEnd, reflections: [], workouts: previousWeekWorkouts.filter(workout => workout.date <= previousWeekToDateEnd), totals: totalsFor(previousWeekWorkouts.filter(workout => workout.date <= previousWeekToDateEnd))},
                planProgressTrend: {latestScore: null, previousScore: null, currentThirtyDayAverage: null, previousThirtyDayAverage: null},
                reflections: [], workouts: selectedWorkouts, weeklyWorkouts: selectedWorkouts.length ? [{startDate: weekStart, endDate: weekEnd, totals}] : []
            })});
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
        if (path === '/api/workout-exercises') return route.fulfill({json: workoutExercises});
        if (path === '/api/workout-plans/current') return route.fulfill({status: 204});
        if (path === '/api/workouts/preload') return route.fulfill({json: workouts.filter(workout => workout.workoutDate <= url.searchParams.get('through'))});
        if (path === '/api/workouts' && request.method() === 'POST') {
            const id = Math.max(0, ...workouts.map(workout => workout.id)) + 1;
            const workout = workoutResponse(id, request.postDataJSON(), workoutExercises);
            workouts.push(workout);
            return route.fulfill({json: {result: workout, recordAchievements: []}});
        }
        const workoutMatch = path.match(/^\/api\/workouts\/(\d+)$/);
        if (workoutMatch && request.method() === 'PUT') {
            const id = Number(workoutMatch[1]);
            const workout = workoutResponse(id, request.postDataJSON(), workoutExercises);
            workouts = workouts.map(item => item.id === id ? workout : item);
            return route.fulfill({json: {result: workout, recordAchievements: []}});
        }
        if (workoutMatch && request.method() === 'DELETE') {
            workouts = workouts.filter(item => item.id !== Number(workoutMatch[1]));
            return route.fulfill({status: 204});
        }
        if (path === '/api/workouts/dashboard' && request.method() === 'GET') {
            await workoutLoad;
            const date = url.searchParams.get('date');
            const previousWeekDate = new Date(`${date}T12:00:00Z`);
            previousWeekDate.setUTCDate(previousWeekDate.getUTCDate() - 7);
            const previousWeek = previousWeekDate.toISOString().slice(0, 10);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({
                days: workoutDays(workouts.filter(workout => workout.workoutDate === date || workout.workoutDate === previousWeek)),
                currentWorkouts: workouts.filter(workout => workout.workoutDate === date),
                previousWeekWorkouts: workouts.filter(workout => workout.workoutDate === previousWeek),
                preloadWorkouts: workouts.filter(workout => workout.workoutDate <= date).sort((left, right) => right.workoutDate.localeCompare(left.workoutDate)).slice(0, 40),
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
        if (path === '/api/foods' && request.method() === 'GET') {
            return route.fulfill({json: foods});
        }
        if (path === '/api/foods' && request.method() === 'POST') {
            const food = {...request.postDataJSON(), id: nextFoodId++};
            if (foods.some(item => item.name.trim().toLowerCase() === food.name.trim().toLowerCase())) return route.fulfill({status: 400, body: 'A food with this name already exists.'});
            foods.push(food);
            return route.fulfill({json: food});
        }
        const foodMatch = path.match(/^\/api\/foods\/(\d+)$/);
        if (foodMatch && request.method() === 'PUT') {
            const food = {...request.postDataJSON(), id: Number(foodMatch[1])};
            foods = foods.map(item => item.id === food.id ? food : item);
            return route.fulfill({json: food});
        }
        if (foodMatch && request.method() === 'DELETE') {
            foods = foods.filter(food => food.id !== Number(foodMatch[1]));
            return route.fulfill({status: 204});
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
        const mealRatingMatch = path.match(/^\/api\/meals\/(\d+)\/rating$/);
        if (mealRatingMatch && request.method() === 'PUT') {
            const id = Number(mealRatingMatch[1]);
            meals = meals.map(meal => meal.id === id ? {...meal, rating: request.postDataJSON().rating} : meal);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(meals.find(meal => meal.id === id))});
        }
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
                nutrients: fixtureNutrientSummary(summary.meals),
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
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: period, recordAchievements: fastingAchievements})});
        }
        const fastingPeriodMatch = path.match(/^\/api\/fasting-periods\/(\d+)$/);
        if (fastingPeriodMatch && request.method() === 'PUT') {
            const id = Number(fastingPeriodMatch[1]);
            fastingPeriods = fastingPeriods.map(period => period.id === id ? {...period, ...request.postDataJSON()} : period);
            return route.fulfill({contentType: 'application/json', body: JSON.stringify({result: fastingPeriods.find(period => period.id === id), recordAchievements: []})});
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
        personalRecordsEnabled: true,
        automaticTrigger: 'NONE',
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

async function captureCompactGuidedWorkout(page, guided, testInfo, stage) {
    for (const [width, height] of [[376, 667], [390, 844], [1280, 900]]) {
        await page.setViewportSize({width, height});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        expect(await guided.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await expect(guided.locator(':scope > .p-dialog-content')).toHaveCSS('display', 'flex');
        await expect(guided.locator(':scope > .p-dialog-content')).toHaveCSS('overflow-y', 'hidden');
        for (const control of [guided.locator('.guided-progress'), guided.locator('.guided-next'), guided.getByRole('button', {name: 'Complete set', exact: true}), guided.getByRole('button', {name: 'Close', exact: true})]) {
            await expect(control).toBeVisible();
            const bounds = await control.boundingBox();
            expect(bounds.y).toBeGreaterThanOrEqual(0);
            expect(bounds.y + bounds.height).toBeLessThanOrEqual(height);
        }
        const {pictureBounds, summaryBounds} = await guided.locator('.guided-card-heading').evaluate(element => ({
            pictureBounds: element.querySelector('.exercise-picture-button').getBoundingClientRect().toJSON(),
            summaryBounds: element.querySelector('.guided-card-summary').getBoundingClientRect().toJSON()
        }));
        expect(pictureBounds.width).toBeLessThanOrEqual(64);
        expect(pictureBounds.height).toBeLessThanOrEqual(64);
        expect(pictureBounds.x + pictureBounds.width).toBeLessThanOrEqual(summaryBounds.x);
        expect(Math.abs(pictureBounds.y - summaryBounds.y)).toBeLessThanOrEqual(1);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-workout-${stage}-${width}.png`)});
        const details = guided.locator('.guided-details');
        await expect(details).not.toHaveAttribute('open', '');
        await details.locator('summary').focus();
        await page.keyboard.press('Enter');
        await expect(details).toHaveAttribute('open', '');
        await expect(details.locator('p')).toBeVisible();
        const progressBeforeScroll = await guided.locator('.guided-progress').boundingBox();
        const nextBeforeScroll = await guided.locator('.guided-next').boundingBox();
        await guided.locator('.guided-content').evaluate(element => { element.scrollTop = element.scrollHeight; });
        expect(await guided.locator('.guided-progress').boundingBox()).toEqual(progressBeforeScroll);
        expect(await guided.locator('.guided-next').boundingBox()).toEqual(nextBeforeScroll);
        expect(await guided.locator('.guided-content').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-workout-${stage}-details-${width}.png`)});
        await details.locator('summary').focus();
        await page.keyboard.press('Enter');
        await expect(details).not.toHaveAttribute('open', '');
        await guided.locator('.guided-content').evaluate(element => { element.scrollTop = 0; });
    }
}

test('credential-only Google response signs in from an Android-sized app window', async ({page}) => {
    await mockLogin(page);
    const loginRequest = page.waitForRequest(request => request.url().endsWith('/api/auth/google') && request.method() === 'POST');

    await page.goto('/');
    await expect(page).toHaveURL('/login');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    expect((await loginRequest).postDataJSON()).toEqual({credential: 'android-id-token'});
    await expect(page).toHaveURL('/');
});

test('authentication failure is visible on the login page', async ({page}) => {
    await mockLogin(page, 400);

    await page.goto('/');
    await expect(page).toHaveURL('/login');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page.getByText('Unable to sign in. Please try again.')).toBeVisible();
    await expect(page).toHaveURL('/login');
});

test('Coach launcher is authenticated and opens the configured GPT in a new tab', async ({page}, testInfo) => {
    await mockLogin(page);
    await page.goto('/');
    await expect(page).toHaveURL('/login');
    await expect(page.getByRole('button', {name: 'Open Coach'})).toHaveCount(0);

    const authenticatedPage = await page.context().newPage();
    await mockAuthenticatedReflections(authenticatedPage);
    await authenticatedPage.context().route(coachOriginPattern, route => route.fulfill({
        contentType: 'text/html',
        body: '<title>Weight Control Coach</title>'
    }));
    await openSpaRoute(authenticatedPage, '/reflections');

    const coachPagePromise = authenticatedPage.context().waitForEvent('page');
    const launcher = authenticatedPage.getByRole('button', {name: 'Open Coach'});
    for (const width of [376, 1280]) {
        await authenticatedPage.setViewportSize({width, height: 900});
        await expectChatGptIcon(launcher);
        await authenticatedPage.locator('.app-header-actions').screenshot({path: testInfo.outputPath(`chatgpt-shell-${width}.png`)});
    }
    await launcher.click();
    const coachPage = await coachPagePromise;
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
            updatedAt: '2026-08-20T18:30:00Z'
        },
        lines: [{exerciseId: 1, exerciseName: 'Bench press', exerciseDescription: 'Horizontal press.', trackingMode: 'REPS', position: 0, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 8, durationSeconds: null, weight: 60}], intervals: []}]
    };
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route(coachOriginPattern, route => route.fulfill({
        contentType: 'text/html',
        body: '<title>Weight Control Coach</title>'
    }));
    await page.setViewportSize({width: 1440, height: 900});
    await mockAuthenticatedWorkouts(page, [workout], exercises);
    await openSpaRoute(page, '/workouts');

    const row = page.locator('tbody tr').filter({hasText: 'Bench press'});
    await expect(row.getByText('Goal 8 · Demand 7')).toBeVisible();
    await row.getByText('Goal 8 · Demand 7').click();
    const dialog = page.getByRole('dialog', {name: 'Training day assessment'});
    await expect(dialog).toContainText('Improve upper-body strength');
    await expect(dialog).toContainText('Add one pulling set.');
    await dialog.locator('.p-dialog-footer').getByRole('button', {name: 'Close'}).click();

    const coachPagePromise = context.waitForEvent('page');
    const rateDay = row.getByRole('button', {name: 'Rate day', exact: true});
    await expectChatGptIcon(rateDay);
    await rateDay.click();
    const coachPage = await coachPagePromise;
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('Assess all my workout sessions on 2026-08-20 together as one training day against my active coaching plan.');
    await coachPage.close();
});

test('workout navigation scrolls in one row on mobile and preserves tab content', async ({page}, testInfo) => {
    await mockWeeklyPlans(page);
    await page.setViewportSize({width: 390, height: 900});
    await openSpaRoute(page, '/workouts');
    const tabs = page.locator('.workout-tabs');
    const content = tabs.locator('.p-tabview-nav-content');
    const next = tabs.locator('.p-tabview-nav-next');
    const previous = tabs.locator('.p-tabview-nav-prev');
    const clickScrollArrow = button => Promise.all([
        content.evaluate(element => new Promise(resolve => element.addEventListener('scrollend', resolve, {once: true}))),
        button.click()
    ]);
    await expect(next).toBeVisible();
    await expect(previous).toHaveCount(0);
    while (await content.evaluate(element => element.scrollWidth - element.clientWidth - element.scrollLeft > 1)) {
        const before = await content.evaluate(element => element.scrollLeft);
        await clickScrollArrow(next);
        await expect.poll(() => content.evaluate(element => element.scrollLeft)).toBeGreaterThan(before);
    }
    await expect(next).toHaveCount(0);
    await expect(tabs.getByRole('tab', {name: 'Training balance', exact: true}).locator('.p-tabview-title')).toBeInViewport({ratio: 1});
    await expect(previous).toBeVisible();
    await tabs.getByRole('tab', {name: 'Plan', exact: true}).click();
    await expect(page.getByRole('region', {name: 'Weekly workout plan'})).toContainText('No weekly plan yet.');
    while (await content.evaluate(element => element.scrollLeft > 1)) {
        const before = await content.evaluate(element => element.scrollLeft);
        await clickScrollArrow(previous);
        await expect.poll(() => content.evaluate(element => element.scrollLeft)).toBeLessThan(before);
    }
    await expect(previous).toHaveCount(0);
    await tabs.getByRole('tab', {name: 'Diary', exact: true}).click();
    for (const name of ['Exercises', 'Cardio', 'Warm-ups', 'Stretching', 'Plan', 'Training balance']) {
        await page.keyboard.press('ArrowRight');
        await expect(tabs.getByRole('tab', {name, exact: true})).toBeFocused();
        await page.keyboard.press('Enter');
        await expect(tabs.getByRole('tabpanel', {name, exact: true})).toBeVisible();
    }
    await tabs.getByRole('tab', {name: 'Stretching', exact: true}).click();
    for (const width of [390, 393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        const positions = await tabs.getByRole('tab').evaluateAll(elements => elements.map(element => element.getBoundingClientRect().top));
        expect(Math.max(...positions) - Math.min(...positions)).toBeLessThan(1);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await expect(tabs.getByRole('tab', {name: 'Stretching', exact: true})).toHaveAttribute('aria-selected', 'true');
        await page.screenshot({path: testInfo.outputPath(`workout-tabs-${width}.png`), fullPage: true});
    }
    expect(await content.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
});

test('workout diary uses expandable compact rows on mobile', async ({page}) => {
    const exercises = [
        {id: 1, name: 'Cat-cow', description: 'Spinal warm-up.', trackingMode: 'REPS', exerciseType: 'WARM_UP'},
        {id: 2, name: 'Bench press', description: 'Horizontal press.', trackingMode: 'REPS', exerciseType: 'TRAINING'}
    ];
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
            updatedAt: '2026-08-20T18:30:00Z'
        },
        lines: [
            {exerciseId: 1, exerciseName: 'Cat-cow', exerciseDescription: 'Spinal warm-up.', trackingMode: 'REPS', exerciseType: 'WARM_UP', position: 0, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 10, durationSeconds: null, weight: 0}], intervals: []},
            {exerciseId: 2, exerciseName: 'Bench press', exerciseDescription: 'Horizontal press.', trackingMode: 'REPS', exerciseType: 'TRAINING', position: 1, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 8, durationSeconds: null, weight: 60}], intervals: []}
        ]
    };
    const warmUpOnlyWorkout = {
        ...workout,
        id: 8,
        workoutDate: '2026-08-19',
        workoutDateFormat: '19/08/2026',
        assessment: null,
        lines: [workout.lines[0]]
    };
    await mockAuthenticatedWorkouts(page, [workout, warmUpOnlyWorkout], exercises);
    await page.setViewportSize({width: 375, height: 800});
    await openSpaRoute(page, '/workouts');

    const mobileWorkout = page.locator('.mobile-diary-workout').filter({hasText: 'Bench press'});
    await expect.poll(() => page.evaluate(() => window.matchMedia('(max-width: 575px)').matches)).toBe(true);
    await expect(page.locator('.diary-desktop')).toBeHidden();
    await expect(page.locator('.mobile-diary-workout')).toHaveCount(2);
    await expect(mobileWorkout).toContainText('Bench press');
    await expect(mobileWorkout).not.toContainText('Cat-cow');
    await expect(page.locator('.mobile-diary-workout').filter({hasText: 'Cat-cow'})).toBeVisible();
    await mobileWorkout.getByRole('button', {name: /Bench press/}).click();
    await expect(mobileWorkout).toContainText('Cat-cow');
    await expect(mobileWorkout.getByText('Warm-up', {exact: true})).toBeVisible();
    await expect(mobileWorkout.getByText('60 kg × 8 reps')).toBeVisible();
    await expect(page.locator('.mobile-diary-day').filter({hasText: 'Bench press'}).getByText('Goal 8 · Demand 7')).toBeVisible();
    await mobileWorkout.getByRole('button', {name: 'Edit workout'}).click();
    await expect(page.getByRole('dialog', {name: 'Workout'})).toBeVisible();
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
    await dialog.getByRole('button', {name: 'Expand Strength'}).click();
    await expect(dialog.locator('.workout-line-card').getByRole('button', {name: /^Expand /})).toHaveCount(3);
    await cards.nth(0).getByRole('button', {name: /^Expand /}).click();
    await cards.nth(1).getByRole('button', {name: /^Expand /}).click();
    await expect(dialog.locator('.workout-line-card').getByRole('button', {name: /^Collapse /})).toHaveCount(2);
    const repetitions = cards.nth(0).getByText('Repetitions', {exact: true}).locator('..').locator('input');
    await repetitions.fill('12');
    await repetitions.press('Tab');
    await cards.nth(0).getByRole('button', {name: /^Collapse /}).click();
    await expect(repetitions).not.toBeVisible();
    await cards.nth(0).getByRole('button', {name: /^Expand /}).focus();
    await page.keyboard.press('Enter');
    await expect(repetitions).toHaveValue('12');
    await repetitions.fill('');
    await repetitions.press('Tab');
    await cards.nth(0).getByRole('button', {name: /^Collapse /}).click();
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(cards.nth(0).getByText('Repetitions are required', {exact: true})).toBeVisible();
    await repetitions.fill('12');
    await repetitions.press('Tab');

    await expect(cards.nth(0).getByRole('button', {name: 'Move exercise 1 up'})).toBeDisabled();
    await expect(cards.nth(2).getByRole('button', {name: 'Move exercise 3 down'})).toBeDisabled();
    await cards.nth(0).getByRole('button', {name: 'Move exercise 1 down'}).click();
    await cards.nth(2).getByRole('button', {name: 'Move exercise 3 up'}).click();
    await expect(cards.nth(0)).toContainText('Bench press');
    await expect(cards.nth(1)).toContainText('Plank');
    await expect(cards.nth(1).getByRole('button', {name: /^Expand /})).toHaveAttribute('aria-expanded', 'false');
    await expect(cards.nth(2).getByRole('button', {name: /^Collapse /})).toHaveAttribute('aria-expanded', 'true');
    await expect(cards.nth(2)).toContainText('Squat');
    const updateRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts/7') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    const savedLines = (await updateRequest).postDataJSON().lines;
    expect(savedLines.map(line => line.exerciseId)).toEqual([2, 3, 1]);
    expect(savedLines[2].segments[0].repetitions).toBe(12);
    expect(savedLines.every(line => !('collapsed' in line) && !('localId' in line))).toBe(true);
    await expect(dialog).not.toBeVisible();
    await expect(page.locator('tbody tr').first()).toContainText('Bench press');
    await expect(page.locator('tbody tr').first()).toContainText('Plank');
    await expect(page.locator('tbody tr').first()).toContainText('Squat');

    await page.getByRole('button', {name: 'New', exact: true}).click();
    dialog = page.getByRole('dialog', {name: 'Workout'});
    const preloadPicker = dialog.locator('.workout-preload');
    await expect(preloadPicker).toBeVisible();
    await preloadPicker.click();
    await page.getByRole('option', {name: 'Mon, 10/08/2026 - Bench press'}).click();
    cards = dialog.locator('.workout-line-card');
    await expect(cards).toHaveCount(3);
    await expect(dialog.getByRole('button', {name: 'Collapse Strength'})).toBeVisible();
    await expect(dialog.locator('.workout-line-card').getByRole('button', {name: /^Expand /})).toHaveCount(3);
    await cards.nth(0).getByRole('button', {name: 'Move exercise 1 down'}).click();
    const createRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await createRequest).postDataJSON().lines.map(line => line.exerciseId)).toEqual([3, 2, 1]);
    await expect(dialog).not.toBeVisible();
});

test('workout editor groups exercises and keeps reordering inside each group', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Marching', description: 'Warm-up.', trackingMode: 'REPS', exerciseType: 'WARM_UP'},
        {id: 2, name: 'Squat', description: 'Strength.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 3, name: 'Plank', description: 'Strength.', trackingMode: 'SECONDS', exerciseType: 'TRAINING'},
        {id: 4, name: 'Hamstring stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'},
        {id: 5, name: 'Treadmill run', description: 'Cardio.', trackingMode: 'CARDIO', cardioMetric: 'SPEED', exerciseType: 'TRAINING'}
    ];
    const lines = exercises.map((exercise, position) => ({exerciseId: exercise.id, exerciseName: exercise.name, exerciseDescription: exercise.description, trackingMode: exercise.trackingMode, exerciseType: exercise.exerciseType, position, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: exercise.trackingMode === 'REPS' ? 10 : null, durationSeconds: exercise.trackingMode === 'SECONDS' ? 30 : null, weight: null}], intervals: []}));
    lines[4].sets = [];
    lines[4].intervals = [{position: 0, durationSeconds: 600, speedKph: 8, distanceKm: 1.3, inclinePercent: 0, resistanceLevel: null}];
    await mockAuthenticatedWorkouts(page, [{id: 7, workoutDate: '2026-08-10', workoutDateFormat: '10/08/2026', note: '', lines}], exercises);
    await openSpaRoute(page, '/workouts');

    await page.locator('tbody tr').getByRole('button', {name: 'Edit workout'}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    const groups = dialog.locator('.workout-exercise-group');
    await expect(groups).toHaveCount(3);
    for (const [groupIndex, label, count] of [[0, 'Warm-up', '1'], [1, 'Training', '3'], [2, 'Stretching', '1']]) {
        const group = groups.nth(groupIndex);
        await expect(group.locator('h3.workout-exercise-group-heading')).toHaveAttribute('aria-label', label);
        await expect(group.locator('.workout-exercise-group-count')).toHaveText(count);
        if (label !== 'Training') await expect(group.getByRole('button', {name: `Expand ${label}`})).toHaveAttribute('aria-expanded', 'false');
    }
    const trainingGroup = groups.nth(1);
    await expect(trainingGroup).toHaveClass(/workout-exercise-group--primary/);
    await expect(trainingGroup.locator('.workout-exercise-group-icon.pi-bolt')).toBeVisible();
    await expect(trainingGroup.locator('.workout-exercise-group-label strong')).toHaveText('Training');
    await expect(trainingGroup.getByRole('button', {name: 'Collapse Training, 3 exercises'})).toHaveAttribute('aria-expanded', 'true');
    const strengthGroup = trainingGroup.locator('h4[aria-label="Strength"]').locator('..');
    await expect(strengthGroup.locator('.workout-exercise-subgroup-count')).toHaveText('2');
    await strengthGroup.getByRole('button', {name: /^Expand Strength,/}).click();
    const trainingCards = strengthGroup.locator('.workout-line-card');
    await expect(trainingCards).toHaveCount(2);
    await expect(trainingCards.first().getByRole('button', {name: 'Move exercise 1 up'})).toBeDisabled();
    await expect(trainingCards.last().getByRole('button', {name: 'Move exercise 2 down'})).toBeDisabled();
    const cardioGroup = trainingGroup.locator('h4[aria-label="Cardio"]').locator('..');
    await expect(cardioGroup.locator('h4.workout-exercise-subgroup-heading')).toHaveAttribute('aria-label', 'Cardio');
    await cardioGroup.getByRole('button', {name: /^Expand Cardio,/}).click();
    await expect(cardioGroup.locator('.workout-line-card')).toHaveCount(1);
    await cardioGroup.locator('.workout-line-card').getByRole('button', {name: /^Expand /}).click();
    await expect(cardioGroup.getByText(/^Intervals/)).toBeVisible();
    await expect(cardioGroup.getByRole('button', {name: 'Move exercise 1 up'})).toBeDisabled();
    await expect(cardioGroup.getByRole('button', {name: 'Move exercise 1 down'})).toBeDisabled();
    const strengthToggle = strengthGroup.locator('.workout-exercise-subgroup-toggle');
    await trainingGroup.getByRole('button', {name: /^Collapse Training,/}).click();
    await expect(trainingGroup.getByRole('button', {name: /^Expand Training,/})).toHaveAttribute('aria-expanded', 'false');
    await expect(strengthToggle).toHaveAttribute('aria-expanded', 'true');
    await trainingGroup.getByRole('button', {name: /^Expand Training,/}).click();
    await expect(strengthToggle).toBeVisible();
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`strength-cardio-groups-${width}.png`), fullPage: true});
    }
    await dialog.getByRole('button', {name: 'Expand Stretching'}).click();
    await dialog.getByRole('button', {name: 'Collapse Stretching'}).click();
    await dialog.getByRole('button', {name: 'Add stretching', exact: true}).click();
    await expect(dialog.getByRole('button', {name: 'Collapse Stretching, 2 stretches', exact: true})).toBeVisible();
});

test('mixed exercise supersets keep rounds synchronized and preserve membership on save', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Bench press', description: 'Press.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Calf stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    const cards = dialog.locator('.workout-line-card');
    await cards.nth(0).locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: 'Bench press', exact: true}).click();
    await cards.nth(0).getByLabel('Repetitions').fill('8');
    await dialog.getByRole('button', {name: 'Add stretching', exact: true}).click();
    await cards.nth(1).locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: 'Calf stretch', exact: true}).click();
    await cards.nth(1).getByLabel('Breaths').fill('4');
    await dialog.getByRole('checkbox', {name: 'Select Exercise 1: Bench press for superset'}).click();
    await dialog.getByRole('checkbox', {name: 'Select Stretching 2: Calf stretch for superset'}).click();
    await dialog.getByRole('button', {name: 'Group as superset'}).click();
    await expect(dialog.locator('.workout-exercise-subgroup--superset')).toHaveCount(1);
    await expect(dialog.locator('.workout-exercise-group--primary .workout-exercise-group-count')).toHaveText('2');
    await expect(dialog.locator('.superset-label')).toHaveCount(2);
    await cards.nth(0).getByRole('button', {name: 'Add set'}).click();
    await expect(cards.nth(0).locator('.segment-card')).toHaveCount(2);
    await expect(cards.nth(1).locator('.segment-card')).toHaveCount(2);
    await cards.nth(1).locator('.segment-card').nth(1).getByRole('button', {name: 'Delete set 2'}).click();
    await expect(cards.nth(0).locator('.segment-card')).toHaveCount(1);
    await expect(cards.nth(1).locator('.segment-card')).toHaveCount(1);
    const trainingParentBeforeSave = dialog.locator('#workout-exercise-group-TRAINING_PARENT').locator('..');
    const supersetGroupBeforeSave = trainingParentBeforeSave.locator('.workout-exercise-subgroup--superset');
    await supersetGroupBeforeSave.getByRole('button', {name: /^Collapse Superset, 2 exercises$/}).click();
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await trainingParentBeforeSave.scrollIntoViewIfNeeded();
        await expect(trainingParentBeforeSave.locator('.workout-exercise-group-heading')).toBeVisible();
        await expect(supersetGroupBeforeSave.locator('.workout-exercise-subgroup-heading')).toBeVisible();
        await page.screenshot({path: testInfo.outputPath(`mixed-training-parent-${width}.png`)});
        await page.screenshot({path: testInfo.outputPath(`mixed-superset-${width}.png`)});
    }
    const createRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    const lines = (await createRequest).postDataJSON().lines;
    expect(lines.map(line => line.exerciseId)).toEqual([1, 2]);
    expect(lines[0].supersetGroupId).toBeTruthy();
    expect(lines[1].supersetGroupId).toBe(lines[0].supersetGroupId);
    await expect(dialog).toBeHidden();
    await page.locator('tbody tr').getByRole('button', {name: 'Edit workout'}).click();
    const reopened = page.getByRole('dialog', {name: 'Workout', exact: true});
    const training = reopened.locator('#workout-exercise-group-TRAINING_PARENT').locator('..');
    await expect(training.locator('.workout-exercise-group-count')).toHaveText('2');
    const superset = training.locator('.workout-exercise-subgroup--superset');
    await expect(superset).toHaveCount(1);
    await expect(superset.getByRole('button', {name: /^Collapse Superset, 2 exercises$/})).toHaveAttribute('aria-expanded', 'true');
    await expect(superset.locator('.workout-line-card')).toHaveCount(2);
    await expect(superset).toContainText('Bench press');
    await expect(superset).toContainText('Calf stretch');
});

test('training parent keeps multiple supersets ahead of cardio after save and reopen', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Bench press', description: 'Press.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Calf stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'},
        {id: 3, name: 'Dumbbell row', description: 'Pull.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 4, name: 'Hamstring stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'},
        {id: 5, name: 'Treadmill run', description: 'Run.', trackingMode: 'CARDIO', exerciseType: 'TRAINING', cardioMetric: 'SPEED'}
    ];
    const lines = [
        {exerciseId: 1, exerciseName: 'Bench press', exerciseDescription: 'Press.', trackingMode: 'REPS', exerciseType: 'TRAINING', supersetGroupId: 'pair-a', position: 0, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 8, weight: 40}], intervals: []},
        {exerciseId: 2, exerciseName: 'Calf stretch', exerciseDescription: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING', stretchingUnit: 'SECONDS', supersetGroupId: 'pair-a', position: 1, calories: null, averageHeartRate: null, sets: [{position: 0, durationSeconds: 30}], intervals: []},
        {exerciseId: 3, exerciseName: 'Dumbbell row', exerciseDescription: 'Pull.', trackingMode: 'REPS', exerciseType: 'TRAINING', supersetGroupId: 'pair-b', position: 2, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 10, weight: 18}], intervals: []},
        {exerciseId: 4, exerciseName: 'Hamstring stretch', exerciseDescription: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING', stretchingUnit: 'SECONDS', supersetGroupId: 'pair-b', position: 3, calories: null, averageHeartRate: null, sets: [{position: 0, durationSeconds: 40}], intervals: []},
        {exerciseId: 5, exerciseName: 'Treadmill run', exerciseDescription: 'Run.', trackingMode: 'CARDIO', exerciseType: 'TRAINING', cardioMetric: 'SPEED', position: 4, calories: null, averageHeartRate: null, sets: [], intervals: [{position: 0, durationSeconds: 600, speedKph: 8, distanceKm: 1.3, inclinePercent: 0, resistanceLevel: null}]}
    ];
    await mockAuthenticatedWorkouts(page, [{id: 7, workoutDate: '2026-08-10', workoutDateFormat: '10/08/2026', note: '', lines}], exercises);
    await openSpaRoute(page, '/workouts');

    async function openEditorAndCheckOrder() {
        await page.locator('tbody tr').getByRole('button', {name: 'Edit workout'}).click();
        const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
        const training = editor.locator('#workout-exercise-group-TRAINING_PARENT').locator('..');
        await expect(training.locator('.workout-exercise-group-count')).toHaveText('5');
        const headings = training.locator('.workout-exercise-subgroup-heading');
        await expect(headings).toHaveCount(3);
        expect(await headings.evaluateAll(elements => elements.map(element => element.getAttribute('aria-label')))).toEqual(['Superset 1', 'Superset 2', 'Cardio']);
        expect(await training.locator('.workout-exercise-subgroup-count').allTextContents()).toEqual(['2', '2', '1']);
        return {editor, training};
    }

    let {editor, training} = await openEditorAndCheckOrder();
    const supersets = training.locator('.workout-exercise-subgroup--superset');
    await expect(supersets).toHaveCount(2);
    for (const width of [390, 1280]) {
        for (const superset of await supersets.all()) await superset.locator('.workout-exercise-subgroup-toggle').click();
        await page.setViewportSize({width, height: 950});
        await training.scrollIntoViewIfNeeded();
        expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`training-supersets-before-cardio-${width}.png`)});
        for (const superset of await supersets.all()) await superset.locator('.workout-exercise-subgroup-toggle').click();
    }
    const updateRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts/7') && request.method() === 'PUT');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    const savedLines = (await updateRequest).postDataJSON().lines;
    expect(savedLines.map(line => line.exerciseId)).toEqual([1, 2, 3, 4, 5]);
    expect(savedLines[0].supersetGroupId).toBe('pair-a');
    expect(savedLines[1].supersetGroupId).toBe('pair-a');
    expect(savedLines[2].supersetGroupId).toBe('pair-b');
    expect(savedLines[3].supersetGroupId).toBe('pair-b');
    await expect(editor).toBeHidden();
    ({editor} = await openEditorAndCheckOrder());
    await expect(editor.locator('.workout-exercise-subgroup--superset')).toHaveCount(2);
});

test('prepared workout draft can be completed in guided mode with separate planned and actual results', async ({page}) => {
    const exercise = {id: 1, name: 'Bench press', description: 'Press with control.', trackingMode: 'REPS', exerciseType: 'TRAINING'};
    await mockAuthenticatedWorkouts(page, [], [exercise]);
    await page.clock.install({time: new Date('2026-09-27T12:00:00')});
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    const line = editor.locator('.workout-line-card').first();
    await line.locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: 'Bench press', exact: true}).click();
    await line.getByLabel('Repetitions').fill('20');
    await line.getByLabel('Weight').fill('20');
    await editor.locator('#workout-duration').fill('15');
    await editor.getByRole('button', {name: 'Start guided workout', exact: true}).click();
    const guided = page.getByRole('dialog', {name: 'Guided workout'});
    await expect(guided.locator('.guided-timer-summary').getByRole('status')).toContainText('Training · Running');
    await page.clock.fastForward(65000);
    await guided.getByLabel('Repetitions', {exact: true}).fill('18');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    await page.clock.fastForward(100);
    const save = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    const payload = (await save).postDataJSON();
    expect(payload.recordingKey).toMatch(/^[0-9a-f-]{36}$/i);
    expect(payload).toMatchObject({startTime: '12:00', warmUpMinutes: 0, trainingMinutes: 2, cardioMinutes: 0, stretchingMinutes: 0, durationMinutes: 2});
    expect(payload.plannedTargets[0].segments[0]).toMatchObject({repetitions: 20, weight: 20});
    expect(payload.lines[0].segments[0]).toMatchObject({repetitions: 18, weight: 20});
});

test('workout collapse defaults and long headers remain usable at mobile and desktop widths', async ({page}, testInfo) => {
    const longName = 'Gentle standing shoulder and upper back mobility with controlled breathing';
    const exercises = [
        {id: 1, name: longName, description: 'Move slowly.', trackingMode: 'REPS', exerciseType: 'WARM_UP'},
        {id: 2, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS', exerciseType: 'TRAINING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    const cards = dialog.locator('.workout-line-card');
    await expect(cards).toHaveCount(1);
    await expect(cards.nth(0).getByRole('button', {name: 'Collapse Exercise 1', exact: true})).toBeVisible();
    await dialog.getByRole('button', {name: 'Add warm-up', exact: true}).click();
    await cards.nth(0).locator('.p-dropdown').click();
    await page.getByRole('option', {name: longName, exact: true}).click();
    await dialog.getByRole('button', {name: 'Add warm-up', exact: true}).click();
    await expect(cards.nth(1).getByRole('button', {name: 'Collapse Warm-up 2', exact: true})).toBeVisible();
    await cards.nth(1).getByRole('button', {name: /^Collapse /}).click();
    await cards.nth(1).getByRole('button', {name: 'Delete exercise 2', exact: true}).click();
    await expect(cards).toHaveCount(2);
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await expect(cards.nth(0).getByRole('button', {name: /^Collapse /})).toBeVisible();
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await cards.nth(0).locator('.workout-line-toggle').scrollIntoViewIfNeeded();
        const title = await cards.nth(0).locator('.workout-line-toggle').boundingBox();
        const actions = await cards.nth(0).locator('.workout-line-actions').boundingBox();
        const horizontallySeparated = title.x + title.width <= actions.x || actions.x + actions.width <= title.x;
        const verticallySeparated = title.y + title.height <= actions.y || actions.y + actions.height <= title.y;
        expect(horizontallySeparated || verticallySeparated).toBe(true);
        const header = await cards.nth(0).locator(':scope > .workout-line-header').boundingBox();
        expect(actions.x).toBeGreaterThanOrEqual(header.x);
        expect(actions.x + actions.width).toBeLessThanOrEqual(header.x + header.width);
        await expectWholeWords(cards.nth(0).locator('.workout-line-toggle strong'));
        await page.screenshot({path: testInfo.outputPath(`workout-${width}.png`), animations: 'disabled'});
    }
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('button', {name: 'New', exact: true}).click();
    await expect(cards).toHaveCount(1);
    await expect(cards.nth(0).getByRole('button', {name: /^Collapse /})).toBeVisible();
});

test('workout preload titles skip warm-ups', async ({page}) => {
    const exercises = [
        {id: 1, name: 'Treadmill', description: 'Easy walk.', trackingMode: 'CARDIO', exerciseType: 'WARM_UP'},
        {id: 2, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS', exerciseType: 'TRAINING'}
    ];
    const workouts = [
        {
            id: 7,
            workoutDate: '2026-08-10',
            workoutDateFormat: '10/08/2026',
            note: '',
            lines: [
                {exerciseId: 1, exerciseName: 'Treadmill', exerciseDescription: 'Easy walk.', trackingMode: 'CARDIO', exerciseType: 'WARM_UP', position: 0, calories: null, averageHeartRate: null, sets: [], intervals: [{position: 0, durationSeconds: 300}]},
                {exerciseId: 2, exerciseName: 'Squat', exerciseDescription: 'Lower-body squat.', trackingMode: 'REPS', exerciseType: 'TRAINING', position: 1, calories: null, averageHeartRate: null, sets: [{position: 0, repetitions: 10, durationSeconds: null, weight: 40}], intervals: []}
            ]
        },
        {
            id: 8,
            workoutDate: '2026-08-09',
            workoutDateFormat: '09/08/2026',
            note: '',
            lines: [
                {exerciseId: 1, exerciseName: 'Treadmill', exerciseDescription: 'Easy walk.', trackingMode: 'CARDIO', exerciseType: 'WARM_UP', position: 0, calories: null, averageHeartRate: null, sets: [], intervals: [{position: 0, durationSeconds: 300}]}
            ]
        }
    ];
    await page.clock.setFixedTime(new Date('2026-08-20T08:00:00Z'));
    await mockAuthenticatedWorkouts(page, workouts, exercises);
    await openSpaRoute(page, '/workouts');

    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    await dialog.locator('.p-field').filter({hasText: 'Preload workout'}).locator('.p-dropdown').click();
    await expect(page.getByRole('option', {name: 'Mon, 10/08/2026 - Squat'})).toBeVisible();
    await expect(page.getByRole('option', {name: 'Sun, 09/08/2026 - Treadmill (0 exercises)', exact: true})).toBeVisible();
    await page.getByRole('option', {name: 'Mon, 10/08/2026 - Squat (1 exercise)', exact: true}).click();
    await expect(dialog.locator('.workout-line-card')).toHaveCount(2);
    await expect(dialog.locator('.workout-line-card').first()).toContainText('Treadmill');
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('button', {name: 'Edit workout', exact: true}).first().click();
    await expect(dialog.locator('.workout-line-card')).toHaveCount(2);
    await expect(dialog.locator('.workout-line-card').first()).toContainText('Treadmill');
});

test('workout preloads show the latest 40 sessions through the selected date with training counts at all widths', async ({page}, testInfo) => {
    const longName = 'Standing single-arm overhead dumbbell press with a controlled lowering phase';
    const exercises = [
        {id: 1, name: 'Treadmill', trackingMode: 'CARDIO', exerciseType: 'WARM_UP'},
        {id: 2, name: longName, trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 3, name: 'Squat', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 4, name: 'Wall calf stretch', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    const lines = exercises.map((exercise, position) => ({
        exerciseId: exercise.id, exerciseName: exercise.name, trackingMode: exercise.trackingMode,
        exerciseType: exercise.exerciseType, position, calories: null, averageHeartRate: null,
        sets: exercise.trackingMode === 'CARDIO' ? [] : [0, 1].map(position => ({position, repetitions: exercise.trackingMode === 'REPS' ? 10 : null, durationSeconds: exercise.trackingMode === 'SECONDS' ? 30 : null, weight: null})),
        intervals: exercise.trackingMode === 'CARDIO' ? [{position: 0, durationSeconds: 300}] : []
    }));
    const workouts = Array.from({length: 43}, (_, index) => {
        const date = new Date(Date.UTC(2026, 7, 20 - index));
        return {id: index + 1, workoutDate: date.toISOString().slice(0, 10), note: '', lines};
    }).reverse();
    await page.clock.setFixedTime(new Date('2026-08-20T08:00:00Z'));
    await mockAuthenticatedWorkouts(page, workouts, exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    const picker = dialog.locator('.workout-preload');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await picker.click();
        const options = page.getByRole('option');
        await expect(options).toHaveCount(40);
        await expect(options.first()).toHaveText(`Thu, 20/08/2026 - ${longName} (2 exercises)`);
        await expect(options.last()).toHaveText(`Sun, 12/07/2026 - ${longName} (2 exercises)`);
        await page.screenshot({path: testInfo.outputPath(`workout-preloads-${width}.png`), animations: 'disabled'});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await picker.press('Escape');
    }
    await picker.click();
    await page.getByRole('option').first().click();
    await expect(picker).toContainText(`Thu, 20/08/2026 - ${longName} (2 exercises)`);
    await expect(dialog.locator('.workout-line-card')).toHaveCount(4);
    await page.setViewportSize({width: 390, height: 950});
    await page.screenshot({path: testInfo.outputPath('workout-preload-selected-390.png'), animations: 'disabled'});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('records page shows current records and paginated progression history', async ({page}) => {
    const exercises = [{id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS'}];
    const bodyRecord = personalRecord({metric: 'BODY_WEIGHT', metricLabel: 'Lowest weight', domain: 'BODY', value: 79, unit: 'KG', subject: {type: 'BODY', id: null, label: 'Body'}});
    const workoutRecord = personalRecord({metric: 'WORKOUT_REPETITIONS', metricLabel: 'Most repetitions', domain: 'WORKOUT', value: 12, unit: 'REPETITIONS', subject: {type: 'EXERCISE', id: 1, label: 'Squat'}, qualifier: {loadKg: 40, label: '40 kg'}});
    const moodRecord = personalRecord({metric: 'MOOD_MAXIMUM', metricLabel: 'Highest mood', domain: 'RECOVERY', value: 5, unit: 'SCORE_OUT_OF_FIVE', subject: {type: 'RECOVERY', id: null, label: 'Mood'}});
    const bmiRecord = personalRecord({metric: 'BODY_BMI_MINIMUM', metricLabel: 'Lowest BMI', domain: 'BODY', value: 24.69, unit: 'KG_PER_SQUARE_METER', subject: {type: 'BODY_CHANGE', id: null, label: 'BMI'}});
    const volumeRecord = personalRecord({metric: 'WORKOUT_STRENGTH_VOLUME_MAXIMUM', metricLabel: 'Highest strength volume', domain: 'WORKOUT', value: 1200, unit: 'KG_REPETITIONS', subject: {type: 'WORKOUT_TOTAL', id: null, label: 'Workout session'}});
    const habitRecord = personalRecord({metric: 'HABIT_COMPLETION_TOTAL_MAXIMUM', metricLabel: 'Most habit completions', domain: 'BEHAVIOR', value: 12, unit: 'COMPLETIONS', recordDate: null, subject: {type: 'HABIT', id: 3, label: 'Read'}, source: {type: 'HABIT_BASELINE', id: 4, linePosition: null, segmentPosition: null}});
    const fastingRecord = personalRecord({metric: 'FASTING_DURATION_MAXIMUM', metricLabel: 'Longest fasting period', domain: 'NUTRITION', value: 57600, unit: 'SECONDS', subject: {type: 'NUTRITION', id: null, label: 'Fasting'}, source: {type: 'FASTING_PERIOD', id: 8, linePosition: null, segmentPosition: null}});
    const historyEvents = [
        {...workoutRecord, kind: 'TIED', previousValue: 12, currentRecord: true, source: {type: 'WORKOUT', id: 7, linePosition: 0, segmentPosition: 0}},
        {...bodyRecord, kind: 'IMPROVED', previousValue: 80, currentRecord: true, source: {type: 'WEIGHT', id: 2, linePosition: null, segmentPosition: null}},
        {...habitRecord, kind: 'FIRST', previousValue: null, currentRecord: true},
        {...fastingRecord, kind: 'FIRST', previousValue: null, currentRecord: true}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises, {currentRecords: [bodyRecord, workoutRecord, moodRecord, bmiRecord, volumeRecord, habitRecord, fastingRecord], historyEvents});

    await openSpaRoute(page, '/records');
    const currentPanel = page.locator('.p-tabview-panel:visible');
    await expect(currentPanel.getByText('Lowest weight', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Most repetitions', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Highest mood', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('5/5', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('24.69 kg/m²', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('1200 kg·reps', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Most habit completions', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Longest fasting period', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('16.0 h', {exact: true})).toBeVisible();
    await expect(currentPanel.getByText('Legacy baseline', {exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'History'}).click();
    const historyPanel = page.locator('.p-tabview-panel:visible');
    await expect(historyPanel.getByText('Tied PR', {exact: true})).toBeVisible();
    await expect(historyPanel.getByText('79 kg', {exact: true})).toBeVisible();
    await expect(historyPanel.getByText('Longest fasting period', {exact: true})).toBeVisible();
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
        ]},
        {key: 'FASTING_DURATION', label: 'Fasting duration', domain: 'NUTRITION', unit: 'SECONDS', precision: 0, defaultMode: 'MAXIMUM', mode: 'MAXIMUM', directions: [
            {direction: 'MINIMUM', metric: 'FASTING_DURATION_MINIMUM', label: 'Shortest fasting period'},
            {direction: 'MAXIMUM', metric: 'FASTING_DURATION_MAXIMUM', label: 'Longest fasting period'}
        ]}
    ];
    await mockAuthenticatedWorkouts(page, [], [], {catalog});
    await openSpaRoute(page, '/records');
    await page.getByRole('tab', {name: 'Settings'}).click();

    const weightSetting = page.locator('.record-setting-row').filter({hasText: 'Body weight'});
    await expect(page.locator('.record-setting-row').filter({hasText: 'Fasting duration'})).toContainText('default: Maximum');
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

test('removed habits stay absent from navigation and routine requests', async ({page}) => {
    await page.setViewportSize({width: 1280, height: 900});
    await mockAuthenticatedRoutines(page, []);
    const habitRequests = [];
    page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/habits')) habitRequests.push(request.url()); });
    await openSpaRoute(page, '/routines');
    await expect(page.getByRole('button', {name: 'New', exact: true})).toBeVisible();
    await page.getByRole('menuitem', {name: 'Plan', exact: true}).click();
    await expect(page.getByRole('menuitem', {name: 'Routines', exact: true})).toBeVisible();
    await expect(page.locator('a[href="/habits"]')).toHaveCount(0);
    expect(habitRequests).toEqual([]);
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
    await editDialog.getByRole('button', {name: /^Expand Strength,/}).click();
    await editDialog.locator('.workout-line-card').getByRole('button', {name: /^Expand /}).click();
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

test('workout diary loads without requesting personal-record history', async ({page}) => {
    const exercises = [{id: 1, name: 'Deadlift', description: 'Hip hinge.', trackingMode: 'REPS', exerciseType: 'TRAINING'}];
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
    await expect(page.locator('.p-tabview-panel:visible tbody tr').filter({hasText: 'Deadlift'})).toBeVisible();
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
        personalRecord({metric: 'CARDIO_DISTANCE', metricLabel: 'Longest distance', domain: 'WORKOUT', value: 163, unit: 'KM', subject: {type: 'EXERCISE_TOTAL', id: 1, label: 'Walking session'}}),
        personalRecord({metric: 'CARDIO_INCLINE', metricLabel: 'Highest incline', domain: 'WORKOUT', value: 12, unit: 'PERCENT', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'CARDIO_RESISTANCE', metricLabel: 'Highest resistance', domain: 'WORKOUT', value: 8, unit: 'LEVEL', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'WORKOUT_CALORIES', metricLabel: 'Highest workout calories', domain: 'WORKOUT', value: 355, unit: 'KCAL', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}}),
        personalRecord({metric: 'WORKOUT_AVERAGE_HEART_RATE', metricLabel: 'Highest workout heart rate', domain: 'WORKOUT', value: 160, unit: 'BPM', subject: {type: 'EXERCISE', id: 1, label: 'Walking'}})
    ];
    await mockAuthenticatedWorkouts(page, [workout], exercises, {currentRecords: records});
    await openSpaRoute(page, '/workouts');

    await page.getByRole('article').filter({hasText: 'Walking'}).getByRole('button', {name: 'Edit workout'}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    await dialog.getByRole('button', {name: /^Expand Cardio,/}).click();
    await dialog.locator('.workout-line-card').getByRole('button', {name: /^Expand /}).click();
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

test('cardio intervals start consecutively and show total duration', async ({page}, testInfo) => {
    const exercises = [{id: 1, name: 'Walking', description: 'Steady-state walking cardio.', trackingMode: 'CARDIO'}];
    const workout = {
        id: 7,
        workoutDate: '2026-08-10',
        workoutDateFormat: '10/08/2026',
        note: null,
        lines: [{exerciseId: 1, exerciseName: 'Walking', exerciseDescription: 'Steady-state walking cardio.', trackingMode: 'CARDIO', position: 0, calories: null, averageHeartRate: null, sets: [], intervals: [
            {position: 0, durationSeconds: 180, speedKph: null, distanceKm: null, inclinePercent: null, resistanceLevel: null},
            {position: 1, durationSeconds: 300, speedKph: null, distanceKm: null, inclinePercent: null, resistanceLevel: null},
            {position: 2, durationSeconds: 120, speedKph: null, distanceKm: null, inclinePercent: null, resistanceLevel: null}
        ]}]
    };
    await mockAuthenticatedWorkouts(page, [workout], exercises);
    await openSpaRoute(page, '/workouts');

    await page.getByRole('article').filter({hasText: 'Walking'}).getByRole('button', {name: 'Edit workout'}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    await dialog.getByRole('button', {name: /^Expand Cardio,/}).click();
    await dialog.locator('.workout-line-card').getByRole('button', {name: /^Expand /}).click();
    await expect(dialog.getByText('Intervals · Total 10:00')).toBeVisible();
    await expect(dialog.getByText('Interval 1 · 00:00')).toBeVisible();
    await expect(dialog.getByText('Interval 2 · 03:00')).toBeVisible();
    await expect(dialog.getByText('Interval 3 · 08:00')).toBeVisible();
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await expectWholeWords(dialog.locator('.workout-line-toggle strong'));
        await dialog.screenshot({animations: 'disabled', path: testInfo.outputPath(`cardio-intervals-${width}.png`)});
    }

    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        for (const label of ['Intervals · Total 10:00', 'Interval 1 · 00:00', 'Interval 2 · 03:00', 'Interval 3 · 08:00']) {
            await dialog.getByText(label).scrollIntoViewIfNeeded();
            await dialog.screenshot({animations: 'disabled', path: testInfo.outputPath(`cardio-${label.startsWith('Intervals') ? 'total' : label.split(' ')[1]}-${width}.png`)});
        }
    }
    const firstIntervalMinutes = dialog.locator('.segment-card').first().getByText('Minutes').locator('..').locator('input');
    await firstIntervalMinutes.fill('6');
    await firstIntervalMinutes.press('Tab');
    await expect(dialog.getByText('Intervals · Total 13:00')).toBeVisible();
    await expect(dialog.getByText('Interval 2 · 06:00')).toBeVisible();

    await dialog.getByRole('button', {name: 'Add interval'}).click();
    await expect(dialog.getByText('Intervals · Total 15:00')).toBeVisible();
    await expect(dialog.getByText('Interval 4 · 13:00')).toBeVisible();
    await dialog.locator('.segment-card').last().locator('button').click();
    await expect(dialog.getByText('Intervals · Total 13:00')).toBeVisible();

    await page.setViewportSize({width: 1440, height: 900});
    await expect(dialog.getByText('Interval 3 · 11:00')).toBeVisible();

    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts/7') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await saving).postDataJSON()).toMatchObject({durationMinutes: 13, warmUpMinutes: 0, trainingMinutes: 0, cardioMinutes: 13, stretchingMinutes: 0});
});

test('elliptical intervals use cadence in RPM', async ({page}) => {
    const exercises = [{id: 1, name: 'Elliptical', description: 'Cardio on an elliptical trainer.', trackingMode: 'CARDIO', cardioMetric: 'CADENCE_RPM'}];
    const records = [personalRecord({metric: 'CARDIO_CADENCE', metricLabel: 'Highest cadence', domain: 'WORKOUT', value: 88, unit: 'RPM', subject: {type: 'EXERCISE', id: 1, label: 'Elliptical'}})];
    await mockAuthenticatedWorkouts(page, [], exercises, {currentRecords: records});
    await openSpaRoute(page, '/workouts');

    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout'});
    await dialog.getByText('Select exercise').click();
    await page.getByRole('option', {name: 'Elliptical'}).click();
    await expect(dialog.getByText('Cadence (RPM)')).toBeVisible();
    await expect(dialog.getByText('Speed (km/h)')).toHaveCount(0);
    await expect(dialog.getByText('Cadence (RPM)').locator('..').locator('.field-record-context')).toHaveText('Highest cadence: 88 RPM');
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
    await dialog.getByRole('button', {name: /^Expand Strength,/}).click();
    await dialog.locator('.workout-line-card').getByRole('button', {name: /^Expand /}).click();
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

    await expect(page).toHaveURL(`/records?tab=history&eventKey=${eventKey}`);
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

test('Home shows blood pressure and lipid records in their status panels', async ({page}) => {
    const pressureRecords = [
        ['BLOOD_PRESSURE_SYSTOLIC_MINIMUM', 'Lowest systolic pressure', 110],
        ['BLOOD_PRESSURE_SYSTOLIC_MAXIMUM', 'Highest systolic pressure', 140],
        ['BLOOD_PRESSURE_DIASTOLIC_MINIMUM', 'Lowest diastolic pressure', 70],
        ['BLOOD_PRESSURE_DIASTOLIC_MAXIMUM', 'Highest diastolic pressure', 90]
    ].map(([metric, metricLabel, value]) => personalRecord({metric, metricLabel, value, domain: 'VITALS', unit: 'MM_HG', subject: {type: 'VITALS', id: null, label: 'Blood pressure'}}));
    const lipidRecords = [
        ['LIPID_TOTAL_CHOLESTEROL_MINIMUM', 'Lowest total cholesterol', 180],
        ['LIPID_HDL_MAXIMUM', 'Highest HDL', 60],
        ['LIPID_LDL_MINIMUM', 'Lowest LDL', 100],
        ['LIPID_TRIGLYCERIDES_MINIMUM', 'Lowest triglycerides', 90]
    ].map(([metric, metricLabel, value]) => personalRecord({metric, metricLabel, value, domain: 'VITALS', unit: 'MG_PER_DL', subject: {type: 'VITALS', id: null, label: 'Lipids'}}));
    const bodyRecord = personalRecord({metric: 'BODY_WEIGHT', metricLabel: 'Lowest weight', domain: 'BODY', value: 79, unit: 'KG', subject: {type: 'BODY', id: null, label: 'Body'}});
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {
        currentRecords: [bodyRecord, ...pressureRecords, ...lipidRecords],
        initialLipidPanels: [{id: 1, date: '2026-08-10', totalCholesterol: 180, hdlCholesterol: 60, ldlCholesterol: 100, triglycerides: 90}]
    });
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Body'}).click();
    const bodyTab = page.locator('.home-panels-tabs .p-tabview-panel:visible');
    const weightPanel = bodyTab.locator('.p-panel').filter({has: page.getByText('Last Weight', {exact: true})});
    const pressurePanel = bodyTab.locator('.p-panel').filter({has: page.getByText('Last Pressure', {exact: true})});
    const lipidPanel = bodyTab.locator('.p-panel').filter({has: page.getByText('Latest Lipid Panel', {exact: true})});

    for (const [panel, records, unit] of [[pressurePanel, pressureRecords, 'mm Hg'], [lipidPanel, lipidRecords, 'mg/dL']]) {
        const table = panel.getByRole('table', {name: 'All-time records'});
        await expect(table.getByRole('columnheader')).toHaveText(['Record', 'Value', 'Date']);
        await expect(table.locator('tbody tr')).toHaveCount(records.length);
        for (const record of records) {
            await expect(table.getByRole('row').filter({hasText: record.metricLabel}).getByRole('cell')).toHaveText([`${record.value} ${unit}`, '2026-08-10']);
        }
        await expect(panel.getByText('Lowest weight', {exact: true})).toHaveCount(0);
    }
    await expect(weightPanel.getByRole('table').locator('tbody tr')).toHaveCount(1);
    for (const width of [393, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await bodyTab.screenshot({path: test.info().outputPath(`vital-records-${width}.png`)});
    }
});

test('Home hides blood pressure and lipid record tables when no records are returned', async ({page}) => {
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {currentRecords: []});
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Body'}).click();
    const bodyTab = page.locator('.home-panels-tabs .p-tabview-panel:visible');
    await expect(bodyTab.getByText('Last Pressure', {exact: true})).toBeVisible();
    await expect(bodyTab.getByText('Latest Lipid Panel', {exact: true})).toBeVisible();
    await expect(bodyTab.getByRole('table', {name: 'All-time records'})).toHaveCount(0);
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
    await expect(panel.getByText('0.1 h', {exact: true})).toBeVisible();
    await page.setViewportSize({width: 393, height: 851});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('Home Nutrition tab does not show optional nutrition personal records', async ({page}) => {
    const nutritionRecord = personalRecord({metric: 'DAILY_CALORIES_MAXIMUM', metricLabel: 'Highest daily calories', domain: 'NUTRITION', value: 6381, unit: 'KCAL', subject: {type: 'NUTRITION_DAY', id: null, label: 'Daily nutrition'}});
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {currentRecords: [nutritionRecord]});
    await openSpaRoute(page, '/');

    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Nutrition'}).click();

    const panel = page.locator('.home-panels-tabs .p-tabview-panel:visible');
    await expect(panel.getByText('All-time Records')).toHaveCount(0);
});

test('Home Nutrition tab merges overlapping fasting periods for the average and record', async ({page}) => {
    const initialFastingPeriods = [
        {id: 1, startTime: '2026-08-09T20:00:00+02:00', endTime: '2026-08-10T12:00:00+02:00', source: 'AUTOMATIC'},
        {id: 2, startTime: '2026-08-10T10:00:00+02:00', endTime: '2026-08-10T16:00:00+02:00', source: 'MANUAL'},
        {id: 3, startTime: '2026-08-11T20:00:00+02:00', endTime: '2026-08-12T06:00:00+02:00', source: 'MANUAL'}
    ];
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialFastingPeriods});
    await openSpaRoute(page, '/');

    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Nutrition'}).click();

    const panel = page.locator('.home-panels-tabs .p-tabview-panel:visible');
    await expect(panel).toContainText('Average fasting period:');
    await expect(panel).toContainText('15h 0m');
    await expect(panel).toContainText('Longest fasting period:');
    await expect(panel).toContainText('20h 0m');
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
    const session = page.getByRole('region', {name: 'Selected day workouts'}).locator('.workout-session').first();
    await session.locator('.workout-session-details summary').click();
    await expect(session).toContainText('Strength session');
    expect(requestedPaths).toContain('/api/workouts/dashboard');
    expect(requestedPaths).not.toContain('/api/workouts');
    await page.setViewportSize({width: 1440, height: 900});
    await expect(session).toContainText('Strength session');
});

test('Home rates the selected workout with Coach', async ({page, context}, testInfo) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route('https://chatgpt.test/**', route => route.fulfill({
        contentType: 'text/html',
        body: '<title>Weight Control Coach</title>'
    }));
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {
        initialWorkouts: [dashboardWorkout(dashboard.anchorDate)]
    });

    await openSpaRoute(page, '/');
    const workoutTab = page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Workout'});
    await workoutTab.click();
    const coachPagePromise = context.waitForEvent('page');
    const rateDay = page.getByRole('button', {name: 'Rate day', exact: true});
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectChatGptIcon(rateDay);
        await page.locator('.daily-workout-assessment').first().screenshot({path: testInfo.outputPath(`chatgpt-home-workout-${width}.png`)});
    }
    await rateDay.click();
    const coachPage = await coachPagePromise;
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe(`Assess all my workout sessions on ${dashboard.anchorDate} together as one training day against my active coaching plan.`);
    await coachPage.close();

    await page.setViewportSize({width: 1440, height: 900});
    await expect(page.getByRole('button', {name: 'Rate day', exact: true})).toBeVisible();
});

test('Home does not show a rating shortcut without a selected-date workout', async ({page}) => {
    await mockAuthenticatedDashboard(page, dashboard.anchorDate);

    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Workout'}).click();
    await expect(page.getByRole('button', {name: 'Rate day', exact: true})).toHaveCount(0);
});

test('dashboard shows sleep durations in hours', async ({page}) => {
    const [sleep] = sleepHistory(dashboard.anchorDate);
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {
        initialSleeps: [{...sleep, deepSleepDuration: 30 * 60}]
    });
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel.getByText('0.5 h / 1.5 h / 4.0 h')).toBeVisible();
});

test('dashboard marks both nightly sleep minimums for the selected date', async ({page}, testInfo) => {
    const [sleep] = sleepHistory(dashboard.anchorDate, 1);
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {
        initialSleeps: [{...sleep, bedtimeStart: '2026-08-11T23:00:00+02:00', bedtimeEnd: '2026-08-12T06:00:00+02:00', totalSleepDuration: 6 * 60 * 60}]
    });
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel).toContainText('Asleep (6 h minimum):');
    await expect(panel).toContainText('6.0 h · Met');
    await expect(panel).toContainText('In bed (7 h minimum):');
    await expect(panel).toContainText('7.0 h · Met');
    await expect(panel).toContainText('Both nightly goals:');
    await expect(panel.getByText('Not enough data (1/30)', {exact: true})).toBeVisible();
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await panel.screenshot({path: testInfo.outputPath(`dashboard-sleep-goals-${width}.png`)});
    }
});

test('sleep history assesses raw durations against separate nightly minimums', async ({page}, testInfo) => {
    const selected = sleepHistory(dashboard.anchorDate, 1)[0];
    const below = sleepHistory('2026-08-11', 1)[0];
    const longNight = sleepHistory('2026-08-10', 1)[0];
    const shortSleep = sleepHistory('2026-08-09', 1)[0];
    const shortBedtime = sleepHistory('2026-08-08', 1)[0];
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: [
        {...selected, bedtimeStart: '2026-08-11T23:00:00+02:00', bedtimeEnd: '2026-08-12T06:00:00+02:00', totalSleepDuration: 6 * 60 * 60},
        {...below, bedtimeStart: '2026-08-10T23:00:00+02:00', bedtimeEnd: '2026-08-11T05:59:59+02:00', totalSleepDuration: 6 * 60 * 60 - 1},
        {...longNight, bedtimeStart: '2026-08-09T21:00:00+02:00', bedtimeEnd: '2026-08-10T07:00:00+02:00', totalSleepDuration: 7 * 60 * 60},
        {...shortSleep, bedtimeStart: '2026-08-08T23:00:00+02:00', bedtimeEnd: '2026-08-09T06:00:00+02:00', totalSleepDuration: 6 * 60 * 60 - 1},
        {...shortBedtime, bedtimeStart: '2026-08-07T23:00:00+02:00', bedtimeEnd: '2026-08-08T05:59:59+02:00', totalSleepDuration: 6 * 60 * 60},
        {...sleepHistory('2026-08-07', 1)[0], bedtimeStart: '2026-08-07T00:00:00+02:00', bedtimeEnd: '2026-08-07T05:55:00+02:00', totalSleepDuration: 6 * 60 * 60 - 42 * 60},
        {...sleepHistory('2026-08-06', 1)[0], bedtimeStart: '2026-08-06T00:00:00+02:00', bedtimeEnd: '2026-08-06T06:00:00+02:00', totalSleepDuration: 6 * 60 * 60 - 65 * 60},
        {...sleepHistory('2026-08-05', 1)[0], bedtimeStart: '2026-08-04T23:00:00+02:00', bedtimeEnd: '2026-08-05T06:00:00+02:00', totalSleepDuration: null},
        {...sleepHistory('2026-08-04', 1)[0], bedtimeStart: null, totalSleepDuration: 6 * 60 * 60},
        {...sleepHistory('2026-08-03', 1)[0], bedtimeEnd: null, totalSleepDuration: null},
        {...sleepHistory('2026-08-02', 1)[0], bedtimeStart: '2026-08-02T00:00:00+02:00', bedtimeEnd: '2026-08-02T05:15:00+02:00', totalSleepDuration: 6 * 60 * 60 - 105 * 60}
    ]});
    await openSpaRoute(page, '/sleep');

    const table = page.getByRole('table');
    await expect(table.getByRole('columnheader', {name: 'Asleep (6 h minimum)'})).toBeVisible();
    await expect(table.getByRole('columnheader', {name: 'In bed (7 h minimum)'})).toBeVisible();
    const exactThreshold = table.locator('tbody tr').filter({hasText: '12/08/2026'});
    await expect(exactThreshold.locator('td').nth(1)).toHaveText('6.0 h');
    await expect(exactThreshold.locator('td').nth(2)).toHaveText('7.0 h');
    await expect(exactThreshold.locator('td').nth(3)).toHaveText('Yes');
    await expect(exactThreshold.getByRole('note')).toHaveCount(0);
    const belowThreshold = table.locator('tbody tr').filter({hasText: '11/08/2026'});
    await expect(belowThreshold.locator('td').nth(1)).toHaveText('6.0 h · -1');
    await expect(belowThreshold.locator('td').nth(1).getByRole('note', {name: '1 minute below the total sleep goal'})).toHaveText('-1');
    await expect(belowThreshold.locator('td').nth(1)).toHaveAccessibleName('6.0 h 1 minute below the total sleep goal');
    await expect(belowThreshold.locator('td').nth(2)).toHaveText('7.0 h · -1');
    await expect(belowThreshold.locator('td').nth(2).getByRole('note', {name: '1 minute below the in-bed goal'})).toHaveText('-1');
    await expect(belowThreshold.locator('td').nth(2)).toHaveAccessibleName('7.0 h 1 minute below the in-bed goal');
    await expect(belowThreshold.locator('td').nth(3)).toHaveText('No');
    const moreThanNineHours = table.locator('tbody tr').filter({hasText: '10/08/2026'});
    await expect(moreThanNineHours.locator('td').nth(1)).toHaveText('7.0 h');
    await expect(moreThanNineHours.locator('td').nth(2)).toHaveText('10.0 h');
    await expect(moreThanNineHours.locator('td').nth(1).locator('.sleep-goal-met')).toHaveCount(1);
    await expect(moreThanNineHours.locator('td').nth(2).locator('.sleep-goal-met')).toHaveCount(1);
    await expect(moreThanNineHours.locator('td').nth(3)).toHaveText('Yes');
    await expect(moreThanNineHours.getByRole('note')).toHaveCount(0);
    const asleepMinimumMissed = table.locator('tbody tr').filter({hasText: '09/08/2026'});
    await expect(asleepMinimumMissed.locator('td').nth(2)).toHaveText('7.0 h');
    await expect(asleepMinimumMissed.locator('td').nth(1)).toHaveText('6.0 h · -1');
    await expect(asleepMinimumMissed.locator('td').nth(3)).toHaveText('No');
    await expect(asleepMinimumMissed.locator('td').nth(1).locator('.sleep-goal-missed')).toHaveCount(1);
    await expect(asleepMinimumMissed.locator('td').nth(2).locator('.sleep-goal-met')).toHaveCount(1);
    const inBedMinimumMissed = table.locator('tbody tr').filter({hasText: '08/08/2026'});
    await expect(inBedMinimumMissed.locator('td').nth(2)).toHaveText('7.0 h · -1');
    await expect(inBedMinimumMissed.locator('td').nth(1)).toHaveText('6.0 h');
    await expect(inBedMinimumMissed.locator('td').nth(3)).toHaveText('No');
    await expect(inBedMinimumMissed.locator('td').nth(1).locator('.sleep-goal-met')).toHaveCount(1);
    await expect(inBedMinimumMissed.locator('td').nth(2).locator('.sleep-goal-missed')).toHaveCount(1);

    const minutesMissed = table.locator('tbody tr').filter({hasText: '07/08/2026'});
    await expect(minutesMissed.locator('td').nth(1)).toHaveText('5.3 h · -42');
    await expect(minutesMissed.locator('td').nth(1).getByRole('note', {name: '42 minutes below the total sleep goal'})).toHaveText('-42');
    await expect(minutesMissed.locator('td').nth(2).getByRole('note', {name: '65 minutes below the in-bed goal'})).toHaveText('-65');
    await expect(minutesMissed.locator('td').nth(2)).toHaveText('5.9 h · -65');
    await expect(minutesMissed.locator('td').nth(3)).toHaveText('No');
    const hoursMissed = table.locator('tbody tr').filter({hasText: '06/08/2026'});
    await expect(hoursMissed.locator('td').nth(1)).toHaveText('4.9 h · -65');
    await expect(hoursMissed.locator('td').nth(2)).toHaveText('6.0 h · -60');
    const missingAsleep = table.locator('tbody tr').filter({hasText: '05/08/2026'});
    await expect(missingAsleep.locator('td').nth(1)).toHaveText('Not recorded');
    await expect(missingAsleep.locator('td').nth(1).locator('.sleep-goal-met, .sleep-goal-missed')).toHaveCount(0);
    await expect(missingAsleep.locator('td').nth(2)).toHaveText('7.0 h');
    await expect(missingAsleep.locator('td').nth(3)).toHaveText('Not recorded');
    const missingStart = table.locator('tbody tr').filter({hasText: '04/08/2026'});
    await expect(missingStart.locator('td').nth(1)).toHaveText('6.0 h');
    await expect(missingStart.locator('td').nth(2)).toHaveText('Not recorded');
    await expect(missingStart.locator('td').nth(2).locator('.sleep-goal-met, .sleep-goal-missed')).toHaveCount(0);
    await expect(missingStart.locator('td').nth(3)).toHaveText('Not recorded');
    const missingEnd = table.locator('tbody tr').filter({hasText: '03/08/2026'});
    await expect(missingEnd.locator('td').nth(1)).toHaveText('Not recorded');
    await expect(missingEnd.locator('td').nth(2)).toHaveText('Not recorded');
    await expect(missingEnd.locator('td').nth(3)).toHaveText('Not recorded');
    await expect(belowThreshold.locator('.sleep-goal-missed')).toHaveCount(3);
    await expect(exactThreshold.locator('.sleep-goal-met')).toHaveCount(3);
    await expect(table).not.toContainText(/\b(?:short|mins?|Met|below minimum)\b/i);

    for (const width of [376, 393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await expect(page.locator('.p-datatable-wrapper')).toHaveCSS('overflow-x', 'auto');
        await table.screenshot({path: testInfo.outputPath(`sleep-history-goals-${width}.png`)});
        if ([376, 1280].includes(width)) {
            await page.screenshot({path: testInfo.outputPath(`sleep-history-goals-full-${width}.png`), fullPage: true});
        }
    }
    await expect(page.getByText('1 to 10 of 11', {exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Next Page', exact: true}).click();
    await expect(page.getByText('11 to 11 of 11', {exact: true})).toBeVisible();
    const oneHourFortyFiveMissed = table.locator('tbody tr').filter({hasText: '02/08/2026'});
    await expect(oneHourFortyFiveMissed.locator('td').nth(1)).toHaveText('4.3 h · -105');
    await expect(oneHourFortyFiveMissed.locator('td').nth(1).getByRole('note', {name: '105 minutes below the total sleep goal'})).toHaveText('-105');
    await expect(oneHourFortyFiveMissed.locator('td').nth(2)).toHaveText('5.3 h · -105');
    await expect(oneHourFortyFiveMissed.locator('td').nth(2).getByRole('note', {name: '105 minutes below the in-bed goal'})).toHaveText('-105');
    await expect(oneHourFortyFiveMissed.getByRole('button', {name: 'Edit', exact: true})).toBeVisible();
    await expect(oneHourFortyFiveMissed.getByRole('button', {name: 'Delete', exact: true})).toBeVisible();
    await table.screenshot({path: testInfo.outputPath('sleep-history-goals-page-2-1280.png')});
});

test('dashboard keeps generic below minimum wording for nightly sleep deficits', async ({page}) => {
    const [sleep] = sleepHistory(dashboard.anchorDate, 1);
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: [{...sleep,
        bedtimeStart: '2026-08-11T23:00:00+02:00', bedtimeEnd: '2026-08-12T05:59:59+02:00', totalSleepDuration: 6 * 60 * 60 - 1
    }]});
    await openSpaRoute(page, '/');
    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel).toContainText('6.0 h · Below minimum');
    await expect(panel).toContainText('7.0 h · Below minimum');
    await expect(panel).not.toContainText('min below minimum');
});

test('dashboard reports unrecorded nightly sleep goals as unknown', async ({page}) => {
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: []});
    await openSpaRoute(page, '/');
    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel).toContainText('Asleep (6 h minimum):');
    await expect(panel).toContainText('In bed (7 h minimum):');
    const sleepGrid = panel.locator('.p-grid');
    const asleepValue = sleepGrid.locator('.p-col-5').filter({hasText: 'Asleep (6 h minimum):'}).locator('xpath=following-sibling::div[1]');
    const inBedValue = sleepGrid.locator('.p-col-5').filter({hasText: 'In bed (7 h minimum):'}).locator('xpath=following-sibling::div[1]');
    const bothGoalsValue = sleepGrid.locator('.p-col-5').filter({hasText: 'Both nightly goals:'}).locator('xpath=following-sibling::div[1]');
    await expect(asleepValue).toContainText('Not recorded');
    await expect(asleepValue.locator('.sleep-goal-result')).toContainText('Not recorded');
    await expect(inBedValue).toContainText('Not recorded');
    await expect(inBedValue.locator('.sleep-goal-result')).toContainText('Not recorded');
    await expect(bothGoalsValue).toHaveText('Not recorded');
});

test('dashboard shows the overall improvement label and weighted explanation', async ({page}, testInfo) => {
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {
        dashboardResponse: {
            ...dashboard,
            dailyStatus: {...dashboardDailyStatus(dashboard.anchorDate), routinesStatus: 68},
            lastWeekDailyStatus: {...dashboardDailyStatus(dashboard.anchorDate), routinesStatus: 74.8}
        },
        overallProgressResponse: {
            status: 'SLIGHTLY_IMPROVING', score: 0.45,
            currentStart: '2026-08-01', currentEnd: '2026-08-30',
            previousStart: '2026-07-02', previousEnd: '2026-07-31',
            contributions: [
                {metric: 'Routine completion', included: true, weight: 30, change: 5, normalizedContribution: 1, explanation: 'Average routine completion changed by 5 points.'},
                {metric: 'Body fat', included: false, weight: 20, change: null, normalizedContribution: null, explanation: 'Not enough observations in both comparison periods.'}
            ]
        }
    });
    await openSpaRoute(page, '/');

    const overview = page.getByRole('region', {name: 'Progress overview', exact: true});
    const performance = overview.getByRole('region', {name: 'Performance Score', exact: true});
    const progress = overview.getByRole('region', {name: 'Overall progress', exact: true});
    const disclosure = overview.locator('details');
    const summary = overview.locator('summary');
    await expect(overview).toHaveCount(1);
    await expect(performance.locator('.performance-score-value')).toHaveText('68/100');
    await expect(performance.locator('.performance-score-trend')).toHaveText('↓ 6.8');
    await expect(performance.locator('.performance-score-trend')).toHaveClass(/bad/);
    await expect(progress.getByText('Slightly improving')).toBeVisible();
    await expect(progress.locator('.overall-progress-score')).toHaveText('0.45');
    await expect(progress.locator('.overall-progress-score')).toHaveAttribute('aria-label', 'Weighted progress score 0.45 on a scale from −2 to +2');
    await expect(progress.locator('.overall-progress-status i')).toHaveClass(/pi-arrow-up/);
    await expect(overview.locator('summary')).toHaveCount(1);
    await expect(summary).toHaveAccessibleName('How this was calculated');
    await expect(summary).toHaveText('');
    await expect(summary.locator('.pi-question-circle')).toBeVisible();
    await expect(overview.locator('.progress-metric-context')).toHaveCount(0);
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expect(disclosure).not.toHaveAttribute('open', '');
        const performanceBounds = await performance.boundingBox();
        const progressBounds = await progress.boundingBox();
        if (width <= 640) {
            expect(progressBounds.y).toBeGreaterThanOrEqual(performanceBounds.y + performanceBounds.height);
            expect(progressBounds.x).toBeCloseTo(performanceBounds.x, 0);
        } else {
            expect(progressBounds.y).toBeCloseTo(performanceBounds.y, 0);
            expect(progressBounds.width).toBeCloseTo(performanceBounds.width, 0);
            expect(progressBounds.x).toBeGreaterThan(performanceBounds.x);
        }
        for (const metric of [performance, progress]) {
            const labelBounds = await metric.locator('h3').boundingBox();
            const valueBounds = await metric.locator('h3 + div').boundingBox();
            expect(valueBounds.x).toBeGreaterThanOrEqual(labelBounds.x + labelBounds.width);
        }
        const summaryBounds = await summary.boundingBox();
        expect(summaryBounds.width).toBeGreaterThanOrEqual(44);
        expect(summaryBounds.height).toBeGreaterThanOrEqual(44);
        const titleBounds = await overview.locator('h2').boundingBox();
        expect(summaryBounds.y).toBeLessThan(titleBounds.y + titleBounds.height);
        await expect(overview.getByText(/rounded to a score from 0 to 100/)).not.toBeVisible();
        const bounds = await overview.boundingBox();
        expect(bounds.x).toBeGreaterThanOrEqual(0);
        expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
        await expect.poll(() => overview.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        await overview.screenshot({path: testInfo.outputPath(`progress-overview-${width}-closed.png`)});
        await summary.focus();
        await expect(summary).toBeFocused();
        await page.keyboard.press('Enter');
        await expect(disclosure).toHaveAttribute('open', '');
        await expect(overview.getByText(/rounded to a score from 0 to 100/)).toBeVisible();
        await expect(disclosure).toContainText('clamps each contribution from −2 to +2');
        await expect(overview.getByText(/2026-08-01 to 2026-08-30/)).toBeVisible();
        await expect(overview.getByText(/30% weight, 5.0 change/)).toBeVisible();
        await expect(overview.getByText(/Body fat/)).toBeVisible();
        await expect.poll(() => overview.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        const disclosureBounds = await disclosure.boundingBox();
        expect(disclosureBounds.width).toBeGreaterThanOrEqual(performanceBounds.width);
        expect(disclosureBounds.y).toBeGreaterThanOrEqual(Math.max(performanceBounds.y + performanceBounds.height, progressBounds.y + progressBounds.height));
        await overview.screenshot({path: testInfo.outputPath(`progress-overview-${width}-open.png`)});
        await page.keyboard.press('Space');
        await expect(disclosure).not.toHaveAttribute('open', '');
        await summary.tap();
        await expect(disclosure).toHaveAttribute('open', '');
        await summary.tap();
        await expect(disclosure).not.toHaveAttribute('open', '');
    }
});

test('dashboard distinguishes insufficient overall progress data from a stable result', async ({page}) => {
    await mockAuthenticatedDashboard(page, dashboard.anchorDate);
    let releaseProgress;
    const progressReady = new Promise(resolve => releaseProgress = resolve);
    await page.route('**/api/dashboard/overall-progress?*', async route => {
        await progressReady;
        return route.fulfill({json: {
            status: null, score: null, currentStart: dashboard.anchorDate, currentEnd: dashboard.anchorDate,
            previousStart: dashboard.anchorDate, previousEnd: dashboard.anchorDate, contributions: []
        }});
    });
    await openSpaRoute(page, '/');
    const overview = page.getByRole('region', {name: 'Progress overview', exact: true});
    const progress = overview.getByRole('region', {name: 'Overall progress', exact: true});
    await expect(progress.getByRole('status')).toHaveText('Calculating…');
    await expect(overview.locator('.performance-score-value')).toBeVisible();
    releaseProgress();
    await expect(progress).toContainText('Not enough data');
    await expect(progress.locator('.overall-progress-score')).toHaveCount(0);
    await overview.locator('summary').click();
    await expect(overview.getByText(/rounded to a score from 0 to 100/)).toBeVisible();
    await expect(overview.locator('details')).toContainText('Not enough data');
    await page.unroute('**/api/dashboard/overall-progress?*');
    let currentProgress;
    await page.route('**/api/dashboard/overall-progress?*', route => route.fulfill({json: {
        ...currentProgress, currentStart: dashboard.anchorDate, currentEnd: dashboard.anchorDate,
        previousStart: dashboard.anchorDate, previousEnd: dashboard.anchorDate, contributions: []
    }}));
    for (const [status, score, label, icon] of [
        ['STABLE', -0.10, 'Stable', 'pi-minus'],
        ['STRONGLY_IMPROVING', 1.50, 'Strongly improving', 'pi-arrow-up'],
        ['SLIGHTLY_IMPROVING', 0.45, 'Slightly improving', 'pi-arrow-up'],
        ['SLIGHTLY_DECLINING', -0.45, 'Slightly declining', 'pi-arrow-down'],
        ['STRONGLY_DECLINING', -1.50, 'Strongly declining', 'pi-arrow-down']
    ]) {
        currentProgress = {status, score};
        await page.reload();
        await expect(progress.getByText(label, {exact: true})).toBeVisible();
        await expect(progress.locator('.overall-progress-score')).toHaveText(score.toFixed(2));
        await expect(progress.locator('.overall-progress-status')).toHaveClass(new RegExp(`overall-progress-${status.toLowerCase()}`));
        await expect(progress.locator('.overall-progress-status i')).toHaveClass(new RegExp(icon));
        await expect(progress.getByText('Not enough data')).toHaveCount(0);
    }
});

test('total bedtime includes awake time on dashboard and history', async ({page}) => {
    const [sleep] = sleepHistory(dashboard.anchorDate);
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {
        initialSleeps: [{...sleep, bedtimeStart: '2026-08-09T23:00:00+02:00', bedtimeEnd: '2026-08-10T07:30:00+02:00'}]
    });
    await openSpaRoute(page, '/');
    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel.getByText('Total bedtime:', {exact: true})).toBeVisible();
    const bedtimeValue = panel.getByText('Total bedtime:', {exact: true}).locator('xpath=following-sibling::*[1]');
    await expect(bedtimeValue).toHaveText('8.5 h');
    for (const width of [393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await panel.screenshot({path: test.info().outputPath(`total-bedtime-dashboard-${width}.png`)});
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    }
    await openSpaRoute(page, '/sleep');
    await expect(page.getByRole('columnheader', {name: 'In bed (7 h minimum)', exact: true})).toBeVisible();
    const eightAndHalfHoursInBed = page.getByRole('cell', {name: '8.5 h', exact: true});
    const sevenHoursAsleep = page.getByRole('cell', {name: '7.0 h', exact: true});
    await expect(eightAndHalfHoursInBed).toBeVisible();
    await expect(eightAndHalfHoursInBed.locator('.sleep-goal-met')).toHaveCount(1);
    await expect(sevenHoursAsleep).toBeVisible();
    await expect(sevenHoursAsleep.locator('.sleep-goal-met')).toHaveCount(1);
    for (const width of [393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await page.screenshot({path: test.info().outputPath(`total-bedtime-history-${width}.png`)});
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    }
});

test('total bedtime uses elapsed time across daylight saving changes', async ({page}) => {
    const [sleep] = sleepHistory(dashboard.anchorDate);
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {
        initialSleeps: [{...sleep, bedtimeStart: '2026-03-28T23:00:00+01:00', bedtimeEnd: '2026-03-29T07:30:00+02:00'}]
    });
    await openSpaRoute(page, '/sleep');
    const sevenAndHalfHoursInBed = page.getByRole('cell', {name: '7.5 h', exact: true});
    await expect(sevenAndHalfHoursInBed).toBeVisible();
    await expect(sevenAndHalfHoursInBed.locator('.sleep-goal-met')).toHaveCount(1);
});

async function expectTimeInBedTrend(page, value, change = '') {
    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    const dashboardValue = panel.getByText('Trend Time in bed:', {exact: true}).locator('+ div');
    await expect(dashboardValue).toHaveText([value, change].filter(Boolean).join(' '));
    await expect(dashboardValue.locator('span').first()).not.toHaveClass(/perfect|good|normal|fail|bad/);
    await expect(dashboardValue.locator('.extra_info')).toHaveCount(change ? 1 : 0);
    if (change) {
        await expect(dashboardValue.locator('.extra_info')).not.toHaveClass(/perfect|good|normal|fail|bad/);
    }
    const homeLabels = await panel.locator('.p-col-5').allTextContents();
    expect(homeLabels.indexOf('Trend Time in bed: ')).toBe(homeLabels.indexOf('Trend Total Sleep: ') + 1);
    await openSpaRoute(page, '/sleep');
    const history = page.locator('.sleep-trend-summary');
    const historyValue = history.locator('.sleep-trend-summary-item').filter({has: page.getByText('Time in bed', {exact: true})});
    await expect(historyValue.locator('.sleep-trend-summary-value')).toHaveText(value);
    await expect(historyValue.locator('.sleep-trend-summary-change')).toHaveText(change);
    await expect(historyValue.locator('.sleep-trend-summary-change')).not.toHaveClass(/positive|negative/);
    const historyLabels = await history.locator('.sleep-trend-summary-label').allTextContents();
    expect(historyLabels.indexOf('Time in bed')).toBe(historyLabels.indexOf('Total sleep') + 1);
}

function sleepWithElapsedHours(sleep, hours) {
    const end = new Date(`${sleep.date}T08:00:00Z`);
    return {...sleep, bedtimeStart: new Date(end.getTime() - hours * 3600000).toISOString(), bedtimeEnd: end.toISOString()};
}

for (const [currentHours, previousHours, change] of [[9, 7, '+120 min'], [7, 9, '-120 min'], [8, 8, '0 min'], [8 + 1 / 120, 8, '+1 min'], [8 - 1 / 120, 8, '-1 min'], [8 + 1 / 3600, 8, '0 min (rounded)']]) {
    test(`time in bed trend shows neutral signed change ${change} on both summaries`, async ({page}) => {
        const sleeps = sleepHistory(dashboard.anchorDate, 60).map((sleep, index) =>
            sleepWithElapsedHours(sleep, index < 30 ? currentHours : previousHours));
        await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleeps});
        await openSpaRoute(page, '/');
        await expectTimeInBedTrend(page, `${currentHours.toFixed(1)} h`, change.replace(' (rounded)', ''));
    });
}

test('time in bed trend averages only complete intervals and fits both summaries at six widths', async ({page}) => {
    const sleeps = sleepHistory(dashboard.anchorDate, 60).map((sleep, index) => {
        const timed = sleepWithElapsedHours(sleep, index < 30 ? (index % 2 ? 10 : 8) : (index % 2 ? 9 : 7));
        return index % 3 === 2 ? {...timed, [index < 30 ? 'bedtimeStart' : 'bedtimeEnd']: null} : timed;
    });
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleeps});
    for (const width of [376, 393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 851});
        await openSpaRoute(page, '/');
        const tabs = page.locator('.home-panels-tabs');
        await tabs.getByRole('tab', {name: 'Sleep'}).click();
        const panel = tabs.locator('.p-tabview-panel:visible');
        await expect(panel.getByText('Trend Status:', {exact: true}).locator('+ div')).toHaveText('Not enough data (20/30)');
        await expect(panel.getByLabel('7.0 h: Excellent', {exact: true})).toBeVisible();
        await expect(panel.getByText('Trend Time in bed:', {exact: true}).locator('+ div')).toHaveText('9.0 h +60 min');
        await panel.screenshot({path: test.info().outputPath(`time-in-bed-home-${width}.png`)});
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        await expectTimeInBedTrend(page, '9.0 h', '+60 min');
        const history = page.locator('.sleep-trend-summary');
        await history.screenshot({path: test.info().outputPath(`time-in-bed-history-${width}.png`)});
        if ([376, 1280].includes(width)) {
            await page.screenshot({path: test.info().outputPath(`time-in-bed-history-full-${width}.png`), fullPage: true});
        }
        await expect(page.locator('.p-datatable-wrapper')).toHaveCSS('overflow-x', 'auto');
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        if (width === 376) {
            const wrapper = page.locator('.p-datatable-wrapper');
            expect(await wrapper.evaluate(element => element.scrollWidth)).toBeGreaterThan(await wrapper.evaluate(element => element.clientWidth));
            await wrapper.evaluate(element => { element.scrollLeft = element.scrollWidth; });
            await expect(page.getByRole('button', {name: 'Edit', exact: true}).first()).toBeVisible();
        }
    }
});

for (const missing of ['current', 'previous', 'all', 'current-start-only', 'current-end-only']) {
    test(`time in bed trend keeps other metrics when complete intervals are missing (${missing})`, async ({page}) => {
        const sleeps = sleepHistory(dashboard.anchorDate, 60).map((sleep, index) => {
            if (index < 30 && missing === 'current-start-only') return {...sleep, bedtimeEnd: null};
            if (index < 30 && missing === 'current-end-only') return {...sleep, bedtimeStart: null};
            const absent = missing === 'all' || (missing === 'current' && index < 30) || (missing === 'previous' && index >= 30);
            return absent ? {...sleep, bedtimeStart: null, bedtimeEnd: null} : sleep;
        });
        await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleeps});
        await openSpaRoute(page, '/');
        const tabs = page.locator('.home-panels-tabs');
        await tabs.getByRole('tab', {name: 'Sleep'}).click();
        await expect(tabs.getByLabel('7.0 h: Excellent', {exact: true})).toBeVisible();
        await expectTimeInBedTrend(page, 'Not enough data');
        await expect(page.locator('.sleep-trend-summary-item').filter({has: page.getByText('Total sleep', {exact: true})}).locator('.sleep-trend-summary-value')).toHaveText('7.0 h');
    });
}

for (const length of [0, 30]) {
    test(`time in bed trend respects overall insufficient history (${length} entries) on both summaries`, async ({page}) => {
        await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleepHistory(dashboard.anchorDate, length)});
        await openSpaRoute(page, '/');
        await expectTimeInBedTrend(page, 'Not enough data');
        await expect(page.locator('.sleep-trend-summary-value')).toHaveText(Array(8).fill('Not enough data'));
    });
}

for (const [selectedDate, bedtimeStart, bedtimeEnd] of [
    ['2026-03-29', '2026-03-28T23:00:00+01:00', '2026-03-29T08:00:00+02:00'],
    ['2026-10-25', '2026-10-25T00:00:00+02:00', '2026-10-25T07:00:00+01:00']
]) {
    test(`time in bed trend respects exact rolling windows and elapsed daylight saving time (${selectedDate})`, async ({page}) => {
        const history = sleepHistory(selectedDate, 61);
        const sleeps = [0, 29, 30, 59, 60].map(index => sleepWithElapsedHours(history[index], index < 30 ? 8 : index < 60 ? 7 : 20));
        sleeps[0] = {...sleeps[0], bedtimeStart, bedtimeEnd};
        await mockAuthenticatedDashboard(page, selectedDate, {initialSleeps: sleeps});
        await openSpaRoute(page, '/');
        await expectTimeInBedTrend(page, '8.0 h', '+60 min');
    });
}

test('time in bed trend uses the selected historical dashboard date and excludes future records', async ({page}) => {
    const selectedDate = '2026-07-12';
    const sleeps = sleepHistory(selectedDate, 60).map((sleep, index) => sleepWithElapsedHours(sleep, index < 30 ? 8 : 7));
    sleeps.push(sleepWithElapsedHours(sleepHistory('2026-07-13', 1)[0], 20));
    await mockAuthenticatedDashboard(page, selectedDate, {initialSleeps: sleeps});
    await openSpaRoute(page, '/');
    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    await expect(tabs.getByText('Trend Time in bed:', {exact: true}).locator('+ div')).toHaveText('8.0 h +60 min');
});

test('dashboard shows all sleep status trends', async ({page}) => {
    const sleeps = sleepHistory(dashboard.anchorDate, 60).map((sleep, index) => index < 30 ? {
        ...sleepWithBedtime(sleep, index % 2 === 0 ? '23:45' : '00:45')
    } : {
        ...sleepWithBedtime(sleep, '23:45'),
        totalSleepDuration: 6 * 60 * 60,
        deepSleepDuration: 60 * 60,
        remSleepDuration: 60 * 60,
        awakeTime: 90 * 60,
        averageHeartRate: 65,
        averageHrv: 25
    });
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleeps});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel.getByText('Trend Total Sleep:', {exact: true})).toBeVisible();
    await expect(panel.getByText('Trend Deep Sleep:', {exact: true})).toBeVisible();
    await expect(panel.getByText('Trend REM Sleep:', {exact: true})).toBeVisible();
    await expect(panel.getByText('Trend Light Sleep:', {exact: true})).toBeVisible();
    await expect(panel.getByText('Trend Awake Time:', {exact: true})).toBeVisible();
    await expect(panel.getByText('Trend Average Heart Rate:', {exact: true})).toBeVisible();
    await expect(panel.getByText('Trend Average HRV:', {exact: true})).toBeVisible();
    await expect(panel.getByText('Trend Bedtime:', {exact: true})).toBeVisible();
    const bedtime = panel.getByText('Trend Bedtime:', {exact: true}).locator('+ div');
    await expect(bedtime).toHaveText('00:15 30 min later');
    for (const value of await bedtime.locator('span').all()) {
        await expect(value).not.toHaveClass(/perfect|good|normal|fail|bad/);
    }
    await expect(panel.getByText(/30-Day Average/)).toHaveCount(0);
    await expect(panel.getByText('Trend Status:', {exact: true})).toBeVisible();
    await expect(panel.getByLabel('7.0 h: Excellent', {exact: true})).toHaveClass(/perfect/);
    await expect(panel.getByLabel('60 bpm: Fair', {exact: true})).toHaveClass(/normal/);
    await expect(panel.getByLabel('30 ms: Fair', {exact: true})).toHaveClass(/normal/);
    await expect(panel.getByText('+30 min', {exact: true}).first()).toHaveClass(/good/);
    await expect(panel.getByText('Trend Awake Time:', {exact: true}).locator('+ div').getByText('-30 min', {exact: true})).toHaveClass(/good/);
    await expect(panel.getByText('-5 bpm', {exact: true})).toHaveClass(/good/);
    await expect(panel.getByText('+5 ms', {exact: true})).toHaveClass(/good/);
    await expect(panel.locator('.extra_info')).toHaveCount(9);
    await expect(panel.getByText(/per month|Current .*Trend/)).toHaveCount(0);
    const labels = await panel.locator('.p-col-5').allTextContents();
    expect(labels.indexOf('Awake: ')).toBeLessThan(labels.indexOf('Trend Status: '));
    for (const width of [376, 393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 851});
        await panel.screenshot({path: test.info().outputPath(`sleep-trends-${width}.png`)});
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    }
});

for (const [currentBedtime, previousBedtime, change] of [
    ['23:45', '00:15', '30 min earlier'],
    ['00:15', '00:15', 'No change']
]) {
    test(`dashboard shows all sleep status trends with bedtime ${change}`, async ({page}) => {
        const sleeps = sleepHistory(dashboard.anchorDate, 60).map((sleep, index) =>
            sleepWithBedtime(sleep, index < 30 ? currentBedtime : previousBedtime));
        await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleeps});
        await openSpaRoute(page, '/');
        const tabs = page.locator('.home-panels-tabs');
        await tabs.getByRole('tab', {name: 'Sleep'}).click();
        const bedtime = tabs.locator('.p-tabview-panel:visible').getByText('Trend Bedtime:', {exact: true}).locator('+ div');
        await expect(bedtime).toHaveText(`${currentBedtime} ${change}`);
        for (const value of await bedtime.locator('span').all()) {
            await expect(value).not.toHaveClass(/perfect|good|normal|fail|bad/);
        }
        await bedtime.screenshot({path: test.info().outputPath(`bedtime-${change.replaceAll(' ', '-')}.png`)});
    });
}

test('dashboard trend labels are consistent across status tabs', async ({page}) => {
    await mockAuthenticatedDashboard(page, dashboard.anchorDate);
    await page.route('**/api/blood-pressures', route => route.fulfill({contentType: 'application/json', body: JSON.stringify(sleepHistory(dashboard.anchorDate, 120).map(entry => ({...dashboardBloodPressures[0], id: entry.id, date: entry.date})))}));
    await openSpaRoute(page, '/');
    const tabs = page.locator('.home-panels-tabs');
    for (const [tab, labels] of [
        ['Status', ['Trend Mood:']],
        ['Body', ['Trend Weight-Loss:', 'Trend Fat-Loss:', 'Trend Muscle-Gain:', 'Trend Status:', 'Trend Upper:', 'Trend Lower:']],
        ['Mood', ['Trend Mood:']],
        ['Nutrition', ['Trend Calories:']]
    ]) {
        await tabs.getByRole('tab', {name: tab}).click();
        const panel = tabs.locator('.p-tabview-panel:visible');
        for (const label of labels) {
            await expect(panel.getByText(label, {exact: true})).toBeVisible();
        }
        await expect(panel.getByText(/Current .*Trend|per month|30-Day Average/)).toHaveCount(0);
        for (const width of [393, 575, 640, 960, 1280]) {
            await page.setViewportSize({width, height: 851});
            await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
            await panel.screenshot({path: test.info().outputPath(`trend-labels-${tab}-${width}.png`)});
            await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        }
    }
});

test('rendered dashboard charts fit after desktop and mobile resizing', async ({page}) => {
    await mockAuthenticatedDashboard(page);
    await page.setViewportSize({width: 1280, height: 851});
    await openSpaRoute(page, '/');
    await page.locator('.dashboard-charts-trigger').scrollIntoViewIfNeeded();
    const charts = page.locator('.dashboard-charts');
    await expect(charts.locator('canvas').first()).toBeVisible();
    for (const width of [393, 575, 640, 960, 1280, 393]) {
        await page.setViewportSize({width, height: 851});
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        await expect.poll(() => charts.locator('canvas').evaluateAll(canvases =>
            Math.max(...canvases.map(canvas => canvas.getBoundingClientRect().right)))).toBeLessThanOrEqual(width);
        await charts.screenshot({path: test.info().outputPath(`responsive-charts-${width}.png`)});
    }
});

for (const length of [0, 30]) {
    test(`dashboard sleep trends remain neutral without enough data (${length} entries)`, async ({page}) => {
        await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleepHistory(dashboard.anchorDate, length)});
        await openSpaRoute(page, '/');
        const tabs = page.locator('.home-panels-tabs');
        await tabs.getByRole('tab', {name: 'Sleep'}).click();
        const panel = tabs.locator('.p-tabview-panel:visible');
        await expect(panel.getByText(/30-Day Average/)).toHaveCount(0);
        await expect(panel.getByText('Trend Total Sleep:', {exact: true})).toBeVisible();
        await expect(panel.getByText('Trend Bedtime:', {exact: true}).locator('+ div')).toHaveText('Not enough data');
        const values = panel.getByText('Not enough data', {exact: true});
        await expect(values).toHaveCount(9);
        for (const value of await values.all()) {
            await expect(value).not.toHaveClass(/perfect|good|normal|fail|bad/);
            await expect(value).not.toHaveAttribute('title');
        }
        await panel.screenshot({path: test.info().outputPath(`sleep-trends-insufficient-${length}.png`)});
    });
}

for (const missingTimes of ['previous', 'current', 'all', 'some', 'current-start-only', 'current-end-only']) {
    test(`dashboard sleep trends remain neutral without enough data for historical bedtime (${missingTimes})`, async ({page}) => {
        const sleeps = sleepHistory(dashboard.anchorDate, 60).map((sleep, index) => {
            const timedSleep = sleepWithBedtime(sleep, '00:15');
            if (index < 30 && missingTimes === 'current-start-only') {
                return {...timedSleep, bedtimeEnd: null};
            }
            if (index < 30 && missingTimes === 'current-end-only') {
                return {...timedSleep, bedtimeStart: null};
            }
            const missing = missingTimes === 'all' || (missingTimes === 'previous' && index >= 30) ||
                (missingTimes === 'current' && index < 30) || (missingTimes === 'some' && index % 2 === 0);
            return missing ? {...sleep, bedtimeStart: null, bedtimeEnd: null} : timedSleep;
        });
        await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleeps});
        await openSpaRoute(page, '/');
        const tabs = page.locator('.home-panels-tabs');
        await tabs.getByRole('tab', {name: 'Sleep'}).click();
        const panel = tabs.locator('.p-tabview-panel:visible');
        const bedtime = panel.getByText('Trend Bedtime:', {exact: true}).locator('+ div');
        await expect(bedtime).toHaveText(['some', 'current-start-only'].includes(missingTimes) ? '00:15 No change' : 'Not enough data');
        const status = panel.getByText('Trend Status:', {exact: true}).locator('+ div');
        await expect(status).toHaveText(missingTimes === 'previous' ? 'EXCELLENT (4/4)' : `Not enough data (${missingTimes === 'some' ? 15 : 0}/30)`);
        await expect(panel.getByLabel('7.0 h: Excellent', {exact: true})).toBeVisible();
        await expect(panel.getByLabel('60 bpm: Fair', {exact: true})).toBeVisible();
        await expect(panel.getByLabel('30 ms: Fair', {exact: true})).toBeVisible();
        for (const value of await bedtime.locator('span').all()) {
            await expect(value).not.toHaveClass(/perfect|good|normal|fail|bad/);
        }
        await panel.screenshot({path: test.info().outputPath(`sleep-trends-historical-${missingTimes}.png`)});
    });
}

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

test.describe('period-aware dashboard warnings', () => {
    test.use({timezoneId: 'Europe/Madrid'});

    const date = '2026-08-12';
    const warning = (page, tab) => page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: tab === 'Calories' ? 'Nutrition' : tab}).getByRole('img', {name: 'Missing entry for selected date'});
    const meal = (mealType, index = 0) => ({id: index + 1, date, mealType, mealSequence: 1, calories: 0, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: []});
    const fast = (startTime, endTime = null) => ({id: 1, startTime, endTime, source: 'AUTOMATIC', notes: null});

    async function openWarnings(page, {time = '13:00', moods = [], meals = [], fasts = [], selectedDate = date, initialSleeps = [], initialWorkouts = []} = {}) {
        await page.clock.install({time: new Date(`${date}T${time}:00+02:00`)});
        const dailyStatus = dashboardDailyStatus(selectedDate);
        for (const period of moods) {
            dailyStatus.mood[period.toLowerCase()] = {id: 1, date: selectedDate, period, value: 3, note: null};
        }
        dailyStatus.mood.average = moods.length ? 3 : null;
        await mockAuthenticatedDashboard(page, selectedDate, {
            initialMeals: meals.map(meal),
            initialFastingPeriods: fasts,
            initialSleeps,
            initialWorkouts,
            dashboardResponse: {...dashboard, anchorDate: selectedDate, dailyStatus}
        });
        await openSpaRoute(page, '/');
        await page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Nutrition'}).click();
        await expect(page.locator('.meal-total')).toBeVisible();
    }

    for (const {time, moods, meals} of [
        {time: '11:59', moods: ['MORNING'], meals: ['BREAKFAST']},
        {time: '17:59', moods: ['MORNING', 'MIDDAY'], meals: ['BREAKFAST', 'LUNCH']}
    ]) {
        test(`only mood warnings become due at the period boundary after ${time}`, async ({page}) => {
            await openWarnings(page, {time, moods, meals});
            await expect(warning(page, 'Mood')).toHaveCount(0);
            await expect(warning(page, 'Calories')).toHaveCount(0);
            await page.clock.fastForward(60000);
            await expect(warning(page, 'Mood')).toHaveCount(1);
            await expect(warning(page, 'Calories')).toHaveCount(0);
            for (const width of [393, 1280]) {
                await page.setViewportSize({width, height: 851});
                await page.locator('.home-panels-tabs').screenshot({path: test.info().outputPath(`period-warnings-${time.replace(':', '')}-${width}.png`)});
                expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
            }
        });
    }

    for (const {label, meals, missing} of [
        {label: 'dinner and snack', meals: ['DINNER', 'SNACK']},
        {label: 'zero-calorie snack', meals: ['SNACK']},
        {label: 'missing calories', meals: [], missing: 'Calories'},
        {label: 'missing mood', meals: ['DINNER'], missing: 'Mood'},
        {label: 'missing sleep', meals: ['DINNER'], missing: 'Sleep'}
    ]) {
        test(`completion warning agrees with entry tabs for ${label}`, async ({page}) => {
            await openWarnings(page, {
                time: '22:00',
                moods: missing === 'Mood' ? ['MORNING', 'MIDDAY'] : ['MORNING', 'MIDDAY', 'EVENING'],
                meals,
                initialSleeps: missing === 'Sleep' ? [] : sleepHistory(date, 1),
                initialWorkouts: [dashboardWorkout(date)]
            });
            await expect(page.getByRole('status', {name: 'Loading sleep data'})).toHaveCount(0);
            await expect(page.getByRole('status', {name: 'Loading workout data'})).toHaveCount(0);
            const completionButton = page.getByRole('button', {name: 'Mark Completed Day', exact: true});
            for (const width of [393, 1280]) {
                await page.setViewportSize({width, height: 851});
                await expect(completionButton).toBeVisible();
                await expect(completionButton.getByRole('img', {name: 'Missing entry for selected date'})).toHaveCount(missing ? 1 : 0);
                for (const entry of ['Calories', 'Mood', 'Sleep', 'Workout', 'Routines']) {
                    await expect(warning(page, entry)).toHaveCount(missing === entry ? 1 : 0);
                }
                expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
                await page.locator('.dashboard-date-header').screenshot({path: test.info().outputPath(`completion-${width}.png`)});
            }
        });
    }

    test('recorded meals hide calorie warnings while overdue mood entries still warn', async ({page}) => {
        await openWarnings(page, {moods: ['MIDDAY'], meals: ['LUNCH', 'SNACK']});
        await expect(warning(page, 'Mood')).toHaveCount(1);
        await expect(warning(page, 'Calories')).toHaveCount(0);
    });

    test('a zero-calorie snack hides the warning only for its recorded date', async ({page}) => {
        await openWarnings(page, {meals: ['SNACK']});
        await expect(warning(page, 'Calories')).toHaveCount(0);
        await page.route('**/api/dashboard/retreat', route => route.fulfill({contentType: 'application/json', body: JSON.stringify({...dashboard, anchorDate: '2026-08-11', dailyStatus: dashboardDailyStatus('2026-08-11')})}));
        await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
        await expect(warning(page, 'Calories')).toHaveCount(1);
    });

    test('recorded due entries clear warnings even with zero calories', async ({page}) => {
        await openWarnings(page, {moods: ['MORNING', 'MIDDAY'], meals: ['BREAKFAST', 'LUNCH']});
        await expect(warning(page, 'Mood')).toHaveCount(0);
        await expect(warning(page, 'Calories')).toHaveCount(0);
    });

    test('fasting warning starts at 16 hours without reloading', async ({page}) => {
        await openWarnings(page, {time: '12:59', fasts: [fast('2026-08-11T21:00:00+02:00')]});
        await expect(warning(page, 'Calories')).toHaveCount(0);
        await expect(warning(page, 'Mood')).toHaveCount(1);
        await page.clock.fastForward(60000);
        await expect(warning(page, 'Calories')).toHaveCount(1);
    });

    test('an automatic fast calculated by the dashboard suppresses warnings before 16 hours', async ({page}) => {
        await page.clock.install({time: new Date('2026-08-12T13:00:00+02:00')});
        await mockAuthenticatedDashboard(page, date, {
            dashboardResponse: {...dashboard, activeFastingPeriod: fast('2026-08-12T01:00:00+02:00')}
        });
        await openSpaRoute(page, '/');
        await page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Nutrition'}).click();
        await expect(page.locator('.meal-total')).toBeVisible();
        await expect(warning(page, 'Calories')).toHaveCount(0);
    });

    test('a recorded meal keeps the calorie tab warning hidden after ending a long fast', async ({page}) => {
        await openWarnings(page, {meals: ['LUNCH'], fasts: [fast('2026-08-10T21:00:00+02:00')]});
        await expect(warning(page, 'Calories')).toHaveCount(0);
        await page.route('**/api/fasting-periods', route => route.fulfill({contentType: 'application/json', body: JSON.stringify([fast('2026-08-10T21:00:00+02:00', '2026-08-12T13:00:00+02:00')])}));
        await page.reload();
        await page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Nutrition'}).click();
        await expect(warning(page, 'Calories')).toHaveCount(0);
    });

    test('a breakfast hides the calorie tab warning during a long fast', async ({page}) => {
        await openWarnings(page, {meals: ['BREAKFAST'], fasts: [fast('2026-08-10T21:00:00+02:00')]});
        await expect(warning(page, 'Calories')).toHaveCount(0);
    });

    for (const recorded of [false, true]) {
        test(`past dates retain daily calorie and full-day mood checks with record=${recorded}`, async ({page}) => {
            await openWarnings(page, {time: '09:00', selectedDate: '2026-08-11', moods: ['MORNING'], fasts: [fast('2026-08-10T21:00:00+02:00')]});
            if (recorded) {
                await page.route('**/api/calories', route => route.fulfill({contentType: 'application/json', body: JSON.stringify([{date: '2026-08-11', calories: 0}])}));
                await page.reload();
                await page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Nutrition'}).click();
                await expect(page.locator('.meal-total')).toBeVisible();
            }
            await expect(warning(page, 'Mood')).toHaveCount(1);
            await expect(warning(page, 'Calories')).toHaveCount(recorded ? 0 : 1);
        });
    }

    test('calorie warnings wait for meal and fasting data to load', async ({page}) => {
        let finishLoad;
        const pendingLoad = new Promise(resolve => finishLoad = resolve);
        await mockAuthenticatedDashboard(page);
        await page.route('**/api/fasting-periods', async route => {
            await pendingLoad;
            await route.fulfill({contentType: 'application/json', body: '[]'});
        });
        await openSpaRoute(page, '/');
        const tab = page.locator('.home-panels-tabs').getByRole('tab').filter({hasText: 'Nutrition'});
        await tab.click();
        await expect(tab.getByRole('status', {name: 'Loading calorie data'})).toBeVisible();
        await expect(warning(page, 'Calories')).toHaveCount(0);
        finishLoad();
        await expect(warning(page, 'Calories')).toHaveCount(1);
    });
});

function sleepWithBedtime(sleep, time) {
    const startDate = new Date(`${sleep.date}T12:00:00Z`);
    if (Number(time.slice(0, 2)) >= 12) {
        startDate.setUTCDate(startDate.getUTCDate() - 1);
    }
    return {...sleep, bedtimeStart: `${startDate.toISOString().slice(0, 10)}T${time}:00`, bedtimeEnd: `${sleep.date}T08:00:00`};
}

function sleepHistory(endDate, length = 30) {
    return Array.from({length}, (_, index) => {
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

test('routine pushes replace earlier reminders for the same routine and expose device actions', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const worker = loadPushWorker(source);
    const routinePayload = {
        title: 'Routine reminder',
        body: 'Morning weigh-in',
        url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
        tag: 'routine-reminder-1',
        snoozeUrl: '/api/routines/1/reminders/10/snooze',
        dismissUrl: '/api/notifications/80/dismiss',
        notificationId: 80
    };

    await dispatchWorkerEvent(worker.listeners.push, {data: {json: () => routinePayload}});
    await dispatchWorkerEvent(worker.listeners.push, {data: {json: () => ({...routinePayload, url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=11', snoozeUrl: '/api/routines/1/reminders/11/snooze', dismissUrl: '/api/notifications/81/dismiss', notificationId: 81})}});

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
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
                snoozeUrl: '/api/routines/1/reminders/10/snooze',
                dismissUrl: '/api/notifications/80/dismiss',
                notificationId: 80
            }
        }
    });
    expect(plain(worker.notifications[1].options)).toMatchObject({
        tag: 'routine-reminder-1',
        data: {url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=11', dismissUrl: '/api/notifications/81/dismiss', notificationId: 81}
    });
});

test('notification reconciliation closes only handled app notifications', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const closed = [];
    const worker = loadPushWorker(source, {
        shownNotifications: [
            {data: {notificationId: 80}, close: () => closed.push(80)},
            {data: {notificationId: 81}, close: () => closed.push(81)},
            {data: {}, close: () => closed.push('legacy')}
        ]
    });

    await dispatchWorkerEvent(worker.listeners.message, {
        data: {type: 'reconcile-in-app-notifications', pendingNotificationIds: [81]}
    });

    expect(closed).toEqual([80]);
});

test('device dismiss closes the routine notification and dismisses its app notification', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const requests = [];
    const worker = loadPushWorker(source, {fetch: async (...args) => requests.push(args)});
    let closed = false;

    await dispatchWorkerEvent(worker.listeners.notificationclick, {
        action: 'dismiss',
        notification: {
            data: {
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
                snoozeUrl: '/api/routines/1/reminders/10/snooze',
                dismissUrl: '/api/notifications/80/dismiss'
            },
            close: () => closed = true
        }
    });

    expect(closed).toBe(true);
    expect(plain(requests)).toEqual([['/api/notifications/80/dismiss', {method: 'POST', credentials: 'include'}]]);
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
                snoozeUrl: '/api/routines/1/reminders/10/snooze',
                dismissUrl: '/api/notifications/80/dismiss'
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

test('clicking the notification body dismisses its app notification before focusing and navigating', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const requests = [];
    const navigatedUrls = [];
    let focused = false;
    const existingClient = {
        url: 'https://weightcontrol.test/routines',
        async navigate(url) {
            navigatedUrls.push(url);
            return {focus: async () => focused = true};
        }
    };
    const worker = loadPushWorker(source, {windowClients: [existingClient], fetch: async (...args) => requests.push(args)});

    await dispatchWorkerEvent(worker.listeners.notificationclick, {
        action: '',
        notification: {
            data: {
                url: '/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10',
                snoozeUrl: null,
                dismissUrl: '/api/notifications/80/dismiss'
            },
            close() {}
        }
    });

    expect(navigatedUrls).toEqual(['https://weightcontrol.test/?routineReminderId=1&routineReminderDate=2026-08-14&routineReminderScheduleId=10']);
    expect(plain(requests)).toEqual([['/api/notifications/80/dismiss', {method: 'POST', credentials: 'include'}]]);
    expect(focused).toBe(true);
    expect(worker.openedUrls).toEqual([]);
});

test('clicking the notification body still opens the app when dismissal fails', async ({request}) => {
    const source = await (await request.get('/push-service-worker.js')).text();
    const worker = loadPushWorker(source, {fetch: async () => Promise.reject(new Error('offline'))});

    await dispatchWorkerEvent(worker.listeners.notificationclick, {
        action: '',
        notification: {
            data: {url: '/records', dismissUrl: '/api/notifications/80/dismiss'},
            close() {}
        }
    });

    expect(worker.openedUrls).toEqual(['https://weightcontrol.test/records']);
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

        await expect(page).toHaveURL('/');
        await expect(page.getByRole('dialog', {name: `Record ${shortcut.outcome}`})).toBeVisible();
        expect(decisionOutcomes).toHaveLength(0);
        await page.getByLabel('Reason (optional)').fill('Walked after lunch');
        await page.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(page.getByText(`${shortcut.outcome} recorded`)).toBeVisible();
        if (shortcut.outcome === 'MISS') {
            await expect(page.locator('.win-celebration--miss')).toBeVisible();
            await expect(page.locator('.win-celebration-title')).toHaveText('MISS');
        } else {
            await expect(page.locator('.win-celebration-title')).toHaveText('WIN');
        }
        expect(decisionOutcomes).toEqual([{date: '2026-08-11', outcome: shortcut.outcome, reason: 'Walked after lunch'}]);

        await page.reload();
        await expect(page.getByText('Dashboard Date')).toBeVisible();
        expect(decisionOutcomes).toHaveLength(1);
    });
}

test('login preserves and records a pending decision outcome shortcut', async ({page}) => {
    const decisionOutcomes = await mockAuthenticatedDashboard(page, '2026-08-11', {requiresLogin: true});

    await page.goto('/?decisionOutcome=WIN');
    await expect(page).toHaveURL('/login?decisionOutcome=WIN');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page).toHaveURL('/');
    await expect(page.getByRole('dialog', {name: 'Record WIN'})).toBeVisible();
    expect(decisionOutcomes).toHaveLength(0);
    await page.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page.getByText('WIN recorded')).toBeVisible();
    expect(decisionOutcomes).toEqual([{date: '2026-08-11', outcome: 'WIN', reason: null}]);
});

test.describe('notification permission prompt', () => {
    test.use({serviceWorkers: 'allow'});

    test('is available without routine reminders', async ({page}) => {
        await mockRoutineReminderHome(page, [], {pushEnabled: true});
        await page.addInitScript(() => Object.defineProperty(Notification, 'permission', {configurable: true, get: () => 'default'}));

        await page.goto('/');

        await expect(page.getByText('Enable notifications')).toBeVisible();
        await expect(page.getByText('Receive daily Mood and Back reminders, weekly Weight and Blood Pressure reminders, routine reminders, 15-minute pause reminders, and notifications when a new app update is available.')).toBeVisible();
    });
});

test('daily reminder settings show and save the three default times', async ({page}) => {
    await mockRoutineReminderHome(page, []);

    await openSpaRoute(page, '/settings');

    await expect(page.locator('#morning-reminder-time')).toHaveValue('07:30');
    await expect(page.locator('#midday-reminder-time')).toHaveValue('13:30');
    await expect(page.locator('#evening-reminder-time')).toHaveValue('20:30');
    await expect(page.getByRole('combobox', {name: 'Weight day', exact: true})).toHaveText('Saturday');
    await expect(page.getByRole('combobox', {name: 'Blood pressure day', exact: true})).toHaveText('Saturday');
    await expect(page.locator('#weight-reminder-time')).toHaveValue('05:00');
    await expect(page.locator('#blood-pressure-reminder-time')).toHaveValue('05:15');
    await expect(page.getByText('Active coaching plan', {exact: true})).toHaveCount(0);
    await expect(page.getByText('Health constraints', {exact: true})).toHaveCount(0);
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/push/reminder-settings') && request.method() === 'PUT');
    await page.getByRole('button', {name: 'Save reminder schedule'}).click();
    expect((await saveRequest).postDataJSON()).toEqual({morningTime: '07:30', middayTime: '13:30', eveningTime: '20:30', weightTime: '05:00', bloodPressureTime: '05:15', weightDay: 'SATURDAY', bloodPressureDay: 'SATURDAY'});
    await expect(page.getByText('Reminder schedule saved')).toBeVisible();
});

async function mockWeeklyMeasurementSchedule(page) {
    await mockRoutineReminderHome(page, []);
    const state = {
        settings: {morningTime: '07:30', middayTime: '13:30', eveningTime: '20:30', weightTime: '05:00', bloodPressureTime: '05:15', weightDay: 'SATURDAY', bloodPressureDay: 'SATURDAY', timeZone: 'Europe/Madrid'},
        requests: [], failSave: false, saveGate: Promise.resolve()
    };
    await page.route('**/api/push/reminder-settings', async route => {
        if (route.request().method() === 'PUT') {
            state.requests.push(route.request().postDataJSON());
            await state.saveGate;
            if (state.failSave) return route.fulfill({status: 500, json: {message: 'Try again'}});
            state.settings = {...state.settings, ...route.request().postDataJSON()};
        }
        return route.fulfill({json: state.settings});
    });
    await page.route('**/api/push/agenda', route => route.fulfill({json: {
        date: '2026-10-03', timeZone: 'Europe/Madrid', entries: [
            ...[{type: 'WEIGHT', key: 'weight', title: 'Weight reminder'}, {type: 'BLOOD_PRESSURE', key: 'bloodPressure', title: 'Blood pressure reminder'}]
                .filter(entry => state.settings[`${entry.key}Day`] === 'SATURDAY')
                .map(entry => ({type: entry.type, scheduledTime: state.settings[`${entry.key}Time`], title: entry.title, details: null, status: 'PENDING'})),
            {type: 'MOOD', scheduledTime: state.settings.morningTime, title: 'Mood check-in', details: 'Morning', status: 'PENDING'}
        ]
    }}));
    return state;
}

async function chooseWeeklyDay(page, label, day) {
    await page.getByRole('combobox', {name: `${label} day`, exact: true}).click();
    await page.getByRole('option', {name: day, exact: true}).click();
    await expect(page.getByRole('listbox')).not.toBeVisible();
}

for (const width of [390, 575, 640, 960, 1280]) {
    test(`weekly measurement schedule stays usable in Settings and Agenda at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        const state = await mockWeeklyMeasurementSchedule(page);
        await openSpaRoute(page, '/settings');
        await chooseWeeklyDay(page, 'Weight', 'Monday');
        await chooseWeeklyDay(page, 'Blood pressure', 'Friday');
        await page.getByLabel('Weight time', {exact: true}).click();
        await page.getByRole('button', {name: 'Next Hour', exact: true}).click();
        await page.getByRole('heading', {name: 'Weekly measurement schedule'}).click();
        await page.getByRole('button', {name: 'Save reminder schedule'}).click();
        await expect(page.getByText('Reminder schedule saved', {exact: true})).toBeVisible();
        expect(state.settings).toMatchObject({weightDay: 'MONDAY', bloodPressureDay: 'FRIDAY', weightTime: '06:00', bloodPressureTime: '05:15', morningTime: '07:30'});
        await page.goto('/');
        await expect(page.getByRole('combobox', {name: 'Weight day', exact: true})).toHaveText('Monday');
        await expect(page.getByRole('combobox', {name: 'Blood pressure day', exact: true})).toHaveText('Friday');
        await page.getByRole('heading', {name: 'Weekly measurement schedule'}).scrollIntoViewIfNeeded();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`settings-weekly-${width}.png`)});

        state.settings.weightDay = 'SATURDAY';
        state.settings.bloodPressureDay = 'SATURDAY';
        await openSpaRoute(page, '/agenda');
        const row = page.locator('.agenda-entry').filter({hasText: 'Blood pressure reminder'});
        await row.getByRole('button', {name: 'Change schedule', exact: true}).click();
        const dialog = page.getByRole('dialog', {name: 'Change notification schedule'});
        await expect(dialog.getByText('This changes the weekly day and time for future Blood Pressure reminders. Times use Europe/Madrid.')).toBeVisible();
        await chooseWeeklyDay(page, 'Blood pressure', 'Thursday');
        await expect(dialog.getByLabel('Blood pressure time', {exact: true})).toHaveValue('05:15');
        const box = await dialog.boundingBox();
        expect(box.x).toBeGreaterThanOrEqual(0);
        expect(box.x + box.width).toBeLessThanOrEqual(width);
        await page.screenshot({path: testInfo.outputPath(`agenda-weekly-${width}.png`)});
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog).not.toBeVisible();
        await expect(row).toHaveCount(0);
        expect(state.settings).toMatchObject({weightDay: 'SATURDAY', bloodPressureDay: 'THURSDAY', weightTime: '06:00', bloodPressureTime: '05:15'});
        await expect(page.locator('.agenda-entry').filter({hasText: 'Weight reminder'})).toBeVisible();
    });
}

for (const entry of [{title: 'Weight reminder', label: 'Weight', key: 'weight'}, {title: 'Blood pressure reminder', label: 'Blood pressure', key: 'bloodPressure'}]) {
    test(`weekly measurement schedule preserves ${entry.label} draft through cancellation, pending and failed saves`, async ({page}) => {
        const state = await mockWeeklyMeasurementSchedule(page);
        await openSpaRoute(page, '/agenda');
        const row = page.locator('.agenda-entry').filter({hasText: entry.title});
        await row.getByRole('button', {name: 'Change schedule', exact: true}).click();
        let dialog = page.getByRole('dialog', {name: 'Change notification schedule'});
        await chooseWeeklyDay(page, entry.label, 'Tuesday');
        await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
        expect(state.requests).toHaveLength(0);
        await row.getByRole('button', {name: 'Change schedule', exact: true}).click();
        await expect(page.getByRole('combobox', {name: `${entry.label} day`, exact: true})).toHaveText('Saturday');
        await chooseWeeklyDay(page, entry.label, 'Tuesday');
        state.failSave = true;
        let finishSave;
        state.saveGate = new Promise(resolve => { finishSave = resolve; });
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog.getByRole('button', {name: 'Saving…', exact: true})).toBeDisabled();
        await expect(dialog.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
        await expect(dialog.locator('#agenda-reminder-day')).toHaveAttribute('aria-disabled', 'true');
        await page.keyboard.press('Escape');
        await expect(dialog).toBeVisible();
        finishSave();
        await expect(page.getByText('Update failed', {exact: true})).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Save', exact: true})).toBeEnabled();
        await expect(dialog.getByRole('combobox', {name: `${entry.label} day`, exact: true})).toHaveText('Tuesday');
        expect(state.settings[`${entry.key}Day`]).toBe('SATURDAY');
        state.failSave = false;
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog).not.toBeVisible();
        expect(state.settings[`${entry.key}Day`]).toBe('TUESDAY');
        expect(state.settings[`${entry.key === 'weight' ? 'bloodPressure' : 'weight'}Day`]).toBe('SATURDAY');
        await expect(row).toHaveCount(0);
    });
}

test('weekly measurement schedule remains unchanged when Agenda edits a daily reminder time', async ({page}) => {
    const state = await mockWeeklyMeasurementSchedule(page);
    state.settings.weightDay = 'MONDAY';
    state.settings.bloodPressureDay = 'FRIDAY';
    await openSpaRoute(page, '/agenda');
    await page.locator('.agenda-entry').filter({hasText: 'Mood check-in'}).getByRole('button', {name: 'Change time', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Change notification time'});
    await expect(dialog.getByRole('combobox', {name: /day/})).toHaveCount(0);
    await dialog.getByLabel('Time', {exact: true}).click();
    await page.getByRole('button', {name: 'Next Hour', exact: true}).click();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', {name: 'Next Hour', exact: true})).not.toBeVisible();
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).not.toBeVisible();
    expect(state.settings).toMatchObject({morningTime: '08:30', middayTime: '13:30', eveningTime: '20:30', weightDay: 'MONDAY', bloodPressureDay: 'FRIDAY'});
});

test('weekly measurement schedule keeps Settings draft after a failed save and retries', async ({page}) => {
    const state = await mockWeeklyMeasurementSchedule(page);
    await openSpaRoute(page, '/settings');
    await chooseWeeklyDay(page, 'Weight', 'Wednesday');
    await chooseWeeklyDay(page, 'Blood pressure', 'Sunday');
    state.failSave = true;
    await page.getByRole('button', {name: 'Save reminder schedule'}).click();
    await expect(page.getByText('Notification failed', {exact: true})).toBeVisible();
    await expect(page.getByRole('combobox', {name: 'Weight day', exact: true})).toHaveText('Wednesday');
    await expect(page.getByRole('combobox', {name: 'Blood pressure day', exact: true})).toHaveText('Sunday');
    state.failSave = false;
    await page.getByRole('button', {name: 'Save reminder schedule'}).click();
    await expect(page.getByText('Reminder schedule saved', {exact: true})).toBeVisible();
    expect(state.settings).toMatchObject({weightDay: 'WEDNESDAY', bloodPressureDay: 'SUNDAY'});
});

test('agenda shows statuses and a current-time divider without mobile overflow', async ({page}) => {
    await page.setViewportSize({width: 390, height: 844});
    await mockAuthenticatedAgenda(page, {
        date: '2026-08-29',
        timeZone: 'Europe/Madrid',
        entries: [
            {scheduledTime: '00:00:00', type: 'MOOD', title: 'Mood check-in', details: 'Morning', status: 'COMPLETED'},
            {scheduledTime: '00:01:00', type: 'BACK_PAIN', title: 'Back pain check-in', details: 'Morning', status: 'NO_ISSUE'},
            {scheduledTime: '23:59:00', type: 'MEDICATION', title: 'Vitamin D', details: '1 tablet', status: 'PENDING'}
        ]
    });

    await openSpaRoute(page, '/agenda');

    await expect(page.getByText('Completed', {exact: true})).toBeVisible();
    await expect(page.getByText('No issue', {exact: true})).toBeVisible();
    await expect(page.getByText('Pending', {exact: true})).toBeVisible();
    await expect(page.getByText('Now', {exact: true})).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();

    await page.setViewportSize({width: 1280, height: 900});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
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
    const goalField = panel.getByLabel('Goal', {exact: true});
    await expect(goalField).toHaveValue('Build strength safely');
    expect(await goalField.evaluate(field => field.tagName)).toBe('TEXTAREA');
    await expect(panel.getByLabel('Guidelines', {exact: true})).toHaveValue('Protect my lower back');
    await expect(panel.getByLabel('Focus areas', {exact: true})).toHaveValue('Training consistency\nRecovery');
    await expect(panel.getByLabel('Next actions', {exact: true})).toHaveValue('Complete three strength sessions each week');

    const guidelinesField = panel.getByLabel('Guidelines', {exact: true}).locator('..');
    const focusAreasField = panel.getByLabel('Focus areas', {exact: true}).locator('..');
    const guidelinesDesktopBox = await guidelinesField.boundingBox();
    const focusAreasDesktopBox = await focusAreasField.boundingBox();
    expect(Math.abs(guidelinesDesktopBox.y - focusAreasDesktopBox.y)).toBeLessThan(2);
    expect(focusAreasDesktopBox.x).toBeGreaterThan(guidelinesDesktopBox.x);

    await goalField.fill('Build strength safely\nImprove recovery');
    await panel.getByLabel('Guidelines', {exact: true}).fill('Protect my lower back\nProgress gradually');
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/coaching-plan') && request.method() === 'PUT');
    await panel.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saveRequest).postDataJSON()).toEqual({
        goal: 'Build strength safely\nImprove recovery',
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

test('routines can have their reminders cleared', async ({page}, testInfo) => {
    await mockAuthenticatedRoutines(page, [
        routine(1, 'Evening walk', '18:00:00'),
        routine(2, 'No reminder', null),
        routine(3, 'Morning weigh-in', ['07:30:00', '12:30:00'])
    ]);

    await openSpaRoute(page, '/routines');
    const row = page.locator('tbody tr').filter({hasText: 'Morning weigh-in'});

    const edit = row.getByRole('button', {name: 'Edit', exact: true});
    const remove = row.getByRole('button', {name: 'Delete', exact: true});
    await expect(edit).toHaveClass(/p-button-success/);
    await expect(remove).toHaveClass(/p-button-warning/);
    await expect(remove).not.toHaveClass(/p-button-danger/);
    await expect(edit).toHaveClass(/p-button-outlined/);
    await expect(remove).toHaveClass(/p-button-outlined/);
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await row.locator('.action-group').screenshot({path: testInfo.outputPath(`routine-management-actions-${width}.png`)});
    }
    await edit.click();
    const dialog = page.getByRole('dialog', {name: 'Routine'});
    await expect(dialog.locator('#routine')).toHaveValue('Morning weigh-in');
    await expect(dialog.getByRole('button', {name: 'Remove reminder 1', exact: true})).toHaveClass(/p-button-danger/);
    const personalRecords = dialog.locator('#routine-personal-records');
    await expect(personalRecords).toBeChecked();
    await dialog.locator('label[for="routine-personal-records"]').click();
    await expect(personalRecords).not.toBeChecked();
    await dialog.getByRole('button', {name: 'Remove reminder 1'}).click();
    await dialog.getByRole('button', {name: 'Remove reminder 1'}).click();
    const updateRequest = page.waitForRequest(request => request.url().endsWith('/api/routines/3') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save'}).click();
    expect((await updateRequest).postDataJSON()).toMatchObject({reminderTimes: [], personalRecordsEnabled: false});
    await expect(row).toContainText('—');
});

test('routine automatic triggers are created, edited, and persisted', async ({page}, testInfo) => {
    await mockAuthenticatedRoutines(page, []);
    await page.route('**/routines', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
        : route.fallback());
    await openSpaRoute(page, '/routines');
    const dialog = page.getByRole('dialog', {name: 'Routine'});
    await page.getByRole('button', {name: 'New', exact: true}).click();
    await expect(dialog.locator('.routine-trigger-field .p-dropdown-label')).toHaveText('Manual only');
    await dialog.locator('#routine').fill('Fruit with breakfast');
    await dialog.locator('.p-multiselect').click();
    await page.getByRole('option', {name: 'MIND', exact: true}).click();
    await dialog.locator('.p-multiselect').press('Escape');
    await dialog.locator('.routine-trigger-field .p-dropdown').click();
    await page.getByRole('option', {name: 'Meal with fruit', exact: true}).click();
    const create = page.waitForRequest(request => request.url().endsWith('/api/routines') && request.method() === 'POST');
    const created = page.waitForResponse(response => response.url().endsWith('/api/routines') && response.request().method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await create).postDataJSON()).toMatchObject({name: 'Fruit with breakfast', types: ['MIND'], automaticTrigger: 'FRUIT_MEAL'});
    expect((await created).ok()).toBe(true);
    await expect(dialog).not.toBeVisible();
    const row = page.locator('tbody tr').filter({hasText: 'Fruit with breakfast'});
    await expect(row).toBeVisible();
    await page.reload();
    await row.getByRole('button', {name: 'Edit', exact: true}).click();
    await expect(dialog.locator('.routine-trigger-field .p-dropdown-label')).toHaveText('Meal with fruit');
    await dialog.locator('.routine-trigger-field .p-dropdown').click();
    await page.getByRole('option', {name: 'Completed fast over 12 hours', exact: true}).click();
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await dialog.evaluate(element => element.getBoundingClientRect().left >= 0 && element.getBoundingClientRect().right <= innerWidth)).toBe(true);
        await dialog.screenshot({path: testInfo.outputPath(`routine-automatic-trigger-${width}.png`), animations: 'disabled'});
    }
    const update = page.waitForRequest(request => request.url().endsWith('/api/routines/1') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await update).postDataJSON()).toMatchObject({automaticTrigger: 'FAST_OVER_12_HOURS'});
    await expect(dialog).not.toBeVisible();
    await page.reload();
    await row.getByRole('button', {name: 'Edit', exact: true}).click();
    await expect(dialog.locator('.routine-trigger-field .p-dropdown-label')).toHaveText('Completed fast over 12 hours');
    await dialog.locator('.routine-trigger-field .p-dropdown').click();
    await page.getByRole('option', {name: 'Manual only', exact: true}).click();
    const disable = page.waitForRequest(request => request.url().endsWith('/api/routines/1') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await disable).postDataJSON()).toMatchObject({automaticTrigger: 'NONE'});
    await expect(dialog).not.toBeVisible();
    await page.reload();
    await row.getByRole('button', {name: 'Edit', exact: true}).click();
    await expect(dialog.locator('.routine-trigger-field .p-dropdown-label')).toHaveText('Manual only');
});

test('meal editor submits a fruit food with the fruit marker', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await openSpaRoute(page, '/meals/new?date=2026-08-12');
    const form = page.locator('#meal-form');
    await form.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Lunch', exact: true}).click();
    await form.getByRole('button', {name: 'Add food', exact: true}).click();
    const food = page.getByRole('dialog', {name: 'Food', exact: true});
    await food.getByLabel('Food', {exact: true}).fill('Apple');
    await enterFoodNutrients(food);
    await food.getByLabel('Calories', {exact: true}).fill('100');
    await food.locator('label[for="dish-fruit"]').click();
    await food.getByRole('button', {name: 'Apply', exact: true}).click();
    const create = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await create).postDataJSON().dishes).toMatchObject([{name: 'Apple', fruit: true}]);
});

test('workout editor submits cardio, strength, and stretching triggers', async ({page}) => {
    const exercises = [
        {id: 1, name: 'Outdoor run', description: 'Run outdoors.', trackingMode: 'CARDIO', exerciseType: 'TRAINING'},
        {id: 2, name: 'Squat', description: 'Strength squat.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 3, name: 'Wall calf stretch', description: 'Hold each side.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    const lines = editor.locator('.workout-line-card');
    await lines.nth(0).locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: 'Squat', exact: true}).click();
    await lines.nth(0).getByLabel('Repetitions').fill('10');
    await editor.getByRole('button', {name: 'Add cardio', exact: true}).click();
    await lines.nth(1).getByLabel('Exercise', {exact: true}).click();
    await page.getByRole('option', {name: 'Outdoor run', exact: true}).click();
    await lines.nth(1).getByLabel('Minutes', {exact: true}).fill('20');
    await editor.getByRole('button', {name: 'Add stretching', exact: true}).click();
    await lines.nth(2).getByLabel('Exercise', {exact: true}).click();
    await page.getByRole('option', {name: 'Wall calf stretch', exact: true}).click();
    await lines.nth(2).getByLabel('Mode', {exact: true}).click();
    await page.getByRole('option', {name: 'Time', exact: true}).click();
    await lines.nth(2).getByLabel('Minutes', {exact: true}).fill('1');
    const create = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    const payload = (await create).postDataJSON();
    expect(payload.lines.map(line => line.exerciseId)).toEqual([2, 1, 3]);
    expect(payload.lines[0].segments[0].repetitions).toBe(10);
    expect(payload.lines[1].segments[0].durationSeconds).toBeGreaterThan(0);
    expect(payload.lines[2].segments[0].durationSeconds).toBeGreaterThan(0);
});

test('fasting editor submits a completed fast longer than twelve hours', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await openSpaRoute(page, '/calories');
    await page.getByRole('tab', {name: 'Fasting periods'}).click();
    await page.locator('.p-tabview-panel:visible').getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Fasting Period'});
    await dialog.getByLabel('Notes (optional)').fill('Automatic routine trigger fast');
    const create = page.waitForRequest(request => request.url().endsWith('/api/fasting-periods') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    const payload = (await create).postDataJSON();
    expect((new Date(payload.endTime) - new Date(payload.startTime)) / 3600000).toBeGreaterThan(12);
    expect(payload.notes).toBe('Automatic routine trigger fast');
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
    await expect(page).toHaveURL('/');
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

test('routine reminder cancel closes the modal without acting on the reminder', async ({page}) => {
    const date = madridDate();
    const actionRequests = [];
    page.on('request', request => {
        const path = new URL(request.url()).pathname;
        if (/^\/api\/routines\/1\/(checkins|reminders\/10\/snooze)$/.test(path) || path === '/api/notifications/80/dismiss') actionRequests.push(path);
    });
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {
        initialNotifications: [{id: 80, type: 'ROUTINE', title: 'Routine reminder', message: 'Morning weigh-in', reminderDate: date, availableAt: `${date}T07:30:00+02:00`, actionUrl: `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10&notificationId=80`}]
    });

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10&notificationId=80`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    const markDone = dialog.getByRole('button', {name: 'Mark as done'});
    const cancel = dialog.getByRole('button', {name: 'Cancel', exact: true});
    await markDone.focus();
    await page.keyboard.press('Tab');
    await expect(cancel).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL('/');
    expect(actionRequests).toEqual([]);
    await expect(page.getByRole('button', {name: '1 pending notification'})).toBeVisible();
    await page.getByRole('button', {name: '1 pending notification'}).click();
    const pendingReminder = page.locator('.notification-panel .notification-content');
    await expect(pendingReminder).toContainText('Morning weigh-in');
    await pendingReminder.click();
    await expect(dialog).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`routineReminderId=1.*routineReminderDate=${date}.*routineReminderScheduleId=10`));
});

test('routine reminder can reschedule only this occurrence', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {
        initialNotifications: [{id: 80, type: 'ROUTINE', title: 'Routine reminder', message: 'Morning weigh-in', reminderDate: date, availableAt: `${date}T07:30:00+02:00`}]
    });

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10&notificationId=80`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});
    await dialog.getByRole('button', {name: 'Change time'}).click();
    await expect(dialog).toContainText('Your regular schedule stays the same.');
    await dialog.locator('#routine-reschedule-time').fill('08:15');
    const request = page.waitForRequest(item => item.url().endsWith('/api/notifications/80/reschedule') && item.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();

    expect((await request).postDataJSON()).toEqual({date, time: '08:15'});
    await expect(page.getByText('Notification rescheduled')).toBeVisible();
    await expect(dialog).not.toBeVisible();
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
    await expect(page).toHaveURL('/');
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

test('medication reminder can reschedule one dose', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {
        medicationDose: medicationReminderDose(),
        initialNotifications: [{id: 82, type: 'MEDICATION', title: 'Medication reminder', message: 'Vitamin D', reminderDate: date, availableAt: `${date}T05:00:00+02:00`}]
    });

    await openSpaRoute(page, `/?medicationDoseId=50&notificationId=82`);
    const dialog = page.getByRole('dialog', {name: 'Medication reminder'});
    await dialog.getByRole('button', {name: 'Change time'}).click();
    await dialog.locator('#medication-reschedule-time').fill('09:00');
    const request = page.waitForRequest(item => item.url().endsWith('/api/notifications/82/reschedule') && item.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();

    expect((await request).postDataJSON()).toEqual({date, time: '09:00'});
    await expect(dialog).not.toBeVisible();
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
    await expect(page).toHaveURL('/');
    finishDashboardLoad();
    await expect(page.getByText('Dashboard Date')).toBeVisible();
});

for (const reminder of [
    {type: 'mood', period: 'MIDDAY', title: 'Midday mood reminder', form: 'Mood'},
    {type: 'back', period: 'EVENING', title: 'Evening back reminder', form: 'Back check-in'}
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

test('routine reminder content and actions remain visible at mobile and desktop sizes', async ({page}, testInfo) => {
    const date = madridDate();
    const routineName = 'Morning weigh-in with a deliberately long title for responsive layout';
    await mockRoutineReminderHome(page, [routine(1, routineName, '07:30:00')], {
        initialNotifications: [{id: 80, type: 'ROUTINE', title: 'Routine reminder', message: routineName, reminderDate: date, availableAt: `${date}T07:30:00+02:00`, actionUrl: `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10&notificationId=80`}]
    });

    await page.setViewportSize({width: 1280, height: 800});
    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10&notificationId=80`);
    const dialog = page.getByRole('dialog', {name: 'Routine reminder'});

    for (const viewport of [{width: 1280, height: 800}, {width: 655, height: 500}, {width: 393, height: 851}]) {
        await page.setViewportSize(viewport);
        await expect(dialog.getByText("It's time for")).toBeVisible();
        await expect(dialog.getByText(routineName)).toBeVisible();
        await expect(dialog.getByText('Scheduled time')).toBeVisible();
        await expect(dialog.getByText('07:30')).toBeVisible();
        await expect(dialog.getByText('Europe/Madrid')).toBeVisible();
        await expect(dialog.getByLabel('Snooze for')).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Change time'})).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Snooze'})).toBeVisible();
        const completeButton = dialog.getByRole('button', {name: 'Mark as done'});
        await expect(completeButton).toBeVisible();
        const cancelButton = dialog.getByRole('button', {name: 'Cancel', exact: true});
        await expect(cancelButton).toBeVisible();
        expect(await dialog.locator('.routine-reminder-dialog-footer--routine .action-group > .p-button').allTextContents()).toEqual(['Change time', 'Snooze', 'Mark as done', 'Cancel']);
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        if (viewport.width === 393) {
            const actionWidths = await dialog.locator('.routine-reminder-dialog-footer--routine .action-group > .p-button').evaluateAll(elements => elements.map(element => element.clientWidth));
            expect(actionWidths).toEqual([actionWidths[0], actionWidths[0], actionWidths[0], actionWidths[0]]);
        }
        if (viewport.width === 1280 || viewport.width === 393) await dialog.screenshot({path: testInfo.outputPath(`routine-reminder-cancel-${viewport.width}.png`)});
        expect(await completeButton.evaluate(button => {
            const label = button.querySelector('.p-button-label');
            return label.scrollWidth <= label.clientWidth;
        })).toBe(true);
    }
});

test('check-in reminder actions stay readable at mobile and desktop sizes', async ({page}, testInfo) => {
    const date = madridDate();
    await page.clock.setFixedTime(new Date(`${date}T09:00:00+02:00`));
    await mockRoutineReminderHome(page, []);
    await openSpaRoute(page, `/?checkInReminder=back&checkInPeriod=EVENING&checkInReminderDate=${date}&notificationId=23`);
    const dialog = page.getByRole('dialog', {name: 'Evening back reminder'});
    const actionGroup = dialog.locator('.reminder-action-group');
    await expect(dialog).toBeVisible();

    for (const viewport of [{width: 1280, height: 800}, {width: 393, height: 851}, {width: 376, height: 812}]) {
        await page.setViewportSize(viewport);
        await expect(dialog.getByRole('button', {name: 'Change time'})).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Record'})).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Dismiss'})).toBeVisible();
        const buttonWidths = await actionGroup.locator('> .p-button').evaluateAll(buttons => buttons.map(button => button.clientWidth));
        if (viewport.width < 576) expect(await actionGroup.evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(1);
        else expect(buttonWidths.every(width => width >= 144)).toBe(true);
        expect(await dialog.locator('.p-dialog-footer .p-button-label').evaluateAll(labels => labels.every(label => label.scrollWidth <= label.clientWidth))).toBe(true);
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        if (viewport.width === 1280 || viewport.width === 376) await dialog.screenshot({path: testInfo.outputPath(`check-in-reminder-actions-${viewport.width}.png`)});
    }
});

test('measurement reminder actions stay readable at mobile and desktop sizes', async ({page}, testInfo) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {today: date, initialWeights: [reminderWeight('2026-08-15')]});
    await openSpaRoute(page, `/?measurementReminder=weight&measurementReminderDate=${date}&notificationId=24`);
    const dialog = page.getByRole('dialog', {name: 'Measurement reminder'});
    const actionGroup = dialog.locator('.reminder-action-group');

    for (const viewport of [{width: 1280, height: 800}, {width: 393, height: 851}, {width: 376, height: 812}]) {
        await page.setViewportSize(viewport);
        await expect(dialog.getByRole('button', {name: 'Change date and time'})).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Record'})).toBeVisible();
        await expect(dialog.getByRole('button', {name: 'Dismiss'})).toBeVisible();
        const buttonWidths = await actionGroup.locator('> .p-button').evaluateAll(buttons => buttons.map(button => button.clientWidth));
        if (viewport.width < 576) expect(await actionGroup.evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(1);
        else expect(buttonWidths.every(width => width >= 144)).toBe(true);
        expect(await dialog.locator('.p-dialog-footer .p-button-label').evaluateAll(labels => labels.every(label => label.scrollWidth <= label.clientWidth))).toBe(true);
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        if (viewport.width === 1280 || viewport.width === 376) await dialog.screenshot({path: testInfo.outputPath(`measurement-reminder-actions-${viewport.width}.png`)});
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
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {checkinDelay: 150});
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
    await expect(dialog.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();

    expect(new Date((await checkinRequest).postDataJSON().date).toString()).not.toBe('Invalid Date');
    await expect(dialog).not.toBeVisible();
    await expect(page).toHaveURL('/');
    await expect(page.getByText('Routine marked as done')).toBeVisible();
    expect(dashboardRefreshRequests).toBe(0);
});

test('different routines can be completed rapidly with compact streak context on mobile', async ({page}, testInfo) => {
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
    const complete = firstRow.getByRole('button', {name: 'Complete routine', exact: true});
    await expect(complete).toHaveClass(/p-button-success/);
    await expect(complete).toHaveClass(/p-button-outlined/);
    await expect(complete).not.toHaveClass(/p-button-rounded/);
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectRoutineActionCentered(complete);
    }
    await page.setViewportSize({width: 390, height: 844});
    const completeBounds = await complete.boundingBox();
    await firstRow.screenshot({path: testInfo.outputPath('routine-complete-action-390.png')});
    await complete.click();
    await secondRow.getByRole('button', {name: 'Complete routine', exact: true}).click();
    await checkins;

    const undo = firstRow.getByRole('button', {name: 'Undo routine', exact: true});
    await expect(undo).toHaveClass(/p-button-warning/);
    await expect(undo).toHaveClass(/p-button-outlined/);
    await expect(undo).not.toHaveClass(/p-button-rounded/);
    const undoBounds = await undo.boundingBox();
    expect(undoBounds.width).toBeCloseTo(completeBounds.width, 1);
    expect(undoBounds.height).toBeCloseTo(completeBounds.height, 1);
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectRoutineActionCentered(undo);
    }
    await page.setViewportSize({width: 390, height: 900});
    const nameCell = await firstRow.locator('.routine-name-cell').boundingBox();
    expect(nameCell.x).toBeGreaterThanOrEqual(0);
    expect(nameCell.x + nameCell.width).toBeLessThanOrEqual(390);
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await panel.screenshot({path: testInfo.outputPath(`routine-checkin-actions-${width}.png`)});
    }
    await expect(firstRow.getByText('Best: 1 days', {exact: true})).toBeVisible();
    await expect(secondRow.getByText('Best: 1 days', {exact: true})).toBeVisible();
    await expect(panel.getByText('Streak', {exact: true})).toBeVisible();
    await expect(page.getByRole('dialog', {name: 'Personal records'})).not.toBeVisible();
});

async function expectRoutineActionCentered(button) {
    const buttonBounds = await button.boundingBox();
    const cellBounds = await button.locator('xpath=ancestor::td').boundingBox();
    expect(Math.abs(buttonBounds.x + buttonBounds.width / 2 - cellBounds.x - cellBounds.width / 2)).toBeLessThanOrEqual(1);
}

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
    await expect(page).toHaveURL('/calories');

    await plan.click();
    const planMenu = menubar.locator('.p-submenu-list').filter({hasText: 'Goal and plan'});
    await expect(planMenu).toBeVisible();
    await planMenu.getByText('Goal and plan', {exact: true}).click();
    await expect(page).toHaveURL('/plan');

    await review.click();
    const reviewMenu = menubar.locator('.p-submenu-list').filter({hasText: 'Personal Records'});
    await expect(reviewMenu).toBeVisible();
    await expect(reviewMenu).toContainText('Daily reflections');
    await expect(reviewMenu.getByText('Personal Records', {exact: true}).locator('..').locator('.pi-star')).toBeVisible();

    await page.getByRole('button', {name: 'Account'}).click();
    await expect(page.getByText('Backup', {exact: true})).toHaveCount(0);
    await page.getByText('Settings', {exact: true}).click();
    await expect(page).toHaveURL('/settings');

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
    await expect(page).toHaveURL('/login');
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
    await expect(page.locator('.notification-panel')).toBeHidden();
    await page.getByRole('button', {name: '0 pending notifications'}).click();
    await expect(page.getByText('No pending notifications.')).toBeVisible();
});

async function swipeNotification(page, item, dx, dy = 0, cancel = false) {
    const box = await item.boundingBox();
    const x = box.x + box.width * 0.7;
    const y = box.y + box.height / 2;
    const session = await page.context().newCDPSession(page);
    await session.send('Input.dispatchTouchEvent', {type: 'touchStart', touchPoints: [{x, y}]});
    for (let step = 1; step <= 5; step++) {
        await session.send('Input.dispatchTouchEvent', {type: 'touchMove', touchPoints: [{x: x + dx * step / 5, y: y + dy * step / 5}]});
    }
    await session.send('Input.dispatchTouchEvent', {type: cancel ? 'touchCancel' : 'touchEnd', touchPoints: []});
    await session.detach();
}

function swipeNotifications(count = 2) {
    const date = madridDate();
    return Array.from({length: count}, (_, index) => ({
        id: 80 + index, type: 'MOOD', title: `Swipe reminder ${index + 1}`,
        message: 'A long notification message that should stay inside the panel at every viewport width.',
        reminderDate: date, availableAt: `${date}T07:30:00+02:00`,
        actionUrl: '/?checkInReminder=mood'
    }));
}

async function expectCenteredNotifications(page) {
    const panel = page.locator('.notification-panel');
    await expect(panel).toBeVisible();
    await expect.poll(async () => panel.evaluate(element => {
        const box = element.getBoundingClientRect();
        return Math.abs(box.left + box.width / 2 - document.documentElement.clientWidth / 2);
    })).toBeLessThan(2);
    const geometry = await panel.evaluate(element => {
        const box = element.getBoundingClientRect();
        const bell = document.querySelector('.notification-bell').getBoundingClientRect();
        return {left: box.left, right: box.right, bottom: box.bottom, top: box.top,
            viewport: document.documentElement.clientWidth, height: innerHeight,
            headerBottom: document.querySelector('.app-menubar').getBoundingClientRect().bottom,
            arrow: box.left + parseFloat(getComputedStyle(element, '::before').left), bell: bell.left + bell.width / 2};
    });
    expect(Math.abs(geometry.arrow - geometry.bell)).toBeLessThan(2);
    expect(geometry.left).toBeGreaterThanOrEqual(15);
    expect(geometry.right).toBeLessThanOrEqual(geometry.viewport - 15);
    expect(geometry.top).toBeGreaterThanOrEqual(geometry.headerBottom);
    expect(geometry.bottom).toBeLessThanOrEqual(geometry.height - 14);
}

for (const width of [390, 575, 640, 960, 1280]) {
    test(`notification swipe dismisses and closes the last item at ${width}px`, async ({page}) => {
        await page.setViewportSize({width, height: 896});
        await mockRoutineReminderHome(page, [], {initialNotifications: swipeNotifications()});
        await openSpaRoute(page, '/');
        await page.getByRole('button', {name: '2 pending notifications'}).click();
        const panel = page.locator('.notification-panel');
        const items = page.locator('.notification-item');
        await expectCenteredNotifications(page);
        await swipeNotification(page, items.first(), -100);
        await expect(items).toHaveCount(1);
        await expect(panel).toBeVisible();
        await expectCenteredNotifications(page);
        await expect(page.getByRole('button', {name: '1 pending notification', exact: true})).toBeVisible();
        expect(new URL(page.url()).search).toBe('');
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        await page.screenshot({path: test.info().outputPath('notification-swipe.png'), animations: 'disabled'});
        await swipeNotification(page, items.first(), -100);
        await expect(panel).toBeHidden();
        await expect(page.getByRole('button', {name: '0 pending notifications'})).toBeVisible();
    });
}

test('notification dismissal slides before collapsing and survives a pending refresh', async ({page}) => {
    await mockRoutineReminderHome(page, [], {initialNotifications: swipeNotifications()});
    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '2 pending notifications'}).click();
    const items = page.locator('.notification-item');
    await expect(page.locator('.notification-panel')).not.toHaveClass(/p-overlaypanel-enter-active/);
    const initial = await items.first().boundingBox();
    const nextInitial = await items.nth(1).boundingBox();
    await page.evaluate(() => {
        window.notificationAnimations = [];
        const animate = Element.prototype.animate;
        Element.prototype.animate = function (...args) {
            const animation = animate.apply(this, args);
            if (this.classList.contains('notification-item')) {
                animation.pause();
                window.notificationAnimations.push(animation);
            }
            return animation;
        };
    });
    let finishRefresh;
    await page.route('**/api/notifications/pending', async route => {
        await new Promise(resolve => { finishRefresh = resolve; });
        await route.fulfill({json: swipeNotifications()});
    }, {times: 1});
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect.poll(() => Boolean(finishRefresh)).toBe(true);
    let finishRequest;
    await page.route('**/api/notifications/80/dismiss', async route => {
        await new Promise(resolve => { finishRequest = resolve; });
        await route.fallback();
    });
    await swipeNotification(page, items.first(), -100);
    await expect.poll(() => Boolean(finishRequest)).toBe(true);
    await page.evaluate(() => { window.notificationAnimations[0].currentTime = 100; });
    const sliding = await items.first().boundingBox();
    expect(sliding.x).toBeLessThan(initial.x - 100);
    expect(sliding.height).toBeCloseTo(initial.height, 0);
    await expect(items.first()).toHaveCSS('opacity', /0\./);
    await page.screenshot({path: test.info().outputPath('notification-mid-slide.png')});
    await page.evaluate(() => window.notificationAnimations[0].finish());
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(items).toHaveCount(2);
    expect((await items.nth(1).boundingBox()).y).toBeCloseTo(nextInitial.y, 0);
    finishRequest();
    await page.waitForFunction(() => window.notificationAnimations.length === 2);
    const staleRefresh = page.waitForResponse('**/api/notifications/pending');
    finishRefresh();
    await staleRefresh;
    await page.evaluate(() => { window.notificationAnimations[1].currentTime = 80; });
    const collapsing = await items.first().boundingBox();
    expect(collapsing.height).toBeGreaterThan(0);
    expect(collapsing.height).toBeLessThan(initial.height);
    const nextCollapsing = await items.nth(1).boundingBox();
    expect(nextCollapsing.y).toBeLessThan(nextInitial.y);
    expect(nextCollapsing.y).toBeGreaterThan(initial.y);
    await page.screenshot({path: test.info().outputPath('notification-mid-collapse.png')});
    await page.evaluate(() => window.notificationAnimations[1].finish());
    await expect(items).toHaveCount(1);
    await expect(page.getByRole('button', {name: '1 pending notification', exact: true})).toBeVisible();
    await swipeNotification(page, items.first(), -100);
    await page.waitForFunction(() => window.notificationAnimations.length === 3);
    await expect(page.locator('.notification-panel')).toBeVisible();
    await page.evaluate(() => window.notificationAnimations[2].finish());
    await page.waitForFunction(() => window.notificationAnimations.length === 4);
    await expect(page.locator('.notification-panel')).toBeVisible();
    await page.evaluate(() => window.notificationAnimations[3].finish());
    await expect(page.locator('.notification-panel')).toBeHidden();
});

test('notification reduced motion dismisses without animated movement', async ({page}) => {
    await page.emulateMedia({reducedMotion: 'reduce'});
    await mockRoutineReminderHome(page, [], {initialNotifications: swipeNotifications(1)});
    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '1 pending notification', exact: true}).click();
    await page.evaluate(() => {
        window.notificationDurations = [];
        const animate = Element.prototype.animate;
        Element.prototype.animate = function (...args) {
            const animation = animate.apply(this, args);
            if (this.classList.contains('notification-item')) window.notificationDurations.push(animation.effect.getTiming().duration);
            return animation;
        };
    });
    await swipeNotification(page, page.locator('.notification-item'), -100);
    await expect(page.locator('.notification-panel')).toBeHidden();
    expect(await page.evaluate(() => window.notificationDurations)).toEqual([0, 0]);
});

test('notification gestures preserve scrolling and reject short rightward and cancelled swipes', async ({page}) => {
    await mockRoutineReminderHome(page, [], {initialNotifications: swipeNotifications(8)});
    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '8 pending notifications'}).click();
    const items = page.locator('.notification-item');
    let dismissals = 0;
    page.on('request', request => { if (request.url().endsWith('/dismiss')) dismissals++; });
    await swipeNotification(page, items.first(), -25);
    await swipeNotification(page, items.first(), 50);
    await swipeNotification(page, items.first(), -100, 0, true);
    await expect(items).toHaveCount(8);
    await expect(items.first()).toHaveCSS('transform', 'none');
    await swipeNotification(page, items.nth(2), 0, -100);
    await expect.poll(() => page.locator('.notification-list').evaluate(list => list.scrollTop)).toBeGreaterThan(0);
    expect(dismissals).toBe(0);
    expect(new URL(page.url()).search).toBe('');
    await page.locator('.notification-list').evaluate(list => { list.scrollTop = 0; });
    await items.first().locator('.notification-dismiss').focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('button', {name: '7 pending notifications'})).toBeVisible();
});

test('notification last x dismissal closes the panel and failures allow retry', async ({page}) => {
    await mockRoutineReminderHome(page, [], {initialNotifications: swipeNotifications(1)});
    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '1 pending notification', exact: true}).click();
    await page.route('**/api/notifications/80/dismiss', route => route.fulfill({status: 500, body: 'Dismissal failed'}));
    await swipeNotification(page, page.locator('.notification-item'), -100);
    await expect(page.getByText('Notification dismissal failed', {exact: true})).toBeVisible();
    await expect(page.locator('.notification-panel')).toBeVisible();
    await expect(page.locator('.notification-item')).toHaveCount(1);
    await expect(page.locator('.notification-item')).toHaveCSS('transform', 'none');
    await expect(page.locator('.notification-item')).toHaveCSS('opacity', '1');
    await page.unroute('**/api/notifications/80/dismiss');
    await page.getByRole('button', {name: 'Dismiss Swipe reminder 1', exact: true}).click();
    await expect(page.locator('.notification-panel')).toBeHidden();
    await expect(page.getByRole('button', {name: '0 pending notifications'})).toBeVisible();
});

test('notification dismiss all failure keeps the panel open for retry', async ({page}) => {
    await mockRoutineReminderHome(page, [], {initialNotifications: swipeNotifications()});
    await openSpaRoute(page, '/');
    await page.getByRole('button', {name: '2 pending notifications'}).click();
    await page.route('**/api/notifications/dismiss-all', route => route.fulfill({status: 500, body: 'Dismissal failed'}));
    await page.getByRole('button', {name: 'Dismiss all'}).click();
    await expect(page.getByText('Notification dismissal failed', {exact: true})).toBeVisible();
    await expect(page.locator('.notification-item')).toHaveCount(2);
    await page.unroute('**/api/notifications/dismiss-all');
    await page.getByRole('button', {name: 'Dismiss all'}).click();
    await expect(page.locator('.notification-panel')).toBeHidden();
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

    const reminderDialog = page.getByRole('dialog', {name: 'Measurement reminder'});
    await reminderDialog.getByRole('button', {name: 'Record'}).click();
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
    await expect(page).toHaveURL('/');
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

    const reminderDialog = page.getByRole('dialog', {name: 'Measurement reminder'});
    await reminderDialog.getByRole('button', {name: 'Record'}).click();
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
    await expect(page).toHaveURL('/');
});

test('measurement reminder can change its date and time', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {
        today: date,
        initialWeights: [reminderWeight('2026-08-15')],
        initialNotifications: [{id: 22, type: 'WEIGHT', title: 'Weight reminder', message: 'Record your weight.', reminderDate: date, availableAt: `${date}T05:00:00+02:00`}]
    });

    await openSpaRoute(page, `/?measurementReminder=weight&measurementReminderDate=${date}&notificationId=22`);
    const dialog = page.getByRole('dialog', {name: 'Measurement reminder'});
    await dialog.getByRole('button', {name: 'Change date and time'}).click();
    await dialog.locator('#measurement-reschedule-date').fill('2026-08-23');
    await dialog.locator('#measurement-reschedule-time').fill('08:00');
    const request = page.waitForRequest(item => item.url().endsWith('/api/notifications/22/reschedule') && item.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();

    expect((await request).postDataJSON()).toEqual({date: '2026-08-23', time: '08:00'});
    await expect(page.getByText('Notification rescheduled')).toBeVisible();
    await expect(dialog).not.toBeVisible();
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
    const reminderDialog = page.getByRole('dialog', {name: 'Measurement reminder'});
    await reminderDialog.getByRole('button', {name: 'Record'}).click();
    const dialog = page.getByRole('dialog', {name: 'Weight'});
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', {name: 'Cancel'}).click();

    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', {name: '1 pending notification'})).toBeVisible();
    await expect(page).toHaveURL('/');
});

test('completed measurement reminder opens Home without a form', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {today: date});

    await openSpaRoute(page, `/?measurementReminder=weight&measurementReminderDate=${date}`);

    await expect(page.getByRole('dialog', {name: 'Weight'})).toHaveCount(0);
    await expect(page).toHaveURL('/');
});

test('login preserves a pending measurement reminder', async ({page}) => {
    const date = '2026-08-22';
    await page.clock.setFixedTime(new Date('2026-08-22T03:30:00Z'));
    await mockRoutineReminderHome(page, [], {requiresLogin: true, today: date, initialWeights: [reminderWeight('2026-08-15')]});

    await openSpaRoute(page, `/?measurementReminder=weight&measurementReminderDate=${date}`);
    await expect(page).toHaveURL(`/login?measurementReminder=weight&measurementReminderDate=${date}`);
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page.getByRole('dialog', {name: 'Measurement reminder'})).toBeVisible();
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
        await expect(page).toHaveURL('/');
    });
}

test('login preserves a pending routine reminder', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [routine(1, 'Morning weigh-in', '07:30:00')], {requiresLogin: true});

    await openSpaRoute(page, `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
    await expect(page).toHaveURL(`/login?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
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
    await expect(page).toHaveURL('/');
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
    await expect(page).toHaveURL('/');
});

test('back reminder opens an optional pain episode form', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, []);

    await openSpaRoute(page, `/?checkInReminder=back&checkInPeriod=EVENING&checkInReminderDate=${date}`);
    const reminder = page.getByRole('dialog', {name: 'Evening back reminder'});
    await expect(reminder).toContainText('Record how your back feels, including no pain.');
    await reminder.getByRole('button', {name: 'Record'}).click();
    const dialog = page.getByRole('dialog', {name: 'Back check-in'});
    await expect(dialog.locator('#period')).toContainText('Evening');
    await expect(dialog.locator('#period')).toHaveClass(/p-disabled/);
    await expect(dialog.locator('label').filter({hasText: /^Time$/})).toHaveCount(0);
    await dialog.getByRole('button', {name: 'Lower Right'}).click();
    await dialog.locator('#severity').click();
    await page.getByRole('option', {name: 'Moderate', exact: true}).click();
    const saveRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();

    expect((await saveRequest).postDataJSON()).toMatchObject({date, period: 'EVENING', region: 'LOWER', side: 'RIGHT', severity: 'MODERATE'});
    await expect(page).toHaveURL('/');
});

for (const reminder of [
    {name: 'stale', date: '2026-01-01', moods: []},
    {name: 'completed', date: madridDate(), moods: [{id: 1, date: madridDate(), period: 'MORNING', value: 4, note: null}]}
]) {
    test(`${reminder.name} mood reminder opens Home without a modal`, async ({page}) => {
        await mockRoutineReminderHome(page, [], {initialMoods: reminder.moods});

        await openSpaRoute(page, `/?checkInReminder=mood&checkInPeriod=MORNING&checkInReminderDate=${reminder.date}`);

        await expect(page.getByRole('dialog', {name: 'Morning mood reminder'})).toHaveCount(0);
        await expect(page).toHaveURL('/');
    });
}

test('completed back reminder opens Home without a modal', async ({page}) => {
    const date = madridDate();
    const episode = {id: 1, date, period: 'MORNING', time: '08:12:00', timeFormat: '08:12', region: 'LOWER', side: 'LEFT', severity: 'MILD', note: null};
    await mockRoutineReminderHome(page, [], {initialBackPainEpisodes: [episode]});

    await openSpaRoute(page, `/?checkInReminder=back&checkInPeriod=MORNING&checkInReminderDate=${date}`);

    await expect(page.getByRole('dialog', {name: 'Morning back reminder'})).toHaveCount(0);
    await expect(page).toHaveURL('/');
});

test('login preserves a pending mood reminder', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [], {requiresLogin: true});

    await openSpaRoute(page, `/?checkInReminder=mood&checkInPeriod=EVENING&checkInReminderDate=${date}`);
    await expect(page).toHaveURL(`/login?checkInReminder=mood&checkInPeriod=EVENING&checkInReminderDate=${date}`);
    await page.getByRole('button', {name: 'Sign in with Google'}).click();

    await expect(page.getByRole('dialog', {name: 'Evening mood reminder'})).toBeVisible();
});

test('back pain history saves an episode without a save-and-add action', async ({page}) => {
    await mockAuthenticatedBackPainEpisodes(page);
    await openSpaRoute(page, '/back');

    await page.getByRole('button', {name: 'Add check-in'}).click();
    const dialog = page.getByRole('dialog', {name: 'Back check-in'});
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

for (const width of [393, 575, 640, 960, 1280]) {
    test(`pain-free back check-in saves and edits at ${width}px`, async ({page}) => {
        await page.setViewportSize({width, height: 950});
        await mockAuthenticatedBackPainEpisodes(page);
        await openSpaRoute(page, '/back');
        await page.getByRole('button', {name: 'Add check-in'}).click();
        const dialog = page.getByRole('dialog', {name: 'Back check-in'});
        await dialog.locator('#period').click();
        await page.getByRole('option', {name: 'Morning', exact: true}).click();
        await dialog.getByRole('button', {name: 'Lower Left', exact: true}).click();
        await dialog.locator('#severity').click();
        await page.getByRole('option', {name: 'No pain', exact: true}).click();
        await expect(dialog.getByRole('group', {name: 'Pain location'})).toHaveCount(0);
        await dialog.locator('#note').fill('My back is fine');
        const bounds = await dialog.boundingBox();
        expect(bounds.x).toBeGreaterThanOrEqual(0);
        expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
        await page.screenshot({path: test.info().outputPath(`back-no-pain-${width}.png`), fullPage: true});
        const createRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes') && request.method() === 'POST');
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await createRequest).postDataJSON()).toMatchObject({period: 'MORNING', severity: 'NONE', region: null, side: null, note: 'My back is fine'});
        const row = page.locator('tbody tr').first();
        await expect(row).toContainText('No pain');
        await expect(row).toContainText('Not applicable');
        await row.getByRole('button', {name: 'Edit'}).click();
        await expect(dialog.locator('#severity')).toContainText('No pain');
        await dialog.locator('#severity').click();
        await page.getByRole('option', {name: 'Mild', exact: true}).click();
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog).toContainText('Choose one pain location.');
        await dialog.getByRole('button', {name: 'Upper Right', exact: true}).click();
        const updateRequest = page.waitForRequest(request => request.url().endsWith('/api/back-pain-episodes/1') && request.method() === 'PUT');
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await updateRequest).postDataJSON()).toMatchObject({severity: 'MILD', region: 'UPPER', side: 'RIGHT'});
        await expect(row).toContainText('Mild');
        page.once('dialog', dialog => dialog.accept());
        await row.getByRole('button', {name: 'Delete'}).click();
        await expect(row).not.toContainText('Mild');
    });
}

test('pain-free back check-in completes the reminder', async ({page}) => {
    const date = madridDate();
    await mockRoutineReminderHome(page, [], {initialBackPainEpisodes: [{id: 1, date, period: 'MORNING', severity: 'NONE', region: null, side: null, note: null}]});
    await openSpaRoute(page, `/?checkInReminder=back&checkInPeriod=MORNING&checkInReminderDate=${date}`);
    await expect(page.getByRole('dialog', {name: 'Morning back reminder'})).toHaveCount(0);
    await expect(page).toHaveURL('/');
});

test('dashboard shows explicit no pain with the existing zero-pain summary', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12', {backPainEpisodes: [{id: 1, date: '2026-08-12', period: 'MORNING', severity: 'NONE', region: null, side: null, note: null}]});
    await openSpaRoute(page, '/');
    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Back', exact: true}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel.locator('.back-pain-summary-value').first()).toHaveText('None');
    await expect(panel.locator('.back-pain-episodes')).toContainText('No pain');
    await expect(panel.locator('.back-pain-episodes')).toContainText('Not applicable');
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
        {tab: 'Back', button: 'Add check-in', buttonIndex: 0, dialog: 'Back check-in'},
        {tab: 'Sleep', button: 'New', buttonIndex: 0, dialog: 'Sleep'},
        {tab: 'Mood', button: 'New', buttonIndex: 0, dialog: 'Mood'},
        {tab: 'Nutrition', button: 'New', buttonIndex: 0, dialog: 'Meal'},
        {tab: 'Workout', button: 'Add session', buttonIndex: 0, dialog: 'Workout'}
    ];

    for (const scenario of scenarios) {
        await tabs.getByRole('tab', {name: scenario.tab}).click();
        const activePanel = tabs.locator('.p-tabview-panel:visible');
        if (scenario.tab === 'Back') {
            await expect(activePanel.locator('.back-pain-summary-value').first()).toContainText('None');
        }
        await activePanel.getByRole('button', {name: scenario.button, exact: true}).nth(scenario.buttonIndex).click();
        const dialog = scenario.dialog === 'Meal' ? page.locator('#meal-form') : page.getByRole('dialog', {name: scenario.dialog});
        await expect(dialog).toBeVisible();
        await expect(dialog.locator('label').filter({hasText: /^Date$/})).toHaveCount(0);
        await expect(dialog.locator('.back-pain-date')).toHaveCount(0);
        await dialog.getByRole('button', {name: 'Cancel'}).click();
        await expect(dialog).not.toBeVisible();
    }
});

test('dashboard shows persisted ten-point meal scores for the selected date', async ({page}, testInfo) => {
    const initialMeals = [
        {id: 1, date: '2026-08-12', mealType: 'LUNCH', rating: 8},
        {id: 2, date: '2026-08-12', mealType: 'DINNER', rating: 9},
        {id: 3, date: '2026-08-12', mealType: 'SNACK', rating: null},
        {id: 4, date: '2026-08-11', mealType: 'LUNCH', rating: 2}
    ].map(meal => ({dateFormat: meal.date.split('-').reverse().join('/'), mealSequence: 1, calories: 500, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: [], ...meal}));
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals});
    let selectedDay = 12;
    await page.route('**/api/dashboard/retreat', route => {
        const date = `2026-08-${--selectedDay}`;
        return route.fulfill({json: {...dashboard, anchorDate: date, dailyStatus: dashboardDailyStatus(date)}});
    });
    await openSpaRoute(page, '/');
    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Nutrition'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel).toContainText('8.5 / 10 (2 rated meals)');
    await expect(panel.locator('.meal-entry').filter({hasText: 'Lunch'})).toContainText('8/10');
    await expect(panel.locator('.meal-entry').filter({hasText: 'Dinner'})).toContainText('9/10');
    await expect(panel.locator('.meal-entry').filter({hasText: 'Snack'})).not.toContainText('/10');
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expect(panel.getByText('8.5 / 10 (2 rated meals)', {exact: true})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        for (const row of await panel.locator('.meal-entry-main').all()) {
            const bounds = await row.evaluate(element => ({row: element.getBoundingClientRect().right, actions: element.querySelector('.meal-entry-actions').getBoundingClientRect().right, summary: element.querySelector('.meal-entry-summary').getBoundingClientRect().right, actionsLeft: element.querySelector('.meal-entry-actions').getBoundingClientRect().left}));
            expect(bounds.actions).toBeLessThanOrEqual(bounds.row);
            expect(bounds.summary).toBeLessThanOrEqual(bounds.actionsLeft);
        }
        if (width === 376 || width === 390 || width === 1280) await page.screenshot({path: testInfo.outputPath(`meal-ratings-${width}.png`), fullPage: true});
    }
    await page.reload();
    await tabs.getByRole('tab', {name: 'Nutrition'}).click();
    await expect(panel).toContainText('8.5 / 10 (2 rated meals)');
    await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
    await expect(panel).toContainText('2 / 10 (1 rated meal)');
    await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
    await expect(panel).toContainText('Not rated');
});

test('dashboard opens Coach with one prompt to rate every selected-date meal', async ({page, context}, testInfo) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route(coachOriginPattern, route => route.fulfill({body: '<title>Coach</title>'}));
    const selectedDate = '2026-08-12';
    const meals = [
        {id: 1, date: selectedDate, mealType: 'BREAKFAST', mealSequence: 1, calories: 320, rating: 8},
        {id: 2, date: selectedDate, mealType: 'LUNCH', mealSequence: 1, calories: 650, rating: null},
        {id: 3, date: selectedDate, mealType: 'DINNER', mealSequence: 1, calories: 540, rating: 7}
    ].map(meal => ({dateFormat: meal.date.split('-').reverse().join('/'), proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: [], ...meal}));
    await mockAuthenticatedDashboard(page, selectedDate, {initialMeals: meals});
    let selectedDay = 12;
    await page.route('**/api/dashboard/retreat', route => {
        const date = `2026-08-${--selectedDay}`;
        return route.fulfill({json: {...dashboard, anchorDate: date, dailyStatus: dashboardDailyStatus(date)}});
    });
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Nutrition'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    const rateAll = panel.getByRole('button', {name: 'Rate all meals'});
    const newMeal = panel.getByRole('button', {name: 'New', exact: true});
    const headerActions = panel.locator('.meal-panel-header .tab-panel-actions');
    await expect(rateAll).toBeVisible();
    await expectChatGptIcon(rateAll);
    await expect(newMeal).toBeVisible();

    for (const width of [320, 376, 390, 575, 576, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expect(rateAll).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        if (width <= 575) {
            await expect(headerActions).toHaveCSS('display', 'grid');
            for (const button of [rateAll, newMeal]) {
                expect((await button.boundingBox()).width).toBeCloseTo((await headerActions.boundingBox()).width, 0);
                const alignment = await button.evaluate(element => {
                    const icon = element.querySelector('.p-button-icon').getBoundingClientRect();
                    const label = element.querySelector('.p-button-label').getBoundingClientRect();
                    const bounds = element.getBoundingClientRect();
                    return {buttonCenter: bounds.left + bounds.width / 2, contentCenter: (icon.left + label.right) / 2};
                });
                expect(alignment.contentCenter).toBeCloseTo(alignment.buttonCenter, 0);
            }
        } else {
            await expect(headerActions).toHaveCSS('display', 'flex');
        }
        if (width <= 360) {
            for (const row of await panel.locator('.meal-entry-main').all()) {
                await expect(row).toHaveCSS('flex-direction', 'column');
                const layout = await row.evaluate(element => {
                    const main = element.getBoundingClientRect();
                    const summary = element.querySelector('.meal-entry-summary').getBoundingClientRect();
                    const actions = element.querySelector('.meal-entry-actions').getBoundingClientRect();
                    return {
                        main: {left: main.left, width: main.width},
                        summary: {left: summary.left, width: summary.width, bottom: summary.bottom},
                        actionsTop: actions.top
                    };
                });
                expect(layout.summary.left).toBeCloseTo(layout.main.left, 0);
                expect(layout.summary.width).toBeCloseTo(layout.main.width, 0);
                expect(layout.summary.bottom).toBeLessThanOrEqual(layout.actionsTop);
            }
        }
        if (width === 390) await expect(panel.locator('.meal-entry-main').first()).toHaveCSS('flex-direction', 'row');
        if ([320, 390, 575, 576, 1280].includes(width)) await panel.screenshot({path: testInfo.outputPath(`rate-all-meals-${width}.png`)});
    }

    const coachPagePromise = context.waitForEvent('page');
    await rateAll.click();
    const coachPage = await coachPagePromise;
    await expect(coachPage).toHaveTitle('Coach');
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('Rate all my meals recorded on 2026-08-12, including meals already rated. Check meals from this Saturday through 2026-08-12, my calorie targets and weekly-average cap, and my active coaching plan. Propose one integer score out of 10 and one improvement for each meal, in the returned meal order. Show the complete meal-by-meal list, then ask whether I want to save all the proposed ratings. Make no writes until I explicitly confirm the full list. Then save each meal\'s rating only. If any write fails, read back every meal, report saved and unsaved ratings accurately, and stop without rolling back or claiming full success.');
    await coachPage.close();

    await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
    await expect(rateAll).toHaveCount(0);
});

test('dashboard records meal calories and optional macronutrients', async ({page, context}, testInfo) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route(coachOriginPattern, route => route.fulfill({body: '<title>Coach</title>'}));
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Nutrition'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel.locator('.meal-total')).toContainText('Total:');
    await expect(panel.locator('.meal-total')).toContainText('0 kcal');
    await expect(panel.locator('.meal-total-macros')).toHaveCount(0);
    await panel.getByRole('button', {name: 'New', exact: true}).click();
    let dialog = page.locator('#meal-form');
    await dialog.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Lunch', exact: true}).click();
    await dialog.getByRole('button', {name: 'On plan · 925 kcal'}).click();
    await dialog.getByLabel('Protein (g)').fill('42.5');
    await dialog.getByLabel('Carbohydrates (g)').fill('80.25');
    await dialog.getByLabel('Fat (g)').fill('20');
    const lunchRequest = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();

    expect((await lunchRequest).postDataJSON()).toEqual({
        date: '2026-08-12',
        mealType: 'LUNCH',
        calories: 925,
        proteinGrams: 42.5,
        carbohydrateGrams: 80.25,
        fatGrams: 20,
        mealTime: null,
        durationMinutes: 30,
        notes: null,
        dishes: []
    });
    const lunch = panel.locator('.meal-entry').filter({hasText: 'Lunch'});
    await expect(lunch).toContainText('925 kcal');
    await expect(lunch.locator('.meal-entry-main')).not.toContainText('P 42.5 g');
    await expect(lunch.locator('.meal-entry-macros')).toHaveText('P 42.5 g (25%) · C 80.25 g (48%) · F 20 g (27%)');
    await expect(panel.locator('.meal-total')).toContainText('925 kcal');
    await expect(panel.locator('.meal-total-macros')).toHaveText('P 42.5 g (25%) · C 80.25 g (48%) · F 20 g (27%)');
    expect((await lunch.locator('.meal-entry-summary span').boundingBox()).x).toBe((await panel.locator('.meal-total span').boundingBox()).x);
    const coachPagePromise = context.waitForEvent('page');
    const rateMeal = lunch.getByRole('button', {name: 'Rate meal'});
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectChatGptIcon(rateMeal);
        await lunch.screenshot({path: testInfo.outputPath(`chatgpt-home-meal-${width}.png`)});
    }
    await rateMeal.click();
    const coachPage = await coachPagePromise;
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('Rate my Lunch on 2026-08-12 out of 10. Check the meals from this Saturday through that date, my calorie targets and weekly-average cap, and my active coaching plan. Suggest one improvement, present the proposed score for my confirmation, then save that rating to the meal after I confirm the exact score.');
    await coachPage.close();

    for (const calories of [150, 250]) {
        await panel.getByRole('button', {name: 'New', exact: true}).click();
        dialog = page.locator('#meal-form');
        await dialog.locator('#meal-type').click();
        await page.getByRole('option', {name: 'Snack', exact: true}).click();
        await dialog.getByLabel('Calories').fill(String(calories));
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    }

    await expect(panel.locator('.meal-entry').filter({hasText: 'Snack 1'})).toContainText('150 kcal');
    const snack2 = panel.locator('.meal-entry').filter({hasText: 'Snack 2'});
    await expect(snack2).toContainText('250 kcal');
    await expect(panel.locator('.meal-total')).toContainText('1325 kcal');
    await expect(panel.locator('.meal-total-macros')).toHaveText('P 42.5 g (25%) · C 80.25 g (48%) · F 20 g (27%)');

    await lunch.getByRole('button', {name: 'Edit'}).click();
    dialog = page.locator('#meal-form');
    await dialog.getByLabel('Protein (g)').fill('45');
    const updateRequest = page.waitForRequest(request => /\/api\/meals\/\d+$/.test(request.url()) && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await updateRequest).postDataJSON().proteinGrams).toBe(45);
    await expect(lunch).toContainText('P 45 g (26%)');
    await expect(panel.locator('.meal-total-macros')).toHaveText('P 45 g (26%) · C 80.25 g (47%) · F 20 g (26%)');

    page.once('dialog', confirmation => confirmation.accept());
    const deleteRequest = page.waitForRequest(request => /\/api\/meals\/\d+$/.test(request.url()) && request.method() === 'DELETE');
    await snack2.getByRole('button', {name: 'Delete'}).click();
    await deleteRequest;
    await expect(panel.locator('.meal-entry').filter({hasText: 'Snack 2'})).toHaveCount(0);
    await expect(panel.locator('.meal-total')).toContainText('1075 kcal');

    await lunch.getByRole('button', {name: 'Edit'}).click();
    dialog = page.locator('#meal-form');
    await dialog.getByRole('button', {name: 'Add food'}).click();
    const dishDialog = page.getByRole('dialog', {name: 'Food', exact: true});
    await dishDialog.getByLabel('Food', {exact: true}).fill('Chicken');
    await enterFoodNutrients(dishDialog);
    await dishDialog.getByLabel('Calories', {exact: true}).fill('500');
    await dishDialog.getByRole('button', {name: 'Apply'}).click();
    await dialog.getByRole('button', {name: 'Add food'}).click();
    await dishDialog.getByLabel('Food', {exact: true}).fill('Rice');
    await enterFoodNutrients(dishDialog);
    await dishDialog.getByLabel('Calories', {exact: true}).fill('300');
    await dishDialog.getByRole('button', {name: 'Apply'}).click();
    await expect(dialog.locator('.meal-dish-row')).toHaveCount(2);
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 800});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }
    const dishRequest = page.waitForRequest(request => /\/api\/meals\/\d+$/.test(request.url()) && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await dishRequest).postDataJSON().dishes).toEqual([
        {name: 'Chicken', calories: 500, proteinGrams: 45, carbohydrateGrams: 80.25, fatGrams: 20, quantity: 1, unit: 'SERVING', fruit: false, reference: {quantity: 1, calories: 500, proteinGrams: 45, carbohydrateGrams: 80.25, fatGrams: 20}},
        {name: 'Rice', calories: 300, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, quantity: 1, unit: 'SERVING', fruit: false, reference: {quantity: 1, calories: 300, proteinGrams: null, carbohydrateGrams: null, fatGrams: null}}
    ].map(foodWithNutrients));
    await expect(lunch.locator('.meal-entry-dishes')).toContainText('Chicken · 500 kcalRice · 300 kcal');
});

test('formats meal macros and calorie summaries without floating tails', async ({page}) => {
    const selectedMeal = {
        id: 1,
        date: '2026-08-12',
        dateFormat: '12/08/2026',
        mealType: 'LUNCH',
        mealSequence: 1,
        calories: 43,
        proteinGrams: 47.38999999999999,
        carbohydrateGrams: 10.5,
        fatGrams: 0,
        source: 'MANUAL',
        dishes: [{id: 1, name: 'Measured lunch', calories: 43, proteinGrams: 47.38999999999999, carbohydrateGrams: 10.5, fatGrams: 0, quantity: 1, unit: 'SERVING'}]
    };
    const initialMeals = [
        selectedMeal,
        {id: 2, date: '2026-08-12', dateFormat: '12/08/2026', mealType: 'SNACK', mealSequence: 1, calories: 60, proteinGrams: 0.11, carbohydrateGrams: 1.5, fatGrams: 2, source: 'MANUAL', dishes: []},
        {id: 3, date: '2026-08-11', dateFormat: '11/08/2026', mealType: 'DINNER', mealSequence: 1, calories: 100, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: []},
        {id: 4, date: '2026-08-05', dateFormat: '05/08/2026', mealType: 'DINNER', mealSequence: 1, calories: 2213, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: []},
        {id: 5, date: '2026-07-12', dateFormat: '12/07/2026', mealType: 'DINNER', mealSequence: 1, calories: 806, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: []},
        {id: 6, date: '2026-07-11', dateFormat: '11/07/2026', mealType: 'DINNER', mealSequence: 1, calories: 806, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: []}
    ];
    await mockAuthenticatedDashboard(page, '2026-08-12', {
        initialMeals,
        profileResponse: {...profile, weeklyAverageCalorieMaximum: 1571}
    });
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Nutrition'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel.locator('.meal-entry').filter({hasText: 'Lunch'}).locator('.meal-entry-macros')).toHaveText('P 47.39 g (82%) · C 10.5 g (18%) · F 0 g (0%)');
    await expect(panel.locator('.meal-total')).toContainText('103 kcal');
    await expect(panel.locator('.meal-total-macros')).toHaveText('P 47.5 g (74%) · C 12 g (19%) · F 2 g (7%)');
    await expect(panel.getByText('Previous Week Calories:').locator('..')).toContainText('2213 kcal');
    await expect(panel.getByText('Trend Calories:').locator('..')).toContainText('805.33 kcal -0.67 kcal');
    await expect(panel.getByText('Weekly Calories at Maximum:').locator('..')).toContainText('0 kcal');
    await expect(panel.getByText('Last Entry Calories:').locator('..')).toContainText('100 kcal');

    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expect(panel.locator('.meal-total-macros')).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }

    const rawMeals = await page.evaluate(() => fetch('/api/meals').then(response => response.json()));
    expect(rawMeals.find(meal => meal.id === 1).proteinGrams).toBe(47.38999999999999);

    await panel.locator('.meal-entry').filter({hasText: 'Lunch'}).getByRole('button', {name: 'Edit'}).click();
    await expect(page.locator('.meal-dish-summary small')).toHaveText('P 47.39 g · C 10.5 g · F 0 g');

    await openSpaRoute(page, '/calories');
    let rows = page.locator('.p-tabview-panel:visible tbody tr');
    await expect(rows.first()).toContainText('47.5 g · 74%');
    await expect(rows.first()).toContainText('12 g · 19%');
    await page.getByRole('tab', {name: 'Meals'}).click();
    rows = page.locator('.p-tabview-panel:visible tbody tr');
    await expect(rows.first()).toContainText('47.39 g · 82%');
});

test('dashboard shows an active automatic fasting period only for today in its header', async ({page}) => {
    await page.clock.install({time: new Date('2026-08-12T12:00:00+02:00')});
    const startTime = '2026-08-12T11:58:00+02:00';
    await mockAuthenticatedDashboard(page, '2026-08-12', {
        dashboardResponse: {
            ...dashboard,
            activeFastingPeriod: {id: 1, startTime, endTime: null, notes: null, source: 'AUTOMATIC'}
        }
    });
    await openSpaRoute(page, '/');

    const status = page.locator('.dashboard-fasting-status');
    await expect(status).toContainText('Fasting');
    await expect(status.locator('.dashboard-fasting-duration')).toHaveText(/0h [2-3]m/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await page.setViewportSize({width: 1280, height: 800});
    await expect(status).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    const activeFastingPeriod = {id: 1, startTime, endTime: null, notes: null, source: 'AUTOMATIC'};
    await page.route('**/api/dashboard/retreat', route => route.fulfill({json: {
        ...dashboard, anchorDate: '2026-08-11', dailyStatus: dashboardDailyStatus('2026-08-11'), activeFastingPeriod
    }}));
    await page.route('**/api/dashboard/advance', route => route.fulfill({json: {...dashboard, activeFastingPeriod}}));
    await page.getByRole('button', {name: 'Previous Day', exact: true}).click();
    await expect(page.locator('.dashboard-date-value')).toHaveText('11/08/2026');
    await expect(status).toHaveCount(0);
    await page.setViewportSize({width: 390, height: 844});
    await expect(status).toHaveCount(0);
    await page.getByRole('button', {name: 'New Day', exact: true}).click();
    await expect(page.locator('.dashboard-date-value')).toHaveText('12/08/2026');
    await expect(status).toBeVisible();
});

test('dashboard hides the live fasting status for a future selected date', async ({page}) => {
    await page.clock.install({time: new Date('2026-08-12T12:00:00+02:00')});
    await mockAuthenticatedDashboard(page, '2026-08-13', {dashboardResponse: {
        ...dashboard, anchorDate: '2026-08-13', dailyStatus: dashboardDailyStatus('2026-08-13'),
        activeFastingPeriod: {id: 1, startTime: '2026-08-12T11:58:00+02:00', endTime: null, notes: null, source: 'AUTOMATIC'}
    }});
    await openSpaRoute(page, '/');
    await expect(page.locator('.dashboard-date-value')).toHaveText('13/08/2026');
    await expect(page.locator('.dashboard-fasting-status')).toHaveCount(0);
});

test('dashboard hides the fasting status when no automatic fast is active', async ({page}) => {
    await page.clock.install({time: new Date('2026-08-12T12:00:00+02:00')});
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await openSpaRoute(page, '/');

    await expect(page.locator('.dashboard-fasting-status')).toHaveCount(0);
});

test.describe('dashboard 16-hour fasting target', () => {
    test.use({timezoneId: 'Europe/Madrid'});

    async function openActiveFast(page, now, selectedDate, startTime) {
        await page.clock.install({time: new Date(now)});
        await mockAuthenticatedDashboard(page, selectedDate, {dashboardResponse: {
            ...dashboard,
            anchorDate: selectedDate,
            dailyStatus: dashboardDailyStatus(selectedDate),
            activeFastingPeriod: {id: 1, startTime, endTime: null, notes: null, source: 'AUTOMATIC'}
        }});
        await openSpaRoute(page, '/');
        return page.locator('.dashboard-fasting-status');
    }

    test('shows the same-day target in local time without changing elapsed duration', async ({page}, testInfo) => {
        const status = await openActiveFast(page, '2026-08-12T12:00:00+02:00', '2026-08-12', '2026-08-12T02:00:00+02:00');
        await expect(status.locator('.dashboard-fasting-duration')).toHaveText('10h 0m');
        await expect(status.locator('.dashboard-fasting-target')).toHaveText('16-hour target: 18:00');

        for (const width of [390, 1280]) {
            await page.setViewportSize({width, height: 900});
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
            await page.screenshot({path: testInfo.outputPath(`fasting-target-${width}.png`), fullPage: true});
        }
    });

    test('shows the next-day local target time', async ({page}) => {
        const status = await openActiveFast(page, '2026-08-12T12:00:00+02:00', '2026-08-12', '2026-08-12T11:58:00+02:00');
        await expect(status.locator('.dashboard-fasting-target')).toHaveText('16-hour target: tomorrow 03:58');
    });

    test('includes the local target date after the target day passes', async ({page}) => {
        const status = await openActiveFast(page, '2026-08-14T04:00:00+02:00', '2026-08-14', '2026-08-12T11:58:00+02:00');
        await expect(status.locator('.dashboard-fasting-target')).toHaveText('16-hour target reached: on 13/08/2026 at 03:58');
    });

    test('marks the target reached at the exact 16-hour threshold and remains reached after it', async ({page}) => {
        const status = await openActiveFast(page, '2026-08-13T03:58:00+02:00', '2026-08-13', '2026-08-12T11:58:00+02:00');
        await expect(status.locator('.dashboard-fasting-duration')).toHaveText('16h 0m');
        await expect(status.locator('.dashboard-fasting-target')).toHaveText('16-hour target reached: 03:58');

        await page.clock.fastForward(2 * 60 * 1000);
        await expect(status.locator('.dashboard-fasting-duration')).toHaveText('16h 2m');
        await expect(status.locator('.dashboard-fasting-target')).toHaveText('16-hour target reached: 03:58');
    });
});

test('dashboard workout panel shows its saved Coach assessment summary', async ({page}, testInfo) => {
    const workout = {
        id: 7,
        workoutDate: '2026-08-12',
        workoutDateFormat: '12/08/2026',
        note: 'Upper body',
        startTime: '18:30', durationMinutes: 60, warmUpMinutes: 10, trainingMinutes: 45, stretchingMinutes: 5,
        assessment: {
            goalAlignmentScore: 8,
            estimatedTrainingDemandScore: 7,
            rationale: 'Strong alignment with the current strength goal.',
            strength: 'Consistent compound work.',
            improvement: 'Add one pulling set.',
            nextWorkoutAction: 'Repeat with controlled progression.',
            goalSnapshot: 'Improve upper-body strength',
            createdAt: '2026-08-12T18:30:00Z',
            updatedAt: '2026-08-12T18:30:00Z'
        },
        lines: [{exerciseId: 1, exerciseName: 'Bench press', trackingMode: 'REPS', position: 0, sets: [{position: 0, repetitions: 8, weight: 60}], intervals: []}]
    };
    const previousWorkout = {
        ...workout,
        id: 8,
        workoutDate: '2026-08-05',
        workoutDateFormat: '05/08/2026',
        assessment: {...workout.assessment, goalAlignmentScore: 5, estimatedTrainingDemandScore: 4}
    };
    await mockAuthenticatedDashboard(page, '2026-08-12', {
        initialWorkouts: [workout, previousWorkout],
        coachMetricsResponse: {
            selectedWeek: {startDate: '2026-08-08', endDate: '2026-08-14', reflections: [], workouts: [], totals: {workoutCount: 1, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}},
            previousWeek: {startDate: '2026-08-01', endDate: '2026-08-07', reflections: [], workouts: [], totals: {workoutCount: 4, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}},
            selectedWeekToDate: {startDate: '2026-08-08', endDate: '2026-08-12', reflections: [], workouts: [], totals: {workoutCount: 1, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}},
            previousWeekToDate: {startDate: '2026-08-01', endDate: '2026-08-05', reflections: [], workouts: [], totals: {workoutCount: 1, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}},
            planProgressTrend: {latestScore: 8, previousScore: 6, currentThirtyDayAverage: 8, previousThirtyDayAverage: 6},
            reflections: [],
            workouts: [],
            weeklyWorkouts: []
        }
    });
    await page.setViewportSize({width: 393, height: 851});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Workout'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await expect(panel.locator('.daily-workout-assessment').first().getByRole('button', {name: 'Rate day', exact: true})).toBeVisible();
    await expect(panel.locator('.daily-workout-assessment').first()).toContainText('Goal alignment: 8/10');
    await expect(panel.getByText('8/10', {exact: true})).toBeVisible();
    await panel.locator('.workout-status-details > summary').click();
    await expect(panel.getByText('This Saturday–Wednesday', {exact: true})).toBeVisible();
    await expect(panel.getByText('-3', {exact: true})).toHaveCount(0);
    await expect(panel.getByText('Goal 8 · Demand 7')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await expect(panel.locator('.workout-session-details').first()).not.toHaveAttribute('open', '');
    await expect(panel.locator('.workout-timing').first()).toBeHidden();
    await panel.screenshot({path: testInfo.outputPath('workout-timing-dashboard-393.png')});
    await page.setViewportSize({width: 1280, height: 800});
    await panel.screenshot({path: testInfo.outputPath('workout-timing-dashboard-1280.png')});
    const rate = panel.locator('.daily-workout-assessment').first().getByRole('button', {name: 'Rate day', exact: true});
    const edit = panel.locator('.workout-session').first().getByRole('button', {name: 'Edit'});
    await expect(rate).toBeVisible();
    expect((await rate.boundingBox()).y).toBeLessThan((await edit.boundingBox()).y);
    await edit.click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    await expect(editor.getByLabel('Start time (optional)', {exact: true})).toHaveValue('18:30');
    await expect(editor.locator('#workout-duration')).toHaveValue('60');
    await expect(editor.getByLabel('Break down duration', {exact: true})).toBeChecked();
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
});

test('dashboard keeps workout ratings in Workout and separates workout charts from Coach', async ({page}) => {
    const workout = {
        id: 7,
        workoutDate: '2026-08-12',
        workoutDateFormat: '12/08/2026',
        note: 'Upper body',
        assessment: {
            goalAlignmentScore: 8,
            estimatedTrainingDemandScore: 7,
            rationale: 'Strong alignment with the current strength goal.',
            strength: 'Consistent compound work.',
            improvement: 'Add one pulling set.',
            nextWorkoutAction: 'Repeat with controlled progression.',
            goalSnapshot: 'Improve upper-body strength',
            createdAt: '2026-08-12T18:30:00Z',
            updatedAt: '2026-08-12T18:30:00Z'
        },
        lines: [{exerciseId: 1, exerciseName: 'Bench press', trackingMode: 'REPS', position: 0, sets: [{position: 0, repetitions: 8, weight: 60}], intervals: []}]
    };
    await mockAuthenticatedDashboard(page, '2026-08-12', {
        initialWorkouts: [workout],
        coachMetricsResponse: {
            selectedWeek: {startDate: '2026-08-08', endDate: '2026-08-14', reflections: [], workouts: [], totals: {workoutCount: 1, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}},
            previousWeek: {startDate: '2026-08-01', endDate: '2026-08-07', reflections: [], workouts: [], totals: {workoutCount: 0, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}},
            selectedWeekToDate: {startDate: '2026-08-08', endDate: '2026-08-12', reflections: [], workouts: [], totals: {workoutCount: 1, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}},
            previousWeekToDate: {startDate: '2026-08-01', endDate: '2026-08-05', reflections: [], workouts: [], totals: {workoutCount: 0, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}},
            planProgressTrend: {latestScore: 8, previousScore: 6, currentThirtyDayAverage: 8, previousThirtyDayAverage: 6},
            reflections: [],
            workouts: [{date: '2026-08-12', dateFormat: '12/08/2026', summary: 'Bench press', goalAlignmentScore: 8, estimatedTrainingDemandScore: 7, totals: {workoutCount: 1, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}}],
            weeklyWorkouts: [{startDate: '2026-08-08', endDate: '2026-08-14', totals: {workoutCount: 1, totalDurationSeconds: 0, totalDistanceKm: 0, totalCalories: 0, strengthVolumeKg: 0}}]
        }
    });
    await page.setViewportSize({width: 393, height: 851});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Workout'}).click();
    const workoutPanel = tabs.locator('.p-tabview-panel:visible');
    await workoutPanel.locator('.workout-status-details > summary').click();
    await expect(workoutPanel.locator('.daily-workout-assessment')).toContainText('Goal alignment');
    await expect(workoutPanel.locator('.daily-workout-assessment')).toContainText('8/10');
    await expect(workoutPanel.getByLabel('Workout status')).toContainText('This Saturday–Wednesday');
    await expect(workoutPanel.getByLabel('Weekly workouts')).toHaveCount(0);
    await expect(workoutPanel.getByText('Workout trends')).toHaveCount(0);
    await expect(page.locator('.week-status').getByText('Workouts', {exact: true})).toBeVisible();
    await expect(page.locator('.week-status').getByText('Previous week', {exact: true})).toHaveCount(0);

    await tabs.getByRole('tab', {name: 'Coach'}).click();
    const coachPanel = tabs.locator('.p-tabview-panel:visible');
    await expect(coachPanel.getByText('Reflection plan-progress ratings for this Saturday–Friday week.')).toBeVisible();
    await expect(coachPanel.getByLabel('Coach status')).toContainText('Plan progress:');
    await expect(coachPanel.getByLabel('Coach status')).toContainText('8/10');
    await expect(coachPanel.getByLabel('Coach status')).toContainText('+2/10');
    await expect(coachPanel.getByLabel('Coach status').getByText('+2/10')).toHaveClass(/good/);
    await expect(coachPanel.getByLabel('Coach status')).toContainText('Trend Plan Progress:');
    await expect(coachPanel.getByLabel('Coach status')).toContainText('8.0/10');
    for (const width of [393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 851});
        await coachPanel.screenshot({path: test.info().outputPath(`trend-labels-Coach-${width}.png`)});
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    }
    await expect(coachPanel.getByText('Workouts', {exact: true})).toHaveCount(0);
    await page.locator('#measures-chart').scrollIntoViewIfNeeded();
    const charts = page.locator('#measures-chart');
    await charts.getByRole('tab', {name: 'Workout'}).click();
    const workoutCharts = charts.locator('.p-tabview-panel:visible');
    await expect(workoutCharts.locator('canvas')).toHaveCount(10);
    await charts.getByRole('tab', {name: 'Coach'}).click();
    const coachCharts = charts.locator('.p-tabview-panel:visible');
    await expect(coachCharts.locator('canvas')).toHaveCount(0);
    await expect(coachCharts.getByText('No rated reflections in the selected period.')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('meal form and growl fit a mobile viewport', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await page.setViewportSize({width: 393, height: 851});
    await openSpaRoute(page, '/');

    const tabs = page.locator('.home-panels-tabs');
    await tabs.getByRole('tab', {name: 'Nutrition'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    await panel.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.locator('#meal-form');
    const fieldWidths = await dialog.evaluate(element => ({
        mealType: element.querySelector('.entry-dropdown').getBoundingClientRect().width,
        calories: element.querySelector('#calories').getBoundingClientRect().width
    }));
    expect(Math.abs(fieldWidths.mealType - fieldWidths.calories)).toBeLessThanOrEqual(1);
    await dialog.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Lunch', exact: true}).click();
    await expect(dialog.locator('#meal-type')).toHaveText('Lunch');
    await dialog.getByLabel('Calories').fill('500');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();

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

for (const width of [1280, 960, 760, 640, 575, 390, 376, 320]) {
    test(`reflection domain cards preserve history, partial and legacy views at ${width}px`, async ({page}, testInfo) => {
        const reflection = {
            reflectionDate: '2026-08-13', generatedAt: '2026-08-13T20:00:00Z', model: 'ChatGPT',
            title: 'Steady progress with limited evidence', summary: 'Recorded habits improved, while gaps limit comparisons.',
            planProgressScore: 7, planProgressRationale: 'Completed the agreed strength sessions consistently.',
            meals: {summary: 'Logged meals included varied vegetables and protein. Portions and some macros are missing, so balance remains uncertain.', nextAction: 'Record portions and available macros at your next lunch.'},
            workouts: {summary: 'Two comparable strength sessions were recorded. Recovery cannot be assessed from these records alone.', nextAction: 'Repeat the planned session and record the completed sets.'},
            positiveSignals: ['Mood improved across recorded days.'], watchouts: ['Bedtimes still varied.'], nextActions: ['Keep a regular bedtime.']
        };
        await mockAuthenticatedReflections(page, reflection);
        await page.setViewportSize({width, height: 1000});
        await openSpaRoute(page, '/reflections');
        const meals = page.getByRole('region', {name: 'Meals', exact: true});
        const workouts = page.getByRole('region', {name: 'Workouts', exact: true});
        await expect(meals.getByRole('heading', {name: 'Meals', level: 3})).toBeVisible();
        await expect(workouts).toContainText(reflection.workouts.summary);
        await expect(meals).toContainText(reflection.meals.nextAction);
        const mealBounds = await meals.boundingBox();
        const workoutBounds = await workouts.boundingBox();
        if (width > 760) {
            expect(Math.abs(mealBounds.y - workoutBounds.y)).toBeLessThanOrEqual(1);
            expect(Math.abs(mealBounds.width - workoutBounds.width)).toBeLessThanOrEqual(1);
            expect(workoutBounds.x).toBeGreaterThan(mealBounds.x + mealBounds.width);
        } else {
            expect(workoutBounds.y).toBeGreaterThan(mealBounds.y + mealBounds.height);
            expect(workoutBounds.x).toBe(mealBounds.x);
        }
        expect(mealBounds.y).toBeGreaterThan((await page.getByLabel('Plan progress rating').boundingBox()).y);
        expect((await page.locator('.insight-grid').boundingBox()).y).toBeGreaterThan(workoutBounds.y + workoutBounds.height);
        await expect(page.locator('.history-score')).toHaveText('7/10');
        await expect(page.getByRole('button', {name: 'Update in ChatGPT'})).toBeVisible();
        const assertNoOverflow = async () => expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await assertNoOverflow();
        const screenshot = async name => {
            if ([1280, 640, 390, 376, 320].includes(width)) await page.screenshot({path: testInfo.outputPath(`reflection-${name}-${width}.png`), fullPage: true});
        };
        await screenshot('both');

        delete reflection.meals;
        await page.getByRole('button', {name: 'Refresh reflection', exact: true}).click();
        await expect(meals).toHaveCount(0);
        await expect(workouts).toBeVisible();
        await screenshot('workouts-only');
        reflection.meals = {summary: 'M'.repeat(200), nextAction: 'A'.repeat(120)};
        reflection.workouts = null;
        await page.getByRole('button', {name: 'Refresh reflection', exact: true}).click();
        await expect(meals).toContainText(reflection.meals.summary);
        await expect(workouts).toHaveCount(0);
        await assertNoOverflow();
        await screenshot('meals-only-long');

        delete reflection.meals;
        delete reflection.workouts;
        delete reflection.planProgressScore;
        delete reflection.planProgressRationale;
        await page.getByRole('button', {name: 'Refresh reflection', exact: true}).click();
        await expect(page.locator('.domain-grid')).toHaveCount(0);
        await expect(page.getByLabel('Plan progress rating')).toHaveCount(0);
        await page.locator('.history-item').focus();
        await page.keyboard.press('Enter');
        await expect(page.getByRole('heading', {name: reflection.title})).toBeVisible();
        await expect(page.locator('.insight-card')).toHaveCount(3);
        await assertNoOverflow();
        await screenshot('legacy');
    });
}

test('reflection advice copies only a short natural Coach request', async ({page, context}, testInfo) => {
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
    await expectChatGptIcon(page.getByRole('button', {name: 'Update in ChatGPT'}));

    const coachPagePromise = context.waitForEvent('page');
    const advice = page.getByRole('button', {name: 'Ask the Coach for current advice'});
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectChatGptIcon(advice);
        await page.screenshot({path: testInfo.outputPath(`chatgpt-reflection-result-${width}.png`), fullPage: true});
    }
    await advice.click();
    const coachPage = await coachPagePromise;
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('What should I do now and for the rest of today?');
    await coachPage.close();
});

test('reflection creation shows the ChatGPT icon and opens the Coach with a dated prompt', async ({page, context}, testInfo) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await mockAuthenticatedReflections(page, null, true);
    await context.route(coachOriginPattern, route => route.fulfill({body: '<title>Coach</title>'}));
    await openSpaRoute(page, '/reflections');

    const create = page.getByRole('button', {name: 'Create in ChatGPT'});
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectChatGptIcon(create);
        await page.screenshot({path: testInfo.outputPath(`chatgpt-reflection-create-${width}.png`), fullPage: true});
    }
    const coachPagePromise = context.waitForEvent('page');
    await create.click();
    const coachPage = await coachPagePromise;
    await expect(coachPage).toHaveURL(coachUrl);
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('Generate or update and save the reflection for 2026-08-13 using the latest context.');
    await coachPage.close();
});

test('dashboard reflection copies its dated prompt and opens the private Coach', async ({page, context}, testInfo) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await mockAuthenticatedDashboard(page, '2026-08-12', {
        dashboardResponse: {...dashboard, lastCompletedDashboardDate: '2026-08-12'}
    });
    await context.route('https://chatgpt.test/**', route => route.fulfill({
        contentType: 'text/html',
        body: '<title>Weight Control Coach</title>'
    }));
    await openSpaRoute(page, '/');

    const coachPagePromise = context.waitForEvent('page');
    const reflection = page.getByRole('button', {name: 'Reflection'});
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectChatGptIcon(reflection);
        await page.locator('.dashboard-date-header').screenshot({path: testInfo.outputPath(`chatgpt-home-reflection-${width}.png`)});
    }
    await reflection.click();
    const coachPage = await coachPagePromise;

    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('Generate or update and save the reflection for 2026-08-12 using the latest context.');
    await expect(coachPage).toHaveURL('https://chatgpt.test/g/weight-control-coach');
    await expect(page).toHaveURL('/');
    await coachPage.close();
});

test('nutrition history summarizes macros and manages meals and fasting periods', async ({page, context}, testInfo) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route(coachOriginPattern, route => route.fulfill({body: '<title>Coach</title>'}));
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [
        {id: 1, date: '2026-08-12', dateFormat: '12/08/2026', mealType: 'LUNCH', mealSequence: 1, mealTime: '13:15:00', durationMinutes: 30, calories: 925, proteinGrams: 42.5, carbohydrateGrams: 80.25, fatGrams: 20, notes: 'Chicken and rice', rating: 8, source: 'MANUAL'},
        {id: 2, date: '2026-08-12', dateFormat: '12/08/2026', mealType: 'SNACK', mealSequence: 1, mealTime: null, calories: 150, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, notes: null, rating: null, source: 'MANUAL'},
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
    await expect(page.locator('.p-tabview-panel:visible').getByRole('columnheader', {name: 'Rating'})).toBeVisible();
    await expect(rows.nth(0)).toContainText('Lunch');
    await expect(rows.nth(0)).toContainText('925 kcal');
    await expect(rows.nth(0)).toContainText('8/10');
    await expect(rows.nth(0)).toContainText('42.5 g');
    await expect(rows.nth(0)).toContainText('13:15');
    await expect(rows.nth(0)).toContainText('30 min');
    await expect(rows.nth(0)).toContainText('Chicken and rice');
    await expect(rows.nth(0)).toContainText('Manual');
    await expect(rows.nth(0)).toContainText('42.5 g · 25%');
    await expect(rows.nth(0)).toContainText('80.25 g · 48%');
    await expect(rows.nth(0)).toContainText('20 g · 27%');
    const coachPagePromise = context.waitForEvent('page');
    const rateMeal = rows.nth(0).getByRole('button', {name: 'Rate meal'});
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectChatGptIcon(rateMeal);
        await rateMeal.screenshot({path: testInfo.outputPath(`chatgpt-nutrition-rate-${width}.png`)});
    }
    await rateMeal.click();
    const coachPage = await coachPagePromise;
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('Rate my Lunch on 2026-08-12 out of 10. Check the meals from this Saturday through that date, my calorie targets and weekly-average cap, and my active coaching plan. Suggest one improvement, present the proposed score for my confirmation, then save that rating to the meal after I confirm the exact score.');
    await coachPage.close();
    await expect(rows.nth(1)).toContainText('Snack 1');
    await expect(rows.nth(1)).toContainText('150 kcal');
    await expect(rows.nth(1)).toContainText('—');

    await openSpaRoute(page, '/calories?tab=meals');
    await expect(page.getByRole('tab', {name: 'Meals'})).toHaveAttribute('aria-selected', 'true');
    rows = page.locator('.p-tabview-panel:visible tbody tr');
    await expect(rows.nth(0)).toContainText('8/10');
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

test('saving a fasting record uses the mutation envelope and celebrates the achievement', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12', {
        fastingAchievements: [{eventKey: 'fasting-record', metric: 'FASTING_DURATION_MAXIMUM'}]
    });
    await openSpaRoute(page, '/calories');
    await page.getByRole('tab', {name: 'Fasting periods'}).click();
    await page.locator('.p-tabview-panel:visible').getByRole('button', {name: 'New'}).click();
    const dialog = page.getByRole('dialog', {name: 'Fasting Period'});
    await dialog.getByLabel('Notes (optional)').fill('Record fast');

    const createRequest = page.waitForRequest(request => request.url().endsWith('/api/fasting-periods') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();
    await createRequest;

    await expect(page.locator('.p-datatable', {hasText: 'Manual fasting periods'})).toContainText('Record fast');
    await expect(page.locator('.win-celebration-title')).toHaveText('WIN');
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

test('meal dish quantities preserve references, drafts and errors', async ({page}, testInfo) => {
    const initialDish = {name: 'Oat flakes with a deliberately long descriptive label', calories: 101, proteinGrams: 1.01, carbohydrateGrams: null, fatGrams: 0, vitaminDMicrograms: 2.01, omega3Milligrams: 100.01, magnesiumMilligrams: 20.01, quantity: 100, unit: 'GRAM', reference: {vitaminDMicrograms: 2.01, omega3Milligrams: 100.01, magnesiumMilligrams: 20.01, quantity: 100, calories: 101, proteinGrams: 1.01, carbohydrateGrams: null, fatGrams: 0}};
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [{id: 1, date: '2026-08-12', mealType: 'LUNCH', mealSequence: 1, mealTime: '13:00:00', durationMinutes: 30, calories: 101, proteinGrams: 1.01, carbohydrateGrams: null, fatGrams: 0, source: 'MANUAL', dishes: [initialDish]}]});
    await openSpaRoute(page, '/meals/1/edit?from=history');
    const form = page.locator('#meal-form');
    const dish = page.getByRole('dialog', {name: 'Food', exact: true});
    await form.getByRole('button', {name: 'Edit food 1', exact: true}).click();
    await dish.getByLabel('Quantity', {exact: true}).fill('50');
    await dish.getByLabel('Quantity', {exact: true}).press('Tab');
    await expect(dish.getByLabel('Calories', {exact: true})).toHaveValue('51');
    await expect(dish.getByLabel('Protein (g)', {exact: true})).toHaveValue('0.51');
    await expect(dish.getByLabel('Vitamin D (µg)', {exact: true})).toHaveValue('1.01');
    await expect(dish.getByLabel('Omega-3 (mg)', {exact: true})).toHaveValue('50.01');
    await expect(dish.getByLabel('Magnesium (mg)', {exact: true})).toHaveValue('10.01');
    await dish.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(form.locator('.meal-dish-row')).toContainText('100 g · 101 kcal');
    await form.getByRole('button', {name: 'Edit food 1', exact: true}).click();
    await dish.getByLabel('Quantity', {exact: true}).fill('50');
    await dish.getByRole('button', {name: 'Apply', exact: true}).click();
    await expect(form.locator('.meal-dish-row')).toContainText('50 g · 51 kcal');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await page.screenshot({path: testInfo.outputPath(`meal-page-${width}.png`), fullPage: true});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await form.getByRole('button', {name: 'Edit food 1', exact: true}).click();
        await page.screenshot({path: testInfo.outputPath(`dish-dialog-${width}.png`), animations: 'disabled'});
        expect(await dish.evaluate(element => element.getBoundingClientRect().right <= innerWidth)).toBe(true);
        await dish.getByRole('button', {name: 'Cancel', exact: true}).click();
    }
    await page.route('**/api/meals/1', route => route.fulfill({status: 500, body: 'Save failed'}), {times: 1});
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(form.getByRole('alert')).toContainText('Your changes are still here');
    await expect(form.locator('.meal-dish-row')).toContainText('50 g · 51 kcal');
    page.once('dialog', dialog => dialog.dismiss());
    await form.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(form).toBeVisible();
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(form).not.toBeVisible();
    await page.getByRole('button', {name: 'Edit meal', exact: true}).click();
    await form.getByRole('button', {name: 'Edit food 1', exact: true}).click();
    await dish.getByLabel('Quantity', {exact: true}).fill('100');
    await dish.getByLabel('Quantity', {exact: true}).press('Tab');
    await expect(dish.getByLabel('Calories', {exact: true})).toHaveValue('101');
    await expect(dish.getByLabel('Protein (g)', {exact: true})).toHaveValue('1.01');
    await expect(dish.getByLabel('Vitamin D (µg)', {exact: true})).toHaveValue('2.01');
    await expect(dish.getByLabel('Nutrient source', {exact: true})).toHaveValue('Test composition');
    await dish.getByLabel('Calories', {exact: true}).fill('200');
    await dish.getByLabel('Quantity', {exact: true}).fill('50');
    await dish.getByLabel('Quantity', {exact: true}).press('Tab');
    await expect(dish.getByLabel('Calories', {exact: true})).toHaveValue('100');
    await dish.getByRole('button', {name: 'Apply', exact: true}).click();
    await form.getByRole('button', {name: 'Remove food 1', exact: true}).click();
    await expect(form.getByLabel('Calories', {exact: true})).toHaveValue('100');
});

test('meal preloads show the latest 14 matching earlier entries with dish titles', async ({page}, testInfo) => {
    const makeMeal = (id, date, mealType = 'LUNCH', dishes = []) => ({id, date, mealType, mealSequence: 1, mealTime: '13:00:00', durationMinutes: 35, calories: 200, proteinGrams: 10, carbohydrateGrams: 20, fatGrams: 5, source: 'MANUAL', notes: 'Source notes', dishes});
    const makeDish = name => ({name, calories: 200, proteinGrams: 10, carbohydrateGrams: 20, fatGrams: 5, quantity: 150, unit: 'GRAM', reference: {quantity: 150, calories: 200, proteinGrams: 10, carbohydrateGrams: 20, fatGrams: 5}});
    const meals = Array.from({length: 16}, (_, i) => makeMeal(i + 1, `2026-08-${String(i + 1).padStart(2, '0')}`, 'LUNCH', [makeDish(`Dish ${i + 1}`)]));
    const longName = 'Lentils and vegetables with a very long descriptive dish name that wraps on mobile';
    meals[15].dishes = [makeDish(longName), makeDish('Rice'), makeDish('Fruit')];
    meals[14].dishes = [];
    meals.push(makeMeal(17, '2026-08-17', 'DINNER', [makeDish('Dinner dish')]), makeMeal(18, '2026-08-18', 'LUNCH', [makeDish('Same day')]), makeMeal(19, '2026-08-19', 'LUNCH', [makeDish('Later day')]));
    await mockAuthenticatedDashboard(page, '2026-08-18', {initialMeals: meals});
    await openSpaRoute(page, '/meals/new?date=2026-08-18');
    const form = page.locator('#meal-form');
    await expect(form.getByText('Choose a meal type to see previous meals.')).toBeVisible();
    // Use a date without an existing Lunch so it is available as a destination.
    await form.locator('#meal-date').fill('17/08/2026');
    await form.locator('#meal-date').press('Tab');
    await form.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Lunch', exact: true}).click();
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await form.locator('#reuse-meal').click();
        const options = page.getByRole('option');
        await expect(options).toHaveCount(14);
        await expect(options.first().locator('strong')).toHaveText(`${longName} + 2 foods`);
        await expect(options.first().locator('small')).toHaveText('16/08/2026');
        await expect(options.nth(1).locator('strong')).toHaveText('No foods');
        await expect(options.last().locator('strong')).toHaveText('Dish 3');
        await page.screenshot({path: testInfo.outputPath(`meal-preloads-${width}.png`), animations: 'disabled'});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await form.locator('#reuse-meal').press('Escape');
    }
    await form.locator('#reuse-meal').press('Space');
    await form.locator('#reuse-meal').press('ArrowDown');
    await form.locator('#reuse-meal').press('Home');
    await form.locator('#reuse-meal').press('Enter');
    await expect(form.locator('.meal-dish-row')).toHaveCount(3);
    await expect(form.locator('#meal-date')).toHaveValue('17/08/2026');
    await expect(form.getByLabel('Duration (minutes)')).toHaveText('35');
    await expect(form.getByLabel('Notes (optional)')).toHaveValue('Source notes');
    const create = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await create).postDataJSON()).toMatchObject({date: '2026-08-17', mealType: 'LUNCH', durationMinutes: 35, dishes: meals[15].dishes});
    await expect(form).not.toBeVisible();
    await openSpaRoute(page, '/meals/new?date=2026-08-20');
    await form.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Dinner', exact: true}).click();
    await form.locator('#reuse-meal').click();
    await expect(page.getByRole('option')).toHaveCount(1);
    await expect(page.getByRole('option')).toContainText('Dinner dish');
    await form.locator('#reuse-meal').press('Escape');
    await form.locator('#meal-date').fill('01/08/2026');
    await form.locator('#meal-date').press('Tab');
    await form.locator('#reuse-meal').click();
    await expect(page.getByRole('option', {name: 'No earlier meals of this type.', exact: true})).toBeVisible();
});

test('meal editor keeps its destination through login and shows missing meals', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12', {requiresLogin: true});
    await openSpaRoute(page, '/meals/new?from=dashboard&date=2026-08-12');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();
    await expect(page.locator('#meal-form')).toBeVisible();
    await expect(page).toHaveURL(/\/meals\/new\?from=dashboard&date=2026-08-12/);
    await page.locator('#meal-form').getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(page.getByRole('tab', {name: /^Nutrition/})).toHaveAttribute('aria-selected', 'true');
    await openSpaRoute(page, '/meals/999/edit');
    await expect(page.getByRole('alert')).toContainText('no longer exists');
});

for (const [type, duration] of [['Breakfast', 30], ['Lunch', 30], ['Dinner', 30], ['Snack', 5]]) {
    test(`default meal duration saves and reloads ${type}`, async ({page}) => {
        await mockAuthenticatedDashboard(page, '2026-08-12');
        await openSpaRoute(page, '/meals/new?date=2026-08-12');
        const form = page.locator('#meal-form');
        await form.locator('#meal-type').click();
        await page.getByRole('option', {name: type, exact: true}).click();
        await expect(form.getByLabel('Duration (minutes)')).toHaveText(String(duration));
        await form.getByLabel('Calories', {exact: true}).fill('100');
        const create = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
        await form.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await create).postDataJSON().durationMinutes).toBe(duration);
        await expect(form).not.toBeVisible();
        await openSpaRoute(page, '/meals/1/edit');
        await expect(form.getByLabel('Duration (minutes)')).toHaveText(String(duration));
    });
}

test('default meal duration follows type changes and preserves explicit values and clearing', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await openSpaRoute(page, '/meals/new?date=2026-08-12');
    const form = page.locator('#meal-form');
    const chooseType = async type => {
        await form.locator('#meal-type').click();
        await page.getByRole('option', {name: type, exact: true}).click();
    };
    await chooseType('Lunch');
    await expect(form.getByLabel('Duration (minutes)')).toHaveText('30');
    await chooseType('Snack');
    await expect(form.getByLabel('Duration (minutes)')).toHaveText('5');
    await chooseType('Dinner');
    await expect(form.getByLabel('Duration (minutes)')).toHaveText('30');
    await form.getByLabel('Duration (minutes)').click();
    const wheel = page.getByRole('listbox', {name: 'Duration in minutes'});
    await wheel.press('ArrowDown');
    await wheel.press('Enter');
    await expect(form.getByLabel('Duration (minutes)')).toHaveText('35');
    await chooseType('Snack');
    await expect(form.getByLabel('Duration (minutes)')).toHaveText('35');
    await form.getByLabel('Duration (minutes)').click();
    await page.getByRole('button', {name: 'Clear', exact: true}).click();
    await chooseType('Lunch');
    await expect(form.getByLabel('Duration (minutes)')).not.toHaveText(/\d/);
    await form.getByLabel('Calories', {exact: true}).fill('100');
    const create = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await create).postDataJSON().durationMinutes).toBeNull();
    await expect(form).not.toBeVisible();
});

test('default meal duration preserves null when editing and preloading', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [
        {id: 1, date: '2026-08-11', mealType: 'LUNCH', mealSequence: 1, mealTime: null, durationMinutes: null, calories: 500, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: []}
    ]});
    const form = page.locator('#meal-form');
    await openSpaRoute(page, '/meals/1/edit');
    await expect(form.getByLabel('Duration (minutes)')).not.toHaveText(/\d/);
    await form.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Snack', exact: true}).click();
    await expect(form.getByLabel('Duration (minutes)')).not.toHaveText(/\d/);
    page.once('dialog', dialog => dialog.accept());
    await form.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(form).not.toBeVisible();
    await openSpaRoute(page, '/meals/new?date=2026-08-12');
    await form.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Lunch', exact: true}).click();
    await form.locator('#reuse-meal').click();
    await page.getByRole('option').filter({hasText: 'No foods'}).click();
    await expect(form.getByLabel('Duration (minutes)')).not.toHaveText(/\d/);
    await form.locator('#meal-type').click();
    await page.getByRole('option', {name: 'Snack', exact: true}).click();
    await expect(form.getByLabel('Duration (minutes)')).not.toHaveText(/\d/);
    const create = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await create).postDataJSON().durationMinutes).toBeNull();
    await expect(form).not.toBeVisible();
});

for (const duration of [17, 150]) {
    test(`meal duration preserves ${duration} minutes when editing and preloading`, async ({page}) => {
        await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [
            {id: 1, date: '2026-08-11', mealType: 'LUNCH', mealSequence: 1, mealTime: '13:00:00', durationMinutes: duration, calories: 500, source: 'MANUAL', dishes: []}
        ]});
        await openSpaRoute(page, '/meals/1/edit');
        const form = page.locator('#meal-form');
        await expect(form.getByLabel('Duration (minutes)')).toHaveText(String(duration));
        await form.getByLabel('Duration (minutes)').press('Space');
        await expect(page.getByRole('option', {name: String(duration), exact: true})).toBeVisible();
        await page.getByRole('listbox', {name: 'Duration in minutes'}).press('Escape');
        const update = page.waitForRequest(request => /\/api\/meals\/1$/.test(request.url()) && request.method() === 'PUT');
        await form.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await update).postDataJSON().durationMinutes).toBe(duration);
        await expect(form).not.toBeVisible();
        await openSpaRoute(page, '/meals/new?date=2026-08-12');
        await form.locator('#meal-type').click();
        await page.getByRole('option', {name: 'Lunch', exact: true}).click();
        await form.locator('#reuse-meal').click();
        await page.getByRole('option').filter({hasText: 'No foods'}).click();
        await expect(form.getByLabel('Duration (minutes)')).toHaveText(String(duration));
        const create = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
        await form.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await create).postDataJSON().durationMinutes).toBe(duration);
        await expect(form).not.toBeVisible();
    });
}

for (const width of [390, 575, 640, 960, 1280]) {
    test(`meal duration supports validation, editing and reuse at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 950});
        await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [
            {id: 1, date: '2026-08-11', mealType: 'LUNCH', mealSequence: 1, mealTime: '13:00:00', durationMinutes: 30, calories: 500, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes: []}
        ]});
        await openSpaRoute(page, '/calories');
        await page.getByRole('tab', {name: 'Meals', exact: true}).click();
        await page.getByRole('button', {name: 'Edit meal'}).click();
        const dialog = page.locator('#meal-form');
        await expect(dialog.getByLabel('Duration (minutes)')).toHaveText('30');
        await dialog.getByLabel('Duration (minutes)').click();
        await page.getByRole('button', {name: 'Clear', exact: true}).click();
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog).toBeVisible();
        await expect(dialog.locator('.meal-timing .error').last()).not.toBeEmpty();
        await dialog.getByLabel('Duration (minutes)').click();
        await expect(page.getByRole('option')).toHaveText(['Not set', ...Array.from({length: 24}, (_, index) => String((index + 1) * 5))]);
        await page.screenshot({path: testInfo.outputPath(`meal-duration-options-${width}.png`), fullPage: true, animations: 'disabled'});
        const wheel = page.getByRole('listbox', {name: 'Duration in minutes'});
        await wheel.press('Home');
        for (let step = 0; step < 9; step++) await wheel.press('ArrowDown');
        await expect(page.getByRole('option', {name: '45', exact: true})).toHaveAttribute('aria-selected', 'true');
        await wheel.hover();
        await page.mouse.wheel(0, 44);
        await expect(page.getByRole('option', {name: '50', exact: true})).toHaveAttribute('aria-selected', 'true');
        await wheel.press('ArrowUp');
        await expect(page.getByRole('option', {name: '45', exact: true})).toHaveAttribute('aria-selected', 'true');
        await page.screenshot({path: testInfo.outputPath(`meal-duration-wheel-${width}.png`), fullPage: true, animations: 'disabled'});
        await page.getByRole('button', {name: 'Done', exact: true}).click();
        await expect(page.getByRole('dialog', {name: 'Meal duration', exact: true})).not.toBeVisible();
        await dialog.getByLabel('Duration (minutes)').press('Tab');
        await page.screenshot({path: testInfo.outputPath(`meal-duration-${width}.png`), fullPage: true, animations: 'disabled'});
        const layout = await dialog.locator('.meal-timing').evaluate(element => {
            const fields = [...element.children].map(child => child.getBoundingClientRect());
            return {first: {x: fields[0].x, y: fields[0].y, right: fields[0].right}, second: {x: fields[1].x, y: fields[1].y, right: fields[1].right}, width: document.documentElement.clientWidth, scrollWidth: document.documentElement.scrollWidth};
        });
        expect(layout.scrollWidth).toBeLessThanOrEqual(layout.width);
        expect(layout.second.right).toBeLessThanOrEqual(width);
        if (width <= 575) expect(layout.second.y).toBeGreaterThan(layout.first.y);
        else expect(layout.second.y).toBe(layout.first.y);
        const update = page.waitForRequest(request => /\/api\/meals\/1$/.test(request.url()) && request.method() === 'PUT');
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await update).postDataJSON().durationMinutes).toBe(45);
        await expect(dialog).not.toBeVisible();
        await expect(page.locator('.p-tabview-panel:visible tbody tr').first()).toContainText('45 min');
        await page.getByRole('button', {name: 'New', exact: true}).click();
        await expect(dialog.getByLabel('Duration (minutes)')).not.toHaveText(/\d/);
        await dialog.locator('#meal-type').click();
        await page.getByRole('option', {name: 'Lunch', exact: true}).click();
        await dialog.locator('#reuse-meal').click();
        await page.getByRole('option').filter({hasText: 'No foods'}).click();
        await expect(dialog.getByLabel('Duration (minutes)')).toHaveText('45');
        const create = page.waitForRequest(request => request.url().endsWith('/api/meals') && request.method() === 'POST');
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await create).postDataJSON().durationMinutes).toBe(45);
        await expect(dialog).not.toBeVisible();
    });
}


test('food autocomplete reuses unique latest foods with independent quantities', async ({page}, testInfo) => {
    const food = (name, calories) => ({name, calories, proteinGrams: 10, carbohydrateGrams: null, fatGrams: 0, quantity: 100, unit: 'GRAM', reference: {quantity: 100, calories, proteinGrams: 10, carbohydrateGrams: null, fatGrams: 0}});
    const meal = (id, date, dishes) => ({id, date, mealType: 'SNACK', mealSequence: id, mealTime: null, durationMinutes: null, calories: 200, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, source: 'MANUAL', dishes});
    const longName = 'Oat flakes with a deliberately long descriptive food name that wraps on mobile screens';
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [
        meal(1, '2026-08-10', [food(' oats ', 90), food(longName, 101)]),
        meal(2, '2026-08-11', [food('OATS', 150)]),
        meal(3, '2026-08-11', [food('Oats', 180), food('Oats', 200)]),
        meal(4, '2026-08-12', [])
    ]});
    await openSpaRoute(page, '/meals/4/edit?from=history');
    const form = page.locator('#meal-form');
    const input = form.getByLabel('Reuse a saved food', {exact: true});
    const dish = page.getByRole('dialog', {name: 'Food', exact: true});
    await input.fill('oAtS');
    await expect(page.getByRole('option')).toHaveCount(1);
    await expect(page.getByRole('option')).toContainText('200 kcal');
    await input.press('ArrowDown');
    await input.press('Enter');
    await expect(dish).toBeVisible();
    await dish.getByLabel('Quantity', {exact: true}).fill('50');
    await dish.getByLabel('Quantity', {exact: true}).press('Tab');
    await expect(dish.getByLabel('Calories', {exact: true})).toHaveValue('100');
    await dish.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(form.locator('.meal-dish-row')).toHaveCount(0);
    await input.fill('not a saved food');
    await expect(page.getByText('No matching foods', {exact: true})).toBeVisible();
    await input.fill('');
    await input.press('Tab');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await form.locator('.food-picker .p-autocomplete-dropdown').click();
        await expect(page.getByRole('option')).toHaveCount(2);
        await expect(page.getByRole('option').filter({hasText: longName})).toBeVisible();
        await expect(page.locator('.p-autocomplete-panel')).toHaveCSS('opacity', '1');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`food-autocomplete-${width}.png`), fullPage: true});
        await input.press('Escape');
    }
    await input.fill('oats');
    await expect(page.getByRole('option')).toHaveCount(1);
    await page.getByRole('option').click();
    await dish.getByLabel('Quantity', {exact: true}).fill('50');
    await dish.getByRole('button', {name: 'Apply', exact: true}).click();
    await expect(form.locator('.meal-dish-row')).toContainText('50 g · 100 kcal');
    const saved = page.waitForRequest(request => request.url().endsWith('/api/meals/4') && request.method() === 'PUT');
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    const payload = (await saved).postDataJSON();
    expect(payload.dishes[0]).toMatchObject({quantity: 50, calories: 100, proteinGrams: 5, carbohydrateGrams: null, fatGrams: 0, reference: {quantity: 100, calories: 200}});
    await expect(form).not.toBeVisible();
    await openSpaRoute(page, '/meals/3/edit?from=history');
    await expect(form.locator('.meal-dish-row').nth(1)).toContainText('100 g · 200 kcal');
});


test('reusable dishes keep meal foods independent and support recipe management', async ({page}, testInfo) => {
    const rice = {name: 'Rice with a long ingredient description for responsive layout checks', calories: 101, proteinGrams: 1.01, carbohydrateGrams: null, fatGrams: 0, quantity: 100, unit: 'GRAM', fruit: false, reference: {quantity: 100, calories: 101, proteinGrams: 1.01, carbohydrateGrams: null, fatGrams: 0}};
    const chicken = {...rice, name: 'Chicken', calories: 200, reference: {...rice.reference, calories: 200}};
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [{id: 1, date: '2026-08-12', mealType: 'SNACK', mealSequence: 1, mealTime: null, durationMinutes: null, calories: 301, proteinGrams: 2.02, carbohydrateGrams: null, fatGrams: 0, source: 'MANUAL', dishes: [rice, chicken]}]});
    let recipes = [];
    let failSave = true;
    const mealWrites = [];
    page.on('request', request => { if (/\/api\/meals(?:\/\d+)?$/.test(request.url()) && ['POST', 'PUT'].includes(request.method())) mealWrites.push(request.postDataJSON()); });
    await page.route('**/api/dishes**', route => {
        const request = route.request();
        const id = Number(new URL(request.url()).pathname.split('/')[3]);
        const reply = body => route.fulfill({contentType: 'application/json', body: JSON.stringify(body)});
        if (request.method() === 'GET') return reply(id ? recipes.find(recipe => recipe.id === id) : recipes);
        if (request.method() === 'DELETE') { recipes = recipes.filter(recipe => recipe.id !== id); return route.fulfill({status: 204}); }
        if (failSave) { failSave = false; return route.fulfill({status: 400, body: 'A dish with this name already exists.'}); }
        const recipe = {...request.postDataJSON(), id: id || 1};
        recipes = [...recipes.filter(item => item.id !== recipe.id), recipe];
        return reply(recipe);
    });
    await openSpaRoute(page, '/meals/1/edit?from=history');
    const form = page.locator('#meal-form');
    await expect(form.getByRole('button', {name: 'Save as dish', exact: true})).toBeDisabled();
    await form.locator('label[for="select-food-0"]').click();
    await expect(form.getByLabel('Select food 1', {exact: true})).toBeChecked();
    await form.locator('label[for="select-food-1"]').click();
    await form.getByRole('button', {name: 'Save as dish', exact: true}).click();
    const create = page.getByRole('dialog', {name: 'Save as dish', exact: true});
    await create.getByRole('button', {name: 'Cancel', exact: true}).click();
    expect(recipes).toHaveLength(0);
    await form.getByRole('button', {name: 'Save as dish', exact: true}).click();
    await create.getByLabel('Dish name', {exact: true}).fill('Chicken and rice');
    await create.getByLabel('Recipe makes (servings)', {exact: true}).fill('2');
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`recipe-create-${width}.png`), fullPage: true, animations: 'disabled'});
    }
    await create.getByRole('button', {name: 'Save dish', exact: true}).click();
    await expect(create.getByRole('alert')).toContainText('name already exists');
    await expect(create.getByLabel('Dish name', {exact: true})).toHaveValue('Chicken and rice');
    await create.getByRole('button', {name: 'Save dish', exact: true}).click();
    await expect(create).not.toBeVisible();
    expect(mealWrites).toHaveLength(0);
    expect(recipes[0].ingredients).toEqual([rice, chicken].map(foodWithNutrients));
    await expect(form.locator('.meal-dish-row')).toHaveCount(2);
    await form.getByLabel('Add dish', {exact: true}).fill('chicken');
    await page.getByRole('option', {name: 'Chicken and rice', exact: true}).click();
    const reuse = page.getByRole('dialog', {name: 'Add dish', exact: true});
    await expect(reuse.getByRole('status')).toContainText('151 kcal');
    await reuse.getByLabel('Servings to add', {exact: true}).fill('99999999.999');
    await reuse.getByLabel('Servings to add', {exact: true}).press('Tab');
    await expect(reuse.getByRole('alert')).toContainText('supported quantity range');
    await expect(reuse.getByRole('button', {name: 'Add foods', exact: true})).toBeDisabled();
    await reuse.getByLabel('Servings to add', {exact: true}).fill('0.5');
    await reuse.getByLabel('Servings to add', {exact: true}).press('Tab');
    await expect(reuse.getByRole('status')).toContainText('75 kcal');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`recipe-preview-${width}.png`), fullPage: true, animations: 'disabled'});
    }
    await reuse.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(form.locator('.meal-dish-row')).toHaveCount(2);
    await form.getByLabel('Add dish', {exact: true}).fill('rice');
    await page.getByRole('option', {name: 'Chicken and rice', exact: true}).click();
    await reuse.getByRole('button', {name: 'Add foods', exact: true}).click();
    await expect(form.locator('.meal-dish-row')).toHaveCount(4);
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(form).not.toBeVisible();
    expect(mealWrites[0].dishes.map(food => food.quantity)).toEqual([100, 100, 50, 50]);
    expect(mealWrites[0].calories).toBe(452);
    await openSpaRoute(page, '/calories?tab=dishes');
    await expect(page.getByRole('tab', {name: 'Dishes', exact: true})).toHaveAttribute('aria-selected', 'true');
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await expect(page.getByRole('tab', {name: 'Dishes', exact: true})).toBeInViewport({ratio: 0.95});
        await page.screenshot({path: testInfo.outputPath(`recipe-list-${width}.png`), fullPage: true, animations: 'disabled'});
    }
    await page.getByLabel('Search dishes', {exact: true}).fill('rice');
    await page.getByRole('button', {name: 'Edit dish', exact: true}).click();
    const editor = page.locator('.recipe-editor');
    await editor.getByRole('button', {name: 'Edit ingredient 1', exact: true}).click();
    const foodDialog = page.getByRole('dialog', {name: 'Food', exact: true});
    await foodDialog.getByLabel('Quantity', {exact: true}).fill('200');
    await foodDialog.getByRole('button', {name: 'Apply', exact: true}).click();
    await editor.getByRole('button', {name: 'Remove ingredient 2', exact: true}).click();
    await editor.getByLabel('Add a saved food', {exact: true}).fill('chicken');
    await page.getByRole('option').click();
    await foodDialog.getByRole('button', {name: 'Apply', exact: true}).click();
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`recipe-editor-${width}.png`), fullPage: true, animations: 'disabled'});
    }
    page.once('dialog', dialog => dialog.dismiss());
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(editor).toBeVisible();
    await editor.getByRole('button', {name: 'Save dish', exact: true}).click();
    await expect(page).toHaveURL(/tab=dishes/);
    expect(recipes[0].ingredients[0].quantity).toBe(200);
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', {name: 'Delete dish', exact: true}).click();
    await expect(page.getByText('No saved dishes.', {exact: true})).toBeVisible();
    await openSpaRoute(page, '/meals/1/edit?from=history');
    await expect(form.locator('.meal-dish-row')).toHaveCount(4);
    await expect(form.locator('.meal-dish-row').first()).toContainText('100 g · 101 kcal');
});


test('recipe editor retains its route through login', async ({page}) => {
    await mockAuthenticatedDashboard(page, '2026-08-12', {requiresLogin: true});
    await page.route('**/api/dishes/1', route => route.fulfill({contentType: 'application/json', body: JSON.stringify({id: 1, name: 'Rice', servings: 1, ingredients: [{name: 'Rice', calories: 100, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, quantity: 1, unit: 'SERVING', reference: {quantity: 1, calories: 100, proteinGrams: null, carbohydrateGrams: null, fatGrams: null}}]})}));
    await openSpaRoute(page, '/dishes/1/edit');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();
    await expect(page.getByLabel('Dish name', {exact: true})).toHaveValue('Rice');
    await expect(page).toHaveURL(/\/dishes\/1\/edit$/);
});


test('food catalog supports portion correction, CRUD, search, reuse and responsive layouts', async ({page}, testInfo) => {
    const oats = {name: 'Oats, 60 g', quantity: 1, unit: 'SERVING', calories: 206, proteinGrams: 8, carbohydrateGrams: 34, fatGrams: 4, reference: {quantity: 1, calories: 206, proteinGrams: 8, carbohydrateGrams: 34, fatGrams: 4}};
    const longName = 'Wholegrain oats with a very long food name that must wrap without overflowing the mobile page';
    const foods = [oats, ...Array.from({length: 11}, (_, i) => ({...oats, name: `Food ${String(i).padStart(2, '0')}`})), {...oats, name: longName}];
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [{id: 1, date: '2026-08-12', mealType: 'SNACK', mealSequence: 1, calories: 206, proteinGrams: 8, carbohydrateGrams: 34, fatGrams: 4, source: 'MANUAL', dishes: foods}]});
    await page.route('**/api/foods', route => route.fulfill({status: 500, body: 'Unavailable'}), {times: 1});
    await openSpaRoute(page, '/calories?tab=foods');
    const catalog = page.getByRole('region', {name: 'Foods', exact: true});
    await expect(catalog.getByRole('alert')).toContainText('Unable to load foods');
    await catalog.getByRole('button', {name: 'Retry', exact: true}).click();
    await expect(catalog.locator('tbody tr')).toHaveCount(10);
    await catalog.locator('.p-paginator-next').click();
    await expect(catalog.locator('tbody tr')).toHaveCount(3);
    await catalog.getByLabel('Search foods', {exact: true}).fill(' oAtS, ');
    await expect(catalog.locator('tbody tr')).toHaveCount(1);
    await catalog.getByRole('button', {name: 'Edit Oats, 60 g', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Food', exact: true});
    const toggle = dialog.getByRole('switch', {name: 'Scale nutrition with quantity'});
    await expect(toggle).toBeChecked();
    await dialog.locator('label[for="scale-nutrition"]').click();
    await expect(toggle).not.toBeChecked();
    await dialog.getByLabel('Quantity', {exact: true}).fill('60');
    await dialog.getByLabel('Quantity', {exact: true}).press('Tab');
    await dialog.getByLabel('Unit', {exact: true}).click();
    await page.getByRole('option', {name: 'g', exact: true}).click();
    await expect(page.locator('.p-dropdown-panel')).not.toBeVisible();
    await expect(dialog.getByLabel('Calories', {exact: true})).toHaveValue('206');
    await expect(dialog.getByLabel('Protein (g)', {exact: true})).toHaveValue('8');
    await expect(dialog.getByLabel('Carbohydrates (g)', {exact: true})).toHaveValue('34');
    await expect(dialog.getByLabel('Fat (g)', {exact: true})).toHaveValue('4');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`food-correction-${width}.png`), fullPage: true});
    }
    await page.route('**/api/foods/*', route => route.fulfill({status: 500, body: 'Save failed'}), {times: 1});
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog.getByRole('alert')).toContainText('Unable to save');
    await expect(dialog.getByLabel('Quantity', {exact: true})).toHaveValue('60');
    const saved = page.waitForRequest(request => /\/api\/foods\/\d+$/.test(request.url()) && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saved).postDataJSON()).toMatchObject({quantity: 60, unit: 'GRAM', calories: 206, reference: {quantity: 60, calories: 206}});
    await expect(dialog).not.toBeVisible();
    await catalog.getByRole('button', {name: 'Edit Oats, 60 g', exact: true}).click();
    await expect(toggle).toBeChecked();
    await dialog.getByLabel('Quantity', {exact: true}).fill('120');
    await dialog.getByLabel('Quantity', {exact: true}).press('Tab');
    await expect(dialog.getByLabel('Calories', {exact: true})).toHaveValue('412');
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    await catalog.getByLabel('Search foods', {exact: true}).fill('very long');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await expect(catalog.getByText(longName, {exact: true})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`food-catalog-${width}.png`), fullPage: true});
    }
    await catalog.getByLabel('Search foods', {exact: true}).fill('no matches');
    await expect(catalog.getByText('No matching foods.', {exact: true})).toBeVisible();
    await catalog.getByRole('button', {name: 'Add food', exact: true}).click();
    await dialog.getByLabel('Food', {exact: true}).fill('New food');
    await enterFoodNutrients(dialog);
    await dialog.getByLabel('Calories', {exact: true}).fill('100');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).not.toBeVisible();
    await catalog.getByLabel('Search foods', {exact: true}).fill('New food');
    await expect(catalog.getByRole('button', {name: 'Edit New food', exact: true})).toBeVisible();
    page.once('dialog', confirmation => confirmation.accept());
    await catalog.getByRole('button', {name: 'Delete New food', exact: true}).click();
    await expect(catalog.getByText('No matching foods.', {exact: true})).toBeVisible();
    await openSpaRoute(page, '/meals/1/edit?from=history');
    const form = page.locator('#meal-form');
    await expect(form.locator('.meal-dish-row').first()).toContainText('1 serving · 206 kcal');
    await form.getByLabel('Reuse a saved food', {exact: true}).fill('Oats,');
    await page.getByRole('option').click();
    await expect(dialog.getByLabel('Quantity', {exact: true})).toHaveValue('60');
    await expect(dialog.getByLabel('Calories', {exact: true})).toHaveValue('206');
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
});

test('food portion toggle preserves unknown macros and supports either edit order in recipes', async ({page}) => {
    await mockAuthenticatedDashboard(page);
    const food = {name: 'Oats', quantity: 1, unit: 'SERVING', calories: 206, proteinGrams: 8, carbohydrateGrams: null, fatGrams: 4, reference: {quantity: 1, calories: 206, proteinGrams: 8, carbohydrateGrams: null, fatGrams: 4}};
    await page.route('**/api/dishes/1', route => route.fulfill({json: {id: 1, name: 'Breakfast', servings: 1, ingredients: [foodWithNutrients(food)]}}));
    await openSpaRoute(page, '/dishes/1/edit');
    await page.getByRole('button', {name: 'Edit ingredient 1', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Food', exact: true});
    const toggle = dialog.getByRole('switch', {name: 'Scale nutrition with quantity'});
    await dialog.locator('label[for="scale-nutrition"]').click();
    await expect(toggle).not.toBeChecked();
    await dialog.getByLabel('Unit', {exact: true}).click();
    await page.getByRole('option', {name: 'g', exact: true}).click();
    await expect(page.locator('.p-dropdown-panel')).not.toBeVisible();
    await dialog.getByLabel('Quantity', {exact: true}).fill('60');
    await dialog.getByLabel('Quantity', {exact: true}).press('Tab');
    await expect(dialog.getByLabel('Calories', {exact: true})).toHaveValue('206');
    await expect(dialog.getByLabel('Carbohydrates (g)', {exact: true})).toHaveValue('');
    await toggle.focus();
    await toggle.press('Space');
    await expect(toggle).toBeChecked();
    for (const quantity of ['120', '60', '120']) {
        await dialog.getByLabel('Quantity', {exact: true}).fill(quantity);
        await dialog.getByLabel('Quantity', {exact: true}).press('Tab');
        await expect(dialog.getByLabel('Calories', {exact: true})).toHaveValue(quantity === '60' ? '206' : '412');
    }
    await dialog.getByLabel('Calories', {exact: true}).fill('400');
    await dialog.getByLabel('Calories', {exact: true}).press('Tab');
    await dialog.getByLabel('Quantity', {exact: true}).fill('60');
    await dialog.getByLabel('Quantity', {exact: true}).press('Tab');
    await expect(dialog.getByLabel('Calories', {exact: true})).toHaveValue('200');
    await dialog.getByRole('button', {name: 'Apply', exact: true}).click();
    await expect(page.locator('.ingredient-row').first()).toContainText('60 g');
    await page.getByRole('button', {name: 'Edit ingredient 1', exact: true}).click();
    await expect(toggle).toBeChecked();
    await expect(dialog.getByLabel('Calories', {exact: true})).toHaveValue('200');
});


test('decision reason dialog cancels shortcuts and retains failed saves without duplicate submissions', async ({page}) => {
    const entries = await mockAuthenticatedDashboard(page, '2026-08-11');
    await page.goto('/?decisionOutcome=MISS');
    await page.getByLabel('Reason (optional)').fill('Cancelled');
    await page.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(entries).toHaveLength(0);
    await page.reload();
    await expect(page.getByText('Dashboard Date')).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await page.getByRole('tab', {name: 'Wins', exact: true}).click();
    await page.getByRole('button', {name: 'MISS', exact: true}).click();
    await page.getByLabel('Reason (optional)').fill('Skipped my walk');
    let attempts = 0;
    await page.route('**/api/decision-outcomes', async route => {
        attempts++;
        await new Promise(resolve => setTimeout(resolve, 250));
        if (attempts === 1) return route.fulfill({status: 503, body: 'Please try again'});
        return route.fallback();
    });
    await page.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Saving…', exact: true})).toBeDisabled();
    await expect(page.getByRole('alert')).toContainText('Please try again');
    await expect(page.getByLabel('Reason (optional)')).toHaveValue('Skipped my walk');
    expect(entries).toHaveLength(0);
    await page.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(entries).toEqual([{date: '2026-08-11', outcome: 'MISS', reason: 'Skipped my walk'}]);
});

for (const width of [390, 393, 575, 640, 960, 1280]) {
    test(`decision history and reason dialog work at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockAuthenticatedDashboard(page, '2026-08-11');
        const longReason = 'Walked after lunch. '.repeat(15) + 'x'.repeat(120);
        const entries = Array.from({length: 12}, (_, i) => ({id: 12 - i, date: '2026-08-11', dateFormat: '11/08/2026', outcome: i % 2 ? 'MISS' : 'WIN', reason: i === 0 ? longReason : null}));
        let fail = true;
        await page.route('**/api/decision-outcomes**', async route => {
            if (route.request().method() === 'GET') return route.fulfill({json: entries});
            const entry = entries.find(entry => route.request().url().endsWith(`/${entry.id}/reason`));
            if (fail) { fail = false; return route.fulfill({status: 503, body: 'Please try again'}); }
            entry.reason = route.request().postDataJSON().reason;
            return route.fulfill({json: entry});
        });
        await page.goto('/');
        await page.getByRole('tab', {name: 'Wins', exact: true}).click();
        await page.getByRole('button', {name: 'History', exact: true}).click();
        await expect(page).toHaveURL('/wins');
        await expect(page.getByText(longReason, {exact: true})).toBeVisible();
        await expect(page.getByText('1 to 10 of 12')).toBeVisible();
        await page.screenshot({path: testInfo.outputPath(`history-${width}.png`), fullPage: true, animations: 'disabled'});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.getByRole('button', {name: 'Edit reason for WIN on 11/08/2026'}).first().click();
        await expect(page.getByLabel('Reason (optional)')).toHaveValue(longReason);
        await expect(page.getByLabel('Reason (optional)')).toHaveAttribute('maxlength', '500');
        await page.getByLabel('Reason (optional)').fill('Updated reason');
        await page.screenshot({path: testInfo.outputPath(`dialog-${width}.png`), animations: 'disabled'});
        const box = await page.getByRole('dialog').boundingBox();
        expect(box.x).toBeGreaterThanOrEqual(0);
        expect(box.x + box.width).toBeLessThanOrEqual(width);
        await page.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(page.getByRole('alert')).toContainText('Please try again');
        await expect(page.getByLabel('Reason (optional)')).toHaveValue('Updated reason');
        await page.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        await expect(page.getByText('Updated reason', {exact: true})).toBeVisible();
        await page.getByRole('button', {name: 'Edit reason for WIN on 11/08/2026'}).first().click();
        await page.getByLabel('Reason (optional)').fill('');
        await page.getByLabel('Reason (optional)').press('Tab');
        await expect(page.getByRole('button', {name: 'Save', exact: true})).toBeFocused();
        await page.keyboard.press('Enter');
        await expect(page.getByRole('dialog')).toHaveCount(0);
        expect(entries[0].reason).toBeNull();
        expect(entries[0].outcome).toBe('WIN');
        expect(entries[0].date).toBe('2026-08-11');
        await openSpaRoute(page, '/wins');
        await expect(page.getByText('1 to 10 of 12')).toBeVisible();
        await page.getByRole('button', {name: 'Next Page', exact: true}).click();
        await expect(page.getByText('11 to 12 of 12')).toBeVisible();
    });
}

for (const width of [393, 1280]) {
    test(`GPT notification opens its section and dismisses at ${width}px`, async ({page}) => {
        await page.setViewportSize({width, height: 900});
        await mockRoutineReminderHome(page, [], {
            initialNotifications: [{
                id: 99, type: 'GPT_ACTION', title: 'Weight Control Coach', message: 'Sleep saved',
                reminderDate: '2026-08-18', availableAt: '2026-08-18T21:45:00+02:00', actionUrl: '/sleep'
            }],
            today: madridDate()
        });
        await page.route('**/api/workouts/dashboard?*', route => route.fulfill({json: {
            currentWorkouts: [], previousWeekWorkouts: [], preloadWorkouts: [], recordEvents: [], days: []
        }}));
        await openSpaRoute(page, '/');
        await page.getByRole('button', {name: '1 pending notification'}).click();
        await expect(page.locator('.notification-bell .count-badge')).toHaveText('1');
        await expect(page.locator('.notification-bell .count-badge')).toHaveCSS('background-color', 'rgb(220, 53, 69)');
        await expect(page.locator('.notification-bell .count-badge')).toHaveCSS('color', 'rgb(255, 255, 255)');
        await expect(page.locator('.notification-item')).toContainText('Sleep saved');
        await expect(page.locator('.p-toast-message-error')).toHaveCount(0);
        await page.screenshot({path: test.info().outputPath('gpt-notification.png'), animations: 'disabled'});
        const panel = await page.locator('.notification-panel').boundingBox();
        expect(panel.x).toBeGreaterThanOrEqual(0);
        expect(panel.x + panel.width).toBeLessThanOrEqual(width);
        const dismissed = page.waitForRequest(request => request.url().endsWith('/api/notifications/99/dismiss') && request.method() === 'POST');
        await page.locator('.notification-content').filter({hasText: 'Sleep saved'}).click();
        await dismissed;
        await expect(page).toHaveURL(/\/sleep$/);
        await expect(page.getByRole('button', {name: '0 pending notifications'})).toBeVisible();
    });
}

const warningFixture = (id, type = 'RECOVERY_STRAIN') => ({
    id, type, status: 'ACTIVE', version: 0,
    content: {explanation: 'Possible accumulated strain; cause uncertain.', evidence: 'Sep 1–7: seven recorded nights with lower HRV and higher sleeping heart rate than the preceding baseline.', action: 'Prioritize consistent sleep.', reviewedDate: '2026-09-08', onsetDate: '2026-09-01'},
    createdAt: '2026-09-08T10:00:00Z', updatedAt: '2026-09-08T10:00:00Z', resolutionRationale: null
});

test('Sleep Coach warning stays current beside favorable historical trends and shares its dialog', async ({page}) => {
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {initialSleeps: sleepHistory(dashboard.anchorDate, 60)});
    let warnings = [warningFixture(1), warningFixture(2, 'SLEEP_DISRUPTION')];
    let overviewRequests = 0;
    await page.route('**/api/coach-warnings', route => {
        overviewRequests++;
        return route.fulfill({json: {active: warnings, hasHistory: true}});
    });
    await page.route('**/api/coach-warnings/2/revisions?*', route => route.fulfill({json: {items: [warningFixture(2, 'SLEEP_DISRUPTION')], page: 0, hasMore: false}}));
    await openSpaRoute(page, '/');
    const tabs = page.locator('.home-panels-tabs');
    await expect(page.getByRole('button', {name: 'Current Coach warnings: 2 warnings', exact: true})).toBeVisible();
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    const panel = tabs.locator('.p-tabview-panel:visible');
    const row = panel.locator('.sleep-coach-warning');
    await expect(panel.getByText('EXCELLENT (4/4)', {exact: true})).toBeVisible();
    await expect(panel.getByLabel('7.0 h: Excellent', {exact: true})).toBeVisible();
    await expect(row).toContainText('Disrupted sleep · Active');
    await expect(row).toContainText('Current Coach warning · Last reviewed 2026-09-08');
    expect(overviewRequests).toBe(1);
    const dialog = page.getByRole('dialog', {name: 'Current Coach warnings', exact: true});
    await expect(dialog).toHaveCount(0);
    const view = row.getByRole('button', {name: 'View current sleep warning', exact: true});
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await view.focus();
        await expect(view).toBeFocused();
        await expect(view).toHaveClass(/compact-action/);
        await view.press('Escape');
        await panel.screenshot({path: test.info().outputPath(`sleep-coach-warning-${width}.png`)});
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    }
    await view.press('Enter');
    await expect(dialog.getByRole('heading', {name: 'Disrupted sleep', exact: true})).toBeVisible();
    await expect(dialog).toContainText(warnings[1].content.explanation);
    await expect(dialog).toContainText(warnings[1].content.evidence);
    await expect(dialog).toContainText(warnings[1].content.action);
    await expect(dialog).toContainText('Coach reviews and resolves warnings during coaching sessions.');
    await dialog.locator('.warning-detail').filter({hasText: 'Disrupted sleep'}).getByRole('button', {name: 'Review history'}).click();
    const reviews = page.getByRole('dialog', {name: 'Disrupted sleep · Reviews', exact: true});
    await expect(reviews).toContainText('2026-09-08 · Active');
    await reviews.getByRole('button', {name: 'Close', exact: true}).last().click();
    await dialog.getByRole('button', {name: 'Close', exact: true}).last().click();
    expect(overviewRequests).toBe(1);
    warnings = [warnings[0]];
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(row).toHaveCount(0);
    await expect(page.getByRole('button', {name: 'Current Coach warnings: 1 warning, Recovery strain', exact: true})).toBeVisible();
    await expect(panel.getByText('EXCELLENT (4/4)', {exact: true})).toBeVisible();
    warnings = [warningFixture(2, 'SLEEP_DISRUPTION')];
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(row).toContainText('Disrupted sleep · Active');
    await page.reload();
    await tabs.getByRole('tab', {name: 'Sleep'}).click();
    await expect(row).toContainText('Current Coach warning · Last reviewed 2026-09-08');
    warnings = [];
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(row).toHaveCount(0);
    await expect(page.getByRole('button', {name: 'Coach history', exact: true})).toBeVisible();
});

async function installWakeLockMock(page, {supported = true, holdRequest = false} = {}) {
    await page.addInitScript(({supported, holdRequest}) => {
        const mock = {supported, holdRequest, rejectNext: false, visibility: 'visible', requests: [], locks: [], active: 0, released: 0};
        function createSentinel() {
            const sentinel = new EventTarget();
            let released = false;
            Object.defineProperty(sentinel, 'released', {get: () => released});
            function release() {
                if (released) return;
                released = true;
                mock.active -= 1;
                mock.released += 1;
                sentinel.dispatchEvent(new Event('release'));
            }
            sentinel.release = async () => release();
            sentinel.releaseByBrowser = release;
            mock.active += 1;
            mock.locks.push(sentinel);
            return sentinel;
        }
        mock.setVisibility = visibility => {
            mock.visibility = visibility;
            document.dispatchEvent(new Event('visibilitychange'));
        };
        mock.releaseByBrowser = () => [...mock.locks].reverse().find(lock => !lock.released)?.releaseByBrowser();
        mock.resolvePending = () => mock.resolvePendingRequest?.(createSentinel());
        Object.defineProperty(window, '__wakeLockMock', {value: mock, configurable: true});
        Object.defineProperty(document, 'visibilityState', {configurable: true, get: () => mock.visibility});
        Object.defineProperty(navigator, 'wakeLock', {configurable: true, get: () => mock.supported ? {
            request(type) {
                mock.requests.push(type);
                if (mock.rejectNext) {
                    mock.rejectNext = false;
                    return Promise.reject(new DOMException('Wake lock request denied', 'NotAllowedError'));
                }
                if (mock.holdRequest) return new Promise(resolve => { mock.resolvePendingRequest = resolve; });
                return Promise.resolve(createSentinel());
            }
        } : undefined});
    }, {supported, holdRequest});
}

async function prepareSingleSetGuidedWorkout(page, accountEmail = 'jllado@gmail.com') {
    await page.clock.install({time: new Date('2026-09-27T12:00:00')});
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, sessions: []}));
    const state = await mockWeeklyPlans(page, {id: 1, startDate: '2026-09-27', reviewDate: '2026-10-26', updateToken: 'wake-lock-token', days, notes: ''});
    if (accountEmail !== 'jllado@gmail.com') {
        await page.route('**/api/auth/me', route => route.fulfill({json: {email: accountEmail, displayName: 'Jordi', authenticated: true}}));
    }
    state.setCurrent({...state.current, days: state.current.days.map(day => day.day === 'SUNDAY' ? {day: day.day, rest: false, note: null, sessions: [{name: 'Screen lock test', note: null, lines: [{exerciseId: 1, exerciseName: state.exercises[0].name, exerciseDescription: state.exercises[0].description, exerciseType: 'TRAINING', trackingMode: 'REPS', stretchingUnit: 'SECONDS', segments: [{repetitions: 8, weight: 20}]}]}]} : day)});
    await page.route('**/api/workout-plans/current', route => route.fulfill({json: state.current}));
    await openSpaRoute(page, '/workouts?tab=plan');
    await page.getByRole('region', {name: 'Weekly workout plan'}).locator('.plan-day').nth(6).locator('.plan-day-toggle').click();
    await page.getByRole('button', {name: 'Start guided', exact: true}).click();
    return page.getByRole('dialog', {name: 'Screen lock test'});
}

async function openGuidedWorkoutResumePanel(page, accountEmail = 'jllado@gmail.com', workoutExercises = []) {
    await mockAuthenticatedDashboard(page, dashboard.anchorDate, {workoutExercises});
    if (accountEmail !== 'jllado@gmail.com') {
        await page.route('**/api/auth/me', route => route.fulfill({json: {email: accountEmail, displayName: 'Jordi', authenticated: true}}));
    }
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Workout'}).click();
    return page.locator('.home-panels-tabs .p-tabview-panel:visible .guided-workout-resume');
}

test('guided workout resume controls live in the Workout panel and preserve confirmation', async ({page}, testInfo) => {
    const guided = await prepareSingleSetGuidedWorkout(page);
    await guided.getByRole('button', {name: 'Close', exact: true}).click();
    const resume = await openGuidedWorkoutResumePanel(page);
    await expect(resume.getByRole('button', {name: 'Resume guided workout', exact: true})).toBeVisible();
    await expect(resume).toContainText('Screen lock test · 1 of 1 sets · Running');
    await expect(page.locator('.guided-workout-resume')).toHaveCount(1);
    expect(await page.evaluate(() => !!(document.querySelector('.dashboard-date-value-row').compareDocumentPosition(document.querySelector('.guided-workout-resume')) & Node.DOCUMENT_POSITION_FOLLOWING))).toBe(true);
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        expect(await resume.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`guided-workout-resume-panel-${width}.png`)});
    }
    await resume.getByRole('button', {name: 'Resume guided workout', exact: true}).click();
    const resumed = page.getByRole('dialog', {name: 'Screen lock test'});
    await expect(resumed).toContainText('Squat with a deliberately long descriptive exercise name');
    await resumed.getByRole('button', {name: 'Close', exact: true}).click();
    await resume.getByRole('button', {name: 'Discard guided workout', exact: true}).click();
    const confirmation = page.getByRole('dialog', {name: 'Discard guided workout?'});
    await confirmation.getByRole('button', {name: 'Keep draft', exact: true}).click();
    await expect(resume.getByRole('button', {name: 'Resume guided workout', exact: true})).toBeVisible();
    await resume.getByRole('button', {name: 'Discard guided workout', exact: true}).click();
    await page.getByRole('dialog', {name: 'Discard guided workout?'}).getByRole('button', {name: 'Discard', exact: true}).click();
    await expect(resume).toHaveCount(0);
    expect(await page.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'))).toBeNull();
});

test('guided keep-screen-on preference follows account and visible workout lifecycle', async ({page, context}) => {
    await installWakeLockMock(page);
    const guided = await prepareSingleSetGuidedWorkout(page);
    await page.setViewportSize({width: 390, height: 844});
    expect(await page.evaluate(() => document.documentElement.clientWidth)).toBe(390);
    const preferenceKey = 'guided-workout-screen-lock-v1:jllado@gmail.com';
    const toggle = guided.getByRole('checkbox', {name: 'Keep screen on'});
    const status = guided.locator('.guided-screen-lock [aria-live="polite"]');
    await expect(toggle).not.toBeChecked();
    expect(await page.evaluate(key => localStorage.getItem(key), preferenceKey)).toBeNull();

    await guided.locator('label[for="guided-keep-screen-on"]').click();
    await expect(status).toContainText('Screen lock is active');
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.active)).toBe(1);
    expect(await page.evaluate(key => localStorage.getItem(key), preferenceKey)).toBe('true');
    expect(await page.evaluate(() => window.__wakeLockMock.requests)).toEqual(['screen']);

    await page.evaluate(() => window.__wakeLockMock.setVisibility('hidden'));
    await expect(status).toContainText('paused while this page is hidden');
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.active)).toBe(0);
    await page.evaluate(() => window.__wakeLockMock.setVisibility('visible'));
    await expect(status).toContainText('Screen lock is active');
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.requests.length)).toBe(2);

    await page.evaluate(() => window.__wakeLockMock.releaseByBrowser());
    await expect(status).toContainText('The browser released the screen lock.');
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.active)).toBe(0);
    await page.evaluate(() => window.__wakeLockMock.setVisibility('hidden'));
    await page.evaluate(() => window.__wakeLockMock.setVisibility('visible'));
    await expect(status).toContainText('Screen lock is active');

    await guided.getByRole('button', {name: 'Close', exact: true}).click();
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.active)).toBe(0);
    const resume = await openGuidedWorkoutResumePanel(page);
    await resume.getByRole('button', {name: 'Resume guided workout', exact: true}).click();
    const resumed = page.getByRole('dialog', {name: 'Screen lock test'});
    await expect(resumed.getByRole('checkbox', {name: 'Keep screen on'})).toBeChecked();
    await expect(resumed.locator('.guided-screen-lock [aria-live="polite"]')).toContainText('Screen lock is active');
    await resumed.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(resumed.getByRole('button', {name: 'Review', exact: true})).toBeVisible();
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.active)).toBe(0);

    const otherAccount = await context.newPage();
    await installWakeLockMock(otherAccount);
    const otherGuided = await prepareSingleSetGuidedWorkout(otherAccount, 'other@example.com');
    await expect(otherGuided.getByRole('checkbox', {name: 'Keep screen on'})).not.toBeChecked();
    expect(await otherAccount.evaluate(() => localStorage.getItem('guided-workout-screen-lock-v1:other@example.com'))).toBeNull();
    await otherGuided.getByRole('button', {name: 'Close', exact: true}).click();
});

test('guided keep-screen-on continues when the browser cannot grant a lock', async ({page}) => {
    await installWakeLockMock(page, {supported: false});
    const guided = await prepareSingleSetGuidedWorkout(page);
    const status = guided.locator('.guided-screen-lock [aria-live="polite"]');
    await guided.locator('label[for="guided-keep-screen-on"]').click();
    await expect(status).toContainText('Screen lock is unavailable in this browser.');
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.active)).toBe(0);

    await guided.locator('label[for="guided-keep-screen-on"]').click();
    await page.evaluate(() => { window.__wakeLockMock.supported = true; window.__wakeLockMock.rejectNext = true; });
    await guided.locator('label[for="guided-keep-screen-on"]').click();
    await expect(status).toContainText('Screen lock could not be acquired; the workout can continue.');
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.active)).toBe(0);
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided.getByRole('button', {name: 'Review', exact: true})).toBeVisible();
});

test('guided keep-screen-on releases a pending request when the session closes', async ({page}) => {
    await installWakeLockMock(page, {holdRequest: true});
    const guided = await prepareSingleSetGuidedWorkout(page);
    await guided.locator('label[for="guided-keep-screen-on"]').click();
    await expect(guided.locator('.guided-screen-lock [aria-live="polite"]')).toContainText('Requesting screen lock');
    await guided.getByRole('button', {name: 'Close', exact: true}).click();
    await page.evaluate(() => window.__wakeLockMock.resolvePending());
    await expect.poll(() => page.evaluate(() => window.__wakeLockMock.active)).toBe(0);
    expect(await page.evaluate(() => window.__wakeLockMock.released)).toBe(1);
    const resume = await openGuidedWorkoutResumePanel(page);
    await expect(resume.getByRole('button', {name: 'Resume guided workout', exact: true})).toBeVisible();
});

for (const width of [320, 376, 390, 575, 640, 960, 1280]) {
    test(`Coach warnings stay compact and resolve independently at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockAuthenticatedDashboard(page);
        let warnings = [warningFixture(1), warningFixture(2, 'PAIN_INCREASE')];
        let fail = false;
        await page.route('**/api/coach-warnings', route => fail ? route.fulfill({status: 503}) : route.fulfill({json: {active: warnings, hasHistory: true}}));
        await page.route('**/api/coach-warnings/history?*', route => route.fulfill({json: {items: [{...warningFixture(3, 'SLEEP_DISRUPTION'), status: 'RESOLVED', resolutionRationale: 'Newer nights returned toward baseline.'}], page: 0, hasMore: false}}));
        await page.route('**/api/coach-warnings/*/revisions?*', route => route.fulfill({json: {items: [warningFixture(1)], page: 0, hasMore: false}}));
        await page.goto('/');
        const indicator = page.getByRole('button', {name: 'Current Coach warnings: 2 warnings', exact: true});
        await expect(indicator).toBeVisible();
        await expect(indicator.locator('xpath=..').locator('.count-badge')).toHaveText('2');
        await expect(indicator.locator('xpath=..').locator('.count-badge')).toHaveAttribute('aria-hidden', 'true');
        await expect(indicator.locator('xpath=..').locator('.count-badge')).toHaveCSS('background-color', 'rgb(254, 243, 199)');
        await expect(indicator.locator('xpath=..').locator('.count-badge')).toHaveCSS('color', 'rgb(146, 64, 14)');
        await expect(indicator).toHaveClass(/p-button-icon-only/);
        await expect(indicator.locator('.p-button-label')).toHaveText('');
        const dateRow = page.locator('.dashboard-date-value-row');
        await expect(dateRow.getByRole('button', {name: 'Current Coach warnings: 2 warnings', exact: true})).toBeVisible();
        await indicator.hover();
        const tooltip = page.getByRole('tooltip');
        await expect(tooltip).toHaveText('Recovery strain · Increased pain');
        await expect(page.locator('#coach-warnings-tooltip')).toHaveCount(1);
        await expect(indicator).toHaveAttribute('aria-describedby', await tooltip.getAttribute('id'));
        await expect(tooltip).toHaveCSS('opacity', '1');
        const tipBox = await tooltip.boundingBox();
        const actionsBox = await page.locator('.dashboard-date-actions').boundingBox();
        expect(tipBox.x).toBeGreaterThanOrEqual(8);
        expect(tipBox.x + tipBox.width).toBeLessThanOrEqual(width - 8);
        expect(tipBox.x < actionsBox.x + actionsBox.width && tipBox.x + tipBox.width > actionsBox.x && tipBox.y < actionsBox.y + actionsBox.height && tipBox.y + tipBox.height > actionsBox.y).toBe(false);
        const contrast = await tooltip.locator('.p-tooltip-text').evaluate(el => {
            const style = getComputedStyle(el);
            const luminance = color => color.match(/\d+/g).slice(0, 3).map(Number).map(value => {
                const channel = value / 255;
                return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
            }).reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0);
            const values = [luminance(style.color), luminance(style.backgroundColor)].sort((a, b) => b - a);
            return (values[0] + 0.05) / (values[1] + 0.05);
        });
        expect(contrast).toBeGreaterThanOrEqual(4.5);
        await page.screenshot({path: testInfo.outputPath(`warnings-tooltip-${width}.png`), animations: 'disabled'});
        await tooltip.hover();
        await expect(tooltip).toBeVisible();
        await page.mouse.move(0, 0);
        await expect(tooltip).toHaveCount(0);
        await indicator.focus();
        await expect(tooltip).toHaveText('Recovery strain · Increased pain');
        await indicator.press('Escape');
        await expect(tooltip).toHaveCount(0);
        await expect(indicator).toBeFocused();
        await expect(page.locator('.dashboard-date-header')).not.toContainText('Possible accumulated');
        await page.screenshot({path: testInfo.outputPath(`warnings-header-${width}.png`)});
        await indicator.focus();
        await page.keyboard.press('Enter');
        const dialog = page.getByRole('dialog', {name: 'Current Coach warnings', exact: true});
        await expect(dialog.getByRole('heading', {name: 'Recovery strain'})).toBeVisible();
        await expect(dialog.getByRole('heading', {name: 'Increased pain'})).toBeVisible();
        await expect(dialog.getByRole('button', {name: /Dismiss|Resolve$|Edit/})).toHaveCount(0);
        await dialog.getByRole('button', {name: 'Resolved history', exact: true}).click();
        await expect(dialog.getByText('Newer nights returned toward baseline.')).toBeVisible();
        await page.screenshot({path: testInfo.outputPath(`warnings-dialog-${width}.png`)});
        await dialog.getByRole('button', {name: 'Close', exact: true}).last().click();
        warnings = [warnings[0]];
        await page.evaluate(() => window.dispatchEvent(new Event('focus')));
        const single = page.getByRole('button', {name: 'Current Coach warnings: 1 warning, Recovery strain', exact: true});
        await expect(single).toBeVisible();
        await expect(single.locator('xpath=..').locator('.count-badge')).toHaveText('1');
        await single.hover();
        await expect(tooltip).toHaveText('Recovery strain');
        await page.mouse.move(0, 0);
        fail = true;
        await page.evaluate(() => window.dispatchEvent(new Event('focus')));
        await expect(page.getByText('Could not refresh Coach warnings.')).toBeVisible();
        await expect(page.getByRole('button', {name: 'Current Coach warnings: 1 warning, Recovery strain', exact: true})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        fail = false; warnings = [];
        await page.getByRole('button', {name: 'Retry', exact: true}).click();
        await expect(page.locator('.coach-warnings .count-badge')).toHaveCount(0);
        const history = page.getByRole('button', {name: 'Coach history', exact: true});
        await expect(history).toHaveClass(/p-button-icon-only/);
        await page.evaluate(() => window.scrollTo(0, 0));
        await page.screenshot({path: testInfo.outputPath(`warnings-history-header-${width}.png`), animations: 'disabled'});
        await history.hover();
        await expect(tooltip).toHaveText('Coach history');
        await expect(tooltip).toHaveCSS('opacity', '1');
        await page.screenshot({path: testInfo.outputPath(`warnings-history-${width}.png`), animations: 'disabled'});
        await tooltip.hover();
        await expect(tooltip).toBeVisible();
        await page.mouse.move(0, 0);
        await history.focus();
        await expect(tooltip).toHaveText('Coach history');
        await history.press('Enter');
        await expect(dialog.getByText('No active warnings.', {exact: true})).toBeVisible();
        await dialog.getByRole('button', {name: 'Resolved history', exact: true}).click();
        await expect(dialog.getByText('Newer nights returned toward baseline.')).toBeVisible();
        await dialog.getByRole('button', {name: 'Close', exact: true}).last().click();
        warnings = [warningFixture(2, 'SLEEP_DISRUPTION')];
        await page.evaluate(() => window.dispatchEvent(new Event('focus')));
        const sleep = page.getByRole('button', {name: 'Current Coach warnings: 1 warning, Disrupted sleep', exact: true});
        await expect(sleep).toBeVisible();
        await page.screenshot({path: testInfo.outputPath(`warnings-sleep-header-${width}.png`), animations: 'disabled'});
        await sleep.click();
        await expect(dialog.getByRole('heading', {name: 'Disrupted sleep'})).toBeVisible();
        await dialog.getByRole('button', {name: 'Close', exact: true}).last().click();
        const agenda = page.getByRole('link', {name: 'Agenda', exact: true});
        await expect(agenda).toHaveCount(1);
        await expect(agenda).toHaveAttribute('href', '/agenda');
        await expect(agenda.locator('.p-button-label')).toBeVisible({visible: width > 575});
        await expect(page.locator('.dashboard-date-header').getByRole('button', {name: 'Agenda', exact: true})).toHaveCount(0);
        const notesBox = await page.getByRole('link', {name: 'Coach Notes', exact: true}).boundingBox();
        const agendaBox = await agenda.boundingBox();
        expect(agendaBox.x).toBeGreaterThan(notesBox.x);
        expect(agendaBox.y + agendaBox.height / 2).toBeCloseTo(notesBox.y + notesBox.height / 2, 0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.route('**/api/push/agenda', route => route.fulfill({json: {date: '2026-09-08', timeZone: 'Europe/Madrid', entries: []}}));
        await agenda.hover();
        await expect(tooltip).toHaveText('Agenda');
        await expect(tooltip).toHaveCSS('opacity', '1');
        await expect(agenda).toHaveAttribute('aria-describedby', await tooltip.getAttribute('id'));
        const agendaTipBox = await tooltip.boundingBox();
        for (const control of [page.locator('.notification-bell-button'), page.locator('.account-menu-button')]) {
            const box = await control.boundingBox();
            expect(agendaTipBox.x < box.x + box.width && agendaTipBox.x + agendaTipBox.width > box.x && agendaTipBox.y < box.y + box.height && agendaTipBox.y + agendaTipBox.height > box.y).toBe(false);
        }
        await page.screenshot({path: testInfo.outputPath(`agenda-tooltip-${width}.png`), animations: 'disabled'});
        await page.mouse.move(0, 0);
        await agenda.focus();
        await expect(tooltip).toHaveText('Agenda');
        await agenda.press('Escape');
        await expect(tooltip).toHaveCount(0);
        await agenda.press('Enter');
        await expect(page).toHaveURL('/agenda');
        await expect(page.getByText('No push notifications are scheduled for today.', {exact: true})).toBeVisible();
        await page.route('**/agenda', route => route.request().resourceType() === 'document'
            ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
            : route.continue());
        await page.reload();
        await expect(page.getByRole('link', {name: 'Agenda', exact: true})).toBeVisible();
    });
}

test('workout catalog separates training cardio while retaining cardio warm-ups and Plan deep links', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Squat', description: 'Strength', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 3, name: 'Bike warm-up', description: 'Easy cycling', trackingMode: 'CARDIO', exerciseType: 'WARM_UP'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await page.route('**/api/workout-exercises**', route => {
        const request = route.request();
        if (request.method() === 'GET') return route.fulfill({json: exercises});
        if (request.method() === 'POST') {
            const exercise = {id: 4, ...request.postDataJSON()};
            exercises.push(exercise);
            return route.fulfill({json: exercise});
        }
        if (request.method() === 'PUT') {
            const exercise = {id: 4, ...request.postDataJSON()};
            exercises[exercises.findIndex(item => item.id === 4)] = exercise;
            return route.fulfill({json: exercise});
        }
        return route.fulfill({json: exercises});
    });
    await openSpaRoute(page, '/workouts');
    await page.getByRole('tab', {name: 'Exercises', exact: true}).click();
    await expect(page.getByText('Squat', {exact: true})).toBeVisible();
    await expect(page.getByText('Bike warm-up', {exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Exercise', exact: true});
    await expect(editor).toBeVisible();
    await expect(editor.locator('#exercise-mode')).not.toContainText('Cardio');
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('tab', {name: 'Cardio', exact: true}).click();
    await expect(page.getByText('No cardio exercises yet.', {exact: true})).toBeVisible();
    for (const width of [376, 390, 575, 576, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`workout-cardio-${width}.png`), animations: 'disabled'});
    }
    await page.getByRole('button', {name: 'New', exact: true}).click();
    await expect(editor).toBeVisible();
    await expect(editor.locator('#exercise-mode')).toContainText('Cardio');
    await editor.getByLabel('Name', {exact: true}).fill('Run');
    await editor.getByLabel('Description', {exact: true}).fill('Steady run');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    await expect(page.getByText('Run', {exact: true})).toBeVisible();
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`workout-cardio-populated-${width}.png`), animations: 'disabled'});
    }
    await page.getByRole('row').filter({hasText: 'Run'}).getByRole('button', {name: 'Edit exercise', exact: true}).click();
    await editor.locator('#exercise-mode').click();
    await page.getByRole('option', {name: 'Reps', exact: true}).click();
    await expect(editor.locator('#exercise-mode')).toContainText('Reps');
    await editor.getByLabel('Primary muscle group', {exact: true}).click();
    await page.getByRole('option', {name: 'Quadriceps', exact: true}).click();
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    expect(exercises.find(exercise => exercise.id === 4).trackingMode).toBe('REPS');
    await expect(page.getByText('No cardio exercises yet.', {exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'Exercises', exact: true}).click();
    await expect(page.getByText('Run', {exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'Warm-ups', exact: true}).click();
    await expect(page.getByText('Bike warm-up', {exact: true})).toBeVisible();
    await page.setViewportSize({width: 376, height: 950});
    const diaryTab = page.getByRole('tab', {name: 'Diary', exact: true});
    const planTab = page.getByRole('tab', {name: 'Plan', exact: true});
    await diaryTab.focus();
    await diaryTab.press('End');
    const balanceTab = page.getByRole('tab', {name: 'Training balance', exact: true});
    await expect(balanceTab).toBeFocused();
    await expect(balanceTab.locator('.p-tabview-title')).toBeInViewport({ratio: 1});
    await balanceTab.press('Enter');
    await expect(balanceTab).toHaveAttribute('aria-selected', 'true');
    await openSpaRoute(page, '/workouts?tab=plan');
    await expect(planTab).toHaveAttribute('aria-selected', 'true');
});

test('Coach warnings show loading then retry an initial failure', async ({page}) => {
    await page.setViewportSize({width: 376, height: 900});
    await mockAuthenticatedDashboard(page);
    let deliver;
    let fail = true;
    const responseReady = new Promise(resolve => { deliver = resolve; });
    await page.route('**/api/coach-warnings', async route => {
        await responseReady;
        return fail ? route.fulfill({status: 503}) : route.fulfill({json: {active: [warningFixture(1, 'SLEEP_DISRUPTION')], hasHistory: false}});
    });
    await page.goto('/');
    await expect(page.getByRole('status').filter({hasText: 'Checking Coach warnings…'})).toBeVisible();
    deliver();
    await expect(page.getByRole('alert').filter({hasText: 'Could not refresh Coach warnings.'})).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    fail = false;
    await page.getByRole('button', {name: 'Retry', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Current Coach warnings: 1 warning, Disrupted sleep', exact: true})).toBeVisible();
    await expect(page.getByText('Could not refresh Coach warnings.')).toHaveCount(0);
});

test('Coach warnings hide empty state and show long review history without overflow', async ({page}) => {
    await mockAuthenticatedDashboard(page);
    let active = [];
    await page.route('**/api/coach-warnings', route => route.fulfill({json: {active, hasHistory: false}}));
    await page.route('**/api/coach-warnings/1/revisions?*', route => route.fulfill({json: {items: [{...warningFixture(1), content: {...warningFixture(1).content, evidence: 'Long evidence '.repeat(200)}}], page: 0, hasMore: false}}));
    await page.goto('/');
    await expect(page.locator('.dashboard-date-header')).toBeVisible();
    await expect(page.getByRole('button', {name: /^Current Coach warnings:/})).toHaveCount(0);
    await expect(page.getByRole('button', {name: 'Coach history', exact: true})).toHaveCount(0);
    active = [warningFixture(1)];
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await page.getByRole('button', {name: 'Current Coach warnings: 1 warning, Recovery strain', exact: true}).click();
    await page.getByRole('button', {name: 'Review history', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Recovery strain · Reviews', exact: true});
    await expect(dialog.getByText(/Long evidence/)).toBeVisible();
    expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
    await dialog.getByRole('button', {name: 'Close', exact: true}).last().click();
    await expect(page.getByRole('dialog', {name: 'Current Coach warnings', exact: true})).toBeVisible();
});

test('stretching catalog supports CRUD and refreshes the workout picker', async ({page}, testInfo) => {
    const longName = 'Gentle cross-body shoulder stretch with relaxed breathing and supported arm';
    const exercises = [
        {id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Arm circles', description: 'Controlled circles.', trackingMode: 'SECONDS', exerciseType: 'WARM_UP'},
        {id: 3, name: longName, description: 'Hold comfortably. Record each side as a separate set.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await page.route('**/api/workout-exercises**', route => {
        const request = route.request();
        if (request.method() === 'POST') {
            const exercise = {id: 4, ...request.postDataJSON()};
            exercises.push(exercise);
            return route.fulfill({json: exercise});
        }
        if (request.method() === 'PUT') {
            const exercise = {id: 4, ...request.postDataJSON()};
            exercises[3] = exercise;
            return route.fulfill({json: exercise});
        }
        if (request.method() === 'DELETE') {
            exercises.pop();
            return route.fulfill({status: 204});
        }
        return route.fulfill({json: exercises});
    });
    await openSpaRoute(page, '/workouts');
    await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
    const panel = page.getByRole('tabpanel');
    await expect(panel).toContainText(longName);
    await expect(panel).not.toContainText('Squat');
    await expect(panel).not.toContainText('Arm circles');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await expect(panel.getByRole('button', {name: 'Edit stretching exercise'})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`stretching-catalog-${width}.png`)});
    }
    await panel.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Stretching', exact: true});
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toContainText('Name is required');
    await editor.getByLabel('Name', {exact: true}).fill('Calf stretch');
    await editor.getByLabel('Description', {exact: true}).fill('Keep the back heel down.');
    await expect(editor.getByLabel('Mode', {exact: true})).toHaveValue('Time or breaths');
    await expect(editor.getByRole('checkbox')).toHaveCount(0);
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    expect(exercises[3]).toMatchObject({exerciseType: 'STRETCHING', trackingMode: 'SECONDS'});
    await panel.getByRole('row').filter({hasText: 'Calf stretch'}).getByRole('button', {name: 'Edit stretching exercise'}).click();
    await editor.getByLabel('Name', {exact: true}).fill('Wall calf stretch');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    await page.getByRole('tab', {name: 'Diary', exact: true}).click();
    await panel.getByRole('button', {name: 'New', exact: true}).click();
    const workout = page.getByRole('dialog', {name: 'Workout', exact: true});
    await workout.getByRole('button', {name: 'Add stretching', exact: true}).click();
    await workout.locator('.workout-line-card').last().locator('.p-dropdown').first().click();
    await expect(page.getByRole('option', {name: 'Wall calf stretch', exact: true})).toBeVisible();
    await expect(page.getByRole('option', {name: 'Squat', exact: true})).toHaveCount(0);
    await page.getByRole('option', {name: 'Wall calf stretch', exact: true}).click();
    await workout.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
    page.once('dialog', dialog => dialog.accept());
    await panel.getByRole('row').filter({hasText: 'Wall calf stretch'}).getByRole('button', {name: 'Delete stretching exercise'}).click();
    await expect(panel).not.toContainText('Wall calf stretch');
});

test('stretching workouts save timed sets, edit, preserve group order and preload on mobile and desktop', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Plank', description: 'Hold a plank.', trackingMode: 'SECONDS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Wall calf stretch', description: 'Keep the back heel down. Record each side as a separate set.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    await page.clock.setFixedTime(new Date('2026-09-08T08:00:00Z'));
    await mockAuthenticatedWorkouts(page, [], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    await dialog.getByRole('button', {name: 'Add stretching', exact: true}).click();
    let cards = dialog.locator('.workout-line-card');
    await cards.nth(1).locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: 'Wall calf stretch', exact: true}).click();
    await cards.nth(0).getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
    await expect(cards.nth(0).getByLabel('Breaths', {exact: true})).toHaveValue('');
    await cards.nth(0).getByLabel('Mode', {exact: true}).click();
    await page.getByRole('option', {name: 'Time', exact: true}).click();
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).toContainText('Duration is required');
    await cards.nth(0).locator('.segment-card .p-dropdown').click();
    await page.getByRole('option', {name: '30', exact: true}).click();
    await cards.nth(0).getByRole('button', {name: 'Add set', exact: true}).click();
    await expect(cards.nth(0).locator('.segment-card')).toHaveCount(2);
    await expect(page.locator('.p-dropdown-panel')).toHaveCount(0);
    await expect(cards.nth(0).getByText('Weight', {exact: true})).toHaveCount(0);
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`stretching-workout-${width}.png`)});
    }
    const createRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    const payload = (await createRequest).postDataJSON();
    expect(payload.lines).toHaveLength(1);
    expect(payload.lines[0].segments.map(set => [set.durationSeconds, set.weight])).toEqual([[30, null], [30, null]]);
    await expect(dialog).toBeHidden();
    await expect(page.locator('.diary-desktop')).toContainText('Stretching');
    await expect(page.locator('.diary-desktop')).not.toContainText('0 kg');
    await page.setViewportSize({width: 390, height: 950});
    const mobile = page.locator('.mobile-diary-workout');
    await mobile.getByRole('button', {name: /Wall calf stretch/}).click();
    await expect(mobile).toContainText('00:30');
    await expect(mobile).not.toContainText('0 kg');
    await mobile.getByRole('button', {name: 'Edit workout', exact: true}).click();
    await dialog.getByRole('button', {name: /^Expand Stretching,/}).click();
    await dialog.locator('.workout-line-card').getByRole('button', {name: /^Expand Stretching/}).click();
    await dialog.locator('.segment-card').first().getByLabel('Minutes', {exact: true}).fill('1');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).toBeHidden();
    await mobile.getByRole('button', {name: /Wall calf stretch/}).click();
    await expect(mobile).toContainText('01:30');
    await page.clock.setFixedTime(new Date('2026-09-09T08:00:00Z'));
    await openSpaRoute(page, '/workouts');
    await page.locator('.diary-mobile').getByRole('button', {name: 'New', exact: true}).click();
    await dialog.locator('.p-field').filter({hasText: 'Preload workout'}).locator('.p-dropdown').click();
    await page.getByRole('option', {name: 'Tue, 08/09/2026 - Wall calf stretch (0 exercises)', exact: true}).click();
    cards = dialog.locator('.workout-line-card');
    await expect(cards).toHaveCount(1);
    await dialog.getByRole('button', {name: 'Add exercise', exact: true}).click();
    await cards.nth(0).locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: 'Plank', exact: true}).click();
    await cards.nth(0).getByLabel('Minutes', {exact: true}).fill('1');
    await expect(cards.nth(0).getByRole('button', {name: 'Move exercise 1 down', exact: true})).toBeDisabled();
    await expect(cards.nth(1).getByRole('button', {name: 'Move exercise 1 up', exact: true})).toBeDisabled();
    await expect(cards.nth(0).locator('.workout-line-toggle')).toContainText('Plank');
    const mixedRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    const mixed = (await mixedRequest).postDataJSON();
    expect(mixed.lines.map(line => line.exerciseId)).toEqual([1, 2]);
    expect(mixed.lines[1].segments.map(set => set.durationSeconds)).toEqual([90, 30]);
    await expect(dialog).toBeHidden();
    await expect(page.locator('.mobile-diary-workout').first().locator('.mobile-diary-summary')).toContainText('Plank');
});

const exercisePictureBytes = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');

for (const width of [376, 390, 1280]) {
    test(`supine knees side to side catalog, picture and refreshed holds at ${width}px`, async ({page}, testInfo) => {
        const exercise = {
            id: 1, name: 'Supine knees side to side',
            description: 'Lie on your back with both knees bent, feet supported on the mat, and arms spread. Keep your knees together and gently lower them toward one side while both shoulders stay grounded. Hold comfortably without forcing your knees to the floor, return to the center, and repeat on the other side. Record one hold per set.',
            trackingMode: 'SECONDS', exerciseType: 'STRETCHING', imageUrl: '/api/workout-exercises/1/image?v=supine-knees-side-to-side'
        };
        await mockAuthenticatedWorkouts(page, [], [exercise]);
        await page.route('**/api/workout-exercises/1/image?*', route => route.fulfill({contentType: 'image/jpeg', path: 'backend/src/main/resources/exercise-images/supine-knees-side-to-side.jpg'}));
        await page.setViewportSize({width, height: 950});
        await openSpaRoute(page, '/workouts');
        await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
        const picture = page.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true});
        await expect(picture.locator('img')).toHaveJSProperty('naturalWidth', 1254);
        await expect(page.getByRole('tabpanel')).toContainText(exercise.description);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`supine-catalog-${width}.png`)});
        await picture.focus();
        await page.keyboard.press('Enter');
        const viewer = page.getByRole('dialog', {name: exercise.name, exact: true});
        await expect(viewer.getByText(exercise.description, {exact: true})).toBeVisible();
        await expect(viewer.locator('img')).toHaveJSProperty('naturalHeight', 1254);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`supine-viewer-${width}.png`)});
        await viewer.getByRole('button', {name: 'Close', exact: true}).last().click();
        await openSpaRoute(page, '/workouts');
        await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
        await expect(picture.locator('img')).toHaveJSProperty('naturalWidth', 1254);
        await page.getByRole('tab', {name: 'Diary', exact: true}).click();
        await page.getByRole('tabpanel').getByRole('button', {name: 'New', exact: true}).click();
        const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
        await editor.getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
        await editor.getByRole('button', {name: 'Add stretching', exact: true}).click();
        const card = editor.locator('.workout-line-card');
        await card.locator('.workout-exercise-picker').click();
        const option = page.getByRole('option', {name: exercise.name, exact: true});
        await expect(option.locator('img')).toHaveJSProperty('naturalWidth', 1254);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`supine-picker-${width}.png`)});
        await option.click();
        await card.getByLabel('Mode', {exact: true}).click();
        await page.getByRole('option', {name: 'Time', exact: true}).click();
        await card.locator('.segment-card .p-dropdown').click();
        await page.getByRole('option', {name: '30', exact: true}).click();
        await expect(page.locator('.p-dropdown-panel')).toHaveCount(0);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`supine-editor-${width}.png`)});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await saving).postDataJSON().lines[0]).toMatchObject({exerciseId: 1, stretchingUnit: 'SECONDS', segments: [{durationSeconds: 30, breaths: null}]});
        await expect(editor).toBeHidden();
        await openSpaRoute(page, '/workouts');
        const session = page.locator(width < 960 ? '.mobile-diary-workout' : '.diary-day-session');
        if (width < 960) await session.locator('.mobile-diary-summary').click();
        await expect(session).toContainText(exercise.name);
        await expect(session).toContainText('00:30');
        await expect(session.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true}).locator('img')).toHaveJSProperty('naturalWidth', 1254);
        await session.getByRole('button', {name: 'Edit workout', exact: true}).click();
        await editor.getByRole('button', {name: /^Expand Stretching,/}).click();
        await editor.locator('.workout-line-card').getByRole('button', {name: /^Expand Stretching/}).click();
        await editor.getByLabel('Mode', {exact: true}).click();
        await page.getByRole('option', {name: 'Breaths', exact: true}).click();
        await expect(page.locator('.p-dropdown-panel')).toHaveCount(0);
        await editor.getByLabel('Breaths', {exact: true}).fill('5');
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`supine-breaths-${width}.png`)});
        const updating = page.waitForRequest(request => /\/api\/workouts\/\d+$/.test(request.url()) && request.method() === 'PUT');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await updating).postDataJSON().lines[0]).toMatchObject({exerciseId: 1, stretchingUnit: 'BREATHS', segments: [{breaths: 5, durationSeconds: null}]});
        await expect(editor).toBeHidden();
        await openSpaRoute(page, '/workouts');
        if (width < 960) await session.locator('.mobile-diary-summary').click();
        await expect(session).toContainText('5 breaths');
        await expect(session).not.toContainText('00:30');
        const heading = session.locator('.diary-exercise-heading');
        const nameLines = await heading.locator('strong').evaluate(element => [...element.getClientRects()].map(rect => rect.left));
        expect(nameLines.every(left => Math.abs(left - nameLines[0]) < 1)).toBe(true);
        const pictureBounds = await heading.locator('.exercise-picture').boundingBox();
        const textBounds = await heading.locator('.diary-exercise-heading-text').boundingBox();
        expect(textBounds.x).toBeGreaterThanOrEqual(pictureBounds.x + pictureBounds.width);
        await expect(heading.locator('.exercise-picture-button')).toHaveCSS('width', '64px');
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`supine-saved-${width}.png`)});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    });
}

test('new illustrated warm-ups appear in the catalog and workout picker', async ({page}) => {
    const exercises = [
        {id: 1, name: 'Standing lunge hip-flexor stretch', description: 'Hold and switch sides.', trackingMode: 'SECONDS', exerciseType: 'WARM_UP', imageUrl: '/api/workout-exercises/1/image?v=lunge'},
        {id: 2, name: 'Calf stretch on step', description: 'Lower the heel and switch sides.', trackingMode: 'SECONDS', exerciseType: 'WARM_UP', imageUrl: '/api/workout-exercises/2/image?v=step'},
        {id: 3, name: 'Floor sit-to-stand without hands', description: 'Rise and return under control.', trackingMode: 'REPS', exerciseType: 'WARM_UP', imageUrl: '/api/workout-exercises/3/image?v=floor'},
        {id: 4, name: 'Resistance-band shoulder pass-through', description: 'Move the band overhead and behind.', trackingMode: 'REPS', exerciseType: 'WARM_UP', imageUrl: '/api/workout-exercises/4/image?v=band'}
    ];
    const pictures = ['standing-lunge-hip-flexor-stretch', 'calf-stretch-on-step', 'floor-sit-to-stand-without-hands', 'resistance-band-shoulder-pass-through'];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await page.route('**/api/workout-exercises/*/image?*', route => {
        const id = Number(new URL(route.request().url()).pathname.split('/')[3]);
        return route.fulfill({contentType: 'image/jpeg', path: `backend/src/main/resources/exercise-images/${pictures[id - 1]}.jpg`});
    });

    for (const width of [390, 1280]) {
        await openSpaRoute(page, '/workouts');
        await page.setViewportSize({width, height: 950});
        await page.getByRole('tab', {name: 'Warm-ups', exact: true}).click();
        for (const exercise of exercises) {
            const picture = page.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true});
            await expect(picture.locator('img')).toBeVisible();
            await expect(picture.locator('img')).toHaveJSProperty('naturalWidth', 1254);
        }
        await page.getByRole('tab', {name: 'Diary', exact: true}).click();
        await page.getByRole('tabpanel').getByRole('button', {name: 'New', exact: true}).click();
        const workout = page.getByRole('dialog', {name: 'Workout', exact: true});
        for (const [index, exercise] of exercises.entries()) {
            await workout.getByRole('button', {name: 'Add warm-up', exact: true}).click();
            const card = workout.locator('.workout-line-card').nth(index);
            await card.locator('.workout-exercise-picker').click();
            const option = page.getByRole('option', {name: exercise.name, exact: true});
            await expect(option.locator('img')).toBeVisible();
            await expect.poll(() => option.locator('img').evaluate(image => image.naturalWidth)).toBe(1254);
            await option.click();
            await expect(card.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true})).toBeVisible();
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }
});

for (const width of [390, 575, 640, 960, 1280]) {
    test(`exercise pictures open from catalogs and workout entry at ${width}px`, async ({page}, testInfo) => {
        const exercises = [
            {id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS', exerciseType: 'TRAINING', imageUrl: '/api/workout-exercises/1/image?v=squat'},
            {id: 2, name: 'Arm circles', description: 'Controlled circles.', trackingMode: 'SECONDS', exerciseType: 'WARM_UP', imageUrl: '/api/workout-exercises/2/image?v=arms'},
            {id: 3, name: 'Wall calf stretch with comfortable support and relaxed breathing', description: 'Keep the back heel down.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING', imageUrl: '/api/workout-exercises/3/image?v=calf'}
        ];
        await mockAuthenticatedWorkouts(page, [], exercises);
        await page.route('**/api/workout-exercises/*/image?*', route => route.fulfill({contentType: 'image/jpeg', path: `backend/src/main/resources/exercise-images/${['squat', 'arm-circles', 'wall-calf-stretch'][Number(new URL(route.request().url()).pathname.split('/')[3]) - 1]}.jpg`}));
        await openSpaRoute(page, '/workouts');
        await page.setViewportSize({width, height: 950});
        for (const [index, tab] of ['Exercises', 'Warm-ups', 'Stretching'].entries()) {
            await page.getByRole('tab', {name: tab, exact: true}).click();
            const button = page.getByRole('button', {name: `View picture of ${exercises[index].name}`, exact: true});
            await expect(button.locator('img')).toBeVisible();
            await expect(page.getByRole('tabpanel').getByRole('button', {name: ['Edit exercise', 'Edit warm-up', 'Edit stretching exercise'][index], exact: true})).toBeInViewport({ratio: 1});
            await expect(page.getByRole('tabpanel').getByRole('button', {name: ['Delete exercise', 'Delete warm-up', 'Delete stretching exercise'][index], exact: true})).toBeInViewport({ratio: 1});
            await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`exercise-catalog-${tab}-${width}.png`)});
            await button.focus(); await page.keyboard.press('Enter');
            const viewer = page.getByRole('dialog', {name: exercises[index].name, exact: true});
            await expect(viewer.getByText(exercises[index].description, {exact: true})).toBeVisible();
            await expect(viewer.locator('img')).toHaveJSProperty('naturalWidth', 1254);
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
            await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`exercise-picture-${tab}-${width}.png`)});
            await viewer.getByRole('button', {name: 'Close', exact: true}).last().click();
        }
        await page.getByRole('tab', {name: 'Diary', exact: true}).click();
        await page.getByRole('tabpanel').getByRole('button', {name: 'New', exact: true}).click();
        const workout = page.getByRole('dialog', {name: 'Workout', exact: true});
        for (const [index, exercise] of exercises.entries()) {
            if (index) await workout.getByRole('button', {name: index === 1 ? 'Add warm-up' : 'Add stretching', exact: true}).click();
            const card = workout.locator('.workout-line-card').nth([0, 0, 2][index]);
            await card.locator('.workout-exercise-picker').click();
            const option = page.getByRole('option', {name: exercise.name, exact: true});
            await expect(option.locator('img')).toBeVisible();
            await expect.poll(() => option.locator('img').evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
            await option.click();
        }
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`exercise-entry-${width}.png`)});
        await workout.getByRole('button', {name: 'View picture of Squat', exact: true}).click();
        await expect(page.getByRole('dialog', {name: 'Squat', exact: true})).toBeVisible();
        await page.getByRole('dialog', {name: 'Squat', exact: true}).getByRole('button', {name: 'Close', exact: true}).last().click();
        await expect(workout).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    });
}

for (const width of [390, 1280]) {
    test(`half-kneeling single-arm dumbbell press picture and selection at ${width}px`, async ({page}) => {
        const exercise = {id: 1, name: 'Half-kneeling single-arm dumbbell press', description: 'Start in a half-kneeling position with one knee on a mat and the opposite foot flat on the floor. Hold a dumbbell at shoulder height with one hand, keep your torso tall, press it overhead, then lower with control. Record repetitions for one side, then repeat on the other side.', trackingMode: 'REPS', exerciseType: 'TRAINING', imageUrl: '/api/workout-exercises/1/image?v=half-kneeling-single-arm-dumbbell-press'};
        await mockAuthenticatedWorkouts(page, [], [exercise]);
        await page.route('**/api/workout-exercises/1/image?*', route => route.fulfill({contentType: 'image/jpeg', path: 'backend/src/main/resources/exercise-images/half-kneeling-single-arm-dumbbell-press.jpg'}));
        await page.setViewportSize({width, height: 950});
        await openSpaRoute(page, '/workouts');
        await page.getByRole('tab', {name: 'Exercises', exact: true}).click();
        await page.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true}).click();
        const viewer = page.getByRole('dialog', {name: exercise.name, exact: true});
        await expect(viewer.getByText(exercise.description, {exact: true})).toBeVisible();
        await expect(viewer.locator('img')).toHaveJSProperty('naturalWidth', 1254);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await viewer.getByRole('button', {name: 'Close', exact: true}).last().click();
        await page.getByRole('tab', {name: 'Diary', exact: true}).click();
        await page.getByRole('tabpanel').getByRole('button', {name: 'New', exact: true}).click();
        const workout = page.getByRole('dialog', {name: 'Workout', exact: true});
        await workout.locator('.workout-line-card').first().locator('.workout-exercise-picker').click();
        await page.getByRole('option', {name: exercise.name, exact: true}).click();
        await expect(workout.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    });
}

test('exercise pictures stage uploads, preserve failed saves, replace and restore pictures', async ({page}, testInfo) => {
    const pageErrors = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    const exercises = [];
    await mockAuthenticatedWorkouts(page, [], exercises);
    let creates = 0, uploads = 0, removals = 0, fail = true;
    await page.route('**/api/workout-exercises**', route => {
        const request = route.request(), path = new URL(request.url()).pathname;
        if (path.endsWith('/image')) {
            if (request.method() === 'POST') {
                uploads++;
                expect(request.headers()['content-type']).toContain('multipart/form-data');
                if (fail) return route.fulfill({status: 500, body: 'Upload unavailable'});
                exercises[0] = {...exercises[0], imageUrl: `/api/workout-exercises/1/image?v=custom-${uploads}`, hasCustomImage: true};
                return route.fulfill({json: exercises[0]});
            }
            if (request.method() === 'DELETE') {
                removals++; exercises[0] = {...exercises[0], imageUrl: '/api/workout-exercises/1/image?v=builtin', hasCustomImage: false};
                return route.fulfill({json: exercises[0]});
            }
            return route.fulfill({contentType: 'image/png', body: exercisePictureBytes});
        }
        if (request.method() === 'POST') { creates++; exercises.push({id: 1, ...request.postDataJSON()}); return route.fulfill({json: exercises[0]}); }
        if (request.method() === 'PUT') { exercises[0] = {...exercises[0], ...request.postDataJSON()}; return route.fulfill({json: exercises[0]}); }
        return route.fulfill({json: exercises});
    });
    await openSpaRoute(page, '/workouts');
    await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
    await page.getByRole('tabpanel').getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Stretching', exact: true});
    const file = {name: 'picture.jpg', mimeType: 'image/jpeg', buffer: require('node:fs').readFileSync('backend/src/main/resources/exercise-images/wall-calf-stretch.jpg')};
    await editor.getByLabel('Name', {exact: true}).fill('Custom stretch');
    await editor.getByLabel('Description', {exact: true}).fill('Hold comfortably.');
    await editor.getByLabel('Picture', {exact: true}).setInputFiles({name: 'bad.txt', mimeType: 'text/plain', buffer: Buffer.from('invalid')});
    await expect(editor.getByRole('alert')).toContainText('Choose a JPEG or PNG');
    await editor.getByLabel('Picture', {exact: true}).setInputFiles(file);
    await expect(editor.locator('.exercise-picture-button img')).toHaveAttribute('src', /^blob:/);
    expect(uploads).toBe(0);
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`exercise-upload-${width}.png`)});
    }
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor.getByRole('alert')).toContainText('Exercise saved, but the picture could not be updated');
    expect(creates).toBe(1); expect(uploads).toBe(1);
    fail = false;
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden(); expect(creates).toBe(1); expect(uploads).toBe(2);
    await page.getByRole('button', {name: 'Edit stretching exercise'}).click();
    await editor.getByLabel('Picture', {exact: true}).setInputFiles(file);
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
    expect(uploads).toBe(2);
    await page.getByRole('button', {name: 'Edit stretching exercise'}).click();
    await editor.getByLabel('Picture', {exact: true}).setInputFiles(file);
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden(); expect(uploads).toBe(3);
    await page.getByRole('button', {name: 'Edit stretching exercise'}).click();
    await editor.getByRole('button', {name: 'Remove picture', exact: true}).click();
    expect(removals).toBe(0);
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden(); expect(removals).toBe(1);
    await expect(page.getByRole('button', {name: 'View picture of Custom stretch'}).locator('img')).toHaveAttribute('src', /v=builtin$/);
    expect(pageErrors).toEqual([]);
});

test('exercise pictures remain available in history and show unavailable images clearly', async ({page}, testInfo) => {
    const exercises = [{id: 1, name: 'Push-up', description: 'Keep the body straight.', trackingMode: 'REPS', exerciseType: 'TRAINING', imageUrl: '/api/workout-exercises/1/image?v=push-up'}];
    const workout = {id: 7, workoutDate: '2026-09-01', note: '', lines: [{exerciseId: 1, exerciseName: 'Push-up', exerciseDescription: 'Keep the body straight.', trackingMode: 'REPS', exerciseType: 'TRAINING', position: 0, sets: [{position: 0, repetitions: 8, weight: 0}], intervals: []}]};
    await mockAuthenticatedWorkouts(page, [workout], exercises);
    let fail = false;
    await page.route('**/api/workout-exercises/1/image?*', route => fail ? route.fulfill({status: 404}) : route.fulfill({contentType: 'image/jpeg', path: 'backend/src/main/resources/exercise-images/push-up.jpg'}));
    await openSpaRoute(page, '/workouts');
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 950});
        if (width === 390) await page.locator('.mobile-diary-summary').click();
        const diary = page.locator(width === 390 ? '.diary-mobile' : '.diary-desktop');
        await diary.getByRole('button', {name: 'View picture of Push-up'}).click();
        const viewer = page.getByRole('dialog', {name: 'Push-up', exact: true});
        await expect(viewer.locator('img')).toHaveJSProperty('naturalWidth', 1254);
        await viewer.getByRole('button', {name: 'Close', exact: true}).last().click();
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`exercise-picture-history-${width}.png`)});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }
    fail = true;
    exercises[0].imageUrl += '-missing';
    await openSpaRoute(page, '/workouts');
    await page.getByRole('tab', {name: 'Exercises', exact: true}).click();
    await expect(page.getByRole('button', {name: 'View picture of Push-up'})).toContainText('Picture unavailable');
});

for (const width of [390, 1280]) {
    test(`expanded stretching catalog pictures and workout selection at ${width}px`, async ({page}, testInfo) => {
        test.setTimeout(45000);
        const migration = ['V64__expand_stretching_catalog.sql', 'V66__add_yoga_and_mobility_exercises.sql', 'V74__add_standing_and_table_stretches.sql', 'V78__add_single_leg_reclining_hero_pose.sql'].map(file => require('node:fs').readFileSync(`backend/src/main/resources/db/migration/${file}`, 'utf8')).join('\n');
        const exercises = [...migration.matchAll(/select '((?:''|[^'])*)' as name, '((?:''|[^'])*)' as description, '([^']+)' as image_key/g)].map((match, index) => ({
            id: index + 1, name: match[1].replaceAll("''", "'"), description: match[2].replaceAll("''", "'"), imageUrl: `/api/workout-exercises/${index + 1}/image?v=${match[3]}`,
            trackingMode: 'SECONDS', exerciseType: 'STRETCHING'
        }));
        expect(exercises).toHaveLength(27);
        await mockAuthenticatedWorkouts(page, [], exercises);
        await page.route('**/api/workout-exercises/*/image?*', route => route.fulfill({contentType: 'image/jpeg', path: `backend/src/main/resources/exercise-images/${new URL(route.request().url()).searchParams.get('v')}.jpg`}));
        await page.setViewportSize({width, height: 950});
        await openSpaRoute(page, '/workouts');
        await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
        const panel = page.getByRole('tabpanel');
        for (const [index, exercise] of exercises.entries()) {
            if (index > 0 && index % 10 === 0) await panel.locator('.p-paginator-next').click();
            await panel.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true}).click();
            const viewer = page.getByRole('dialog', {name: exercise.name, exact: true});
            await expect(viewer.getByText(exercise.description, {exact: true})).toBeVisible();
            await expect.poll(() => viewer.locator('img').evaluate(image => image.naturalWidth)).toBeGreaterThan(500);
            expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
            if (exercise.name === 'Lying figure-four stretch') await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`figure-four-picture-${width}.png`)});
            if (index === 12 || index >= 22) await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`expanded-stretching-picture-${index}-${width}.png`)});
            await viewer.getByRole('button', {name: 'Close', exact: true}).last().click();
            await expect(viewer).toBeHidden();
        }
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`expanded-stretching-catalog-${width}.png`)});
        await page.getByRole('tab', {name: 'Diary', exact: true}).click();
        await panel.getByRole('button', {name: 'New', exact: true}).click();
        const workout = page.getByRole('dialog', {name: 'Workout', exact: true});
        for (const exercise of [exercises[12], ...exercises.slice(22)]) {
            await workout.getByRole('button', {name: 'Add stretching', exact: true}).click();
            const card = workout.locator('.workout-line-card').last();
            await card.locator('.p-dropdown').first().click();
            const option = page.getByRole('option', {name: exercise.name, exact: true});
            await option.click();
            await card.getByLabel('Mode', {exact: true}).click();
            await page.getByRole('option', {name: 'Time', exact: true}).click();
            await expect(card.getByLabel('Minutes', {exact: true})).toBeVisible();
            await card.locator('.segment-card .p-dropdown').click();
            await page.getByRole('option', {name: '30', exact: true}).click();
            await card.getByRole('button', {name: `View picture of ${exercise.name}`, exact: true}).click();
            const viewer = page.getByRole('dialog', {name: exercise.name, exact: true});
            await expect(viewer.locator('img')).toHaveJSProperty('naturalWidth', 1254);
            await viewer.getByRole('button', {name: 'Close', exact: true}).last().click();
            await expect(viewer).toBeHidden();
        }
        await workout.locator('.workout-line-card').first().getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`standing-table-stretching-workout-${width}.png`)});
        const savedRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
        await workout.getByRole('button', {name: 'Save', exact: true}).click();
        const saved = (await savedRequest).postDataJSON();
        expect(saved.lines).toHaveLength(6);
        expect(saved.lines).toMatchObject([13, 23, 24, 25, 26, 27].map(exerciseId => ({exerciseId, segments: [{durationSeconds: 30}]})));
        await expect(workout).toBeHidden();
    });
}

async function mockUrgePause(page, {initialPause = null, requiresLogin = false} = {}) {
    await mockAuthenticatedDashboard(page, '2026-08-11', {requiresLogin});
    const state = {pause: initialPause, now: Date.parse('2026-08-12T10:00:00Z'), nextId: 2, finishes: [], decisions: [], starts: [], fail: false, delayCheckIn: false};
    await page.clock.install({time: new Date(state.now)});
    await page.route('**/api/urge-pauses**', async route => {
        const request = route.request();
        if (request.method() === 'POST') {
            if (state.fail) return route.fulfill({status: 503, body: 'Please try again'});
            const path = new URL(request.url()).pathname;
            const body = request.postDataJSON();
            if (path.endsWith('/urge-pauses')) {
                state.starts.push(body);
                state.pause = {id: state.nextId++, description: body.description, startedAt: new Date(state.now).toISOString(), endsAt: new Date(state.now + 900000).toISOString(), pausedAt: null, status: 'ACTIVE', answer: null};
            } else if (path.endsWith('/pause')) {
                state.pause.status = 'PAUSED';
                state.pause.pausedAt = new Date(state.now).toISOString();
            } else if (path.endsWith('/resume')) {
                const remaining = Date.parse(state.pause.endsAt) - Date.parse(state.pause.pausedAt);
                state.pause.endsAt = new Date(state.now + remaining).toISOString();
                state.pause.pausedAt = null;
                state.pause.status = 'ACTIVE';
            } else if (path.endsWith('/cancel')) state.pause = null;
            else if (path.endsWith('/check-in')) {
                if (state.delayCheckIn) await new Promise(resolve => { state.releaseCheckIn = resolve; });
                state.pause.answer = body.answer;
                if (body.answer === 'NOT_ANYMORE') {
                    const result = {id: state.decisions.length + 1, date: new Date(state.now).toISOString().slice(0, 10), outcome: 'WIN', reason: state.pause.description};
                    state.decisions.push(result);
                    state.pause = null;
                    return route.fulfill({json: {pause: null, serverNow: new Date(state.now).toISOString(), decisionOutcome: {result, recordAchievements: []}}});
                }
            }
            else if (path.endsWith('/repeat')) state.pause = {...state.pause, id: state.nextId++, startedAt: new Date(state.now).toISOString(), endsAt: new Date(state.now + 900000).toISOString(), pausedAt: null, status: 'ACTIVE', answer: null};
            else if (path.endsWith('/finish')) {
                state.finishes.push(body);
                state.pause = null;
                return route.fulfill({json: {result: body.outcome ? {id: 1, ...body} : null, recordAchievements: []}});
            }
        }
        return route.fulfill({json: {pause: state.pause, serverNow: new Date(state.now).toISOString(), decisionOutcome: null}});
    });
    state.advance = async milliseconds => { state.now += milliseconds; await page.clock.fastForward(milliseconds); };
    return state;
}

function expiredUrgePause(description = 'Sweets') {
    return {id: 1, description, startedAt: '2026-08-12T09:40:00Z', endsAt: '2026-08-12T09:55:00Z', pausedAt: null, status: 'ACTIVE', answer: null};
}

test('15-minute pause starts, pauses across reload, resumes, repeats and finishes without a decision', async ({page}) => {
    const state = await mockUrgePause(page);
    await page.goto('/');
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await page.getByRole('button', {name: 'Wait 15 minutes', exact: true}).click();
    await page.getByRole('button', {name: 'Start', exact: true}).click();
    await expect(page.getByLabel('Time remaining')).toHaveText('15:00');
    expect(state.starts).toEqual([{description: null}]);
    await state.advance(2 * 60000);
    await expect(page.getByLabel('Time remaining')).toHaveText('13:00');
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Pause timer', exact: true})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Cancel pause', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Pause timer', exact: true}).click();
    await expect(page.getByLabel('Pause time remaining')).toHaveText('13:00 · Paused');
    await state.advance(8 * 60000);
    await expect(page.getByLabel('Pause time remaining')).toHaveText('13:00 · Paused');
    await page.reload();
    await expect(page.getByRole('button', {name: 'Timer paused, 13:00 remaining', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Resume timer', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Resume timer', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Time remaining'})).toHaveText('13:00');
    await state.advance(10 * 60000);
    await expect(page.getByRole('button', {name: 'Time remaining'})).toHaveText('03:00');
    await state.advance(3 * 60000);
    const dialog = page.getByRole('dialog', {name: '15 minutes are up'});
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', {name: 'Still want to', exact: true}).click();
    await expect(dialog.getByRole('button', {name: 'Still want to', exact: true})).toHaveAttribute('aria-pressed', 'true');
    expect(state.finishes).toEqual([]);
    await dialog.getByRole('button', {name: 'Wait another 15 minutes'}).click();
    await expect(page.getByLabel('Time remaining')).toHaveText('15:00');
    await state.advance(15 * 60000);
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', {name: 'Not anymore'}).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByLabel('Time remaining', {exact: true})).toHaveCount(0);
    expect(state.decisions).toHaveLength(1);
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Wait 15 minutes', exact: true})).toBeVisible();
    expect(state.finishes).toEqual([]);
    await page.getByRole('button', {name: 'Wait 15 minutes', exact: true}).click();
    await page.getByRole('button', {name: 'Start', exact: true}).click();
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await page.getByRole('button', {name: 'Pause timer', exact: true}).click();
    await page.getByRole('button', {name: 'Cancel pause', exact: true}).click();
    await expect(page.getByLabel('Time remaining', {exact: true})).toHaveCount(0);
});

test('15-minute pause retains a failed Not anymore check-in and records one linked WIN', async ({page}) => {
    const state = await mockUrgePause(page);
    await page.goto('/');
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await page.getByRole('button', {name: 'Wait 15 minutes', exact: true}).click();
    await page.getByLabel('What are you craving or tempted to do? (optional)').fill('Chocolate after lunch');
    state.fail = true;
    const start = page.getByRole('dialog', {name: 'Wait 15 minutes', exact: true});
    await start.getByRole('button', {name: 'Start', exact: true}).click();
    await expect(start.getByRole('alert')).toHaveText('Please try again');
    await expect(start.getByRole('textbox')).toHaveValue('Chocolate after lunch');
    state.fail = false;
    await start.getByRole('button', {name: 'Start', exact: true}).click();
    await expect(page.getByLabel('Time remaining')).toHaveText('15:00');
    const checkin = page.getByRole('dialog', {name: '15 minutes are up'});
    await state.advance(900000);
    await expect(checkin).toBeVisible();
    state.fail = true;
    await checkin.getByRole('button', {name: 'Not anymore'}).click();
    await expect(checkin.getByRole('alert')).toHaveText('Please try again');
    await expect(checkin).toBeVisible();
    state.fail = false;
    state.delayCheckIn = true;
    const recordWin = checkin.getByRole('button', {name: 'Not anymore'});
    const request = recordWin.click();
    await expect(checkin.getByRole('button', {name: 'Recording WIN…', exact: true})).toHaveAttribute('aria-busy', 'true');
    state.releaseCheckIn();
    await request;
    await expect(checkin).toBeHidden();
    await expect(page.getByText('WIN recorded', {exact: true})).toBeVisible();
    expect(state.decisions).toEqual([{id: 1, date: '2026-08-12', outcome: 'WIN', reason: 'Chocolate after lunch'}]);
    expect(state.finishes).toEqual([]);
});

test('15-minute pause notification restores its check-in after login and stale links preserve the active timer', async ({page}) => {
    const state = await mockUrgePause(page, {initialPause: expiredUrgePause(), requiresLogin: true});
    await page.goto('/?urgePauseId=1');
    await expect(page).toHaveURL('/login?urgePauseId=1');
    await page.getByRole('button', {name: 'Sign in with Google'}).click();
    const dialog = page.getByRole('dialog', {name: '15 minutes are up'});
    await expect(dialog).toBeVisible();
    await expect(page).toHaveURL('/');
    await dialog.getByRole('button', {name: 'Wait another 15 minutes'}).click();
    await expect(page.getByLabel('Time remaining')).toHaveText('15:00');
    await page.goto('/?urgePauseId=1');
    await expect(page.getByLabel('Time remaining')).toHaveText('15:00');
    await expect(dialog).toBeHidden();
    expect(state.pause.id).toBe(2);
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await page.getByRole('button', {name: 'Cancel pause', exact: true}).click();
    await page.getByRole('dialog', {name: 'Pause or record'}).getByRole('button', {name: 'Close', exact: true}).click();
    await expect(page.getByLabel('Time remaining', {exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Wait 15 minutes', exact: true})).toBeVisible();
});

for (const width of [390, 575, 640, 960, 1280]) {
    test(`15-minute pause dialogs fit ${width}px and support keyboard access`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockUrgePause(page, {initialPause: expiredUrgePause('A long description '.repeat(20) + 'x'.repeat(100))});
        await page.goto('/?urgePauseId=1');
        const dialog = page.getByRole('dialog', {name: '15 minutes are up'});
        await expect(dialog).toBeVisible();
        await dialog.getByRole('button', {name: 'Still want to', exact: true}).focus();
        await page.keyboard.press('Enter');
        await expect(dialog.getByRole('button', {name: 'Still want to', exact: true})).toHaveAttribute('aria-pressed', 'true');
        await expect(dialog.getByRole('button', {name: 'Still want to', exact: true})).toBeEnabled();
        const box = await dialog.boundingBox();
        expect(box.x).toBeGreaterThanOrEqual(0); expect(box.x + box.width).toBeLessThanOrEqual(width);
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
        await page.screenshot({path: testInfo.outputPath(`urge-pause-${width}.png`), animations: 'disabled'});
        await page.keyboard.press('Escape');
        await expect(dialog).toBeHidden();
        await page.getByRole('button', {name: 'Check in', exact: true}).click();
        await expect(dialog).toBeVisible();
        await page.keyboard.press('Escape');
        await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
        const controls = page.getByRole('dialog', {name: 'Pause or record', exact: true});
        await expect(controls).toBeVisible();
        await expect(controls).not.toHaveClass(/p-dialog-enter-active/);
        const controlsBox = await controls.boundingBox();
        expect(controlsBox.x).toBeGreaterThanOrEqual(0);
        expect(controlsBox.x + controlsBox.width).toBeLessThanOrEqual(width);
        expect(controlsBox.y).toBeGreaterThanOrEqual(0);
        expect(controlsBox.y + controlsBox.height).toBeLessThanOrEqual(900);
        await page.screenshot({path: testInfo.outputPath(`pause-controls-${width}.png`), animations: 'disabled'});
        await page.keyboard.press('Escape');
        await expect(page.getByRole('dialog', {name: 'Pause or record', exact: true})).toBeHidden();
        await page.screenshot({path: testInfo.outputPath(`pause-header-${width}.png`), animations: 'disabled'});
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    });
}


test('15-minute pause persists away from the dashboard and reconciles another device on focus', async ({page}) => {
    const state = await mockUrgePause(page);
    await page.goto('/');
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await page.getByRole('button', {name: 'Wait 15 minutes', exact: true}).click();
    await page.getByRole('button', {name: 'Start', exact: true}).click();
    await expect(page.getByLabel('Time remaining')).toHaveText('15:00');
    await page.locator('.app-menubar .p-menubar-button').click();
    await page.locator('.app-menubar').getByText('Track', {exact: true}).click();
    await page.locator('.app-menubar').getByText('Weight', {exact: true}).click();
    await expect(page).toHaveURL('/weights');
    await expect(page.getByRole('region', {name: '15-minute rule'})).toHaveCount(0);
    await state.advance(900000);
    await page.goto('/');
    await page.getByRole('button', {name: 'Check in', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: '15 minutes are up'});
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', {name: 'Close', exact: true}).click();
    state.pause = null;
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(page.getByLabel('Time remaining', {exact: true})).toHaveCount(0);
    await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Wait 15 minutes', exact: true})).toBeVisible();
});

for (const outcome of ['WIN', 'MISS']) {
    test(`15-minute header saves ${outcome} during countdown and finishes the linked pause`, async ({page}) => {
        const state = await mockUrgePause(page);
        const saves = [];
        await page.route('**/api/decision-outcomes', async route => {
            const body = route.request().postDataJSON();
            saves.push(body);
            await route.fulfill({json: {result: {id: 1, ...body}, recordAchievements: []}});
        });
        await page.goto('/');
        await expect(page.getByRole('region', {name: '15-minute rule'})).toHaveCount(0);
        await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
        await page.getByRole('button', {name: 'Wait 15 minutes', exact: true}).click();
        await page.getByRole('button', {name: 'Start', exact: true}).click();
        await expect(page.getByLabel('Time remaining', {exact: true})).toHaveText('15:00');
        await page.locator('.app-menubar .p-menubar-button').click();
        await page.locator('.app-menubar').getByText('Track', {exact: true}).click();
        await page.locator('.app-menubar').getByText('Weight', {exact: true}).click();
        await expect(page).toHaveURL('/weights');
        await page.getByRole('button', {name: 'Pause or record', exact: true}).click();
        await expect(page.getByLabel('Pause time remaining', {exact: true})).toHaveText('15:00');
        await page.getByRole('button', {name: outcome, exact: true}).click();
        const dialog = page.getByRole('dialog', {name: `Record ${outcome}`, exact: true});
        await expect(dialog).toContainText('12/08/2026');
        await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
        expect(saves).toEqual([]);
        await expect(page.getByRole('dialog', {name: 'Pause or record', exact: true})).toBeVisible();
        await expect(page.getByLabel('Pause time remaining')).toHaveText('15:00');
        await page.getByRole('button', {name: outcome, exact: true}).click();
        await dialog.getByLabel('Reason (optional)').fill('A decision during the countdown');
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog).toBeHidden();
        expect(saves).toEqual([]);
        expect(state.finishes).toEqual([{outcome, reason: 'A decision during the countdown'}]);
        expect(state.pause).toBeNull();
        await page.goto('/');
        await expect(page.getByLabel('Time remaining', {exact: true})).toHaveCount(0);
    });
}

async function decisionButtonAppearance(container) {
    return container.locator('.decision-outcome-actions button').evaluateAll(buttons => buttons.map(button => {
        const style = getComputedStyle(button);
        return {label: button.textContent.trim(), icon: button.querySelector('.p-button-icon').className,
            width: style.width, height: style.height, background: style.backgroundColor, color: style.color,
            border: style.border, font: style.font, outlined: button.classList.contains('p-button-outlined')};
    }));
}

for (const width of [390, 575, 640, 960, 1280]) {
    test(`pause actions match the Wins panel and stay centered at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        const state = await mockUrgePause(page);
        await page.goto('/');
        await page.getByRole('tab', {name: 'Wins', exact: true}).click();
        const panel = page.locator('.wins-and-misses-header');
        await expect(panel.getByRole('button', {name: 'WIN', exact: true})).toBeVisible();
        const reference = await decisionButtonAppearance(panel);
        expect(reference.map(button => button.label)).toEqual(['WIN', 'MISS']);
        for (const button of reference) {
            expect(button.width).toBe('112px');
            expect(button.outlined).toBe(false);
            expect(button.background).not.toBe('rgba(0, 0, 0, 0)');
        }
        await panel.screenshot({path: testInfo.outputPath(`wins-reference-${width}.png`), animations: 'disabled'});
        const trigger = page.getByRole('button', {name: 'Pause or record', exact: true});
        await expect(trigger.locator('.pi-flag')).toHaveCount(1);
        await expect(trigger).toHaveAttribute('title', 'Pause or record');
        const coach = page.getByRole('button', {name: 'Open Coach', exact: true});
        const iconButtons = [trigger];
        if (width <= 575) iconButtons.push(coach);
        if (width <= 575) iconButtons.push(page.getByRole('link', {name: 'Agenda', exact: true}));
        for (const button of iconButtons) {
            const appearance = await button.evaluate(element => {
                const box = element.getBoundingClientRect();
                const icon = element.querySelector('.p-button-icon');
                const iconBox = icon.getBoundingClientRect();
                return {width: box.width, height: box.height, rem: parseFloat(getComputedStyle(document.documentElement).fontSize),
                    iconSize: parseFloat(getComputedStyle(icon).fontSize),
                    offsetX: iconBox.x + iconBox.width / 2 - box.x - box.width / 2,
                    offsetY: iconBox.y + iconBox.height / 2 - box.y - box.height / 2};
            });
            expect(appearance.width).toBeCloseTo(appearance.rem * 2.357, 1);
            expect(appearance.height).toBe(appearance.width);
            expect(appearance.iconSize).toBe(appearance.rem);
            expect(Math.abs(appearance.offsetX)).toBeLessThan(1);
            expect(Math.abs(appearance.offsetY)).toBeLessThan(1);
        }
        await expect(coach.locator('.p-button-label')).toBeVisible({visible: width > 575});
        await page.locator('.app-header-actions').screenshot({path: testInfo.outputPath(`icon-buttons-${width}.png`)});
        await trigger.focus();
        await expect(trigger).toBeFocused();
        expect(await trigger.evaluate(element => getComputedStyle(element).boxShadow)).not.toBe('none');
        await trigger.press('Enter');
        const controls = page.getByRole('dialog', {name: 'Pause or record', exact: true});
        await expect(controls).not.toHaveClass(/p-dialog-enter-active/);
        expect(await decisionButtonAppearance(controls)).toEqual(reference);
        const wait = controls.getByRole('button', {name: 'Wait 15 minutes', exact: true});
        const waitBox = await wait.boundingBox();
        const winBox = await controls.getByRole('button', {name: 'WIN', exact: true}).boundingBox();
        const missBox = await controls.getByRole('button', {name: 'MISS', exact: true}).boundingBox();
        const box = await controls.boundingBox();
        expect(Math.abs(waitBox.x + waitBox.width / 2 - (box.x + box.width / 2))).toBeLessThan(1);
        expect(Math.abs((winBox.x + missBox.x + missBox.width) / 2 - (box.x + box.width / 2))).toBeLessThan(1);
        expect(winBox.y).toBeCloseTo(missBox.y, 0);
        expect(winBox.y - (waitBox.y + waitBox.height)).toBeCloseTo(16, 0);
        await page.screenshot({path: testInfo.outputPath(`pause-idle-${width}.png`), animations: 'disabled'});
        await wait.click();
        await page.getByRole('button', {name: 'Start', exact: true}).click();
        await expect(page.getByLabel('Time remaining', {exact: true})).toHaveText('15:00');
        await trigger.click();
        await expect(controls).not.toHaveClass(/p-dialog-enter-active/);
        await expect(controls.getByLabel('Pause time remaining', {exact: true})).toHaveText('15:00');
        expect(await decisionButtonAppearance(controls)).toEqual(reference);
        await page.screenshot({path: testInfo.outputPath(`pause-running-${width}.png`), animations: 'disabled'});
        await page.keyboard.press('Escape');
        await state.advance(900000);
        const checkin = page.getByRole('dialog', {name: '15 minutes are up', exact: true});
        await expect(checkin).toBeVisible();
        await checkin.screenshot({path: testInfo.outputPath(`pause-checkin-${width}.png`), animations: 'disabled'});
        await checkin.getByRole('button', {name: 'Still want to', exact: true}).click();
        await expect(checkin.getByRole('button', {name: 'WIN', exact: true})).toBeEnabled();
        expect(await decisionButtonAppearance(checkin)).toEqual(reference);
        await page.screenshot({path: testInfo.outputPath(`pause-completed-${width}.png`), animations: 'disabled'});
        expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    });
}

for (const width of [390, 1280]) {
    test(`modernization baseline captures representative workflows at ${width}px`, async ({page, browser}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await page.clock.setFixedTime(new Date('2026-09-10T12:00:00Z'));
        await mockAuthenticatedDashboard(page, '2026-09-10');
        const diagnostics = {browser: browser.version(), viewport: {width, height: 900}, pageErrors: [], consoleWarnings: [], failedRequests: []};
        page.on('pageerror', error => diagnostics.pageErrors.push(error.message));
        page.on('console', message => { if (['warning', 'error'].includes(message.type())) diagnostics.consoleWarnings.push(message.text()); });
        page.on('requestfailed', request => diagnostics.failedRequests.push({url: request.url(), failure: request.failure()}));
        const capture = async (name, locator = page) => {
            await locator.screenshot({path: testInfo.outputPath(`baseline-${name}-${width}.png`), animations: 'disabled'});
        };
        await page.goto('/');
        await expect(page.getByText('Dashboard Date', {exact: true})).toBeVisible();
        await capture('dashboard');
        const trigger = page.getByRole('button', {name: 'Pause or record', exact: true});
        await trigger.focus();
        await page.keyboard.press('Enter');
        const pause = page.getByRole('dialog', {name: 'Pause or record', exact: true});
        await expect(pause).toBeVisible();
        await expect(pause).not.toHaveClass(/p-dialog-enter-active/);
        await capture('dialog', pause);
        await page.keyboard.press('Escape');
        await expect(pause).toBeHidden();
        diagnostics.pauseFocusRestored = await trigger.evaluate(element => element === document.activeElement);
        await expect(trigger).toBeVisible();
        await page.locator('.dashboard-charts-trigger').scrollIntoViewIfNeeded();
        const charts = page.locator('.dashboard-charts');
        await expect(charts.locator('canvas').first()).toBeVisible();
        await capture('charts', charts);
        await openSpaRoute(page, '/weights');
        const table = page.locator('.p-datatable');
        await expect(table).toBeVisible();
        await capture('table');
        await page.getByRole('button', {name: 'New', exact: true}).click();
        const weight = page.getByRole('dialog', {name: 'Weight', exact: true});
        await expect(weight).toBeVisible();
        await expect(weight).not.toHaveClass(/p-dialog-enter-active/);
        await weight.locator('.p-calendar input').click();
        await expect(page.locator('.p-datepicker')).toBeVisible();
        await expect(page.locator('.p-datepicker')).not.toHaveClass(/p-connected-overlay-enter-active/);
        await capture('calendar');
        await page.keyboard.press('Escape');
        const photo = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="240" height="320"><rect width="240" height="320" fill="#dcebf5"/><circle cx="120" cy="95" r="35" fill="#497c9a"/><rect x="75" y="140" width="90" height="125" rx="25" fill="#497c9a"/><text x="120" y="305" text-anchor="middle">Synthetic fixture</text></svg>');
        const writes = [];
        page.on('request', request => { if (request.method() === 'POST' && request.url().includes('/api/weights')) writes.push(request.url()); });
        await weight.locator('input[type=file]').first().setInputFiles({name: 'synthetic-front.svg', mimeType: 'image/svg+xml', buffer: photo});
        await expect(weight.locator('img')).toBeVisible();
        await expect(weight.locator('img')).toHaveJSProperty('naturalWidth', 240);
        await capture('upload', weight);
        expect(writes).toEqual([]);
        await weight.getByRole('button', {name: 'Cancel', exact: true}).click();
        await expect(weight).toBeHidden();
        expect(writes).toEqual([]);
        await page.getByRole('button', {name: 'New', exact: true}).click();
        await expect(weight.locator('img')).toHaveCount(0);
        await weight.getByRole('button', {name: 'Cancel', exact: true}).click();
        await page.route('**/api/weights', route => route.fulfill({json: [{id: 1, date: '2026-09-10T12:00:00Z', weight: 80, fatPercentage: 20, muscle: 30, photoFront: '/baseline-photo.svg'}]}));
        await page.route('**/baseline-photo.svg', route => route.fulfill({contentType: 'image/svg+xml', body: photo}));
        await openSpaRoute(page, '/photos');
        await expect(page.locator('.carousel img')).toBeVisible();
        await expect(page.locator('.carousel img')).toHaveJSProperty('naturalWidth', 240);
        await capture('photos');
        await testInfo.attach('baseline-diagnostics', {body: JSON.stringify(diagnostics, null, 2), contentType: 'application/json'});
        require('node:fs').writeFileSync(testInfo.outputPath(`baseline-diagnostics-${width}.json`), JSON.stringify(diagnostics, null, 2));
        expect(diagnostics.pageErrors).toEqual([]);
    });
}

test('notification panel centers empty and long lists after viewport changes', async ({page}) => {
    await mockRoutineReminderHome(page, [], {initialNotifications: swipeNotifications(12)});
    await openSpaRoute(page, '/');
    for (const width of [1280, 390]) {
        await page.setViewportSize({width, height: 500});
        await page.getByRole('button', {name: '12 pending notifications'}).click();
        await expectCenteredNotifications(page);
        const list = page.locator('.notification-list');
        expect(await list.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true);
        await page.keyboard.press('Escape');
        await expect(page.locator('.notification-panel')).toBeHidden();
    }
    await page.getByRole('button', {name: '12 pending notifications'}).click();
    await page.getByRole('button', {name: 'Dismiss all', exact: true}).click();
    await page.getByRole('button', {name: '0 pending notifications'}).click();
    await expectCenteredNotifications(page);
    await expect(page.locator('.notification-panel')).toContainText('No pending notifications.');
    await page.setViewportSize({width: 575, height: 600});
    await expectCenteredNotifications(page);
    await page.screenshot({path: test.info().outputPath('notification-centered-empty.png'), animations: 'disabled'});
});

test('saved stretching sets manage ordered holds and copy only missing exercises', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Wall calf stretch', description: 'Hold each side.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING', imageUrl: '/api/workout-exercises/1/image?v=calf'},
        {id: 2, name: 'Seated hamstring stretch with a deliberately long descriptive name', description: 'Hold comfortably.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await page.route('**/api/workout-exercises/1/image?*', route => route.fulfill({contentType: 'image/jpeg', path: 'backend/src/main/resources/exercise-images/wall-calf-stretch.jpg'}));
    let sets = [], failSave = false;
    await page.route('**/api/stretching-sets**', async route => {
        const request = route.request();
        if (request.method() === 'GET') return route.fulfill({json: sets});
        if (request.method() === 'DELETE') { sets = []; return route.fulfill({status: 204}); }
        expect(request.method()).toBe(sets.length ? 'PUT' : 'POST');
        expect(new URL(request.url()).pathname).toBe(sets.length ? '/api/stretching-sets/1' : '/api/stretching-sets');
        expect(Object.keys(request.postDataJSON()).sort()).toEqual(['entries', 'name']);
        if (failSave) return route.fulfill({status: 400, body: 'Stretching set name already exists'});
        const saved = {...request.postDataJSON(), id: 1};
        sets = [saved];
        return route.fulfill({json: saved});
    });
    await openSpaRoute(page, '/workouts');
    await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
    const section = page.getByRole('region', {name: 'Saved stretching sets'});
    await expect(section).toContainText('No saved stretching sets yet.');
    await section.getByRole('button', {name: 'New set', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Stretching set', exact: true});
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toContainText('Name is required');
    await editor.getByLabel('Name', {exact: true}).fill('Morning mobility');
    await editor.locator('.p-multiselect').click();
    const illustratedOption = page.getByRole('option', {name: exercises[0].name, exact: true});
    const plainOption = page.getByRole('option', {name: exercises[1].name, exact: true});
    await expect(illustratedOption.locator('img')).toBeVisible();
    await expect.poll(() => illustratedOption.locator('img').evaluate(img => img.naturalWidth)).toBeGreaterThan(0);
    await expect(plainOption.locator('img')).toHaveCount(0);
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.keyboard.press('Escape');
        await page.setViewportSize({width, height: 900});
        await editor.locator('.p-multiselect').click();
        const panel = page.locator('.p-multiselect-panel');
        await expect(panel).toBeVisible();
        expect(await panel.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        const bounds = await panel.boundingBox();
        expect(bounds.x).toBeGreaterThanOrEqual(0);
        expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`stretching-selector-${width}.png`)});
    }
    await illustratedOption.locator('img').click();
    await expect(illustratedOption).toHaveAttribute('aria-selected', 'true');
    const filter = page.locator('.p-multiselect-filter');
    await filter.fill('Seated hamstring');
    await expect(illustratedOption).toHaveCount(0);
    await expect(plainOption).toBeVisible();
    await filter.press('ArrowDown');
    await page.keyboard.press('Enter');
    await expect(plainOption).toHaveAttribute('aria-selected', 'true');
    await page.keyboard.press('Escape');
    await editor.getByLabel('Mode', {exact: true}).first().click();
    await page.locator('.p-dropdown-panel').last().getByRole('option', {name: 'Time', exact: true}).click();
    await editor.getByLabel('Mode', {exact: true}).nth(1).click();
    await page.locator('.p-dropdown-panel').last().getByRole('option', {name: 'Time', exact: true}).click();
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor.getByText('Enter a duration for this hold', {exact: true})).toHaveCount(2);
    await editor.locator('.set-hold .p-dropdown').nth(0).click();
    await page.getByRole('option', {name: '30', exact: true}).click();
    await expect(page.getByRole('option', {name: '30', exact: true})).toBeHidden();
    await editor.locator('.set-hold .p-dropdown').nth(1).click();
    await page.getByRole('option', {name: '20', exact: true}).click();
    await expect(page.getByRole('option', {name: '20', exact: true})).toBeHidden();
    await editor.getByRole('button', {name: 'Add hold', exact: true}).first().click();
    await editor.locator('.set-hold .p-dropdown').nth(1).click();
    await page.getByRole('option', {name: '45', exact: true}).click();
    await expect(page.getByRole('option', {name: '45', exact: true})).toBeHidden();
    await editor.getByRole('button', {name: 'Move stretch 2 up', exact: true}).click();
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 1100});
        expect(await editor.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`saved-stretching-editor-${width}.png`)});
    }
    failSave = true;
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toContainText('Stretching set name already exists');
    await expect(editor.getByLabel('Name', {exact: true})).toHaveValue('Morning mobility');
    failSave = false;
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    expect(sets[0].entries).toEqual([{exerciseId: 2, durations: [20], stretchingUnit: 'SECONDS', breaths: []}, {exerciseId: 1, durations: [30, 45], stretchingUnit: 'SECONDS', breaths: []}]);
    await section.getByRole('button', {name: 'Edit stretching set Morning mobility', exact: true}).click();
    await editor.getByLabel('Name', {exact: true}).fill('Discarded');
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(section).toContainText('Morning mobility');
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 1100});
        await section.getByRole('button', {name: 'Edit stretching set Morning mobility', exact: true}).click();
        await editor.getByLabel('Name', {exact: true}).fill('Updated mobility');
        await editor.locator('.set-hold .p-dropdown').first().click();
        await page.getByRole('option', {name: '25', exact: true}).click();
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(editor).toBeHidden();
        expect(sets[0]).toEqual({id: 1, name: 'Updated mobility', entries: [{exerciseId: 2, durations: [25], stretchingUnit: 'SECONDS', breaths: []}, {exerciseId: 1, durations: [30, 45], stretchingUnit: 'SECONDS', breaths: []}]});
        await section.getByRole('button', {name: 'Edit stretching set Updated mobility', exact: true}).click();
        await expect(editor.getByLabel('Name', {exact: true})).toHaveValue('Updated mobility');
        await expect(editor.locator('.set-hold .p-dropdown').first()).toContainText('25');
        expect(await editor.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`saved-stretching-updated-${width}.png`)});
        await editor.getByLabel('Name', {exact: true}).fill('Morning mobility');
        await editor.locator('.set-hold .p-dropdown').first().click();
        await page.getByRole('option', {name: '20', exact: true}).click();
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(editor).toBeHidden();
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`saved-stretching-list-${width}.png`)});
    }
    await page.getByRole('tab', {name: 'Diary', exact: true}).click();
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const workout = page.getByRole('dialog', {name: 'Workout', exact: true});
    await workout.getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
    const picker = page.getByRole('dialog', {name: 'Add stretching set', exact: true});
    async function pick() {
        await workout.getByRole('button', {name: 'Add stretching set', exact: true}).click();
        await picker.locator('.p-dropdown').click();
        await page.getByRole('option', {name: 'Morning mobility', exact: true}).click();
    }
    await pick();
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 1100});
        await expect.poll(() => page.evaluate(() => window.innerWidth)).toBe(width);
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        expect(await picker.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`saved-stretching-picker-${width}.png`)});
    }
    await picker.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(workout.locator('.workout-line-card')).toHaveCount(0);
    await pick();
    await picker.getByRole('button', {name: 'Add', exact: true}).click();
    await expect(page.locator('.p-toast-message-success').filter({hasText: 'Stretching set added'}).last()).toBeVisible();
    await expect(workout.locator('p[role="status"].stretching-notice')).toHaveCount(0);
    await expect(workout.locator('.workout-line-card')).toHaveCount(2);
    await workout.getByRole('button', {name: /^Expand Stretching 2/}).click();
    await expect(workout.locator('.workout-line-card').nth(1).locator('.segment-card .p-dropdown').first()).toContainText('30');
    await workout.locator('.workout-line-card').nth(1).getByLabel('Minutes', {exact: true}).first().fill('1');
    await workout.getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
    await page.setViewportSize({width: 390, height: 1100});
    await pick();
    await picker.getByRole('button', {name: 'Add', exact: true}).click();
    const partialGrowl = page.locator('.p-toast-message-success').filter({hasText: 'Stretching set added'}).last();
    await expect(partialGrowl).toContainText('Already present: Wall calf stretch. Existing holds were kept.');
    const partialGrowlBounds = await partialGrowl.boundingBox();
    expect(partialGrowlBounds.x).toBeGreaterThanOrEqual(0);
    expect(partialGrowlBounds.x + partialGrowlBounds.width).toBeLessThanOrEqual(390);
    await pick();
    await picker.getByRole('button', {name: 'Add', exact: true}).click();
    await expect(page.locator('.p-toast-message-info').filter({hasText: 'No exercises added'}).last()).toContainText('Already present: Seated hamstring stretch with a deliberately long descriptive name, Wall calf stretch. Existing holds were kept.');
    await page.setViewportSize({width: 1280, height: 1100});
    const savedRequest = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await workout.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await savedRequest).postDataJSON().lines.map(line => [line.exerciseId, line.segments.map(hold => hold.durationSeconds)])).toEqual([[1, [90, 45]], [2, [20]]]);
    await expect(workout).toBeHidden();
    await page.getByRole('button', {name: 'Edit workout', exact: true}).click();
    await pick();
    await picker.getByRole('button', {name: 'Add', exact: true}).click();
    await expect(page.locator('.p-toast-message-info').filter({hasText: 'No exercises added'}).last()).toBeVisible();
    await workout.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
    await section.getByRole('button', {name: 'Delete stretching set Morning mobility', exact: true}).click();
    await page.getByRole('dialog', {name: 'Delete stretching set', exact: true}).getByRole('button', {name: 'Delete', exact: true}).click();
    await expect(section).toContainText('No saved stretching sets yet.');
    await page.getByRole('tab', {name: 'Diary', exact: true}).click();
    await expect(page.locator('.diary-desktop')).toContainText('01:30');
});

const savingFeedbackCases = [
    {route: '/weights', api: '/weights', dialog: 'Weight', value: () => reminderWeight('2026-08-10')},
    {route: '/pressures', api: '/blood-pressures', dialog: 'Blood Pressure', value: () => ({id: 1, date: '2026-08-10T08:00:00Z', upper: 120, lower: 80})},
    {route: '/moods', api: '/moods', dialog: 'Mood', value: () => ({id: 1, date: '2026-08-10', period: 'MORNING', value: 3, note: 'Keep this note'})},
    {route: '/sleep', api: '/sleeps', dialog: 'Sleep', value: () => sleepHistory('2026-08-10')[0]},
    {route: '/sicknesses', api: '/sicknesses', dialog: 'Sickness', value: () => ({id: 1, date: '2026-08-10', type: 'COLD', severity: 'LOW', note: 'Keep this note'})},
    {route: '/cholesterol', api: '/lipid-panels', dialog: 'Lipid Panel', value: () => ({id: 1, date: '2026-08-10', totalCholesterol: 180, hdlCholesterol: 50, ldlCholesterol: 100, triglycerides: 100})},
    {route: '/back', api: '/back-pain-episodes', dialog: 'Back check-in', value: () => ({id: 1, date: '2026-08-10', period: 'MORNING', severity: 'NONE', region: null, side: null, note: 'Keep this note'})}
];

for (const {entry, width} of savingFeedbackCases.flatMap(entry => [390, 1280].map(width => ({entry, width})))) {
    test(`saving feedback retains ${entry.dialog} edits after failure at ${width}px`, async ({page}, testInfo) => {
        await page.clock.setFixedTime(new Date('2026-08-20T08:00:00Z'));
        await mockAuthenticatedDashboard(page);
        await page.setViewportSize({width, height: 900});
        const value = entry.value();
        let release;
        let attempts = 0;
        const pending = new Promise(resolve => { release = resolve; });
        await page.route(new RegExp(`/api${entry.api}(/.*)?$`), async route => {
            if (route.request().method() === 'GET') return route.fulfill({json: [value]});
            attempts++;
            if (attempts === 1) { await pending; return route.fulfill({status: 503, body: 'Please try again'}); }
            return route.fulfill({json: ['/sicknesses', '/back-pain-episodes'].includes(entry.api) ? value : {result: value, recordAchievements: []}});
        });
        await openSpaRoute(page, entry.route);
        await page.locator('button:has(.pi-pencil)').first().click();
        const dialog = page.getByRole('dialog', {name: entry.dialog, exact: true});
        const before = await dialog.locator('input, textarea').evaluateAll(inputs => inputs.map(input => input.value));
        const save = dialog.getByRole('button', {name: 'Save', exact: true});
        await save.click();
        const busy = dialog.getByRole('button', {name: 'Saving…', exact: true});
        await expect(busy).toBeDisabled();
        await expect(busy).toHaveAttribute('aria-busy', 'true');
        await expect(dialog.locator('.save-fields')).toHaveAttribute('inert', '');
        await expect(dialog.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
        await busy.dispatchEvent('click');
        await page.keyboard.press('Escape');
        expect(attempts).toBe(1);
        await expect(dialog).toBeVisible();
        await page.screenshot({path: testInfo.outputPath('saving.png'), animations: 'disabled'});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        release();
        await expect(save).toBeEnabled();
        expect(await dialog.locator('input, textarea').evaluateAll(inputs => inputs.map(input => input.value))).toEqual(before);
        await save.click();
        await expect(dialog).toBeHidden();
        expect(attempts).toBe(2);
    });
}

for (const width of [390, 575, 640, 960, 1280]) {
    test(`saving feedback covers workout save and delete at ${width}px`, async ({page}, testInfo) => {
        const exercises = [{id: 1, name: 'Squat with a comfortably long exercise label', description: 'Controlled movement.', trackingMode: 'REPS', exerciseType: 'TRAINING'}];
        const workout = workoutResponse(1, {workoutDate: '2026-08-10', note: 'Preserve this draft', lines: [{exerciseId: 1, calories: null, averageHeartRate: null, segments: [{repetitions: 10, weight: 40}]}]}, exercises);
        await mockAuthenticatedWorkouts(page, [workout], exercises);
        await page.setViewportSize({width, height: 900});
        let release;
        let writes = 0;
        const pending = new Promise(resolve => { release = resolve; });
        await page.route('**/api/workouts/1', async route => {
            writes++;
            if (route.request().method() === 'PUT') { await pending; return route.fallback(); }
            await route.fulfill({status: 503, body: 'Please try again'});
        });
        await openSpaRoute(page, '/workouts');
        if (width <= 575) await page.locator('.mobile-diary-summary').click();
        await page.getByRole('button', {name: 'Edit workout', exact: true}).click();
        const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
        await dialog.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(dialog.getByRole('button', {name: 'Saving…'})).toBeDisabled();
        await expect(dialog.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
        await dialog.getByRole('button', {name: 'Saving…'}).dispatchEvent('click');
        expect(writes).toBe(1);
        await page.screenshot({path: testInfo.outputPath(`workout-saving-${width}.png`), animations: 'disabled'});
        const bounds = await dialog.boundingBox();
        expect(bounds.x).toBeGreaterThanOrEqual(0);
        expect(bounds.x + bounds.width).toBeLessThanOrEqual(width + 1);
        release();
        await expect(dialog).toBeHidden();
        if (width <= 575) await page.locator('.mobile-diary-summary').click();
        page.on('dialog', dialog => dialog.accept());
        await page.getByRole('button', {name: 'Delete workout', exact: true}).click();
        await expect(page.getByText('Please try again', {exact: true})).toBeVisible();
        await expect(page.getByRole('button', {name: 'Delete workout', exact: true})).toBeEnabled();
    });
}

test('saving feedback retries weight photos without recreating the saved entry', async ({page}) => {
    await mockRoutineReminderHome(page, [], {initialWeights: []});
    await openSpaRoute(page, '/weights');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Weight', exact: true});
    await dialog.locator('#weight input').fill('80');
    await dialog.locator('#fat-percentage input').fill('20');
    await dialog.locator('#muscle input').fill('60');
    await dialog.locator('#muscle input').press('Tab');
    const photo = {name: 'photo.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="20" height="20"><rect width="20" height="20" fill="gray"/></svg>')};
    await dialog.locator('input[type=file]').nth(0).setInputFiles(photo);
    await dialog.locator('input[type=file]').nth(0).setInputFiles({...photo, name: 'right.svg'});
    let creates = 0;
    let frontUploads = 0;
    let rightUploads = 0;
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    const saved = {...reminderWeight('2026-08-10'), id: 42};
    await page.route('**/api/weights', async route => {
        if (route.request().method() === 'POST') { creates++; return route.fulfill({json: {result: saved, recordAchievements: []}}); }
        return route.fallback();
    });
    await page.route('**/api/weights/42', route => route.fulfill({json: {result: saved, recordAchievements: []}}));
    await page.route('**/api/weights/42/photos/*', async route => {
        if (route.request().url().endsWith('/front')) { frontUploads++; return route.fulfill({json: {...saved, photoFront: '/front.jpg'}}); }
        rightUploads++;
        if (rightUploads === 1) { await pending; return route.fulfill({status: 503, body: 'Upload unavailable'}); }
        return route.fulfill({json: {...saved, photoFront: '/front.jpg', photoRight: '/right.jpg'}});
    });
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect.poll(() => rightUploads).toBe(1);
    await expect(dialog.getByRole('button', {name: 'Saving…'})).toBeDisabled();
    release();
    await expect(dialog.getByRole('alert')).toContainText('Weight saved, but a photo could not be uploaded');
    await expect(dialog.locator('#weight input')).toHaveValue('80.00');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog).toBeHidden();
    expect({creates, frontUploads, rightUploads}).toEqual({creates: 1, frontUploads: 1, rightUploads: 2});
});

async function mockWeeklyPlans(page, initial = null) {
    const canonicalDay = day => ({day: day.day, rest: day.rest, note: day.rest ? day.note || null : null, sessions: day.sessions || (day.rest ? [] : [{name: null, note: day.note, lines: day.lines || []}])});
    let current = initial ? {...initial, days: initial.days.map(canonicalDay)} : null, archive = [], failSave = false, revision = 1;
    const exercises = [
        {id: 1, name: 'Squat with a deliberately long descriptive exercise name', description: 'Keep the prescribed range of motion.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Exercise bike', description: 'Steady pace.', trackingMode: 'CARDIO', exerciseType: 'WARM_UP'},
        {id: 3, name: 'Wall calf stretch', description: 'Hold each side.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'},
        {id: 4, name: 'Plank', description: 'Hold steadily.', trackingMode: 'SECONDS', exerciseType: 'TRAINING'},
        {id: 9, name: 'Outdoor run', description: 'Run outdoors.', trackingMode: 'CARDIO', cardioMetric: 'SPEED', exerciseType: 'TRAINING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await page.route('**/api/workout-plans**', async route => {
        const request = route.request(), url = new URL(request.url());
        if (request.method() === 'GET') {
            if (url.pathname.endsWith('/current')) return current ? route.fulfill({json: current}) : route.fulfill({status: 204});
            if (/\/\d+$/.test(url.pathname)) return route.fulfill({json: archive.find(plan => plan.id === Number(url.pathname.split('/').pop()))});
            return route.fulfill({json: {items: archive, page: 0, totalElements: archive.length, totalPages: archive.length ? 1 : 0}});
        }
        if (failSave) return route.fulfill({status: 409, body: 'The workout plan changed. Reload it and review your changes before saving again.'});
        const payload = request.postDataJSON(), plan = request.method() === 'POST' ? payload : payload.plan;
        if (request.method() === 'POST' && current) archive.unshift({...current, archivedAt: '2026-09-12T06:00:00Z'});
        const previous = current;
        current = {...plan, id: request.method() === 'POST' ? ++revision : current.id, updateToken: `token-${++revision}`, createdAt: '2026-09-12T06:00:00Z', updatedAt: '2026-09-12T06:00:00Z', archivedAt: null,
            days: plan.days.map(day => ({...day, sessions: day.sessions.map(session => ({...session, lines: session.lines.map(line => {
                const existing = previous?.days.flatMap(day => day.sessions).flatMap(session => session.lines).find(item => item.exerciseId === line.exerciseId);
                const exercise = exercises.find(exercise => exercise.id === line.exerciseId);
                return {...line, exerciseName: existing?.exerciseName || exercise.name, exerciseDescription: existing?.exerciseDescription || exercise.description, exerciseType: existing?.exerciseType || exercise.exerciseType, trackingMode: existing?.trackingMode || exercise.trackingMode};
            })}))}))};
        return route.fulfill({json: current});
    });
    return {get current() { return current; }, setCurrent(value) { current = value; }, get archive() { return archive; }, setFail(value) { failSave = value; }, exercises};
}

for (const planning of [false, true]) {
    test(`workout add actions stay in one full-width column in ${planning ? 'weekly plans' : 'recorded workouts'}`, async ({page}, testInfo) => {
        await mockWeeklyPlans(page);
        await page.route('**/api/stretching-sets', route => route.fulfill({json: []}));
        await page.route('**/workouts*', route => route.request().resourceType() === 'document'
            ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
            : route.fallback());
        await openSpaRoute(page, planning ? '/workouts?tab=plan' : '/workouts');

        async function openEditor() {
            if (planning) {
                const section = page.getByRole('region', {name: 'Weekly workout plan'});
                await section.getByRole('button', {name: 'New plan', exact: true}).click();
                await page.getByRole('dialog', {name: 'New weekly plan'}).getByRole('button', {name: 'Start blank'}).click();
                await section.getByLabel('Start date', {exact: true}).fill('2026-09-14');
                await section.getByLabel('Review date', {exact: true}).fill('2026-10-26');
                await section.locator('.plan-day').first().getByRole('button', {name: 'Add session', exact: true}).click();
            } else await page.getByRole('button', {name: 'New', exact: true}).click();
            const editor = page.getByRole('dialog', {name: planning ? 'Planned workout' : 'Workout', exact: true});
            await expect(editor).toBeVisible();
            return editor;
        }

        async function expectActionContract(editor, screenshotSuffix = null) {
            const group = editor.locator('.workout-add-line-actions');
            const buttons = group.getByRole('button');
            const labels = ['Add warm-up', 'Add exercise', 'Add cardio', 'Add stretching', 'Add stretching set'];
            await expect(buttons).toHaveText(labels);
            for (const label of labels) await expect(group.getByRole('button', {name: label, exact: true}).locator('.pi-plus')).toBeVisible();
            const layout = await group.evaluate(element => {
                const buttons = [...element.querySelectorAll(':scope > button')];
                return {
                    parentWidth: element.parentElement.clientWidth,
                    groupWidth: element.clientWidth,
                    gap: parseFloat(getComputedStyle(element).gap),
                    buttons: buttons.map(button => ({left: button.offsetLeft, top: button.offsetTop, bottom: button.offsetTop + button.offsetHeight, width: button.offsetWidth, height: button.offsetHeight}))
                };
            });
            expect(Math.abs(layout.groupWidth - layout.parentWidth)).toBeLessThanOrEqual(1);
            for (const button of layout.buttons) {
                expect(Math.abs(button.width - layout.groupWidth)).toBeLessThanOrEqual(1);
                expect(Math.abs(button.left - layout.buttons[0].left)).toBeLessThanOrEqual(1);
                expect(Math.abs(button.height - layout.buttons[0].height)).toBeLessThanOrEqual(1);
            }
            for (let index = 1; index < layout.buttons.length; index++) {
                expect(layout.buttons[index].top).toBeGreaterThan(layout.buttons[index - 1].top);
                expect(Math.abs(layout.buttons[index].top - layout.buttons[index - 1].bottom - layout.gap)).toBeLessThanOrEqual(1);
            }
            expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
            await expect(editor.getByRole('button', {name: 'Save', exact: true})).toBeVisible();
            await expect(editor.getByRole('button', {name: 'Cancel', exact: true})).toBeVisible();
            if (screenshotSuffix) {
                await group.scrollIntoViewIfNeeded();
                await editor.screenshot({animations: 'disabled', path: testInfo.outputPath(screenshotSuffix)});
            }
        }

        let editor = await openEditor();
        const cards = editor.locator('.workout-line-card');
        const initialCards = await cards.count();
        await editor.getByRole('button', {name: 'Add warm-up', exact: true}).click();
        await expect(cards).toHaveCount(initialCards + 1);
        await editor.getByRole('button', {name: 'Add exercise', exact: true}).focus();
        await page.keyboard.press('Enter');
        await expect(cards).toHaveCount(initialCards + 2);
        await editor.getByRole('button', {name: 'Add stretching', exact: true}).click();
        await expect(cards).toHaveCount(initialCards + 3);
        await editor.getByRole('button', {name: 'Add stretching set', exact: true}).click();
        const stretchingPicker = page.getByRole('dialog', {name: 'Add stretching set', exact: true});
        await expect(stretchingPicker).toBeVisible();
        await stretchingPicker.getByRole('button', {name: 'Cancel', exact: true}).click();

        for (const width of [320, 376, 390, 1280]) {
            await page.setViewportSize({width, height: 1100});
            await expectActionContract(editor, `workout-add-actions-${planning ? 'plan' : 'recorded'}-${width}.png`);
        }

        await page.reload();
        editor = await openEditor();
        for (const width of [320, 376, 390, 1280]) {
            await page.setViewportSize({width, height: 1100});
            await expectActionContract(editor);
        }
    });
}

for (const planning of [false, true]) {
    test(`Add cardio selects a cardio-only exercise and scrolls to it in ${planning ? 'weekly plans' : 'recorded workouts'}`, async ({page}) => {
        await mockWeeklyPlans(page);
        await page.setViewportSize({width: 390, height: 700});
        await openSpaRoute(page, planning ? '/workouts?tab=plan' : '/workouts');
        const section = page.getByRole('region', {name: 'Weekly workout plan'});
        if (planning) {
            await section.getByRole('button', {name: 'New plan', exact: true}).click();
            await page.getByRole('dialog', {name: 'New weekly plan'}).getByRole('button', {name: 'Start blank', exact: true}).click();
            await section.getByLabel('Start date', {exact: true}).fill('2026-09-14');
            await section.getByLabel('Review date', {exact: true}).fill('2026-10-26');
            await section.locator('.plan-day').first().getByRole('button', {name: 'Add session', exact: true}).click();
        } else {
            await page.getByRole('button', {name: 'New', exact: true}).click();
        }
        const editor = page.getByRole('dialog', {name: planning ? 'Planned workout' : 'Workout', exact: true});
        await editor.getByRole('button', {name: 'Add cardio', exact: true}).click();
        const cardioGroup = editor.locator('#workout-exercise-group-TRAINING_CARDIO').locator('..');
        const cardioLine = cardioGroup.locator('.workout-line-card');
        const picker = cardioLine.getByLabel('Exercise', {exact: true});
        await expect(picker).toBeInViewport();
        await picker.click();
        await expect(page.getByRole('option', {name: 'Outdoor run', exact: true})).toBeVisible();
        await expect(page.getByRole('option', {name: 'Squat with a deliberately long descriptive exercise name', exact: true})).toHaveCount(0);
        await page.getByRole('option', {name: 'Outdoor run', exact: true}).click();
        await expect(cardioGroup.getByRole('button', {name: /^Collapse Cardio,/})).toBeVisible();
        await expect(cardioLine).toContainText('Outdoor run');
    });

    test(`workout editor type-group section headers share responsive behavior in ${planning ? 'weekly plans' : 'recorded workouts'}`, async ({page}, testInfo) => {
        const state = await mockWeeklyPlans(page);
        await openSpaRoute(page, planning ? '/workouts?tab=plan' : '/workouts');
        if (planning) {
            const section = page.getByRole('region', {name: 'Weekly workout plan'});
            await section.getByRole('button', {name: 'New plan', exact: true}).click();
            await page.getByRole('dialog', {name: 'New weekly plan'}).getByRole('button', {name: 'Start blank'}).click();
            await section.getByLabel('Start date', {exact: true}).fill('2026-09-14');
            await section.getByLabel('Review date', {exact: true}).fill('2026-10-26');
            await section.locator('.plan-day').first().getByRole('button', {name: 'Add session', exact: true}).click();
        } else await page.getByRole('button', {name: 'New', exact: true}).click();

        const editor = page.getByRole('dialog', {name: planning ? 'Planned workout' : 'Workout', exact: true});
        if (planning) await editor.getByRole('button', {name: 'Add exercise', exact: true}).click();
        const trainingParent = editor.locator('#workout-exercise-group-TRAINING_PARENT').locator('..');
        const trainingGroup = editor.locator('#workout-exercise-group-TRAINING_STRENGTH').locator('..');
        const parentToggle = trainingParent.locator('.workout-exercise-group-toggle');
        if (await parentToggle.getAttribute('aria-expanded') === 'false') await parentToggle.click();
        const strengthToggle = trainingGroup.locator('.workout-exercise-subgroup-toggle');
        if (await strengthToggle.getAttribute('aria-expanded') === 'false') await strengthToggle.click();
        await trainingGroup.getByLabel('Exercise', {exact: true}).click();
        await page.getByRole('option', {name: state.exercises[0].name, exact: true}).click();
        await editor.getByRole('button', {name: 'Add warm-up', exact: true}).click();
        await editor.getByRole('button', {name: 'Add stretching', exact: true}).click();

        for (const [label, type, icon, count] of [['Warm-up', 'WARM_UP', 'sun', '1'], ['Stretching', 'STRETCHING', 'arrows-v', '1']]) {
            const group = editor.locator(`#workout-exercise-group-${type}`).locator('..');
            await expect(group.locator('h3.workout-exercise-group-heading')).toHaveAttribute('aria-label', label);
            await expect(group.locator(`.workout-exercise-group-icon.pi-${icon}`)).toBeVisible();
            await expect(group.locator('.workout-exercise-group-count')).toHaveText(count);
            await expect(group.locator('.workout-exercise-group-toggle')).toHaveAttribute('aria-controls', `workout-exercise-group-${type}`);
            await expect(group.locator('.workout-exercise-group-toggle')).toHaveAccessibleName(`Collapse ${label}, ${count} ${label === 'Strength' || label === 'Cardio' ? 'exercise' : label === 'Warm-up' ? 'warm-up' : 'stretch'}`);
        }
        await expect(trainingParent.locator('.workout-exercise-group-count')).toHaveText('1');
        await expect(trainingParent.locator('.workout-exercise-group-toggle')).toHaveAccessibleName('Collapse Training, 1 exercise');
        await expect(trainingGroup.locator('h4.workout-exercise-subgroup-heading')).toHaveAttribute('aria-label', 'Strength');
        await expect(trainingGroup.locator('.workout-exercise-subgroup-icon.pi-bolt')).toBeVisible();
        await expect(trainingGroup.locator('.workout-exercise-subgroup-count')).toHaveText('1');
        await expect(trainingGroup.locator('.workout-exercise-subgroup-toggle')).toHaveAccessibleName('Collapse Strength, 1 exercise');
        const cardioGroup = editor.locator('#workout-exercise-group-TRAINING_CARDIO').locator('..');
        await expect(cardioGroup.locator('.workout-exercise-subgroup-count')).toHaveText('0');
        await expect(cardioGroup.locator('.workout-exercise-subgroup-toggle')).toHaveAccessibleName('Expand Cardio, 0 exercises');
        const trainingToggle = trainingParent.locator('.workout-exercise-group-toggle');
        await trainingToggle.focus();
        await page.keyboard.press('Enter');
        await expect(trainingParent.locator('.workout-exercise-group-lines')).toBeHidden();
        await expect(trainingToggle).toHaveAccessibleName('Expand Training, 1 exercise');
        await page.keyboard.press('Space');
        await expect(trainingParent.locator('.workout-exercise-group-lines')).toBeVisible();
        for (const width of [376, 390, 1280]) {
            await page.setViewportSize({width, height: 950});
            expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
            expect(await trainingParent.locator('.workout-exercise-group-heading').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
            expect(await trainingToggle.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
            await page.screenshot({path: testInfo.outputPath(`workout-type-groups-${width}.png`), animations: 'disabled'});
        }
    });
}

test('workout editor keeps footer labels readable on mobile and desktop', async ({page}, testInfo) => {
    await mockAuthenticatedWorkouts(page, [], [{id: 1, name: 'Bench press', description: 'Press with control.', trackingMode: 'REPS', exerciseType: 'TRAINING'}]);
    await page.setViewportSize({width: 376, height: 900});
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();

    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    const actions = editor.locator('.workout-editor-actions');
    const startGuided = actions.getByRole('button', {name: 'Start guided workout', exact: true});
    const save = actions.getByRole('button', {name: 'Save', exact: true});
    const cancel = actions.getByRole('button', {name: 'Cancel', exact: true});

    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        const layout = await actions.evaluate(element => {
            const bounds = button => {
                const {x, y, width, height} = button.getBoundingClientRect();
                return {x, y, width, height};
            };
            return {
                footer: element.getBoundingClientRect().toJSON(),
                start: bounds(element.querySelector('.workout-editor-start-guided')),
                save: bounds([...element.querySelectorAll('button')].find(button => button.textContent.trim() === 'Save')),
                cancel: bounds([...element.querySelectorAll('button')].find(button => button.textContent.trim() === 'Cancel')),
                startLabelHeight: element.querySelector('.workout-editor-start-guided .p-button-label').getBoundingClientRect().height,
                labelsFit: [...element.querySelectorAll('.p-button-label')].every(label => label.scrollHeight <= label.clientHeight + 1)
            };
        });
        expect(layout.labelsFit).toBe(true);
        expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        if (width === 376) {
            expect(layout.start.width).toBeGreaterThan(layout.footer.width * 0.9);
            expect(layout.start.y).toBeLessThan(layout.save.y);
            expect(Math.abs(layout.save.y - layout.cancel.y)).toBeLessThanOrEqual(1);
            expect(Math.abs(layout.save.width - layout.cancel.width)).toBeLessThanOrEqual(1);
            expect(layout.startLabelHeight).toBeLessThan(30);
        }
        await editor.screenshot({animations: 'disabled', path: testInfo.outputPath(`workout-modal-footer-${width}.png`)});
    }

    await expect(startGuided).toBeVisible();
    await expect(save).toBeVisible();
    await expect(cancel).toBeVisible();
});

for (const planning of [false, true]) {
    test(`exercise additions follow their type in ${planning ? 'weekly plans' : 'recorded workouts'}`, async ({page}, testInfo) => {
        const state = await mockWeeklyPlans(page);
        state.exercises.push(
            {id: 5, name: 'Shoulder circles', description: '', trackingMode: 'REPS', exerciseType: 'WARM_UP'},
            {id: 6, name: 'Hip circles', description: '', trackingMode: 'REPS', exerciseType: 'WARM_UP'},
            {id: 7, name: 'Hamstring stretch', description: '', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'},
            {id: 8, name: 'Shoulder stretch', description: '', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
        );
        await page.route('**/api/stretching-sets', route => route.fulfill({json: [{id: 1, name: 'Finishing stretches', entries: [{exerciseId: 3, durations: [30]}, {exerciseId: 8, durations: [20, 40]}]}]}));
        await openSpaRoute(page, planning ? '/workouts?tab=plan' : '/workouts');
        const section = page.getByRole('region', {name: 'Weekly workout plan'});
        if (planning) {
            await section.getByRole('button', {name: 'New plan', exact: true}).click();
            await page.getByRole('dialog', {name: 'New weekly plan'}).getByRole('button', {name: 'Start blank'}).click();
            await section.getByLabel('Start date', {exact: true}).fill('2026-09-14');
            await section.getByLabel('Review date', {exact: true}).fill('2026-10-26');
            await section.locator('.plan-day').first().getByRole('button', {name: 'Add session', exact: true}).click();
        } else {
            await page.getByRole('button', {name: 'New', exact: true}).click();
        }
        const editor = page.getByRole('dialog', {name: planning ? 'Planned workout' : 'Workout', exact: true});
        const cards = editor.locator('.workout-line-card');
        if (!planning) await editor.getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
        async function expectOrder(ids) {
            await expect(cards.locator('.workout-line-toggle strong')).toHaveText(ids.map((id, index) => {
                const exercise = state.exercises.find(item => item.id === id);
                const label = {WARM_UP: 'Warm-up', TRAINING: 'Exercise', STRETCHING: 'Stretching'}[exercise.exerciseType];
                return `${label} ${index + 1}: ${exercise.name}`;
            }));
        }
        async function add(id, index, label) {
            await editor.getByRole('button', {name: label, exact: true}).click();
            const card = cards.nth(index);
            const exercise = state.exercises.find(item => item.id === id);
            await card.getByLabel('Exercise', {exact: true}).click();
            await page.getByRole('option', {name: exercise.name, exact: true}).click();
            if (exercise.trackingMode === 'REPS') await card.locator('.segment-card input').first().fill('10');
            else {
                if (exercise.exerciseType === 'STRETCHING') {
                    await card.getByLabel('Mode', {exact: true}).click();
                    await page.getByRole('option', {name: 'Time', exact: true}).click();
                }
                await card.getByLabel('Minutes', {exact: true}).fill('1');
            }
            await card.getByRole('button', {name: /^Collapse /}).click();
        }
        await add(3, 0, 'Add stretching');
        await add(1, 0, 'Add exercise');
        await add(5, 0, 'Add warm-up');
        await add(4, 2, 'Add exercise');
        await add(6, 1, 'Add warm-up');
        await add(7, 5, 'Add stretching');
        await expectOrder([5, 6, 1, 4, 3, 7]);
        await cards.nth(0).getByRole('button', {name: 'Move exercise 1 down', exact: true}).click();
        await expectOrder([6, 5, 1, 4, 3, 7]);
        await editor.getByRole('button', {name: 'Add warm-up', exact: true}).click();
        await expect(cards.nth(2).getByRole('button', {name: 'Collapse Warm-up 3', exact: true})).toBeVisible();
        await cards.nth(2).getByRole('button', {name: 'Delete exercise 3', exact: true}).click();
        await expectOrder([6, 5, 1, 4, 3, 7]);
        await cards.nth(2).getByRole('button', {name: 'Move exercise 1 down', exact: true}).click();
        await cards.nth(4).getByRole('button', {name: 'Move exercise 1 down', exact: true}).click();
        await editor.getByRole('button', {name: 'Add stretching set', exact: true}).click();
        const picker = page.getByRole('dialog', {name: 'Add stretching set', exact: true});
        await picker.locator('.p-dropdown').click();
        await page.getByRole('option', {name: 'Finishing stretches', exact: true}).click();
        await picker.getByRole('button', {name: 'Add', exact: true}).click();
        const expectedIds = [6, 5, 4, 1, 7, 3, 8];
        await expectOrder(expectedIds);
        await expect(cards.getByRole('button', {name: /^Expand /})).toHaveCount(7);
        for (const width of [390, 1280]) {
            await page.setViewportSize({width, height: 1100});
            expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
            await page.screenshot({path: testInfo.outputPath(`exercise-order-${width}.png`), animations: 'disabled'});
        }
        const saved = planning ? null : page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(editor).toBeHidden();
        let lines;
        if (planning) {
            for (let index = 1; index < 7; index++) await section.locator('.plan-day').nth(index).getByRole('button', {name: 'Rest', exact: true}).click();
            await section.getByRole('button', {name: 'Save plan', exact: true}).click();
            await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toBeVisible();
            lines = state.current.days[0].sessions[0].lines;
        } else lines = (await saved).postDataJSON().lines;
        expect(lines.map(line => line.exerciseId)).toEqual(expectedIds);
        expect(lines.find(line => line.exerciseId === 3).segments[0].durationSeconds).toBe(60);
        expect(lines.find(line => line.exerciseId === 8).segments.map(segment => segment.durationSeconds)).toEqual([20, 40]);
        expect(lines.find(line => line.exerciseId === 1).segments[0].repetitions).toBe(10);
    });
}

test('weekly workout plan creates detailed days, copies, preserves failed drafts and archives commitments', async ({page}, testInfo) => {
    const state = await mockWeeklyPlans(page);
    await openSpaRoute(page, '/workouts?tab=plan');
    const section = page.getByRole('region', {name: 'Weekly workout plan'});
    await expect(section).toContainText('No weekly plan yet.');
    await section.getByRole('button', {name: 'New plan', exact: true}).click();
    await page.getByRole('dialog', {name: 'New weekly plan'}).getByRole('button', {name: 'Start blank'}).click();
    await section.getByRole('button', {name: 'Save plan'}).click();
    const validationError = section.locator('.plan-save-error');
    await expect(validationError).toContainText('Choose a workout or rest for all seven days.');
    await expect(validationError).toBeInViewport();
    await expect(page.locator('.p-toast-message-error').filter({hasText: 'Workout plan not saved'}).last()).toContainText('Choose a workout or rest for all seven days.');
    await section.getByLabel('Start date', {exact: true}).fill('2026-09-14');
    await section.getByLabel('Review date', {exact: true}).fill('2026-10-26');
    await section.getByLabel('Notes (optional)', {exact: true}).fill('A six-week commitment.');
    const monday = section.locator('.plan-day').nth(0);
    await monday.getByRole('button', {name: 'Add session', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Planned workout', exact: true});
    await expect(editor.getByRole('region', {name: 'Workout timing'})).toHaveCount(0);
    await editor.getByRole('button', {name: 'Add exercise', exact: true}).click();
    await editor.getByLabel('Exercise', {exact: true}).click();
    await page.getByRole('option', {name: state.exercises[0].name, exact: true}).click();
    await editor.locator('.segment-card input').nth(0).fill('8');
    await editor.locator('.segment-card input').nth(1).fill('20');
    await editor.getByRole('button', {name: 'Add set', exact: true}).click();
    await expect(editor.locator('.segment-card')).toHaveCount(2);
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 1100});
        expect(await editor.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-plan-editor-${width}.png`)});
    }
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    await expect(monday).toContainText('20 kg × 8 reps');
    const tuesday = section.locator('.plan-day').nth(1);
    await tuesday.getByRole('button', {name: 'Copy', exact: true}).click();
    const copyDialog = page.getByRole('dialog', {name: 'Copy a day'});
    await copyDialog.getByLabel('Copy from').click();
    await page.getByRole('option', {name: 'Monday', exact: true}).click();
    await copyDialog.getByRole('button', {name: 'Copy', exact: true}).click();
    for (let index = 2; index < 7; index++) await section.locator('.plan-day').nth(index).getByRole('button', {name: 'Rest', exact: true}).click();
    state.setFail(true);
    await section.getByRole('button', {name: 'Save plan'}).click();
    await expect(section).toContainText('The workout plan changed.');
    await expect(section.getByLabel('Notes (optional)', {exact: true})).toHaveValue('A six-week commitment.');
    state.setFail(false);
    await section.getByRole('button', {name: 'Save plan'}).click();
    await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toBeVisible();
    expect(state.current.days[0].sessions).toEqual(state.current.days[1].sessions);
    expect(state.current.days[0].sessions[0].lines[0].segments).toHaveLength(2);
    expect(state.current.days.slice(2).every(day => day.rest)).toBe(true);
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 1100});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`weekly-plan-overview-${width}.png`)});
    }
    await section.getByRole('button', {name: 'Edit plan', exact: true}).click();
    await section.getByLabel('Notes (optional)', {exact: true}).fill('Cancelled change');
    await section.getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(section).not.toContainText('Cancelled change');
    await section.getByRole('button', {name: 'Edit plan', exact: true}).click();
    await section.getByLabel('Notes (optional)', {exact: true}).fill('Edited commitment');
    await section.getByRole('button', {name: 'Save plan'}).click();
    await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toBeVisible();
    expect(state.archive).toHaveLength(0);
    await section.getByRole('button', {name: 'New plan', exact: true}).click();
    await page.getByRole('dialog', {name: 'New weekly plan'}).getByRole('button', {name: 'Copy current plan'}).click();
    await section.getByLabel('Notes (optional)', {exact: true}).fill('Next commitment');
    await section.getByRole('button', {name: 'Save plan'}).click();
    await expect(section.getByRole('button', {name: 'Previous plans', exact: true})).toBeVisible();
    expect(state.archive).toHaveLength(1);
    await section.getByRole('button', {name: 'Previous plans', exact: true}).click();
    await page.getByRole('dialog', {name: 'Previous plans'}).getByRole('button', {name: 'View', exact: true}).click();
    await expect(section).toContainText('Edited commitment');
    await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toHaveCount(0);
    await expect(section.getByRole('button', {name: 'Add session', exact: true})).toHaveCount(0);
    await section.getByRole('button', {name: 'Current plan', exact: true}).click();
    await expect(section).toContainText('Next commitment');
});

test('weekly plan copies sauna-only sessions and preserves stretching in a sauna session', async ({page}, testInfo) => {
    const state = await mockWeeklyPlans(page);
    await openSpaRoute(page, '/workouts?tab=plan');
    const plan = page.getByRole('region', {name: 'Weekly workout plan'});
    await plan.getByRole('button', {name: 'New plan', exact: true}).click();
    await page.getByRole('dialog', {name: 'New weekly plan'}).getByRole('button', {name: 'Start blank'}).click();
    await plan.getByLabel('Start date', {exact: true}).fill('2026-09-14');
    await plan.getByLabel('Review date', {exact: true}).fill('2026-10-26');
    await plan.locator('.plan-day').first().getByRole('button', {name: 'Add session'}).click();
    const editor = page.getByRole('dialog', {name: 'Planned workout'});
    await editor.locator('label[for="workout-sauna-session"]').click();
    await editor.locator('#workout-sauna-round-0').fill('12');
    await editor.locator('#workout-sauna-round-0').press('Tab');
    await editor.getByRole('button', {name: 'Add round'}).click();
    await editor.locator('#workout-sauna-round-1').fill('8');
    await editor.locator('#workout-sauna-round-1').press('Tab');
    await editor.getByRole('heading', {name: 'Sauna rounds'}).click();
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await page.mouse.move(0, 0);
        expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await editor.screenshot({path: testInfo.outputPath(`sauna-plan-editor-${width}.png`), animations: 'disabled'});
    }
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    const monday = plan.locator('.plan-day').first();
    await expect(monday.locator('.plan-day-summary')).toContainText('2 sauna rounds · 20 min sauna');
    await expect(monday).toContainText('2 sauna rounds · 20 min sauna');
    await expect(monday).toContainText('Round 1: 12 min · Round 2: 8 min');
    await plan.locator('.plan-day').nth(1).getByRole('button', {name: 'Copy'}).click();
    const copy = page.getByRole('dialog', {name: 'Copy a day'});
    await copy.getByLabel('Copy from').click();
    await page.getByRole('option', {name: 'Monday', exact: true}).click();
    await copy.getByRole('button', {name: 'Copy', exact: true}).click();
    for (let index = 2; index < 7; index++) await plan.locator('.plan-day').nth(index).getByRole('button', {name: 'Rest', exact: true}).click();
    await plan.getByRole('button', {name: 'Save plan'}).click();
    await expect(plan.getByRole('button', {name: 'Edit plan'})).toBeVisible();
    expect(state.current.days[0].sessions[0]).toMatchObject({saunaSession: true, saunaRoundsMinutes: [12, 8], lines: []});
    expect(state.current.days[1].sessions[0]).toMatchObject({saunaSession: true, saunaRoundsMinutes: [12, 8], lines: []});

    await plan.getByRole('button', {name: 'Edit plan', exact: true}).click();
    await plan.locator('.plan-day').nth(2).getByRole('button', {name: 'Add session', exact: true}).click();
    await editor.locator('label[for="workout-sauna-session"]').click();
    await expect(editor.locator('.workout-exercise-group-toggle')).toHaveCount(1);
    await expect(editor.getByRole('button', {name: /^(Collapse|Expand) Stretching,/})).toBeVisible();
    for (const action of ['Add warm-up', 'Add exercise', 'Add cardio']) {
        await expect(editor.getByRole('button', {name: action, exact: true})).toHaveCount(0);
    }
    await expect(editor.getByRole('button', {name: 'Add stretching', exact: true})).toBeVisible();
    await expect(editor.getByRole('button', {name: 'Add stretching set', exact: true})).toBeVisible();
    await editor.locator('#workout-sauna-round-0').fill('10');
    await editor.locator('#workout-sauna-round-0').press('Tab');
    await editor.getByRole('button', {name: 'Add stretching', exact: true}).click();
    await editor.locator('.workout-line-card').getByRole('combobox', {name: 'Exercise'}).click();
    await expect(page.getByRole('option', {name: state.exercises[2].name, exact: true})).toBeVisible();
    await expect(page.getByRole('option', {name: state.exercises[0].name, exact: true})).toHaveCount(0);
    await expect(page.getByRole('option')).toHaveCount(1);
    await page.getByRole('option', {name: state.exercises[2].name, exact: true}).click();
    await editor.getByLabel('Breaths', {exact: true}).fill('5');
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await editor.screenshot({path: testInfo.outputPath(`sauna-plan-with-stretch-${width}.png`), animations: 'disabled'});
        await editor.locator('.workout-add-line-actions').scrollIntoViewIfNeeded();
        await expect(editor.locator('.workout-add-line-actions')).toBeInViewport({ratio: 1});
        await editor.screenshot({path: testInfo.outputPath(`sauna-plan-with-stretch-actions-${width}.png`), animations: 'disabled'});
    }
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await plan.getByRole('button', {name: 'Save plan', exact: true}).click();
    expect(state.current.days[2].sessions[0]).toMatchObject({saunaSession: true, saunaRoundsMinutes: [10], lines: [{exerciseId: 3, stretchingUnit: 'BREATHS', segments: [{breaths: 5}]}]});

    await openSpaRoute(page, '/workouts?tab=plan');
    const wednesday = plan.locator('.plan-day').nth(2);
    await wednesday.locator('.plan-day-toggle').click();
    await expect(wednesday).toContainText('Wall calf stretch');
    await expect(wednesday).toContainText('5 breaths');
    await expect(wednesday).toContainText('Round 1: 10 min');
    await plan.getByRole('button', {name: 'Edit plan', exact: true}).click();
    await wednesday.getByRole('button', {name: 'Edit Wall calf stretch', exact: true}).click();
    await expect(editor.locator('#workout-sauna-session')).toBeChecked();
    await expect(editor.locator('#workout-sauna-round-0')).toHaveValue('10');
    await editor.getByRole('button', {name: /^Expand Stretching,/}).click();
    await editor.locator('.workout-line-card').getByRole('button', {name: /^Expand Stretching/}).click();
    await expect(editor.getByLabel('Breaths', {exact: true})).toHaveValue('5');
});

test('manual sauna session saves and edits round minutes without exercises', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Calf stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    await editor.locator('label[for="workout-sauna-session"]').click();
    await expect(editor.locator('.workout-line-card')).toHaveCount(0);
    for (const action of ['Add warm-up', 'Add exercise', 'Add cardio']) {
        await expect(editor.getByRole('button', {name: action, exact: true})).toHaveCount(0);
    }
    await editor.getByRole('button', {name: 'Add stretching', exact: true}).click();
    const stretch = editor.locator('.workout-line-card').first();
    await stretch.locator('.workout-exercise-picker').click();
    await expect(page.getByRole('option', {name: 'Calf stretch', exact: true})).toBeVisible();
    await expect(page.getByRole('option', {name: 'Squat', exact: true})).toHaveCount(0);
    await page.getByRole('option', {name: 'Calf stretch', exact: true}).click();
    await stretch.getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
    await expect(editor.locator('.workout-line-card')).toHaveCount(0);
    await editor.locator('#workout-sauna-round-0').fill('12');
    await editor.locator('#workout-sauna-round-0').press('Tab');
    await editor.getByRole('button', {name: 'Add round'}).click();
    await editor.locator('#workout-sauna-round-1').fill('8');
    await editor.locator('#workout-sauna-round-1').press('Tab');
    await editor.getByRole('heading', {name: 'Sauna rounds'}).click();
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await page.mouse.move(0, 0);
        expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await editor.screenshot({path: testInfo.outputPath(`sauna-manual-editor-${width}.png`), animations: 'disabled'});
    }
    let saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({saunaSession: true, saunaRoundsMinutes: [12, 8], durationMinutes: 20, lines: []});
    await expect(editor).toBeHidden();
    await page.locator('.diary-desktop').getByRole('button', {name: 'Edit workout', exact: true}).click();
    await editor.locator('#workout-sauna-round-0').fill('15');
    await editor.locator('#workout-sauna-round-0').press('Tab');
    saving = page.waitForRequest(request => /\/api\/workouts\/\d+$/.test(request.url()) && request.method() === 'PUT');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({saunaSession: true, saunaRoundsMinutes: [15, 8], durationMinutes: 23, lines: []});
    await expect(editor).toBeHidden();
});

test('manual sauna session retains a timed stretch and sauna round after reopening', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Calf stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await page.route('**/api/stretching-sets', route => route.fulfill({json: [{id: 1, name: 'Calf release', entries: [{exerciseId: 2, stretchingUnit: 'SECONDS', durations: [30]}]}]}));
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    await editor.locator('label[for="workout-sauna-session"]').click();
    await expect(editor.locator('.workout-exercise-group-toggle')).toHaveCount(1);
    await expect(editor.getByRole('button', {name: /^(Collapse|Expand) Stretching,/})).toBeVisible();
    await editor.locator('#workout-sauna-round-0').fill('12');
    await editor.locator('#workout-sauna-round-0').press('Tab');
    await editor.getByRole('button', {name: 'Add stretching', exact: true}).click();
    const stretch = editor.locator('.workout-line-card').first();
    await stretch.locator('.workout-exercise-picker').click();
    await page.getByRole('option', {name: 'Calf stretch', exact: true}).click();
    await stretch.getByLabel('Mode', {exact: true}).click();
    await page.getByRole('option', {name: 'Time', exact: true}).click();
    await stretch.locator('.segment-card .p-dropdown').click();
    await page.getByRole('option', {name: '30', exact: true}).click();
    await editor.getByRole('button', {name: 'Add stretching set', exact: true}).click();
    const stretchingPicker = page.getByRole('dialog', {name: 'Add stretching set', exact: true});
    await stretchingPicker.locator('#workout-stretching-set').click();
    await page.getByRole('option', {name: 'Calf release', exact: true}).click();
    await expect(stretchingPicker).toContainText('Calf stretch: 00:30');
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expect(stretchingPicker.locator('.p-dialog-content')).toBeInViewport({ratio: 1});
        await page.screenshot({path: testInfo.outputPath(`sauna-stretching-set-picker-${width}.png`), animations: 'disabled'});
    }
    await stretchingPicker.getByRole('button', {name: 'Cancel', exact: true}).click();
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await editor.screenshot({path: testInfo.outputPath(`sauna-manual-with-stretch-${width}.png`), animations: 'disabled'});
        await editor.locator('.workout-add-line-actions').scrollIntoViewIfNeeded();
        await expect(editor.locator('.workout-add-line-actions')).toBeInViewport({ratio: 1});
        await editor.screenshot({path: testInfo.outputPath(`sauna-manual-with-stretch-actions-${width}.png`), animations: 'disabled'});
    }
    const saving = page.waitForResponse(response => new URL(response.url()).pathname === '/api/workouts' && response.request().method() === 'POST');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    const response = await saving;
    expect(response.request().postDataJSON()).toMatchObject({saunaSession: true, saunaRoundsMinutes: [12], lines: [{exerciseId: 2, stretchingUnit: 'SECONDS', segments: [{durationSeconds: 30}]}]});
    expect((await response.json()).result).toMatchObject({saunaSession: true, saunaRoundsMinutes: [12], lines: [{exerciseId: 2, sets: [{durationSeconds: 30}]}]});
    await expect(editor).toBeHidden();

    await openSpaRoute(page, '/workouts');
    const [persisted] = await page.evaluate(async () => fetch('/api/workouts').then(result => result.json()));
    expect(persisted).toMatchObject({saunaSession: true, saunaRoundsMinutes: [12], lines: [{exerciseId: 2, sets: [{durationSeconds: 30}]}]});
    await page.locator('.diary-desktop').getByRole('button', {name: 'Edit workout', exact: true}).click();
    await expect(editor.locator('#workout-sauna-session')).toBeChecked();
    await expect(editor.locator('#workout-sauna-round-0')).toHaveValue('12');
    await editor.getByRole('button', {name: /^Expand Stretching,/}).click();
    await editor.locator('.workout-line-card').getByRole('button', {name: /^Expand Stretching/}).click();
    await expect(editor.locator('.workout-line-card .workout-exercise-picker')).toContainText('Calf stretch');
    await expect(editor.locator('.workout-line-card .segment-card .p-dropdown')).toContainText('30');
});

test('existing mixed sauna workout retains strength while sauna picker choices stay restricted', async ({page}) => {
    const exercises = [
        {id: 1, name: 'Squat', description: 'Lower-body squat.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Lunge', description: 'Single-leg strength.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 3, name: 'Calf stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'},
        {id: 4, name: 'Hamstring stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
    ];
    const existing = workoutResponse(7, {workoutDate: '2026-09-15', saunaSession: true, saunaRoundsMinutes: [12], durationMinutes: 12, lines: [
        {exerciseId: 1, segments: [{repetitions: 8, weight: 20}]},
        {exerciseId: 3, stretchingUnit: 'SECONDS', segments: [{durationSeconds: 30}]}
    ]}, exercises);
    await mockAuthenticatedWorkouts(page, [existing], exercises);
    await openSpaRoute(page, '/workouts');
    await page.locator('.diary-desktop').getByRole('button', {name: 'Edit workout', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    await expect(editor.locator('#workout-sauna-session')).toBeChecked();
    await expect(editor.locator('.workout-line-card')).toHaveCount(2);
    await editor.getByRole('button', {name: /^Expand Strength,/}).click();
    const strength = editor.locator('.workout-line-card').filter({hasText: 'Squat'});
    await strength.getByRole('button', {name: /^Expand Exercise 1:/}).click();
    await expect(strength.locator('.workout-exercise-picker')).toContainText('Squat');
    await strength.locator('.workout-exercise-picker').click();
    await expect(page.getByRole('option', {name: 'Squat', exact: true})).toBeVisible();
    await expect(page.getByRole('option', {name: 'Lunge', exact: true})).toHaveCount(0);
    await page.keyboard.press('Escape');
    await editor.getByRole('button', {name: 'Add stretching', exact: true}).click();
    const addedStretch = editor.locator('.workout-line-card').last();
    await addedStretch.locator('.workout-exercise-picker').click();
    await expect(page.getByRole('option', {name: 'Hamstring stretch', exact: true})).toBeVisible();
    await expect(page.getByRole('option', {name: 'Squat', exact: true})).toHaveCount(0);
    await expect(page.getByRole('option', {name: 'Lunge', exact: true})).toHaveCount(0);
    await page.getByRole('option', {name: 'Hamstring stretch', exact: true}).click();
    await addedStretch.getByLabel('Breaths', {exact: true}).fill('5');
    const saving = page.waitForRequest(request => /\/api\/workouts\/\d+$/.test(new URL(request.url()).pathname) && request.method() === 'PUT');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    const saved = (await saving).postDataJSON();
    expect(saved.lines.map(line => line.exerciseId)).toEqual([1, 3, 4]);
    expect(saved.lines[0].segments[0]).toMatchObject({repetitions: 8, weight: 20});
    await expect(editor).toBeHidden();

    await page.locator('.diary-desktop').getByRole('button', {name: 'Edit workout', exact: true}).click();
    await expect(editor.locator('#workout-sauna-session')).toBeChecked();
    await editor.getByRole('button', {name: /^Expand Strength,/}).click();
    await strength.getByRole('button', {name: /^Expand Exercise 1:/}).click();
    await expect(strength.locator('.workout-exercise-picker')).toContainText('Squat');
    await expect(strength.getByLabel('Repetitions', {exact: true})).toHaveValue('8');
    await expect(strength.getByLabel('Weight', {exact: true})).toHaveValue('20');
    await editor.locator('label[for="workout-sauna-session"]').click();
    for (const action of ['Add warm-up', 'Add exercise', 'Add cardio']) {
        await expect(editor.getByRole('button', {name: action, exact: true})).toBeVisible();
    }
    await strength.locator('.workout-exercise-picker').click();
    await expect(page.getByRole('option', {name: 'Squat', exact: true})).toBeVisible();
    await expect(page.getByRole('option', {name: 'Lunge', exact: true})).toBeVisible();
});

test('weekly workout plan edits timed, cardio and stretching targets without recording a workout', async ({page}) => {
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, lines: []}));
    const targets = [
        {exerciseId: 4, exerciseName: 'Plank', exerciseDescription: 'Hold steadily.', exerciseType: 'TRAINING', trackingMode: 'SECONDS', segments: [{durationSeconds: 65, weight: 0}]},
        {exerciseId: 2, exerciseName: 'Exercise bike', exerciseDescription: 'Steady pace.', exerciseType: 'WARM_UP', trackingMode: 'CARDIO', segments: [{durationSeconds: 600, speedKph: 10, distanceKm: 1, inclinePercent: 0, resistanceLevel: 2}]},
        {exerciseId: 3, exerciseName: 'Wall calf stretch', exerciseDescription: 'Hold each side.', exerciseType: 'STRETCHING', trackingMode: 'SECONDS', segments: [{durationSeconds: 35}]}
    ];
    days[0] = {...days[0], rest: false, lines: targets};
    const state = await mockWeeklyPlans(page, {id: 1, updateToken: 'first', startDate: '2026-08-01', reviewDate: '2026-08-30', days, notes: 'Past review'});
    let recorded = false;
    page.on('request', request => { if (new URL(request.url()).pathname === '/api/workouts' && request.method() === 'POST') recorded = true; });
    await openSpaRoute(page, '/workouts?tab=plan');
    const section = page.getByRole('region', {name: 'Weekly workout plan'});
    await expect(section).toContainText('Review due');
    await section.getByRole('button', {name: 'Edit plan', exact: true}).click();
    await section.locator('.plan-day').nth(0).locator('.plan-day-toggle').click();
    await section.locator('.plan-day').nth(0).getByRole('button', {name: /^Edit /}).click();
    const editor = page.getByRole('dialog', {name: 'Planned workout', exact: true});
    await expect(editor.getByRole('region', {name: 'Workout timing'})).toHaveCount(0);
    await expect(editor.getByText('Calories', {exact: true})).toHaveCount(0);
    await expect(editor.getByText('Average Heart Rate (bpm)', {exact: true})).toHaveCount(0);
    await expect(editor.getByText('Preload workout', {exact: true})).toHaveCount(0);
    await editor.getByRole('button', {name: /^Expand Strength,/}).click();
    await editor.getByRole('button', {name: 'Expand Exercise 1: Plank', exact: true}).click();
    await editor.locator('.workout-line-card').filter({hasText: 'Plank'}).getByLabel('Minutes', {exact: true}).fill('2');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    await section.getByRole('button', {name: 'Save plan'}).click();
    await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toBeVisible();
    expect(state.current.days[0].sessions[0].lines[0].segments[0].durationSeconds).toBe(125);
    expect(state.current.days[0].sessions[0].lines[1].segments[0]).toMatchObject({durationSeconds: 600, speedKph: 10, distanceKm: 1, inclinePercent: 0, resistanceLevel: 2});
    expect(state.current.days[0].sessions[0].lines[2].segments[0].durationSeconds).toBe(35);
    expect(recorded).toBe(false);
});

test('weekly plan moves a superset as a block and preserves planned order and targets after reopen', async ({page}) => {
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, lines: []}));
    days[0] = {day: 'MONDAY', rest: false, note: null, sessions: [{name: 'Superset session', note: '', lines: [
        {exerciseId: 1, exerciseName: 'Squat with a deliberately long descriptive exercise name', exerciseDescription: 'Keep the prescribed range of motion.', exerciseType: 'TRAINING', trackingMode: 'REPS', supersetGroupId: 'pair-a', segments: [{repetitions: 8, weight: 40}]},
        {exerciseId: 3, exerciseName: 'Wall calf stretch', exerciseDescription: 'Hold each side.', exerciseType: 'STRETCHING', trackingMode: 'SECONDS', stretchingUnit: 'SECONDS', supersetGroupId: 'pair-a', segments: [{durationSeconds: 30}]},
        {exerciseId: 4, exerciseName: 'Plank', exerciseDescription: 'Hold steadily.', exerciseType: 'TRAINING', trackingMode: 'SECONDS', supersetGroupId: 'pair-b', segments: [{durationSeconds: 45, weight: 0}]},
        {exerciseId: 2, exerciseName: 'Exercise bike', exerciseDescription: 'Steady pace.', exerciseType: 'WARM_UP', trackingMode: 'CARDIO', supersetGroupId: 'pair-b', segments: [{durationSeconds: 300, speedKph: 10, distanceKm: 1, inclinePercent: 0, resistanceLevel: 2}]},
        {exerciseId: 9, exerciseName: 'Outdoor run', exerciseDescription: 'Run outdoors.', exerciseType: 'TRAINING', trackingMode: 'CARDIO', cardioMetric: 'SPEED', segments: [{durationSeconds: 600, speedKph: 8, distanceKm: 1.3, inclinePercent: 0, resistanceLevel: null}]}
    ]}]};
    const state = await mockWeeklyPlans(page, {id: 1, updateToken: 'first', startDate: '2026-08-01', reviewDate: '2026-08-30', days, notes: ''});
    await openSpaRoute(page, '/workouts?tab=plan');
    const section = page.getByRole('region', {name: 'Weekly workout plan'});
    await expect(section).toContainText('Review due');

    async function openSessionEditor() {
        await section.getByRole('button', {name: 'Edit plan', exact: true}).click();
        const day = section.locator('.plan-day').first();
        if (await day.locator('.plan-day-toggle').getAttribute('aria-expanded') === 'false') await day.locator('.plan-day-toggle').click();
        await day.getByRole('button', {name: /^Edit /}).click();
        return page.getByRole('dialog', {name: 'Planned workout', exact: true});
    }

    async function expectSupersetOrder(editor, firstName, secondName) {
        const training = editor.locator('#workout-exercise-group-TRAINING_PARENT').locator('..');
        const headings = training.locator('.workout-exercise-subgroup-heading');
        expect(await headings.evaluateAll(elements => elements.map(element => element.getAttribute('aria-label')))).toEqual(['Superset 1', 'Superset 2', 'Cardio']);
        expect(await training.locator('.workout-exercise-subgroup-count').allTextContents()).toEqual(['2', '2', '1']);
        const groups = training.locator('.workout-exercise-subgroup--superset');
        await expect(groups).toHaveCount(2);
        await expect(groups.nth(0)).toContainText(firstName);
        await expect(groups.nth(1)).toContainText(secondName);
        return groups;
    }

    let editor = await openSessionEditor();
    let groups = await expectSupersetOrder(editor, 'Squat', 'Plank');
    await groups.nth(0).locator('.superset-label').getByRole('button', {name: 'Move superset down'}).first().click();
    ({groups} = await expectSupersetOrder(editor, 'Plank', 'Squat'));
    const updateOrder = page.waitForRequest(request => request.url().endsWith('/api/workout-plans/1') && request.method() === 'PUT');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    await section.getByRole('button', {name: 'Save plan', exact: true}).click();
    await updateOrder;
    const savedLines = state.current.days[0].sessions[0].lines;
    expect(savedLines.map(line => line.exerciseId)).toEqual([4, 2, 1, 3, 9]);
    expect(savedLines.map(line => line.supersetGroupId ?? null)).toEqual(['pair-b', 'pair-b', 'pair-a', 'pair-a', null]);
    expect(savedLines.map(line => line.segments[0])).toMatchObject([
        {durationSeconds: 45, weight: 0},
        {durationSeconds: 300, speedKph: 10, distanceKm: 1, inclinePercent: 0, resistanceLevel: 2},
        {repetitions: 8, weight: 40},
        {durationSeconds: 30},
        {durationSeconds: 600, speedKph: 8, distanceKm: 1.3, inclinePercent: 0, resistanceLevel: null}
    ]);

    editor = await openSessionEditor();
    groups = await expectSupersetOrder(editor, 'Plank', 'Squat');
    await expect(groups.nth(0)).toContainText('Exercise bike');
    await expect(groups.nth(1)).toContainText('Wall calf stretch');
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
});

test('weekly workout plan keeps rest, incomplete and no-training summaries', async ({page}) => {
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, lines: []}));
    days[0] = {...days[0], rest: false, lines: [
        {exerciseId: 2, exerciseName: 'Exercise bike', exerciseDescription: 'Steady pace.', exerciseType: 'WARM_UP', trackingMode: 'CARDIO', segments: [{durationSeconds: 600, speedKph: 10, distanceKm: 1, inclinePercent: 0, resistanceLevel: 2}]},
        {exerciseId: 3, exerciseName: 'Wall calf stretch', exerciseDescription: 'Hold each side.', exerciseType: 'STRETCHING', trackingMode: 'SECONDS', segments: [{durationSeconds: 35}]}
    ]};
    days[1] = {...days[1], rest: null};
    await mockWeeklyPlans(page, {id: 1, updateToken: 'first', startDate: '2026-08-01', reviewDate: '2026-08-30', days, notes: ''});
    await openSpaRoute(page, '/workouts?tab=plan');
    const planDays = page.getByRole('region', {name: 'Weekly workout plan'}).locator('.plan-day');
    await expect(planDays.nth(0).locator('.plan-day-summary')).toHaveText('Exercise bike');
    await expect(planDays.nth(1).locator('.plan-day-summary')).toHaveText('Choose workout or rest');
    await expect(planDays.nth(2).locator('.plan-day-summary')).toHaveText('Rest');
});

for (const width of [390, 1280]) {
    test(`saving feedback keeps weekly plan drafts safe at ${width}px`, async ({page}, testInfo) => {
        await mockWeeklyPlans(page);
        await page.setViewportSize({width, height: 900});
        await openSpaRoute(page, '/workouts?tab=plan');
        const section = page.getByRole('region', {name: 'Weekly workout plan'});
        await section.getByRole('button', {name: 'New plan', exact: true}).click();
        await page.getByRole('button', {name: 'Start blank', exact: true}).click();
        await section.getByLabel('Start date', {exact: true}).fill('2026-09-14');
        await section.getByLabel('Review date', {exact: true}).fill('2026-10-26');
        await section.getByLabel('Notes (optional)', {exact: true}).fill('Preserve this weekly plan');
        for (const day of await section.locator('.plan-day').all()) await day.getByRole('button', {name: 'Rest', exact: true}).click();
        let release;
        let attempts = 0;
        const pending = new Promise(resolve => { release = resolve; });
        await page.route('**/api/workout-plans', async route => {
            if (route.request().method() !== 'POST') return route.fallback();
            attempts++;
            if (attempts === 1) { await pending; return route.fulfill({status: 503, body: 'Please try again'}); }
            return route.fallback();
        });
        await section.getByRole('button', {name: 'Save plan', exact: true}).click();
        await expect(section.getByRole('button', {name: 'Saving…'})).toBeDisabled();
        await expect(section.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
        await expect(section.locator('.save-fields')).toHaveAttribute('inert', '');
        await section.getByRole('button', {name: 'Saving…'}).dispatchEvent('click');
        expect(attempts).toBe(1);
        await page.screenshot({path: testInfo.outputPath(`weekly-plan-saving-${width}.png`), animations: 'disabled'});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        release();
        const inlineError = section.locator('.plan-save-error');
        await expect(inlineError).toContainText('Please try again');
        await expect(inlineError).toBeInViewport();
        const growl = page.locator('.p-toast-message-error').filter({hasText: 'Workout plan not saved'}).last();
        await expect(growl).toContainText('Please try again');
        const growlBounds = await growl.boundingBox();
        expect(growlBounds.x).toBeGreaterThanOrEqual(0);
        expect(growlBounds.x + growlBounds.width).toBeLessThanOrEqual(width);
        await expect(section.getByLabel('Notes (optional)', {exact: true})).toHaveValue('Preserve this weekly plan');
        await page.screenshot({path: testInfo.outputPath(`weekly-plan-error-${width}.png`), animations: 'disabled'});
        await section.getByRole('button', {name: 'Save plan', exact: true}).click();
        await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toBeVisible();
        expect(attempts).toBe(2);
    });
}


test('workout timing records optional totals and breakdowns, preserves drafts and clears values', async ({page}, testInfo) => {
    const exercises = [{id: 1, name: 'Plank', description: 'Hold steady', trackingMode: 'SECONDS', exerciseType: 'TRAINING'}];
    const previous = workoutResponse(1, {workoutDate: '2026-08-20', startTime: '07:30', durationMinutes: 60, warmUpMinutes: 10, trainingMinutes: 45, stretchingMinutes: 5, lines: [{exerciseId: 1, segments: [{durationSeconds: 30}]}]}, exercises);
    await mockAuthenticatedWorkouts(page, [previous], exercises);
    await openSpaRoute(page, '/workouts');
    await expect(page.locator('.diary-desktop')).toContainText('Duration: 60 min');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    const time = dialog.getByLabel('Start time (optional)', {exact: true});
    const duration = dialog.locator('#workout-duration');
    await expect(time).toHaveValue('');
    await expect(duration).toHaveValue('');
    await dialog.locator('#preload-workout').click();
    await page.getByRole('option', {name: 'Thu, 20/08/2026 - Plank'}).click();
    await expect(time).toHaveValue('');
    await expect(duration).toHaveValue('');
    await time.fill('00:00'); await time.press('Tab');
    await duration.fill('50'); await duration.press('Tab');
    await dialog.locator('#preload-workout').click();
    await page.getByRole('option', {name: 'Thu, 20/08/2026 - Plank'}).click();
    await expect(time).toHaveValue('00:00');
    await expect(duration).toHaveValue('50');
    await dialog.getByText('Break down duration', {exact: true}).click();
    await expect(duration).toHaveAttribute('readonly');
    for (const name of ['Warm-up (min)', 'Training (min)', 'Cardio (min)', 'Stretching (min)']) {
        await expect(dialog.getByLabel(name, {exact: true})).toHaveValue('0');
    }
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog.getByRole('alert')).toContainText('Duration must be a positive whole number');
    await dialog.getByLabel('Warm-up (min)', {exact: true}).fill('');
    await dialog.getByLabel('Warm-up (min)', {exact: true}).press('Tab');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog.getByRole('alert')).toContainText('Enter all four duration values');
    for (const [name, value] of [['Warm-up (min)', '10'], ['Training (min)', '40'], ['Stretching (min)', '0']]) {
        await dialog.getByLabel(name, {exact: true}).fill(value);
        await dialog.getByLabel(name, {exact: true}).press('Tab');
    }
    await expect(duration).toHaveValue('50');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expect(dialog.getByLabel('Stretching (min)', {exact: true})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`workout-timing-form-${width}.png`), fullPage: true});
    }
    await page.route('**/api/workouts', route => route.fulfill({status: 500, body: 'Unable to save workout'}), {times: 1});
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page.getByText('Unable to save workout', {exact: true})).toBeVisible();
    await expect(time).toHaveValue('00:00');
    await expect(duration).toHaveValue('50');
    let saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({startTime: '00:00', durationMinutes: 50, warmUpMinutes: 10, trainingMinutes: 40, stretchingMinutes: 0});
    await expect(dialog).not.toBeVisible();
    await expect(page.locator('.diary-desktop')).toContainText('Duration: 50 min');
    await page.screenshot({path: testInfo.outputPath('workout-timing-diary-1280.png'), fullPage: true});
    await page.setViewportSize({width: 390, height: 900});
    await page.locator('.mobile-diary-summary').first().click();
    const details = page.locator('.mobile-diary-details');
    await expect(details).toContainText('Start: 00:00');
    await expect(details).toContainText('Warm-up: 10 min · Training: 40 min · Cardio: 0 min · Stretching: 0 min');
    await page.screenshot({path: testInfo.outputPath('workout-timing-diary-390.png'), fullPage: true});
    await details.getByRole('button', {name: 'Edit workout', exact: true}).click();
    await expect(dialog.getByLabel('Break down duration', {exact: true})).toBeChecked();
    await expect(duration).toHaveValue('50');
    await dialog.getByText('Break down duration', {exact: true}).click();
    await expect(duration).toHaveValue('50');
    await time.fill(''); await time.press('Tab');
    saving = page.waitForRequest(request => request.url().endsWith('/api/workouts/2') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({startTime: null, durationMinutes: 50, warmUpMinutes: null, trainingMinutes: null, stretchingMinutes: null});
    await expect(dialog).not.toBeVisible();
    await page.locator('.mobile-diary-summary').first().click();
    await page.getByRole('button', {name: 'Edit workout', exact: true}).click();
    await duration.fill(''); await duration.press('Tab');
    saving = page.waitForRequest(request => request.url().endsWith('/api/workouts/2') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({startTime: null, durationMinutes: null, warmUpMinutes: null, trainingMinutes: null, stretchingMinutes: null});
    await expect(dialog).not.toBeVisible();
});

test('workout end time follows duration by default and accepts only a long enough override', async ({page}, testInfo) => {
    const exercises = [{id: 1, name: 'Plank', description: 'Hold steady', trackingMode: 'SECONDS', exerciseType: 'TRAINING'}];
    const previous = workoutResponse(1, {workoutDate: '2026-08-20', lines: [{exerciseId: 1, segments: [{durationSeconds: 30}]}]}, exercises);
    await mockAuthenticatedWorkouts(page, [previous], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    await dialog.getByLabel('Exercise', {exact: true}).click();
    await page.getByRole('option', {name: 'Plank', exact: true}).click();
    await dialog.getByLabel('Minutes', {exact: true}).fill('1');
    await dialog.getByLabel('Start time (optional)', {exact: true}).fill('23:30');
    await dialog.getByLabel('Start time (optional)', {exact: true}).press('Tab');
    await dialog.locator('#workout-duration').fill('60');
    await dialog.locator('#workout-duration').press('Tab');
    const endTime = dialog.getByLabel('End time', {exact: true});
    await expect(endTime).toBeVisible();
    await expect(endTime).toHaveValue(/\d{2}\/\d{2}\/\d{4} 00:30/);
    await endTime.press('Escape');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await dialog.screenshot({path: testInfo.outputPath(`workout-end-time-${width}.png`), animations: 'disabled'});
    }
    await dialog.locator('#workout-duration').fill('75');
    await dialog.locator('#workout-duration').press('Tab');
    await expect(endTime).toHaveValue(/\d{2}\/\d{2}\/\d{4} 00:45/);

    const defaultDate = (await endTime.inputValue()).split(' ')[0];
    await endTime.fill(`${defaultDate} 00:44`);
    await endTime.press('Tab');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog.getByRole('alert')).toContainText('End time must be at least the full duration after the start');

    await endTime.fill(`${defaultDate} 01:00`);
    await endTime.press('Tab');
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    const payload = (await saving).postDataJSON();
    expect(payload).toMatchObject({startTime: '23:30', durationMinutes: 75});
    expect(new Intl.DateTimeFormat('en-GB', {timeZone: 'Europe/Madrid', hour: '2-digit', minute: '2-digit', hourCycle: 'h23'}).format(new Date(payload.endTime))).toBe('01:00');
    const madridDate = new Intl.DateTimeFormat('en-CA', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'});
    const nextWorkoutDate = new Date(`${payload.workoutDate}T12:00:00Z`);
    nextWorkoutDate.setUTCDate(nextWorkoutDate.getUTCDate() + 1);
    expect(madridDate.format(new Date(payload.endTime))).toBe(madridDate.format(nextWorkoutDate));
    await expect(dialog).not.toBeVisible();
    await expect(page.locator('.diary-desktop')).toContainText(/End: \d{2}\/\d{2}\/\d{4} 01:00/);
});

test('editing a completed workout preserves its original end timestamp', async ({page}) => {
    const exercises = [{id: 1, name: 'Plank', description: 'Hold steady', trackingMode: 'SECONDS', exerciseType: 'TRAINING'}];
    const endTime = '2026-08-20T06:59:59.123Z';
    const previous = workoutResponse(1, {workoutDate: '2026-08-20', startTime: '08:00', durationMinutes: 60, endTime,
        lines: [{exerciseId: 1, segments: [{durationSeconds: 30}]}]}, exercises);
    await mockAuthenticatedWorkouts(page, [previous], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'Edit workout', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts/1') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saving).postDataJSON().endTime).toBe(endTime);
});

test('workout end time includes phase breakdown and sauna duration', async ({page}) => {
    const exercises = [{id: 1, name: 'Plank', description: 'Hold steady', trackingMode: 'SECONDS', exerciseType: 'TRAINING'}];
    const previous = workoutResponse(1, {workoutDate: '2026-08-20', lines: [{exerciseId: 1, segments: [{durationSeconds: 30}]}]}, exercises);
    await mockAuthenticatedWorkouts(page, [previous], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    await dialog.getByLabel('Start time (optional)', {exact: true}).fill('10:00');
    await dialog.getByLabel('Start time (optional)', {exact: true}).press('Tab');
    await dialog.getByText('Sauna session', {exact: true}).click();
    await dialog.getByLabel('Round 1 (min)', {exact: true}).fill('10');
    await dialog.getByLabel('Round 1 (min)', {exact: true}).press('Tab');
    await dialog.getByText('Break down duration', {exact: true}).click();
    for (const [name, minutes] of [['Warm-up (min)', '10'], ['Training (min)', '20'], ['Cardio (min)', '5'], ['Stretching (min)', '5']]) {
        await dialog.getByLabel(name, {exact: true}).fill(minutes);
        await dialog.getByLabel(name, {exact: true}).press('Tab');
    }
    await expect(dialog.getByLabel('End time', {exact: true})).toHaveValue(/\d{2}\/\d{2}\/\d{4} 10:50/);
});


test('workout status panel identifies sauna-only sessions and their rounds', async ({page}, testInfo) => {
    const workout = workoutResponse(1, {workoutDate: '2026-08-12', plannedSessionName: 'Evening sauna', saunaSession: true,
        saunaRoundsMinutes: [12, 8], plannedSaunaRoundsMinutes: [10, 10], durationMinutes: 20, lines: []}, []);
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialWorkouts: [workout], workoutExercises: []});
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Workout', exact: true}).click();
    const group = page.getByRole('region', {name: 'Selected day workouts'});
    await expect(group).toContainText('Evening sauna');
    await expect(group).toContainText('Sauna');
    await expect(group.locator('.workout-session-summary')).toContainText('2 sauna rounds · 20 min sauna');
    await group.locator('.workout-session-details summary').click();
    await expect(group).toContainText('Round 1: 12 min · Round 2: 8 min');
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await group.screenshot({path: testInfo.outputPath(`sauna-status-${width}.png`), animations: 'disabled'});
    }
});

test('multiple workout sessions remain independent on the dashboard and same-day preloads', async ({page, context}, testInfo) => {
    const exercises = [{id: 1, name: 'Plank with controlled breathing and a comfortable range', description: 'Hold steady', trackingMode: 'SECONDS', exerciseType: 'TRAINING'}];
    const session = (id, time, duration, plannedSessionName = null) => workoutResponse(id, {workoutDate: '2026-08-12', startTime: time, durationMinutes: duration, plannedSessionName, lines: [{exerciseId: 1, segments: [{durationSeconds: 30}]}]}, exercises);
    const morning = session(1, '08:00', 45, 'McGill Big Three'), evening = session(2, '18:00', 30), untimed = session(3, null, null);
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route(coachOriginPattern, route => route.fulfill({body: '<title>Coach</title>'}));
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialWorkouts: [morning, evening, untimed], workoutExercises: exercises});
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Workout', exact: true}).click();
    const group = page.getByRole('region', {name: 'Selected day workouts'});
    await expect(group.locator('.workout-session')).toHaveCount(3);
    await expect(group.getByRole('button', {name: 'Rate day', exact: true})).toHaveCount(1);
    const morningOption = page.getByRole('option').filter({hasText: '08:00'});
    await expect(page.getByText('Training days:', {exact: true}).locator('..').locator('.p-col-7').first()).toContainText('1');
    await expect(group.locator('.session-day-summary')).toContainText('Logged duration: 75 min (incomplete)');
    const dayDetails = group.locator('.workout-day-details');
    await expect(dayDetails).not.toHaveAttribute('open', '');
    await dayDetails.locator('summary').click();
    await expect(dayDetails).toContainText('Timed training: 01:30');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await group.screenshot({path: testInfo.outputPath(`workout-sessions-${width}.png`)});
    }
    const popup = context.waitForEvent('page');
    await group.getByRole('button', {name: 'Rate day', exact: true}).click();
    const coach = await popup;
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('Assess all my workout sessions on 2026-08-12 together as one training day against my active coaching plan.');
    await coach.close();
    await page.getByRole('button', {name: 'Add session', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    await editor.locator('#preload-workout').click();
    await expect(morningOption).toContainText('McGill Big Three');
    await morningOption.click();
    await expect(editor.getByLabel('Start time (optional)', {exact: true})).toHaveValue('');
    await expect(editor.locator('#workout-duration')).toHaveValue('');
    await editor.locator('#workout-duration').fill('20'); await editor.locator('#workout-duration').press('Tab');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).not.toBeVisible();
    await expect(group.locator('.workout-session')).toHaveCount(4);
    await expect(group.locator('.session-day-summary')).toContainText('Logged duration: 95 min (incomplete)');
    await group.locator('.workout-session').first().getByRole('button', {name: 'Edit', exact: true}).click();
    await editor.locator('#workout-duration').fill('50'); await editor.locator('#workout-duration').press('Tab');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).not.toBeVisible();
    await expect(group.locator('.session-day-summary')).toContainText('Logged duration: 100 min (incomplete)');
    const eveningDetails = group.locator('.workout-session').nth(1).locator('.workout-session-details');
    await eveningDetails.locator('summary').click();
    await expect(eveningDetails).toContainText('Duration: 30 min');
    await group.locator('.workout-session').nth(2).getByRole('button', {name: 'Delete', exact: true}).click();
    await expect(group.locator('.workout-session')).toHaveCount(3);
    await expect(group.locator('.session-day-summary')).toContainText('Logged duration: 100 min');
    await expect(group.locator('.session-day-summary')).not.toContainText('incomplete');
    await expect(page.getByRole('button', {name: 'Add session', exact: true})).toBeVisible();
});

async function selectWorkoutTimerPhase(page, dialog, phase) {
    const expand = dialog.getByRole('button', {name: 'Expand workout timers', exact: true});
    if (await expand.isVisible()) await expand.click();
    await dialog.getByRole('combobox', {name: 'Phase', exact: true}).click();
    await page.getByRole('option', {name: phase, exact: true}).click();
}

async function startWorkoutTimerPhase(page, dialog, phase) {
    const running = await dialog.getByRole('button', {name: /^Stop (warm-up|training|cardio|stretching)$/}).isVisible();
    await selectWorkoutTimerPhase(page, dialog, phase);
    if (!running) await dialog.getByRole('button', {name: `Start ${phase.toLowerCase()}`, exact: true}).click();
}

test('workout single timer collapses, switches phases and remains accessible', async ({page}, testInfo) => {
    await mockAuthenticatedWorkouts(page, [], []);
    await page.clock.install({time: new Date('2026-09-12T10:00:00')});
    await page.clock.pauseAt(new Date('2026-09-12T10:00:01'));
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    await page.clock.runFor(300);
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    const expand = dialog.getByRole('button', {name: 'Expand workout timers', exact: true});
    await expect(expand).toHaveAttribute('aria-expanded', 'false');
    await expect(dialog.getByRole('combobox', {name: 'Phase', exact: true})).toBeHidden();
    for (const width of [390, 393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await page.clock.runFor(300);
        await page.screenshot({path: testInfo.outputPath(`timer-collapsed-${width}.png`), fullPage: false, animations: 'disabled'});
    }
    await expand.focus();
    await page.keyboard.press('Enter');
    await expect(dialog.getByRole('button', {name: 'Collapse workout timers', exact: true})).toBeFocused();
    await expect(dialog.getByRole('button', {name: 'Start warm-up', exact: true})).toBeVisible();
    await selectWorkoutTimerPhase(page, dialog, 'Cardio');
    await expect(dialog.getByRole('button', {name: 'Start cardio', exact: true})).toBeVisible();
    await expect(dialog.getByRole('button', {name: 'Stop cardio', exact: true})).toHaveCount(0);
    await dialog.getByRole('button', {name: 'Start cardio', exact: true}).click();
    await expect(dialog.getByRole('button', {name: 'Stop cardio', exact: true})).toBeVisible();
    await page.clock.fastForward(65000);
    await selectWorkoutTimerPhase(page, dialog, 'Training');
    await expect(dialog.getByRole('button', {name: 'Stop training', exact: true})).toBeVisible();
    await expect(dialog.getByLabel('Cardio (min)', {exact: true})).toHaveValue('2');
    await page.clock.fastForward(30000);
    await expect(dialog.getByLabel('Training elapsed time', {exact: true})).toHaveText('00:00:30');
    await expect(dialog.locator('.timer-clock')).toContainText('Total: 00:01:35');
    for (const width of [390, 393, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await page.clock.runFor(300);
        expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`timer-expanded-${width}.png`), fullPage: false, animations: 'disabled'});
        await dialog.getByRole('button', {name: 'Collapse workout timers', exact: true}).click();
        await expect(dialog.locator('.timer-summary')).toContainText('Training · Running');
        await expect(dialog.getByRole('button', {name: 'Stop training', exact: true})).toBeVisible();
        await page.screenshot({path: testInfo.outputPath(`timer-running-collapsed-${width}.png`), fullPage: false, animations: 'disabled'});
        await expand.click();
    }
    await dialog.getByRole('button', {name: 'Collapse workout timers', exact: true}).click();
    await dialog.getByRole('button', {name: 'Stop training', exact: true}).click();
    await expect(dialog.locator('.timer-summary')).toContainText('Stopped');
    const stoppedSummary = await dialog.locator('.timer-summary').textContent();
    await page.clock.fastForward(60000);
    await expect(dialog.locator('.timer-summary')).toHaveText(stoppedSummary);
    await dialog.getByRole('button', {name: 'Close', exact: true}).click();
    await page.clock.runFor(300);
    await page.getByRole('button', {name: 'Resume workout', exact: true}).click();
    await expect(dialog.getByRole('button', {name: 'Collapse workout timers', exact: true})).toHaveAttribute('aria-expanded', 'true');
    await expect(dialog.getByRole('button', {name: 'Start warm-up', exact: true})).toBeVisible();
    await expect(dialog.getByText('Stored in this browser only.', {exact: false})).toBeHidden();
    await dialog.getByText('Timer details', {exact: true}).click();
    await expect(dialog.getByText('Stored in this browser only.', {exact: false})).toBeVisible();
});

test('workout phase timers recover the complete draft and exclude stopped gaps', async ({page}, testInfo) => {
    const exercises = [{id: 1, name: 'Plank', description: 'Hold steady', trackingMode: 'SECONDS', exerciseType: 'TRAINING'}];
    const previous = workoutResponse(1, {workoutDate: '2026-08-20', lines: [{exerciseId: 1, segments: [{durationSeconds: 30}]}]}, exercises);
    await mockAuthenticatedWorkouts(page, [previous], exercises);
    await page.clock.install({time: new Date('2026-09-11T23:59:30')});
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    await dialog.locator('#preload-workout').click();
    await page.getByRole('option', {name: 'Thu, 20/08/2026 - Plank'}).click();
    await dialog.getByLabel('Note', {exact: true}).fill('Morning exercises and later cardio');
    await startWorkoutTimerPhase(page, dialog, 'Warm-up');
    await expect(dialog.getByRole('button', {name: 'Save', exact: true})).toBeDisabled();
    await page.clock.fastForward(65000);
    await startWorkoutTimerPhase(page, dialog, 'Training');
    await page.clock.fastForward(65000);
    await dialog.getByRole('button', {name: 'Stop training', exact: true}).click();
    await expect(dialog.locator('#workout-duration')).toHaveValue('4');
    await dialog.getByRole('button', {name: 'Close', exact: true}).click();
    await page.clock.fastForward(3600000);
    await page.goto('/');
    await page.getByRole('button', {name: 'Resume workout', exact: true}).click();
    await expect(dialog.getByLabel('Note', {exact: true})).toHaveValue('Morning exercises and later cardio');
    await expect(dialog.locator('#workout-duration')).toHaveValue('4');
    await startWorkoutTimerPhase(page, dialog, 'Cardio');
    await expect(dialog.getByRole('button', {name: 'Stop cardio', exact: true})).toBeVisible();
    await page.clock.fastForward(65000);
    await dialog.getByRole('button', {name: 'Close', exact: true}).click();
    await page.goto('/');
    await page.getByRole('button', {name: 'Resume workout', exact: true}).click();
    await expect(dialog.getByRole('button', {name: 'Stop cardio', exact: true})).toBeVisible();
    await dialog.getByRole('button', {name: 'Stop cardio', exact: true}).click();
    await startWorkoutTimerPhase(page, dialog, 'Warm-up');
    await page.clock.fastForward(10000);
    await dialog.getByRole('button', {name: 'Stop warm-up', exact: true}).click();
    // Repeated intervals round only after accumulation: 65s + 10s remains two minutes.
    await expect(dialog.getByLabel('Warm-up (min)', {exact: true})).toHaveValue('2');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await expect(dialog.getByRole('button', {name: 'Start warm-up', exact: true})).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`workout-timers-${width}.png`), fullPage: true});
    }
    await page.route('**/api/workouts', route => route.fulfill({status: 500, body: 'Save failed'}), {times: 1});
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(dialog.getByRole('button', {name: 'Save', exact: true})).toBeEnabled();
    await expect(dialog.getByLabel('Note', {exact: true})).toHaveValue('Morning exercises and later cardio');
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({workoutDate: '2026-09-11', startTime: '23:59', warmUpMinutes: 2, trainingMinutes: 2, cardioMinutes: 2, stretchingMinutes: 0, durationMinutes: 6});
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', {name: 'Resume workout', exact: true})).toHaveCount(0);
    await expect(page.locator('.diary-desktop')).toContainText('Cardio: 2 min');
});

test('workout phase timers add to saved sessions and discard preserves the original', async ({page}) => {
    const exercises = [{id: 1, name: 'Plank', description: 'Hold steady', trackingMode: 'SECONDS', exerciseType: 'TRAINING'}];
    const previous = workoutResponse(1, {workoutDate: '2026-08-20', startTime: '07:30', durationMinutes: 10, lines: [{exerciseId: 1, segments: [{durationSeconds: 30}]}]}, exercises);
    await mockAuthenticatedWorkouts(page, [previous], exercises);
    await page.clock.install();
    await openSpaRoute(page, '/workouts');
    await page.locator('.diary-desktop').getByRole('button', {name: 'Edit workout', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    await startWorkoutTimerPhase(page, dialog, 'Cardio');
    await expect(dialog.getByRole('alert')).toContainText('Split the existing total');
    await dialog.getByText('Break down duration', {exact: true}).click();
    for (const [name, value] of [['Warm-up (min)', '0'], ['Training (min)', '10'], ['Stretching (min)', '0']]) {
        await dialog.getByLabel(name, {exact: true}).fill(value);
        await dialog.getByLabel(name, {exact: true}).press('Tab');
    }
    await startWorkoutTimerPhase(page, dialog, 'Cardio');
    await expect(dialog.getByRole('button', {name: 'Stop cardio', exact: true})).toBeVisible();
    await page.clock.fastForward(65000);
    await dialog.getByRole('button', {name: 'Stop cardio', exact: true}).click();
    await dialog.getByRole('button', {name: 'Close', exact: true}).click();
    await page.getByRole('button', {name: 'Resume workout', exact: true}).click();
    await dialog.getByLabel('Training (min)', {exact: true}).fill('');
    await dialog.getByLabel('Training (min)', {exact: true}).press('Tab');
    await dialog.getByRole('button', {name: 'Close', exact: true}).click();
    await page.getByRole('button', {name: 'Resume workout', exact: true}).click();
    await expect(dialog.getByLabel('Training (min)', {exact: true})).toHaveValue('');
    await startWorkoutTimerPhase(page, dialog, 'Training');
    await expect(dialog.getByRole('alert')).toContainText('Enter all four phase durations');
    await dialog.getByLabel('Training (min)', {exact: true}).fill('10');
    await dialog.getByLabel('Training (min)', {exact: true}).press('Tab');
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts/1') && request.method() === 'PUT');
    await dialog.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({workoutDate: '2026-08-20', startTime: '07:30', durationMinutes: 12, trainingMinutes: 10, cardioMinutes: 2});
    await expect(dialog).not.toBeVisible();
    await page.locator('.diary-desktop').getByRole('button', {name: 'Edit workout', exact: true}).click();
    await startWorkoutTimerPhase(page, dialog, 'Stretching');
    await page.clock.fastForward(65000);
    await expect(dialog.getByRole('button', {name: 'Discard', exact: true})).toHaveCount(0);
    await dialog.getByRole('button', {name: 'Close', exact: true}).click();
    await page.getByRole('button', {name: 'Discard workout', exact: true}).click();
    await page.getByRole('dialog', {name: 'Discard timed workout?'}).getByRole('button', {name: 'Discard', exact: true}).click();
    await expect(dialog).not.toBeVisible();
    await expect(page.getByRole('button', {name: 'Resume workout', exact: true})).toHaveCount(0);
    await expect(page.locator('.diary-desktop')).toContainText('Duration: 12 min');
});

test('workout timed drafts are isolated by account and serialize competing tabs', async ({page, context}) => {
    const exercises = [{id: 1, name: 'Plank', description: 'Hold steady', trackingMode: 'SECONDS', exerciseType: 'TRAINING'}];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    await startWorkoutTimerPhase(page, dialog, 'Training');
    await expect(dialog.getByRole('button', {name: 'Stop training', exact: true})).toBeVisible();
    const other = await context.newPage();
    await mockAuthenticatedWorkouts(other, [], exercises);
    await openSpaRoute(other, '/workouts');
    await other.getByRole('button', {name: 'Resume workout', exact: true}).click();
    await expect(other.getByRole('alert')).toContainText('open in another tab');
    await other.getByRole('dialog', {name: 'Workout', exact: true}).getByRole('button', {name: 'Cancel', exact: true}).click();
    await dialog.getByRole('button', {name: 'Close', exact: true}).click();
    await other.getByRole('button', {name: 'Resume workout', exact: true}).click();
    await expect(other.getByRole('button', {name: 'Stop training', exact: true})).toBeVisible();
    await other.getByRole('button', {name: 'Stop training', exact: true}).click();
    await expect(page.locator('.workout-resume')).toContainText('Stopped');
    await other.close();
    await page.route('**/api/auth/me', route => route.fulfill({json: {email: 'other@example.com', displayName: 'Other', authenticated: true}}));
    await page.goto('/');
    await expect(page.locator('.diary-desktop')).toBeVisible();
    await expect(page.getByRole('button', {name: 'Resume workout', exact: true})).toHaveCount(0);
    await page.unroute('**/api/auth/me');
    await page.goto('/');
    await expect(page.getByRole('button', {name: 'Resume workout', exact: true})).toBeVisible();
});


test('stretching breaths survive saved sets, unit changes, timer recovery and preloading', async ({page}, testInfo) => {
    const exercises = [{id: 1, name: 'Wall calf stretch with a deliberately long descriptive name', description: 'Hold each side.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}];
    await mockAuthenticatedWorkouts(page, [], exercises);
    let sets = [];
    await page.route('**/api/stretching-sets**', route => {
        if (route.request().method() === 'GET') return route.fulfill({json: sets});
        sets = [{...route.request().postDataJSON(), id: 1}];
        return route.fulfill({json: sets[0]});
    });
    await openSpaRoute(page, '/workouts');
    await page.getByRole('tab', {name: 'Stretching', exact: true}).click();
    const section = page.getByRole('region', {name: 'Saved stretching sets'});
    await section.getByRole('button', {name: 'New set', exact: true}).click();
    const setEditor = page.getByRole('dialog', {name: 'Stretching set', exact: true});
    await setEditor.getByLabel('Name', {exact: true}).fill('Breathing stretches');
    await setEditor.locator('.p-multiselect').click();
    await page.getByRole('option', {name: exercises[0].name, exact: true}).click();
    await page.keyboard.press('Escape');
    await setEditor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(setEditor).toContainText('Enter a positive breath count');
    await setEditor.getByLabel('Breaths', {exact: true}).fill('5');
    await setEditor.getByRole('button', {name: 'Add hold', exact: true}).click();
    await setEditor.getByLabel('Breaths', {exact: true}).nth(1).fill('8');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 1000});
        expect(await setEditor.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`breath-set-${width}.png`)});
    }
    await setEditor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(setEditor).toBeHidden();
    expect(sets[0].entries[0]).toEqual({exerciseId: 1, stretchingUnit: 'BREATHS', durations: [], breaths: [5, 8]});
    await expect(section).toContainText('5 breaths + 8 breaths');
    await section.getByRole('button', {name: 'Edit stretching set Breathing stretches', exact: true}).click();
    await expect(setEditor.getByLabel('Breaths', {exact: true}).first()).toHaveValue('5');
    await setEditor.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('tab', {name: 'Diary', exact: true}).click();
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    await editor.getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
    async function applySet() {
        await editor.getByRole('button', {name: 'Add stretching set', exact: true}).click();
        const picker = page.getByRole('dialog', {name: 'Add stretching set', exact: true});
        await picker.locator('.p-dropdown').click();
        await page.getByRole('option', {name: 'Breathing stretches', exact: true}).click();
        await expect(picker).toContainText('5 breaths + 8 breaths');
        await picker.getByRole('button', {name: 'Add', exact: true}).click();
        await expect(picker).toBeHidden();
    }
    await applySet();
    await editor.getByRole('button', {name: /^Expand Stretching/}).click();
    await expect(editor.getByLabel('Breaths', {exact: true}).first()).toHaveValue('5');
    await editor.getByLabel('Mode', {exact: true}).click();
    await page.getByRole('option', {name: 'Time', exact: true}).click();
    await expect(editor.getByLabel('Minutes', {exact: true}).first()).toHaveValue('0');
    await editor.getByLabel('Mode', {exact: true}).click();
    await page.getByRole('option', {name: 'Breaths', exact: true}).click();
    await expect(editor.getByLabel('Breaths', {exact: true}).first()).toHaveValue('');
    await editor.getByLabel('Breaths', {exact: true}).first().fill('6');
    await editor.getByLabel('Breaths', {exact: true}).nth(1).fill('9');
    await applySet();
    await expect(page.locator('.p-toast-message-info').filter({hasText: 'No exercises added'}).last()).toBeVisible();
    await expect(editor.getByLabel('Breaths', {exact: true}).first()).toHaveValue('6');
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 1000});
        expect(await editor.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`breath-workout-${width}.png`)});
    }
    await startWorkoutTimerPhase(page, editor, 'Stretching');
    await editor.getByRole('button', {name: 'Stop stretching', exact: true}).click();
    await editor.getByRole('button', {name: 'Close', exact: true}).click();
    await page.goto('/');
    await page.getByRole('button', {name: 'Resume workout', exact: true}).click();
    await expect(editor.getByLabel('Breaths', {exact: true}).first()).toHaveValue('6');
    await editor.getByLabel('Stretching (min)', {exact: true}).fill('2');
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    const payload = (await saving).postDataJSON();
    expect(payload.lines[0]).toMatchObject({stretchingUnit: 'BREATHS', segments: [{breaths: 6, durationSeconds: null}, {breaths: 9, durationSeconds: null}]});
    expect(payload.durationMinutes).toBe(2);
    await expect(editor).toBeHidden();
    await expect(page.locator('.diary-desktop')).toContainText('6 breaths');
    await page.getByRole('button', {name: 'Edit workout', exact: true}).click();
    await editor.getByRole('button', {name: /^Expand Stretching/}).click();
    await expect(editor.getByLabel('Breaths', {exact: true}).nth(1)).toHaveValue('9');
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('button', {name: 'New', exact: true}).click();
    await editor.locator('#preload-workout').click();
    await page.getByRole('option').filter({hasText: exercises[0].name}).click();
    await editor.getByRole('button', {name: /^Expand Stretching/}).click();
    await expect(editor.getByLabel('Breaths', {exact: true}).first()).toHaveValue('6');
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
    await mockAuthenticatedDashboard(page, payload.workoutDate, {initialWorkouts: [workoutResponse(1, payload, exercises)], workoutExercises: exercises});
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Workout', exact: true}).click();
    const sessions = page.getByRole('region', {name: 'Selected day workouts'});
    await sessions.locator('.workout-session-details summary').first().click();
    await expect(sessions).toContainText('6 breaths');
    await expect(sessions).toContainText('9 breaths');
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 1000});
        await sessions.screenshot({animations: 'disabled', path: testInfo.outputPath(`breath-dashboard-${width}.png`)});
    }
});

test('workout dashboard keeps compact titles and exercise names visible across weeks', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Squat with a deliberately long descriptive exercise name', description: 'Controlled movement', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Wall calf stretch', description: 'Hold each side', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'},
        {id: 3, name: 'Shoulder circles', description: 'Warm up slowly', trackingMode: 'REPS', exerciseType: 'WARM_UP'}
    ];
    const selected = workoutResponse(1, {workoutDate: '2026-08-12', durationMinutes: 36, note: 'Keep the final set controlled.', lines: [
        {exerciseId: 1, supersetGroupId: 'pair-a', segments: [{repetitions: 8, weight: 40}]},
        {exerciseId: 2, supersetGroupId: 'pair-a', segments: [{durationSeconds: 30}]}
    ]}, exercises);
    const previousTraining = workoutResponse(2, {workoutDate: '2026-08-05', lines: [
        {exerciseId: 3, segments: [{repetitions: 10}]},
        {exerciseId: 1, segments: [{repetitions: 8, weight: 35}]}
    ]}, exercises);
    const previousWarmup = workoutResponse(3, {workoutDate: '2026-08-05', lines: [{exerciseId: 3, segments: [{repetitions: 10}]}]}, exercises);
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialWorkouts: [selected, previousTraining, previousWarmup], workoutExercises: exercises});
    await openSpaRoute(page, '/');
    await page.setViewportSize({width: 390, height: 844});
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Workout', exact: true}).click();

    const selectedGroup = page.getByRole('region', {name: 'Selected day workouts'});
    const selectedSession = selectedGroup.locator('.workout-session').first();
    await expect(selectedSession.getByRole('heading', {name: 'Session 1 · Squat with a deliberately long descriptive exercise name'})).toBeVisible();
    await expect(selectedSession.locator('.workout-session-summary')).toContainText('36 min');
    await expect(selectedSession.locator('.workout-session-summary')).toContainText('2 exercises');
    await expect(selectedSession.locator('.workout-exercise-names li')).toHaveCount(2);
    await expect(selectedSession.locator('.workout-exercise-names li').nth(0)).toContainText('Squat with a deliberately long descriptive exercise name');
    await expect(selectedSession.locator('.workout-exercise-names li').nth(1)).toContainText('Wall calf stretch');
    await expect(selectedSession.locator('.workout-exercise-names')).toContainText('Superset');
    const details = selectedSession.locator('.workout-session-details');
    await expect(details).not.toHaveAttribute('open', '');
    await expect(details.locator('.workout-line-detail').first()).toBeHidden();
    await selectedGroup.screenshot({path: testInfo.outputPath('compact-workout-selected-mobile.png')});

    const previousGroup = page.getByRole('region', {name: 'Previous week workouts'});
    await expect(previousGroup.locator('.workout-session').nth(0).getByRole('heading', {name: 'Session 1 · Squat with a deliberately long descriptive exercise name'})).toBeVisible();
    await expect(previousGroup.locator('.workout-session').nth(1).getByRole('heading', {name: 'Session 2 · Shoulder circles'})).toBeVisible();
    await expect(previousGroup.locator('.workout-session').nth(1).locator('.workout-exercise-names li').first()).toContainText('Shoulder circles');

    await details.locator('summary').focus();
    await page.keyboard.press('Enter');
    await expect(details).toHaveAttribute('open', '');
    await expect(details).toContainText('Keep the final set controlled.');
    await expect(details.locator('.workout-line-detail')).toHaveCount(2);
    await expect(details).toContainText('40 kg x 8 reps');
    await expect(details).toContainText('00:30');
    await page.setViewportSize({width: 1280, height: 900});
    await selectedGroup.screenshot({path: testInfo.outputPath('compact-workout-selected-desktop.png')});
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('weekly workout plan preserves breath targets through editing and archives', async ({page}, testInfo) => {
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, lines: []}));
    days[0] = {...days[0], rest: false, lines: [{exerciseId: 3, exerciseName: 'Wall calf stretch', exerciseDescription: 'Hold each side.', exerciseType: 'STRETCHING', trackingMode: 'SECONDS', stretchingUnit: 'BREATHS', segments: [{breaths: 5}]}]};
    const state = await mockWeeklyPlans(page, {id: 1, updateToken: 'first', startDate: '2026-08-01', reviewDate: '2026-08-30', days, notes: ''});
    await openSpaRoute(page, '/workouts?tab=plan');
    const section = page.getByRole('region', {name: 'Weekly workout plan'});
    await expect(section).toContainText('5 breaths');
    await section.getByRole('button', {name: 'Edit plan', exact: true}).click();
    await section.locator('.plan-day').first().locator('.plan-day-toggle').click();
    await section.locator('.plan-day').first().getByRole('button', {name: /^Edit /}).click();
    const editor = page.getByRole('dialog', {name: 'Planned workout', exact: true});
    await editor.getByRole('button', {name: /^Expand Stretching,/}).click();
    await editor.locator('.workout-line-card').getByRole('button', {name: /^Expand Stretching/}).click();
    await editor.getByLabel('Breaths', {exact: true}).fill('7');
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 1000});
        expect(await editor.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`breath-plan-${width}.png`)});
    }
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await section.getByRole('button', {name: 'Save plan', exact: true}).click();
    await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toBeVisible();
    expect(state.current.days[0].sessions[0].lines[0]).toMatchObject({stretchingUnit: 'BREATHS', segments: [{breaths: 7, durationSeconds: null}]});
    await page.goto('/');
    await expect(section).toContainText('7 breaths');
    await section.getByRole('button', {name: 'New plan', exact: true}).click();
    await page.getByRole('button', {name: 'Copy current plan', exact: true}).click();
    await section.getByRole('button', {name: 'Save plan', exact: true}).click();
    await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toBeVisible();
    expect(state.archive[0].days[0].sessions[0].lines[0]).toMatchObject({stretchingUnit: 'BREATHS', segments: [{breaths: 7}]});
});

test('weekly workout plan stores multiple named sessions and recording snapshots the selected targets', async ({page}, testInfo) => {
    await page.clock.install({time: new Date('2026-09-27T12:00:00')});
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, sessions: []}));
    const state = await mockWeeklyPlans(page, {id: 1, startDate: '2026-09-27', reviewDate: '2026-10-26', updateToken: 'snapshot-token', days, notes: ''});
    const sessions = [
        {name: 'Morning strength', note: 'Heavy day', lines: [{exerciseId: 1, exerciseName: state.exercises[0].name, exerciseDescription: state.exercises[0].description, trackingMode: 'REPS', exerciseType: 'TRAINING', stretchingUnit: 'SECONDS', segments: [{repetitions: 8, weight: 40}]}]},
        {name: 'Evening strength', note: 'Technique', lines: [{exerciseId: 1, exerciseName: state.exercises[0].name, exerciseDescription: state.exercises[0].description, trackingMode: 'REPS', exerciseType: 'TRAINING', stretchingUnit: 'SECONDS', segments: [{repetitions: 12, weight: 25}]}]}
    ];
    state.setCurrent({...state.current, days: state.current.days.map(day => day.day === 'SUNDAY' ? {day: day.day, rest: false, note: null, sessions} : day)});
    await page.route('**/api/workout-plans/current', route => route.fulfill({json: state.current}));
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    const picker = editor.locator('.workout-preload');
    await picker.click();
    await expect(page.getByRole('option', {name: 'Planned · Evening strength', exact: true})).toBeVisible();
    await page.getByRole('option', {name: 'Planned · Evening strength', exact: true}).click();
    await editor.getByRole('button', {name: /^Expand Exercise 1:/}).click();
    await expect(editor.getByLabel('Repetitions', {exact: true}).first()).toHaveValue('12');
    await expect(editor.getByLabel('Weight', {exact: true}).first()).toHaveValue('25');
    await editor.getByLabel('Repetitions', {exact: true}).first().fill('10');
    await editor.getByLabel('Weight', {exact: true}).first().fill('20');
    const save = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    const payload = (await save).postDataJSON();
    expect(payload.plannedSessionName).toBe('Evening strength');
    expect(payload.plannedTargets).toEqual([{exerciseName: state.exercises[0].name, exerciseDescription: state.exercises[0].description, trackingMode: 'REPS', exerciseType: 'TRAINING', stretchingUnit: 'SECONDS', segments: [{repetitions: 12, weight: 25}]}]);
    expect(payload.plannedTargets[0]).not.toHaveProperty('exerciseId');
    expect(payload.lines[0].segments[0]).toMatchObject({repetitions: 10, weight: 20});

    await openSpaRoute(page, '/workouts?tab=plan');
    const plan = page.getByRole('region', {name: 'Weekly workout plan'});
    await plan.getByRole('button', {name: 'Edit plan', exact: true}).click();
    const sunday = plan.locator('.plan-day').nth(6);
    await sunday.locator('.plan-day-toggle').click();
    const evening = sunday.locator('.planned-session').filter({hasText: 'Evening strength'});
    await evening.getByRole('button', {name: 'Move up', exact: true}).click();
    await expect(sunday.locator('.planned-session').first()).toHaveAttribute('aria-label', 'Evening strength');
    await expect(sunday.locator('.planned-session').first().getByRole('button', {name: 'Move up', exact: true})).toBeDisabled();
    await page.addStyleTag({content: '.p-toast { display: none !important; }'});
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 1000});
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`multi-session-plan-${width}.png`)});
    }
    const morning = sunday.locator('.planned-session').filter({hasText: 'Morning strength'});
    await morning.getByRole('button', {name: 'Remove', exact: true}).click();
    await expect(sunday.locator('.planned-session')).toHaveCount(1);
    const saturday = plan.locator('.plan-day').nth(5);
    await saturday.getByRole('button', {name: 'Copy', exact: true}).click();
    let copy = page.getByRole('dialog', {name: 'Copy a day'});
    await copy.getByLabel('Copy from').click();
    await page.getByRole('option', {name: 'Sunday', exact: true}).click();
    await copy.getByRole('button', {name: 'Copy', exact: true}).click();
    await saturday.getByRole('button', {name: 'Copy', exact: true}).click();
    copy = page.getByRole('dialog', {name: 'Copy a day'});
    await copy.getByLabel('Copy from').click();
    await page.getByRole('option', {name: 'Sunday', exact: true}).click();
    page.once('dialog', dialog => dialog.accept());
    await copy.getByRole('button', {name: 'Copy', exact: true}).click();
    await plan.getByRole('button', {name: 'Save plan', exact: true}).click();
    expect(state.current.days[6].sessions.map(session => session.name)).toEqual(['Evening strength']);
    expect(state.current.days[5].sessions.map(session => session.name)).toEqual(['Evening strength']);
    await openSpaRoute(page, '/workouts');
    const [reloadedWorkout] = await page.evaluate(async () => fetch('/api/workouts').then(response => response.json()));
    expect(reloadedWorkout.plannedSessionName).toBe('Evening strength');
    expect(reloadedWorkout.plannedTargets).toEqual(payload.plannedTargets);
    expect(reloadedWorkout.lines[0].sets[0]).toMatchObject({repetitions: 10, weight: 20});
});

test('sauna-only and mixed plans keep guided rounds separate from planned targets', async ({page}, testInfo) => {
    await page.clock.install({time: new Date('2026-09-27T12:00:00')});
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, sessions: []}));
    const state = await mockWeeklyPlans(page, {id: 1, startDate: '2026-09-27', reviewDate: '2026-10-26', updateToken: 'sauna-token', days, notes: ''});
    const sessions = [
        {name: 'Sauna reset', note: null, lines: [], saunaSession: true, saunaRoundsMinutes: [12, 8]},
        {name: 'Stretch and sauna', note: null, saunaSession: true, saunaRoundsMinutes: [10], lines: [
            {exerciseId: 3, exerciseName: state.exercises[2].name, exerciseDescription: state.exercises[2].description, trackingMode: 'SECONDS', exerciseType: 'STRETCHING', stretchingUnit: 'SECONDS', segments: [{durationSeconds: 30}]}
        ]}
    ];
    state.setCurrent({...state.current, days: state.current.days.map(day => day.day === 'SUNDAY' ? {...day, rest: false, sessions} : day)});
    await openSpaRoute(page, '/workouts?tab=plan');
    const plan = page.getByRole('region', {name: 'Weekly workout plan'});
    const sunday = plan.locator('.plan-day').nth(6);
    await expect(sunday.locator('.plan-day-summary')).toContainText('2 sauna rounds · 20 min sauna');
    await sunday.locator('.plan-day-toggle').click();
    await expect(sunday).toContainText('1 sauna round · 10 min sauna');
    await expect(sunday).toContainText('Round 1: 12 min · Round 2: 8 min');
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await plan.screenshot({animations: 'disabled', path: testInfo.outputPath(`sauna-plan-${width}.png`)});
    }

    await sunday.locator('.planned-session').first().getByRole('button', {name: 'Start guided'}).click();
    const sauna = page.getByRole('dialog', {name: 'Sauna reset'});
    await expect(sauna.getByRole('region', {name: 'Review workout'})).toBeVisible();
    await expect(sauna.getByRole('button', {name: 'Back to last set'})).toHaveCount(0);
    await sauna.locator('#guided-sauna-round-0').fill('15');
    await sauna.locator('#guided-sauna-round-0').press('Tab');
    await sauna.getByRole('button', {name: 'Add round'}).click();
    await sauna.locator('#guided-sauna-round-2').fill('5');
    await sauna.locator('#guided-sauna-round-2').press('Tab');
    await sauna.getByRole('heading', {name: 'Review workout'}).click();
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await sauna.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        expect(await sauna.locator('.sauna-rounds').evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        expect(await sauna.locator('.sauna-round').evaluateAll(rows => rows.every(row => [...row.children].every(child => child.getBoundingClientRect().right <= row.getBoundingClientRect().right + 1)))).toBe(true);
        await expectWholeWords(sauna.locator('.guided-actions .p-button-label'));
        await sauna.screenshot({animations: 'disabled', path: testInfo.outputPath(`sauna-guided-review-${width}.png`)});
    }
    let saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await sauna.getByRole('heading', {name: 'Review workout', exact: true}).click();
    await page.mouse.move(0, 0);
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectWholeWords(sauna.locator('.guided-actions .p-button-label'));
        await sauna.screenshot({animations: 'disabled', path: testInfo.outputPath(`sauna-ready-${width}.png`)});
    }
    await expect(sauna.getByRole('region', {name: 'Guided workout completion'})).toContainText('Ready to complete');
    await sauna.getByRole('button', {name: 'Complete workout'}).click();
    await expect(sauna.getByRole('region', {name: 'Guided workout completion'})).toContainText('Complete');
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectWholeWords(sauna.locator('.guided-actions .p-button-label'));
        await sauna.screenshot({animations: 'disabled', path: testInfo.outputPath(`sauna-completed-${width}.png`)});
    }
    const saunaCompletedAt = JSON.parse(await page.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'))).workout.endTime;
    await page.clock.fastForward(60000);
    await sauna.getByRole('button', {name: 'Save workout'}).click();
    const saunaPayload = (await saving).postDataJSON();
    expect(saunaPayload.endTime).toBe(saunaCompletedAt);
    expect(saunaPayload).toMatchObject({saunaSession: true, saunaRoundsMinutes: [15, 8, 5], plannedSaunaRoundsMinutes: [12, 8], durationMinutes: 28, lines: []});

    await sunday.locator('.planned-session').nth(1).getByRole('button', {name: 'Start guided'}).click();
    const mixed = page.getByRole('dialog', {name: 'Stretch and sauna'});
    await expect(mixed.getByRole('button', {name: 'Complete set'})).toBeVisible();
    await page.clock.fastForward(65000);
    await mixed.getByRole('button', {name: 'Complete set'}).click();
    await expect(mixed.locator('.guided-timer-summary').getByRole('status')).toContainText('Ready to complete');
    await mixed.getByRole('button', {name: 'Review', exact: true}).click();
    await mixed.locator('#guided-sauna-round-0').fill('9');
    await mixed.locator('#guided-sauna-round-0').press('Tab');
    await mixed.getByRole('heading', {name: 'Review workout', exact: true}).click();
    await page.mouse.move(0, 0);
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectWholeWords(mixed.locator('.guided-actions .p-button-label'));
        await mixed.screenshot({animations: 'disabled', path: testInfo.outputPath(`mixed-sauna-ready-${width}.png`)});
    }
    await mixed.getByRole('button', {name: 'Complete workout'}).click();
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectWholeWords(mixed.locator('.guided-actions .p-button-label'));
        await mixed.screenshot({animations: 'disabled', path: testInfo.outputPath(`mixed-sauna-completed-${width}.png`)});
    }
    const mixedCompletedAt = JSON.parse(await page.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'))).workout.endTime;
    await page.clock.fastForward(60000);
    saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await mixed.getByRole('button', {name: 'Save workout'}).click();
    const mixedPayload = (await saving).postDataJSON();
    expect(mixedPayload.endTime).toBe(mixedCompletedAt);
    expect(mixedPayload.saunaRoundsMinutes).toEqual([9]);
    expect(mixedPayload.plannedSaunaRoundsMinutes).toEqual([10]);
    expect(mixedPayload.lines).toHaveLength(1);
    expect(mixedPayload.durationMinutes).toBe(mixedPayload.warmUpMinutes + mixedPayload.trainingMinutes + mixedPayload.cardioMinutes + mixedPayload.stretchingMinutes + 9);

    await page.getByRole('tab', {name: 'Diary', exact: true}).click();
    for (const width of [376, 1280]) {
        await page.setViewportSize({width, height: 900});
        const diary = page.locator(width <= 575 ? '.diary-mobile' : '.diary-desktop');
        if (width <= 575) await diary.locator('.mobile-diary-summary').nth(1).click();
        await expect(diary).toContainText('3 sauna rounds · 28 min sauna');
        await expect(diary).toContainText('Round 1: 15 min · Round 2: 8 min · Round 3: 5 min');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await diary.screenshot({animations: 'disabled', path: testInfo.outputPath(`sauna-diary-${width}.png`)});
    }
});

test('manual guided workout previews repeated sets before the next exercise and hides the final preview', async ({page}, testInfo) => {
    const exercises = [
        {id: 1, name: 'Slow controlled deep squat with a long exercise name for guided training', description: 'Keep the prescribed range of motion, move slowly through each repetition, and maintain a comfortable upright position.', imageUrl: '/api/workout-exercises/1/image?v=manual-guided', trackingMode: 'REPS', exerciseType: 'TRAINING'},
        {id: 2, name: 'Bench press', description: 'Press with control.', trackingMode: 'REPS', exerciseType: 'TRAINING'}
    ];
    await mockAuthenticatedWorkouts(page, [], exercises);
    await page.route('**/api/workout-exercises/1/image*', route => route.fulfill({path: path.resolve(__dirname, '../../backend/src/main/resources/exercise-images/squat.jpg')}));
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    const firstLine = editor.locator('.workout-line-card').first();
    await firstLine.locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: exercises[0].name, exact: true}).click();
    await firstLine.getByLabel('Repetitions', {exact: true}).fill('12');
    await firstLine.getByRole('button', {name: 'Add set', exact: true}).click();
    await firstLine.getByLabel('Repetitions', {exact: true}).nth(1).fill('10');
    await editor.getByRole('button', {name: 'Add exercise', exact: true}).click();
    const secondLine = editor.locator('.workout-line-card').nth(1);
    await secondLine.locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: exercises[1].name, exact: true}).click();
    await secondLine.getByLabel('Repetitions', {exact: true}).fill('8');
    await editor.getByRole('button', {name: 'Start guided workout', exact: true}).click();
    const guided = page.getByRole('dialog', {name: 'Guided workout', exact: true});
    const next = guided.locator('.guided-next');
    await expect(guided.locator('.guided-progress')).toHaveText('1 of 3 sets');
    await expect(next).toHaveText(`Next: ${exercises[0].name} · Set 2`);
    await captureCompactGuidedWorkout(page, guided, testInfo, 'manual-before-advance');
    await guided.getByLabel('Repetitions', {exact: true}).fill('11');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided.locator('.guided-progress')).toHaveText('2 of 3 sets');
    await expect(next).toHaveText(`Next: ${exercises[1].name} · Set 1`);
    await captureCompactGuidedWorkout(page, guided, testInfo, 'manual-after-advance');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided.locator('.guided-card h2')).toHaveText(exercises[1].name);
    await expect(guided.locator('.guided-progress')).toHaveText('3 of 3 sets');
    await expect(next).toHaveCount(0);
    await expect(guided.locator('.exercise-picture')).toHaveCount(0);
    await guided.locator('.guided-details summary').focus();
    await page.keyboard.press('Enter');
    await expect(guided.locator('.guided-details p')).toHaveText(exercises[1].description);
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    await expect(next).toHaveCount(0);
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    const payload = (await saving).postDataJSON();
    expect(payload.plannedTargets.map(line => line.segments.map(segment => segment.repetitions))).toEqual([[12, 10], [8]]);
    expect(payload.lines.map(line => line.segments.map(segment => segment.repetitions))).toEqual([[11, 10], [8]]);
    await expect(guided).toBeHidden();
});

test('guided workout alternates superset rounds, resumes the current set, and saves actual values with planned targets', async ({page, context}, testInfo) => {
    await page.clock.install({time: new Date('2026-09-27T12:00:00')});
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, sessions: []}));
    const state = await mockWeeklyPlans(page, {id: 1, startDate: '2026-09-27', reviewDate: '2026-10-26', updateToken: 'guided-token', days, notes: ''});
    await page.route('**/workouts*', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
        : route.fallback());
    state.exercises[0].name = 'Slow controlled deep squat with a long exercise name for guided training';
    state.exercises[0].description = 'Keep the prescribed range of motion, move slowly through each repetition, and maintain a comfortable upright position.';
    state.exercises[0].imageUrl = '/api/workout-exercises/1/image?v=test-guided';
    state.exercises[2].name = 'Standing wall calf stretch with controlled breathing and a comfortable range';
    state.exercises[2].description = 'Hold each side with the heel on the floor, breathe steadily, and avoid bouncing throughout the stretch.';
    state.exercises[2].imageUrl = '/api/workout-exercises/3/image?v=test-guided';
    async function mockGuidedPictures() {
        for (const [id, filename] of [[1, 'squat.jpg'], [3, 'wall-calf-stretch.jpg']]) {
            await page.route(`**/api/workout-exercises/${id}/image*`, route => route.fulfill({path: path.resolve(__dirname, '../../backend/src/main/resources/exercise-images', filename)}));
        }
    }
    await mockGuidedPictures();
    const group = 'f04f3d14-c6a7-4e8a-a896-1fd1f1f812f1';
    const session = {name: 'Sunday circuit', note: 'Keep moving', lines: [
        {exerciseId: 1, exerciseName: state.exercises[0].name, exerciseDescription: state.exercises[0].description, trackingMode: 'REPS', exerciseType: 'TRAINING', stretchingUnit: 'SECONDS', supersetGroupId: group, segments: [{repetitions: 20, weight: 20}, {repetitions: 18, weight: 22}]},
        {exerciseId: 3, exerciseName: state.exercises[2].name, exerciseDescription: state.exercises[2].description, trackingMode: 'SECONDS', exerciseType: 'STRETCHING', stretchingUnit: 'SECONDS', supersetGroupId: group, segments: [{durationSeconds: 30}, {durationSeconds: 30}]},
        {exerciseId: 9, exerciseName: state.exercises[4].name, exerciseDescription: state.exercises[4].description, trackingMode: 'CARDIO', cardioMetric: 'SPEED', exerciseType: 'TRAINING', segments: [{durationSeconds: 1200, speedKph: 8, distanceKm: 2, inclinePercent: 1, resistanceLevel: 2}]}
    ]};
    state.setCurrent({...state.current, days: state.current.days.map(day => day.day === 'SUNDAY' ? {day: day.day, rest: false, note: null, sessions: [session]} : day)});
    await page.route('**/workouts*', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
        : route.fallback());
    await page.route('**/api/workout-plans/current', route => route.fulfill({json: state.current}));
    const writes = [];
    page.on('request', request => { if (request.url().endsWith('/api/workouts') && request.method() === 'POST') writes.push(request.postDataJSON()); });
    await openSpaRoute(page, '/workouts?tab=plan');
    const plan = page.getByRole('region', {name: 'Weekly workout plan'});
    await plan.locator('.plan-day').nth(6).locator('.plan-day-toggle').click();
    await plan.getByRole('button', {name: 'Start guided', exact: true}).click();
    const guided = page.getByRole('dialog', {name: 'Sunday circuit'});
    await expect(guided).toContainText(state.exercises[0].name);
    const nextExercise = guided.locator('.guided-next');
    await expect(nextExercise).toHaveText(`Next: ${state.exercises[2].name} · Set 1`);
    const picture = guided.locator('.guided-card img');
    await expect(picture).toBeVisible();
    await expect.poll(() => picture.evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
    await guided.getByRole('button', {name: `View picture of ${state.exercises[0].name}`}).click();
    const pictureDialog = page.getByRole('dialog', {name: state.exercises[0].name});
    await expect(pictureDialog.locator('.exercise-picture-large')).toBeVisible();
    await pictureDialog.getByRole('button', {name: 'Close', exact: true}).last().click();
    await expect(guided.getByRole('button', {name: /Skip/})).toHaveCount(0);
    const secondPage = await context.newPage();
    await mockAuthenticatedWorkouts(secondPage, [], state.exercises);
    const secondResume = await openGuidedWorkoutResumePanel(secondPage, 'jllado@gmail.com', state.exercises);
    const storedDraft = await secondPage.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'));
    await secondResume.getByRole('button', {name: 'Resume guided workout', exact: true}).click();
    const blocked = secondPage.getByRole('dialog', {name: 'Sunday circuit'});
    await expect(blocked).toContainText('This guided workout is open in another tab. Close it there first.');
    for (const action of ['Complete set', 'Review', 'Back to last set', 'Save workout']) await expect(blocked.getByRole('button', {name: action, exact: true})).toHaveCount(0);
    for (const width of [376, 1280]) {
        await secondPage.setViewportSize({width, height: 900});
        expect(await blocked.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await secondPage.screenshot({path: testInfo.outputPath(`guided-workout-blocked-${width}.png`)});
    }
    expect(await secondPage.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'))).toBe(storedDraft);
    await blocked.getByRole('button', {name: 'Close', exact: true}).click();
    await secondResume.getByRole('button', {name: 'Discard guided workout', exact: true}).click();
    await secondPage.getByRole('dialog', {name: 'Discard guided workout?'}).getByRole('button', {name: 'Discard', exact: true}).click();
    await expect(blocked).toContainText('This guided workout is open in another tab. Close it there first.');
    expect(await secondPage.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'))).toBe(storedDraft);
    await blocked.getByRole('button', {name: 'Close', exact: true}).click();
    const otherAccount = await context.newPage();
    await mockAuthenticatedWorkouts(otherAccount, [], state.exercises, {accountEmail: 'other@example.com'});
    const otherResume = await openGuidedWorkoutResumePanel(otherAccount, 'other@example.com', state.exercises);
    await expect(otherResume).toHaveCount(0);
    await otherAccount.close();
    await captureCompactGuidedWorkout(page, guided, testInfo, 'planned-before-advance');
    await page.clock.fastForward(65000);
    const repetitions = guided.getByLabel('Repetitions', {exact: true});
    await repetitions.focus();
    await page.keyboard.press('Control+A');
    await page.keyboard.type('18');
    const weight = guided.getByLabel('Weight (kg)', {exact: true});
    await weight.focus();
    await page.keyboard.press('Control+A');
    await page.keyboard.type('20');
    const complete = guided.getByRole('button', {name: 'Complete set', exact: true});
    await complete.focus();
    await page.keyboard.press('Enter');
    await expect(guided).toContainText(state.exercises[2].name);
    await expect(nextExercise).toHaveText(`Next: ${state.exercises[0].name} · Set 2`);
    await expect(guided.locator('.guided-timer-summary').getByRole('status')).toContainText('Stretching · Running');
    await captureCompactGuidedWorkout(page, guided, testInfo, 'planned-after-advance');
    await guided.getByRole('button', {name: 'Pause', exact: true}).click();
    const pausedTime = await guided.getByRole('timer', {name: 'Total elapsed time'}).textContent();
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await guided.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`guided-workout-paused-${width}.png`)});
    }
    await page.clock.fastForward(3600000);
    await expect(guided.getByRole('timer', {name: 'Total elapsed time'})).toHaveText(pausedTime);
    await guided.getByRole('button', {name: 'Resume', exact: true}).click();
    await page.clock.fastForward(10000);
    await guided.getByLabel('Seconds', {exact: true}).fill('20');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided).toContainText(state.exercises[0].name);
    await expect(nextExercise).toHaveText(`Next: ${state.exercises[2].name} · Set 2`);
    await guided.getByRole('button', {name: 'Close', exact: true}).click();
    const resume = await openGuidedWorkoutResumePanel(page, 'jllado@gmail.com', state.exercises);
    await expect(resume).toContainText('Running');
    await page.clock.fastForward(30000);
    await page.reload();
    await expect.poll(() => page.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'))).not.toBeNull();
    const reloadedResume = await openGuidedWorkoutResumePanel(page, 'jllado@gmail.com', state.exercises);
    await mockGuidedPictures();
    await reloadedResume.getByRole('button', {name: 'Resume guided workout', exact: true}).click();
    await page.clock.fastForward(35000);
    await expect(guided).toContainText(state.exercises[0].name);
    await expect(guided.getByLabel('Repetitions', {exact: true})).toHaveValue('18');
    await expect.poll(() => guided.locator('.guided-card img').evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided).toContainText(state.exercises[2].name);
    await page.clock.fastForward(65000);
    await guided.getByLabel('Seconds', {exact: true}).fill('25');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided).toContainText(state.exercises[4].name);
    await expect(nextExercise).toHaveCount(0);
    await expect(guided.locator('.guided-progress')).toHaveText('5 of 5 sets');
    for (const [width, height] of [[376, 667], [390, 844], [1280, 900]]) {
        await page.setViewportSize({width, height});
        for (const label of ['Duration (minutes)', 'Seconds', 'Speed (km/h)', 'Cadence (rpm)', 'Distance (km)', 'Incline (%)', 'Resistance', 'Calories', 'Average heart rate (bpm)']) await expect(guided.getByLabel(label, {exact: true})).toHaveCount(1);
        const progress = await guided.locator('.guided-progress').boundingBox();
        const complete = await guided.getByRole('button', {name: 'Complete set', exact: true}).boundingBox();
        const layout = await guided.locator('.guided-content').evaluate(element => {
            element.scrollTop = element.scrollHeight;
            const parent = element.parentElement;
            return {scrollTop: element.scrollTop, scrollHeight: element.scrollHeight, clientHeight: element.clientHeight, content: element.getBoundingClientRect().toJSON(), dialogScopeAttributes: parent.parentElement.getAttributeNames().filter(name => name.startsWith('data-v-')), dialogContentDisplay: getComputedStyle(parent).display, dialogContentOverflow: getComputedStyle(parent).overflowY};
        });
        await testInfo.attach(`guided-scroll-layout-${width}`, {body: JSON.stringify(layout), contentType: 'application/json'});
        require('node:fs').writeFileSync(testInfo.outputPath(`guided-scroll-layout-${width}.json`), JSON.stringify(layout, null, 2));
        expect(layout.dialogContentDisplay).toBe('flex');
        expect(layout.dialogContentOverflow).toBe('hidden');
        if (width === 376) expect(layout.scrollTop).toBeGreaterThan(0);
        const lastField = await guided.getByLabel('Average heart rate (bpm)', {exact: true}).boundingBox();
        expect(lastField.y).toBeGreaterThanOrEqual(layout.content.y);
        expect(lastField.y + lastField.height).toBeLessThanOrEqual(layout.content.y + layout.content.height + 1);
        expect(await guided.locator('.guided-progress').boundingBox()).toEqual(progress);
        expect(await guided.getByRole('button', {name: 'Complete set', exact: true}).boundingBox()).toEqual(complete);
        expect(progress.y).toBeGreaterThanOrEqual(0);
        expect(complete.y + complete.height).toBeLessThanOrEqual(height);
        expect(await guided.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-workout-final-${width}.png`)});
    }
    await page.clock.fastForward(65000);
    await guided.getByLabel('Duration (minutes)', {exact: true}).fill('15');
    await guided.getByLabel('Seconds', {exact: true}).fill('30');
    await guided.getByLabel('Speed (km/h)', {exact: true}).fill('9');
    await guided.getByLabel('Distance (km)', {exact: true}).fill('2.2');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    await expect(guided.getByRole('heading', {name: 'Review workout'})).toBeVisible();
    const reviewPictures = guided.locator('.guided-review-line img');
    await expect(reviewPictures).toHaveCount(2);
    for (const picture of await reviewPictures.all()) {
        await picture.scrollIntoViewIfNeeded();
        await expect.poll(() => picture.evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
    }
    await guided.locator('.guided-content').evaluate(element => { element.scrollTop = 0; });
    await expect(guided.locator('.guided-timer-review')).toContainText('Warm-up: 0 min');
    await expect(guided.locator('.guided-timer-review')).toContainText('Training: 3 min');
    await expect(guided.locator('.guided-timer-review')).toContainText('Cardio: 2 min');
    await expect(guided.locator('.guided-timer-review')).toContainText('Stretching: 2 min');
    const completedTime = await guided.getByRole('timer', {name: 'Total elapsed time'}).textContent();
    await page.clock.fastForward(3600000);
    await expect(guided.getByRole('timer', {name: 'Total elapsed time'})).toHaveText(completedTime);
    for (const [width, height] of [[376, 667], [390, 844], [1280, 900]]) {
        await page.setViewportSize({width, height});
        expect(await guided.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        const save = await guided.getByRole('button', {name: 'Save workout', exact: true}).boundingBox();
        expect(save.y).toBeGreaterThanOrEqual(0);
        expect(save.y + save.height).toBeLessThanOrEqual(height);
        await expect(nextExercise).toHaveCount(0);
        await page.screenshot({path: testInfo.outputPath(`guided-workout-review-${width}.png`)});
    }
    await guided.getByRole('button', {name: 'Back to last set', exact: true}).click();
    await expect(guided.locator('.guided-timer-summary').getByRole('status')).toContainText('Cardio · Running');
    await page.clock.fastForward(65000);
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    await expect(guided.locator('.guided-timer-review')).toContainText('Cardio: 3 min');
    let failFirstSave = true;
    await page.route('**/api/workouts', route => {
        if (route.request().method() === 'POST' && failFirstSave) {
            failFirstSave = false;
            return route.fulfill({status: 503, body: 'Workout service unavailable'});
        }
        return route.fallback();
    });
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    await expect(guided.getByRole('alert')).toContainText('Workout service unavailable');
    const firstRecordingKey = writes.at(-1).recordingKey;
    expect(writes.at(-1)).toMatchObject({startTime: '12:00', warmUpMinutes: 0, trainingMinutes: 3, cardioMinutes: 3, stretchingMinutes: 2, durationMinutes: 8});
    expect(JSON.parse(await page.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'))).currentStep).toBe(5);
    await guided.getByRole('button', {name: 'Close', exact: true}).click();
    expect(await page.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com'))).not.toBeNull();
    await page.reload();
    const completeResume = await openGuidedWorkoutResumePanel(page, 'jllado@gmail.com', state.exercises);
    await mockGuidedPictures();
    await expect(completeResume.getByRole('button', {name: 'Resume guided workout', exact: true})).toBeVisible();
    await completeResume.getByRole('button', {name: 'Resume guided workout', exact: true}).click();
    await expect(guided.locator('.guided-timer-summary').getByRole('status')).toContainText('Workout · Complete');
    await expect(guided.getByRole('heading', {name: 'Review workout'})).toBeVisible();
    await expect(reviewPictures).toHaveCount(2);
    for (const picture of await reviewPictures.all()) {
        await picture.scrollIntoViewIfNeeded();
        await expect.poll(() => picture.evaluate(image => image.naturalWidth)).toBeGreaterThan(0);
    }
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    await expect(guided).toBeHidden();
    expect(writes).toHaveLength(2);
    expect(writes[0].recordingKey).toMatch(/^[0-9a-f-]{36}$/i);
    expect(writes[1].recordingKey).toBe(firstRecordingKey);
    expect(writes[1].plannedTargets.map(line => line.segments)).toEqual([
        [{repetitions: 20, weight: 20}, {repetitions: 18, weight: 22}],
        [{durationSeconds: 30}, {durationSeconds: 30}],
        [{durationSeconds: 1200, speedKph: 8, distanceKm: 2, inclinePercent: 1, resistanceLevel: 2}]
    ]);
    expect(writes[1].plannedTargets.map(line => line.supersetGroupId)).toEqual([group, group, undefined]);
    expect(writes[1].lines.map(line => line.segments.map(segment => segment.repetitions ?? segment.durationSeconds))).toEqual([[18, 18], [20, 25], [930]]);
    expect(writes[1].lines[2].segments[0]).toMatchObject({speedKph: 9, distanceKm: 2.2});
});

test('guided workout goes back, minimizes across navigation, and saves its actual end time', async ({page}, testInfo) => {
    await page.clock.install({time: new Date('2026-09-27T12:00:00Z')});
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, sessions: []}));
    const state = await mockWeeklyPlans(page, {id: 1, startDate: '2026-09-27', reviewDate: '2026-10-26', updateToken: 'guided-controls-token', days, notes: ''});
    state.setCurrent({...state.current, days: state.current.days.map(day => day.day === 'SUNDAY' ? {day: day.day, rest: false, note: null, sessions: [{name: 'Two-set workout', note: null, lines: [
        {exerciseId: 1, exerciseName: state.exercises[0].name, exerciseDescription: state.exercises[0].description, exerciseType: 'TRAINING', trackingMode: 'REPS', stretchingUnit: 'SECONDS', segments: [{repetitions: 10, weight: 20}]},
        {exerciseId: 3, exerciseName: state.exercises[2].name, exerciseDescription: state.exercises[2].description, exerciseType: 'STRETCHING', trackingMode: 'SECONDS', stretchingUnit: 'SECONDS', segments: [{durationSeconds: 8}]}
    ]}]} : day)});
    await page.route('**/workouts*', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
        : route.fallback());
    await openSpaRoute(page, '/workouts?tab=plan');
    await page.getByRole('region', {name: 'Weekly workout plan'}).locator('.plan-day').nth(6).locator('.plan-day-toggle').click();
    await page.getByRole('button', {name: 'Start guided', exact: true}).click();
    const guided = page.getByRole('dialog', {name: 'Two-set workout'});
    await guided.getByLabel('Repetitions', {exact: true}).fill('12');
    await guided.getByLabel('Weight (kg)', {exact: true}).fill('24');
    await guided.getByRole('button', {name: 'Pause', exact: true}).click();
    const pausedBeforeComplete = await guided.getByRole('timer', {name: 'Total elapsed time'}).textContent();
    await page.clock.fastForward(60000);
    await expect(guided.getByRole('timer', {name: 'Total elapsed time'})).toHaveText(pausedBeforeComplete);
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided.getByLabel('Seconds', {exact: true})).toHaveValue('8');
    await expect(guided.locator('.guided-timer-summary').getByRole('status')).toContainText('Stretching · Running');
    const nextPhaseBeforeComplete = await guided.getByRole('timer', {name: 'Stretching elapsed time'}).textContent();
    await page.clock.fastForward(10000);
    await expect(guided.getByRole('timer', {name: 'Stretching elapsed time'})).not.toHaveText(nextPhaseBeforeComplete);
    await expect(guided.getByRole('timer', {name: 'Total elapsed time'})).not.toHaveText(pausedBeforeComplete);
    await guided.getByRole('button', {name: 'Back', exact: true}).click();
    await expect(guided.getByLabel('Repetitions', {exact: true})).toHaveValue('12');
    await expect(guided.getByLabel('Weight (kg)', {exact: true})).toHaveValue('24');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await guided.getByRole('button', {name: 'Minimize', exact: true}).click();
    const minimized = page.getByRole('region', {name: 'Minimized guided workout'});
    await expect(minimized).toBeVisible();
    await page.getByRole('link', {name: 'Coach Notes', exact: true}).click();
    await expect(page.getByRole('heading', {name: 'Coach Notes', exact: true})).toBeVisible();
    await expect(page.getByText('No Coach Notes yet.', {exact: true})).toBeVisible();
    for (const [width, height] of [[320, 900], [376, 900], [390, 900], [640, 900], [1280, 900]]) {
        await page.setViewportSize({width, height});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        const bounds = await minimized.boundingBox();
        expect(bounds.x).toBeGreaterThanOrEqual(0);
        expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-workout-minimized-${width}.png`)});
    }
    const elapsedSeconds = value => {
        const [, hours, minutes, seconds] = value.match(/Total (\d{2}):(\d{2}):(\d{2})/);
        return Number(hours) * 3600 + Number(minutes) * 60 + Number(seconds);
    };
    const elapsedBeforeBrowsing = elapsedSeconds(await minimized.locator('div > span').textContent());
    await page.clock.fastForward(65000);
    await expect.poll(async () => elapsedSeconds(await minimized.locator('div > span').textContent())).toBeGreaterThanOrEqual(elapsedBeforeBrowsing + 65);
    await page.getByRole('menuitem', {name: 'Plan', exact: true}).click();
    await page.locator('a[href="/workouts"]').click();
    await page.getByRole('tab', {name: 'Plan', exact: true}).click();
    await page.getByRole('button', {name: 'New plan', exact: true}).click();
    const laterDialog = page.getByRole('dialog', {name: 'New weekly plan', exact: true});
    await expect(laterDialog).toBeVisible();
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        expect(await minimized.evaluate(element => {
            const box = element.getBoundingClientRect();
            return !document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2).closest('.guided-workout-minimized');
        })).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-later-dialog-${width}.png`)});
    }
    await laterDialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    await minimized.getByRole('button', {name: 'Reopen workout', exact: true}).click();
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectWholeWords(guided.locator('.guided-actions .p-button-label'));
        await guided.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-running-${width}.png`)});
    }
    await guided.getByRole('button', {name: 'Pause', exact: true}).click();
    const pausedTime = await guided.getByRole('timer', {name: 'Total elapsed time'}).textContent();
    await page.clock.fastForward(60000);
    await expect(guided.getByRole('timer', {name: 'Total elapsed time'})).toHaveText(pausedTime);
    await guided.getByRole('button', {name: 'Minimize', exact: true}).click();
    await page.clock.fastForward(60000);
    await expect(minimized).toContainText('Paused');
    await minimized.getByRole('button', {name: 'Reopen workout', exact: true}).click();
    await expect(guided.getByRole('timer', {name: 'Total elapsed time'})).toHaveText(pausedTime);
    for (const width of [320, 376, 390, 640, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectWholeWords(guided.locator('.guided-actions .p-button-label'));
        await guided.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-paused-reopened-${width}.png`)});
    }
    await guided.getByRole('button', {name: 'Resume', exact: true}).click();
    await page.clock.fastForward(10000);
    await guided.getByLabel('Seconds', {exact: true}).fill('9');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    const savedDraft = JSON.parse(await page.evaluate(() => localStorage.getItem('guided-workout-v1:jllado@gmail.com')));
    expect(Math.abs(Date.parse(savedDraft.workout.endTime) - await page.evaluate(() => Date.now()))).toBeLessThan(1000);
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    await page.clock.fastForward(60000);
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({endTime: savedDraft.workout.endTime, lines: [{segments: [{repetitions: 12, weight: 24}]}, {segments: [{durationSeconds: 9}]}]});
});

test('guided workout edits weight for timed strength sets while preserving planned targets', async ({page}, testInfo) => {
    await page.clock.install({time: new Date('2026-09-27T12:00:00')});
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, sessions: []}));
    const state = await mockWeeklyPlans(page, {id: 1, startDate: '2026-09-27', reviewDate: '2026-10-26', updateToken: 'guided-weight-token', days, notes: ''});
    const plannedTarget = {exerciseId: 4, exerciseName: state.exercises[3].name, exerciseDescription: state.exercises[3].description, trackingMode: 'SECONDS', exerciseType: 'TRAINING', stretchingUnit: 'SECONDS', segments: [{durationSeconds: 65, weight: 5}]};
    state.setCurrent({...state.current, days: state.current.days.map(day => day.day === 'SUNDAY' ? {...day, rest: false, sessions: [{name: 'Timed strength', note: null, lines: [plannedTarget]}]} : day)});
    await openSpaRoute(page, '/workouts?tab=plan');
    const plan = page.getByRole('region', {name: 'Weekly workout plan'});
    const sunday = plan.locator('.plan-day').nth(6);
    await sunday.locator('.plan-day-toggle').click();
    await sunday.locator('.planned-session').getByRole('button', {name: 'Start guided'}).click();

    const guided = page.getByRole('dialog', {name: 'Timed strength'});
    await expect(guided.getByLabel('Duration (minutes)', {exact: true})).toHaveValue('1');
    await expect(guided.getByLabel('Seconds', {exact: true})).toHaveValue('5');
    const weight = guided.getByLabel('Weight (kg)', {exact: true});
    await expect(weight).toHaveValue('5');
    for (const [width, height] of [[390, 844], [640, 900], [1280, 900]]) {
        await page.setViewportSize({width, height});
        await weight.scrollIntoViewIfNeeded();
        const fieldBounds = await weight.boundingBox();
        const contentBounds = await guided.locator('.guided-content').boundingBox();
        const complete = guided.getByRole('button', {name: 'Complete set', exact: true});
        const actionBounds = await complete.boundingBox();
        expect(fieldBounds.y).toBeGreaterThanOrEqual(contentBounds.y);
        expect(fieldBounds.y + fieldBounds.height).toBeLessThanOrEqual(contentBounds.y + contentBounds.height + 1);
        expect(actionBounds.y).toBeGreaterThanOrEqual(0);
        expect(actionBounds.y + actionBounds.height).toBeLessThanOrEqual(height);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await expect(weight).toBeVisible();
        await expect(complete).toBeVisible();
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-weight-active-${width}.png`)});
    }
    await weight.fill('12.5');
    await guided.getByLabel('Seconds', {exact: true}).fill('16');
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    const reviewLine = guided.locator('.guided-review-line').filter({hasText: state.exercises[3].name});
    await expect(reviewLine).toContainText('1 min 16 sec · 12.5 kg');

    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    const payload = (await saving).postDataJSON();
    expect(payload.plannedTargets[0].segments).toEqual([{durationSeconds: 65, weight: 5}]);
    expect(payload.lines[0].segments[0]).toMatchObject({durationSeconds: 76, weight: 12.5});
});

test('guided workout records exercise time, distinguishes skipped work from unlogged work, and keeps superset flow', async ({page}, testInfo) => {
    await page.clock.install({time: new Date('2026-09-27T12:00:00')});
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, sessions: []}));
    const state = await mockWeeklyPlans(page, {id: 1, startDate: '2026-09-27', reviewDate: '2026-10-26', updateToken: 'guided-skip-token', days, notes: ''});
    const group = '6f4d5d80-a643-4a6f-955f-54445ea62c39';
    const session = {name: 'Skip and record exercise time', note: null, lines: [
        {exerciseId: 1, exerciseName: state.exercises[0].name, trackingMode: 'REPS', exerciseType: 'TRAINING', supersetGroupId: group, segments: [{repetitions: 10, weight: 20}, {repetitions: 9, weight: 20}, {repetitions: 8, weight: 20}]},
        {exerciseId: 4, exerciseName: state.exercises[3].name, trackingMode: 'SECONDS', exerciseType: 'TRAINING', supersetGroupId: group, segments: [{durationSeconds: 30, weight: 5}, {durationSeconds: 30, weight: 5}, {durationSeconds: 30, weight: 5}]},
        {exerciseId: 9, exerciseName: state.exercises[4].name, trackingMode: 'CARDIO', cardioMetric: 'SPEED', exerciseType: 'TRAINING', segments: [{durationSeconds: 60, speedKph: 8, distanceKm: 0.1}]}
    ]};
    state.setCurrent({...state.current, days: state.current.days.map(day => day.day === 'SUNDAY' ? {day: day.day, rest: false, note: null, sessions: [session]} : day)});
    await openSpaRoute(page, '/workouts?tab=plan');
    const plan = page.getByRole('region', {name: 'Weekly workout plan'});
    await plan.locator('.plan-day').nth(6).locator('.plan-day-toggle').click();
    await plan.getByRole('button', {name: 'Start guided', exact: true}).click();
    const guided = page.getByRole('dialog', {name: session.name});
    const markSkipped = guided.getByRole('button', {name: 'Mark skipped', exact: true});
    const nextWithoutLogging = guided.getByRole('button', {name: 'Next without logging', exact: true});
    await expect(markSkipped).toBeVisible();
    await expect(nextWithoutLogging).toBeVisible();
    await expect(markSkipped).toHaveAccessibleName('Mark skipped');
    await expect(nextWithoutLogging).toHaveAccessibleName('Next without logging');
    for (const [width, height] of [[320, 740], [390, 844], [640, 900], [1280, 900]]) {
        await page.setViewportSize({width, height});
        await guided.getByLabel('Repetitions', {exact: true}).scrollIntoViewIfNeeded();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await expect(guided.getByLabel('Repetitions', {exact: true})).toBeVisible();
        await expect(guided.getByRole('button', {name: 'Complete set', exact: true})).toBeVisible();
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`guided-skip-active-${width}.png`)});
    }
    await guided.getByLabel('Weight (kg)', {exact: true}).focus();
    await page.keyboard.press('Tab');
    await expect(markSkipped).toBeFocused();
    expect(await markSkipped.evaluate(button => button.matches(':focus-visible'))).toBe(true);
    await page.keyboard.press('Tab');
    await expect(nextWithoutLogging).toBeFocused();
    expect(await nextWithoutLogging.evaluate(button => button.matches(':focus-visible'))).toBe(true);
    await page.clock.fastForward(5000);
    await guided.getByRole('button', {name: 'Pause', exact: true}).click();
    await page.clock.fastForward(60000);
    await guided.getByRole('button', {name: 'Resume', exact: true}).click();
    await page.clock.fastForward(7000);
    await expect(guided.locator('.guided-exercise-time')).toContainText(/00:00:1[23]/);
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided.locator('.guided-card h2')).toHaveText(state.exercises[3].name);
    await page.clock.fastForward(4000);
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await expect(guided.locator('.guided-card h2')).toHaveText(state.exercises[0].name);
    await page.clock.fastForward(5000);
    await guided.getByRole('button', {name: 'Next without logging', exact: true}).click();
    await expect(guided.locator('.guided-card h2')).toHaveText(state.exercises[3].name);
    await expect(guided.locator('.guided-card')).toHaveAttribute('aria-label', `${state.exercises[3].name}, set 2`);
    await guided.getByRole('button', {name: 'Mark skipped', exact: true}).click();
    await expect(guided.locator('.guided-card h2')).toHaveText(state.exercises[4].name);
    await page.clock.fastForward(3000);
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    await expect(guided).toBeVisible();
    await expect(guided.getByRole('heading', {name: 'Review workout'})).toBeVisible();
    const review = guided.getByRole('region', {name: 'Review workout'});
    const recordedSquatReview = review.locator('.guided-review-line').filter({hasText: state.exercises[0].name});
    const squatReviewTime = await recordedSquatReview.locator('p').innerText();
    expect(squatReviewTime).toMatch(/Exercise time 00:00:1[23]/);
    const squatExerciseSeconds = Number(squatReviewTime.slice(-2));
    const skippedLine = review.locator('.guided-review-line').filter({hasText: state.exercises[3].name});
    await expect(skippedLine).toContainText('Skipped · no time recorded');
    const skippedReviewTime = await skippedLine.locator('p').innerText();
    expect(skippedReviewTime).toMatch(/Exercise time 00:00:0[45]/);
    const skippedExerciseSeconds = Number(skippedReviewTime.slice(-2));

    const saving = page.waitForResponse(response => response.url().endsWith('/api/workouts') && response.request().method() === 'POST');
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    const savedResponse = await saving;
    const payload = savedResponse.request().postDataJSON();
    expect(payload.plannedTargets.map(line => line.segments.length)).toEqual([3, 3, 1]);
    expect(payload.lines.map(line => line.exerciseId)).toEqual([1, 4, 9]);
    expect(payload.lines[0]).toMatchObject({exerciseDurationSeconds: squatExerciseSeconds});
    expect(payload.lines[0].segments).toHaveLength(1);
    expect(payload.lines[1]).toMatchObject({exerciseDurationSeconds: skippedExerciseSeconds});
    expect(payload.lines[1].segments).toEqual([
        expect.objectContaining({durationSeconds: 30, weight: 5, skipped: false}),
        expect.objectContaining({skipped: true}),
        expect.objectContaining({skipped: true})
    ]);
    expect(payload.lines[2].exerciseDurationSeconds).toBe(3);
    const {result: savedWorkout} = await savedResponse.json();
    expect(savedWorkout.lines[0]).toMatchObject({exerciseDurationSeconds: squatExerciseSeconds, sets: [{repetitions: 10, weight: 20}]});
    expect(savedWorkout.lines[1].sets).toEqual([
        expect.objectContaining({durationSeconds: 30, skipped: false}),
        expect.objectContaining({skipped: true}),
        expect.objectContaining({skipped: true})
    ]);
    await page.getByRole('tab', {name: 'Diary', exact: true}).click();
    const recordedSquat = page.locator('.diary-workout-line').filter({hasText: state.exercises[0].name});
    await expect(recordedSquat).toContainText(`Exercise time 00:00:${String(squatExerciseSeconds).padStart(2, '0')}`);
    const recordedPlank = page.locator('.diary-workout-line').filter({hasText: state.exercises[3].name});
    await expect(recordedPlank).toContainText('Skipped · no time recorded');
    await expect(recordedPlank).toContainText(`Exercise time 00:00:${String(skippedExerciseSeconds).padStart(2, '0')}`);
});

test('guided workout records warm-up, training, cardio and stretching phase times', async ({page}) => {
    await page.clock.install({time: new Date('2026-09-27T12:00:00')});
    const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: null, sessions: []}));
    const state = await mockWeeklyPlans(page, {id: 1, startDate: '2026-09-27', reviewDate: '2026-10-26', updateToken: 'phase-token', days, notes: ''});
    const lines = [
        {exerciseId: 2, exerciseName: 'Exercise bike', trackingMode: 'CARDIO', exerciseType: 'WARM_UP', segments: [{durationSeconds: 60}]},
        {exerciseId: 1, exerciseName: 'Squat', trackingMode: 'REPS', exerciseType: 'TRAINING', segments: [{repetitions: 10, weight: 20}]},
        {exerciseId: 9, exerciseName: 'Outdoor run', trackingMode: 'CARDIO', exerciseType: 'TRAINING', segments: [{durationSeconds: 60}]},
        {exerciseId: 3, exerciseName: 'Wall calf stretch', trackingMode: 'SECONDS', exerciseType: 'STRETCHING', stretchingUnit: 'SECONDS', segments: [{durationSeconds: 30}]}
    ];
    state.setCurrent({...state.current, days: state.current.days.map(day => day.day === 'SUNDAY' ? {day: day.day, rest: false, note: null, sessions: [{name: 'Four phases', note: null, lines}]} : day)});
    await page.route('**/workouts*', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
        : route.fallback());
    await page.route('**/api/workout-plans/current', route => route.fulfill({json: state.current}));
    await openSpaRoute(page, '/workouts?tab=plan');
    const plan = page.getByRole('region', {name: 'Weekly workout plan'});
    await plan.locator('.plan-day').nth(6).locator('.plan-day-toggle').click();
    await plan.getByRole('button', {name: 'Start guided', exact: true}).click();
    const guided = page.getByRole('dialog', {name: 'Four phases'});
    for (const phase of ['Warm-up', 'Training', 'Cardio', 'Stretching']) {
        await expect(guided.locator('.guided-timer-summary').getByRole('status')).toContainText(`${phase} · Running`);
        await page.clock.fastForward(65000);
        await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    }
    await expect(guided.locator('.guided-timer-summary').getByRole('status')).toContainText('Workout · Complete');
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({startTime: '12:00', warmUpMinutes: 2, trainingMinutes: 2, cardioMinutes: 2, stretchingMinutes: 2, durationMinutes: 8});
});

test('guided workout keeps older drafts untimed without changing their recorded timing', async ({page}) => {
    const exercise = {id: 1, name: 'Bench press', trackingMode: 'REPS', exerciseType: 'TRAINING'};
    await mockAuthenticatedWorkouts(page, [], [exercise]);
    await page.route('**/workouts*', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
        : route.fallback());
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    const line = editor.locator('.workout-line-card').first();
    await line.locator('.p-dropdown').first().click();
    await page.getByRole('option', {name: 'Bench press', exact: true}).click();
    await line.getByLabel('Repetitions').fill('10');
    await editor.getByRole('button', {name: 'Start guided workout', exact: true}).click();
    const guided = page.getByRole('dialog', {name: 'Guided workout'});
    await guided.getByRole('button', {name: 'Close', exact: true}).click();
    const originalKey = await page.evaluate(() => {
        const key = 'guided-workout-v1:jllado@gmail.com', draft = JSON.parse(localStorage.getItem(key));
        delete draft.timer;
        Object.assign(draft.workout, {durationMinutes: 17, warmUpMinutes: 5, trainingMinutes: 12, cardioMinutes: null, stretchingMinutes: 0});
        localStorage.setItem(key, JSON.stringify(draft));
        return draft.recordingKey;
    });
    await page.reload();
    const resume = await openGuidedWorkoutResumePanel(page, 'jllado@gmail.com', [exercise]);
    await resume.getByRole('button', {name: 'Resume guided workout', exact: true}).click();
    await expect(guided.getByText('This draft started before automatic timing.', {exact: false})).toBeVisible();
    await guided.getByRole('button', {name: 'Complete set', exact: true}).click();
    await guided.getByRole('button', {name: 'Review', exact: true}).click();
    const saving = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
    await guided.getByRole('button', {name: 'Save workout', exact: true}).click();
    expect((await saving).postDataJSON()).toMatchObject({recordingKey: originalKey, durationMinutes: 17, warmUpMinutes: 5, trainingMinutes: 12, cardioMinutes: null, stretchingMinutes: 0});
});

for (const width of [376, 390, 575, 640, 960, 1280]) {
    test(`weekly workout plan reuses completed workouts at ${width}px`, async ({page}, testInfo) => {
        await page.clock.install({time: new Date('2026-09-12T12:00:00')});
        const days = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'].map(day => ({day, rest: true, note: 'Keep my plan note', lines: []}));
        const state = await mockWeeklyPlans(page, {id: 1, updateToken: 'first', startDate: '2026-08-01', reviewDate: '2026-08-30', days, notes: ''});
        await page.setViewportSize({width, height: 900});
        const segments = [
            {exerciseId: 2, calories: 80, averageHeartRate: 120, segments: [{durationSeconds: 600, speedKph: 10, distanceKm: 1, inclinePercent: 0, resistanceLevel: 2}]},
            {exerciseId: 1, segments: [{repetitions: 10, weight: 40}, {repetitions: 8, weight: 45}]},
            {exerciseId: 4, segments: [{durationSeconds: 65, weight: 0}]},
            {exerciseId: 3, stretchingUnit: width === 390 || width === 1280 ? 'BREATHS' : 'SECONDS', segments: [width === 390 || width === 1280 ? {breaths: 5} : {durationSeconds: 35}]}
        ];
        const sources = Array.from({length: 15}, (_, i) => workoutResponse(i + 1, {workoutDate: `2026-09-${String(12 - Math.floor(i / 2)).padStart(2, '0')}`, startTime: i % 2 ? '18:00' : '08:00', durationMinutes: 60, note: 'Recorded note', lines: segments}, state.exercises));
        const original = JSON.stringify(sources);
        await page.route('**/api/workouts/preload?**', route => {
            expect(new URL(route.request().url()).searchParams.get('through')).toBe('2026-09-12');
            return route.fulfill({json: sources});
        });
        const writes = [];
        page.on('request', request => { if (/\/api\/workouts(?:\/|$)/.test(new URL(request.url()).pathname) && request.method() !== 'GET') writes.push(request.url()); });
        await openSpaRoute(page, '/workouts?tab=plan');
        const section = page.getByRole('region', {name: 'Weekly workout plan'});
        const editor = page.getByRole('dialog', {name: 'Planned workout', exact: true});
        await section.getByRole('button', {name: 'Edit plan', exact: true}).click();
        await section.locator('.plan-day').first().getByRole('button', {name: 'Add session', exact: true}).click();
        const picker = editor.getByRole('combobox', {name: 'Use completed workout'});
        await expect(picker).toBeEnabled();
        await picker.focus();
        await page.keyboard.press('ArrowDown');
        await expect(page.getByRole('option')).toHaveCount(14);
        await expect(page.getByRole('option').first()).toContainText('Sat, 12/09/2026');
        await expect(page.getByRole('option').first()).toContainText('08:00');
        await page.screenshot({path: testInfo.outputPath(`planned-preloads-${width}.png`), animations: 'disabled'});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.getByRole('option').first().click();
        await expect(picker).toContainText('Sat, 12/09/2026');
        await expect(editor.locator('.workout-line-card')).toHaveCount(4);
        await expect(editor.locator('#workout-editor-note')).toHaveValue('');
        await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
        await expect(section.locator('.plan-day').first()).toContainText('Rest');
        await section.locator('.plan-day').first().getByRole('button', {name: 'Add session', exact: true}).click();
        await editor.locator('#planned-preload-workout').click();
        await page.getByRole('option').first().click();
        await editor.getByRole('button', {name: 'Expand Exercise 3: Plank', exact: true}).click();
        await editor.locator('.workout-line-card').nth(2).getByLabel('Minutes', {exact: true}).fill('2');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expect(section.locator('.plan-day').first().locator('.planned-session')).toHaveCount(1);
        await section.locator('.plan-day').first().getByRole('button', {name: /^Edit /}).click();
        await editor.locator('#planned-preload-workout').click();
        await page.getByRole('option').first().click();
        await expect(picker).toContainText('Sat, 12/09/2026');
        await expect(editor.locator('.workout-line-card')).toHaveCount(4);
        await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
        await section.getByRole('button', {name: 'Save plan', exact: true}).click();
        await expect(section.getByRole('button', {name: 'Edit plan', exact: true})).toBeVisible();
        const saved = state.current.days[0];
        expect(saved.note).toBeNull();
        expect(saved.sessions[0].note).toBeNull();
        expect(saved.sessions[0].lines.map(line => line.exerciseId)).toEqual([2, 1, 4, 3]);
        expect(saved.sessions[0].lines[0].segments[0]).toMatchObject(segments[0].segments[0]);
        expect(saved.sessions[0].lines[1].segments).toMatchObject(segments[1].segments);
        expect(saved.sessions[0].lines[2].segments[0].durationSeconds).toBe(125);
        expect(saved.sessions[0].lines[3].stretchingUnit).toBe(segments[3].stretchingUnit);
        expect(saved.sessions[0].lines[3].segments[0]).toMatchObject(segments[3].segments[0]);
        expect(saved.sessions[0].lines[0]).not.toHaveProperty('calories');
        expect(saved.sessions[0].lines[0]).not.toHaveProperty('averageHeartRate');
        const monday = section.locator('.plan-day').first();
        await monday.getByRole('button').click();
        await expect(monday.locator('.plan-day-summary')).toHaveText('Squat with a deliberately long descriptive exercise name');
        await monday.getByRole('button').click();
        await expect(monday).toContainText('Exercise bike');
        await expect(monday).toContainText('Squat with a deliberately long descriptive exercise name');
        await expect(monday).toContainText('Plank');
        await expect(monday).toContainText('Wall calf stretch');
        await expect(monday).toContainText('10:00 · 1 km · 10 km/h · 0 % incline · 2 resistance');
        await expect(monday).toContainText('45 kg × 8 reps');
        await expect(monday).toContainText('2:05');
        await openSpaRoute(page, '/workouts?tab=plan');
        await expect(section.locator('.plan-day').first().locator('.plan-day-summary')).toHaveText('Squat with a deliberately long descriptive exercise name');
        await section.locator('.plan-day').first().getByRole('button').click();
        await expect(section.locator('.plan-day').first()).toContainText('Exercise bike');
        await expect(section.locator('.plan-day').first()).toContainText('45 kg × 8 reps');
        await expect(section.locator('.plan-day').first()).toContainText('2:05');
        expect(JSON.stringify(sources)).toBe(original);
        expect(writes).toEqual([]);
    });
}

test('weekly workout plan handles completed workout loading, retry and empty history', async ({page}) => {
    await mockWeeklyPlans(page);
    let release;
    const pending = new Promise(resolve => { release = resolve; });
    let attempts = 0;
    await page.route('**/api/workouts/preload?**', async route => {
        attempts++;
        if (attempts === 1) { await pending; return route.fulfill({status: 503, body: 'Could not load workouts'}); }
        return route.fulfill({json: []});
    });
    await openSpaRoute(page, '/workouts?tab=plan');
    await page.getByRole('button', {name: 'New plan', exact: true}).click();
    await page.getByRole('button', {name: 'Start blank', exact: true}).click();
    await page.getByRole('button', {name: 'Add session', exact: true}).first().click();
    const editor = page.getByRole('dialog', {name: 'Planned workout', exact: true});
    await expect(editor.getByRole('status')).toHaveText('Loading completed workouts…');
    await editor.locator('#workout-editor-note').fill('Keep this draft');
    release();
    await expect(editor.getByRole('alert')).toContainText('Could not load workouts');
    await editor.getByRole('button', {name: 'Retry', exact: true}).click();
    await expect(editor.getByText('No completed workouts yet.', {exact: true})).toBeVisible();
    await expect(editor.locator('#workout-editor-note')).toHaveValue('Keep this draft');
    await expect(editor.getByRole('combobox', {name: 'Use completed workout'})).toBeDisabled();
    await editor.getByRole('button', {name: 'Cancel', exact: true}).click();
    await page.getByRole('region', {name: 'Weekly workout plan'}).getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(page.getByText('No weekly plan yet. Create a plan for your next commitment.')).toBeVisible();
});

async function expectActionLayout(page) {
    await expect.poll(() => page.locator('.p-dialog:visible').evaluateAll(dialogs => dialogs.every(dialog => new DOMMatrix(getComputedStyle(dialog).transform).isIdentity))).toBe(true);
    const failures = await page.locator('.action-group:visible').evaluateAll(groups => groups.flatMap(group => {
        const buttons = [...group.querySelectorAll(':scope > button')].filter(button => button.getClientRects().length);
        const boxes = buttons.map(button => button.getBoundingClientRect());
        const issues = [];
        if (!boxes.length) return issues;
        if (boxes.some(box => Math.abs(box.width - boxes[0].width) > 1 || Math.abs(box.height - boxes[0].height) > 1)) issues.push(`Unequal actions: ${buttons.map(button => button.getAttribute('aria-label') || button.textContent).join(', ')}`);
        for (let index = 1; index < boxes.length; index++) {
            const previous = boxes[index - 1], current = boxes[index];
            if (Math.abs(current.y - previous.y) < 1 && Math.abs(current.x - previous.right - 8) > 1) issues.push(`Inconsistent horizontal action gap in ${group.className}: ${current.x - previous.right}`);
            if (current.y > previous.y + 1 && Math.abs(current.x - boxes[0].x) > 1) issues.push('Wrapped actions are not aligned');
        }
        if (group.closest('.p-datatable') && group.classList.contains('action-group--compact') && boxes.some(box => Math.abs(box.y - boxes[0].y) > 1)) issues.push('Table record actions must share one row');
        const parent = group.getBoundingClientRect();
        if (group.parentElement.classList.contains('p-dialog-footer') && boxes.length === 2 && parent.width >= 200 && boxes[1].y > boxes[0].y + 1) issues.push('Two footer actions should fit side by side');
        if (boxes.some(box => box.left < parent.left - 1 || box.right > parent.right + 1)) issues.push('Actions overflow their group');
        return issues;
    }));
    expect(failures).toEqual([]);
    const unnamed = await page.locator('button.compact-action:visible').evaluateAll(buttons => buttons.filter(button => !button.getAttribute('aria-label')?.trim()).length);
    expect(unnamed).toBe(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}

test('standardized actions keep dashboard and history controls aligned at every breakpoint', async ({page}, testInfo) => {
    const exercises = [{id: 1, name: 'McGill Big Three, Cat-cow, Dead bug, Dumbbell walking lunges', description: 'Controlled movement', trackingMode: 'REPS', exerciseType: 'TRAINING'}];
    const session = workoutResponse(1, {workoutDate: '2026-08-12', startTime: '09:45', durationMinutes: 45, lines: [{exerciseId: 1, segments: [{repetitions: 20, weight: 4}]}]}, exercises);
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialWorkouts: [session], workoutExercises: exercises});
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Workout', exact: true}).click();
    const group = page.getByRole('region', {name: 'Selected day workouts'}).locator('.session-actions');
    await expect(group.locator('button')).toHaveCount(2);
    await expect(page.getByRole('button', {name: 'Rate day', exact: true})).toHaveClass(/compact-action/);
    expect((await group.locator('.p-button-label').allTextContents()).every(label => !label.trim())).toBe(true);
    for (const width of [376, 390, 393, 574, 575, 576, 639, 640, 641, 959, 960, 961, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectActionLayout(page);
        await group.screenshot({path: testInfo.outputPath(`standard-actions-workout-${width}.png`)});
    }
    const edit = group.getByRole('button', {name: 'Edit', exact: true});
    await edit.focus();
    await expect(page.getByRole('tooltip')).toHaveText('Edit');
    await edit.press('Escape');
    await expect(page.getByRole('tooltip')).toHaveCount(0);
    await edit.blur();
    await edit.hover();
    await expect(page.getByRole('tooltip')).toHaveText('Edit');
    await edit.click();
    const dialog = page.getByRole('dialog', {name: 'Workout', exact: true});
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expectActionLayout(page);
        await dialog.screenshot({path: testInfo.outputPath(`standard-actions-workout-editor-${width}.png`)});
    }
    await dialog.getByRole('button', {name: 'Cancel', exact: true}).click();
    for (const route of ['/weights', '/pressures']) {
        await openSpaRoute(page, route);
        await expect(page.locator('tbody .compact-action')).toHaveCount(2);
        for (const width of [376, 390, 1280]) {
            await page.setViewportSize({width, height: 900});
            await expectActionLayout(page);
            await page.screenshot({path: testInfo.outputPath(`standard-actions-${route.slice(1)}-${width}.png`), fullPage: true});
        }
    }
});

test('standardized compact mutations keep their size and accessible name while pending and after errors', async ({page}) => {
    await mockAuthenticatedWorkouts(page, [], [{id: 1, name: 'Squat', description: '', exerciseType: 'TRAINING', trackingMode: 'REPS'}]);
    await openSpaRoute(page, '/workouts');
    await page.getByRole('tab', {name: 'Exercises', exact: true}).click();
    const remove = page.getByRole('button', {name: 'Delete exercise', exact: true});
    const before = await remove.boundingBox();
    let releaseDelete;
    const held = new Promise(resolve => releaseDelete = resolve);
    await page.route('**/api/workout-exercises/1', async route => { await held; await route.fulfill({status: 500, body: 'Exercise is still in use'}); });
    page.on('dialog', dialog => dialog.accept());
    await remove.click();
    await expect(remove).toBeDisabled();
    await expect(remove).toHaveAttribute('aria-busy', 'true');
    const pending = await remove.boundingBox();
    expect(pending.width).toBeCloseTo(before.width, 1);
    expect(pending.height).toBeCloseTo(before.height, 1);
    expect((await remove.textContent()).trim()).toBe('');
    releaseDelete();
    await expect(remove).toBeEnabled();
    await expect(remove).toHaveAttribute('aria-busy', 'false');
    await expect(page.getByText('Exercise is still in use', {exact: true})).toBeVisible();
});

for (const [route, form] of [
    ['/weights', 'Weight'], ['/pressures', 'Blood Pressure'], ['/cholesterol', 'Cholesterol'],
    ['/moods', 'Mood'], ['/sleep', 'Sleep'], ['/sicknesses', 'Sickness'], ['/back', 'Back pain'],
    ['/coach-notes', null], ['/routines', 'Routine'], ['/medications', 'Medication'],
    ['/calories', null], ['/workouts', null], ['/records', null], ['/settings', null],
    ['/plan', null], ['/reflections', null], ['/wins', null], ['/photos', null], ['/agenda', null], ['/meals/new', null]
]) {
    test(`standardized action audit ${route} at mobile and desktop widths`, async ({page}, testInfo) => {
        test.setTimeout(60000);
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        await mockAuthenticatedDashboard(page);
        const responses = {
            '/api/coaching-plan': null,
            '/api/workouts/diary': {items: [], recordEvents: [], page: 0, size: 10, totalElements: 0, totalPages: 0},
            '/api/workout-plans/current': null,
            '/api/push/config': {enabled: false, publicKey: null, timeZone: 'Europe/Madrid'},
            '/api/push/reminder-settings': {morningTime: '07:30:00', middayTime: '13:30:00', eveningTime: '20:30:00', weightTime: '05:00:00', bloodPressureTime: '05:15:00', weightDay: 'SATURDAY', bloodPressureDay: 'SATURDAY', timeZone: 'Europe/Madrid'},
            '/api/push/agenda': {date: '2026-08-12', currentTime: '12:00:00', timeZone: 'Europe/Madrid', entries: []},
            '/api/weekly-summary/config': {enabled: false, canSend: true, recipientEmail: 'jllado@gmail.com', deliveryDay: 'MONDAY', deliveryTime: '08:00:00', timeZone: 'Europe/Madrid'}
        };
        await page.route('**/api/**', api => {
            const path = new URL(api.request().url()).pathname;
            return Object.hasOwn(responses, path) ? api.fulfill({contentType: 'application/json', body: JSON.stringify(responses[path])}) : api.fallback();
        });
        await openSpaRoute(page, route);
        await expect(page.getByRole('button', {name: 'Account', exact: true})).toBeVisible();
        await expect(page.locator('.vld-overlay:visible, .p-datatable-loading-overlay:visible')).toHaveCount(0);
        const capture = async suffix => {
            for (const width of [376, 390, 1280]) {
                await page.setViewportSize({width, height: 900});
                await expectActionLayout(page);
                await page.screenshot({path: testInfo.outputPath(`audit-${suffix}-${width}.png`), fullPage: true});
            }
        };
        await capture('page');
        const tabs = await page.getByRole('tab').allTextContents();
        for (const name of tabs.slice(1)) {
            await page.getByRole('tab', {name: name.trim(), exact: true}).click();
            await expect(page.getByRole('button', {name: 'Account', exact: true})).toBeVisible();
        await expect(page.locator('.vld-overlay:visible, .p-datatable-loading-overlay:visible')).toHaveCount(0);
            await capture(name.trim().replaceAll(' ', '-'));
        }
        if (form) {
            if (tabs.length) await page.getByRole('tab', {name: tabs[0].trim(), exact: true}).click();
            await page.getByRole('button', {name: route === '/back' ? 'Add check-in' : 'New', exact: true}).click();
            await expect(page.getByRole('dialog')).toBeVisible();
            await capture('form');
            await page.getByRole('dialog').getByRole('button', {name: 'Cancel', exact: true}).click();
        }
        expect(errors).toEqual([]);
    });
}

for (const medication of [false, true]) {
    test(`standardized ${medication ? 'medication' : 'routine'} reminder actions align on mobile and desktop`, async ({page}, testInfo) => {
        const date = madridDate();
        await mockRoutineReminderHome(page, [routine(1, 'Morning walk with a comfortable pace and controlled breathing', ['07:30:00'])], {medicationDose: medication ? medicationReminderDose() : null});
        await openSpaRoute(page, medication ? '/?medicationDoseId=50' : `/?routineReminderId=1&routineReminderDate=${date}&routineReminderScheduleId=10`);
        const dialog = page.getByRole('dialog', {name: medication ? 'Medication reminder' : 'Routine reminder', exact: true});
        await expect(dialog).toBeVisible();
        for (const width of [376, 390, 575, 640, 960, 1280]) {
            await page.setViewportSize({width, height: 900});
            await expectActionLayout(page);
            await dialog.screenshot({path: testInfo.outputPath(`reminder-${width}.png`)});
        }
    });
}

test('workout diary keeps complete training days together with one rating action', async ({page, context}, testInfo) => {
    const exercises = [{id: 1, name: 'Dumbbell walking lunges with a controlled comfortable range', description: 'Walk with control.', trackingMode: 'REPS', exerciseType: 'TRAINING'}];
    const session = (id, date) => workoutResponse(id, {workoutDate: date, lines: [{exerciseId: 1, segments: [{repetitions: 20, weight: 4}]}]}, exercises);
    const workouts = [...Array.from({length: 12}, (_, index) => session(index + 1, '2026-08-20')), ...Array.from({length: 10}, (_, index) => session(index + 20, `2026-08-${String(19 - index).padStart(2, '0')}`))];
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await context.route(coachOriginPattern, route => route.fulfill({body: '<title>Coach</title>'}));
    await mockAuthenticatedWorkouts(page, workouts, exercises);
    await openSpaRoute(page, '/workouts');
    const desktop = page.locator('.diary-desktop');
    const mobile = page.locator('.diary-mobile');
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        const visibleDiary = width <= 575 ? mobile : desktop;
        await expect(visibleDiary.getByRole('button', {name: 'Rate day', exact: true})).toHaveCount(10);
        await expectChatGptIcon(visibleDiary.getByRole('button', {name: 'Rate day', exact: true}).first());
        if (width <= 575) await expect(mobile.locator('.mobile-diary-day').first().locator('.mobile-diary-workout')).toHaveCount(12);
        else {
            await expect(desktop.locator('tbody tr').first().locator('.diary-day-session')).toHaveCount(12);
            await expect(desktop.getByRole('button', {name: 'Rate day', exact: true}).first()).toBeInViewport();
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`daily-workouts-${width}.png`)});
    }
    const popup = context.waitForEvent('page');
    await desktop.getByRole('button', {name: 'Rate day', exact: true}).first().click();
    const coach = await popup;
    await expect.poll(() => page.evaluate(() => navigator.clipboard.readText())).toBe('Assess all my workout sessions on 2026-08-20 together as one training day against my active coaching plan.');
    await coach.close();
    await page.setViewportSize({width: 390, height: 900});
    await mobile.getByRole('button', {name: 'Next', exact: true}).click();
    await expect(mobile.locator('.mobile-diary-day')).toHaveCount(1);
    await expect(mobile.locator('.mobile-diary-day')).toContainText('10/08/2026');
});

for (const width of [390, 1280]) {
    test(`workout loading renders the plan before the catalog and retains tabs at ${width}px`, async ({page}, testInfo) => {
        await mockWeeklyPlans(page);
        await page.setViewportSize({width, height: 900});
        const cdp = await page.context().newCDPSession(page);
        await cdp.send('Emulation.setCPUThrottlingRate', {rate: 4});
        await cdp.send('Network.enable');
        await cdp.send('Network.emulateNetworkConditions', {offline: false, latency: 150, downloadThroughput: -1, uploadThroughput: -1});
        const counts = {diary: 0, catalog: 0, plan: 0, stretching: 0};
        page.on('request', request => {
            const path = new URL(request.url()).pathname;
            if (path === '/api/workouts/diary') counts.diary++;
            if (path === '/api/workout-exercises') counts.catalog++;
            if (path === '/api/workout-plans/current') counts.plan++;
            if (path === '/api/stretching-sets') counts.stretching++;
        });
        let releaseCatalog;
        const catalogPending = new Promise(resolve => { releaseCatalog = resolve; });
        await page.route('**/api/workout-exercises', async route => { await catalogPending; await route.fallback(); });
        const started = Date.now();
        await openSpaRoute(page, '/workouts?tab=plan');
        const section = page.getByRole('region', {name: 'Weekly workout plan'});
        try {
            await expect(section).toContainText('No weekly plan yet.');
            await expect(section.getByText('Loading workout plan…')).toHaveCount(0);
            expect(counts).toEqual({diary: 0, catalog: 1, plan: 1, stretching: 0});
            await testInfo.attach('plan-loading-timing', {body: JSON.stringify({width, millisecondsToPlan: Date.now() - started, catalogStillPending: true}), contentType: 'application/json'});
            await section.getByRole('button', {name: 'New plan', exact: true}).click();
            await page.getByRole('dialog', {name: 'New weekly plan'}).getByRole('button', {name: 'Start blank'}).click();
            await section.getByLabel('Notes (optional)', {exact: true}).fill('Keep my draft when switching tabs');
            await page.getByRole('tab', {name: 'Diary', exact: true}).click();
            await expect(page.getByText('Loading workouts…')).toHaveCount(0);
            await expect.poll(() => counts.diary).toBe(1);
            await page.getByRole('tab', {name: 'Plan', exact: true}).click();
            await expect(section.getByLabel('Notes (optional)', {exact: true})).toHaveValue('Keep my draft when switching tabs');
            expect(counts.plan).toBe(1);
        } finally { releaseCatalog(); }
        await expect.poll(() => counts.catalog).toBe(1);
        await page.screenshot({path: testInfo.outputPath(`workout-loading-plan-${width}.png`), fullPage: true});
        await page.getByRole('tab', {name: 'Diary', exact: true}).click();
        await expect(page.locator(width <= 575 ? '.diary-mobile' : '.diary-desktop')).toBeVisible();
        expect(counts.diary).toBe(1);
        expect(counts.stretching).toBe(0);
    });
}

test('workout loading retries failed diary data and renders only the active responsive layout', async ({page}, testInfo) => {
    const exercise = {id: 1, name: 'Push-up', description: 'Controlled repetitions', trackingMode: 'REPS', exerciseType: 'TRAINING'};
    const workouts = Array.from({length: 11}, (_, index) => workoutResponse(index + 1, {workoutDate: `2026-08-${20 - index}`, note: `Session ${index + 1}`, lines: [{exerciseId: 1, segments: [{repetitions: 10, weight: 0}]}]}, [exercise]));
    await mockAuthenticatedWorkouts(page, workouts, [exercise]);
    await page.setViewportSize({width: 390, height: 900});
    let diaryRequests = 0;
    await page.route('**/api/workouts/diary?*', route => {
        diaryRequests++;
        return diaryRequests === 1 ? route.fulfill({status: 503, body: 'Workout data temporarily unavailable'}) : route.fallback();
    });
    await openSpaRoute(page, '/workouts');
    await expect(page.getByRole('alert')).toContainText('Workout data temporarily unavailable');
    await expect(page.getByText('Loading workouts…')).toHaveCount(0);
    await expect(page.getByText('No workouts recorded.')).toHaveCount(0);
    await page.getByRole('button', {name: 'Retry workouts'}).click();
    await expect(page.locator('.mobile-diary-workout')).toHaveCount(10);
    await expect(page.locator('.diary-desktop')).toHaveCount(0);
    await page.locator('.mobile-diary-summary').first().click();
    await expect(page.locator('.mobile-diary-details')).toContainText('10 reps');
    await page.screenshot({path: testInfo.outputPath('workout-loading-diary-390.png'), fullPage: true});
    await page.getByRole('tabpanel', {name: 'Diary', exact: true}).getByRole('button', {name: 'Next', exact: true}).click();
    await expect(page.locator('.mobile-diary-workout')).toHaveCount(1);
    for (const width of [575, 576, 640, 960, 1280, 390]) {
        await page.setViewportSize({width, height: 900});
        await expect(page.locator(width <= 575 ? '.diary-mobile' : '.diary-desktop')).toBeVisible();
        await expect(page.locator(width <= 575 ? '.diary-desktop' : '.diary-mobile')).toHaveCount(0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        if (width === 1280) await page.screenshot({path: testInfo.outputPath('workout-loading-diary-1280.png'), fullPage: true});
    }
    expect(diaryRequests).toBe(3);
});

async function offerAppInstall(page, outcome = 'dismissed') {
    await page.evaluate(outcome => {
        window.installPromptCalls = 0;
        const event = new Event('beforeinstallprompt', {cancelable: true});
        event.prompt = async () => { window.installPromptCalls++; };
        event.userChoice = Promise.resolve({outcome});
        window.dispatchEvent(event);
    }, outcome);
}

test('install prompt dismissal persists in this browser and Account can still install', async ({page, browser}, testInfo) => {
    await mockAuthenticatedRoutines(page, []);
    await openSpaRoute(page, '/routines');
    await expect(page.getByRole('button', {name: 'Account', exact: true})).toBeVisible();
    await offerAppInstall(page);
    const notices = page.locator('.app-action-notices');
    const install = notices.getByRole('button', {name: 'Install app', exact: true});
    const dismiss = notices.getByRole('button', {name: 'Dismiss install prompt'});
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 900});
        await expect(install).toBeVisible();
        const installBox = await install.boundingBox();
        const dismissBox = await dismiss.boundingBox();
        expect(dismissBox.y).toBeCloseTo(installBox.y, 0);
        expect(dismissBox.width).toBeCloseTo(installBox.width, 0);
        expect(dismissBox.x - installBox.x - installBox.width).toBeCloseTo(8, 0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await notices.screenshot({path: testInfo.outputPath(`install-prompt-${width}.png`)});
    }
    await install.focus();
    await page.keyboard.press('Tab');
    await expect(dismiss).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(notices).toBeHidden();
    await offerAppInstall(page);
    await expect(notices).toBeHidden();
    // The static test server serves the SPA at /; the init script restores /routines.
    await page.evaluate(() => window.history.replaceState({}, '', '/'));
    await page.reload();
    await expect(page.getByRole('button', {name: 'Account', exact: true})).toBeVisible();
    await offerAppInstall(page);
    await expect(notices).toBeHidden();
    await page.getByRole('button', {name: 'Account', exact: true}).click();
    await page.getByRole('menuitem', {name: 'Install app', exact: true}).click();
    expect(await page.evaluate(() => window.installPromptCalls)).toBe(1);
    await expect(notices).toBeHidden();
    await page.getByRole('button', {name: 'Account', exact: true}).click();
    await expect(page.getByRole('menuitem', {name: 'Install app', exact: true})).toHaveCount(0);

    const freshContext = await browser.newContext({serviceWorkers: 'block'});
    try {
        const freshPage = await freshContext.newPage();
        await mockAuthenticatedRoutines(freshPage, []);
        await openSpaRoute(freshPage, '/routines');
        await expect(freshPage.getByRole('button', {name: 'Account', exact: true})).toBeVisible();
        await offerAppInstall(freshPage);
        await expect(freshPage.getByRole('button', {name: 'Dismiss install prompt'})).toBeVisible();
    } finally {
        await freshContext.close();
    }
});

test('install prompt preserves updates and hides installation controls when unavailable or installed', async ({page}) => {
    await mockAuthenticatedRoutines(page, []);
    await openSpaRoute(page, '/routines');
    const account = page.getByRole('button', {name: 'Account', exact: true});
    await account.click();
    await expect(page.getByRole('menuitem', {name: 'Install app', exact: true})).toHaveCount(0);
    await page.keyboard.press('Escape');
    await offerAppInstall(page, 'accepted');
    await page.evaluate(() => {
        // Inject the worker's waiting state; exercise the real update and dismissal controls.
        const state = document.querySelector('#app').__vue_app__._container._vnode.component.proxy.state;
        window.workerMessages = [];
        state.updateRegistration = {waiting: {postMessage: message => window.workerMessages.push(message)}};
        state.updateAvailable = true;
    });
    await page.getByRole('button', {name: 'Dismiss install prompt'}).click();
    const update = page.getByRole('button', {name: 'Update app', exact: true});
    await expect(update).toBeVisible();
    await update.click();
    expect(await page.evaluate(() => window.workerMessages)).toEqual([{type: 'SKIP_WAITING'}]);
    await expect(page.getByRole('button', {name: 'Updating...'})).toBeDisabled();
    await account.click();
    await page.getByRole('menuitem', {name: 'Install app', exact: true}).click();
    expect(await page.evaluate(() => window.installPromptCalls)).toBe(1);
    await page.evaluate(() => window.dispatchEvent(new Event('appinstalled')));
    await account.click();
    await expect(page.getByRole('menuitem', {name: 'Install app', exact: true})).toHaveCount(0);
    await expect(page.getByRole('button', {name: 'Install app', exact: true})).toHaveCount(0);
});

async function expectWorkoutErrorNavigation(editor, input, message) {
    await expect(input).toBeFocused();
    await expect.poll(() => editor.evaluate((dialog, text) => {
        const error = [...dialog.querySelectorAll('[data-workout-error]')].find(element => element.textContent === text);
        const content = dialog.querySelector('.p-dialog-content').getBoundingClientRect();
        const bounds = error.getBoundingClientRect();
        return bounds.top >= content.top && bounds.bottom <= content.bottom;
    }, message)).toBe(true);
    await expect(editor.getByText(message, {exact: true})).toBeVisible();
}

for (const width of [390, 1280]) {
    test(`workout validation explains empty workouts, missing exercises and sets at ${width}px`, async ({page}) => {
        await mockAuthenticatedWorkouts(page, [], [{id: 1, name: 'Squat', description: 'Strength.', trackingMode: 'REPS', exerciseType: 'TRAINING'}]);
        await page.setViewportSize({width, height: 894});
        const writes = [];
        page.on('request', request => { if (request.method() === 'POST' && request.url().endsWith('/api/workouts')) writes.push(request); });
        await openSpaRoute(page, '/workouts');
        await page.getByRole('button', {name: 'New', exact: true}).click();
        const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
        await editor.getByRole('button', {name: 'Delete exercise 1', exact: true}).click();
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.getByRole('button', {name: 'Add exercise', exact: true}), 'Add at least one exercise');
        await expect(page.locator('.p-toast')).toContainText('Workout not saved');
        await editor.getByRole('button', {name: 'Add exercise', exact: true}).click();
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.getByRole('combobox', {name: 'Exercise', exact: true}), 'Exercise is required');
        await expect(editor.getByRole('combobox', {name: 'Exercise', exact: true})).toHaveAttribute('aria-invalid', 'true');
        await editor.getByRole('combobox', {name: 'Exercise', exact: true}).click();
        await page.getByRole('option', {name: 'Squat', exact: true}).click();
        await editor.getByRole('button', {name: 'Delete set 1', exact: true}).click();
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.getByText('Add at least one set', {exact: true}), 'Add at least one set');
        expect(writes).toHaveLength(0);
    });

    test(`workout validation focuses the first invalid sauna round and explains missing rounds at ${width}px`, async ({page}, testInfo) => {
        await mockAuthenticatedWorkouts(page, [], []);
        await page.setViewportSize({width, height: 894});
        await openSpaRoute(page, '/workouts');
        await page.getByRole('button', {name: 'New', exact: true}).click();
        const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
        await editor.locator('label[for="workout-sauna-session"]').click();
        await editor.getByLabel('Round 1 (min)', {exact: true}).fill('10');
        await editor.getByLabel('Round 1 (min)', {exact: true}).press('Tab');
        await editor.getByRole('button', {name: 'Add round', exact: true}).click();
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        const error = 'Enter positive whole minutes for this sauna round';
        const round = editor.getByLabel('Round 2 (min)', {exact: true});
        await expectWorkoutErrorNavigation(editor, round, error);
        await expect(round).toHaveAttribute('aria-invalid', 'true');
        await expect(round).toHaveAttribute('aria-describedby', 'workout-sauna-error');
        await page.screenshot({path: testInfo.outputPath(`workout-validation-sauna-round-${width}.png`)});
        await expect(editor.getByLabel('Round 1 (min)', {exact: true})).toHaveAttribute('aria-invalid', 'false');
        await round.fill('1');
        await editor.getByLabel('Round 1 (min)', {exact: true}).fill('2147483647');
        await editor.getByLabel('Round 1 (min)', {exact: true}).press('Tab');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.getByLabel('Round 1 (min)', {exact: true}), 'Total sauna time is too long; reduce the round minutes');
        await editor.getByRole('button', {name: 'Remove sauna round 2', exact: true}).click();
        await editor.getByRole('button', {name: 'Remove sauna round 1', exact: true}).click();
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.getByText('Add at least one sauna round', {exact: true}), 'Add at least one sauna round');
        await editor.getByRole('button', {name: 'Add round', exact: true}).click();
        await editor.getByLabel('Round 1 (min)', {exact: true}).fill('10');
        await editor.getByLabel('Round 1 (min)', {exact: true}).press('Tab');
        const save = page.waitForRequest(request => request.url().endsWith('/api/workouts') && request.method() === 'POST');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await save).postDataJSON()).toMatchObject({saunaSession: true, saunaRoundsMinutes: [10], lines: []});
        await expect(editor).toBeHidden();
    });

    test(`workout validation expands nested categories and reaches sets, intervals and stretching at ${width}px`, async ({page}, testInfo) => {
        const exercises = [
            {id: 1, name: 'Squat', description: 'Strength.', trackingMode: 'REPS', exerciseType: 'TRAINING'},
            {id: 2, name: 'Treadmill run', description: 'Cardio.', trackingMode: 'CARDIO', exerciseType: 'TRAINING'},
            {id: 3, name: 'Calf stretch', description: 'Stretch.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}
        ];
        const previous = workoutResponse(1, {workoutDate: '2026-08-20', note: 'Keep this draft', lines: [
            {exerciseId: 1, segments: [{repetitions: null}]},
            {exerciseId: 2, segments: [{durationSeconds: 0}]},
            {exerciseId: 3, stretchingUnit: 'BREATHS', segments: [{breaths: null}]}
        ]}, exercises);
        await mockAuthenticatedWorkouts(page, [previous], exercises);
        await openSpaRoute(page, '/workouts');
        await page.locator('.diary-desktop').getByRole('button', {name: 'Edit workout', exact: true}).click();
        await page.setViewportSize({width, height: 894});
        const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
        await editor.getByRole('button', {name: /^Collapse Training,/}).click();
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.getByLabel('Repetitions', {exact: true}), 'Repetitions are required');
        for (const category of ['Training', 'Strength', 'Cardio', 'Stretching']) await expect(editor.getByRole('button', {name: new RegExp(`^Collapse ${category},`)})).toHaveAttribute('aria-expanded', 'true');
        await page.screenshot({path: testInfo.outputPath(`workout-validation-set-${width}.png`)});
        await editor.getByLabel('Repetitions', {exact: true}).fill('10');
        await editor.getByLabel('Repetitions', {exact: true}).press('Tab');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.getByLabel('Minutes', {exact: true}), 'Duration is required');
        await editor.getByLabel('Minutes', {exact: true}).fill('15');
        await editor.getByLabel('Minutes', {exact: true}).press('Tab');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.getByLabel('Breaths', {exact: true}), 'Enter a positive breath count');
        await expect(editor.locator('#workout-editor-note')).toHaveValue('Keep this draft');
        await page.screenshot({path: testInfo.outputPath(`workout-validation-stretch-${width}.png`)});
        await editor.getByLabel('Breaths', {exact: true}).fill('5');
        await editor.getByLabel('Breaths', {exact: true}).press('Tab');
        const save = page.waitForRequest(request => /\/api\/workouts\/1$/.test(request.url()) && request.method() === 'PUT');
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        expect((await save).postDataJSON()).toMatchObject({note: 'Keep this draft'});
        await expect(editor).toBeHidden();
    });
}

test('workout validation reveals the first duration error in a long sauna workout and retains a failed save', async ({page}, testInfo) => {
    const exercises = Array.from({length: 9}, (_, index) => ({id: index + 1, name: `Controlled stretch ${index + 1}`, description: 'Hold steadily with comfortable breathing.', trackingMode: 'SECONDS', exerciseType: 'STRETCHING'}));
    const previous = workoutResponse(1, {workoutDate: '2026-08-20', durationMinutes: 20, saunaSession: true, saunaRoundsMinutes: [15, 10], note: 'Keep every entered value', lines: exercises.map(exercise => ({exerciseId: exercise.id, segments: [{durationSeconds: 30}]}))}, exercises);
    await mockAuthenticatedWorkouts(page, [previous], exercises);
    await openSpaRoute(page, '/workouts');
    await page.locator('.diary-desktop').getByRole('button', {name: 'Edit workout', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    await editor.getByRole('button', {name: /^Expand Stretching,/}).click();
    for (const toggle of await editor.locator('.workout-line-toggle').all()) await toggle.click();
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 894});
        await editor.locator('.p-dialog-content').evaluate(element => { element.scrollTop = element.scrollHeight; });
        await editor.getByRole('button', {name: 'Save', exact: true}).click();
        await expectWorkoutErrorNavigation(editor, editor.locator('#workout-duration'), 'Duration cannot be shorter than sauna rounds');
        await expect(page.locator('.p-toast')).toContainText('Workout not saved');
        await expect(editor.locator('#workout-duration')).toHaveAttribute('aria-describedby', 'workout-duration-error');
        expect(await editor.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`workout-validation-duration-${width}.png`)});
        for (const close of await page.locator('.p-toast-icon-close').all()) await close.click();
        await expect.poll(() => page.locator('.p-toast').evaluate(element => element.style.zIndex)).toBe('');
    }
    await editor.locator('#workout-duration').fill('30');
    await editor.locator('#workout-duration').press('Tab');
    await page.route('**/api/workouts/1', route => route.fulfill({status: 500, json: {message: 'Could not save workout. Try again.'}}), {times: 1});
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page.locator('.p-toast')).toContainText('Workout not saved');
    await expect(page.locator('.p-toast')).toContainText('Could not save workout. Try again.');
    await expect(editor.locator('#workout-duration')).toHaveValue('30');
    await expect(editor.locator('#workout-editor-note')).toHaveValue('Keep every entered value');
    await expect(editor.locator('.workout-line-card')).toHaveCount(9);
    await expect(editor.getByRole('button', {name: 'Save', exact: true})).toBeEnabled();
    const retry = page.waitForRequest(request => /\/api\/workouts\/1$/.test(request.url()) && request.method() === 'PUT');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    expect((await retry).postDataJSON()).toMatchObject({durationMinutes: 30, saunaRoundsMinutes: [15, 10], note: 'Keep every entered value'});
    await expect(editor).toBeHidden();
});

test('workout validation focuses editable duration phases and gives guided start feedback', async ({page}) => {
    await mockAuthenticatedWorkouts(page, [], [{id: 1, name: 'Squat', description: 'Strength.', trackingMode: 'REPS', exerciseType: 'TRAINING'}]);
    await page.setViewportSize({width: 390, height: 894});
    await page.emulateMedia({reducedMotion: 'reduce'});
    await openSpaRoute(page, '/workouts');
    await page.getByRole('button', {name: 'New', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Workout', exact: true});
    await editor.getByText('Break down duration', {exact: true}).click();
    await editor.getByRole('button', {name: 'Start guided workout', exact: true}).click();
    await expectWorkoutErrorNavigation(editor, editor.getByLabel('Warm-up (min)', {exact: true}), 'Duration must be a positive whole number of minutes');
    await expect(editor.getByLabel('Warm-up (min)', {exact: true})).toHaveAttribute('aria-describedby', 'workout-duration-error');
    await expect(editor.locator('#workout-duration')).toHaveAttribute('readonly');
    await expect(page.locator('.p-toast')).toContainText('Workout not started');
    await editor.getByLabel('Warm-up (min)', {exact: true}).fill('5');
    await editor.getByLabel('Cardio (min)', {exact: true}).fill('');
    await editor.getByLabel('Cardio (min)', {exact: true}).press('Tab');
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expectWorkoutErrorNavigation(editor, editor.getByLabel('Cardio (min)', {exact: true}), 'Enter all four duration values, using zero for phases you skipped');
});

async function openTrainingBalanceRoute(page, routePath) {
    await page.route(url => url.pathname === '/workouts', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
        : route.continue());
    await page.goto(routePath);
}

const balanceGroups = ['CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'FOREARMS', 'CORE', 'GLUTES', 'QUADRICEPS', 'HAMSTRINGS', 'CALVES'];
function balanceResponse(date, counts = {}) {
    const dayjs = require('dayjs');
    const start = dayjs(date).subtract((dayjs(date).day() + 1) % 7, 'day');
    const groups = balanceGroups.map(muscleGroup => ({muscleGroup, sets: counts[muscleGroup] || 0}));
    return {weekStart: start.format('YYYY-MM-DD'), weekEnd: start.add(6, 'day').format('YYYY-MM-DD'), totalSets: groups.reduce((sum, group) => sum + group.sets, 0), groups};
}

for (const width of [376, 390, 575, 640, 960, 1280]) {
    test(`training balance navigation, exact counts and accessible rows at ${width}px`, async ({page}, testInfo) => {
        await mockAuthenticatedWorkouts(page, [], []);
        await page.setViewportSize({width, height: 1000});
        const requests = [];
        await page.route('**/api/workouts/training-balance?*', route => {
            const date = new URL(route.request().url()).searchParams.get('date'); requests.push(date);
            return route.fulfill({json: balanceResponse(date, {CHEST: 12, CORE: 3})});
        });
        await openTrainingBalanceRoute(page, '/workouts?tab=training-balance&date=2026-10-04');
        const tab = page.getByRole('tab', {name: 'Training balance', exact: true});
        await expect(tab).toHaveAttribute('aria-selected', 'true');
        await expect(tab.locator('.p-tabview-title')).toBeInViewport({ratio: 1});
        await expect(page.getByText('03/10/2026 – 09/10/2026')).toBeVisible();
        await expect(page.getByText('15 total sets', {exact: true})).toBeVisible();
        const rows = page.getByRole('list', {name: 'Weekly sets by muscle group'}).getByRole('listitem');
        await expect(rows).toHaveCount(11);
        await expect(rows.nth(0)).toHaveText('Chest12 sets');
        await expect(rows.nth(5)).toHaveText('Forearms0 sets');
        await expect(rows.nth(6)).toHaveText('Core3 sets');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({animations: 'disabled', path: testInfo.outputPath(`training-balance-${width}.png`)});
        await page.getByRole('button', {name: 'Previous week', exact: true}).click();
        await expect(page).toHaveURL(/date=2026-09-27/);
        await expect(page.getByText('26/09/2026 – 02/10/2026')).toBeVisible();
        await page.getByRole('button', {name: 'Next week', exact: true}).click();
        await expect(page).toHaveURL(/date=2026-10-04/);
        await page.getByLabel('Date in week').fill('01/01/2026');
        await page.getByLabel('Date in week').press('Tab');
        await expect(page).toHaveURL(/date=2026-01-01/);
        await expect(page.getByText('27/12/2025 – 02/01/2026')).toBeVisible();
        await page.reload();
        await expect(page.getByLabel('Date in week')).toHaveValue('01/01/2026');
        await expect(page.getByText('27/12/2025 – 02/01/2026')).toBeVisible();
        await page.getByRole('button', {name: 'Previous week', exact: true}).focus();
        await expect(page.getByRole('button', {name: 'Previous week', exact: true})).toBeFocused();
        await page.getByRole('button', {name: 'This week', exact: true}).focus();
        await page.keyboard.press('Enter');
        const today = await page.evaluate(() => { const parts = Object.fromEntries(new Intl.DateTimeFormat('en', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'}).formatToParts(new Date()).map(part => [part.type, part.value])); return `${parts.year}-${parts.month}-${parts.day}`; });
        await expect(page.getByLabel('Date in week')).toHaveValue(require('dayjs')(today).format('DD/MM/YYYY'));
        await page.goBack();
        await expect(page.getByLabel('Date in week')).toHaveValue('01/01/2026');
        await page.goForward();
        await expect(page.getByLabel('Date in week')).toHaveValue(require('dayjs')(today).format('DD/MM/YYYY'));
        expect(requests).toContain('2026-01-01');
    });
}

test('training balance stays in the current week when opened or selected with a future week', async ({page}) => {
    await mockAuthenticatedWorkouts(page, [], []);
    await page.route('**/api/workouts/training-balance?*', route => route.fulfill({json: balanceResponse(new URL(route.request().url()).searchParams.get('date'))}));
    const today = await page.evaluate(() => {
        const parts = Object.fromEntries(new Intl.DateTimeFormat('en', {timeZone: 'Europe/Madrid', year: 'numeric', month: '2-digit', day: '2-digit'}).formatToParts(new Date()).map(part => [part.type, part.value]));
        return `${parts.year}-${parts.month}-${parts.day}`;
    });
    const futureDate = require('dayjs')(today).add(7, 'day').format('YYYY-MM-DD');
    await openTrainingBalanceRoute(page, `/workouts?tab=training-balance&date=${futureDate}`);
    await expect(page).toHaveURL(new RegExp(`date=${today}`));
    await expect(page.getByLabel('Date in week')).toHaveValue(require('dayjs')(today).format('DD/MM/YYYY'));
    await expect(page.getByRole('button', {name: 'Next week', exact: true})).toBeDisabled();

    await page.getByRole('button', {name: 'Previous week', exact: true}).click();
    await expect(page).toHaveURL(new RegExp(`date=${require('dayjs')(today).subtract(7, 'day').format('YYYY-MM-DD')}`));
    await page.getByRole('button', {name: 'Next week', exact: true}).click();
    await expect(page).toHaveURL(new RegExp(`date=${today}`));
    await expect(page.getByRole('button', {name: 'Next week', exact: true})).toBeDisabled();

    await page.getByLabel('Date in week').fill(require('dayjs')(futureDate).format('DD/MM/YYYY'));
    await page.getByLabel('Date in week').press('Tab');
    await expect(page).toHaveURL(new RegExp(`date=${today}`));
    await expect(page.getByText(new RegExp(`${require('dayjs')(today).subtract((require('dayjs')(today).day() + 1) % 7, 'day').format('DD/MM/YYYY')}`))).toBeVisible();
});

test('training balance opens the selected dashboard date and persists on refresh', async ({page}, testInfo) => {
    await mockAuthenticatedDashboard(page, '2026-08-12');
    await page.route('**/api/workouts/training-balance?*', route => route.fulfill({json: balanceResponse(new URL(route.request().url()).searchParams.get('date'))}));
    await openTrainingBalanceRoute(page, '/');
    await page.getByRole('tab', {name: /^Workout/}).click();
    const action = page.getByRole('button', {name: 'Training balance', exact: true});
    await expect(action).toBeVisible();
    await expect(page.locator('.workout-status-details').getByRole('button', {name: 'Training balance', exact: true})).toHaveCount(0);
    for (const width of [376, 390, 1280]) {
        await page.setViewportSize({width, height: 1000});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        if (width <= 575) {
            const heading = await page.locator('.workout-panel-header > strong').boundingBox();
            const add = await page.getByRole('button', {name: 'Add session', exact: true}).boundingBox();
            const balance = await action.boundingBox();
            expect(heading.y + heading.height).toBeLessThanOrEqual(add.y);
            expect(add.y + add.height).toBeLessThanOrEqual(balance.y);
            expect(add.width).toBeCloseTo(balance.width, 1);
        }
        await page.locator('.p-panel').filter({has: action}).screenshot({animations: 'disabled', path: testInfo.outputPath(`training-balance-dashboard-${width}.png`)});
    }
    await action.click();
    await expect(page).toHaveURL(/\/workouts\?tab=training-balance&date=2026-08-12/);
    await expect(page.getByText('08/08/2026 – 14/08/2026')).toBeVisible();
    await expect(page.getByText('No strength sets recorded this week.')).toBeVisible();
    await page.reload();
    await expect(page.getByRole('tab', {name: 'Training balance', exact: true})).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('08/08/2026 – 14/08/2026')).toBeVisible();
});

test('training balance defaults to Madrid week and retries errors without losing the date', async ({page}) => {
    await mockAuthenticatedWorkouts(page, [], []);
    let fail = true, requests = 0;
    await page.route('**/api/workouts/training-balance?*', route => { requests++; return fail ? route.fulfill({status: 503, body: 'Balance temporarily unavailable'}) : route.fulfill({json: balanceResponse(new URL(route.request().url()).searchParams.get('date'))}); });
    await openTrainingBalanceRoute(page, '/workouts?tab=training-balance');
    await expect(page).toHaveURL(/date=\d{4}-\d{2}-\d{2}/);
    const date = await page.getByLabel('Date in week').inputValue();
    await expect(page.getByRole('alert')).toContainText('Balance temporarily unavailable');
    expect(requests).toBe(1);
    fail = false;
    await page.getByRole('button', {name: 'Retry', exact: true}).click();
    await expect(page.getByText('No strength sets recorded this week.')).toBeVisible();
    await expect(page.getByLabel('Date in week')).toHaveValue(date);
    expect(requests).toBe(2);
    await expect(page.getByRole('list', {name: 'Weekly sets by muscle group'}).getByRole('listitem')).toHaveCount(11);
});

test('training balance edits classification and refreshes historical counts after saved workout changes', async ({page}) => {
    const exercise = {id: 1, name: 'Balance press with a long readable exercise name', description: 'Press horizontally.', exerciseType: 'TRAINING', trackingMode: 'REPS', primaryMuscleGroup: 'CHEST'};
    const exercises = [exercise];
    const recorded = workoutResponse(1, {workoutDate: '2026-10-04', lines: [{exerciseId: 1, segments: [{repetitions: 10, weight: 0}]}]}, exercises);
    await mockAuthenticatedWorkouts(page, [recorded], exercises);
    let savedSets = 3;
    await page.route('**/api/workout-exercises/1', route => { Object.assign(exercise, route.request().postDataJSON()); return route.fulfill({json: exercise}); });
    await page.route('**/api/workouts/training-balance?*', route => route.fulfill({json: balanceResponse(new URL(route.request().url()).searchParams.get('date'), {[exercise.primaryMuscleGroup]: savedSets})}));
    await openTrainingBalanceRoute(page, '/workouts?tab=training-balance&date=2026-10-04');
    await expect(page.locator('.balance-row').first()).toHaveText('Chest3 sets');
    await page.getByRole('tab', {name: 'Exercises', exact: true}).click();
    await page.getByRole('button', {name: 'Edit exercise', exact: true}).click();
    const editor = page.getByRole('dialog', {name: 'Exercise', exact: true});
    await editor.getByLabel('Primary muscle group', {exact: true}).click();
    await page.getByRole('option', {name: 'Triceps', exact: true}).click();
    await editor.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(editor).toBeHidden();
    await expect(page.getByRole('tabpanel').getByText('Triceps', {exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'Training balance', exact: true}).click();
    await expect(page.locator('.balance-row').first()).toHaveText('Chest0 sets');
    await expect(page.locator('.balance-row').nth(4)).toHaveText('Triceps3 sets');
    await page.reload();
    await expect(page.locator('.balance-row').nth(4)).toHaveText('Triceps3 sets');
    savedSets = 4;
    await page.evaluate(() => window.dispatchEvent(new Event('timed-workout-saved')));
    await expect(page.getByText('4 total sets', {exact: true})).toBeVisible();
    await page.getByRole('tab', {name: 'Diary', exact: true}).click();
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('button', {name: 'Delete workout', exact: true}).click();
    savedSets = 0;
    await page.getByRole('tab', {name: 'Training balance', exact: true}).click();
    await expect(page.getByText('No strength sets recorded this week.')).toBeVisible();
});

test('training balance ignores stale requests during rapid week navigation', async ({page}) => {
    await mockAuthenticatedWorkouts(page, [], []);
    let finishOld;
    const delayed = new Promise(resolve => { finishOld = resolve; });
    await page.route('**/api/workouts/training-balance?*', async route => {
        const date = new URL(route.request().url()).searchParams.get('date');
        if (date === '2026-09-27') await delayed;
        await route.fulfill({json: balanceResponse(date, {CHEST: date === '2026-09-27' ? 99 : 3})});
    });
    await openTrainingBalanceRoute(page, '/workouts?tab=training-balance&date=2026-10-04');
    await expect(page.getByText('3 total sets', {exact: true})).toBeVisible();
    const previousRequest = page.waitForRequest('**/api/workouts/training-balance?date=2026-09-27');
    await page.getByRole('button', {name: 'Previous week', exact: true}).click();
    await previousRequest;
    await expect(page.locator('.training-balance').getByRole('status')).toHaveText('Loading training balance…');
    await page.getByRole('button', {name: 'Next week', exact: true}).click();
    await expect(page.getByText('03/10/2026 – 09/10/2026')).toBeVisible();
    const oldResponse = page.waitForResponse('**/api/workouts/training-balance?date=2026-09-27');
    finishOld();
    await (await oldResponse).finished();
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    await expect(page.getByText('3 total sets', {exact: true})).toBeVisible();
    await expect(page.getByText('99 total sets', {exact: true})).toHaveCount(0);
});

test('nutrient totals show estimates and incomplete coverage at mobile and desktop widths', async ({page}, testInfo) => {
    const food = {name: 'Fish', quantity: 100, unit: 'GRAM', calories: 200, proteinGrams: 20, carbohydrateGrams: 0, fatGrams: 10,
        vitaminDMicrograms: 2.5, omega3Milligrams: 1200, magnesiumMilligrams: 30, nutrientSource: 'Test composition', nutrientsEstimated: true};
    await mockAuthenticatedDashboard(page, '2026-08-12', {initialMeals: [
        {id: 1, date: '2026-08-12', mealType: 'LUNCH', mealSequence: 1, calories: 200, proteinGrams: 20, carbohydrateGrams: 0, fatGrams: 10, dishes: [food]},
        {id: 2, date: '2026-08-12', mealType: 'SNACK', mealSequence: 1, calories: 10, proteinGrams: null, carbohydrateGrams: null, fatGrams: null, dishes: []}
    ]});
    await openSpaRoute(page, '/calories');
    const panel = page.getByRole('region', {name: 'Food nutrients'});
    await expect(panel).toContainText('2.5 µg');
    await expect(panel).toContainText('1200 mg');
    await expect(panel).toContainText('30 mg');
    await expect(panel).toContainText('Includes estimates');
    await expect(panel).toContainText('Incomplete coverage');
    for (const width of [390, 1280]) {
        await page.setViewportSize({width, height: 950});
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await panel.scrollIntoViewIfNeeded();
        await page.screenshot({path: testInfo.outputPath(`nutrient-panel-${width}.png`), fullPage: true});
    }
    await openSpaRoute(page, '/');
    await page.locator('.home-panels-tabs').getByRole('tab', {name: 'Nutrition'}).click();
    await expect(panel).toContainText('1200 mg');
    await page.screenshot({path: testInfo.outputPath('nutrient-dashboard-desktop.png'), fullPage: true});
    await page.setViewportSize({width: 390, height: 950});
    await page.screenshot({path: testInfo.outputPath('nutrient-dashboard-mobile.png'), fullPage: true});
    page.once('dialog', confirmation => confirmation.accept());
    await page.locator('.meal-entry').first().getByRole('button', {name: 'Delete', exact: true}).click();
    await expect(panel).toContainText('No nutrient data recorded.');
    await expect(panel).toContainText('Incomplete coverage');
});
