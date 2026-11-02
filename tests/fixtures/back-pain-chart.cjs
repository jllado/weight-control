const {googleClientScript, profile, dashboard} = require('./dashboard.cjs');

const backPainRecords = [
    {id: 1, date: '2026-08-12', period: 'MORNING', severity: 'SEVERE', region: 'LOWER', side: 'LEFT', note: 'Morning'},
    {id: 2, date: '2026-08-12', period: 'EVENING', severity: 'MILD', region: 'UPPER', side: 'RIGHT', note: 'Evening'},
    {id: 3, date: '2026-08-12', period: 'MORNING', severity: 'NONE', region: null, side: null, note: null},
    {id: 4, date: '2026-08-10', period: 'MIDDAY', severity: 'NONE', region: null, side: null, note: null},
    {id: 5, date: '2025-08-12', period: 'MORNING', severity: 'MODERATE', region: 'LOWER', side: 'LEFT', note: null}
];

async function mockBackPainHome(page, {records = backPainRecords, fail = false} = {}) {
    let saved = records.map(record => ({...record}));
    const state = {reads: 0, fail, records: () => saved};
    await page.clock.install({time: new Date('2026-08-12T12:00:00Z')});
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({contentType: 'application/javascript', body: googleClientScript}));
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const json = value => route.fulfill({json: value});
        if (path === '/api/auth/me') return json({authenticated: true, email: 'owner@example.com'});
        if (path === '/api/profile') return json(profile);
        if (path === '/api/dashboard' || path === '/api/dashboard/refresh') return json(dashboard);
        if (path === '/api/dashboard/overall-progress') return json({status: null, score: null, contributions: []});
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: '2026-08-12T12:00:00Z'});
        if (path === '/api/workouts/dashboard') return json({currentWorkouts: [], previousWeekWorkouts: [], preloadWorkouts: [], recordEvents: [], days: []});
        if (path === '/api/coach-warnings') return json({active: [], hasHistory: false});
        if (path.startsWith('/api/weekly-calorie-plans/')) return route.fulfill({status: 204});
        if (path === '/api/dashboard/coach-metrics') {
            const totals = {workoutCount: 0, totalDurationSeconds: 0, strengthVolumeKg: 0, totalDistanceKm: 0, totalCalories: 0};
            const week = {startDate: '2026-08-08', endDate: '2026-08-14', reflections: [], workouts: [], totals};
            return json({selectedWeek: week, previousWeek: week, selectedWeekToDate: week, previousWeekToDate: week,
                reflections: [], workouts: [], weeklyWorkouts: [], planProgressTrend: {latestScore: null, previousScore: null}});
        }
        if (path === '/api/back-pain-episodes') {
            if (request.method() === 'GET') {
                state.reads++;
                return state.fail ? route.fulfill({status: 503, json: {message: 'Temporarily unavailable'}}) : json(saved);
            }
            const record = {...request.postDataJSON(), id: 20};
            saved = [...saved, record];
            return json(record);
        }
        if (path.startsWith('/api/back-pain-episodes/')) {
            const id = Number(path.split('/').at(-1));
            if (request.method() === 'DELETE') {
                saved = saved.filter(record => record.id !== id);
                return route.fulfill({status: 204});
            }
            saved = saved.map(record => record.id === id ? {...record, ...request.postDataJSON()} : record);
            return json(saved.find(record => record.id === id));
        }
        return json([]);
    });
    return state;
}

module.exports = {backPainRecords, mockBackPainHome};
