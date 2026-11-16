const {test, expect} = require('@playwright/test');
const {profile, dashboard, dashboardDailyStatus, nutrientTargetsForDate} = require('../fixtures/dashboard.cjs');

const targetDate = '2026-08-20';
const sourceDate = '2026-08-19';
const longName = 'Yogurt with a very long descriptive food name that wraps across narrow screens';
const nutrientKeys = ['calories', 'proteinGrams', 'carbohydrateGrams', 'fatGrams', 'vitaminDMicrograms', 'omega3Milligrams', 'magnesiumMilligrams', 'epaDhaMilligrams', 'alaMilligrams', 'fiberGrams', 'saltGrams'];
function food(name = longName, overrides = {}) {
    return {id: 41, name, calories: 51, proteinGrams: 0.51, carbohydrateGrams: 2.12, fatGrams: null,
        vitaminDMicrograms: 1.01, omega3Milligrams: 13.13, magnesiumMilligrams: 6.67, epaDhaMilligrams: 0, alaMilligrams: 1.11, fiberGrams: null, saltGrams: 0.23,
        quantity: 50, unit: 'GRAM', fruit: false, nutrientSource: null, nutrientsEstimated: null,
        epaDhaSource: 'Measured zero', epaDhaEstimated: false, alaSource: 'Independent ALA estimate', alaEstimated: true,
        fiberSource: null, fiberEstimated: null, saltSource: 'Label salt', saltEstimated: false,
        reference: {quantity: 100, calories: 101, proteinGrams: 1.01, carbohydrateGrams: 4.23, fatGrams: null,
            vitaminDMicrograms: 2.01, omega3Milligrams: 26.25, magnesiumMilligrams: 13.33, epaDhaMilligrams: 0, alaMilligrams: 2.21, fiberGrams: null, saltGrams: 0.45}, ...overrides};
}
function meal(id, date, mealType, overrides = {}) {
    return {id, date, mealType, mealSequence: 1, calories: 333, proteinGrams: 17.17, carbohydrateGrams: 20.02, fatGrams: null,
        mealTime: '08:05:27', durationMinutes: 20, notes: 'Original notes', rating: 7, source: 'GPT_IMAGE_ESTIMATE', dishes: [food()], ...overrides};
}
function sourceMeals() {
    const legacy = food('Legacy lentils', {vitaminDMicrograms: null, omega3Milligrams: null, magnesiumMilligrams: null, nutrientSource: null,
        epaDhaMilligrams: null, epaDhaSource: null, epaDhaEstimated: null, alaMilligrams: null, alaSource: null, alaEstimated: null});
    legacy.reference = {...legacy.reference, vitaminDMicrograms: null, omega3Milligrams: null, magnesiumMilligrams: null, epaDhaMilligrams: null, alaMilligrams: null};
    return [meal(1, sourceDate, 'BREAKFAST', {dishes: [food(), food('Apple', {fruit: true})]}),
        meal(2, sourceDate, 'LUNCH', {calories: 700, proteinGrams: null, mealTime: '13:30:00', durationMinutes: 35, dishes: [legacy]}),
        meal(3, sourceDate, 'SNACK', {mealSequence: 2, calories: 42, proteinGrams: 0, carbohydrateGrams: 0, fatGrams: 0, mealTime: null, durationMinutes: null, notes: null, dishes: []}),
        meal(4, sourceDate, 'SNACK', {mealSequence: 5, calories: 150, mealTime: '17:00:00', durationMinutes: 5, dishes: [food('Almonds')]})];
}
function snapshot(entry) {
    const {id, date, rating, source, ...copy} = entry; // Persisted identity and evaluation are deliberately excluded.
    void id; void date; void rating; void source;
    copy.dishes = entry.dishes.map(dish => { const {id, ...copy} = dish; void id; return copy; });
    return copy;
}
async function openCopy(page, entries = sourceMeals(), route = `/meals/new?from=dashboard&date=${targetDate}`) {
    const state = {meals: structuredClone(entries), writes: [], error: false, hold: null, loseResponse: false, dashboardDates: [], errors: []};
    page.on('pageerror', error => state.errors.push(error.message));
    await page.route('**/api/**', async route => {
        const request = route.request(), url = new URL(request.url()), path = url.pathname;
        const json = value => route.fulfill({json: value});
        if (request.method() !== 'GET') state.writes.push({path, body: request.postDataJSON()});
        if (path === '/api/auth/me') return json({authenticated: true, email: 'owner@example.com'});
        if (path === '/api/profile') return json(profile);
        if (path === '/api/urge-pauses') return json({pause: null, serverNow: '2026-08-20T12:00:00Z'});
        if (path === '/api/coach-warnings') return json({active: [], hasHistory: false});
        if (path === '/api/meals/copy-day') {
            if (state.hold) await state.hold;
            if (state.error) return route.fulfill({status: 400, json: {message: 'Target date must be empty before copying meals'}});
            const body = request.postDataJSON();
            const result = body.meals.map((entry, index) => ({...entry, date: body.date, id: 100 + index, source: 'MANUAL', rating: null, dishes: entry.dishes.map((dish, index) => ({...dish, id: 1000 + index}))}));
            state.meals.push(...result);
            if (state.loseResponse) return route.abort('failed');
            return json({result, recordAchievements: []});
        }
        if (path === '/api/meals') return json(state.meals);
        if (path === '/api/nutrition/targets') return json(nutrientTargetsForDate(url.searchParams.get('asOf') || targetDate));
        if (path.startsWith('/api/weekly-calorie-plans/')) return route.fulfill({status: 204});
        if (path === '/api/dashboard') {
            const date = url.searchParams.get('selectedDate') || '2026-08-21';
            state.dashboardDates.push(date);
            return json({...dashboard, anchorDate: date, dailyStatus: dashboardDailyStatus(date)});
        }
        if (path === '/api/dashboard/overall-progress') return json({status: null, score: null, contributions: []});
        if (path === '/api/workouts/dashboard') return json({currentWorkouts: [], previousWeekWorkouts: [], preloadWorkouts: [], recordEvents: [], days: []});
        if (path === '/api/dashboard/coach-metrics') {
            const totals = {workoutCount: 0, totalDurationSeconds: 0, strengthVolumeKg: 0, totalDistanceKm: 0, totalCalories: 0};
            const week = {startDate: '2026-08-15', endDate: '2026-08-21', reflections: [], workouts: [], totals};
            return json({selectedWeek: week, previousWeek: week, selectedWeekToDate: week, previousWeekToDate: week, reflections: [], workouts: [], weeklyWorkouts: [], planProgressTrend: {latestScore: null, previousScore: null}});
        }
        return json([]);
    });
    await page.addInitScript(route => {
        if (!sessionStorage.getItem('day-copy-initial-route')) {
            sessionStorage.setItem('day-copy-initial-route', 'ready');
            window.history.replaceState({}, '', route);
        }
    }, route);
    await page.goto('/');
    await expect(page.locator('#meal-form')).toBeVisible();
    return state;
}
async function chooseDay(page) {
    await page.getByRole('combobox', {name: 'Preload a previous day', exact: true}).click();
    await page.getByRole('option').first().click();
    await expect(page.getByRole('heading', {name: 'Copy day', exact: true})).toBeVisible();
}
async function editMeal(page, label) {
    await page.getByRole('button', {name: `Edit ${label}`, exact: true}).click();
    await expect(page.getByRole('heading', {name: 'Edit meal', exact: true})).toBeFocused();
}
async function saveLocal(page) { await page.locator('#meal-form').getByRole('button', {name: 'Apply', exact: true}).click(); }

// A different saved meal aggregate and a non-roundtripping food reference expose accidental recalculation.
test('day copy preserves every snapshot through no-op and descriptive local edits, save and reload', async ({page}) => {
    const sources = sourceMeals(), state = await openCopy(page, sources);
    await chooseDay(page);
    await expect(page.locator('.day-meal-row')).toHaveCount(4);
    await expect(page.getByRole('article', {name: 'Snack 2', exact: true})).toContainText('No start time');
    await editMeal(page, 'breakfast');
    await page.getByRole('button', {name: 'Edit food 1', exact: true}).click();
    const foodDialog = page.getByRole('dialog', {name: 'Food', exact: true});
    await foodDialog.getByRole('button', {name: 'Apply', exact: true}).click();
    await saveLocal(page);
    await expect(page.getByRole('article', {name: 'Breakfast', exact: true})).toContainText('333 kcal');
    await editMeal(page, 'lunch');
    await page.getByRole('button', {name: 'Edit food 1', exact: true}).click();
    await foodDialog.getByLabel('Food', {exact: true}).fill('Renamed legacy lentils');
    await foodDialog.getByRole('button', {name: 'Apply', exact: true}).click();
    await page.getByLabel('Notes (optional)', {exact: true}).fill('Copied-day notes');
    await saveLocal(page);
    const expected = sources.map(snapshot);
    expected[1].dishes[0].name = 'Renamed legacy lentils';
    expected[1].notes = 'Copied-day notes';
    await editMeal(page, 'snack 5');
    await page.locator('#meal-time').fill('17:15');
    await page.locator('#meal-time').press('Tab');
    await page.getByLabel('Duration (minutes)', {exact: true}).click();
    const duration = page.getByRole('listbox', {name: 'Duration in minutes', exact: true});
    await duration.press('ArrowDown');
    await duration.press('Enter');
    await expect(page.getByLabel('Duration (minutes)', {exact: true})).toHaveText('10');
    await saveLocal(page);
    expected[3].mealTime = '17:15:00';
    expected[3].durationMinutes = 10;
    expect(state.writes).toEqual([]);
    await page.locator('#meal-day-copy').getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page).toHaveURL(`/?tab=calories&date=${targetDate}`);
    expect(state.writes).toEqual([{path: '/api/meals/copy-day', body: {date: targetDate, meals: expected}}]);
    expect(state.meals.filter(entry => entry.date === sourceDate)).toEqual(sources);
    expect(state.meals.filter(entry => entry.date === targetDate).map(snapshot)).toEqual(expected);
    await expect(page.locator('.dashboard-date-value')).toHaveText('20/08/2026');
    await expect(page.locator('.meal-entry')).toHaveCount(4);
    await page.reload();
    await expect(page.locator('.meal-entry')).toHaveCount(4);
    await expect(page.locator('.dashboard-date-value')).toHaveText('20/08/2026');
    expect(state.dashboardDates.every(date => date === targetDate)).toBe(true);
    expect(state.errors).toEqual([]);
});

for (const scaling of [true, false]) {
    test(`day copy intentional portion changes preserve reference semantics with scaling ${scaling ? 'on' : 'off'}`, async ({page}) => {
        const sources = [sourceMeals()[0]], state = await openCopy(page, sources);
        await chooseDay(page);
        await editMeal(page, 'breakfast');
        await page.getByRole('button', {name: 'Edit food 1', exact: true}).click();
        const dialog = page.getByRole('dialog', {name: 'Food', exact: true});
        if (!scaling) {
            await dialog.locator('label[for="scale-nutrition"]').click();
            await expect(dialog.getByRole('switch', {name: 'Scale nutrition with quantity', exact: true})).toHaveAttribute('aria-checked', 'false');
        }
        await dialog.getByLabel('Quantity', {exact: true}).fill('100');
        await dialog.getByLabel('Quantity', {exact: true}).press('Tab');
        await expect(dialog.getByLabel('Calories', {exact: true})).toHaveValue(scaling ? '101' : '51');
        await dialog.getByRole('button', {name: 'Apply', exact: true}).click();
        await saveLocal(page);
        await page.locator('#meal-day-copy').getByRole('button', {name: 'Save', exact: true}).click();
        await expect(page).toHaveURL(`/?tab=calories&date=${targetDate}`);
        const copied = state.writes[0].body.meals[0], original = sources[0].dishes[0];
        expect(copied.dishes[0].quantity).toBe(100);
        expect(copied.dishes[1]).toEqual(snapshot(sources[0]).dishes[1]);
        if (scaling) {
            for (const key of nutrientKeys) expect(copied.dishes[0][key]).toBe(original.reference[key]);
            expect(copied.dishes[0].reference).toEqual(original.reference);
            expect(copied.calories).toBe(152);
            expect(copied.proteinGrams).toBe(1.52);
        } else {
            for (const key of nutrientKeys) expect(copied.dishes[0][key]).toBe(original[key]);
            expect(copied.dishes[0].reference).toEqual({quantity: 100, ...Object.fromEntries(nutrientKeys.map(key => [key, original[key]]))});
            expect(copied.calories).toBe(sources[0].calories);
            expect(copied.proteinGrams).toBe(sources[0].proteinGrams);
        }
        expect(state.errors).toEqual([]);
    });
}

test('day selector keeps latest 14 distinct earlier days, food count and kcal; draft and edits fit all widths', async ({page}, testInfo) => {
    test.setTimeout(60000);
    const history = Array.from({length: 16}, (_, index) => meal(20 + index, `2026-08-${String(index + 1).padStart(2, '0')}`, 'DINNER', {dishes: [food(`Old food ${index + 1}`)]}));
    history[15].dishes = [];
    const state = await openCopy(page, [...sourceMeals(), ...history, meal(90, targetDate, 'DINNER'), meal(91, '2026-08-21', 'DINNER')].filter(entry => entry.date !== targetDate));
    const selector = page.getByRole('combobox', {name: 'Preload a previous day', exact: true});
    for (const width of [376, 390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await expect(page.locator('#meal-form').getByText('Value is required', {exact: true})).toHaveCount(0);
        await selector.click();
        await expect(page.locator('#meal-form').getByText('Value is required', {exact: true})).toHaveCount(0);
        const options = page.getByRole('option');
        await expect(options).toHaveCount(14);
        await expect(options.first().locator('strong')).toHaveText(`${longName} + 3 foods`);
        await expect(options.first().locator('small')).toHaveText('19/08/2026');
        await expect(options.first()).toContainText('1,225 kcal');
        await expect(options.nth(1).locator('strong')).toHaveText('No foods');
        await expect(options.last().locator('small')).toHaveText('04/08/2026');
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`day-selector-${width}.png`), animations: 'disabled'});
        await selector.press('Escape');
    }
    await selector.press('Space');
    await selector.press('Home');
    await selector.press('Enter');
    const copy = page.locator('#meal-day-copy');
    await expect(copy).toBeVisible();
    await copy.locator('summary').click();
    await expect(copy.getByRole('region', {name: 'Meal nutrients'})).toBeVisible();
    await expect(copy.getByText('Omega-3', {exact: true})).toBeVisible();
    for (const width of [390, 575, 640, 960, 1280]) {
        await page.setViewportSize({width, height: 950});
        await copy.getByRole('heading', {name: 'Copy day', exact: true}).scrollIntoViewIfNeeded();
        await expect(copy.getByText('20/08/2026', {exact: true})).toBeVisible();
        await expect(copy.getByText('20/08/2026', {exact: true})).toBeInViewport();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`day-draft-${width}.png`), animations: 'disabled', fullPage: true});
        await editMeal(page, 'breakfast');
        await expect(page.getByRole('button', {name: 'Save as dish', exact: true})).toBeDisabled();
        await expect(page.locator('#meal-type')).toHaveCount(0);
        await expect(page.locator('#meal-date')).toHaveCount(0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`day-meal-edit-${width}.png`), animations: 'disabled', fullPage: true});
        await page.locator('#meal-form').getByRole('button', {name: 'Cancel', exact: true}).click();
        await expect(copy.getByRole('button', {name: 'Edit breakfast', exact: true})).toBeFocused();
    }
    expect(state.writes).toEqual([]);
    expect(state.errors).toEqual([]);
});

test('day copy rejects occupied targets while normal meal selection remains available', async ({page}) => {
    const state = await openCopy(page, [...sourceMeals(), meal(8, targetDate, 'BREAKFAST')]);
    await expect(page.getByRole('combobox', {name: 'Preload a previous day', exact: true})).toHaveAttribute('aria-disabled', 'true');
    await expect(page.getByText('This day already has meals. Choose a meal type to add one meal.')).toBeVisible();
    await page.getByRole('combobox', {name: 'Meal', exact: true}).click();
    await page.getByRole('option', {name: 'Lunch', exact: true}).click();
    await expect(page.getByRole('combobox', {name: 'Preload a previous meal', exact: true})).toBeVisible();
    await page.getByRole('combobox', {name: 'Preload a previous meal', exact: true}).click();
    await expect(page.getByRole('option').first()).toContainText('Legacy lentils');
    await expect(page.getByRole('option').first()).toContainText('700 kcal');
    expect(state.writes).toEqual([]);
});

test('day copy local Cancel, source discard and navigation confirmation never write drafts', async ({page}) => {
    const state = await openCopy(page, [...sourceMeals(), meal(50, '2026-08-18', 'DINNER')]);
    await chooseDay(page);
    await editMeal(page, 'breakfast');
    await page.getByLabel('Notes (optional)', {exact: true}).fill('Canceled local note');
    page.once('dialog', dialog => dialog.accept());
    await page.locator('#meal-form').getByRole('button', {name: 'Cancel', exact: true}).click();
    await editMeal(page, 'breakfast');
    await expect(page.getByLabel('Notes (optional)', {exact: true})).toHaveValue('Original notes');
    await page.getByLabel('Notes (optional)', {exact: true}).fill('Edited local note');
    await saveLocal(page);
    const source = page.getByRole('combobox', {name: 'Preload a previous day', exact: true});
    await source.click();
    page.once('dialog', dialog => dialog.dismiss());
    await page.getByRole('option').nth(1).click();
    await expect(source).toContainText('19/08/2026');
    await source.click();
    page.once('dialog', dialog => dialog.accept());
    await page.getByRole('option').nth(1).click();
    await expect(source).toContainText('18/08/2026');
    await expect(page.locator('.day-meal-row')).toHaveCount(1);
    page.once('dialog', dialog => dialog.dismiss());
    await page.getByRole('link', {name: 'Home', exact: true}).click();
    await expect(page.locator('#meal-day-copy')).toBeVisible();
    page.once('dialog', dialog => dialog.accept());
    await page.locator('#meal-day-copy').getByRole('button', {name: 'Cancel', exact: true}).click();
    await expect(page).toHaveURL(`/?tab=calories&date=${targetDate}`);
    expect(state.writes).toEqual([]);
    expect(state.errors).toEqual([]);
});

test('day copy pending save blocks navigation; conflict refresh and successful retry preserve edited draft', async ({page}) => {
    const state = await openCopy(page);
    await chooseDay(page);
    await editMeal(page, 'lunch');
    await page.getByLabel('Notes (optional)', {exact: true}).fill('Retry preserves this edited note');
    await saveLocal(page);
    let release;
    state.hold = new Promise(resolve => { release = resolve; });
    state.error = true;
    const copy = page.locator('#meal-day-copy');
    await copy.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(copy.getByRole('button', {name: 'Saving…', exact: true})).toBeDisabled();
    await expect(copy.getByRole('button', {name: 'Cancel', exact: true})).toBeDisabled();
    await expect(copy.getByRole('button', {name: 'Edit breakfast', exact: true})).toBeDisabled();
    await page.getByRole('link', {name: 'Home', exact: true}).click();
    await expect(copy).toBeVisible();
    release();
    await expect(copy.getByRole('alert')).toContainText('Your changes are still here.');
    await expect(copy.locator('.day-meal-row')).toHaveCount(4);
    await copy.getByRole('button', {name: 'Refresh target status', exact: true}).click();
    await expect(copy.getByRole('alert')).toContainText('The target day is still empty.');
    state.meals.push(meal(99, targetDate, 'DINNER'));
    await copy.getByRole('button', {name: 'Refresh target status', exact: true}).click();
    await expect(copy.getByText('This day already has meals. Refresh the target status or return to the dashboard to review them.')).toBeVisible();
    await expect(copy.getByRole('button', {name: 'Save', exact: true})).toBeDisabled();
    expect(state.writes).toHaveLength(1);
    state.meals = state.meals.filter(entry => entry.date !== targetDate);
    state.error = false;
    await copy.getByRole('button', {name: 'Refresh target status', exact: true}).click();
    await expect(copy.getByRole('button', {name: 'Save', exact: true})).toBeEnabled();
    await copy.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(page).toHaveURL(`/?tab=calories&date=${targetDate}`);
    expect(state.writes).toHaveLength(2);
    expect(state.writes[1]).toEqual(state.writes[0]);
    expect(state.writes[1].body.meals[1].notes).toBe('Retry preserves this edited note');
    expect(state.meals.filter(entry => entry.date === targetDate).map(snapshot)).toEqual(state.writes[1].body.meals);
    expect(state.errors).toEqual([]);
});

test('day copy lost save response retains draft and refresh prevents a duplicate retry', async ({page}) => {
    const state = await openCopy(page);
    state.loseResponse = true;
    await chooseDay(page);
    const copy = page.locator('#meal-day-copy');
    await copy.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(copy.getByRole('alert')).toContainText('Refresh the target status before retrying');
    await expect(copy.locator('.day-meal-row')).toHaveCount(4);
    await copy.getByRole('button', {name: 'Refresh target status', exact: true}).click();
    await expect(copy.getByRole('button', {name: 'Save', exact: true})).toBeDisabled();
    expect(state.meals.filter(entry => entry.date === targetDate)).toHaveLength(4);
    expect(state.writes).toHaveLength(1);
});

test('ordinary blank meal Save still explains the required meal type', async ({page}) => {
    const state = await openCopy(page, sourceMeals(), `/meals/new?date=${targetDate}`);
    const form = page.locator('#meal-form');
    await expect(form.locator('#meal-type-error')).toHaveCount(0);
    await form.getByRole('button', {name: 'Save', exact: true}).click();
    await expect(form.locator('#meal-type-error')).toHaveText('Value is required');
    expect(state.writes).toEqual([]);
    expect(state.errors).toEqual([]);
});
