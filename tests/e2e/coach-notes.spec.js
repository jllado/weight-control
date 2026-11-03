const {test, expect} = require('@playwright/test');
const path = require('node:path');
const {profile} = require('../fixtures/dashboard.cjs');

async function mockCoachNotes(page) {
    const notes = [{id: 1, date: '2026-09-19', content: 'Existing private note'}];
    await page.route('**/coach-notes', route => route.request().resourceType() === 'document'
        ? route.fulfill({path: path.resolve(__dirname, '../../dist/index.html')})
        : route.continue());
    await page.route('**/api/**', route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') return route.fulfill({json: {email: 'test@example.com', displayName: 'Test', authenticated: true}});
        if (path === '/api/profile') return route.fulfill({json: profile});
        if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
        if (path === '/api/notifications/pending') return route.fulfill({json: []});
        if (path === '/api/coach-notes' && request.method() === 'GET') return route.fulfill({json: notes});
        if (path === '/api/coach-notes' && request.method() === 'POST') {
            const note = {id: 2, ...request.postDataJSON()};
            notes.unshift(note);
            return route.fulfill({json: note});
        }
        return route.fulfill({json: []});
    });
}

for (const width of [320, 376, 390, 1280]) {
    test(`Coach Notes navigation routes and preserves a saved note at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        await mockCoachNotes(page);
        await page.addInitScript(() => {
            if (!window.sessionStorage.getItem('coach-notes-test-route')) {
                window.sessionStorage.setItem('coach-notes-test-route', 'ready');
                window.history.replaceState({}, '', '/weights');
            }
        });
        await page.goto('/');

        const headerShortcut = page.locator('.app-header-actions').getByRole('link', {name: 'Coach Notes', exact: true});
        await expect(headerShortcut).toBeVisible({visible: width > 575});
        if (width > 575) {
            await expect(headerShortcut).toHaveAttribute('href', '/coach-notes');
            await expect(headerShortcut).toHaveAttribute('title', 'Coach Notes');
            await expect(headerShortcut.locator('.pi-book.p-button-icon-left')).toHaveCount(1);
            await expect(headerShortcut).toHaveCSS('text-decoration-line', 'none');
            await expect(headerShortcut.locator('.p-button-label')).toBeVisible();
        }
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.locator('.app-menubar').screenshot({path: testInfo.outputPath(`coach-notes-header-${width}.png`), animations: 'disabled'});

        const openNotes = async () => {
            if (width <= 575) {
                await page.getByRole('button', {name: 'Navigation', exact: true}).click();
                await page.getByRole('menuitem', {name: 'Review', exact: true}).click();
                await page.getByRole('menuitem', {name: 'Coach Notes', exact: true}).click();
                return;
            }
            await headerShortcut.focus();
            await expect(headerShortcut).toBeFocused();
            await headerShortcut.press('Enter');
        };
        await openNotes();
        await expect(page).toHaveURL('/coach-notes');
        await expect(page.getByText('Existing private note', {exact: true})).toBeVisible();

        const content = `Saved through navigation at ${width}px`;
        await page.getByLabel('Note', {exact: true}).fill(content);
        await page.getByRole('button', {name: 'Add note', exact: true}).click();
        await expect(page.getByText(content, {exact: true})).toBeVisible();
        await page.goBack();
        await expect(page).toHaveURL('/weights');
        await openNotes();
        await expect(page.getByText(content, {exact: true})).toBeVisible();
        await page.reload();
        await expect(page.getByText(content, {exact: true})).toBeVisible();
        await page.screenshot({path: testInfo.outputPath(`coach-notes-page-${width}.png`), fullPage: true, animations: 'disabled'});
    });
}

for (const width of [376, 390, 1280]) {
    test(`Coach Notes compact actions edit and delete at ${width}px`, async ({page}, testInfo) => {
        await page.setViewportSize({width, height: 900});
        const note = {id: 1, date: '2026-09-20', content: 'A private note with a long word: ' + 'exercise'.repeat(30)};
        await page.route('**/api/**', route => {
            const request = route.request();
            const path = new URL(request.url()).pathname;
            if (path === '/api/auth/me') return route.fulfill({json: {email: 'test@example.com', displayName: 'Test', authenticated: true}});
            if (path === '/api/profile') return route.fulfill({json: profile});
            if (path === '/api/urge-pauses') return route.fulfill({json: {pause: null, serverNow: new Date().toISOString()}});
            if (path === '/api/coach-notes') return route.fulfill({json: [note]});
            if (path === '/api/coach-notes/1' && request.method() === 'DELETE') return route.fulfill({status: 204});
            return route.fulfill({json: []});
        });
        await page.addInitScript(() => window.history.replaceState({}, '', '/coach-notes'));
        await page.goto('/');
        const edit = page.getByRole('button', {name: 'Edit note from 20/09/2026', exact: true});
        const remove = page.getByRole('button', {name: 'Delete note from 20/09/2026', exact: true});
        await expect(edit).toBeVisible();
        await expect(edit).toHaveClass(/compact-action.*p-button-outlined/);
        await expect(remove).toHaveClass(/p-button-danger/);
        const editBox = await edit.boundingBox();
        const deleteBox = await remove.boundingBox();
        expect(editBox.width).toBeCloseTo(editBox.height, 0);
        expect(deleteBox.width).toBeCloseTo(editBox.width, 0);
        expect(deleteBox.y).toBeCloseTo(editBox.y, 0);
        expect(deleteBox.x - editBox.x - editBox.width).toBeCloseTo(8, 0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
        await page.screenshot({path: testInfo.outputPath(`coach-notes-${width}.png`), fullPage: true});
        await edit.click();
        await expect(page.getByLabel('Note', {exact: true})).toHaveValue(note.content);
        await page.getByRole('button', {name: 'Cancel', exact: true}).click();
        page.once('dialog', dialog => dialog.accept());
        await remove.click();
        await expect(page.getByText('No Coach Notes yet.')).toBeVisible();
    });
}
