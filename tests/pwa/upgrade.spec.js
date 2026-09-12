const {test, expect} = require('@playwright/test');
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const {googleClientScript, profile, dashboard} = require('../fixtures/dashboard.cjs');

test('installed Vue CLI app upgrades to Vite once, keeps its identity and works offline', async ({browser}) => {
  let directory = path.resolve('tmp/pwa-legacy/dist');
  let authenticated = false;
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    res.setHeader('Cache-Control', 'no-store');
    if (url.pathname.startsWith('/api/')) {
      let data = [];
      if (url.pathname === '/api/auth/google') authenticated = true;
      if (['/api/auth/me', '/api/auth/google'].includes(url.pathname)) {
        res.statusCode = authenticated ? 200 : 403;
        data = {authenticated, email: 'pwa@example.test', displayName: 'PWA test'};
      } else if (url.pathname === '/api/profile') data = profile;
      else if (url.pathname === '/api/dashboard') data = dashboard;
      else if (url.pathname === '/api/urge-pauses') data = {pause: null, serverNow: new Date().toISOString()};
      else if (url.pathname === '/api/coach-warnings') data = {active: [], hasHistory: false};
      else if (url.pathname === '/api/workouts/dashboard') data = {currentWorkout: null, previousWeekWorkout: null, preloadWorkouts: [], recordEvents: []};
      res.setHeader('Content-Type', 'application/json');
      res.end(JSON.stringify(data));
      return;
    }
    let file = path.join(directory, url.pathname);
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      if (path.extname(url.pathname)) {res.writeHead(404); res.end(); return;}
      file = path.join(directory, 'index.html');
    }
    res.setHeader('Content-Type', {'.html':'text/html', '.js':'application/javascript', '.css':'text/css', '.json':'application/json', '.png':'image/png', '.svg':'image/svg+xml', '.woff2':'font/woff2'}[path.extname(file)] || 'application/octet-stream');
    fs.createReadStream(file).pipe(res);
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const context = await browser.newContext({serviceWorkers: 'allow', permissions: ['notifications']});
  try {
    await context.route('https://accounts.google.com/**', route => route.fulfill({contentType: 'application/javascript', body: googleClientScript}));
    const page = await context.newPage();
    await page.goto(origin + '/login');
    await expect(page.getByRole('button', {name: 'Sign in with Google'})).toBeVisible();
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.reload();
    await expect.poll(() => page.evaluate(() => navigator.serviceWorker.controller?.scriptURL)).toBe(origin + '/service-worker.js');
    const oldTitle = await page.title();
    const oldManifest = await (await context.request.get(origin + '/manifest.json')).json();
    const oldAssets = await page.evaluate(async () => (await Promise.all((await caches.keys()).map(async key => (await (await caches.open(key)).keys()).map(req => req.url)))).flat());
    expect(oldAssets.some(url => url.includes('/js/'))).toBe(true);
    const cdp = await context.newCDPSession(page);
    const registrations = new Set();
    cdp.on('ServiceWorker.workerRegistrationUpdated', ({registrations: entries}) => entries.filter(entry => !entry.isDeleted).forEach(entry => registrations.add(entry.registrationId)));
    await cdp.send('ServiceWorker.enable');
    await expect.poll(() => registrations.size).toBe(1);
    const registrationId = [...registrations][0];
    const beforeSubscription = await page.evaluate(async () => (await (await navigator.serviceWorker.ready).pushManager.getSubscription())?.toJSON() || null);
    directory = path.resolve('dist');
    const nextWorker = context.waitForEvent('serviceworker');
    await page.evaluate(async () => (await navigator.serviceWorker.ready).update());
    const worker = await nextWorker;
    await expect(page.getByRole('button', {name: 'Update app', exact: true})).toBeVisible();
    let reloads = 0;
    const navigation = request => {if (request.isNavigationRequest() && request.frame() === page.mainFrame()) reloads++;};
    page.on('request', navigation);
    await Promise.all([page.waitForEvent('framenavigated', frame => frame === page.mainFrame()), page.getByRole('button', {name: 'Update app', exact: true}).click()]);
    await expect(page.locator('script[type="module"][src^="/assets/"]')).toHaveCount(1);
    await expect(page.getByRole('button', {name: 'Sign in with Google'})).toBeVisible();
    expect(reloads).toBe(1);
    await expect(page).toHaveTitle(oldTitle);
    page.off('request', navigation);
    const manifest = await (await context.request.get(origin + '/manifest.json')).json();
    for (const key of ['id','scope','start_url','shortcuts','icons','name','short_name','display']) expect(manifest[key]).toEqual(oldManifest[key]);
    expect([...registrations]).toEqual([registrationId]);
    expect(await page.evaluate(async () => (await (await navigator.serviceWorker.ready).pushManager.getSubscription())?.toJSON() || null)).toEqual(beforeSubscription);
    const cached = await page.evaluate(async () => (await Promise.all((await caches.keys()).map(async key => (await (await caches.open(key)).keys()).map(req => req.url)))).flat());
    expect(cached.some(url => url.includes('/assets/'))).toBe(true);
    expect(cached.some(url => url.includes('/js/'))).toBe(false);
    expect(cached.some(url => url.includes('/api/'))).toBe(false);
    await context.setOffline(true);
    await page.goto(origin + '/weights');
    await expect(page.locator('script[type="module"][src^="/assets/"]')).toHaveCount(1);
    await expect(page.getByAltText('Weight Control', {exact:true})).toBeVisible();
    await context.setOffline(false);
    await page.goto(origin + '/login');
    await cdp.send('ServiceWorker.deliverPushMessage', {origin, registrationId, data: JSON.stringify({title: 'PWA upgrade reminder', body: 'Open the saved action.', url: '/?decisionOutcome=WIN', tag: 'pwa-upgrade'})});
    await expect.poll(() => page.evaluate(async () => (await (await navigator.serviceWorker.ready).getNotifications()).length)).toBe(1);
    await worker.evaluate(async () => {
      const [notification] = await self.registration.getNotifications();
      self.dispatchEvent(new self.NotificationEvent('notificationclick', {notification}));
    });
    await expect(page).toHaveURL(/\/login\?decisionOutcome=WIN$/);
    await page.getByRole('button', {name: 'Sign in with Google'}).click();
    await expect(page.getByRole('dialog', {name: 'Record WIN', exact: true})).toBeVisible();
    await expect(page).not.toHaveURL(/login/);
  } finally {
    await context.close();
    await new Promise(resolve => server.close(resolve));
  }
});
