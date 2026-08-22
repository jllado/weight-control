const {test, expect} = require('@playwright/test');

const profile = {
    birthDate: null,
    heightCm: 180,
    sex: 'MALE',
    fitnessLevel: 'ACTIVE',
    takesMedication: true,
    weeklyAverageCalorieMaximum: 2500,
    typicalCaloriesPerDay: {saturday: 2500, sunday: 2500, monday: 2000, tuesday: 2000, wednesday: 2000, thursday: 2000, friday: 2000},
    calorieShortcuts: {onPlan: 2000, flexible: 2500, offPlan: 3000, binge: 4000}
};

function medication(id = 1) {
    return {
        id,
        name: 'Vitamin D',
        doseAmount: 1,
        doseUnit: 'tablet',
        notes: 'Take with breakfast',
        startDate: '2026-08-01',
        endDate: '2026-12-31',
        repeatEvery: 2,
        repeatUnit: 'WEEK',
        reminderTimes: ['08:00:00', '20:00:00'],
        active: true
    };
}

function dose(id, status, source = 'SCHEDULED') {
    return {
        id,
        medicationId: 1,
        scheduledAt: `2026-08-${String(23 - id).padStart(2, '0')}T08:00:00+02:00`,
        status,
        source,
        takenAt: status === 'TAKEN' ? `2026-08-${String(23 - id).padStart(2, '0')}T08:03:00+02:00` : null,
        snoozedUntil: status === 'SNOOZED' ? '2026-08-22T08:30:00+02:00' : null,
        medicationName: 'Vitamin D',
        doseAmount: 1,
        doseUnit: 'tablet',
        notes: 'Take with breakfast'
    };
}

async function mockMedications(page, initialMedications = [medication()], initialDoses = [dose(1, 'TAKEN'), dose(2, 'MISSED'), dose(3, 'SNOOZED')]) {
    let medications = initialMedications.map(value => ({...value, reminderTimes: [...value.reminderTimes]}));
    let doses = initialDoses.map(value => ({...value}));
    await page.route('https://accounts.google.com/gsi/client', route => route.fulfill({contentType: 'application/javascript', body: ''}));
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
            return route.fulfill({contentType: 'application/json', body: '[]'});
        }
        if (path === '/api/medications' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(medications)});
        }
        if (path === '/api/medications' && request.method() === 'POST') {
            const created = {...request.postDataJSON(), id: medications.length + 1};
            medications = [...medications, created];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(created)});
        }
        if (path === '/api/medications/doses' && request.method() === 'GET') {
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(doses)});
        }
        const manualDoseMatch = path.match(/^\/api\/medications\/(\d+)\/doses$/);
        if (manualDoseMatch && request.method() === 'POST') {
            const recorded = {
                ...dose(doses.length + 10, 'TAKEN', 'MANUAL'),
                medicationId: Number(manualDoseMatch[1]),
                scheduledAt: request.postDataJSON().takenAt,
                takenAt: request.postDataJSON().takenAt
            };
            doses = [recorded, ...doses];
            return route.fulfill({contentType: 'application/json', body: JSON.stringify(recorded)});
        }
        return route.fulfill({contentType: 'application/json', body: '[]'});
    });
}

async function openMedications(page) {
    await page.addInitScript(() => window.history.replaceState({}, '', '/medications'));
    await page.goto('/');
}

test('medications show exact recurring times and recent dose states', async ({page}) => {
    await mockMedications(page);
    await openMedications(page);

    await expect(page.getByRole('cell', {name: 'Vitamin D'})).toBeVisible();
    await expect(page.getByText('Every 2 weeks at 08:00, 20:00')).toBeVisible();
    await page.getByRole('tab', {name: 'Dose log'}).click();
    await expect(page.getByRole('cell', {name: 'Taken', exact: true})).toBeVisible();
    await expect(page.getByRole('cell', {name: 'Missed', exact: true})).toBeVisible();
    await expect(page.getByRole('cell', {name: 'Snoozed', exact: true})).toBeVisible();
});

test('a medication can be created with an exact reminder and logged now', async ({page}) => {
    await mockMedications(page, [], []);
    await openMedications(page);

    await page.getByRole('button', {name: 'New'}).click();
    const dialog = page.getByRole('dialog', {name: 'Medication'});
    await dialog.getByLabel('Medication name').fill('Magnesium');
    await dialog.getByLabel('Unit').fill('tablet');
    const createRequest = page.waitForRequest(request => request.url().endsWith('/api/medications') && request.method() === 'POST');
    await dialog.getByRole('button', {name: 'Save'}).click();

    const payload = (await createRequest).postDataJSON();
    expect(payload.name).toBe('Magnesium');
    expect(payload.reminderTimes).toEqual(['08:00']);
    expect(payload.repeatEvery).toBe(1);
    expect(payload.repeatUnit).toBe('DAY');
    await expect(page.getByRole('cell', {name: 'Magnesium'})).toBeVisible();

    const logRequest = page.waitForRequest(request => /\/api\/medications\/1\/doses$/.test(request.url()) && request.method() === 'POST');
    await page.getByRole('button', {name: 'Log now'}).click();
    expect((await logRequest).postDataJSON().takenAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    await expect(page.getByText('Magnesium dose recorded')).toBeVisible();
    await page.getByRole('tab', {name: 'Dose log'}).click();
    await expect(page.getByText('Manual', {exact: true})).toBeVisible();
});

test('medication management fits mobile and desktop widths', async ({page}) => {
    await mockMedications(page);
    await page.setViewportSize({width: 1280, height: 800});
    await openMedications(page);

    let dimensions = await page.evaluate(() => ({scrollWidth: document.documentElement.scrollWidth, width: window.innerWidth}));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.width);

    await page.setViewportSize({width: 393, height: 851});
    await page.getByRole('button', {name: 'Edit medication'}).click();
    const dialog = page.getByRole('dialog', {name: 'Medication'});
    await expect(dialog.getByLabel('Medication name')).toBeVisible();
    dimensions = await page.evaluate(() => ({scrollWidth: document.documentElement.scrollWidth, width: window.innerWidth}));
    expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.width);
});
