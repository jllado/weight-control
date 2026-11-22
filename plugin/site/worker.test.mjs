import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, before, test } from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const api = 'https://weightcontrol.devjllado.com/api/chatgpt-actions';
const site = 'https://weight-control.example';
const email = 'coach-owner@example.test';
const identity = { 'oai-authenticated-user-id': 'fixture-owner', 'oai-authenticated-user-email': email };
const env = { CHATGPT_ACTION_TOKEN: 'fixture-only-token', COACH_OWNER_EMAIL: email };
const date = '2026-05-12';
const from = '2026-05-10';
const range = { from, to: date };
const rangeQuery = '?from=' + from + '&to=' + date;
const note = { date, content: 'Synthetic test note', confirmed: true };
const values = {
    quantity: 100, calories: 103, proteinGrams: 12, carbohydrateGrams: 7, fatGrams: 3,
    omega3Milligrams: 15, epaDhaMilligrams: 4, alaMilligrams: 9, magnesiumMilligrams: 20,
    vitaminDMicrograms: 2, fiberGrams: 3, saltGrams: 0.17, saturatedFatGrams: 1.2
};
const dish = {
    name: 'Synthetic test food', unit: 'GRAM', ...values, reference: values, addToCatalog: false,
    nutrientSource: 'Test fixture', nutrientsEstimated: true, epaDhaSource: 'Test fixture', epaDhaEstimated: true,
    alaSource: 'Test fixture', alaEstimated: true, fiberSource: 'Test fixture', fiberEstimated: true,
    saltSource: 'Test fixture', saltEstimated: true, saturatedFatSource: 'Test fixture', saturatedFatEstimated: true
};
const meal = {
    date, mealType: 'LUNCH', mealTime: '13:45:00', calories: 103, source: 'MANUAL',
    dishes: [dish], nutrientSchemaVersion: 4, confirmed: true
};
const fasting = { startTime: '2026-05-11T20:30:00+02:00', endTime: '2026-05-12T08:30:00+02:00', confirmed: true };
const constraint = { type: 'OTHER', title: 'Fixture', details: 'Acceptance test', source: 'SELF_REPORTED', startDate: date, active: false, endDate: null, confirmed: true };
const plan = { goal: 'Fixture goal', principles: ['Principle'], priorities: ['Priority'], actions: ['Action'], startDate: date, reviewDate: null, notes: null, confirmed: true };
const weight = { date, weight: 72.5, fatPercentage: 15, muscle: 51, confirmed: true };
const sleep = {
    sleepDate: date, bedtimeStart: '2026-05-11T23:00:00+02:00', bedtimeEnd: '2026-05-12T07:00:00+02:00',
    totalSleepDuration: 27000, deepSleepDuration: 5400, remSleepDuration: 5400, lightSleepDuration: 16200,
    awakeTime: 1800, averageHeartRate: 52, averageHrv: 70, confirmed: true
};
const section = { summary: 'Synthetic observation', nextAction: 'Synthetic action' };
const review = { reviewedDate: date, reviews: [], newWarnings: [] };
const daily = {
    title: 'Fixture', summary: 'Synthetic reflection', positiveSignals: ['Signal'], watchouts: ['Watchout'],
    nextActions: ['Action'], meals: section, workouts: section, warningReview: review, confirmed: true
};
const weekly = {
    title: 'Fixture', summary: 'Synthetic weekly reflection', bodyComposition: section, bloodPressure: section,
    routines: section, nutrition: section, trainingRecovery: section, goalProgress: section,
    nextWeekActions: ['Action'], warningReview: review, confirmed: true
};
const warning = {
    id: 42, resolve: {
        version: 2, reviewedDate: date, rationale: 'The synthetic goal is met.',
        metricsReview: {
            windowDays: 7, windowEnd: date, todayComplete: true, shortStatus: 'Target met.',
            metrics: [{ key: 'sleep', label: 'Sleep', source: 'RECOVERY', aggregation: 'SUM',
                unit: 'seconds', value: 160000, minimum: 151200, coveredDays: 7, goalBasis: 'Fixture goal' }]
        }
    }
};

// Keep explicit examples independent of the OpenAPI-to-MCP compiler. The last column is precisely the JSON body.
const operations = [
    ['getCoachCatalog', 'GET', '/coach/catalog'],
    ['getHealthContext', 'GET', '/coach/context' + rangeQuery + '&domains=RECORDS%2CFOODS&recordsPage=0&foodQuery=tea+%26+milk&recordsPageSize=5',
        { ...range, domains: ['RECORDS', 'FOODS'], recordsPage: 0, foodQuery: 'tea & milk', recordsPageSize: 5 }],
    ['listProgressPhotos', 'GET', '/coach/progress-photos'],
    ['getProgressPhotoFiles', 'GET', '/coach/progress-photos/29/files?sides=FRONT%2CLEFT', { photoSetId: 29, sides: ['FRONT', 'LEFT'] }],
    ['createCoachNote', 'POST', '/coach/coach-notes', {}, note],
    ['getMeals', 'GET', '/coach/meals' + rangeQuery, range],
    ['createMeal', 'POST', '/coach/meals', {}, meal],
    ['updateMeal', 'PUT', '/coach/meals/11', { id: 11 }, meal],
    ['deleteMeal', 'POST', '/coach/meals/11/delete', { id: 11 }, { confirmed: true }],
    ['getFastingPeriods', 'GET', '/coach/fasting-periods' + rangeQuery, range],
    ['createFastingPeriod', 'POST', '/coach/fasting-periods', {}, fasting],
    ['updateFastingPeriod', 'PUT', '/coach/fasting-periods/5', { id: 5 }, fasting],
    ['deleteFastingPeriod', 'POST', '/coach/fasting-periods/5/delete', { id: 5 }, { confirmed: true }],
    ['getHealthConstraints', 'GET', '/coach/health-constraints'],
    ['createHealthConstraint', 'POST', '/coach/health-constraints', {}, constraint],
    ['updateHealthConstraint', 'PUT', '/coach/health-constraints/5', { id: 5 }, constraint],
    ['getActivePlan', 'GET', '/coach/active-plan?target=WORKOUT&exerciseQuery=chest+press', { target: 'WORKOUT', exerciseQuery: 'chest press' }],
    ['updateActivePlan', 'PUT', '/coach/active-plan?target=COACHING', { target: 'COACHING' }, plan],
    ['getWorkoutAssessmentContext', 'GET', '/coach/workouts/' + date + '/assessment-context', { workoutDate: date }],
    ['getRatingRules', 'GET', '/coach/rating-rules'],
    ['getHealthEntries', 'GET', '/coach/health-entries/WEIGHT' + rangeQuery, { entryType: 'WEIGHT', ...range }],
    ['createHealthEntry', 'POST', '/coach/health-entries/WEIGHT', { entryType: 'WEIGHT' }, weight],
    ['updateHealthEntry', 'PUT', '/coach/health-entries/WEIGHT/9', { entryType: 'WEIGHT', id: 9 }, weight],
    ['createSleep', 'POST', '/coach/sleeps', {}, sleep],
    ['updateSleep', 'PUT', '/coach/sleeps/7', { id: 7 }, sleep],
    ['getReflectionOverview', 'GET', '/reflections/overview?target=WEEKLY', { target: 'WEEKLY' }],
    ['getReflectionContext', 'GET', '/reflections/' + date + '/context?target=DAILY', { date, target: 'DAILY' }],
    ['saveReflection', 'POST', '/reflections/' + date + '?target=DAILY', { date, target: 'DAILY' }, daily],
    ['getCoachWarnings', 'GET', '/coach/warnings?view=REVISIONS&id=42&page=0', { view: 'REVISIONS', id: 42, page: 0 }],
    ['saveCoachWarning', 'POST', '/coach/warnings', {}, warning]
];

let directory, worker;
before(async () => {
    directory = mkdtempSync(join(tmpdir(), 'weight-control-coach-test-'));
    const bundle = join(directory, 'worker.mjs');
    execFileSync(process.execPath, [fileURLToPath(new URL('build.mjs', import.meta.url)), '--output', bundle]);
    worker = (await import(pathToFileURL(bundle))).default;
});
after(() => rmSync(directory, { recursive: true, force: true }));

function post(method, params, headers = identity, environment = env) {
    return worker.fetch(new Request(site + '/mcp', {
        method: 'POST', headers: { 'content-type': 'application/json', ...headers },
        body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params })
    }), environment);
}

async function rpc(method, params) {
    const response = await post(method, params);
    assert.equal(response.status, 200);
    return response.json();
}

async function tool(name, args) {
    const message = await rpc('tools/call', { name, arguments: args });
    assert.ok(message.result, JSON.stringify(message.error));
    return message.result;
}

test('the installed server advertises the coaching instructions and all 30 operations', async () => {
    const { result } = await rpc('server/discover');
    const guide = readFileSync(new URL('../../docs/coach/coach-gpt.md', import.meta.url), 'utf8');
    const canonical = guide.match(/^## Instructions\r?\n\s*```text\r?\n([\s\S]+?)\r?\n```/m)[1];
    assert.ok(result.instructions.includes(canonical), 'The hosted skill must include the current Coach instructions.');
    assert.deepEqual(result.capabilities.tools, {});
    const { result: { tools } } = await rpc('tools/list');
    assert.equal(tools.length, 30);
    assert.deepEqual(tools.map(t => t.name).sort(), operations.map(([name]) => name).sort());
    for (const [name, method] of operations) {
        const definition = tools.find(t => t.name === name);
        assert.equal(definition.annotations.readOnlyHint, method === 'GET', name);
        assert.equal(definition.inputSchema.type, 'object', name);
        assert.doesNotMatch(JSON.stringify(definition.inputSchema), /#\/components\//, name);
    }
});

for (const [name, method, path, params, body] of operations.filter(([name]) => name !== 'getProgressPhotoFiles')) {
    test(name + ' forwards the contracted method, route, and body', async t => {
        let request;
        t.mock.method(globalThis, 'fetch', async (url, init) => {
            request = new Request(url, init);
            return Response.json({ fixture: name });
        });
        const result = await tool(name, { ...params, ...body });
        assert.equal(result.isError, false, result.content[0].text);
        assert.deepEqual(JSON.parse(result.content[0].text), { fixture: name });
        const actual = new URL(request.url), expected = new URL(api + path);
        actual.searchParams.sort();
        expected.searchParams.sort();
        assert.equal(actual.href, expected.href);
        assert.equal(request.method, method);
        assert.equal(request.headers.get('authorization'), 'Bearer ' + env.CHATGPT_ACTION_TOKEN);
        assert.equal(request.headers.get('oai-authenticated-user-email'), null);
        assert.equal(request.headers.get('oai-authenticated-user-id'), null);
        assert.equal(request.redirect, 'manual');
        if (body) assert.deepEqual(await request.json(), body);
        else assert.equal(request.body, null);
    });
}

test('the weekly reflection branch strips the routing fields from its saved payload', async t => {
    let body;
    t.mock.method(globalThis, 'fetch', async (url, init) => {
        assert.equal(String(url), api + '/reflections/' + date + '?target=WEEKLY');
        body = JSON.parse(init.body);
        return Response.json({ saved: true });
    });
    const result = await tool('saveReflection', { date, target: 'WEEKLY', ...weekly });
    assert.equal(result.isError, false, result.content[0].text);
    assert.deepEqual(body, weekly);
});

test('private records require the configured owner, a Sites identity and the backend credential', async t => {
    const upstream = t.mock.method(globalThis, 'fetch', () => Response.json({}));
    const params = { name: 'getCoachCatalog', arguments: {} };
    for (const headers of [{}, { ...identity, origin: 'https://unrelated.example' },
        { ...identity, 'oai-authenticated-user-email': 'other@example.test' },
        { 'oai-authenticated-user-email': email }, { 'oai-authenticated-user-id': 'fixture-owner' }]) {
        assert.equal((await post('tools/call', params, headers)).status, 403);
    }
    for (const key of Object.keys(env)) {
        const missing = { ...env };
        delete missing[key];
        const response = await post('tools/call', params, identity, missing);
        if (response.ok) assert.equal((await response.json()).result?.isError, true, key);
    }
    assert.equal(upstream.mock.callCount(), 0);
});

test('a declined meal stays unsaved, and one confirmed meal reads back with the full reference', async t => {
    const records = [];
    t.mock.method(globalThis, 'fetch', async (url, init) => {
        if (init.method === 'GET') return Response.json(records);
        const row = { ...JSON.parse(init.body), id: records.length + 1 };
        records.push(row);
        return Response.json(row, { status: 201 });
    });
    assert.equal((await tool('createMeal', { ...meal, confirmed: false })).isError, true);
    assert.deepEqual(records, []);
    const saved = await tool('createMeal', meal);
    assert.equal(saved.isError, false, saved.content[0].text);
    const read = await tool('getMeals', range);
    assert.deepEqual(JSON.parse(read.content[0].text), [{ ...meal, id: 1 }]);
});

test('invalid identifiers, obsolete meal payloads and missing consent are rejected before forwarding', async t => {
    const upstream = t.mock.method(globalThis, 'fetch', () => Response.json({}));
    for (const [name, args] of [
        ['getProgressPhotoFiles', { photoSetId: 0, sides: ['FRONT'] }],
        ['getHealthEntries', { entryType: 'OTHER_ACCOUNT' }],
        ['createMeal', { ...meal, nutrientSchemaVersion: 3 }],
        ['createMeal', { ...meal, dishes: [{ ...dish, saltGrams: null }] }],
        ['deleteMeal', { id: 5, confirmed: false }],
        ['saveReflection', { ...daily, date, confirmed: undefined }]
    ]) {
        assert.equal((await tool(name, args)).isError, true, name);
    }
    assert.ok((await rpc('tools/call', { name: '__proto__', arguments: {} })).error);
    assert.equal(upstream.mock.callCount(), 0);
});

test('stale edits and unknown save outcomes return errors without automatic retry', async t => {
    const upstream = t.mock.method(globalThis, 'fetch', () => Response.json({ message: 'Stale edit' }, { status: 409 }));
    const stale = await tool('updateHealthEntry', { ...weight, id: 9, entryType: 'WEIGHT' });
    assert.equal(stale.isError, true);
    assert.match(stale.content[0].text, /409/);
    upstream.mock.mockImplementation(() => { throw new Error('connection lost'); });
    assert.equal((await tool('createCoachNote', note)).isError, true);
    assert.equal(upstream.mock.callCount(), 2);
});


test('selected photo sides return labeled image content and keep signed URLs and credentials private', async t => {
    const origin = new URL(api).origin;
    const paths = ['front-fixture-token', 'left-fixture-token'].map(token => '/api/chatgpt-files/progress-photos/' + token);
    const sent = [];
    t.mock.method(globalThis, 'fetch', async (url, init) => {
        const request = new Request(url, init);
        sent.push(request);
        if (sent.length === 1) return Response.json({ openaiFileResponse: paths.map(path => origin + path) });
        const side = paths.indexOf(new URL(request.url).pathname);
        return new Response(new Uint8Array([42, side]), { headers: { 'Content-Type': 'image/jpeg' } });
    });
    const result = await tool('getProgressPhotoFiles', { photoSetId: 29, sides: ['FRONT', 'LEFT'] });
    assert.equal(result.isError, false, result.content[0].text);
    assert.deepEqual(sent.map(request => request.url), [
        api + '/coach/progress-photos/29/files?sides=FRONT%2CLEFT', ...paths.map(path => origin + path)
    ]);
    assert.equal(sent[0].headers.get('authorization'), 'Bearer ' + env.CHATGPT_ACTION_TOKEN);
    for (const request of sent.slice(1)) {
        assert.equal(request.headers.get('authorization'), null);
        assert.equal(request.redirect, 'manual');
    }
    assert.deepEqual(result.content.filter(block => block.type === 'image').map(({ mimeType, data }) => [mimeType, data]),
        [['image/jpeg', 'KgA='], ['image/jpeg', 'KgE=']]);
    const labels = result.content.filter(block => block.type === 'text').map(block => block.text).join(' ');
    assert.match(labels, /front/i);
    assert.match(labels, /left/i);
    assert.doesNotMatch(JSON.stringify(result), /fixture-token/);
});

test('photo URLs outside the Weight Control signed-file route are never fetched or returned', async t => {
    for (const url of ['https://other.example/api/chatgpt-files/progress-photos/secret', new URL('/elsewhere/secret', api).href]) {
        const upstream = t.mock.method(globalThis, 'fetch', async () => Response.json({ openaiFileResponse: [url] }));
        const result = await tool('getProgressPhotoFiles', { photoSetId: 29, sides: ['FRONT'] });
        assert.equal(result.isError, true);
        assert.doesNotMatch(JSON.stringify(result), /secret/);
        assert.equal(upstream.mock.callCount(), 1);
        upstream.mock.restore();
    }
});

test('an unexpected successful HTML page cannot confirm a save', async t => {
    const upstream = t.mock.method(globalThis, 'fetch', () => new Response('<html>Login</html>', {
        headers: { 'content-type': 'text/html' }
    }));
    const result = await tool('createCoachNote', note);
    assert.equal(result.isError, true);
    assert.equal(upstream.mock.callCount(), 1);
});

test('an explicitly retried warning creation passes the original request key and content', async t => {
    const create = {
        requestKey: 'dd9cff47-90d8-4179-8fa4-47935cfe035a',
        type: 'RECOVERY_STRAIN',
        content: {
            explanation: 'Synthetic explanation.', evidence: 'Synthetic evidence.', action: 'Synthetic action.',
            reviewedDate: date, metricsReview: warning.resolve.metricsReview
        }
    };
    const requests = [];
    t.mock.method(globalThis, 'fetch', async (url, init) => {
        requests.push(JSON.parse(init.body));
        if (requests.length === 1) throw new Error('response lost');
        return Response.json({ id: 42 });
    });
    assert.equal((await tool('saveCoachWarning', { create })).isError, true);
    const retry = await tool('saveCoachWarning', { create });
    assert.equal(retry.isError, false, retry.content[0].text);
    assert.deepEqual(requests, [{ create }, { create }]);
});

test('an expected empty read can succeed while an empty write result cannot confirm persistence', async t => {
    const upstream = t.mock.method(globalThis, 'fetch', () => new Response(null, { status: 204 }));
    const emptyPlan = await tool('getActivePlan', { target: 'COACHING' });
    assert.equal(emptyPlan.isError, false);
    assert.equal(JSON.parse(emptyPlan.content[0].text), null);
    for (const status of [200, 204]) {
        upstream.mock.mockImplementation(() => new Response(null, { status }));
        const saved = await tool('createCoachNote', note);
        assert.equal(saved.isError, true, 'HTTP ' + status);
    }
    assert.equal(upstream.mock.callCount(), 3);
});

test('backend validation and conflict messages are actionable and omit server internals', async t => {
    for (const status of [400, 409]) {
        const upstream = t.mock.method(globalThis, 'fetch', () => Response.json({
            message: 'Read the current entry before editing it.', stackTrace: 'Private implementation details'
        }, { status }));
        const result = await tool('updateHealthEntry', { ...weight, id: 9, entryType: 'WEIGHT' });
        assert.equal(result.isError, true);
        assert.match(result.content[0].text, /Read the current entry/);
        assert.doesNotMatch(result.content[0].text, /Private implementation details/);
        upstream.mock.restore();
    }
});

for (const target of ['DAILY', 'WEEKLY']) {
    test(target + ' reflection context returns its exact write shape and field limits next to unchanged read data', async t => {
        const data = { selectedDate: date, syntheticObservation: 'Context read' };
        t.mock.method(globalThis, 'fetch', () => Response.json(data));
        const result = await tool('getReflectionContext', { target, date });
        assert.equal(result.isError, false);
        assert.equal(result.content.length, 2);
        assert.deepEqual(JSON.parse(result.content[0].text), data);
        assert.equal(result.content[1].type, 'text');
        const schema = JSON.parse(result.content[1].text);
        const resolve = field => {
            if (!field.$ref) return field;
            const [, section, name] = field.$ref.split('/');
            assert.equal(section, '$defs');
            assert.ok(schema.$defs[name], name);
            return schema.$defs[name];
        };
        const props = schema.properties;
        const fields = input => Object.keys(input).sort();
        const unique = input => [...new Set(input)].sort();
        const expected = target === 'DAILY' ? daily : weekly;
        assert.deepEqual(unique(schema.required), unique(['date', 'target', ...fields(expected)]));
        for (const name of schema.required) assert.ok(props[name], name);
        assert.equal(props.confirmed.const, true);
        assert.equal(props.target.const, target);
        assert.equal(props.title.maxLength, 80);
        assert.equal(props.summary.maxLength, target === 'DAILY' ? 200 : 500);
        const warningReview = resolve(props.warningReview);
        assert.deepEqual(unique(warningReview.required), ['newWarnings', 'reviewedDate', 'reviews']);
        if (target === 'DAILY') {
            for (const key of ['positiveSignals', 'watchouts', 'nextActions']) {
                const listSchema = resolve(props[key]);
                assert.equal(listSchema.minItems, 1, key);
                assert.equal(listSchema.maxItems, 1, key);
                assert.equal(resolve(listSchema.items).maxLength, key === 'nextActions' ? 200 : 120, key);
            }
            assert.equal(props.nextWeekActions, undefined);
            assert.equal(props.bodyComposition, undefined);
        } else {
            const actions = resolve(props.nextWeekActions);
            assert.equal(actions.minItems, 1);
            assert.equal(actions.maxItems, 5);
            assert.equal(resolve(actions.items).maxLength, 120);
            assert.equal(props.positiveSignals, undefined);
            assert.equal(props.meals, undefined);
        }
        const sections = target === 'DAILY' ? ['meals', 'workouts'] :
            ['bodyComposition', 'bloodPressure', 'routines', 'nutrition', 'trainingRecovery', 'goalProgress'];
        for (const name of sections) {
            const section = resolve(props[name]);
            assert.deepEqual(unique(section.required), ['nextAction', 'summary']);
            assert.equal(section.properties.summary.maxLength, target === 'DAILY' ? 200 : 400);
            assert.equal(section.properties.nextAction.maxLength, target === 'DAILY' ? 120 : 200);
        }
    });
}
