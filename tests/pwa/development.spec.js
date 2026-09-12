const {test, expect} = require('@playwright/test');
const http = require('node:http');
const {googleClientScript} = require('../fixtures/dashboard.cjs');

test('Vite development serves Vue modules, proxies API requests and reloads through HMR', async ({browser}) => {
  const requests = [];
  const api = http.createServer((req, res) => {
    requests.push(req.url);
    res.writeHead(403, {'Content-Type': 'application/json'});
    res.end('{}');
  });
  await new Promise(resolve => api.listen(0, '127.0.0.1', resolve));
  const {createServer} = await import('vite');
  const vite = await createServer({define: {'import.meta.env.VITE_GOOGLE_CLIENT_ID': JSON.stringify('test-client-id')}, server: {host: '127.0.0.1', port: 0, strictPort: true, proxy: {'/api': `http://127.0.0.1:${api.address().port}`}}});
  const context = await browser.newContext({serviceWorkers: 'block'});
  try {
    await vite.listen();
    await context.route('https://accounts.google.com/**', route => route.fulfill({contentType: 'application/javascript', body: googleClientScript}));
    const page = await context.newPage();
    await page.goto(`http://127.0.0.1:${vite.httpServer.address().port}/login`);
    await expect(page.getByRole('button', {name: 'Sign in with Google'})).toBeVisible();
    await expect(page.getByAltText('Weight Control', {exact: true})).toBeVisible();
    expect(requests).toContain('/api/auth/me');
    const reload = page.waitForEvent('framenavigated', frame => frame === page.mainFrame());
    vite.ws.send({type: 'full-reload'});
    await reload;
    await expect(page.getByRole('button', {name: 'Sign in with Google'})).toBeVisible();
    expect(requests.filter(url => url === '/api/auth/me').length).toBe(2);
  } finally {
    await context.close();
    await vite.close();
    await new Promise(resolve => api.close(resolve));
  }
});
