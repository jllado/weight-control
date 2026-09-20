const {test, expect} = require('@playwright/test');
const {profile} = require('../fixtures/dashboard.cjs');

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
