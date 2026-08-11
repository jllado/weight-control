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
